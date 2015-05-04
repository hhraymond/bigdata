/*
 * Copyright 2014 Claude Mamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package controllers

import play.api.libs.json.Json
import play.api.mvc._
import models.OffsetPoint
import java.sql.Timestamp
import java.text.SimpleDateFormat

object OffsetHistory extends Controller {

  def show(consumerGroup: String, topic: String, zookeeper: String, from_time: String, to_time: String) = Action {
    //val to_millis = System.currentTimeMillis()
    //val from_millis = to_millis - 60 * 60 * 1000  // 1 hour before

    val sdf = new SimpleDateFormat("yyyyMMddHHmm")
    val from_millis = sdf.parse(from_time).getTime()
    val to_millis = sdf.parse(to_time).getTime()

    models.Zookeeper.findByName(zookeeper) match {
      case Some(zk) => {
        models.OffsetHistory.findByZookeeperIdAndTopic(zk.id, topic) match {
          case Some(oH) => Ok(Json.toJson(OffsetPoint.findByOffsetHistoryIdAndConsumerGroup(oH.id, consumerGroup, new Timestamp(from_millis), new Timestamp(to_millis))))
          case _ => Ok(Json.toJson(Seq[String]()))
        }
      }
      case _ => Ok(Json.toJson(Seq[String]()))
    }
  }

}
