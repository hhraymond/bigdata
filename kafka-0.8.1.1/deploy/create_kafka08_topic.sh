#!/bin/bash

/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic a.s.3 --partitions 30 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic d.b.3 --partitions 30 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic d.e.3 --partitions 90 --replication-factor 1 --config retention.ms=86400000
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic d.s.3 --partitions 30 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic d.u.3 --partitions 90 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic d.x.3 --partitions 90 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic e.e.3 --partitions 60 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic e.s.3 --partitions 60 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic e.u.3 --partitions 60 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic e.x.3 --partitions 60 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic t.m.3 --partitions 16 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic u.x.3 --partitions 16 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic u.u.3 --partitions 16 --replication-factor 2

/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic a.c.3 --partitions 6 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic d.c.3 --partitions 6 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic d.i.3 --partitions 6 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic e.c.3 --partitions 6 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic m.a.3 --partitions 6 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic t.b.3 --partitions 6 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic t.c.3 --partitions 6 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic t.r.3 --partitions 6 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic t.s.3 --partitions 6 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic t.t.3 --partitions 6 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic t.v.3 --partitions 6 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic t.e.3 --partitions 6 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic t.i.3 --partitions 6 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic t.z.3 --partitions 6 --replication-factor 2
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic s.t.3 --partitions 6 --replication-factor 2

/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic r.c.3 --partitions 6 --replication-factor 1

# add at 2015-02-04
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic d.x.3.m --partitions 16 --replication-factor 1
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic d.u.3.m --partitions 16 --replication-factor 1
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic d.s.3.m --partitions 16 --replication-factor 1
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic d.b.3.m --partitions 16 --replication-factor 1
/data/kafka08/bin/kafka-topics.sh --zookeeper zk1dg.prod.mediav.com:2191/kafka08 --create --topic d.c.3.m --partitions 6 --replication-factor 1
