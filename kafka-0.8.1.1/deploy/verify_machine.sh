#!/bin/bash

# cpu logic core
cpu_num=`cat /proc/cpuinfo | grep "processor"|wc -l`
echo "cpu logic core is $cpu_num;"
if [[ $cpu_num -lt 16 ]]; then
  echo "error, cpu logic core is not enough ( >= 16 ), standard: 24"
  exit 1
fi

# memory size
mem_size=`free | grep Mem | awk '{print $2}'`
echo "memory size is $mem_size;"
if [[ $mem_size -lt 49000000 ]]; then
  echo "error, memory size is not enough ( >= 48G ), standard: 128G"
  exit 1
fi

# disk num
disk_num=`df -h | grep data* | awk '{print $1}' | sort -u | wc -l`
echo "disk number $disk_num;"
if [[ $disk_num -ne 8 ]]; then
  echo "error, disk number is not 8. standard: 1 system disk + 7 data disk"
  exit 1
fi

# check system disk, gap should less then 1G
sys_disk_size=`df | grep data$ | awk '{print $2}'`
echo "system disk size is $sys_disk_size;"
if [[ $sys_disk_size -lt 364000000 ]]; then
  echo "error, size is not enough ( >= 347G ), standard: 347G"
  exit 1
fi

# check data disk, gap should less then 1G
for size in `df | grep data[1-7] | awk '{print $2}'`; do
  echo "data disk size is $size;"
  if [[ $size -lt 1171000000 ]]; then
    echo "error, size is not enough ( >= 1.1T ), standard: 1.1T"
    exit 1
  fi
done

for type in `df -T | grep data* | awk '{print $2}'`; do
  echo "disk type is $type;"
  if [[ $type -ne xfs ]]; then
    echo "error, type is not xfs, standard: xfs"
    exit 1
  fi
done

# net device
bond=`/sbin/ifconfig bond0 | grep "inet addr"`

if [[ x$bond = x ]]; then
  echo "error, no binding card!"
  exit 1
else
  echo "binding card:"
  echo "$bond;"
fi

for netcard in `/sbin/ifconfig | grep eth | awk '{print $1}'`; do
  speed=`sudo /sbin/ethtool $netcard | grep Speed |awk '{print $2}'`
  echo "$netcard speed is $speed;"
  if [[ $speed != "1000Mb/s" ]]; then
    echo "error, $netcard speed is not 1000Mb/s, standard: 1000Mb/s"
    exit 1
  fi
done

echo "Done. Check success."
