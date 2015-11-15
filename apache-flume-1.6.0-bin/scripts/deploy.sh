#!/bin/bash
if [ $# -lt 1 ]; then
  echo "Usage: bash deploy.sh dc|rc "
  exit 1
fi
env=$1
echo "deploy $env "

PKG="apache-flume-1.6.0"

if [ ! -d "flume-data" ]; then
  mkdir flume-data
fi

if [ -d "$PKG" ]; then
  mv $PKG $PKG.bak$(date +%Y%m%d-%H%M%S)
fi

rm -f $PKG.tgz
wget http://10.110.10.xx:20001/$PKG.tgz
tar -zxvf $PKG.tgz

ln -sfT $PKG flume 

cd flume
mv scripts/*.sh bin/
cp conf/flume-conf-$env.properties conf/flume-conf.properties

#stop java process
./bin/stop.sh

# start
./bin/start.sh
