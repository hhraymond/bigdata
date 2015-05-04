#!/bin/bash

#if [[ `whoami` != "root" ]]; then
#  echo "error: should run in user root."
#  exit 1
#fi

if [[ $# -ne 1 ]]; then
  echo "should run with args <machine.list>, eg: $0 kafka08/machine.list"
  exit 1
fi

LIST=$1
shift 1

list=`awk -F '[ :.]' '$1 !~ /^#/ {print $3}' $LIST`

# install jdk
for m in $list; do
  echo "installing jdk for: $m"
  ssh $m "sudo yum --enablerepo=rhel-other-package clean metadata;\
          echo y | sudo yum install jdk"
done

echo "done."

#yum install jdk
#ln -sfT /opt/jdk1.6.0_45 /opt/jdk
#
#echo "" >> /etc/profile
#echo "export JAVA_HOME=/opt/jdk" >> /etc/profile
#echo "export PATH=\$JAVA_HOME/bin:\$PATH" >> /etc/profile

