#!/bin/bash

function gen()
{
  echo "     {"
  echo "         \"topic\": \"d.x.3\","
  echo "         \"partition\": $1,"
  echo "         \"replicas\": [$2]"
  echo "     },"
}

echo "
{
  \"version\": 1,
  \"partitions\": ["

j=7
for i in {0..2}; do
  gen $i $j
  let j=j+1
  if [[ 15 -eq $j ]]; then
     j=1
  fi
done

echo "  ]
}"
