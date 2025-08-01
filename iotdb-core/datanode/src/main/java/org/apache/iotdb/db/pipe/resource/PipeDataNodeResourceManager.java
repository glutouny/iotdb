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

package org.apache.iotdb.db.pipe.resource;

import org.apache.iotdb.commons.pipe.resource.log.PipeLogManager;
import org.apache.iotdb.commons.pipe.resource.ref.PipePhantomReferenceManager;
import org.apache.iotdb.commons.pipe.resource.snapshot.PipeSnapshotResourceManager;
import org.apache.iotdb.db.pipe.resource.memory.PipeMemoryManager;
import org.apache.iotdb.db.pipe.resource.ref.PipeDataNodePhantomReferenceManager;
import org.apache.iotdb.db.pipe.resource.snapshot.PipeDataNodeSnapshotResourceManager;
import org.apache.iotdb.db.pipe.resource.tsfile.PipeCompactionManager;
import org.apache.iotdb.db.pipe.resource.tsfile.PipeTsFileResourceManager;

public class PipeDataNodeResourceManager {

  private final PipeTsFileResourceManager pipeTsFileResourceManager;
  private final PipeCompactionManager pipeCompactionManager;
  private final PipeSnapshotResourceManager pipeSnapshotResourceManager;
  private final PipeMemoryManager pipeMemoryManager;
  private final PipeLogManager pipeLogManager;
  private final PipePhantomReferenceManager pipePhantomReferenceManager;

  public static PipeTsFileResourceManager tsfile() {
    return PipeResourceManagerHolder.INSTANCE.pipeTsFileResourceManager;
  }

  public static PipeCompactionManager compaction() {
    return PipeResourceManagerHolder.INSTANCE.pipeCompactionManager;
  }

  public static PipeSnapshotResourceManager snapshot() {
    return PipeResourceManagerHolder.INSTANCE.pipeSnapshotResourceManager;
  }

  public static PipeMemoryManager memory() {
    return PipeResourceManagerHolder.INSTANCE.pipeMemoryManager;
  }

  public static PipeLogManager log() {
    return PipeResourceManagerHolder.INSTANCE.pipeLogManager;
  }

  public static PipePhantomReferenceManager ref() {
    return PipeResourceManagerHolder.INSTANCE.pipePhantomReferenceManager;
  }

  ///////////////////////////// SINGLETON /////////////////////////////

  private PipeDataNodeResourceManager() {
    pipeTsFileResourceManager = new PipeTsFileResourceManager();
    pipeCompactionManager = new PipeCompactionManager();
    pipeSnapshotResourceManager = new PipeDataNodeSnapshotResourceManager();
    pipeMemoryManager = new PipeMemoryManager();
    pipeLogManager = new PipeLogManager();
    pipePhantomReferenceManager = new PipeDataNodePhantomReferenceManager();
  }

  private static class PipeResourceManagerHolder {
    private static final PipeDataNodeResourceManager INSTANCE = new PipeDataNodeResourceManager();
  }
}
