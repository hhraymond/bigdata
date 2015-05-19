#!/bin/bash

# Arrange by allen huang, 2014-11-12
# also see:
# http://kafka.apache.org/documentation.html
# https://cwiki.apache.org/confluence/display/KAFKA/System+Tools

./bin/kafka-console-producer.sh --broker-list kf16dg.prod.mediav.com:9092 --topic a.s.1
./bin/kafka-console-consumer.sh --zookeeper zk1dg.prod.mediav.com:2181/kafka --topic a.s.1 --group console1
./bin/kafka-producer-perf-test.sh --broker-list="kf16dg.mediav.com:9092,kf17dg.mediav.com:9092" --messages=100000 --initial-message-id=1 --request-num-acks=1 --compression-codec=1 --thread=5 --batch-size=300 --topic=a.s.1
./bin/kafka-consumer-perf-test.sh  --zookeeper zk1dg.prod.mediav.com:2181/kafka --topic a.s.1 --group console2 --show-detailed-stats
# Consumer Offset Checker
./bin/kafka-run-class.sh kafka.tools.ConsumerOffsetChecker --zookeeper zk1dg.prod.mediav.com:2181/kafka --topic a.s.1 --group console1 
# Get Offset Shell
./bin/kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list kf16dg.prod.mediav.com:9092 --topic a.s.1 --time -1
# Export Zookeeper Offsets
./bin/kafka-run-class.sh kafka.tools.ExportZkOffsets --zkconnect zk1dg.prod.mediav.com:2181/kafka --group console1 --output-file offsets.txt

offsets.txt
/consumers/group1/offsets/topic1/1-0:286894308
/consumers/group1/offsets/topic1/2-0:284803985

# Import Zookeeper Offsets
./bin/kafka-run-class.sh kafka.tools.ImportZkOffsets --zkconnect zk1dg.prod.mediav.com:2181/kafka --input-file offsets.txt
# Verify Consumer Rebalance
./bin/kafka-run-class.sh kafka.tools.VerifyConsumerRebalance --zookeeper.connect zk1dg.prod.mediav.com:2181/kafka --group console1
# Kafka Migration Tool
./bin/kafka-run-class.sh kafka.tools.KafkaMigrationTool --kafka.07.jar /data/kafka/migrate/kafka_2.9.2-0.7.2.jar --zkclient.01.jar /data/kafka/migrate/zkclient-0.1.jar --num.producers 16 --consumer.config=/data/kafka/migrate/07consumer.properties --producer.config=/data/kafka/migrate/08producer.properties --whitelist=a.s.*

# Dump Log Segment 
bin/kafka-run-class.sh kafka.tools.DumpLogSegments
# JMX Tool
bin/kafka-run-class.sh kafka.tools.JmxTool
# Mirror Maker
bin/kafka-run-class.sh kafka.tools.MirrorMaker
# Replay Log Producer
bin/kafka-run-class.sh kafka.tools.ReplayLogProducer
# Simple Consumer Shell
bin/kafka-run-class.sh kafka.tools.SimpleConsumerShell
# State Change Log Merger
bin/kafka-run-class.sh kafka.tools.StateChangeLogMerger
# Update Offsets In Zookeeper
bin/kafka-run-class.sh kafka.tools.UpdateOffsetsInZK
