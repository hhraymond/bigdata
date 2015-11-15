#!/bin/bash

alive=`ps xf | grep -i 'org.apache.flume.node.Application' | grep -v grep | wc -l`

if [[ "x"$alive = "x0" ]]; then
   cd /home/urs/project/flume/flume
   bash bin/start.sh
   echo "restart flume agent at " `date` >> logs/restart.log
fi
#echo "check" `date` >> /home/urs/project/flume/flume/logs/restart.log
