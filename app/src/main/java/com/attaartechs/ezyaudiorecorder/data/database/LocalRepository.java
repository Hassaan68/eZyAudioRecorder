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

package com.attaartechs.ezyaudiorecorder.data.database;

import java.io.IOException;
import java.util.List;

public interface LocalRepository {

	void open();

	void close();

	Record getRecord(int id);

	List<Record> getAllRecords();

	List<Record> getRecords(int page);

	List<Record> getRecords(int page, int order);

	boolean deleteAllRecords();

	Record getLastRecord();

	Record insertRecord(Record record);

	boolean updateRecord(Record record);

	long insertFile(String filePath) throws IOException;

	long insertFile(String filePath, long duration, int[] waveform) throws IOException;

	boolean updateWaveform(int id) throws IOException, OutOfMemoryError, IllegalStateException;

	void deleteRecord(int id);

	List<Long> getRecordsDurations();

	boolean addToBookmarks(int id);

	boolean removeFromBookmarks(int id);

	List<Record> getBookmarks();
}
