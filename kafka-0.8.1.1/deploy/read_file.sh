#!/bin/bash

FILE="kafka08/machine.list"

#list=`sed -n "/^kf/p" $FILE`
list=`awk '$1 !~ /^#/' $FILE`
brokerId=`awk -F '[ :.]' '$1 !~ /^#/ {print $1}' $FILE`
host=`awk -F '[ :.]' '$1 !~ /^#/ {print $3}' $FILE`
port=`awk -F '[ :.]' '$1 !~ /^#/ {print $7}' $FILE`
echo "list:"
echo $list
echo "brokerId:"
echo $brokerId
echo "host:"
echo $host
echo "port:"
echo $port
echo ""

list=`awk -F '[ :]' '$1 !~ /^#/ {print $1,$3,$4}' OFS="," $FILE`
for h in $list; do
  array=(${h//,/ })
  id=${array[0]}
  hostname=${array[1]}
  port=${array[2]}
  host=`echo $hostname | awk -F. '{print $1}'`
  echo $h
  echo $id
  echo $hostname
  echo $port
  echo $host
  break
  #echo ""
  #echo ""
done
