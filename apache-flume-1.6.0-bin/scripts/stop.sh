#!/bin/bash

#set -x
#bin=`dirname "$0"`
#APP_HOME=`cd "${bin}/.."; pwd`
APP_NAME=org.apache.flume.node.Application

pid=$(ps xf | grep "$APP_NAME" | grep -v grep | awk '{print $1}')
if [ "x$pid" != "x" ]; then
  kill $pid
  echo "Killed [$APP_NAME]: $pid."
  sleep 1
  pid=$(ps xf | grep "$APP_NAME" | grep -v grep | awk '{print $1}')
  while [[ "x$pid" != "x" ]]
  do
    echo "Waiting for $pid shutted down..."
    sleep 1
    pid=$(ps xf | grep "$APP_NAME" | grep -v grep | awk '{print $1}')
  done

  echo "Success: [$APP_NAME] is shutted down..."
else
  echo "WARN: [$APP_NAME] process NOT found, please check..."
fi
