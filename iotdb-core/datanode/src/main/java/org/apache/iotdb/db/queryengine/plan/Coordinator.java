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

package org.apache.iotdb.db.queryengine.plan;

import org.apache.iotdb.common.rpc.thrift.TEndPoint;
import org.apache.iotdb.commons.client.ClientPoolFactory;
import org.apache.iotdb.commons.client.IClientManager;
import org.apache.iotdb.commons.client.async.AsyncDataNodeInternalServiceClient;
import org.apache.iotdb.commons.client.sync.SyncDataNodeInternalServiceClient;
import org.apache.iotdb.commons.concurrent.IoTDBThreadPoolFactory;
import org.apache.iotdb.commons.concurrent.ThreadName;
import org.apache.iotdb.commons.conf.CommonConfig;
import org.apache.iotdb.commons.conf.CommonDescriptor;
import org.apache.iotdb.commons.conf.IoTDBConstant;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.protocol.session.IClientSession;
import org.apache.iotdb.db.queryengine.common.DataNodeEndPoints;
import org.apache.iotdb.db.queryengine.common.MPPQueryContext;
import org.apache.iotdb.db.queryengine.common.QueryId;
import org.apache.iotdb.db.queryengine.common.SessionInfo;
import org.apache.iotdb.db.queryengine.execution.QueryIdGenerator;
import org.apache.iotdb.db.queryengine.plan.analyze.IPartitionFetcher;
import org.apache.iotdb.db.queryengine.plan.analyze.lock.DataNodeSchemaLockManager;
import org.apache.iotdb.db.queryengine.plan.analyze.schema.ISchemaFetcher;
import org.apache.iotdb.db.queryengine.plan.execution.ExecutionResult;
import org.apache.iotdb.db.queryengine.plan.execution.IQueryExecution;
import org.apache.iotdb.db.queryengine.plan.execution.QueryExecution;
import org.apache.iotdb.db.queryengine.plan.execution.config.ConfigExecution;
import org.apache.iotdb.db.queryengine.plan.execution.config.TableConfigTaskVisitor;
import org.apache.iotdb.db.queryengine.plan.execution.config.TreeConfigTaskVisitor;
import org.apache.iotdb.db.queryengine.plan.planner.LocalExecutionPlanner;
import org.apache.iotdb.db.queryengine.plan.planner.TreeModelPlanner;
import org.apache.iotdb.db.queryengine.plan.relational.metadata.Metadata;
import org.apache.iotdb.db.queryengine.plan.relational.planner.PlannerContext;
import org.apache.iotdb.db.queryengine.plan.relational.planner.TableModelPlanner;
import org.apache.iotdb.db.queryengine.plan.relational.planner.optimizations.DataNodeLocationSupplierFactory;
import org.apache.iotdb.db.queryengine.plan.relational.planner.optimizations.DistributedOptimizeFactory;
import org.apache.iotdb.db.queryengine.plan.relational.planner.optimizations.LogicalOptimizeFactory;
import org.apache.iotdb.db.queryengine.plan.relational.planner.optimizations.PlanOptimizer;
import org.apache.iotdb.db.queryengine.plan.relational.security.AccessControl;
import org.apache.iotdb.db.queryengine.plan.relational.security.AccessControlImpl;
import org.apache.iotdb.db.queryengine.plan.relational.security.ITableAuthCheckerImpl;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.AddColumn;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.AlterDB;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ClearCache;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.CreateDB;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.CreateFunction;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.CreateModel;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.CreateTable;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.CreateTraining;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.DeleteDevice;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.DescribeTable;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.DropColumn;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.DropDB;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.DropFunction;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.DropModel;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.DropTable;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ExtendRegion;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.Flush;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.KillQuery;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.LoadConfiguration;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.MigrateRegion;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.PipeStatement;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ReconstructRegion;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.RelationalAuthorStatement;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.RemoveAINode;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.RemoveConfigNode;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.RemoveDataNode;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.RemoveRegion;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.RenameColumn;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.RenameTable;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.SetColumnComment;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.SetConfiguration;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.SetProperties;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.SetSqlDialect;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.SetSystemStatus;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.SetTableComment;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowAINodes;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowCluster;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowClusterId;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowConfigNodes;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowCurrentDatabase;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowCurrentSqlDialect;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowCurrentTimestamp;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowCurrentUser;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowDB;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowDataNodes;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowFunctions;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowModels;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowRegions;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowTables;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowVariables;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.ShowVersion;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.StartRepairData;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.StopRepairData;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.SubscriptionStatement;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.Use;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.WrappedInsertStatement;
import org.apache.iotdb.db.queryengine.plan.relational.sql.parser.SqlParser;
import org.apache.iotdb.db.queryengine.plan.relational.sql.rewrite.StatementRewrite;
import org.apache.iotdb.db.queryengine.plan.relational.sql.rewrite.StatementRewriteFactory;
import org.apache.iotdb.db.queryengine.plan.relational.type.InternalTypeManager;
import org.apache.iotdb.db.queryengine.plan.statement.IConfigStatement;
import org.apache.iotdb.db.queryengine.plan.statement.Statement;
import org.apache.iotdb.db.utils.SetThreadName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiFunction;

import static org.apache.iotdb.commons.utils.StatusUtils.needRetry;
import static org.apache.iotdb.db.utils.CommonUtils.getContentOfRequest;

/**
 * The coordinator for MPP. It manages all the queries which are executed in current Node. And it
 * will be responsible for the lifecycle of a query. A query request will be represented as a
 * QueryExecution.
 */
public class Coordinator {

  private static final Logger LOGGER = LoggerFactory.getLogger(Coordinator.class);
  private static final int COORDINATOR_SCHEDULED_EXECUTOR_SIZE = 10;
  private static final IoTDBConfig CONFIG = IoTDBDescriptor.getInstance().getConfig();
  private static final CommonConfig COMMON_CONFIG = CommonDescriptor.getInstance().getConfig();

  private static final Logger SLOW_SQL_LOGGER =
      LoggerFactory.getLogger(IoTDBConstant.SLOW_SQL_LOGGER_NAME);

  private static final Logger SAMPLED_QUERIES_LOGGER =
      LoggerFactory.getLogger(IoTDBConstant.SAMPLED_QUERIES_LOGGER_NAME);

  private static final IClientManager<TEndPoint, SyncDataNodeInternalServiceClient>
      SYNC_INTERNAL_SERVICE_CLIENT_MANAGER =
          new IClientManager.Factory<TEndPoint, SyncDataNodeInternalServiceClient>()
              .createClientManager(
                  new ClientPoolFactory.SyncDataNodeInternalServiceClientPoolFactory());

  private static final IClientManager<TEndPoint, AsyncDataNodeInternalServiceClient>
      ASYNC_INTERNAL_SERVICE_CLIENT_MANAGER =
          new IClientManager.Factory<TEndPoint, AsyncDataNodeInternalServiceClient>()
              .createClientManager(
                  new ClientPoolFactory.AsyncDataNodeInternalServiceClientPoolFactory());

  private final ExecutorService executor;
  private final ExecutorService writeOperationExecutor;
  private final ScheduledExecutorService scheduledExecutor;
  private final ExecutorService dispatchExecutor;

  private final QueryIdGenerator queryIdGenerator =
      new QueryIdGenerator(IoTDBDescriptor.getInstance().getConfig().getDataNodeId());

  private static final Coordinator INSTANCE = new Coordinator();

  private final ConcurrentHashMap<Long, IQueryExecution> queryExecutionMap;

  private final StatementRewrite statementRewrite;
  private final List<PlanOptimizer> logicalPlanOptimizers;
  private final List<PlanOptimizer> distributionPlanOptimizers;
  private final AccessControl accessControl;
  private final DataNodeLocationSupplierFactory.DataNodeLocationSupplier dataNodeLocationSupplier;

  private Coordinator() {
    this.queryExecutionMap = new ConcurrentHashMap<>();
    this.executor = getQueryExecutor();
    this.writeOperationExecutor = getWriteExecutor();
    this.scheduledExecutor = getScheduledExecutor();
    int dispatchThreadNum = Math.max(20, Runtime.getRuntime().availableProcessors() * 2);
    this.dispatchExecutor =
        IoTDBThreadPoolFactory.newCachedThreadPool(
            ThreadName.FRAGMENT_INSTANCE_DISPATCH.getName(),
            dispatchThreadNum,
            dispatchThreadNum,
            new ThreadPoolExecutor.CallerRunsPolicy());
    this.accessControl = new AccessControlImpl(new ITableAuthCheckerImpl());
    this.statementRewrite = new StatementRewriteFactory().getStatementRewrite();
    this.logicalPlanOptimizers =
        new LogicalOptimizeFactory(
                new PlannerContext(
                    LocalExecutionPlanner.getInstance().metadata, new InternalTypeManager()))
            .getPlanOptimizers();
    this.distributionPlanOptimizers =
        new DistributedOptimizeFactory(
                new PlannerContext(
                    LocalExecutionPlanner.getInstance().metadata, new InternalTypeManager()))
            .getPlanOptimizers();
    this.dataNodeLocationSupplier = DataNodeLocationSupplierFactory.getSupplier();
  }

  private ExecutionResult execution(
      long queryId,
      SessionInfo session,
      String sql,
      boolean userQuery,
      BiFunction<MPPQueryContext, Long, IQueryExecution> iQueryExecutionFactory) {
    long startTime = System.currentTimeMillis();
    QueryId globalQueryId = queryIdGenerator.createNextQueryId();
    MPPQueryContext queryContext = null;
    try (SetThreadName queryName = new SetThreadName(globalQueryId.getId())) {
      if (sql != null && !sql.isEmpty()) {
        LOGGER.debug("[QueryStart] sql: {}", sql);
      }
      queryContext =
          new MPPQueryContext(
              sql,
              globalQueryId,
              queryId,
              session,
              DataNodeEndPoints.LOCAL_HOST_DATA_BLOCK_ENDPOINT,
              DataNodeEndPoints.LOCAL_HOST_INTERNAL_ENDPOINT);
      queryContext.setUserQuery(userQuery);
      IQueryExecution execution = iQueryExecutionFactory.apply(queryContext, startTime);
      if (execution.isQuery()) {
        queryExecutionMap.put(queryId, execution);
      } else {
        // we won't limit write operation's execution time
        queryContext.setTimeOut(Long.MAX_VALUE);
      }
      execution.start();
      ExecutionResult result = execution.getStatus();
      if (!execution.isQuery() && result.status != null && needRetry(result.status)) {
        // if it's write request and the result status needs to retry
        result.status.setNeedRetry(true);
      }
      return result;
    } finally {
      if (queryContext != null) {
        queryContext.releaseAllMemoryReservedForFrontEnd();
      }
      DataNodeSchemaLockManager.getInstance().releaseReadLock(queryContext);
    }
  }

  /** This method is called by the write method. So it does not set the timeout parameter. */
  public ExecutionResult executeForTreeModel(
      Statement statement,
      long queryId,
      SessionInfo session,
      String sql,
      IPartitionFetcher partitionFetcher,
      ISchemaFetcher schemaFetcher) {
    return executeForTreeModel(
        statement, queryId, session, sql, partitionFetcher, schemaFetcher, Long.MAX_VALUE, false);
  }

  public ExecutionResult executeForTreeModel(
      Statement statement,
      long queryId,
      SessionInfo session,
      String sql,
      IPartitionFetcher partitionFetcher,
      ISchemaFetcher schemaFetcher,
      long timeOut,
      boolean userQuery) {
    return execution(
        queryId,
        session,
        sql,
        userQuery,
        ((queryContext, startTime) ->
            createQueryExecutionForTreeModel(
                statement,
                queryContext,
                partitionFetcher,
                schemaFetcher,
                timeOut > 0 ? timeOut : CONFIG.getQueryTimeoutThreshold(),
                startTime)));
  }

  private IQueryExecution createQueryExecutionForTreeModel(
      Statement statement,
      MPPQueryContext queryContext,
      IPartitionFetcher partitionFetcher,
      ISchemaFetcher schemaFetcher,
      long timeOut,
      long startTime) {
    queryContext.setTimeOut(timeOut);
    queryContext.setStartTime(startTime);
    if (statement instanceof IConfigStatement) {
      queryContext.setQueryType(((IConfigStatement) statement).getQueryType());
      return new ConfigExecution(
          queryContext,
          statement.getType(),
          executor,
          statement.accept(new TreeConfigTaskVisitor(), queryContext));
    }
    TreeModelPlanner treeModelPlanner =
        new TreeModelPlanner(
            statement,
            executor,
            writeOperationExecutor,
            scheduledExecutor,
            partitionFetcher,
            schemaFetcher,
            SYNC_INTERNAL_SERVICE_CLIENT_MANAGER,
            ASYNC_INTERNAL_SERVICE_CLIENT_MANAGER);
    return new QueryExecution(treeModelPlanner, queryContext, executor);
  }

  public ExecutionResult executeForTableModel(
      org.apache.iotdb.db.queryengine.plan.relational.sql.ast.Statement statement,
      SqlParser sqlParser,
      IClientSession clientSession,
      long queryId,
      SessionInfo session,
      String sql,
      Metadata metadata,
      long timeOut,
      boolean userQuery) {
    return execution(
        queryId,
        session,
        sql,
        userQuery,
        ((queryContext, startTime) ->
            createQueryExecutionForTableModel(
                statement,
                sqlParser,
                clientSession,
                queryContext,
                metadata,
                timeOut > 0 ? timeOut : CONFIG.getQueryTimeoutThreshold(),
                startTime)));
  }

  public ExecutionResult executeForTableModel(
      Statement statement,
      SqlParser sqlParser,
      IClientSession clientSession,
      long queryId,
      SessionInfo session,
      String sql,
      Metadata metadata,
      long timeOut) {
    return execution(
        queryId,
        session,
        sql,
        false,
        ((queryContext, startTime) ->
            createQueryExecutionForTableModel(
                statement,
                sqlParser,
                clientSession,
                queryContext,
                metadata,
                timeOut > 0 ? timeOut : CONFIG.getQueryTimeoutThreshold(),
                startTime)));
  }

  private IQueryExecution createQueryExecutionForTableModel(
      Statement statement,
      SqlParser sqlParser,
      IClientSession clientSession,
      MPPQueryContext queryContext,
      Metadata metadata,
      long timeOut,
      long startTime) {
    queryContext.setTimeOut(timeOut);
    queryContext.setStartTime(startTime);
    TableModelPlanner tableModelPlanner =
        new TableModelPlanner(
            statement.toRelationalStatement(queryContext),
            sqlParser,
            metadata,
            scheduledExecutor,
            SYNC_INTERNAL_SERVICE_CLIENT_MANAGER,
            ASYNC_INTERNAL_SERVICE_CLIENT_MANAGER,
            statementRewrite,
            logicalPlanOptimizers,
            distributionPlanOptimizers,
            accessControl,
            dataNodeLocationSupplier);
    return new QueryExecution(tableModelPlanner, queryContext, executor);
  }

  private IQueryExecution createQueryExecutionForTableModel(
      final org.apache.iotdb.db.queryengine.plan.relational.sql.ast.Statement statement,
      final SqlParser sqlParser,
      final IClientSession clientSession,
      final MPPQueryContext queryContext,
      final Metadata metadata,
      final long timeOut,
      final long startTime) {
    queryContext.setTimeOut(timeOut);
    queryContext.setStartTime(startTime);
    if (statement instanceof DropDB
        || statement instanceof ShowDB
        || statement instanceof CreateDB
        || statement instanceof AlterDB
        || statement instanceof Use
        || statement instanceof CreateTable
        || statement instanceof DescribeTable
        || statement instanceof ShowTables
        || statement instanceof AddColumn
        || statement instanceof SetProperties
        || statement instanceof DropColumn
        || statement instanceof DropTable
        || statement instanceof SetTableComment
        || statement instanceof SetColumnComment
        || statement instanceof DeleteDevice
        || statement instanceof RenameColumn
        || statement instanceof RenameTable
        || statement instanceof ShowCluster
        || statement instanceof ShowRegions
        || statement instanceof ShowDataNodes
        || statement instanceof ShowConfigNodes
        || statement instanceof ShowAINodes
        || statement instanceof Flush
        || statement instanceof ClearCache
        || statement instanceof SetConfiguration
        || statement instanceof LoadConfiguration
        || statement instanceof SetSystemStatus
        || statement instanceof StartRepairData
        || statement instanceof StopRepairData
        || statement instanceof PipeStatement
        || statement instanceof RemoveDataNode
        || statement instanceof RemoveConfigNode
        || statement instanceof RemoveAINode
        || statement instanceof SubscriptionStatement
        || statement instanceof ShowCurrentSqlDialect
        || statement instanceof SetSqlDialect
        || statement instanceof ShowCurrentUser
        || statement instanceof ShowCurrentDatabase
        || statement instanceof ShowVersion
        || statement instanceof ShowVariables
        || statement instanceof ShowClusterId
        || statement instanceof ShowCurrentTimestamp
        || statement instanceof KillQuery
        || statement instanceof CreateFunction
        || statement instanceof DropFunction
        || statement instanceof ShowFunctions
        || statement instanceof RelationalAuthorStatement
        || statement instanceof MigrateRegion
        || statement instanceof ReconstructRegion
        || statement instanceof ExtendRegion
        || statement instanceof CreateModel
        || statement instanceof CreateTraining
        || statement instanceof ShowModels
        || statement instanceof DropModel
        || statement instanceof RemoveRegion) {
      return new ConfigExecution(
          queryContext,
          null,
          executor,
          statement.accept(
              new TableConfigTaskVisitor(clientSession, metadata, accessControl), queryContext));
    }
    if (statement instanceof WrappedInsertStatement) {
      ((WrappedInsertStatement) statement).setContext(queryContext);
    }
    final TableModelPlanner tableModelPlanner =
        new TableModelPlanner(
            statement,
            sqlParser,
            metadata,
            scheduledExecutor,
            SYNC_INTERNAL_SERVICE_CLIENT_MANAGER,
            ASYNC_INTERNAL_SERVICE_CLIENT_MANAGER,
            statementRewrite,
            logicalPlanOptimizers,
            distributionPlanOptimizers,
            accessControl,
            dataNodeLocationSupplier);
    return new QueryExecution(tableModelPlanner, queryContext, executor);
  }

  public IQueryExecution getQueryExecution(Long queryId) {
    return queryExecutionMap.get(queryId);
  }

  public List<IQueryExecution> getAllQueryExecutions() {
    return new ArrayList<>(queryExecutionMap.values());
  }

  public int getQueryExecutionMapSize() {
    return queryExecutionMap.size();
  }

  private ExecutorService getQueryExecutor() {
    int coordinatorReadExecutorSize = CONFIG.getCoordinatorReadExecutorSize();
    return IoTDBThreadPoolFactory.newFixedThreadPool(
        coordinatorReadExecutorSize, ThreadName.MPP_COORDINATOR_EXECUTOR_POOL.getName());
  }

  private ExecutorService getWriteExecutor() {
    int coordinatorWriteExecutorSize = CONFIG.getCoordinatorWriteExecutorSize();
    return IoTDBThreadPoolFactory.newFixedThreadPool(
        coordinatorWriteExecutorSize, ThreadName.MPP_COORDINATOR_WRITE_EXECUTOR.getName());
  }

  private ScheduledExecutorService getScheduledExecutor() {
    return IoTDBThreadPoolFactory.newScheduledThreadPool(
        COORDINATOR_SCHEDULED_EXECUTOR_SIZE,
        ThreadName.MPP_COORDINATOR_SCHEDULED_EXECUTOR.getName());
  }

  public QueryId createQueryId() {
    return queryIdGenerator.createNextQueryId();
  }

  public void cleanupQueryExecution(
      Long queryId, org.apache.thrift.TBase<?, ?> nativeApiRequest, Throwable t) {
    IQueryExecution queryExecution = getQueryExecution(queryId);
    if (queryExecution != null) {
      try (SetThreadName threadName = new SetThreadName(queryExecution.getQueryId())) {
        LOGGER.debug("[CleanUpQuery]]");
        queryExecution.stopAndCleanup(t);
        queryExecutionMap.remove(queryId);
        if (queryExecution.isQuery() && queryExecution.isUserQuery()) {
          long costTime = queryExecution.getTotalExecutionTime();
          // print slow query
          if (costTime / 1_000_000 >= CONFIG.getSlowQueryThreshold()) {
            SLOW_SQL_LOGGER.info(
                "Cost: {} ms, {}",
                costTime / 1_000_000,
                getContentOfRequest(nativeApiRequest, queryExecution));
          }

          // only sample successful query
          if (t == null && COMMON_CONFIG.isEnableQuerySampling()) { // sampling is enabled
            String queryRequest = getContentOfRequest(nativeApiRequest, queryExecution);
            if (COMMON_CONFIG.isQuerySamplingHasRateLimit()) {
              if (COMMON_CONFIG.getQuerySamplingRateLimiter().tryAcquire(queryRequest.length())) {
                SAMPLED_QUERIES_LOGGER.info(queryRequest);
              }
            } else {
              // no limit, always sampled
              SAMPLED_QUERIES_LOGGER.info(queryRequest);
            }
          }
        }
      }
    }
  }

  public void cleanupQueryExecution(Long queryId) {
    cleanupQueryExecution(queryId, null, null);
  }

  public IClientManager<TEndPoint, SyncDataNodeInternalServiceClient>
      getInternalServiceClientManager() {
    return SYNC_INTERNAL_SERVICE_CLIENT_MANAGER;
  }

  public static Coordinator getInstance() {
    return INSTANCE;
  }

  public AccessControl getAccessControl() {
    return accessControl;
  }

  public void recordExecutionTime(long queryId, long executionTime) {
    IQueryExecution queryExecution = getQueryExecution(queryId);
    if (queryExecution != null) {
      queryExecution.recordExecutionTime(executionTime);
    }
  }

  public long getTotalExecutionTime(long queryId) {
    IQueryExecution queryExecution = getQueryExecution(queryId);
    if (queryExecution != null) {
      return queryExecution.getTotalExecutionTime();
    }
    return -1L;
  }

  public List<PlanOptimizer> getDistributionPlanOptimizers() {
    return distributionPlanOptimizers;
  }

  public List<PlanOptimizer> getLogicalPlanOptimizers() {
    return logicalPlanOptimizers;
  }

  public DataNodeLocationSupplierFactory.DataNodeLocationSupplier getDataNodeLocationSupplier() {
    return dataNodeLocationSupplier;
  }

  public ExecutorService getDispatchExecutor() {
    return dispatchExecutor;
  }
}
