#!/bin/bash

/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08rtlp --create --topic r.3 --partitions 16 --replication-factor 2
