#!/bin/bash

# Arrange by huangzhen@mediav.com, 2014-11-12
# also see:
# http://kafka.apache.org/documentation.html
# https://cwiki.apache.org/confluence/display/KAFKA/Replication+tools

## Controlled Shutdown ##
./bin/kafka-run-class.sh kafka.admin.ShutdownBroker --zookeeper zk3dg.prod.mediav.com:2191/kafka08 --broker 0 --num.retries 3 --retry.interval.ms 60

## List Topic ##
./bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --list
./bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --describe --topic a.s.1

## Create Topic ##
./bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic a.s.1 --partitions 30 --replication-factor 2
## Alter Retention Per Topic Partition
./bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic a.s.1 --partitions 1 
        --replication-factor 1 --config retention.ms=86400000
./bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --alter --topic a.s.1 
        --deleteConfig retention.ms

## Add Partition ##
# Partition Can only be Add, can't decrease !!!
./bin/kafka-topics.sh --zookeeper zk2dg.prod.mediav.com:2191/kafka08 --alter --topic a.s.1 --partition 40


## Delete Topic ##
# This script was bad. Only deleted in zookeeper, but log NOT deleted, so can not be trust !!!
./bin/kafka-run-class.sh kafka.admin.DeleteTopicCommand --zookeeper zk2dg.prod.mediav.com:2191/kafka08 --topic a.s.1


## Reassign Partitions ##
# when add broker, can use this command to reassign
# --generate, --execute or --verify
# 1.generate
./bin/kafka-reassign-partitions.sh --zookeeper pt3dg.prod.mediav.com:2181/my-kafka-0.8.1.1 --broker-list "0,1,2" --topics-to-move-json-file topics-to-move.json --generate

topics-to-move.json
{"topics":
     [{"topic": "a.u.2"}],
     "version":1
}

# 2. execute
./bin/kafka-reassign-partitions.sh --zookeeper pt3dg.prod.mediav.com:2181/my-kafka-0.8.1.1 --execute --reassignment-json-file reassignment.json

reassignment.json
{"version":1,"partitions":[{"topic":"a.u.2","partition":8,"replicas":[0,1]},{"topic":"a.u.2","partition":0,"replicas":[1,2]},{"topic":"a.u.2","partition":6,"replicas":[1,2]},{"topic":"a.u.2","partition":7,"replicas":[2,0]},{"topic":"a.u.2","partition":3,"replicas":[1,0]},{"topic":"a.u.2","partition":9,"replicas":[1,0]},{"topic":"a.u.2","partition":4,"replicas":[2,1]},{"topic":"a.u.2","partition":1,"replicas":[2,0]},{"topic":"a.u.2","partition":2,"replicas":[0,1]},{"topic":"a.u.2","partition":5,"replicas":[0,2]}]}

## Preferred Replica Leader Election ##
# for all:
./bin/kafka-preferred-replica-election.sh --zookeeper zk3dg.prod.mediav.com:2191/kafka08
# for specified:
./bin/kafka-preferred-replica-election.sh --zookeeper zk3dg.prod.mediav.com:2191/kafka08 --path-to-json-file topicPartitionList.json

topicPartitionList.json
{
 "partitions":
  [
    {"topic": "topic1", "partition": 0},
    {"topic": "topic1", "partition": 1},
    {"topic": "topic1", "partition": 2},
 
    {"topic": "topic2", "partition": 0},
    {"topic": "topic2", "partition": 1}
  ]
}
## StateChangeLogMerger ##
bin/kafka-run-class.sh kafka.tools.StateChangeLogMerger
