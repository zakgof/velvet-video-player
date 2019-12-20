package com.zakgof.velvetvideo.player;


import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import com.zakgof.velvetvideo.IAudioFrame;
import com.zakgof.velvetvideo.IDecodedPacket;
import com.zakgof.velvetvideo.IDemuxer;
import com.zakgof.velvetvideo.IVideoFrame;
import com.zakgof.velvetvideo.MediaType;
import com.zakgof.velvetvideo.impl.VelvetVideoLib;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.DefaultSubscriber;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MainRx extends Application {

	private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, ":::decoder"));
	private static final Scheduler decodeScheduler = Schedulers.from(executor);

	private ImageView iv;
	private long start;
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
		initStage(stage);

		decoder(params.get(0))
			.observeOn(JavaFxScheduler.platform(), false, 256)
			.subscribe(new DefaultSubscriber<>() {
				@Override
				public void onNext(IDecodedPacket<?> packet) {
					if (packet.is(MediaType.Video) && packet.stream().index() == 0) {
						consumeVideoFrame(packet.asVideo(), () -> request(1));
					} else if (packet.is(MediaType.Audio) && packet.stream().index() == 1) {
						consumeAudioFrame(packet.asAudio(), () -> request(1));
					} else {
						System.err.println("receiver: skip packet");
						request(1);
					}
				}

				@Override
				protected void onStart() {
					request(1);
				}

				@Override
				public void onError(Throwable t) {
					t.printStackTrace();
				}

				@Override
				public void onComplete() {
					System.out.println("Playback finished");
					executor.shutdown();
				}
			});
	}

	@Override
	public void stop() throws Exception {
		decodeScheduler.shutdown();
		executor.shutdown();
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

	private void consumeVideoFrame(IVideoFrame videoFrame, Runnable requester) {
		Image fxImage = SwingFXUtils.toFXImage(videoFrame.image(), null);

		long wallTime = System.currentTimeMillis() - start;
		if (start == 0)
			wallTime = 0;
		long frameTime = videoFrame.nanostamp() / 1000000L;
		long wait = frameTime - wallTime;
		System.out.println("ui: fetched frame [" + frameTime  + "] , wall=" + wallTime + "  wait=" + wait);

		if (soundLine != null) {
			long audioTime = soundLine.getMicrosecondPosition() / 1000L;
			long audioDesync = wallTime - audioTime;
			wait += audioDesync / 4;
		}

		if (wait < 0 || start == 0)
			wait = 0;

		JavaFxScheduler.platform().scheduleDirect(() -> {
			renderFrame(fxImage, frameTime);
			if (soundLine != null && !soundLine.isActive()) {
				soundLine.start();
			}
			requester.run();
		}, wait, TimeUnit.MILLISECONDS);
	}

	private void consumeAudioFrame(IAudioFrame audioFrame, Runnable requester) {
		if (soundLine == null) {
			initAudio(audioFrame.stream().properties().format());
		}
		soundLine.write(audioFrame.samples(), 0, audioFrame.samples().length);
		System.out.println("audio: written bytes = " + audioFrame.samples().length + "   available=" + soundLine.available());
		requester.run();
	}

	private void renderFrame(Image fxImage, long frameTime) {
		iv.setImage(fxImage);
		long now = System.currentTimeMillis();
		if (start == 0)
			start = now;
		long wallTimeRender = now - start;
		System.out.println("+++ Render frame [" + frameTime + "] at " + wallTimeRender);
	}

	private Flowable<IDecodedPacket<?>> decoder(String filename) {
		return Flowable.<IDecodedPacket<?>, IDemuxer> generate(
			() -> VelvetVideoLib.getInstance().demuxer(new File(filename)),
			(demuxer, emitter) -> {
				IDecodedPacket<?> packet = demuxer.nextPacket();
				if (packet == null) {
					demuxer.close();
					emitter.onComplete();
				} else {
					System.out.println("decoder: fetch a packet " + packet.type());
					emitter.onNext(packet);
				}
			}
		).subscribeOn(decodeScheduler);
	}
}
