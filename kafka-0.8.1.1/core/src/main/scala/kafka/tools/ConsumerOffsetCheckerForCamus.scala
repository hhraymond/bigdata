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

package kafka.tools

import joptsimple._
import org.I0Itec.zkclient.ZkClient
import kafka.utils.{Json, ZkUtils, ZKStringSerializer, Logging}
import kafka.consumer.SimpleConsumer
import kafka.api.{PartitionOffsetRequestInfo, OffsetRequest}
import kafka.common.{UnknownTopicOrPartitionException, TopicAndPartition}
import scala.collection._

import org.json.simple.JSONObject
import org.json.simple.JSONValue
import org.json.simple.parser.ContainerFactory
import org.json.simple.parser.JSONParser
import org.json.simple.parser.ParseException

object ConsumerOffsetCheckerForCamus extends Logging {

  private val consumerMap: mutable.Map[String, SimpleConsumer] = mutable.Map()

  private def getConsumer(host: String, port: Int): SimpleConsumer = {
    new SimpleConsumer(host, port, 10000, 100000, "ConsumerOffsetChecker")
  }

   def getLatestOffset(consumer: SimpleConsumer,
      topic: String, partition: Int): Long = {
    var try_count = 0
    var loop = true
    var latest = 0L
    //while(try_count < 2 && loop)  {
      try  {
        val topicAndPartition = TopicAndPartition(topic, partition)
        val request =
          OffsetRequest(immutable.Map(topicAndPartition -> PartitionOffsetRequestInfo(OffsetRequest.LatestTime, 1)))
        latest = consumer.getOffsetsBefore(request).partitionErrorAndOffsets(topicAndPartition).offsets.head
        loop = false
      } catch {
        case e: Exception => {
          error("exception caught when get latest offset,host="
              + consumer.host + ":" + consumer.port + ",topic=" + topic
              + ",partition=" + partition + ",trycount=" + try_count)
          error("exception:" + e.getMessage())
          error(e.getStackTraceString)
        }
      }

    //  try_count = try_count + 1
    //}
    latest
  }

  private def getConsumeInfo(zkClient: ZkClient, zkPath: String, topic: String,
                                  partition: String): (Option[Long], Option[String], Option[String], Option[Int]) = {
    try {
      val data = ZkUtils.readData(zkClient, "%s/%s/%s".format(zkPath, topic, partition))._1
      var offset: Long = 0L
      var time: String = ""
      var host: String = ""
      var port: Int = 0
      val obj = JSONValue.parse(data)
      debug("json parse obj:" + obj)
      obj match {
        case jsonObj: JSONObject =>
          debug("json parse jsonObj:" + jsonObj)
          offset = jsonObj.get("offset").toString.toLong
          time = jsonObj.get("readingTime").toString
          val broker = jsonObj.get("broker")
          broker match {
            case bObj: JSONObject =>
              host = bObj.get("host").toString
              port = bObj.get("port").toString.toInt
          }
      }
      //println("offset: " + offset + ", host: " + host + ", port: " + port);
      (Some(offset), Some(time), Some(host), Some(port))
    } catch {
      case t: Throwable =>
        error("Could not parse partition info", t)
        (None, None, None, None)
    }
  }

  private def processPartition(zkClient: ZkClient, zkPath: String,
                               topic: String, partition: String) {
    //println("processPartition, zkPath: " + zkPath + ", topic: " + topic + ", partition: " + partition)
    getConsumeInfo(zkClient, zkPath, topic, partition) match {
      case (Some(offset), Some(time), Some(host), Some(port)) =>
        //println("topic: %s, partition: %s, offset: %s, host: %s, port: %s".format(topic, partition, offset, host, port))
        val consumer = consumerMap.getOrElseUpdate(host + ":" + port, getConsumer(host, port))
        val logSize = getLatestOffset(consumer, topic, partition.toInt)

        val lag = logSize - offset
        println("%-10s %-5s %-15s %-15s %-12s %-20s %-30s".format(topic, partition, offset, logSize, lag, time, host+":"+port))
      case (None, None, None, None) =>
        error("%s%s : has no partition: %s".format(zkPath, topic, partition))
    }
  }

  private def processTopic(zkClient: ZkClient, zkPath: String, topic: String) {
    // e.g. /camus/consume/a.s.3
    val partitions = ZkUtils.getChildrenParentMayNotExist(zkClient, "%s/%s".format(zkPath, topic)).toList
    //println("processTopic, partitions: " + partitions.toString())
    partitions.sorted.foreach {
      partition =>  processPartition(zkClient, zkPath, topic, partition)
    }
  }

  def main(args: Array[String]) {
    val parser = new OptionParser()

    val camusZkConnectOpt = parser.accepts("camuszkconnect", "Camus zooKeeper connect string, like: zk1dg.prod.mediav.com:2191").
            withRequiredArg().ofType(classOf[String])
    val topicsOpt = parser.accepts("topic",
            "Comma-separated list of consumer topics (all topics if absent).").
            withRequiredArg().ofType(classOf[String])
    val appZkPath = parser.accepts("appzkpath", "Application ZooKeeper path, like: /camus/consume").
            withRequiredArg().ofType(classOf[String])
    parser.accepts("help", "Print this message.")

    val options = parser.parse(args : _*)

    if (options.has("help")) {
       parser.printHelpOn(System.out)
       System.exit(0)
    }

    val camusZkConnect = options.valueOf(camusZkConnectOpt)
    val zookeeperPath = options.valueOf(appZkPath)
    val topics = if (options.has(topicsOpt)) Some(options.valueOf(topicsOpt))
      else None

    var zkClient: ZkClient = null
    try {
      zkClient = new ZkClient(camusZkConnect, 30000, 30000, ZKStringSerializer)

      val topicList = topics match {
        case Some(x) => x.split(",").view.toList
        case None => Nil
      }

      debug("camusZkConnect = %s; zkPath = %s; topics = %s".format(
        camusZkConnect, zookeeperPath, topicList.toString()))
      //println("args: camusZkConnect = %s; zkPath = %s; topics = %s".format(
      //  camusZkConnect, zookeeperPath, topicList.toString()))
      println("%-10s %-5s %-15s %-15s %-12s %-20s %-30s".format("Topic", "Pid", "Offset", "logSize", "Lag", "ReadingTime", "Leader"))
      topicList.sorted.foreach {
        topic => processTopic(zkClient, zookeeperPath, topic)
      }

      for (consumer <- consumerMap.values) {
        consumer.close()
      }
    }
    finally {
      for (consumer <- consumerMap.values) {
        consumer.close()
      }
      if (zkClient != null)
        zkClient.close()
    }
  }
}

