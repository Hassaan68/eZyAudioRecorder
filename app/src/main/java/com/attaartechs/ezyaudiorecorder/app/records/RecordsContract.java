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

import com.attaartechs.ezyaudiorecorder.Contract;

import java.util.List;

public interface RecordsContract {

	interface View extends Contract.View {

		void showPlayStart();
		void showPlayPause();
		void showPlayStop();
		void onPlayProgress(long mills, int px, int percent);

		void showNextRecord();
		void showPrevRecord();

		void showPlayerPanel();

		void startPlaybackService();
		void stopPlaybackService();

		void showWaveForm(int[] waveForm, long duration);
		void showDuration(String duration);

		void showRecords(List<ListItem> records, int order);
		void addRecords(List<ListItem> records, int order);

		void showEmptyList();
		void showEmptyBookmarksList();

		void showPanelProgress();
		void hidePanelProgress();

		void showRecordName(String name);

		void onDeleteRecord(long id);

		void hidePlayPanel();

		void addedToBookmarks(int id, boolean isActive);
		void removedFromBookmarks(int id, boolean isActive);

		void showSortType(int type);

		void bookmarksSelected();
		void bookmarksUnselected();

		void showRecordInfo(String name, String format, long duration, long size, String location);
	}

	interface UserActionsListener extends Contract.UserActionsListener<RecordsContract.View> {

		void startPlayback();

		void pausePlayback();

		void seekPlayback(int px);

		void stopPlayback();

		void playNext();

		void playPrev();

		void deleteActiveRecord();

		void deleteRecord(long id, String path);

		void renameRecord(long id, String name);

		void copyToDownloads(String path, String name);

		void loadRecords();

		void updateRecordsOrder(int order);

		void loadRecordsPage(int page);

		void applyBookmarksFilter();
		void checkBookmarkActiveRecord();

		void addToBookmark(int id);
		void removeFromBookmarks(int id);

		void setActiveRecord(long id, Callback callback);

		long getActiveRecordId();

		String getActiveRecordPath();

		String getRecordName();

		void onRecordInfo(String name, long duration, String location);
	}

	interface Callback {
		void onSuccess();
		void onError(Exception e);
	}
}
