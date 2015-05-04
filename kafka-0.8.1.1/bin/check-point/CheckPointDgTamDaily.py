#!/bin/env python
__author__ = 'xianghl'

import os
import OffsetStorage
from datetime import datetime, timedelta
import subprocess

broker_list = 'kf43dg.prod.mediav.com:9092,kf44dg.prod.mediav.com:9092,kf45dg.prod.mediav.com:9092'
topics = 'a.s.3,a.c.3,t.b.3,t.c.3,t.m.3,t.t.3,t.v.3,t.z.3'
hadoop_meta_dir = '/user/battle08/meta/tam'


base_dir, _ = os.path.split(os.path.abspath(__file__))

check_time = datetime.now() - timedelta(days=1)

check_point = '/user/hadoop/check_point/tam_daily/' + check_time.strftime('%Y-%m-%d')

temp_hdfs_dir = base_dir + '/tam_daily/hadoop_offset/' + check_time.strftime('%Y-%m-%d/%H')

#make parent dir

ret = subprocess.call([OffsetStorage.hadoop_bin, 'fs', '-mkdir', '-p', os.path.split(check_point)[0]])

OffsetStorage.check_hdfs_ok(broker_list, topics, hadoop_meta_dir, temp_hdfs_dir, check_point)


