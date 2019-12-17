package com.zakgof.velvetvideo.player;


import java.io.File;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import com.zakgof.actr.ActorRef;
import com.zakgof.actr.ActorSystem;
import com.zakgof.actr.DedicatedThreadScheduler;
import com.zakgof.velvetvideo.IAudioFrame;
import com.zakgof.velvetvideo.IDecodedPacket;
import com.zakgof.velvetvideo.IDemuxer;
import com.zakgof.velvetvideo.IVelvetVideoLib;
import com.zakgof.velvetvideo.IVideoFrame;
import com.zakgof.velvetvideo.impl.VelvetVideoLib;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MainActr extends Application {

	private static final long BUFFER_READY_SPIN_WAIT = 100L;

	static ActorRef<MainActr> UI_ACTOR;
	static ActorRef<DecoderActor> DECODER_ACTOR;
	static ActorSystem SYSTEM = ActorSystem.create("default");

	private ImageView iv;
	private Buffer buffer = new Buffer(0, 1);
	private boolean waiting;
	private long start;
	private long absstart;
	private SourceDataLine soundLine;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) {
		var params = getParameters().getRaw();
		if (params.isEmpty()) {
			throw new IllegalArgumentException("pass video file name via command file");
		}
		createActors();

		UI_ACTOR.tell(main -> main.startUI(stage));

		var videoFile = params.get(0);
		DECODER_ACTOR.tell(decoder -> decoder.startFile(videoFile));
	}

	private void createActors() {
		DECODER_ACTOR = SYSTEM.<DecoderActor>actorBuilder()
			.constructor(DecoderActor::new)
			.scheduler(new DedicatedThreadScheduler("decoder"), true)
			.build();

		UI_ACTOR = SYSTEM.<MainActr>actorBuilder()
			.object(this)
			.scheduler((runnable, obj) -> Platform.runLater(runnable), false)
			.build();
	}

	public void startUI(Stage stage) {
		initStage(stage);
		UI_ACTOR.tell(MainActr::waitBuffersReady);
	}

	private void initAudio(AudioFormat format) {
		try {
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
			soundLine = (SourceDataLine) AudioSystem.getLine(info);
			soundLine.open(format);
			soundLine.addLineListener(e -> {
				if (e.getFramePosition() % 1000 == 0)
					System.out.println("Playing frame " + e.getFramePosition());
			});
			soundLine.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void initStage(Stage stage) {
		iv = new ImageView();
		iv.fitWidthProperty().bind(stage.widthProperty());
		iv.fitHeightProperty().bind(stage.heightProperty());
		iv.setPreserveRatio(true);
		Scene scene = new Scene(new StackPane(iv), 640, 480);
		stage.setScene(scene);
		stage.show();
	}

	private void waitBuffersReady() {
		if (buffer.isReady()) {
			startPlayback();
		} else {
			UI_ACTOR.later(m -> m.waitBuffersReady(), BUFFER_READY_SPIN_WAIT);
		}
	}

	private void startPlayback() {
		System.err.println("Start playback");
		fillAudio();
		IVideoFrame videoFrame = buffer.pullVideo();
		Image fxImage = SwingFXUtils.toFXImage(videoFrame.image(), null);
		long now = System.currentTimeMillis();
		start = now;
		absstart = now;
		display(fxImage);
		soundLine.start();
		UI_ACTOR.tell(m -> m.prepareNextFrame());
	}

	private void display(Image fxImage) {
		iv.setImage(fxImage);
	}

	private void fillAudio() {
		for (;;) {
			if (soundLine != null) {
				if (soundLine.available() < 8192)
					return;
			}
			IAudioFrame audioFrame = buffer.pullAudio();
			if (audioFrame == null)
				return;
			if (soundLine == null) {
				initAudio(audioFrame.stream().properties().format());
			}
			soundLine.write(audioFrame.samples(), 0, audioFrame.samples().length);
			System.out.println("audio: written bytes = " + audioFrame.samples().length + "   available=" + soundLine.available());
		}
	}

	private void prepareNextFrame() {
		fillAudio();
		IVideoFrame videoFrame = buffer.pullVideo();

		if (videoFrame == null) {
			waiting = true;
			System.err.println("Waiting");
			return;
		}

		Image fxImage = SwingFXUtils.toFXImage(videoFrame.image(), null);
		long wallTime = System.currentTimeMillis() - start;
		long frameTime = videoFrame.nanostamp() / 1000000L;
		long audioTime = soundLine.getMicrosecondPosition() / 1000L;

		long wait = frameTime - wallTime;
		System.out.println("ui: wait=" + wait);

		long audioDesync = wallTime - audioTime;
		wait += audioDesync / 4;

		if (wait < 0)
			wait = 0;

		UI_ACTOR.later(m -> m.playFrame(fxImage, frameTime), wait);
	}

	private void playFrame(Image fxImage, long frameTime) {
		long wall = System.currentTimeMillis() - start;
		long audioTime = soundLine.getMicrosecondPosition() / 1000;
		System.out.println("ui: render shift=" + (frameTime - wall) + "   wall=" + wall + "  audio=" + audioTime);
		display(fxImage);
		prepareNextFrame();
	}

	public void push(IDecodedPacket<?> packet) {
		buffer.push(packet);
		if (waiting) {
			waiting = false;
			prepareNextFrame();
		}
	}

	public boolean canPush() {
		return !buffer.isFull();
	}

	@Override
	public void stop() throws Exception {
		SYSTEM.shutdown();
	}
}

class DecoderActor {
	private static final int DECODE_SPIN_WAIT = 100;
	private final IVelvetVideoLib lib = VelvetVideoLib.getInstance();
	private IDemuxer demuxer;

	void startFile(String filename) {
		demuxer = lib.demuxer(new File(filename));
		decodeFrame();
	}

	private void decodeFrame() {
		MainActr.UI_ACTOR.ask(m -> m.canPush(), push -> {
			if (push) {
				decodeAndPush();
			} else {
				System.out.println("decoder: idle");
				MainActr.DECODER_ACTOR.later(d -> d.decodeFrame(), DECODE_SPIN_WAIT);
			}
		});
	}

	private void decodeAndPush() {
		IDecodedPacket<?> packet = demuxer.nextPacket();
		if (packet != null) {
			MainActr.UI_ACTOR.tell(m -> m.push(packet));
			MainActr.DECODER_ACTOR.tell(d -> d.decodeFrame());
		} else {
			MainActr.SYSTEM.shutdown();
		}
	}
}
