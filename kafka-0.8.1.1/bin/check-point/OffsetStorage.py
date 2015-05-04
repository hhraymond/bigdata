#!/bin/env python
__author__ = 'xianghl'

import os
import fnmatch
import subprocess
import time
from datetime import datetime
import sys

path = os.path.dirname(os.path.abspath(__file__))
kafka_bin = path + '/../kafka-run-class.sh'
hadoop_bin = 'hadoop'


class OffsetStorage(object):

    def __init__(self):
        self.kv = dict()

    def __ge__(self, other):
        for (k, v1) in other.kv.iteritems():
            if not k in self.kv:
                if long(v1) > 0:
                    print_log('doesn\'t have key:', k)
                    return False
                else:
                    continue
            v2 = self.kv[k]
            if long(v2) < long(v1):
                print_log('key:', k, ',value:', v2, '<', v1)
                return False
        return True


class HdfsOffsetStorage(OffsetStorage):

    def __init__(self, hadoop_dir, base_dir):
        OffsetStorage.__init__(self)
        self.hadoop_dir = hadoop_dir
        self.base_dir = base_dir

    def load(self):
        now = datetime.now().strftime('%Y-%m-%d-%H-%M')
        directory = self.base_dir + '/' + now
        if os.path.exists(directory):
            os.removedirs(directory)
        os.makedirs(directory)
        ret = subprocess.call([hadoop_bin, 'fs', '-get', self.hadoop_dir + '/*.meta', directory])

        if ret != 0:
            raise Exception('failed to get hadoop offsets.')

        for path, subdirs, files in os.walk(directory):
            for name in files:
                if fnmatch.fnmatch(name, '*.meta'):
                    filename = os.path.join(path, name)
                    f = open(filename)
                    for line in f:
                        line = line.strip()
                        arr = line.split('-')  # topic-partition-offset
                        key = arr[0] + '-' + arr[1]
                        self.kv[key] = arr[2]
                    f.close()


class ZkOffsetStorage(OffsetStorage):

    def __init__(self, zkconnect, group, base_dir):
        OffsetStorage.__init__(self)
        self.zkconnect = zkconnect
        self.group = group
        self.base_dir = base_dir
        if not os.path.exists(base_dir):
            os.makedirs(base_dir)

    def load(self):
        now = datetime.now().strftime('%Y-%m-%d-%H-%M')
        offset_file = self.base_dir + '/' + now
        if os.path.exists(offset_file):
            os.remove(offset_file)

        ret = subprocess.call([
            kafka_bin,        'kafka.tools.ExportZkOffsetsForSimpleMirrorMaker',
            '-zkconnect',     self.zkconnect,
            '-group',         self.group,
            '-output-file',   offset_file])

        if ret != 0:
            raise Exception('failed to get mirrormaker offsets.')

        f = open(offset_file)
        for line in f:
            line = line.strip()
            arr = line.split('\t')   # broker \t topic \t partition \t offset
            key = arr[0].strip() + '/' + arr[1].strip() + '/' + arr[2].strip()
            self.kv[key] = arr[3].strip()
        f.close()


class OldMirrorMakerOffsetStorage(OffsetStorage):

    def __init__(self, zkconnect, group, base_dir):
        OffsetStorage.__init__(self)
        self.zkconnect = zkconnect
        self.group = group
        self.base_dir = base_dir
        if not os.path.exists(base_dir):
            os.makedirs(base_dir)

    def load(self):
        now = datetime.now().strftime('%Y-%m-%d-%H-%M')
        offset_file = self.base_dir + '/' + now
        if os.path.exists(offset_file):
            os.remove(offset_file)

        ret = subprocess.call([
            kafka_bin,        'kafka.tools.ExportZkOffsets',
            '-zkconnect',     self.zkconnect,
            '-group',         self.group,
            '-output-file',   offset_file])

        if ret != 0:
            raise Exception('failed to get mirrormaker offsets.')

        f = open(offset_file)
        for line in f:
            line = line.strip()
            # /consumers/group/offsets/topic/broker/partition/offset
            _, _, _, _, topic, broker, partition, offset = line.split('/')
            key = broker + '/' + topic + '/' + partition
            self.kv[key] = offset
        f.close()


class BrokerOffsetStorage(OffsetStorage):

    def __init__(self, brokerlist, topics):
        self.brokerlist = brokerlist 
        self.topics = topics
        OffsetStorage.__init__(self)

    def load(self):
        now = datetime.now().strftime('%Y-%m-%d-%H-%M')
        
        for topic in self.topics.split(","):
            while True:
                p = subprocess.Popen([
                    kafka_bin,       'kafka.tools.GetOffsetShell',
                    '--max-wait-ms', '5000',
                    '--time',        '-1',
                    '--broker-list', self.brokerlist,
                    '--topic',       topic],
                    stdout=subprocess.PIPE)
                c = p.communicate()
                if p.wait() != 0:
                    print_log('export broker offsets for topic ' + topic + ' failed.')
                    continue
                else:
                    try:
                        for line in c[0].split("\n"):
                            if len(line.strip()) == 0: continue
                            arr = line.split(":")
                            self.kv[arr[0] + "-" + arr[1]] = arr[2].strip().replace(',', '')
                        break
                    except Exception, e:
                        print_log(e)
                        time.sleep(5)

def check_hdfs_ok(broker_list, topics, hadoop_dir,
                  temp_hdfs_offset_dir, checkpoint_dir):
    while True:
        try:
            broker_offset_storage = BrokerOffsetStorage(broker_list, topics)
            broker_offset_storage.load()
            break
        except Exception, e:
            print_log(e)
            time.sleep(5)

    while True:
        try:
            hdfs_offset_storage = HdfsOffsetStorage(hadoop_dir, temp_hdfs_offset_dir)
            hdfs_offset_storage.load()
            if hdfs_offset_storage >= broker_offset_storage:
                print_log('hdfs check is ok now.')
                break
            else:
                print_log('hdfs offset smaller than broker offset. wait another 60 seconds...')
                time.sleep(60)
        except Exception, e:
            print_log(e)
            time.sleep(60)
        else:
            time.sleep(60)

    subprocess.call([hadoop_bin, 'fs', '-touchz', checkpoint_dir])


def check_mirrormaker_ok(zkconnect_src, zkconnect_dst, topics, group,
                         temp_broker_offset_dir, temp_mirrormaker_offset_dir):
    while True:
        try:
            broker_offset_storage = BrokerOffsetStorage(zkconnect_src, topics, temp_broker_offset_dir)
            broker_offset_storage.load()
            break
        except Exception, e:
            print_log(e)
            time.sleep(5)

    while True:
        try:
            mirrormaker_offset_storage = ZkOffsetStorage(zkconnect_dst, group, temp_mirrormaker_offset_dir)
            mirrormaker_offset_storage.load()
            if mirrormaker_offset_storage >= broker_offset_storage:
                print_log('mirror maker check is ok now.')
                break
            else:
                time.sleep(60)
        except Exception, e:
            print_log(e)
            time.sleep(60)
        else:
            time.sleep(60)


def check_oldmirrormaker_ok(zkconnect_src, zkconnect_dst, topics, group,
                            temp_broker_offset_dir, temp_mirrormaker_offset_dir):
    while True:
        try:
            broker_offset_storage = BrokerOffsetStorage(zkconnect_src, topics, temp_broker_offset_dir)
            broker_offset_storage.load()
            break
        except Exception, e:
            print_log(e)
            time.sleep(5)

    while True:
        try:
            mirrormaker_offset_storage = OldMirrorMakerOffsetStorage(zkconnect_src, group, temp_mirrormaker_offset_dir)
            mirrormaker_offset_storage.load()
            if mirrormaker_offset_storage >= broker_offset_storage:
                print_log('mirror maker check is ok now.')
                break
            else:
                time.sleep(60)
        except Exception, e:
            print_log(e)
            time.sleep(60)
        else:
            time.sleep(60)


def print_log(*args):
    lineno = sys._getframe().f_back.f_lineno
    print >> sys.stderr, '[', datetime.now(), '|', lineno, ']',
    for arg in args:
        print >> sys.stderr, arg,
    print >> sys.stderr
