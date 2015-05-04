#!/bin/env python
__author__ = 'xianghl'

import os
import OffsetStorage
from datetime import datetime, timedelta
import subprocess

broker_list = 'kf40dg.prod.mediav.com:9092,kf41dg.prod.mediav.com:9092,kf42dg.prod.mediav.com:9092'
topics = 'e.x.3,e.u.3,e.s.3,e.c.3'
hadoop_meta_dir = '/user/battle08/meta/exchange'


base_dir, _ = os.path.split(os.path.abspath(__file__))

check_time = datetime.now() - timedelta(days=1)

check_point = '/user/hadoop/check_point/exchange_daily/' + check_time.strftime('%Y-%m-%d')

temp_hdfs_dir = base_dir + '/exchange_daily/hadoop_offset/' + check_time.strftime('%Y-%m-%d/%H')

#make parent dir

ret = subprocess.call([OffsetStorage.hadoop_bin, 'fs', '-mkdir', '-p', os.path.split(check_point)[0]])

OffsetStorage.check_hdfs_ok(broker_list, topics, hadoop_meta_dir, temp_hdfs_dir, check_point)


