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

package com.attaartechs.ezyaudiorecorder.app.records;

import android.os.Environment;
import com.attaartechs.ezyaudiorecorder.ARApplication;
import com.attaartechs.ezyaudiorecorder.AppConstants;
import com.attaartechs.ezyaudiorecorder.BackgroundQueue;
import com.attaartechs.ezyaudiorecorder.Mapper;
import com.attaartechs.ezyaudiorecorder.R;
import com.attaartechs.ezyaudiorecorder.app.AppRecorder;
import com.attaartechs.ezyaudiorecorder.app.AppRecorderCallback;
import com.attaartechs.ezyaudiorecorder.audio.player.PlayerContract;
import com.attaartechs.ezyaudiorecorder.data.FileRepository;
import com.attaartechs.ezyaudiorecorder.data.Prefs;
import com.attaartechs.ezyaudiorecorder.data.database.LocalRepository;
import com.attaartechs.ezyaudiorecorder.data.database.Record;
import com.attaartechs.ezyaudiorecorder.exception.AppException;
import com.attaartechs.ezyaudiorecorder.exception.ErrorParser;
import com.attaartechs.ezyaudiorecorder.util.AndroidUtils;
import com.attaartechs.ezyaudiorecorder.util.FileUtil;
import com.attaartechs.ezyaudiorecorder.util.TimeUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import timber.log.Timber;

public class RecordsPresenter implements RecordsContract.UserActionsListener {

	private RecordsContract.View view;
	private final PlayerContract.Player audioPlayer;
	private AppRecorder appRecorder;
	private PlayerContract.PlayerCallback playerCallback;
	private AppRecorderCallback appRecorderCallback;
	private final BackgroundQueue loadingTasks;
	private final BackgroundQueue recordingsTasks;
	private final BackgroundQueue copyTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final Prefs prefs;

	private Record activeRecord;
	private float dpPerSecond = AppConstants.SHORT_RECORD_DP_PER_SECOND;
	private boolean showBookmarks = false;

	public RecordsPresenter(final LocalRepository localRepository, FileRepository fileRepository,
									BackgroundQueue loadingTasks, BackgroundQueue recordingsTasks, BackgroundQueue copyTasks,
									PlayerContract.Player player, AppRecorder appRecorder, Prefs prefs) {
		this.localRepository = localRepository;
		this.fileRepository = fileRepository;
		this.loadingTasks = loadingTasks;
		this.recordingsTasks = recordingsTasks;
		this.copyTasks = copyTasks;
		this.audioPlayer = player;
		this.appRecorder = appRecorder;
		this.playerCallback = null;
		this.prefs = prefs;
	}

	@Override
	public void bindView(final RecordsContract.View v) {
		this.view = v;

		if (appRecorderCallback == null) {
			appRecorderCallback = new AppRecorderCallback() {
				@Override public void onRecordingStarted() {}
				@Override public void onRecordingPaused() {}
				@Override public void onRecordProcessing() {}

				@Override
				public void onRecordFinishProcessing() {
					loadRecords();
				}

				@Override public void onRecordingProgress(long mills, int amp) {}

				@Override
				public void onRecordingStopped(long id, File file) {
					loadRecords();
				}

				@Override
				public void onError(AppException e) {
					view.showError(ErrorParser.parseException(e));
				}
			};
		}
		appRecorder.addRecordingCallback(appRecorderCallback);

		if (playerCallback == null) {
			this.playerCallback = new PlayerContract.PlayerCallback() {

				@Override
				public void onPreparePlay() {
					Timber.d("onPreparePlay");
				}

				@Override
				public void onStartPlay() {
					if (view != null) {
						view.showPlayStart();
						view.startPlaybackService();
					}
				}

				@Override
				public void onPlayProgress(final long mills) {
					if (view != null) {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override public void run() {
								Record rec = activeRecord;
								if (view != null && rec != null) {
									long duration = rec.getDuration()/1000;
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
		audioPlayer.addPlayerCallback(playerCallback);
		if (audioPlayer.isPlaying()) {
			if (view != null) {
				view.showPlayerPanel();
				view.showPlayStart();
			}
		}
		if (view != null) {
			view.showSortType(prefs.getRecordsOrder());
		}
	}

	@Override
	public void unbindView() {
		if (view != null) {
			audioPlayer.removePlayerCallback(playerCallback);
			appRecorder.removeRecordingCallback(appRecorderCallback);
			this.view = null;
		}
	}

	@Override
	public void clear() {
		if (view != null) {
			unbindView();
		}
	}

	@Override
	public void startPlayback() {
		if (!appRecorder.isRecording()) {
			if (activeRecord != null) {
				if (!audioPlayer.isPlaying()) {
					audioPlayer.setData(activeRecord.getPath());
				}
				audioPlayer.playOrPause();
			}
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
	public void playNext() {
	}

	@Override
	public void playPrev() {
	}

	@Override
	public void deleteActiveRecord() {
		if (activeRecord != null) {
			deleteRecord(activeRecord.getId(), activeRecord.getPath());
		}
	}

	@Override
	public void deleteRecord(final long id, final String path) {
		final Record rec = activeRecord;
		if (rec != null) {
			if (rec.getId() == id) {
				audioPlayer.stop();
			}
			recordingsTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					localRepository.deleteRecord((int) id);
					fileRepository.deleteRecordFile(path);
					if (rec.getId() == id) {
						prefs.setActiveRecord(-1);
						dpPerSecond = AppConstants.SHORT_RECORD_DP_PER_SECOND;
					}
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.onDeleteRecord(id);
								if (rec.getId() == id) {
									view.hidePlayPanel();
									view.showMessage(R.string.record_deleted_successfully);
									activeRecord = null;
								}
							}
						}
					});
				}
			});
		}
	}

	@Override
	public void renameRecord(final long id, String n) {
		if (id < 0 || n == null || n.isEmpty()) {
			AndroidUtils.runOnUIThread(new Runnable() {
				@Override public void run() {
					if (view != null) {
						view.showError(R.string.error_failed_to_rename);
					}
				}});
			return;
		}
		view.showProgress();
		final String name = FileUtil.removeUnallowedSignsFromName(n);
		loadingTasks.postRunnable(new Runnable() {
			@Override public void run() {
//				TODO: This code need to be refactored!
				Record r = localRepository.getRecord((int)id);
//				String nameWithExt = name + AppConstants.EXTENSION_SEPARATOR + AppConstants.M4A_EXTENSION;
				String nameWithExt;
				if (prefs.getFormat() == AppConstants.RECORDING_FORMAT_WAV) {
					nameWithExt = name + AppConstants.EXTENSION_SEPARATOR + AppConstants.WAV_EXTENSION;
				} else {
					nameWithExt = name + AppConstants.EXTENSION_SEPARATOR + AppConstants.M4A_EXTENSION;
				}
				File file = new File(r.getPath());
				File renamed = new File(file.getParentFile().getAbsolutePath() + File.separator + nameWithExt);

				if (renamed.exists()) {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override public void run() {
							if (view != null) {
								view.showError(R.string.error_file_exists);
							}
						}});
				} else {
					String ext;
					if (prefs.getFormat() == AppConstants.RECORDING_FORMAT_WAV) {
						ext = AppConstants.WAV_EXTENSION;
					} else {
						ext = AppConstants.M4A_EXTENSION;
					}
					if (fileRepository.renameFile(r.getPath(), name, ext)) {
						activeRecord = new Record(r.getId(), nameWithExt, r.getDuration(), r.getCreated(),
								r.getAdded(), renamed.getAbsolutePath(), r.isBookmarked(),
								r.isWaveformProcessed(), r.getAmps());
						if (localRepository.updateRecord(activeRecord)) {
							AndroidUtils.runOnUIThread(new Runnable() {
								@Override public void run() {
									if (view != null) {
										view.hideProgress();
										view.showRecordName(name);
									}
								}});
						} else {
							AndroidUtils.runOnUIThread(new Runnable() {
								@Override public void run() {
									if (view != null) {
										view.showError(R.string.error_failed_to_rename);
									}
								}});
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
							@Override public void run() {
								if (view != null) {
									view.showError(R.string.error_failed_to_rename);
								}
							}});
					}
				}
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override public void run() {
						if (view != null) {
							view.hideProgress();
						}
					}});
			}});
	}

	@Override
	public void copyToDownloads(final String path, final String name) {
		if (view != null) {
			//TODO: show copy progress
			copyTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					try {
						FileUtil.copyFile(new File(path), FileUtil.createFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name));
						//TODO: show success result
					} catch (IOException e) {
						Timber.v(e);
						//TODO: show copy error
					}
					//TODO:hide progress
				}
			});
		}
	}

	@Override
	public void loadRecords() {
		if (view != null) {
			view.showProgress();
			view.showPanelProgress();
			loadingTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					final int order = prefs.getRecordsOrder();
					final List<Record> recordList = localRepository.getRecords(0, order);
					final Record rec = localRepository.getRecord((int) prefs.getActiveRecord());
					activeRecord = rec;
					if (rec != null) {
						dpPerSecond = ARApplication.getDpPerSecond((float) rec.getDuration() / 1000000f);
					}
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.showRecords(Mapper.recordsToListItems(recordList), order);
								if (rec != null) {
									view.showWaveForm(rec.getAmps(), rec.getDuration());
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(rec.getDuration() / 1000));
									view.showRecordName(FileUtil.removeFileExtension(rec.getName()));
									if (rec.isBookmarked()) {
										view.bookmarksSelected();
									} else {
										view.bookmarksUnselected();
									}
								}
								view.hideProgress();
								view.hidePanelProgress();
								view.bookmarksUnselected();
								if (recordList.size() == 0) {
									view.showEmptyList();
								}
							}
						}
					});
				}
			});
		}
	}

	@Override
	public void updateRecordsOrder(int order) {
		prefs.setRecordOrder(order);
		view.showSortType(order);
		loadRecords();
	}

	@Override
	public void loadRecordsPage(final int page) {
		if (view != null) {
			view.showProgress();
			view.showPanelProgress();
			loadingTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					final int order = prefs.getRecordsOrder();
					final List<Record> recordList = localRepository.getRecords(page, order);
					final Record rec = localRepository.getRecord((int) prefs.getActiveRecord());
					activeRecord = rec;
					if (rec != null) {
						dpPerSecond = ARApplication.getDpPerSecond((float) rec.getDuration() / 1000000f);
					}
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								if (rec != null) {
									view.addRecords(Mapper.recordsToListItems(recordList), order);
									view.showWaveForm(rec.getAmps(), rec.getDuration());
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(rec.getDuration() / 1000));
									view.showRecordName(FileUtil.removeFileExtension(rec.getName()));
									if (rec.isBookmarked()) {
										view.bookmarksSelected();
									} else {
										view.bookmarksUnselected();
									}
								}
								view.hideProgress();
								view.hidePanelProgress();
								view.bookmarksUnselected();
							}
						}
					});
				}
			});
		}
	}

	public void loadBookmarks() {
		if (!showBookmarks) {
			loadRecords();
		} else {
			if (view != null) {
				view.showProgress();
				view.showPanelProgress();
				loadingTasks.postRunnable(new Runnable() {
					@Override
					public void run() {
						final List<Record> recordList = localRepository.getBookmarks();
						final Record rec = localRepository.getRecord((int) prefs.getActiveRecord());
						activeRecord = rec;
						if (rec != null) {
							dpPerSecond = ARApplication.getDpPerSecond((float) rec.getDuration() / 1000000f);
						}
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.showRecords(Mapper.recordsToListItems(recordList), AppConstants.SORT_DATE);
									if (rec != null) {
										view.showWaveForm(rec.getAmps(), rec.getDuration());
										view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(rec.getDuration() / 1000));
										view.showRecordName(FileUtil.removeFileExtension(rec.getName()));
									}
									view.hideProgress();
									view.hidePanelProgress();
									view.bookmarksSelected();
									if (recordList.size() == 0) {
										view.showEmptyBookmarksList();
									}
								}
							}
						});
					}
				});
			}
		}
	}

	@Override
	public void applyBookmarksFilter() {
		showBookmarks = !showBookmarks;
		loadBookmarks();
	}

	@Override
	public void checkBookmarkActiveRecord() {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				final Record rec = activeRecord;
				if (rec != null) {
					boolean success;
					if (rec.isBookmarked()) {
						success = localRepository.removeFromBookmarks(rec.getId());
					} else {
						success = localRepository.addToBookmarks(rec.getId());
					}
					if (success) {
						rec.setBookmark(!rec.isBookmarked());

						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									if (rec.isBookmarked()) {
										view.addedToBookmarks(rec.getId(), true);
									} else {
										view.removedFromBookmarks(rec.getId(), true);
									}
								}
							}
						});
					}
				}
			}
		});
	}

	@Override
	public void addToBookmark(final int id) {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				final Record r = localRepository.getRecord(id);
				if (r != null) {
					if (localRepository.addToBookmarks(r.getId())) {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.addedToBookmarks(r.getId(), activeRecord != null && r.getId() == activeRecord.getId());
								}
							}
						});
					}
				}
			}
		});
	}

	@Override
	public void removeFromBookmarks(final int id) {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				final Record r = localRepository.getRecord(id);
				if (r != null) {
					localRepository.removeFromBookmarks(r.getId());
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.removedFromBookmarks(r.getId(), activeRecord != null && r.getId() == activeRecord.getId());
							}
						}
					});
				}
			}
		});
	}

	@Override
	public void setActiveRecord(final long id, final RecordsContract.Callback callback) {
		if (id >= 0 && !appRecorder.isRecording()) {
			prefs.setActiveRecord(id);
			if (view != null) {
				view.showPanelProgress();
			}
			loadingTasks.postRunnable(new Runnable() {
				@Override
				public void run() {
					final Record rec = localRepository.getRecord((int) id);
					activeRecord = rec;
					if (rec != null) {
						dpPerSecond = ARApplication.getDpPerSecond((float) rec.getDuration()/1000000f);
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								if (view != null) {
									view.showWaveForm(rec.getAmps(), rec.getDuration());
									view.showDuration(TimeUtils.formatTimeIntervalHourMinSec2(rec.getDuration() / 1000));
									view.showRecordName(FileUtil.removeFileExtension(rec.getName()));
									callback.onSuccess();
									if (rec.isBookmarked()) {
										view.addedToBookmarks(rec.getId(), true);
									} else {
										view.removedFromBookmarks(rec.getId(), true);
									}
									view.hidePanelProgress();
									view.showPlayerPanel();
								}
							}
						});
					} else {
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								callback.onError(new Exception("Record is NULL!"));
								if (view != null) {
									view.hidePanelProgress();
								}
							}
						});
					}
				}
			});
		}
	}

	@Override
	public long getActiveRecordId() {
		return prefs.getActiveRecord();
	}

	@Override
	public String getActiveRecordPath() {
		if (activeRecord != null) {
			return activeRecord.getPath();
		} else {
			return null;
		}
	}

	@Override
	public String getRecordName() {
		if (activeRecord != null) {
			return activeRecord.getName();
		} else {
			return "Record";
		}
	}

	@Override
	public void onRecordInfo(String name, long duration, String location) {
		String format;
		if (location.contains(AppConstants.M4A_EXTENSION)) {
			format = AppConstants.M4A_EXTENSION;
		} else if (location.contains(AppConstants.WAV_EXTENSION)) {
			format = AppConstants.WAV_EXTENSION;
		} else {
			format = "";
		}
		view.showRecordInfo(name, format, duration, new File(location).length(), location);
	}
}
