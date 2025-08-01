/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.queryengine.plan.planner.plan;

import org.apache.iotdb.common.rpc.thrift.TDataNodeLocation;
import org.apache.iotdb.common.rpc.thrift.TRegionReplicaSet;
import org.apache.iotdb.commons.partition.DataPartition;
import org.apache.iotdb.db.queryengine.common.PlanFragmentId;
import org.apache.iotdb.db.queryengine.plan.analyze.TypeProvider;
import org.apache.iotdb.db.queryengine.plan.planner.SubPlanTypeExtractor;
import org.apache.iotdb.db.queryengine.plan.planner.distribution.NodeDistribution;
import org.apache.iotdb.db.queryengine.plan.planner.plan.node.IPartitionRelatedNode;
import org.apache.iotdb.db.queryengine.plan.planner.plan.node.PlanNode;
import org.apache.iotdb.db.queryengine.plan.planner.plan.node.PlanNodeId;
import org.apache.iotdb.db.queryengine.plan.planner.plan.node.PlanNodeType;
import org.apache.iotdb.db.queryengine.plan.planner.plan.node.source.AlignedSeriesAggregationScanNode;
import org.apache.iotdb.db.queryengine.plan.planner.plan.node.source.AlignedSeriesScanNode;
import org.apache.iotdb.db.queryengine.plan.planner.plan.node.source.VirtualSourceNode;
import org.apache.iotdb.db.queryengine.plan.relational.planner.distribute.TableModelTypeProviderExtractor;
import org.apache.iotdb.db.queryengine.plan.relational.planner.node.InformationSchemaTableScanNode;

import org.apache.tsfile.utils.ReadWriteIOUtils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/** PlanFragment contains a sub-query of distributed query. */
public class PlanFragment {
  // once you add field for this class you need to change the serialize and deserialize methods
  private final PlanFragmentId id;
  private PlanNode planNodeTree;

  // map from output column name (for every node) to its datatype
  private TypeProvider typeProvider;

  // indicate whether this PlanFragment is the root of the whole Fragment-Plan-Tree or not
  private boolean isRoot;
  private int indexInFragmentInstanceList = -1;

  public PlanFragment(PlanFragmentId id, PlanNode planNodeTree) {
    this.id = id;
    this.planNodeTree = planNodeTree;
    this.isRoot = false;
  }

  public int getIndexInFragmentInstanceList() {
    return indexInFragmentInstanceList;
  }

  public void setIndexInFragmentInstanceList(int indexInFragmentInstanceList) {
    this.indexInFragmentInstanceList = indexInFragmentInstanceList;
  }

  public PlanFragmentId getId() {
    return id;
  }

  public PlanNode getPlanNodeTree() {
    return planNodeTree;
  }

  public void setPlanNodeTree(PlanNode planNodeTree) {
    this.planNodeTree = planNodeTree;
  }

  public TypeProvider getTypeProvider() {
    return typeProvider;
  }

  public void setTypeProvider(TypeProvider typeProvider) {
    this.typeProvider = typeProvider;
  }

  public void generateTypeProvider(TypeProvider allTypes) {
    this.typeProvider = SubPlanTypeExtractor.extractor(planNodeTree, allTypes);
  }

  public void generateTableModelTypeProvider(TypeProvider allTypes) {
    this.typeProvider = TableModelTypeProviderExtractor.extractor(planNodeTree, allTypes);
  }

  public boolean isRoot() {
    return isRoot;
  }

  public void setRoot(boolean root) {
    isRoot = root;
  }

  @Override
  public String toString() {
    return String.format("PlanFragment-%s", getId());
  }

  // Every Fragment related with DataPartition should only run in one DataRegion.
  // But it can select any one of the Endpoint of the target DataRegion
  // In current version, one PlanFragment should contain at least one SourceNode,
  // and the DataRegions of all SourceNodes should be same in one PlanFragment.
  // So we can use the DataRegion of one SourceNode as the PlanFragment's DataRegion.
  public TRegionReplicaSet getTargetRegionForTreeModel() {
    return getNodeRegion(planNodeTree, Collections.emptyMap());
  }

  public TRegionReplicaSet getTargetRegionForTableModel(
      final Map<PlanNodeId, NodeDistribution> nodeDistributionMap) {
    return getNodeRegion(planNodeTree, nodeDistributionMap);
  }

  // If a Fragment is not related with DataPartition,
  // it may be related with a specific DataNode.
  // This method return the DataNodeLocation will offer execution of this Fragment.
  public TDataNodeLocation getTargetLocation() {
    return getNodeLocation(planNodeTree);
  }

  private TRegionReplicaSet getNodeRegion(
      PlanNode root, final Map<PlanNodeId, NodeDistribution> nodeDistributionMap) {
    if (nodeDistributionMap.containsKey(root.getPlanNodeId())) {
      return nodeDistributionMap.get(root.getPlanNodeId()).getRegion();
    } else if (root instanceof IPartitionRelatedNode) {
      return ((IPartitionRelatedNode) root).getRegionReplicaSet();
    }
    for (PlanNode child : root.getChildren()) {
      TRegionReplicaSet result = getNodeRegion(child, nodeDistributionMap);
      if (result != null && result != DataPartition.NOT_ASSIGNED) {
        return result;
      }
    }
    return null;
  }

  private TDataNodeLocation getNodeLocation(PlanNode root) {
    if (root instanceof VirtualSourceNode) {
      return ((VirtualSourceNode) root).getDataNodeLocation();
    } else if (root instanceof InformationSchemaTableScanNode) {
      TRegionReplicaSet regionReplicaSet =
          ((InformationSchemaTableScanNode) root).getRegionReplicaSet();

      checkArgument(
          regionReplicaSet != null, "InformationSchemaTableScanNode must have regionReplicaSet");
      checkArgument(
          regionReplicaSet.getDataNodeLocations().size() == 1,
          "each InformationSchemaTableScanNode have only one DataNodeLocation");

      return regionReplicaSet.getDataNodeLocations().get(0);
    }
    for (PlanNode child : root.getChildren()) {
      TDataNodeLocation result = getNodeLocation(child);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  public void serialize(ByteBuffer byteBuffer) {
    id.serialize(byteBuffer);
    planNodeTree.serialize(byteBuffer);
    if (typeProvider == null) {
      ReadWriteIOUtils.write((byte) 0, byteBuffer);
    } else {
      ReadWriteIOUtils.write((byte) 1, byteBuffer);
      typeProvider.serialize(byteBuffer);
    }
  }

  public void serialize(DataOutputStream stream) throws IOException {
    id.serialize(stream);
    if (typeProvider == null) {
      ReadWriteIOUtils.write((byte) 0, stream);
    } else {
      ReadWriteIOUtils.write((byte) 1, stream);

      // templated align by device query, the serialized attributes are same,
      // so there is no need to serialize all the SeriesScanNode repeated
      if (typeProvider.getTemplatedInfo() != null) {
        typeProvider.serialize(stream);
        planNodeTree.serializeUseTemplate(stream, typeProvider);
        return;
      }

      typeProvider.serialize(stream);
    }
    planNodeTree.serialize(stream);
  }

  public static PlanFragment deserialize(ByteBuffer byteBuffer) {
    PlanFragmentId planFragmentId = PlanFragmentId.deserialize(byteBuffer);
    byte hasTypeProvider = ReadWriteIOUtils.readByte(byteBuffer);
    TypeProvider typeProvider = null;
    if (hasTypeProvider == 1) {
      typeProvider = TypeProvider.deserialize(byteBuffer);
    }
    PlanFragment planFragment =
        new PlanFragment(planFragmentId, deserializeHelper(byteBuffer, typeProvider));
    planFragment.setTypeProvider(typeProvider);
    return planFragment;
  }

  // deserialize the plan node recursively
  public static PlanNode deserializeHelper(ByteBuffer byteBuffer, TypeProvider typeProvider) {
    PlanNode root;
    if (typeProvider != null && typeProvider.getTemplatedInfo() != null) {
      root = PlanNodeType.deserializeWithTemplate(byteBuffer, typeProvider);
      if (root instanceof AlignedSeriesScanNode
          || root instanceof AlignedSeriesAggregationScanNode) {
        return root;
      }
    } else {
      root = PlanNodeType.deserialize(byteBuffer);
    }

    int childrenCount = byteBuffer.getInt();
    for (int i = 0; i < childrenCount; i++) {
      root.addChild(deserializeHelper(byteBuffer, typeProvider));
    }
    return root;
  }

  public void clearUselessField() {
    planNodeTree = null;
    typeProvider = null;
  }

  public void clearTypeProvider() {
    typeProvider = null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PlanFragment that = (PlanFragment) o;
    return Objects.equals(id, that.id) && Objects.equals(planNodeTree, that.planNodeTree);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, planNodeTree);
  }
}
