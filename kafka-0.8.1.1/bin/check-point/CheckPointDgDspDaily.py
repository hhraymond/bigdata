#!/bin/env python
__author__ = 'xianghl'

import os
import OffsetStorage
from datetime import datetime, timedelta
import subprocess

broker_list = 'kf31dg.prod.mediav.com:9092,kf32dg.prod.mediav.com:9092,kf33dg.prod.mediav.com:9092'
topics = 'd.x.3,d.u.3,d.s.3,d.c.3'
hadoop_meta_dir = '/user/battle08/meta/dsp'


base_dir, _ = os.path.split(os.path.abspath(__file__))

check_time = datetime.now() - timedelta(days=1)

check_point = '/user/hadoop/check_point/dsp_daily/' + check_time.strftime('%Y-%m-%d')

temp_hdfs_dir = base_dir + '/dsp_daily/hadoop_offset/' + check_time.strftime('%Y-%m-%d')

#make parent dir

ret = subprocess.call([OffsetStorage.hadoop_bin, 'fs', '-mkdir', '-p', os.path.split(check_point)[0]])

OffsetStorage.check_hdfs_ok(broker_list, topics, hadoop_meta_dir, temp_hdfs_dir, check_point)


