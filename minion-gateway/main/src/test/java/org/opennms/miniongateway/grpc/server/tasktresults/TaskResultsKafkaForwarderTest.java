/*
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2023 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2023 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *
 */

package org.opennms.miniongateway.grpc.server.tasktresults;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opennms.horizon.shared.grpc.common.TenantIDGrpcServerInterceptor;
import org.opennms.horizon.shared.protobuf.mapper.TenantedTaskSetResultsMapper;
import org.opennms.taskset.contract.TaskResult;
import org.opennms.taskset.contract.TaskSetResults;
import org.opennms.taskset.contract.TenantedTaskSetResults;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.Assert.*;

public class TaskResultsKafkaForwarderTest {

    public static final String TEST_AFTER_COPY_TENANT_ID = "x-tenant-after-copy-x";

    private TaskResultsKafkaForwarder taskResultsKafkaForwarder;

    private KafkaTemplate<String, byte[]> mockKafkaTemplate;
    private TenantIDGrpcServerInterceptor mockTenantIDGrpcInterceptor;
    private TenantedTaskSetResultsMapper mockTenantedTaskSetResultsMapper;

    private TaskSetResults testTaskSetResults;
    private TaskResult testTaskResult;
    private TenantedTaskSetResults testTenantedTaskSetResults;

    @Before
    public void setUp() throws Exception {
       /*
        KafkaTemplate<String, byte[]> kafkaTemplate,
        TenantIDGrpcServerInterceptor tenantIDGrpcInterceptor,
        TenantedTaskSetResultsMapper tenantedTaskSetResultsMapper) {
        */

        mockKafkaTemplate = Mockito.mock(KafkaTemplate.class);
        mockTenantIDGrpcInterceptor = Mockito.mock(TenantIDGrpcServerInterceptor.class);
        mockTenantedTaskSetResultsMapper = Mockito.mock(TenantedTaskSetResultsMapper.class);

        testTaskResult =
            TaskResult.newBuilder()
                .build();

        testTaskSetResults =
            TaskSetResults.newBuilder()
                .addResults(testTaskResult)
                .build();

        testTenantedTaskSetResults =
            TenantedTaskSetResults.newBuilder()
                .setTenantId(TEST_AFTER_COPY_TENANT_ID)   // Use a distinct tenant id, even though it is unrealistic, for test verification purposes
                .build();

        taskResultsKafkaForwarder = new TaskResultsKafkaForwarder(mockKafkaTemplate, mockTenantIDGrpcInterceptor, mockTenantedTaskSetResultsMapper, "x-kafka-topic-x");

        Mockito.when(mockTenantIDGrpcInterceptor.readCurrentContextTenantId()).thenReturn("x-tenant-x");
        Mockito.when(mockTenantedTaskSetResultsMapper.mapBareToTenanted("x-tenant-x", testTaskSetResults)).thenReturn(testTenantedTaskSetResults);
    }

    @Test
    public void testHandleMessage() throws InvalidProtocolBufferException {
        //
        // Setup Test Data and Interactions
        //

        //
        // Execute
        //
        taskResultsKafkaForwarder.handleMessage(testTaskSetResults);

        //
        // Verify the Results
        //
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, byte[]>> producerRecordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);

        Mockito.verify(mockKafkaTemplate).send(producerRecordCaptor.capture());
        ProducerRecord<String, byte[]> producerRecord = producerRecordCaptor.getValue();

        assertNotNull(producerRecord);
        assertEquals("x-kafka-topic-x", producerRecord.topic());

        byte[] raw = producerRecord.value();
        var tenantedTaskSetResults = TenantedTaskSetResults.parseFrom(raw);

        assertEquals(TEST_AFTER_COPY_TENANT_ID, tenantedTaskSetResults.getTenantId());
    }
}
