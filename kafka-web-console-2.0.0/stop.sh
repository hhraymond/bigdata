#!/bin/bash

ps -ef | grep kafka-web-console | grep -v grep | grep -v 'restart.sh' | awk '{print $2}' | xargs kill
