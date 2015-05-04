#!/bin/bash

export JAVA_HOME=/opt/jdk
export PLAY_HOME=/opt/play
export PATH=$PLAY_HOME:$JAVA_HOME/bin:$PATH

install_dir=/home/mediav/kafka-web-console-2.0.0

connection=`netstat | grep kf | wc -l`
fail=`tail -n 10 $install_dir/nohup.out | grep 1st`
pid=`ps -ef | grep kafka-web-console | grep -v grep | grep -v 'restart.sh' | awk '{print $2}'`
time=`date "+%F %H:%M:%S"`

if [[ $connection -gt 1000 || $connection -eq 0 || "x$fail" != "x" ||  "x$1" = "xforce" ]]; then
  echo "$time $pid need restart, force: $1, connection number: $connection, fail: $fail." >> $install_dir/restart.log
  #echo $pid
  kill $pid
  sleep 1
  mv $install_dir/nohup.out $install_dir/nohup.out.bak
  rm -f $install_dir/target/universal/stage/RUNNING_PID
  nohup $install_dir/target/universal/stage/bin/kafka-web-console  > $install_dir/nohup.out 2>&1 &
else 
  echo "$time $pid no need restart, connection number: $connection." >> $install_dir/restart.log
fi
