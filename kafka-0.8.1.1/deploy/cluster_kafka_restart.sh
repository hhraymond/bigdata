#!/bin/bash

if [[ $# -ne 1 ]]; then
  echo "should run with arg <machine.list>, eg: $0 kafka08/machine.list"
  exit 1
fi

LIST=$1
shift 1

list=`awk -F '[ :.]' '$1 !~ /^#/ {print $3}' $LIST`

# verify.sh
for m in $list; do
  echo "restart kafka machine: $m"
  res=`ssh $m "cd /data/kafka08; \
               sudo bash stop-mv-server.sh; \
               sleep 1; \
               sudo bash start-mv-server.sh"`
  echo $res
  ret=`echo $res | grep "start success"`
  if [[ "x$ret" != x ]]; then
     echo "$m broker restart success."
  else
     echo "$m broker restart failed.."
  fi
  echo ""
done

echo "Done."
