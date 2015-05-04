#!/bin/bash

nohup ./bin/kafka-run-class.sh kafka.tools.KafkaMigrationTool --kafka.07.jar migrate/kafka_2.9.2-0.7.2.jar --zkclient.01.jar migrate/zkclient-0.1.jar --num.producers 6 --consumer.config=migrate/07consumer.properties --producer.config=migrate/08producer.properties --whitelist=r.3 &
