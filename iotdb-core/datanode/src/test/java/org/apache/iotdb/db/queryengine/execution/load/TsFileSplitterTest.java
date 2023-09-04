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

package org.apache.iotdb.db.queryengine.execution.load;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class TsFileSplitterTest extends TestBase{
  private List<TsFileData> resultSet = new ArrayList<>();

  @Test
  public void testSplit() throws IOException {
    long start = System.currentTimeMillis();
    for (File file : files) {
      TsFileSplitter splitter = new TsFileSplitter(file, this::consumeSplit);
      splitter.splitTsFileByDataPartition();
    }
    for (TsFileData tsFileData : resultSet) {
      // System.out.println(tsFileData);
    }
    System.out.printf("%d splits after %dms\n", resultSet.size(), System.currentTimeMillis() - start);
  }

  public boolean consumeSplit(TsFileData data) {
    resultSet.add(data);
    return true;
  }
}
