package com.zakgof.velvetvideo.player;

import java.awt.image.BufferedImage;
import java.io.File;

import com.zakgof.velvetvideo.IVideoDecoderStream;
import com.zakgof.velvetvideo.IVideoEncoderStream;
import com.zakgof.velvetvideo.IVideoEncoderBuilder;
import com.zakgof.velvetvideo.impl.VelvetVideoLib;

public class Main {

	static int i;

	public static void main(String[] args) {
		var lib = VelvetVideoLib.getInstance();
		// System.err.println(lib.codecs(Direction.Encode, MediaType.Video));
		IVideoEncoderBuilder[] encoders = new IVideoEncoderBuilder[] {
    		lib.videoEncoder("mjpeg"),
			lib.videoEncoder("libopenh264").param("b",  "600000"),
			lib.videoEncoder("libopenh264").param("b", "1000000"),
			lib.videoEncoder("libopenh264").param("b", "2000000"),
			lib.videoEncoder("mpeg4").param("b",  "500000"),
			lib.videoEncoder("mpeg4").param("b", "1000000"),
			lib.videoEncoder("mpeg4").param("b", "2000000"),
			lib.videoEncoder("h263p"),
			lib.videoEncoder("libx264").param("preset", "veryslow"),
			lib.videoEncoder("libx264").param("preset", "medium"),
			lib.videoEncoder("libx264").param("preset", "veryfast"),
			lib.videoEncoder("libx264").param("preset", "ultrafast"),
			lib.videoEncoder("libx265"),
			lib.videoEncoder("libvpx"),
			lib.videoEncoder("libvpx-vp9").enableExperimental().param("deadline", "realtime").param("threads", "4"),
			lib.videoEncoder("libvpx-vp9").enableExperimental().param("deadline", "good").param("threads", "4"),
			lib.videoEncoder("libvpx-vp9").enableExperimental().param("deadline", "best").param("threads", "4"),
			lib.videoEncoder("libvpx-vp9").enableExperimental().param("deadline", "realtime"),
			lib.videoEncoder("libvpx-vp9").enableExperimental().param("deadline", "good"),
			lib.videoEncoder("libvpx-vp9").enableExperimental().param("deadline", "best"),
		};

		String names[] = {
 				"mjpeg",
				"libopenh264-600k",
				"libopenh264-1m",
				"libopenh264-2m",
				"mpeg4-500k",
				"mpeg4-1m",
				"mpeg4-2m",
				"h263p",
				"libx264-veryslow",
				"libx264-medium",
				"libx264-veryfast",
				"libx264-ultrafast",
				"libx265",
				"libvpx",
				"libvpx-vp9-realtime-t4",
				"libvpx-vp9-good-t4",
				"libvpx-vp9-best-t4",
				"libvpx-vp9-realtime",
				"libvpx-vp9-good",
				"libvpx-vp9-best",
		};


		File f = new File("D:\\Download\\I.Am.Mother-6.9.mkv");

		BufferedImage[] images = new BufferedImage[20];
		try (var demuxer = lib.demuxer(f)) {
			IVideoDecoderStream videoStream = demuxer.videoStreams().get(0);
			videoStream.seek(5000);
			for (int i = 0; i < images.length; i++) {
				BufferedImage image = videoStream.nextFrame().image();
				images[i] = image;
			}
		}

		for (int i=0; i<encoders.length; i++) {
			encode(encoders[i], names[i], images);
		}
	}

	private static void encode(IVideoEncoderBuilder builder, String name, BufferedImage[] images) {

		File out = new File("E:\\Business\\coderlab\\" + name + ".avi");
		// System.err.println("starting " + name + " ...");

		var lib = VelvetVideoLib.getInstance();

		long start = System.currentTimeMillis();
		try (var muxer = lib.muxer("avi").videoEncoder(builder.bitrate(1000000)).build(out)) {
			IVideoEncoderStream encoder = muxer.videoEncoder(0);
			int fr = 0;
			for (BufferedImage image : images) {
				encoder.encode(image);
				// if (i%1 == 0) System.out.print(fr*100/images.length + "%... ");
				fr++;
			}
		}
		long end = System.currentTimeMillis();
		System.err.println();
		System.err.println("" + out + "   " + out.length() + "  in " + (end-start) + " ms");
	}

}
