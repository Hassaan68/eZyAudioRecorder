package com.attaartechs.ezyaudiorecorder.app;

import com.attaartechs.ezyaudiorecorder.audio.recorder.RecorderContract;

import java.util.List;

public interface AppRecorder {

	void addRecordingCallback(AppRecorderCallback recorderCallback);
	void removeRecordingCallback(AppRecorderCallback recorderCallback);
	void setRecorder(RecorderContract.Recorder recorder);
	void startRecording(String filePath);
	void pauseRecording();
	void stopRecording();
	List<Integer> getRecordingData();
	boolean isRecording();
	boolean isProcessing();
	void release();
}
