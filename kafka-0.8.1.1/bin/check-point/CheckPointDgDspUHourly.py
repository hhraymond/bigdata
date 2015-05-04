#!/bin/env python
__author__ = 'xianghl'

import os
import OffsetStorage
from datetime import datetime, timedelta
import subprocess

broker_list = 'kf37dg.prod.mediav.com:9092,kf38dg.prod.mediav.com:9092,kf39dg.prod.mediav.com:9092'
topics = 'd.b.3'
hadoop_meta_dir = '/user/battle08/meta/other'


base_dir, _ = os.path.split(os.path.abspath(__file__))

check_time = datetime.now() - timedelta(hours=1)

check_point = '/user/hadoop/check_point/dsp_u_hourly/' + check_time.strftime('%Y-%m-%d/%H')

temp_hdfs_dir = base_dir + '/dsp_u_hourly/hadoop_offset/' + check_time.strftime('%Y-%m-%d/%H')

#make parent dir

ret = subprocess.call([OffsetStorage.hadoop_bin, 'fs', '-mkdir', '-p', os.path.split(check_point)[0]])

OffsetStorage.check_hdfs_ok(broker_list, topics, hadoop_meta_dir, temp_hdfs_dir, check_point)

