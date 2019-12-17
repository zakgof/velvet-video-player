package com.zakgof.velvetvideo.player;

import java.io.File;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

import com.zakgof.velvetvideo.Direction;
import com.zakgof.velvetvideo.IAudioFrame;
import com.zakgof.velvetvideo.IDecodedPacket;
import com.zakgof.velvetvideo.IDecoderAudioStream;
import com.zakgof.velvetvideo.IDemuxer;
import com.zakgof.velvetvideo.MediaType;
import com.zakgof.velvetvideo.impl.VelvetVideoLib;

public class Main2 {

	static int i;

	public static void main(String[] args) throws LineUnavailableException {



		for (Mixer.Info mixerinfo : AudioSystem.getMixerInfo()) {
			System.err.println(mixerinfo.getName());
			for (Line.Info lineInfo : AudioSystem.getMixer(mixerinfo).getTargetLineInfo()) {
				if (lineInfo instanceof DataLine.Info) {
					for (AudioFormat fmt : ((DataLine.Info)lineInfo).getFormats()) {
						System.err.println("  " + fmt);
					}
				}
			}
		}

		var lib = VelvetVideoLib.getInstance();
		List<String> codecs = lib.codecs(Direction.Encode, MediaType.Audio);
		System.err.println(codecs);

		try (IDemuxer demuxer = lib.demuxer(new File(
				"D:\\Download\\I.Am.Mother-6.9.mkv"))) {
		//		"D:\\Download\\Mr.Robot.S01.720p.BDRip.3xRus.Eng.HDCLUB\\Mr.Robot.S01E01.720p.BDRip.3xRus.Eng.HDCLUB.mkv"))) {

			IDecoderAudioStream stream = demuxer.audioStreams().get(0);
			AudioFormat format = stream.properties().format();

			DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

			SourceDataLine soundLine = (SourceDataLine) AudioSystem.getLine(info);
			soundLine.open(format);
			soundLine.addLineListener(e -> {
				if (e.getFramePosition() % 1000 == 0)
					System.out.println("Playing frame " + e.getFramePosition());
			});
			soundLine.start();
			IDecodedPacket<?> packet;
			while ((packet = demuxer.nextPacket()) != null) {

				System.out.println("=== FRAME FOR " + (packet.is(MediaType.Audio) ? "audio " + packet.asAudio().stream().name() : "") + (packet.is(MediaType.Video) ? "video":""));

				if (packet.is(MediaType.Audio)) {
					IAudioFrame audioFrame = packet.asAudio();
					if (audioFrame.stream().index() == 1) {
						byte[] samples = audioFrame.samples();
						System.err.println("== Client received frame: " + samples.length + " bytes");
						soundLine.write(samples, 0, samples.length);
					}
				}
			}
			soundLine.close();

		}
	}

}
