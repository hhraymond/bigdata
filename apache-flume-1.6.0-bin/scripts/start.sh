#!/bin/bash

APP_NAME=org.apache.flume.node.Application

nohup bin/flume-ng agent -n a1 -c conf -f conf/flume-conf.properties &

sleep 1
pid=$(ps xf | grep "$APP_NAME" | grep -v grep | awk '{print $1}')
if [ "x$pid" != "x" ]; then
  echo "Success: [$APP_NAME] started, pid: $pid."
else
  echo "ERROR:   [$APP_NAME] start failed..."
fi
