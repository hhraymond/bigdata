#!/bin/bash

function gen()
{
  echo "     {"
  echo "         \"topic\": \"d.u.3\","
  echo "         \"partition\": $1,"
  echo "         \"replicas\": [$2]"
  echo "     },"
}

echo "
{
  \"version\": 1,
  \"partitions\": ["

j=10
for i in {0..89}; do
  gen $i $j
  let j=j+1
  if [[ 15 -eq $j ]]; then
     j=1
  fi
done

echo "  ]
}"
