/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kafka.server

import kafka.admin.AdminUtils
import kafka.api._
import kafka.message._
import kafka.network._
import kafka.log._
import kafka.utils.ZKGroupTopicDirs
import scala.collection._
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic._
import kafka.metrics.KafkaMetricsGroup
import kafka.common._
import kafka.utils.{ZkUtils, Pool, SystemTime, Logging}
import kafka.network.RequestChannel.Response
import kafka.cluster.Broker
import kafka.controller.KafkaController
import kafka.utils.Utils.inLock
import org.I0Itec.zkclient.ZkClient
import java.util.concurrent.locks.ReentrantReadWriteLock
import kafka.controller.KafkaController.StateChangeLogger

/**
 * Logic to handle the various Kafka requests
 */
class KafkaApis(val requestChannel: RequestChannel,
                val replicaManager: ReplicaManager,
                val zkClient: ZkClient,
                val brokerId: Int,
                val config: KafkaConfig,
                val controller: KafkaController) extends Logging {

  private val producerRequestPurgatory =
    new ProducerRequestPurgatory(replicaManager.config.producerPurgatoryPurgeIntervalRequests)
  private val fetchRequestPurgatory =
    new FetchRequestPurgatory(requestChannel, replicaManager.config.fetchPurgatoryPurgeIntervalRequests)
  private val delayedRequestMetrics = new DelayedRequestMetrics
  /* following 3 data structures are updated by the update metadata request
  * and is queried by the topic metadata request. */
  var metadataCache = new MetadataCache
  this.logIdent = "[KafkaApi-%d] ".format(brokerId)

  class MetadataCache {
    private val cache: mutable.Map[String, mutable.Map[Int, PartitionStateInfo]] =
      new mutable.HashMap[String, mutable.Map[Int, PartitionStateInfo]]()

    private val aliveBrokers: mutable.Map[Int, Broker] = new mutable.HashMap[Int, Broker]()
    private val partitionMetadataLock = new ReentrantReadWriteLock()

    def getTopicMetadata(topics: Set[String]) = {
      val isAllTopics = topics.isEmpty
      val topicsRequested = if(isAllTopics) cache.keySet else topics
      val topicResponses: mutable.ListBuffer[TopicMetadata] = new mutable.ListBuffer[TopicMetadata]
      inLock(partitionMetadataLock.readLock()) {
        for (topic <- topicsRequested) {
          if (isAllTopics || this.containsTopic(topic)) {
            val partitionStateInfos = cache(topic)
            val partitionMetadata = partitionStateInfos.map { case (partitionId, partitionState) =>
              val replicas = partitionState.allReplicas
              val replicaInfo: Seq[Broker] = replicas.map(aliveBrokers.getOrElse(_, null)).filter(_ != null).toSeq
              var leaderInfo: Option[Broker] = None
              var isrInfo: Seq[Broker] = Nil
              val leaderIsrAndEpoch = partitionState.leaderIsrAndControllerEpoch
              val leader = leaderIsrAndEpoch.leaderAndIsr.leader
              val isr = leaderIsrAndEpoch.leaderAndIsr.isr
              val topicPartition = TopicAndPartition(topic, partitionId)
              try {
                leaderInfo = aliveBrokers.get(leader)
                if (!leaderInfo.isDefined)
                  throw new LeaderNotAvailableException("Leader not available for %s.".format(topicPartition))
                isrInfo = isr.map(aliveBrokers.getOrElse(_, null)).filter(_ != null)
                if (replicaInfo.size < replicas.size)
                  throw new ReplicaNotAvailableException("Replica information not available for following brokers: " +
                    replicas.filterNot(replicaInfo.map(_.id).contains(_)).mkString(","))
                if (isrInfo.size < isr.size)
                  throw new ReplicaNotAvailableException("In Sync Replica information not available for following brokers: " +
                    isr.filterNot(isrInfo.map(_.id).contains(_)).mkString(","))
                new PartitionMetadata(partitionId, leaderInfo, replicaInfo, isrInfo, ErrorMapping.NoError)
              } catch {
                case e: Throwable =>
                  debug("Error while fetching metadata for %s. Possible cause: %s".format(topicPartition, e.getMessage))
                  new PartitionMetadata(partitionId, leaderInfo, replicaInfo, isrInfo,
                    ErrorMapping.codeFor(e.getClass.asInstanceOf[Class[Throwable]]))
              }
            }
            topicResponses += new TopicMetadata(topic, partitionMetadata.toSeq)
          }
        }
      }
      topicResponses
    }

    def addPartitionInfo(topic: String,
                         partitionId: Int,
                         stateInfo: PartitionStateInfo) {
      inLock(partitionMetadataLock.writeLock()) {
        addPartitionInfoInternal(topic, partitionId, stateInfo)
      }
    }

    def getPartitionInfos(topic: String) = {
      inLock(partitionMetadataLock.readLock()) {
        cache(topic)
      }
    }

    def containsTopicAndPartition(topic: String,
                                  partitionId: Int): Boolean = {
      inLock(partitionMetadataLock.readLock()) {
        cache.get(topic) match {
          case Some(partitionInfos) => partitionInfos.contains(partitionId)
          case None => false
        }
      }
    }

    def containsTopic(topic: String) = cache.contains(topic)

    def updateCache(updateMetadataRequest: UpdateMetadataRequest,
                    brokerId: Int,
                    stateChangeLogger: StateChangeLogger) {
      inLock(partitionMetadataLock.writeLock()) {
        updateMetadataRequest.aliveBrokers.foreach(b => aliveBrokers.put(b.id, b))
        val topicsToDelete = mutable.Set[String]()
        updateMetadataRequest.partitionStateInfos.foreach { partitionState =>
          if (partitionState._2.leaderIsrAndControllerEpoch.leaderAndIsr.leader == LeaderAndIsr.LeaderDuringDelete) {
            topicsToDelete.add(partitionState._1.topic)
          } else {
            addPartitionInfoInternal(partitionState._1.topic, partitionState._1.partition, partitionState._2)
            stateChangeLogger.trace(("Broker %d cached leader info %s for partition %s in response to " +
                                     "UpdateMetadata request sent by controller %d epoch %d with correlation id %d")
                                     .format(brokerId, partitionState._2, partitionState._1, updateMetadataRequest.controllerId,
                                             updateMetadataRequest.controllerEpoch, updateMetadataRequest.correlationId))
          }
        }

        topicsToDelete.foreach { topic =>
          cache.remove(topic)
          stateChangeLogger.trace(("Broker %d deleted partitions for topic %s from metadata cache in response to " +
                                   "UpdateMetadata request  sent by controller %d epoch %d with correlation id %d")
                                   .format(brokerId, topic, updateMetadataRequest.controllerId,
                                           updateMetadataRequest.controllerEpoch, updateMetadataRequest.correlationId))
        }
      }
    }

    private def addPartitionInfoInternal(topic: String,
                                         partitionId: Int,
                                         stateInfo: PartitionStateInfo) {
      cache.get(topic) match {
        case Some(infos) => infos.put(partitionId, stateInfo)
        case None => {
          val newInfos: mutable.Map[Int, PartitionStateInfo] = new mutable.HashMap[Int, PartitionStateInfo]
          cache.put(topic, newInfos)
          newInfos.put(partitionId, stateInfo)
        }
      }
    }
  }

  /**
   * Top-level method that handles all requests and multiplexes to the right api
   */
  def handle(request: RequestChannel.Request) {
    try{
      trace("Handling request: " + request.requestObj + " from client: " + request.remoteAddress)
      request.requestId match {
        case RequestKeys.ProduceKey => handleProducerRequest(request)
        case RequestKeys.FetchKey => handleFetchRequest(request)
        case RequestKeys.OffsetsKey => handleOffsetRequest(request)
        case RequestKeys.MetadataKey => handleTopicMetadataRequest(request)
        case RequestKeys.LeaderAndIsrKey => handleLeaderAndIsrRequest(request)
        case RequestKeys.StopReplicaKey => handleStopReplicaRequest(request)
        case RequestKeys.UpdateMetadataKey => handleUpdateMetadataRequest(request)
        case RequestKeys.ControlledShutdownKey => handleControlledShutdownRequest(request)
        case RequestKeys.OffsetCommitKey => handleOffsetCommitRequest(request)
        case RequestKeys.OffsetFetchKey => handleOffsetFetchRequest(request)
        case requestId => throw new KafkaException("Unknown api code " + requestId)
      }
    } catch {
      case e: Throwable =>
        request.requestObj.handleError(e, requestChannel, request)
        error("error when handling request %s".format(request.requestObj), e)
    } finally
      request.apiLocalCompleteTimeMs = SystemTime.milliseconds
  }

  // ensureTopicExists is only for client facing requests
  private def ensureTopicExists(topic: String) = {
    if (!metadataCache.containsTopic(topic))
      throw new UnknownTopicOrPartitionException("Topic " + topic + " either doesn't exist or is in the process of being deleted")
  }

  def handleLeaderAndIsrRequest(request: RequestChannel.Request) {
    // ensureTopicExists is only for client facing requests
    // We can't have the ensureTopicExists check here since the controller sends it as an advisory to all brokers so they
    // stop serving data to clients for the topic being deleted
    val leaderAndIsrRequest = request.requestObj.asInstanceOf[LeaderAndIsrRequest]
    try {
      val (response, error) = replicaManager.becomeLeaderOrFollower(leaderAndIsrRequest)
      val leaderAndIsrResponse = new LeaderAndIsrResponse(leaderAndIsrRequest.correlationId, response, error)
      requestChannel.sendResponse(new Response(request, new BoundedByteBufferSend(leaderAndIsrResponse)))
    } catch {
      case e: KafkaStorageException =>
        fatal("Disk error during leadership change.", e)
        Runtime.getRuntime.halt(1)
    }
  }

  def handleStopReplicaRequest(request: RequestChannel.Request) {
    // ensureTopicExists is only for client facing requests
    // We can't have the ensureTopicExists check here since the controller sends it as an advisory to all brokers so they
    // stop serving data to clients for the topic being deleted
    val stopReplicaRequest = request.requestObj.asInstanceOf[StopReplicaRequest]
    val (response, error) = replicaManager.stopReplicas(stopReplicaRequest)
    val stopReplicaResponse = new StopReplicaResponse(stopReplicaRequest.correlationId, response.toMap, error)
    requestChannel.sendResponse(new Response(request, new BoundedByteBufferSend(stopReplicaResponse)))
    replicaManager.replicaFetcherManager.shutdownIdleFetcherThreads()
  }

  def handleUpdateMetadataRequest(request: RequestChannel.Request) {
    val updateMetadataRequest = request.requestObj.asInstanceOf[UpdateMetadataRequest]
    // ensureTopicExists is only for client facing requests
    // We can't have the ensureTopicExists check here since the controller sends it as an advisory to all brokers so they
    // stop serving data to clients for the topic being deleted
    val stateChangeLogger = replicaManager.stateChangeLogger
    if(updateMetadataRequest.controllerEpoch < replicaManager.controllerEpoch) {
      val stateControllerEpochErrorMessage = ("Broker %d received update metadata request with correlation id %d from an " +
        "old controller %d with epoch %d. Latest known controller epoch is %d").format(brokerId,
        updateMetadataRequest.correlationId, updateMetadataRequest.controllerId, updateMetadataRequest.controllerEpoch,
        replicaManager.controllerEpoch)
      stateChangeLogger.warn(stateControllerEpochErrorMessage)
      throw new ControllerMovedException(stateControllerEpochErrorMessage)
    }
    replicaManager.controllerEpoch = updateMetadataRequest.controllerEpoch
      // cache the list of alive brokers in the cluster
    metadataCache.updateCache(updateMetadataRequest, brokerId, stateChangeLogger)
    val updateMetadataResponse = new UpdateMetadataResponse(updateMetadataRequest.correlationId)
    requestChannel.sendResponse(new Response(request, new BoundedByteBufferSend(updateMetadataResponse)))
  }

  def handleControlledShutdownRequest(request: RequestChannel.Request) {
    // ensureTopicExists is only for client facing requests
    // We can't have the ensureTopicExists check here since the controller sends it as an advisory to all brokers so they
    // stop serving data to clients for the topic being deleted
    val controlledShutdownRequest = request.requestObj.asInstanceOf[ControlledShutdownRequest]
    val partitionsRemaining = controller.shutdownBroker(controlledShutdownRequest.brokerId)
    val controlledShutdownResponse = new ControlledShutdownResponse(controlledShutdownRequest.correlationId,
      ErrorMapping.NoError, partitionsRemaining)
    requestChannel.sendResponse(new Response(request, new BoundedByteBufferSend(controlledShutdownResponse)))
  }

  /**
   * Check if a partitionData from a produce request can unblock any
   * DelayedFetch requests.
   */
  def maybeUnblockDelayedFetchRequests(topic: String, partition: Int, messageSizeInBytes: Int) {
    val satisfied =  fetchRequestPurgatory.update(RequestKey(topic, partition), messageSizeInBytes)
    trace("Producer request to (%s-%d) unblocked %d fetch requests.".format(topic, partition, satisfied.size))

    // send any newly unblocked responses
    for(fetchReq <- satisfied) {
      val topicData = readMessageSets(fetchReq.fetch)
      val response = FetchResponse(fetchReq.fetch.correlationId, topicData)
      requestChannel.sendResponse(new RequestChannel.Response(fetchReq.request, new FetchResponseSend(response)))
    }
  }

  /**
   * Handle a produce request
   */
  def handleProducerRequest(request: RequestChannel.Request) {
    val produceRequest = request.requestObj.asInstanceOf[ProducerRequest]
    val sTime = SystemTime.milliseconds
    val localProduceResults = appendToLocalLog(produceRequest)
    debug("Produce to local log in %d ms".format(SystemTime.milliseconds - sTime))

    val numPartitionsInError = localProduceResults.count(_.error.isDefined)
    produceRequest.data.foreach(partitionAndData =>
      maybeUnblockDelayedFetchRequests(partitionAndData._1.topic, partitionAndData._1.partition, partitionAndData._2.sizeInBytes))

    val allPartitionHaveReplicationFactorOne =
      !produceRequest.data.keySet.exists(
        m => replicaManager.getReplicationFactorForPartition(m.topic, m.partition) != 1)
    if(produceRequest.requiredAcks == 0) {
      // no operation needed if producer request.required.acks = 0; however, if there is any exception in handling the request, since
      // no response is expected by the producer the handler will send a close connection response to the socket server
      // to close the socket so that the producer client will know that some exception has happened and will refresh its metadata
      if (numPartitionsInError != 0) {
        info(("Send the close connection response due to error handling produce request " +
          "[clientId = %s, correlationId = %s, topicAndPartition = %s] with Ack=0")
          .format(produceRequest.clientId, produceRequest.correlationId, produceRequest.topicPartitionMessageSizeMap.keySet.mkString(",")))
        requestChannel.closeConnection(request.processor, request)
      } else {
        requestChannel.noOperation(request.processor, request)
      }
    } else if (produceRequest.requiredAcks == 1 ||
        produceRequest.numPartitions <= 0 ||
        allPartitionHaveReplicationFactorOne ||
        numPartitionsInError == produceRequest.numPartitions) {
      val statuses = localProduceResults.map(r => r.key -> ProducerResponseStatus(r.errorCode, r.start)).toMap
      val response = ProducerResponse(produceRequest.correlationId, statuses)
      requestChannel.sendResponse(new RequestChannel.Response(request, new BoundedByteBufferSend(response)))
    } else {
      // create a list of (topic, partition) pairs to use as keys for this delayed request
      val producerRequestKeys = produceRequest.data.keys.map(
        topicAndPartition => new RequestKey(topicAndPartition)).toSeq
      val statuses = localProduceResults.map(r => r.key -> ProducerResponseStatus(r.errorCode, r.end + 1)).toMap
      val delayedProduce = new DelayedProduce(producerRequestKeys, 
                                              request,
                                              statuses,
                                              produceRequest, 
                                              produceRequest.ackTimeoutMs.toLong)
      producerRequestPurgatory.watch(delayedProduce)

      /*
       * Replica fetch requests may have arrived (and potentially satisfied)
       * delayedProduce requests while they were being added to the purgatory.
       * Here, we explicitly check if any of them can be satisfied.
       */
      var satisfiedProduceRequests = new mutable.ArrayBuffer[DelayedProduce]
      producerRequestKeys.foreach(key =>
        satisfiedProduceRequests ++=
          producerRequestPurgatory.update(key, key))
      debug(satisfiedProduceRequests.size +
        " producer requests unblocked during produce to local log.")
      satisfiedProduceRequests.foreach(_.respond())
      // we do not need the data anymore
      produceRequest.emptyData()
    }
  }
  
  case class ProduceResult(key: TopicAndPartition, start: Long, end: Long, error: Option[Throwable] = None) {
    def this(key: TopicAndPartition, throwable: Throwable) = 
      this(key, -1L, -1L, Some(throwable))
    
    def errorCode = error match {
      case None => ErrorMapping.NoError
      case Some(error) => ErrorMapping.codeFor(error.getClass.asInstanceOf[Class[Throwable]])
    }
  }

  /**
   * Helper method for handling a parsed producer request
   */
  private def appendToLocalLog(producerRequest: ProducerRequest): Iterable[ProduceResult] = {
    val partitionAndData: Map[TopicAndPartition, MessageSet] = producerRequest.data
    trace("Append [%s] to local log ".format(partitionAndData.toString))
    partitionAndData.map {case (topicAndPartition, messages) =>
      // update stats for incoming bytes rate
      BrokerTopicStats.getBrokerTopicStats(topicAndPartition.topic).bytesInRate.mark(messages.sizeInBytes)
      BrokerTopicStats.getBrokerAllTopicsStats.bytesInRate.mark(messages.sizeInBytes)

      try {
        ensureTopicExists(topicAndPartition.topic)
        val partitionOpt = replicaManager.getPartition(topicAndPartition.topic, topicAndPartition.partition)
        val info =
          partitionOpt match {
            case Some(partition) => partition.appendMessagesToLeader(messages.asInstanceOf[ByteBufferMessageSet])
            case None => throw new UnknownTopicOrPartitionException("Partition %s doesn't exist on %d"
              .format(topicAndPartition, brokerId))

          }
        val numAppendedMessages = if (info.firstOffset == -1L || info.lastOffset == -1L) 0 else (info.lastOffset - info.firstOffset + 1)

        // update stats for successfully appended messages
        BrokerTopicStats.getBrokerTopicStats(topicAndPartition.topic).logBytesAppendRate.mark(messages.sizeInBytes)
        BrokerTopicStats.getBrokerAllTopicsStats.logBytesAppendRate.mark(messages.sizeInBytes)
        BrokerTopicStats.getBrokerTopicStats(topicAndPartition.topic).messagesInRate.mark(numAppendedMessages)
        BrokerTopicStats.getBrokerAllTopicsStats.messagesInRate.mark(numAppendedMessages)

        trace("%d bytes written to log %s-%d beginning at offset %d and ending at offset %d"
              .format(messages.size, topicAndPartition.topic, topicAndPartition.partition, info.firstOffset, info.lastOffset))
        ProduceResult(topicAndPartition, info.firstOffset, info.lastOffset)
      } catch {
        // NOTE: Failed produce requests is not incremented for UnknownTopicOrPartitionException and NotLeaderForPartitionException
        // since failed produce requests metric is supposed to indicate failure of a broker in handling a produce request
        // for a partition it is the leader for
        case e: KafkaStorageException =>
          fatal("Halting due to unrecoverable I/O error while handling produce request: ", e)
          Runtime.getRuntime.halt(1)
          null
        case utpe: UnknownTopicOrPartitionException =>
          warn("Produce request with correlation id %d from client %s on partition %s failed due to %s".format(
               producerRequest.correlationId, producerRequest.clientId, topicAndPartition, utpe.getMessage))
          new ProduceResult(topicAndPartition, utpe)
        case nle: NotLeaderForPartitionException =>
          warn("Produce request with correlation id %d from client %s on partition %s failed due to %s".format(
               producerRequest.correlationId, producerRequest.clientId, topicAndPartition, nle.getMessage))
          new ProduceResult(topicAndPartition, nle)
        case e: Throwable =>
          BrokerTopicStats.getBrokerTopicStats(topicAndPartition.topic).failedProduceRequestRate.mark()
          BrokerTopicStats.getBrokerAllTopicsStats.failedProduceRequestRate.mark()
          error("Error processing ProducerRequest with correlation id %d from client %s on partition %s"
            .format(producerRequest.correlationId, producerRequest.clientId, topicAndPartition), e)
          new ProduceResult(topicAndPartition, e)
       }
    }
  }

  /**
   * Handle a fetch request
   */
  def handleFetchRequest(request: RequestChannel.Request) {
    val fetchRequest = request.requestObj.asInstanceOf[FetchRequest]
    if(fetchRequest.isFromFollower) {
      maybeUpdatePartitionHw(fetchRequest)
      // after updating HW, some delayed produce requests may be unblocked
      var satisfiedProduceRequests = new mutable.ArrayBuffer[DelayedProduce]
      fetchRequest.requestInfo.foreach {
        case (topicAndPartition, _) =>
          val key = new RequestKey(topicAndPartition)
          satisfiedProduceRequests ++= producerRequestPurgatory.update(key, key)
      }
      debug("Replica %d fetch unblocked %d producer requests."
        .format(fetchRequest.replicaId, satisfiedProduceRequests.size))
      satisfiedProduceRequests.foreach(_.respond())
    }

    val dataRead = readMessageSets(fetchRequest)
    val bytesReadable = dataRead.values.map(_.messages.sizeInBytes).sum
    if(fetchRequest.maxWait <= 0 ||
       bytesReadable >= fetchRequest.minBytes ||
       fetchRequest.numPartitions <= 0) {
      debug("Returning fetch response %s for fetch request with correlation id %d to client %s"
        .format(dataRead.values.map(_.error).mkString(","), fetchRequest.correlationId, fetchRequest.clientId))
      val response = new FetchResponse(fetchRequest.correlationId, dataRead)
      requestChannel.sendResponse(new RequestChannel.Response(request, new FetchResponseSend(response)))
    } else {
      debug("Putting fetch request with correlation id %d from client %s into purgatory".format(fetchRequest.correlationId,
        fetchRequest.clientId))
      // create a list of (topic, partition) pairs to use as keys for this delayed request
      val delayedFetchKeys = fetchRequest.requestInfo.keys.toSeq.map(new RequestKey(_))
      val delayedFetch = new DelayedFetch(delayedFetchKeys, request, fetchRequest, fetchRequest.maxWait, bytesReadable)
      fetchRequestPurgatory.watch(delayedFetch)
    }
  }

  private def maybeUpdatePartitionHw(fetchRequest: FetchRequest) {
    debug("Maybe update partition HW due to fetch request: %s ".format(fetchRequest))
    fetchRequest.requestInfo.foreach(info => {
      val (topic, partition, offset) = (info._1.topic, info._1.partition, info._2.offset)
      replicaManager.recordFollowerPosition(topic, partition, fetchRequest.replicaId, offset)
    })
  }

  /**
   * Read from all the offset details given and return a map of
   * (topic, partition) -> PartitionData
   */
  private def readMessageSets(fetchRequest: FetchRequest) = {
    val isFetchFromFollower = fetchRequest.isFromFollower
    fetchRequest.requestInfo.map
    {
      case (TopicAndPartition(topic, partition), PartitionFetchInfo(offset, fetchSize)) =>
        val partitionData =
          try {
            ensureTopicExists(topic)
            val (messages, highWatermark) = readMessageSet(topic, partition, offset, fetchSize, fetchRequest.replicaId)
            BrokerTopicStats.getBrokerTopicStats(topic).bytesOutRate.mark(messages.sizeInBytes)
            BrokerTopicStats.getBrokerAllTopicsStats.bytesOutRate.mark(messages.sizeInBytes)
            if (!isFetchFromFollower) {
              new FetchResponsePartitionData(ErrorMapping.NoError, highWatermark, messages)
            } else {
              debug("Leader %d for partition [%s,%d] received fetch request from follower %d"
                            .format(brokerId, topic, partition, fetchRequest.replicaId))
              new FetchResponsePartitionData(ErrorMapping.NoError, highWatermark, messages)
            }
          } catch {
            // NOTE: Failed fetch requests is not incremented for UnknownTopicOrPartitionException and NotLeaderForPartitionException
            // since failed fetch requests metric is supposed to indicate failure of a broker in handling a fetch request
            // for a partition it is the leader for
            case utpe: UnknownTopicOrPartitionException =>
              warn("Fetch request with correlation id %d from client %s on partition [%s,%d] failed due to %s".format(
                   fetchRequest.correlationId, fetchRequest.clientId, topic, partition, utpe.getMessage))
              new FetchResponsePartitionData(ErrorMapping.codeFor(utpe.getClass.asInstanceOf[Class[Throwable]]), -1L, MessageSet.Empty)
            case nle: NotLeaderForPartitionException =>
              warn("Fetch request with correlation id %d from client %s on partition [%s,%d] failed due to %s".format(
                fetchRequest.correlationId, fetchRequest.clientId, topic, partition, nle.getMessage))
              new FetchResponsePartitionData(ErrorMapping.codeFor(nle.getClass.asInstanceOf[Class[Throwable]]), -1L, MessageSet.Empty)
            case t: Throwable =>
              BrokerTopicStats.getBrokerTopicStats(topic).failedFetchRequestRate.mark()
              BrokerTopicStats.getBrokerAllTopicsStats.failedFetchRequestRate.mark()
              error("Error when processing fetch request for partition [%s,%d] offset %d from %s with correlation id %d"
                    .format(topic, partition, offset, if (isFetchFromFollower) "follower" else "consumer", fetchRequest.correlationId), t)
              new FetchResponsePartitionData(ErrorMapping.codeFor(t.getClass.asInstanceOf[Class[Throwable]]), -1L, MessageSet.Empty)
          }
        (TopicAndPartition(topic, partition), partitionData)
    }
  }

  /**
   * Read from a single topic/partition at the given offset upto maxSize bytes
   */
  private def readMessageSet(topic: String, 
                             partition: Int, 
                             offset: Long,
                             maxSize: Int, 
                             fromReplicaId: Int): (MessageSet, Long) = {
    // check if the current broker is the leader for the partitions
    val localReplica = if(fromReplicaId == Request.DebuggingConsumerId)
      replicaManager.getReplicaOrException(topic, partition)
    else
      replicaManager.getLeaderReplicaIfLocal(topic, partition)
    trace("Fetching log segment for topic, partition, offset, size = " + (topic, partition, offset, maxSize))
    val maxOffsetOpt = 
      if (Request.isReplicaIdFromFollower(fromReplicaId))
        None
      else
        Some(localReplica.highWatermark)
    val messages = localReplica.log match {
      case Some(log) =>
        log.read(offset, maxSize, maxOffsetOpt)
      case None =>
        error("Leader for partition [%s,%d] on broker %d does not have a local log".format(topic, partition, brokerId))
        MessageSet.Empty
    }
    (messages, localReplica.highWatermark)
  }

  /**
   * Service the offset request API 
   */
  def handleOffsetRequest(request: RequestChannel.Request) {
    val offsetRequest = request.requestObj.asInstanceOf[OffsetRequest]
    val responseMap = offsetRequest.requestInfo.map(elem => {
      val (topicAndPartition, partitionOffsetRequestInfo) = elem
      try {
        ensureTopicExists(topicAndPartition.topic)
        // ensure leader exists
        val localReplica = if(!offsetRequest.isFromDebuggingClient)
          replicaManager.getLeaderReplicaIfLocal(topicAndPartition.topic, topicAndPartition.partition)
        else
          replicaManager.getReplicaOrException(topicAndPartition.topic, topicAndPartition.partition)
        val offsets = {
          val allOffsets = fetchOffsets(replicaManager.logManager,
                                        topicAndPartition,
                                        partitionOffsetRequestInfo.time,
                                        partitionOffsetRequestInfo.maxNumOffsets)
          if (!offsetRequest.isFromOrdinaryClient) {
            allOffsets
          } else {
            val hw = localReplica.highWatermark
            if (allOffsets.exists(_ > hw))
              hw +: allOffsets.dropWhile(_ > hw)
            else 
              allOffsets
          }
        }
        (topicAndPartition, PartitionOffsetsResponse(ErrorMapping.NoError, offsets))
      } catch {
        // NOTE: UnknownTopicOrPartitionException and NotLeaderForPartitionException are special cased since these error messages
        // are typically transient and there is no value in logging the entire stack trace for the same
        case utpe: UnknownTopicOrPartitionException =>
          warn("Offset request with correlation id %d from client %s on partition %s failed due to %s".format(
               offsetRequest.correlationId, offsetRequest.clientId, topicAndPartition, utpe.getMessage))
          (topicAndPartition, PartitionOffsetsResponse(ErrorMapping.codeFor(utpe.getClass.asInstanceOf[Class[Throwable]]), Nil) )
        case nle: NotLeaderForPartitionException =>
          warn("Offset request with correlation id %d from client %s on partition %s failed due to %s".format(
               offsetRequest.correlationId, offsetRequest.clientId, topicAndPartition,nle.getMessage))
          (topicAndPartition, PartitionOffsetsResponse(ErrorMapping.codeFor(nle.getClass.asInstanceOf[Class[Throwable]]), Nil) )
        case e: Throwable =>
          warn("Error while responding to offset request", e)
          (topicAndPartition, PartitionOffsetsResponse(ErrorMapping.codeFor(e.getClass.asInstanceOf[Class[Throwable]]), Nil) )
      }
    })
    val response = OffsetResponse(offsetRequest.correlationId, responseMap)
    requestChannel.sendResponse(new RequestChannel.Response(request, new BoundedByteBufferSend(response)))
  }
  
  def fetchOffsets(logManager: LogManager, topicAndPartition: TopicAndPartition, timestamp: Long, maxNumOffsets: Int): Seq[Long] = {
    logManager.getLog(topicAndPartition) match {
      case Some(log) => 
        fetchOffsetsBefore(log, timestamp, maxNumOffsets)
      case None => 
        if (timestamp == OffsetRequest.LatestTime || timestamp == OffsetRequest.EarliestTime)
          Seq(0L)
        else
          Nil
    }
  }
  
  def fetchOffsetsBefore(log: Log, timestamp: Long, maxNumOffsets: Int): Seq[Long] = {
    val segsArray = log.logSegments.toArray
    var offsetTimeArray: Array[(Long, Long)] = null
    if(segsArray.last.size > 0)
      offsetTimeArray = new Array[(Long, Long)](segsArray.length + 1)
    else
      offsetTimeArray = new Array[(Long, Long)](segsArray.length)

    for(i <- 0 until segsArray.length)
      offsetTimeArray(i) = (segsArray(i).baseOffset, segsArray(i).lastModified)
    if(segsArray.last.size > 0)
      offsetTimeArray(segsArray.length) = (log.logEndOffset, SystemTime.milliseconds)

    var startIndex = -1
    timestamp match {
      case OffsetRequest.LatestTime =>
        startIndex = offsetTimeArray.length - 1
      case OffsetRequest.EarliestTime =>
        startIndex = 0
      case _ =>
        var isFound = false
        debug("Offset time array = " + offsetTimeArray.foreach(o => "%d, %d".format(o._1, o._2)))
        startIndex = offsetTimeArray.length - 1
        while (startIndex >= 0 && !isFound) {
          if (offsetTimeArray(startIndex)._2 <= timestamp)
            isFound = true
          else
            startIndex -=1
        }
    }

    val retSize = maxNumOffsets.min(startIndex + 1)
    val ret = new Array[Long](retSize)
    for(j <- 0 until retSize) {
      ret(j) = offsetTimeArray(startIndex)._1
      startIndex -= 1
    }
    // ensure that the returned seq is in descending order of offsets
    ret.toSeq.sortBy(- _)
  }

  /**
   * Service the topic metadata request API
   */
  def handleTopicMetadataRequest(request: RequestChannel.Request) {
    val metadataRequest = request.requestObj.asInstanceOf[TopicMetadataRequest]
    val topicMetadata = getTopicMetadata(metadataRequest.topics.toSet)
    trace("Sending topic metadata %s for correlation id %d to client %s".format(topicMetadata.mkString(","), metadataRequest.correlationId, metadataRequest.clientId))
    val response = new TopicMetadataResponse(topicMetadata, metadataRequest.correlationId)
    requestChannel.sendResponse(new RequestChannel.Response(request, new BoundedByteBufferSend(response)))
  }

  private def getTopicMetadata(topics: Set[String]): Seq[TopicMetadata] = {
    val topicResponses = metadataCache.getTopicMetadata(topics)
    if (topics.size > 0 && topicResponses.size != topics.size) {
      val nonExistentTopics = topics -- topicResponses.map(_.topic).toSet
      val responsesForNonExistentTopics = nonExistentTopics.map { topic =>
        if (config.autoCreateTopicsEnable) {
          try {
            AdminUtils.createTopic(zkClient, topic, config.numPartitions, config.defaultReplicationFactor)
            info("Auto creation of topic %s with %d partitions and replication factor %d is successful!".format(topic, config.numPartitions, config.defaultReplicationFactor))
          } catch {
            case e: TopicExistsException => // let it go, possibly another broker created this topic
          }
          new TopicMetadata(topic, Seq.empty[PartitionMetadata], ErrorMapping.LeaderNotAvailableCode)
        } else {
          new TopicMetadata(topic, Seq.empty[PartitionMetadata], ErrorMapping.UnknownTopicOrPartitionCode)
        }
      }
      topicResponses.appendAll(responsesForNonExistentTopics)
    }

    topicResponses
  }

  /* 
   * Service the Offset commit API
   */
  def handleOffsetCommitRequest(request: RequestChannel.Request) {
    val offsetCommitRequest = request.requestObj.asInstanceOf[OffsetCommitRequest]
    val responseInfo = offsetCommitRequest.requestInfo.map{
      case (topicAndPartition, metaAndError) => {
        val topicDirs = new ZKGroupTopicDirs(offsetCommitRequest.groupId, topicAndPartition.topic)
        try {
          ensureTopicExists(topicAndPartition.topic)
          if(metaAndError.metadata != null && metaAndError.metadata.length > config.offsetMetadataMaxSize) {
            (topicAndPartition, ErrorMapping.OffsetMetadataTooLargeCode)
          } else {
            ZkUtils.updatePersistentPath(zkClient, topicDirs.consumerOffsetDir + "/" +
              topicAndPartition.partition, metaAndError.offset.toString)
            (topicAndPartition, ErrorMapping.NoError)
          }
        } catch {
          case e: Throwable => (topicAndPartition, ErrorMapping.codeFor(e.getClass.asInstanceOf[Class[Throwable]]))
        }
      }
    }
    val response = new OffsetCommitResponse(responseInfo, 
                                            offsetCommitRequest.correlationId)
    requestChannel.sendResponse(new RequestChannel.Response(request, new BoundedByteBufferSend(response)))
  }

  /*
   * Service the Offset fetch API
   */
  def handleOffsetFetchRequest(request: RequestChannel.Request) {
    val offsetFetchRequest = request.requestObj.asInstanceOf[OffsetFetchRequest]
    val responseInfo = offsetFetchRequest.requestInfo.map( t => {
      val topicDirs = new ZKGroupTopicDirs(offsetFetchRequest.groupId, t.topic)
      try {
        ensureTopicExists(t.topic)
        val payloadOpt = ZkUtils.readDataMaybeNull(zkClient, topicDirs.consumerOffsetDir + "/" + t.partition)._1
        payloadOpt match {
          case Some(payload) => {
            (t, OffsetMetadataAndError(offset=payload.toLong, error=ErrorMapping.NoError))
          } 
          case None => (t, OffsetMetadataAndError(OffsetMetadataAndError.InvalidOffset, OffsetMetadataAndError.NoMetadata,
                          ErrorMapping.UnknownTopicOrPartitionCode))
        }
      } catch {
        case e: Throwable =>
          (t, OffsetMetadataAndError(OffsetMetadataAndError.InvalidOffset, OffsetMetadataAndError.NoMetadata,
             ErrorMapping.codeFor(e.getClass.asInstanceOf[Class[Throwable]])))
      }
    })
    val response = new OffsetFetchResponse(collection.immutable.Map(responseInfo: _*), 
                                           offsetFetchRequest.correlationId)
    requestChannel.sendResponse(new RequestChannel.Response(request, new BoundedByteBufferSend(response)))
  }

  def close() {
    debug("Shutting down.")
    fetchRequestPurgatory.shutdown()
    producerRequestPurgatory.shutdown()
    debug("Shut down complete.")
  }

  private [kafka] trait MetricKey {
    def keyLabel: String
  }
  private [kafka] object MetricKey {
    val globalLabel = "All"
  }

  private [kafka] case class RequestKey(topic: String, partition: Int)
          extends MetricKey {

    def this(topicAndPartition: TopicAndPartition) = this(topicAndPartition.topic, topicAndPartition.partition)

    def topicAndPartition = TopicAndPartition(topic, partition)

    override def keyLabel = "%s-%d".format(topic, partition)
  }

  /**
   * A delayed fetch request
   */
  class DelayedFetch(keys: Seq[RequestKey], request: RequestChannel.Request, val fetch: FetchRequest, delayMs: Long, initialSize: Long)
    extends DelayedRequest(keys, request, delayMs) {
    val bytesAccumulated = new AtomicLong(initialSize)
  }

  /**
   * A holding pen for fetch requests waiting to be satisfied
   */
  class FetchRequestPurgatory(requestChannel: RequestChannel, purgeInterval: Int)
          extends RequestPurgatory[DelayedFetch, Int](brokerId, purgeInterval) {
    this.logIdent = "[FetchRequestPurgatory-%d] ".format(brokerId)

    /**
     * A fetch request is satisfied when it has accumulated enough data to meet the min_bytes field
     */
    def checkSatisfied(messageSizeInBytes: Int, delayedFetch: DelayedFetch): Boolean = {
      val accumulatedSize = delayedFetch.bytesAccumulated.addAndGet(messageSizeInBytes)
      accumulatedSize >= delayedFetch.fetch.minBytes
    }

    /**
     * When a request expires just answer it with whatever data is present
     */
    def expire(delayed: DelayedFetch) {
      debug("Expiring fetch request %s.".format(delayed.fetch))
      try {
        val topicData = readMessageSets(delayed.fetch)
        val response = FetchResponse(delayed.fetch.correlationId, topicData)
        val fromFollower = delayed.fetch.isFromFollower
        delayedRequestMetrics.recordDelayedFetchExpired(fromFollower)
        requestChannel.sendResponse(new RequestChannel.Response(delayed.request, new FetchResponseSend(response)))
      }
      catch {
        case e1: LeaderNotAvailableException =>
          debug("Leader changed before fetch request %s expired.".format(delayed.fetch))
        case e2: UnknownTopicOrPartitionException =>
          debug("Replica went offline before fetch request %s expired.".format(delayed.fetch))
      }
    }
  }

  class DelayedProduce(keys: Seq[RequestKey],
                       request: RequestChannel.Request,
                       initialErrorsAndOffsets: Map[TopicAndPartition, ProducerResponseStatus],
                       val produce: ProducerRequest,
                       delayMs: Long)
          extends DelayedRequest(keys, request, delayMs) with Logging {

    /**
     * Map of (topic, partition) -> partition status
     * The values in this map don't need to be synchronized since updates to the
     * values are effectively synchronized by the ProducerRequestPurgatory's
     * update method
     */
    private [kafka] val partitionStatus = keys.map(requestKey => {
      val producerResponseStatus = initialErrorsAndOffsets(TopicAndPartition(requestKey.topic, requestKey.partition))
      // if there was an error in writing to the local replica's log, then don't
      // wait for acks on this partition
      val (acksPending, error, nextOffset) =
        if (producerResponseStatus.error == ErrorMapping.NoError) {
          // Timeout error state will be cleared when requiredAcks are received
          (true, ErrorMapping.RequestTimedOutCode, producerResponseStatus.offset)
        }
        else (false, producerResponseStatus.error, producerResponseStatus.offset)

      val initialStatus = PartitionStatus(acksPending, error, nextOffset)
      trace("Initial partition status for %s = %s".format(requestKey.keyLabel, initialStatus))
      (requestKey, initialStatus)
    }).toMap

    def respond() {
      val finalErrorsAndOffsets = initialErrorsAndOffsets.map(
        status => {
          val pstat = partitionStatus(new RequestKey(status._1))
          (status._1, ProducerResponseStatus(pstat.error, pstat.requiredOffset))
        })
      
      val response = ProducerResponse(produce.correlationId, finalErrorsAndOffsets)

      requestChannel.sendResponse(new RequestChannel.Response(
        request, new BoundedByteBufferSend(response)))
    }

    /**
     * Returns true if this delayed produce request is satisfied (or more
     * accurately, unblocked) -- this is the case if for every partition:
     * Case A: This broker is not the leader: unblock - should return error.
     * Case B: This broker is the leader:
     *   B.1 - If there was a localError (when writing to the local log): unblock - should return error
     *   B.2 - else, at least requiredAcks replicas should be caught up to this request.
     *
     * As partitions become acknowledged, we may be able to unblock
     * DelayedFetchRequests that are pending on those partitions.
     */
    def isSatisfied(followerFetchRequestKey: RequestKey) = {
      val topic = followerFetchRequestKey.topic
      val partitionId = followerFetchRequestKey.partition
      val key = RequestKey(topic, partitionId)
      val fetchPartitionStatus = partitionStatus(key)
      trace("Checking producer request satisfaction for %s-%d, acksPending = %b"
        .format(topic, partitionId, fetchPartitionStatus.acksPending))
      if (fetchPartitionStatus.acksPending) {
        val partitionOpt = replicaManager.getPartition(topic, partitionId)
        val (hasEnough, errorCode) = partitionOpt match {
          case Some(partition) =>
            partition.checkEnoughReplicasReachOffset(fetchPartitionStatus.requiredOffset, produce.requiredAcks)
          case None =>
            (false, ErrorMapping.UnknownTopicOrPartitionCode)
        }
        if (errorCode != ErrorMapping.NoError) {
          fetchPartitionStatus.acksPending = false
          fetchPartitionStatus.error = errorCode
        } else if (hasEnough) {
          fetchPartitionStatus.acksPending = false
          fetchPartitionStatus.error = ErrorMapping.NoError
        }
        if (!fetchPartitionStatus.acksPending) {
          val messageSizeInBytes = produce.topicPartitionMessageSizeMap(followerFetchRequestKey.topicAndPartition)
          maybeUnblockDelayedFetchRequests(topic, partitionId, messageSizeInBytes)
        }
      }

      // unblocked if there are no partitions with pending acks
      val satisfied = ! partitionStatus.exists(p => p._2.acksPending)
      trace("Producer request satisfaction for %s-%d = %b".format(topic, partitionId, satisfied))
      satisfied
    }

    case class PartitionStatus(var acksPending: Boolean,
                          var error: Short,
                          requiredOffset: Long) {
      def setThisBrokerNotLeader() {
        error = ErrorMapping.NotLeaderForPartitionCode
        acksPending = false
      }

      override def toString =
        "acksPending:%b, error: %d, requiredOffset: %d".format(
          acksPending, error, requiredOffset
        )
    }
  }

  /**
   * A holding pen for produce requests waiting to be satisfied.
   */
  private [kafka] class ProducerRequestPurgatory(purgeInterval: Int)
          extends RequestPurgatory[DelayedProduce, RequestKey](brokerId, purgeInterval) {
    this.logIdent = "[ProducerRequestPurgatory-%d] ".format(brokerId)

    protected def checkSatisfied(followerFetchRequestKey: RequestKey,
                                 delayedProduce: DelayedProduce) =
      delayedProduce.isSatisfied(followerFetchRequestKey)

    /**
     * Handle an expired delayed request
     */
    protected def expire(delayedProduce: DelayedProduce) {
      for (partitionStatus <- delayedProduce.partitionStatus if partitionStatus._2.acksPending)
        delayedRequestMetrics.recordDelayedProducerKeyExpired(partitionStatus._1)

      delayedProduce.respond()
    }
  }

  private class DelayedRequestMetrics {
    private class DelayedProducerRequestMetrics(keyLabel: String = MetricKey.globalLabel) extends KafkaMetricsGroup {
      val expiredRequestMeter = newMeter(keyLabel + "ExpiresPerSecond", "requests", TimeUnit.SECONDS)
    }


    private class DelayedFetchRequestMetrics(forFollower: Boolean) extends KafkaMetricsGroup {
      private val metricPrefix = if (forFollower) "Follower" else "Consumer"

      val expiredRequestMeter = newMeter(metricPrefix + "ExpiresPerSecond", "requests", TimeUnit.SECONDS)
    }

    private val producerRequestMetricsForKey = {
      val valueFactory = (k: MetricKey) => new DelayedProducerRequestMetrics(k.keyLabel + "-")
      new Pool[MetricKey, DelayedProducerRequestMetrics](Some(valueFactory))
    }

    private val aggregateProduceRequestMetrics = new DelayedProducerRequestMetrics

    private val aggregateFollowerFetchRequestMetrics = new DelayedFetchRequestMetrics(forFollower = true)
    private val aggregateNonFollowerFetchRequestMetrics = new DelayedFetchRequestMetrics(forFollower = false)

    def recordDelayedProducerKeyExpired(key: MetricKey) {
      val keyMetrics = producerRequestMetricsForKey.getAndMaybePut(key)
      List(keyMetrics, aggregateProduceRequestMetrics).foreach(_.expiredRequestMeter.mark())
    }

    def recordDelayedFetchExpired(forFollower: Boolean) {
      val metrics = if (forFollower) aggregateFollowerFetchRequestMetrics
        else aggregateNonFollowerFetchRequestMetrics
      
      metrics.expiredRequestMeter.mark()
    }
  }
}

