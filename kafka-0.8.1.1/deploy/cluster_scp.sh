#!/bin/bash

if [[ $# -lt 2 ]]; then
  echo "should run with args <machine.list> <file1> <file2>, eg: $0 machine.list verify_machine.sh"
  exit 1
fi

LIST=$1
shift 1
echo "scp file: $@"

host=`awk -F '[ :.]' '$1 !~ /^#/ {print $3}' $LIST`
for h in $host
do
  echo "scp to $h"
  scp $@ $h:~
done

echo "done"
