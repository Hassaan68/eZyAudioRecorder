/*
 * Copyright 2018 Dmitriy Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.attaartechs.ezyaudiorecorder.app.main;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import com.attaartechs.ezyaudiorecorder.ARApplication;
import com.attaartechs.ezyaudiorecorder.AppConstants;
import com.attaartechs.ezyaudiorecorder.BackgroundQueue;
import com.attaartechs.ezyaudiorecorder.R;
import com.attaartechs.ezyaudiorecorder.app.AppRecorder;
import com.attaartechs.ezyaudiorecorder.app.AppRecorderCallback;
import com.attaartechs.ezyaudiorecorder.audio.player.PlayerContract;
import com.attaartechs.ezyaudiorecorder.audio.recorder.RecorderContract;
import com.attaartechs.ezyaudiorecorder.data.FileRepository;
import com.attaartechs.ezyaudiorecorder.data.Prefs;
import com.attaartechs.ezyaudiorecorder.data.database.LocalRepository;
import com.attaartechs.ezyaudiorecorder.data.database.Record;
import com.attaartechs.ezyaudiorecorder.exception.AppException;
import com.attaartechs.ezyaudiorecorder.exception.CantCreateFileException;
import com.attaartechs.ezyaudiorecorder.exception.ErrorParser;
import com.attaartechs.ezyaudiorecorder.util.AndroidUtils;
import com.attaartechs.ezyaudiorecorder.util.FileUtil;
import com.attaartechs.ezyaudiorecorder.util.TimeUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Date;

import timber.log.Timber;

public class MainPresenter implements MainContract.UserActionsListener {

	private MainContract.View view;
	private AppRecorder appRecorder;
	private final PlayerContract.Player audioPlayer;
	private PlayerContract.PlayerCallback playerCallback;
	private AppRecorderCallback appRecorderCallback;
	private final BackgroundQueue loadingTasks;
	private final BackgroundQueue recordingsTasks;
	private final BackgroundQueue importTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final Prefs prefs;
	private long songDuration = 0;
	private float dpPerSecond = AppConstants.SHORT_RECORD_DP_PER_SECOND;
	private Record record;
	private boolean isProcessing = false;

	/** Flag true defines that presenter called to show import progress when view was not bind.
	 * And after view bind we need to show import progress.*/
	private boolean showImportProgress = false;

	public MainPresenter(final Prefs prefs, final FileRepository fileRepository,
								final LocalRepository localRepository,
								PlayerContract.Player audioPlayer,
								AppRecorder appRecorder,
								final BackgroundQueue recordingTasks,
								final BackgroundQueue loadingTasks,
								final BackgroundQueue importTasks) {
		this.prefs = prefs;
		this.fileRepository = fileRepository;
		this.localRepository = localRepository;
		this.loadingTasks = loadingTasks;
		this.recordingsTasks = recordingTasks;
		this.importTasks = importTasks;
		this.audioPlayer = audioPlayer;
		this.appRecorder = appRecorder;
	}

	@Override
	public void bindView(final MainContract.View v) {
		this.view = v;
		if (showImportProgress) {
			view.showImportStart();
		} else {
			view.hideImportProgress();
		}

		if (!prefs.hasAskToRenameAfterStopRecordingSetting()) {
			prefs.setAskToRenameAfterStopRecording(true);
		}

		if (appRecorder.isRecording()) {
			view.showRecordingStart();
			view.keepScreenOn(prefs.isKeepScreenOn());
			view.updateRecordingView(appRecorder.getRecordingData());
		} else {
			view.showRecordingStop();
			view.keepScreenOn(false);
		}
		if (appRecorder.isProcessing()) {
			view.showRecordProcessing();
		} else {
			view.hideRecordProcessing();
		}

		if (appRecorderCallback == null) {
			appRecorderCallback = new AppRecorderCallback() {
				@Override
				public void onRecordingStarted() {
					if (view != null) {
						view.showRecordingStart();
						view.keepScreenOn(prefs.isKeepScreenOn());
						view.showName("");
						view.startRecordingService();
					}
				}

				@Override
				public void onRecordingPaused() {
					view.keepScreenOn(false);
				}

				@Override
				public void onRecordProcessing() {
					if (view != null) {
						view.showProgress();
						view.showRecordProcessing();
					}
				}

				@Override
				public void onRecordFinishProcessing() {
					if (view != null) {
						view.hideRecordProcessing();
						loadActiveRecord();
					}
				}

				@Override
				public void onRecordingStopped(long id, File file) {
					if (view != null) {
						view.keepScreenOn(false);
						view.stopRecordingService();
						view.hideProgress();
						view.showRecordingStop();
						loadActiveRecord();
						if (prefs.isAskToRenameAfterStopRecording()) {
							view.askRecordingNewName(id, file);
						}
					}
				}

				@Override
				public void onRecordingProgress(final long mills, final int amp) {
					Timber.v("onRecordProgress time = %d, apm = %d", mills, amp);
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.onRecordingProgress(mills, amp);
							}
						}
					});
				}

				@Override
				public void onError(AppException throwable) {
					Timber.e(throwable);
					if (view != null) {
						view.showError(ErrorParser.parseException(throwable));
						view.showRecordingStop();
					}
				}
			};
		}
		appRecorder.addRecordingCallback(appRecorderCallback);

		if (playerCallback == null) {
			playerCallback = new PlayerContract.PlayerCallback() {
				@Override
				public void onPreparePlay() {
				}

				@Override
				public void onStartPlay() {
					if (view != null) {
						view.showPlayStart(true);
						view.startPlaybackService(record.getName());
					}
				}

				@Override
				public void onPlayProgress(final long mills) {
					if (view != null) {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override public void run() {
								if (view != null) {
									long duration = songDuration/1000;
									if (duration > 0) {
										view.onPlayProgress(mills, AndroidUtils.convertMillsToPx(mills,
												AndroidUtils.dpToPx(dpPerSecond)), (int) (1000 * mills / duration));
									}
								}
							}});
					}
				}

				@Override
				public void onStopPlay() {
					if (view != null) {
						view.showPlayStop();
						view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000));
					}
				}

				@Override
				public void onPausePlay() {
					if (view != null) {
						view.showPlayPause();
					}
				}

				@Override
				public void onSeek(long mills) {
				}

				@Override
				public void onError(AppException throwable) {
					Timber.e(throwable);
					if (view != null) {
						view.showError(ErrorParser.parseException(throwable));
					}
				}
			};
		}

		this.audioPlayer.addPlayerCallback(playerCallback);

		if (audioPlayer.isPlaying()) {
			view.showPlayStart(false);
		} else {
			view.showPlayStop();
		}
	}

	@Override
	public void unbindView() {
		if (view != null) {
			audioPlayer.removePlayerCallback(playerCallback);
			appRecorder.removeRecordingCallback(appRecorderCallback);
			this.view.stopPlaybackService();
			this.view = null;
		}
	}

	@Override
	public void clear() {
		if (view != null) {
			unbindView();
		}
		localRepository.close();
		audioPlayer.release();
		appRecorder.release();
		loadingTasks.close();
		recordingsTasks.close();
	}

	@Override
	public void executeFirstRun() {
		if (prefs.isFirstRun()) {
			prefs.firstRunExecuted();
		}
	}

	@Override
	public void setAudioRecorder(RecorderContract.Recorder recorder) {
		appRecorder.setRecorder(recorder);
	}

	@Override
	public void startRecording() {
		if (audioPlayer.isPlaying()) {
			audioPlayer.stop();
		}
		if (!appRecorder.isRecording()) {
			try {
				appRecorder.startRecording(fileRepository.provideRecordFile().getAbsolutePath());
			} catch (CantCreateFileException e) {
				if (view != null) {
					view.showError(ErrorParser.parseException(e));
				}
			}
		} else {
			appRecorder.pauseRecording();
		}
	}

	@Override
	public void stopRecording() {
		if (appRecorder.isRecording()) {
			appRecorder.stopRecording();
		}
	}

	@Override
	public void startPlayback() {
		if (record != null) {
			if (!audioPlayer.isPlaying()) {
				audioPlayer.setData(record.getPath());
			}
			audioPlayer.playOrPause();
		}
	}

	@Override
	public void pausePlayback() {
		if (audioPlayer.isPlaying()) {
			audioPlayer.pause();
		}
	}

	@Override
	public void seekPlayback(int px) {
		audioPlayer.seek(AndroidUtils.convertPxToMills(px, AndroidUtils.dpToPx(dpPerSecond)));
	}

	@Override
	public void stopPlayback() {
		audioPlayer.stop();
	}

	@Override
	public void renameRecord(final long id, final String n) {
		if (id < 0 || n == null || n.isEmpty()) {
			AndroidUtils.runOnUIThread(new Runnable() {
				@Override public void run() {
					if (view != null) {
						view.showError(R.string.error_failed_to_rename);
					}
				}});
			return;
		}
		if (view != null) {
			view.showProgress();
		}
		final String name = FileUtil.removeUnallowedSignsFromName(n);
		loadingTasks.postRunnable(new Runnable() {
			@Override public void run() {
//				TODO: This code need to be refactored!
				Record record = localRepository.getRecord((int)id);
				if (record != null) {
					String nameWithExt;
					if (prefs.getFormat() == AppConstants.RECORDING_FORMAT_WAV) {
						nameWithExt = name + AppConstants.EXTENSION_SEPARATOR + AppConstants.WAV_EXTENSION;
					} else {
						nameWithExt = name + AppConstants.EXTENSION_SEPARATOR + AppConstants.M4A_EXTENSION;
					}

					File file = new File(record.getPath());
					File renamed = new File(file.getParentFile().getAbsolutePath() + File.separator + nameWithExt);

					if (renamed.exists()) {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.showError(R.string.error_file_exists);
								}
							}
						});
					} else {
						String ext;
						if (prefs.getFormat() == AppConstants.RECORDING_FORMAT_WAV) {
							ext = AppConstants.WAV_EXTENSION;
						} else {
							ext = AppConstants.M4A_EXTENSION;
						}
						if (fileRepository.renameFile(record.getPath(), name, ext)) {
							MainPresenter.this.record = new Record(record.getId(), nameWithExt, record.getDuration(), record.getCreated(),
									record.getAdded(), renamed.getAbsolutePath(), record.isBookmarked(),
									record.isWaveformProcessed(), record.getAmps());
							if (localRepository.updateRecord(MainPresenter.this.record)) {
								AndroidUtils.runOnUIThread(new Runnable() {
									@Override
									public void run() {
										if (view != null) {
											view.hideProgress();
											view.showName(name);
										}
									}
								});
							} else {
								AndroidUtils.runOnUIThread(new Runnable() {
									@Override
									public void run() {
										view.showError(R.string.error_failed_to_rename);
									}
								});
								//Restore file name after fail update path in local database.
								if (renamed.exists()) {
									//Try to rename 3 times;
									if (!renamed.renameTo(file)) {
										if (!renamed.renameTo(file)) {
											renamed.renameTo(file);
										}
									}
								}
							}

						} else {
							AndroidUtils.runOnUIThread(new Runnable() {
								@Override
								public void run() {
									if (view != null) {
										view.showError(R.string.error_failed_to_rename);
									}
								}
							});
						}
					}
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.hideProgress();
							}
						}
					});
				}
			}
		});
	}

	@Override
	public void loadActiveRecord() {
		if (!appRecorder.isRecording()) {
			view.showProgress();
			loadingTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					final Record rec = localRepository.getRecord((int) prefs.getActiveRecord());
					record = rec;
					if (rec != null) {
						songDuration = rec.getDuration();
						dpPerSecond = ARApplication.getDpPerSecond((float) songDuration / 1000000f);
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.showWaveForm(rec.getAmps(), songDuration);
									view.showName(FileUtil.removeFileExtension(rec.getName()));
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000));
									view.showOptionsMenu();
									view.hideProgress();
								}
							}
						});
						if (!rec.isWaveformProcessed() && !isProcessing) {
							try {
								if (view != null) {
									AndroidUtils.runOnUIThread(new Runnable() {
										@Override
										public void run() {
											if (view != null) {
												view.showRecordProcessing();
											}
										}
									});
									isProcessing = true;
									localRepository.updateWaveform(rec.getId());
									AndroidUtils.runOnUIThread(new Runnable() {
										@Override
										public void run() {
											if (view != null) {
												view.hideRecordProcessing();
											}
										}
									});
								}
							} catch (IOException | OutOfMemoryError | IllegalStateException e) {
								Timber.e(e);
								AndroidUtils.runOnUIThread(new Runnable() {
									@Override
									public void run() {
										if (view != null) {
											view.showError(R.string.error_process_waveform);
										}
									}
								});
							}
							isProcessing = false;
						}
					} else {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.showWaveForm(new int[]{}, 0);
									view.showName("");
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(0));
									view.hideProgress();
									view.hideOptionsMenu();
								}
							}
						});
					}
				}
			});
		}
	}

	@Override
	public void updateRecordingDir(Context context) {
		fileRepository.updateRecordingDir(context, prefs);
	}

	@Override
	public void setStoragePrivate(Context context) {
		prefs.setStoreDirPublic(false);
		fileRepository.updateRecordingDir(context, prefs);
	}

	@Override
	public boolean isStorePublic() {
		return prefs.isStoreDirPublic();
	}

	@Override
	public String getActiveRecordPath() {
		if (record != null) {
			return record.getPath();
		} else {
			return null;
		}
	}

	@Override
	public String getActiveRecordName() {
		if (record != null) {
			return record.getName();
		} else {
			return null;
		}
	}

	@Override
	public int getActiveRecordId() {
		if (record != null) {
			return record.getId();
		} else {
			return -1;
		}
	}

	@Override
	public void deleteActiveRecord() {
		if (record != null) {
			audioPlayer.stop();
		}
		recordingsTasks.postRunnable(new Runnable() {
			@Override public void run() {
				if (record != null) {
					localRepository.deleteRecord(record.getId());
					fileRepository.deleteRecordFile(record.getPath());
					prefs.setActiveRecord(-1);
					dpPerSecond = AppConstants.SHORT_RECORD_DP_PER_SECOND;
				}
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override
					public void run() {
						if (view != null) {
							view.showWaveForm(new int[]{}, 0);
							view.showName("");
							view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(0));
							view.showMessage(R.string.record_deleted_successfully);
							view.hideProgress();
							record = null;
						}
					}
				});
			}
		});
	}

	@Override
	public void onRecordInfo() {
		String format;
		Record rec = record;
		if (rec != null) {
			if (rec.getPath().contains(AppConstants.M4A_EXTENSION)) {
				format = AppConstants.M4A_EXTENSION;
			} else if (rec.getPath().contains(AppConstants.WAV_EXTENSION)) {
				format = AppConstants.WAV_EXTENSION;
			} else {
				format = "";
			}
			view.showRecordInfo(rec.getName(), format, rec.getDuration() / 1000, new File(rec.getPath()).length(), rec.getPath());
		}
	}

	@Override
	public void importAudioFile(final Context context, final Uri uri) {
		if (view != null) {
			view.showImportStart();
		}
		showImportProgress = true;

		importTasks.postRunnable(new Runnable() {
			long id = -1;

			@Override
			public void run() {
				try {
					ParcelFileDescriptor parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
					FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
					String name = extractFileName(context, uri);

					File newFile = fileRepository.provideRecordFile(name);
					if (FileUtil.copyFile(fileDescriptor, newFile)) {
						long duration = AndroidUtils.readRecordDuration(newFile);
						if (duration/1000000 < AppConstants.LONG_RECORD_THRESHOLD_SECONDS) {
							//Do simple import for short records.
							id = localRepository.insertFile(newFile.getAbsolutePath());
							prefs.setActiveRecord(id);
							AndroidUtils.runOnUIThread(new Runnable() {
								@Override public void run() {
									if (view != null) {
										view.hideImportProgress();
										audioPlayer.stop();
										loadActiveRecord();
									}
								}
							});
						} else {
							//Do 2 step import: 1) Import record with empty waveform. 2) Process and update waveform in background.
							record = localRepository.insertRecord(
									new Record(
											Record.NO_ID,
											newFile.getName(),
											duration, //mills
											newFile.lastModified(),
											new Date().getTime(),
											newFile.getAbsolutePath(),
											false,
											true,
											new int[ARApplication.getLongWaveformSampleCount()]));

							final Record rec = record;
							if (rec != null) {
								id = rec.getId();
								prefs.setActiveRecord(id);
								songDuration = duration;
								dpPerSecond = ARApplication.getDpPerSecond((float) songDuration / 1000000f);
								AndroidUtils.runOnUIThread(new Runnable() {
									@Override
									public void run() {
										if (view != null) {
											audioPlayer.stop();
											view.showWaveForm(rec.getAmps(), songDuration);
											view.showName(FileUtil.removeFileExtension(rec.getName()));
											view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(songDuration / 1000));
											view.hideProgress();
										}
									}
								});
							}

							try {
								if (view != null) {
									AndroidUtils.runOnUIThread(new Runnable() {
										@Override
										public void run() {
											if (view != null) {
												view.hideImportProgress();
												view.showRecordProcessing();
											}
										}
									});
									isProcessing = true;
									localRepository.updateWaveform((int) id);
									record = localRepository.getRecord((int) id);
									final Record rec2 = record;
									if (rec2 != null) {
										AndroidUtils.runOnUIThread(new Runnable() {
											@Override
											public void run() {
												if (view != null) {
													view.showWaveForm(rec2.getAmps(), songDuration);
													view.hideRecordProcessing();
												}
											}
										});
									}
								}
							} catch (IOException | OutOfMemoryError | IllegalStateException e) {
								Timber.e(e);
								if (view != null) {
									view.hideRecordProcessing();
									view.showError(R.string.error_process_waveform);
								}
							}
							isProcessing = false;
						}
					}
				} catch (SecurityException e) {
					Timber.e(e);
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override public void run() { if (view != null) view.showError(R.string.error_permission_denied); }
					});
				} catch (IOException | OutOfMemoryError | IllegalStateException e) {
					Timber.e(e);
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override public void run() { if (view != null) view.showError(R.string.error_unable_to_read_sound_file); }
					});
				} catch (final CantCreateFileException ex) {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override public void run() { if (view != null) view.showError(ErrorParser.parseException(ex)); }
					});
				}
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override public void run() {
						if (view != null) { view.hideImportProgress(); }
					}});
				showImportProgress = false;
			}
		});
	}

	private String extractFileName(Context context, Uri uri) {
		Cursor cursor = context.getContentResolver().query(uri, null, null, null, null, null);
		try {
			if (cursor != null && cursor.moveToFirst()) {
				String name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
//				TODO: find a better way to extract file extension.
				if (!name.contains(".")) {
					return name + ".m4a";
				}
				return name;
			}
		} finally {
			cursor.close();
		}
		return null;
	}
}
