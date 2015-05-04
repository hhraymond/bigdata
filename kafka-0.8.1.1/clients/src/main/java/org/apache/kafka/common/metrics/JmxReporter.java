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
package org.apache.kafka.common.metrics;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.kafka.common.KafkaException;


/**
 * Register metrics in JMX as dynamic mbeans based on the metric names
 */
public class JmxReporter implements MetricsReporter {

    private final String prefix;
    private final Map<String, KafkaMbean> mbeans = new HashMap<String, KafkaMbean>();

    public JmxReporter() {
        this("");
    }

    /**
     * Create a JMX reporter that prefixes all metrics with the given string.
     */
    public JmxReporter(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public synchronized void init(List<KafkaMetric> metrics) {
        for (KafkaMetric metric : metrics)
            addAttribute(metric);
        for (KafkaMbean mbean : mbeans.values())
            reregister(mbean);

    }

    @Override
    public synchronized void metricChange(KafkaMetric metric) {
        KafkaMbean mbean = addAttribute(metric);
        reregister(mbean);
    }

    private KafkaMbean addAttribute(KafkaMetric metric) {
        try {
            String[] names = split(prefix + metric.name());
            String qualifiedName = names[0] + "." + names[1];
            if (!this.mbeans.containsKey(qualifiedName))
                mbeans.put(qualifiedName, new KafkaMbean(names[0], names[1]));
            KafkaMbean mbean = this.mbeans.get(qualifiedName);
            mbean.setAttribute(names[2], metric);
            return mbean;
        } catch (JMException e) {
            throw new KafkaException("Error creating mbean attribute " + metric.name(), e);
        }
    }

    public synchronized void close() {
        for (KafkaMbean mbean : this.mbeans.values())
            unregister(mbean);

    }

    private void unregister(KafkaMbean mbean) {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        try {
            if (server.isRegistered(mbean.name()))
                server.unregisterMBean(mbean.name());
        } catch (JMException e) {
            throw new KafkaException("Error unregistering mbean", e);
        }
    }

    private void reregister(KafkaMbean mbean) {
        unregister(mbean);
        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(mbean, mbean.name());
        } catch (JMException e) {
            throw new KafkaException("Error registering mbean " + mbean.name(), e);
        }
    }

    private String[] split(String name) {
        int attributeStart = name.lastIndexOf('.');
        if (attributeStart < 0)
            throw new IllegalArgumentException("No MBean name in metric name: " + name);
        String attributeName = name.substring(attributeStart + 1, name.length());
        String remainder = name.substring(0, attributeStart);
        int beanStart = remainder.lastIndexOf('.');
        if (beanStart < 0)
            return new String[] { "", remainder, attributeName };
        String packageName = remainder.substring(0, beanStart);
        String beanName = remainder.substring(beanStart + 1, remainder.length());
        return new String[] { packageName, beanName, attributeName };
    }

    private static class KafkaMbean implements DynamicMBean {
        private final String beanName;
        private final ObjectName objectName;
        private final Map<String, KafkaMetric> metrics;

        public KafkaMbean(String packageName, String beanName) throws MalformedObjectNameException {
            this.beanName = beanName;
            this.metrics = new HashMap<String, KafkaMetric>();
            this.objectName = new ObjectName(packageName + ":type=" + beanName);
        }

        public ObjectName name() {
            return objectName;
        }

        public void setAttribute(String name, KafkaMetric metric) {
            this.metrics.put(name, metric);
        }

        @Override
        public Object getAttribute(String name) throws AttributeNotFoundException, MBeanException, ReflectionException {
            if (this.metrics.containsKey(name))
                return this.metrics.get(name).value();
            else
                throw new AttributeNotFoundException("Could not find attribute " + name);
        }

        @Override
        public AttributeList getAttributes(String[] names) {
            try {
                AttributeList list = new AttributeList();
                for (String name : names)
                    list.add(new Attribute(name, getAttribute(name)));
                return list;
            } catch (Exception e) {
                e.printStackTrace();
                return new AttributeList();
            }
        }

        @Override
        public MBeanInfo getMBeanInfo() {
            MBeanAttributeInfo[] attrs = new MBeanAttributeInfo[metrics.size()];
            int i = 0;
            for (Map.Entry<String, KafkaMetric> entry : this.metrics.entrySet()) {
                String attribute = entry.getKey();
                KafkaMetric metric = entry.getValue();
                attrs[i] = new MBeanAttributeInfo(attribute, double.class.getName(), metric.description(), true, false, false);
                i += 1;
            }
            return new MBeanInfo(beanName, "", attrs, null, null, null);
        }

        @Override
        public Object invoke(String name, Object[] params, String[] sig) throws MBeanException, ReflectionException {
            throw new UnsupportedOperationException("Set not allowed.");
        }

        @Override
        public void setAttribute(Attribute attribute) throws AttributeNotFoundException,
                                                     InvalidAttributeValueException,
                                                     MBeanException,
                                                     ReflectionException {
            throw new UnsupportedOperationException("Set not allowed.");
        }

        @Override
        public AttributeList setAttributes(AttributeList list) {
            throw new UnsupportedOperationException("Set not allowed.");
        }

    }

}
