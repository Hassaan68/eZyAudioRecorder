/*
 * Copyright 2018 Dmitriy Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.attaartechs.ezyaudiorecorder.audio.recorder;

import android.media.MediaRecorder;

import com.attaartechs.ezyaudiorecorder.ARApplication;
import com.attaartechs.ezyaudiorecorder.exception.InvalidOutputFile;
import com.attaartechs.ezyaudiorecorder.exception.RecorderInitException;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import timber.log.Timber;

import static com.attaartechs.ezyaudiorecorder.AppConstants.RECORD_MAX_DURATION;
import static com.attaartechs.ezyaudiorecorder.AppConstants.VISUALIZATION_INTERVAL;

public class AudioRecorder implements RecorderContract.Recorder {

	private MediaRecorder recorder = null;
	private File recordFile = null;

	private boolean isPrepared = false;
	private boolean isRecording = false;
	private Timer timerProgress;
	private long progress = 0;

	private RecorderContract.RecorderCallback recorderCallback;

	private static class RecorderSingletonHolder {
		private static AudioRecorder singleton = new AudioRecorder();

		public static AudioRecorder getSingleton() {
			return RecorderSingletonHolder.singleton;
		}
	}

	public static AudioRecorder getInstance() {
		return RecorderSingletonHolder.getSingleton();
	}

	private AudioRecorder() { }

	@Override
	public void setRecorderCallback(RecorderContract.RecorderCallback callback) {
		this.recorderCallback = callback;
	}

	@Override
	public void prepare(String outputFile, int channelCount, int sampleRate, int bitrate) {
		recordFile = new File(outputFile);
		if (recordFile.exists() && recordFile.isFile()) {
			recorder = new MediaRecorder();
			recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
			recorder.setAudioChannels(channelCount);
			recorder.setAudioSamplingRate(sampleRate);
			recorder.setAudioEncodingBitRate(bitrate);
			recorder.setMaxDuration(RECORD_MAX_DURATION);
			recorder.setOutputFile(recordFile.getAbsolutePath());
			try {
				recorder.prepare();
				isPrepared = true;
				if (recorderCallback != null) {
					recorderCallback.onPrepareRecord();
				}
			} catch (IOException | IllegalStateException e) {
				Timber.e(e, "prepare() failed");
				if (recorderCallback != null) {
					recorderCallback.onError(new RecorderInitException());
				}
			}
		} else {
			if (recorderCallback != null) {
				recorderCallback.onError(new InvalidOutputFile());
			}
		}
	}

	@Override
	public void startRecording() {
		if (isPrepared) {
			try {
				recorder.start();
				isRecording = true;
				ARApplication.setRecording(true);
				startRecordingTimer();
				if (recorderCallback != null) {
					recorderCallback.onStartRecord();
				}
			} catch (RuntimeException e) {
				Timber.e(e, "startRecording() failed");
				if (recorderCallback != null) {
					recorderCallback.onError(new RecorderInitException());
				}
			}
		} else {
			Timber.e("Recorder is not prepared!!!");
		}
	}

	@Override
	public void pauseRecording() {
		if (isRecording) {
			//TODO: For below API 24 pause is not available that why records append should be implemented.
//			recorder.pause();
			stopRecording();
		}
	}

	@Override
	public void stopRecording() {
		if (isRecording) {
			stopRecordingTimer();
			try {
				recorder.stop();
				ARApplication.setRecording(false);
			} catch (RuntimeException e) {
				Timber.e(e, "stopRecording() problems");
			}
			recorder.release();
			if (recorderCallback != null) {
				recorderCallback.onStopRecord(recordFile);
			}
			recordFile = null;
			isPrepared = false;
			isRecording = false;
			recorder = null;
		} else {
			Timber.e("Recording has already stopped or hasn't started");
		}
	}

	private void startRecordingTimer() {
		timerProgress = new Timer();
		timerProgress.schedule(new TimerTask() {
			@Override
			public void run() {
				if (recorderCallback != null && recorder != null) {
					try {
						recorderCallback.onRecordProgress(progress, recorder.getMaxAmplitude());
					} catch (IllegalStateException e) {
						Timber.e(e);
					}
					progress += VISUALIZATION_INTERVAL;
				}
			}
		}, 0, VISUALIZATION_INTERVAL);
	}

	private void stopRecordingTimer() {
		timerProgress.cancel();
		timerProgress.purge();
		progress = 0;
	}

	@Override
	public boolean isRecording() {
		return isRecording;
	}
}
