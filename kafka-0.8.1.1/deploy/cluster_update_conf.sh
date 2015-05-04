#!/bin/bash

if [[ $# -ne 2 ]]; then
  echo "should run with arg <machine.list> <config>, eg: $0 kafka08/machine.list kafka08/mv-server.properties"
  exit 1
fi

LIST=$1
CONF=$2
shift 3

conf_file=`basename $CONF`
echo "machine list: $LIST, config; $CONF"

list=`awk -F '[ :]' '$1 !~ /^#/ {print $1,$3,$4}' OFS="," $LIST`
for h in $list; do
  # 17,kf47dg.prod.mediav.com,9092
  array=(${h//,/ })
  id=${array[0]}        # 17
  host_name=${array[1]} # kf47dg.prod.mediav.com
  port=${array[2]}      # 9092
  host=`echo $host_name | awk -F. '{print $1}'` # kf47dg
  echo "Updating broker id: $id, host_name: $host_name, port: $port, host: $host"

  cp $CONF $conf_file.tmp
  sed -i "s/broker\.id=.*/broker\.id=$id/g;
          s/host\.name=.*/host\.name=$host_name/g;
          s/port=.*/port=$port/g" $conf_file.tmp

  echo "scp conf to: $host"
  scp $PKG $conf_file.tmp $host:~
  ssh $host "sudo mv ~/$conf_file.tmp /data/kafka08/config/mv-server.properties"
  rm -f $conf_file.tmp
  echo ""
done

echo "Done."
