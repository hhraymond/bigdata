#!/bin/bash

if [[ $# -lt 1 ]]; then
  echo "should run with args <machine.list>, eg: $0 machine.list"
  exit 1
fi

LIST=$1
shift 1

host=`awk -F '[ :.]' '$1 !~ /^#/ {print $3}' $LIST`
for h in $host
do
  echo "rm logs for $h"
  ret=$(ssh $h "sudo rm -rf /data/kafka_2.9.2-0.8.1.1-r7570/logs")
  #ret=$(ssh $h "sudo rm -rf /data1/kafka-logs /data2/kafka-logs /data3/kafka-logs /data4/kafka-logs /data5/kafka-logs /data6/kafka-logs /data7/kafka-logs")
  echo $ret
done

echo "done"
