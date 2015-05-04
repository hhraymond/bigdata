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

import static org.apache.kafka.common.config.ConfigDef.Range.atLeast;
import static org.apache.kafka.common.config.ConfigDef.Range.between;

import java.util.Map;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Type;


/**
 * The producer configuration keys
 */
public class ProducerConfig extends AbstractConfig {

    private static final ConfigDef config;

    /**
     * A list of URLs to use for establishing the initial connection to the cluster. This list should be in the form
     * <code>host1:port1,host2:port2,...</code>. These urls are just used for the initial connection to discover the
     * full cluster membership (which may change dynamically) so this list need not contain the full set of servers (you
     * may want more than one, though, in case a server is down).
     */
    public static final String BROKER_LIST_CONFIG = "metadata.broker.list";

    /**
     * The amount of time to block waiting to fetch metadata about a topic the first time a record is sent to that
     * topic.
     */
    public static final String METADATA_FETCH_TIMEOUT_CONFIG = "metadata.fetch.timeout.ms";

    /**
     * The buffer size allocated for a partition. When records are received which are smaller than this size the
     * producer will attempt to optimistically group them together until this size is reached.
     */
    public static final String MAX_PARTITION_SIZE_CONFIG = "max.partition.bytes";

    /**
     * The total memory used by the producer to buffer records waiting to be sent to the server. If records are sent
     * faster than they can be delivered to the server the producer will either block or throw an exception based on the
     * preference specified by {@link #BLOCK_ON_BUFFER_FULL}.
     */
    public static final String TOTAL_BUFFER_MEMORY_CONFIG = "total.memory.bytes";

    /**
     * The number of acknowledgments the producer requires from the server before considering a request complete.
     */
    public static final String REQUIRED_ACKS_CONFIG = "request.required.acks";

    /**
     * The maximum amount of time the server will wait for acknowledgments from followers to meet the acknowledgment
     * requirements the producer has specified. If the requested number of acknowledgments are not met an error will be
     * returned.
     */
    public static final String REQUEST_TIMEOUT_CONFIG = "request.timeout.ms";

    /**
     * The producer groups together any records that arrive in between request sends. Normally this occurs only under
     * load when records arrive faster than they can be sent out. However the client can reduce the number of requests
     * and increase throughput by adding a small amount of artificial delay to force more records to batch together.
     * This setting gives an upper bound on this delay. If we get {@link #MAX_PARTITION_SIZE_CONFIG} worth of records
     * for a partition it will be sent immediately regardless of this setting, however if we have fewer than this many
     * bytes accumulated for this partition we will "linger" for the specified time waiting for more records to show up.
     * This setting defaults to 0.
     */
    public static final String LINGER_MS_CONFIG = "linger.ms";

    /**
     * Force a refresh of the cluster metadata after this period of time. This ensures that changes to the number of
     * partitions or other settings will by taken up by producers without restart.
     */
    public static final String METADATA_REFRESH_MS_CONFIG = "topic.metadata.refresh.interval.ms";

    /**
     * The id string to pass to the server when making requests. The purpose of this is to be able to track the source
     * of requests beyond just ip/port by allowing a logical application name to be included.
     */
    public static final String CLIENT_ID_CONFIG = "client.id";

    /**
     * The size of the TCP send buffer to use when sending data
     */
    public static final String SEND_BUFFER_CONFIG = "send.buffer.bytes";

    /**
     * The maximum size of a request. This is also effectively a cap on the maximum record size. Note that the server
     * has its own cap on record size which may be different from this.
     */
    public static final String MAX_REQUEST_SIZE_CONFIG = "max.request.size";

    /**
     * The amount of time to wait before attempting to reconnect to a given host. This avoids repeated connecting to a
     * host in a tight loop.
     */
    public static final String RECONNECT_BACKOFF_MS_CONFIG = "reconnect.backoff.ms";

    /**
     * When our memory buffer is exhausted we must either stop accepting new records (block) or throw errors. By default
     * this setting is true and we block, however users who want to guarantee we never block can turn this into an
     * error.
     */
    public static final String BLOCK_ON_BUFFER_FULL = "block.on.buffer.full";

    public static final String ENABLE_JMX = "enable.jmx";

    static {
        /* TODO: add docs */
        config = new ConfigDef().define(BROKER_LIST_CONFIG, Type.LIST, "blah blah")
                                .define(METADATA_FETCH_TIMEOUT_CONFIG, Type.LONG, 60 * 1000, atLeast(0), "blah blah")
                                .define(MAX_PARTITION_SIZE_CONFIG, Type.INT, 16384, atLeast(0), "blah blah")
                                .define(TOTAL_BUFFER_MEMORY_CONFIG, Type.LONG, 32 * 1024 * 1024L, atLeast(0L), "blah blah")
                                /* TODO: should be a string to handle acks=in-sync */
                                .define(REQUIRED_ACKS_CONFIG, Type.INT, 1, between(-1, Short.MAX_VALUE), "blah blah")
                                .define(REQUEST_TIMEOUT_CONFIG, Type.INT, 30 * 1000, atLeast(0), "blah blah")
                                .define(LINGER_MS_CONFIG, Type.LONG, 0, atLeast(0L), "blah blah")
                                .define(METADATA_REFRESH_MS_CONFIG, Type.LONG, 10 * 60 * 1000, atLeast(-1L), "blah blah")
                                .define(CLIENT_ID_CONFIG, Type.STRING, "", "blah blah")
                                .define(SEND_BUFFER_CONFIG, Type.INT, 128 * 1024, atLeast(0), "blah blah")
                                .define(MAX_REQUEST_SIZE_CONFIG, Type.INT, 1 * 1024 * 1024, atLeast(0), "blah blah")
                                .define(RECONNECT_BACKOFF_MS_CONFIG, Type.LONG, 10L, atLeast(0L), "blah blah")
                                .define(BLOCK_ON_BUFFER_FULL, Type.BOOLEAN, true, "blah blah")
                                .define(ENABLE_JMX, Type.BOOLEAN, true, "");
    }

    ProducerConfig(Map<? extends Object, ? extends Object> props) {
        super(config, props);
    }

}
