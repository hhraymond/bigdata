#!/bin/bash

mv /home/mediav/kafka-web-console-2.0.0/nohup.out /home/mediav/kafka-web-console-2.0.0/nohup.out.bak
nohup /home/mediav/kafka-web-console-2.0.0/target/universal/stage/bin/kafka-web-console  > /home/mediav/kafka-web-console-2.0.0/nohup.out 2>&1 &
