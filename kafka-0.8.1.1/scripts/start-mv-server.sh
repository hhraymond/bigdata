#!/bin/bash

sudo bash bin/kafka-server-start.sh -daemon config/mv-server.properties

sleep 1
pid=$(sudo ps xf | grep "kafka\.Kafka config/mv-server" | grep -v grep | awk '{print $1}')
if [ "x$pid" != "x" ]; then
  echo "Success: Server process $pid, start success."
else
  echo "WARN: Server process start failed..."
fi
