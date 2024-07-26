/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.utils.writelog;

import org.apache.iotdb.db.storageengine.dataregion.tsfile.TsFileID;
import org.apache.iotdb.db.storageengine.dataregion.tsfile.timeindex.FileTimeIndex;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

public class PartitionLogReader {

  private final File logFile;
  private final long fileLength;
  private final int dataRegionId;
  private final long partitionId;

  public PartitionLogReader(File logFile, String dataRegionId, long partitionId)
      throws IOException {
    this.logFile = logFile;
    this.fileLength = logFile.length();
    this.dataRegionId = Integer.parseInt(dataRegionId);
    this.partitionId = partitionId;
  }

  public void read(Map<TsFileID, FileTimeIndex> fileTimeIndexMap) throws IOException {
    DataInputStream logStream =
        new DataInputStream(new BufferedInputStream(Files.newInputStream(logFile.toPath())));
    long readLength = 0L;
    while (readLength < fileLength) {
      long fileVersion = logStream.readLong();
      long compactionVersion = logStream.readLong();
      long minStartTime = logStream.readLong();
      long maxEndTime = logStream.readLong();
      TsFileID tsFileID = new TsFileID(dataRegionId, partitionId, fileVersion, compactionVersion);
      FileTimeIndex fileTimeIndex = new FileTimeIndex(minStartTime, maxEndTime);
      fileTimeIndexMap.put(tsFileID, fileTimeIndex);
      readLength += 4 * Long.BYTES;
    }
    logStream.close();
  }
}
