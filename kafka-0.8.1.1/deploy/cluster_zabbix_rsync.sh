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
  echo "Syncing $h"
  ret=$(ssh $h "sudo zabbix-rsync")
  echo $ret
done

echo "done"
