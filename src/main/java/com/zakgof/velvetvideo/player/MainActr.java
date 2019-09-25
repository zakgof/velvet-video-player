package com.zakgof.velvetvideo.player;


import java.io.File;

import com.zakgof.actr.ActorRef;
import com.zakgof.actr.ActorSystem;
import com.zakgof.actr.DedicatedThreadScheduler;
import com.zakgof.velvetvideo.FFMpegVideoLib;
import com.zakgof.velvetvideo.IVideoLib;
import com.zakgof.velvetvideo.IVideoLib.IDecodedPacket;
import com.zakgof.velvetvideo.IVideoLib.IDemuxer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MainActr extends Application {

	static ActorRef<MainActr> UI_ACTOR;
	static ActorRef<DecoderActor> DECODER_ACTOR;
	static ActorSystem SYSTEM = ActorSystem.create("default");

	private ImageView iv;

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
		iv = new ImageView();
		iv.fitWidthProperty().bind(stage.widthProperty());
		iv.fitHeightProperty().bind(stage.heightProperty());
		iv.setPreserveRatio(true);
		Scene scene = new Scene(new StackPane(iv), 640, 480);
		stage.setScene(scene);
		stage.show();
	}
	
	public void displayFrame(Image image) {
		iv.setImage(image);
	}

	@Override
	public void stop() throws Exception {
		SYSTEM.shutdown();
	}
}

class DecoderActor {
	private final IVideoLib lib = new FFMpegVideoLib();
	private long start;
	private IDemuxer demuxer;
	
	void startFile(String filename) {
		start = System.nanoTime();
		demuxer = lib.demuxer(new File(filename));
		decodeFrame();
	}

	private void decodeFrame() {
		IDecodedPacket packet = demuxer.nextPacket();
		if (packet != null) {
			if (packet.isVideo() && packet.video().stream() == demuxer.videos().get(0)) {
				var frame = packet.video();
				var bi = frame.image();
				var image = SwingFXUtils.toFXImage(bi, null);
				var nextFrame = start + frame.nanostamp();
				MainActr.DECODER_ACTOR.later(DecoderActor::decodeFrame, (nextFrame - System.nanoTime()) / 1000000L);
				MainActr.UI_ACTOR.tell(main -> main.displayFrame(image));	
			}
		}
	}
}
