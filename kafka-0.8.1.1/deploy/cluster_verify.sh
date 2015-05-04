#!/bin/bash

if [[ $# -ne 2 ]]; then
  echo "should run with arg <machine.list> <verify script>, eg: $0 kafka08/machine.list verify_machine.sh"
  exit 1
fi

LIST=$1
VRFY=$2
shift 2

vfile=`basename $VRFY`
list=`awk -F '[ :.]' '$1 !~ /^#/ {print $3}' $LIST`

# verify.sh
for m in $list; do
  echo "verify machine: $m"
  scp $vfile $m:~
  res=`ssh $m "sudo bash $vfile"`
  echo $res
  ret=`echo $res | grep "success"`
  if [[ "x$ret" != x ]]; then
     echo "verify machine: $m passed."
  else
     echo "verify machine: $m failed."
  fi
  echo ""
done

echo "Done."
