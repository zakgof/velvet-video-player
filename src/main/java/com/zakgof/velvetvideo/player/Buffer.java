package com.zakgof.velvetvideo.player;

import java.util.LinkedList;

import com.zakgof.velvetvideo.IAudioFrame;
import com.zakgof.velvetvideo.IDecodedPacket;
import com.zakgof.velvetvideo.IVideoFrame;
import com.zakgof.velvetvideo.MediaType;

public class Buffer {

	private final static int MIN_BUFFER_SIZE =   8;
	private final static int MAX_BUFFER_SIZE = 128;

	private final int videoIndex;
	private final int audioIndex;

	private final LinkedList<IVideoFrame> videoQueue = new LinkedList<>();
	private final LinkedList<IAudioFrame> audioQueue = new LinkedList<>();

	public Buffer(int videoIndex, int audioIndex) {
		this.videoIndex = videoIndex;
		this.audioIndex = audioIndex;
	}

	public void push(IDecodedPacket<?> packet) {
		System.out.println("buffer: " + packet.type() + "/" + packet.stream().index());
		if (packet.is(MediaType.Video) && packet.stream().index() == videoIndex) {
			videoQueue.add(packet.asVideo());
			System.out.println("buffer: video queue = " + videoQueue.size());
		}
		if (packet.is(MediaType.Audio) && packet.stream().index() == audioIndex) {
			audioQueue.add(packet.asAudio());
			System.out.println("buffer: audio queue = " + audioQueue.size());
		}
	}

	public IVideoFrame pullVideo() {
		return videoQueue.isEmpty() ? null : videoQueue.remove(0);
	}

	public IAudioFrame pullAudio() {
		return audioQueue.isEmpty() ? null : audioQueue.remove(0);
	}

	public boolean isFull() {
		return audioQueue.size() > MAX_BUFFER_SIZE || videoQueue.size() > MAX_BUFFER_SIZE;
	}

	public boolean isReady() {
		return audioQueue.size() > MIN_BUFFER_SIZE && videoQueue.size() > MIN_BUFFER_SIZE;
	}

}
