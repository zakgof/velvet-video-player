package com.zakgof.velvetvideo.player;

import java.io.File;

import com.zakgof.velvetvideo.MediaType;
import com.zakgof.velvetvideo.impl.VelvetVideoLib;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MainSimple extends Application {

	private ImageView iv;
	private Thread worker;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) {
		var params = getParameters().getRaw();
		if (params.isEmpty()) {
			throw new IllegalArgumentException("pass video file name via command file");
		}

		startUI(stage);

		var videoFile = params.get(0);
		worker = new Thread(() -> worker(videoFile), "decoder");
		worker.start();
	}

	private void startUI(Stage stage) {
		iv = new ImageView();
		iv.fitWidthProperty().bind(stage.widthProperty());
		iv.fitHeightProperty().bind(stage.heightProperty());
		iv.setPreserveRatio(true);
		Scene scene = new Scene(new StackPane(iv), 640, 480);
		stage.setScene(scene);
		stage.show();
	}

	@Override
	public void stop() throws Exception {
		worker.interrupt();
		worker.join();
	}

	void worker(String filename) {
		var lib = VelvetVideoLib.getInstance();
		try (var demuxer = lib.demuxer(new File(filename))) {
			var start = System.nanoTime();
			for (var packet : demuxer) {
				if (Thread.currentThread().isInterrupted()) {
					return;
				}
				if (packet.is(MediaType.Video) && packet.asVideo().stream() == demuxer.videoStreams().get(0)) {
					var frame = packet.asVideo();
					var nanostamp = frame.nanostamp();
					var bi = frame.image();
					var image = SwingFXUtils.toFXImage(bi, null);
					var wait = start + nanostamp - System.nanoTime();
					if (wait > 0)
						try {
							Thread.sleep(wait / 1000000L, (int) (wait % 1000000L));
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
					Platform.runLater(() -> displayImage(image));
				}
			};
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	void displayImage(Image image) {
		iv.setImage(image);
	}
}
