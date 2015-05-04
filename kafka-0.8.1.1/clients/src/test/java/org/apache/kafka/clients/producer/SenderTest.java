/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.clients.producer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.concurrent.Future;


import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.internals.Metadata;
import org.apache.kafka.clients.producer.internals.RecordAccumulator;
import org.apache.kafka.clients.producer.internals.Sender;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.network.NetworkReceive;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.protocol.ProtoUtils;
import org.apache.kafka.common.protocol.types.Struct;
import org.apache.kafka.common.record.CompressionType;
import org.apache.kafka.common.requests.RequestSend;
import org.apache.kafka.common.requests.ResponseHeader;
import org.apache.kafka.common.utils.MockTime;
import org.apache.kafka.test.MockSelector;
import org.apache.kafka.test.TestUtils;
import org.junit.Before;
import org.junit.Test;

public class SenderTest {

    private MockTime time = new MockTime();
    private MockSelector selector = new MockSelector(time);
    private int batchSize = 16 * 1024;
    private Metadata metadata = new Metadata(0, Long.MAX_VALUE);
    private Cluster cluster = TestUtils.singletonCluster("test", 1);
    private Metrics metrics = new Metrics(time);
    private RecordAccumulator accumulator = new RecordAccumulator(batchSize, 1024 * 1024, 0L, false, metrics, time);
    private Sender sender = new Sender(selector, metadata, this.accumulator, "", 1024 * 1024, 0L, (short) -1, 10000, time);

    @Before
    public void setup() {
        metadata.update(cluster, time.milliseconds());
    }

    @Test
    public void testSimple() throws Exception {
        TopicPartition tp = new TopicPartition("test", 0);
        Future<RecordMetadata> future = accumulator.append(tp, "key".getBytes(), "value".getBytes(), CompressionType.NONE, null);
        sender.run(time.milliseconds());
        assertEquals("We should have connected", 1, selector.connected().size());
        selector.clear();
        sender.run(time.milliseconds());
        assertEquals("Single request should be sent", 1, selector.completedSends().size());
        RequestSend request = (RequestSend) selector.completedSends().get(0);
        selector.clear();
        long offset = 42;
        selector.completeReceive(produceResponse(request.header().correlationId(),
                                                 cluster.leaderFor(tp).id(),
                                                 tp.topic(),
                                                 tp.partition(),
                                                 offset,
                                                 Errors.NONE.code()));
        sender.run(time.milliseconds());
        assertTrue("Request should be completed", future.isDone());
        assertEquals(offset, future.get().offset());
    }

    private NetworkReceive produceResponse(int correlation, int source, String topic, int part, long offset, int error) {
        Struct struct = new Struct(ProtoUtils.currentResponseSchema(ApiKeys.PRODUCE.id));
        Struct response = struct.instance("responses");
        response.set("topic", topic);
        Struct partResp = response.instance("partition_responses");
        partResp.set("partition", part);
        partResp.set("error_code", (short) error);
        partResp.set("base_offset", offset);
        response.set("partition_responses", new Object[] { partResp });
        struct.set("responses", new Object[] { response });
        ResponseHeader header = new ResponseHeader(correlation);
        ByteBuffer buffer = ByteBuffer.allocate(header.sizeOf() + struct.sizeOf());
        header.writeTo(buffer);
        struct.writeTo(buffer);
        buffer.rewind();
        return new NetworkReceive(source, buffer);
    }

}
