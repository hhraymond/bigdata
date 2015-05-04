#!/bin/bash

pid=$(sudo ps xf | grep "kafka\.Kafka config/mv-server" | grep -v grep | awk '{print $1}')

if [ "x$pid" != "x" ]; then
  sudo kill $pid
  echo "Killed broker process: $pid."
  sleep 1
  pid=$(sudo ps xf | grep "kafka\.Kafka config/mv-server" | grep -v grep | awk '{print $1}')
  while [[ "x$pid" != "x" ]]
  do
    echo "Waiting for $pid shutted down..."
    sleep 1
    pid=$(sudo ps xf | grep "kafka\.Kafka config/mv-server" | grep -v grep | awk '{print $1}')
  done

  echo "Success: Server process is shutted down..."
else
  echo "WARN: Server process NOT found, please check..."
fi
