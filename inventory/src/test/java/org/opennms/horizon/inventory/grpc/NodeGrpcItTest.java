/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2022 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2022 The OpenNMS Group, Inc.
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
 *******************************************************************************/

package org.opennms.horizon.inventory.grpc;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.common.VerificationException;
import org.opennms.horizon.inventory.SpringContextTestInitializer;
import org.opennms.horizon.inventory.dto.NodeCreateDTO;
import org.opennms.horizon.inventory.dto.NodeDTO;
import org.opennms.horizon.inventory.dto.NodeServiceGrpc;
import org.opennms.horizon.inventory.grpc.taskset.TestTaskSetGrpcService;
import org.opennms.horizon.inventory.model.IpInterface;
import org.opennms.horizon.inventory.model.MonitoringLocation;
import org.opennms.horizon.inventory.model.Node;
import org.opennms.horizon.inventory.repository.IpInterfaceRepository;
import org.opennms.horizon.inventory.repository.MonitoringLocationRepository;
import org.opennms.horizon.inventory.repository.NodeRepository;
import org.opennms.taskset.service.contract.TaskSetServiceGrpc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import com.google.rpc.Code;
import com.google.rpc.Status;
import com.vladmihalcea.hibernate.type.basic.Inet;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;

@SpringBootTest
@ContextConfiguration(initializers = {SpringContextTestInitializer.class})
class NodeGrpcItTest extends GrpcTestBase {
    private NodeServiceGrpc.NodeServiceBlockingStub serviceStub;

    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private MonitoringLocationRepository monitoringLocationRepository;
    @Autowired
    private IpInterfaceRepository ipInterfaceRepository;

    private static TestTaskSetGrpcService testGrpcService;

    public void initStub() {
        serviceStub = NodeServiceGrpc.newBlockingStub(channel);
    }


    @BeforeAll
    public static void setup() throws IOException {
        testGrpcService = new TestTaskSetGrpcService();
        server = startMockServer(TaskSetServiceGrpc.SERVICE_NAME, testGrpcService);
    }

    @AfterAll
    public static void tearDown() throws InterruptedException {
        server.shutdownNow();
        server.awaitTermination();
    }

    @BeforeEach
    public void prepare() throws VerificationException {
        prepareServer();
    }

    @AfterEach
    public void cleanUp() throws InterruptedException {
        ipInterfaceRepository.deleteAll();
        nodeRepository.deleteAll();
        monitoringLocationRepository.deleteAll();
        testGrpcService.reset();
        afterTest();
    }


    @Test
    void testCreateNode() throws VerificationException {
        setupGrpc();
        initStub();

        String label = "label";

        NodeCreateDTO createDTO = NodeCreateDTO.newBuilder()
            .setLocation("location")
            .setLabel(label)
            .setManagementIp("127.0.0.1")
            .build();

        NodeDTO node = serviceStub.createNode(createDTO);

        assertEquals(label, node.getNodeLabel());
        assertEquals(1, testGrpcService.getTimesCalled());
        verify(spyInterceptor).verifyAccessToken(authHeader);
        verify(spyInterceptor).interceptCall(any(ServerCall.class), any(Metadata.class), any(ServerCallHandler.class));
    }

    @Test
    void testCreateNodeExistingIpAddress() throws Exception {
        String location = "location";
        String ip = "127.0.0.1";
        String label = "label";

        setupGrpc();
        populateTables(location, ip);
        initStub();

        NodeCreateDTO createDTO = NodeCreateDTO.newBuilder()
            .setLocation(location)
            .setLabel(label)
            .setManagementIp(ip)
            .build();

        StatusRuntimeException exception = Assertions.assertThrows(StatusRuntimeException.class, ()->serviceStub.createNode(createDTO));
        Status status = StatusProto.fromThrowable(exception);
        assertThat(status.getCode()).isEqualTo(Code.ALREADY_EXISTS_VALUE);
        assertThat(status.getMessage()).isEqualTo("Ip address already exists for location");
        assertEquals(0, testGrpcService.getTimesCalled());
        verify(spyInterceptor).verifyAccessToken(authHeader);
        verify(spyInterceptor).interceptCall(any(ServerCall.class), any(Metadata.class), any(ServerCallHandler.class));
    }

    @Test
    void testCreateNodeExistingIpAddressDifferentTenantId() throws Exception {
        String location = "location";
        String ip = "127.0.0.1";
        String label = "label";

        setupGrpcWithDifferentTenantID();
        populateTables(location, ip);
        initStub();

        NodeCreateDTO createDTO = NodeCreateDTO.newBuilder()
            .setLocation(location)
            .setLabel(label)
            .setManagementIp(ip)
            .build();

        NodeDTO node = serviceStub.createNode(createDTO);

        assertEquals(label, node.getNodeLabel());
        assertEquals(1, testGrpcService.getTimesCalled());
        verify(spyInterceptor).verifyAccessToken(differentTenantHeader);
        verify(spyInterceptor).interceptCall(any(ServerCall.class), any(Metadata.class), any(ServerCallHandler.class));
    }

    @Test
    void testCreateNodeExistingIpAddressNewDifferentLocation() throws Exception {
        String location = "location";
        String ip = "127.0.0.1";
        String label = "label";

        setupGrpc();
        populateTables(location, ip);
        initStub();

        NodeCreateDTO createDTO = NodeCreateDTO.newBuilder()
            .setLocation("different")
            .setLabel(label)
            .setManagementIp(ip)
            .build();

        NodeDTO node = serviceStub.createNode(createDTO);

        assertEquals(label, node.getNodeLabel());
        assertEquals(1, testGrpcService.getTimesCalled());
        verify(spyInterceptor).verifyAccessToken(authHeader);
        verify(spyInterceptor).interceptCall(any(ServerCall.class), any(Metadata.class), any(ServerCallHandler.class));
    }

    @Test
    void testCreateNodeExistingIpAddressInDifferentLocation() throws Exception {
        String location = "location";
        String ip = "127.0.0.1";
        String label = "label";
        String secondLocation = "loc2";
        String secondIp = "127.0.0.2";

        setupGrpc();
        populateTables(location, ip);
        populateTables(secondLocation, secondIp);
        initStub();

        NodeCreateDTO createDTO = NodeCreateDTO.newBuilder()
            .setLocation(secondLocation)
            .setLabel(label)
            .setManagementIp(ip)
            .build();

        NodeDTO node = serviceStub.createNode(createDTO);

        assertEquals(label, node.getNodeLabel());
        assertEquals(1, testGrpcService.getTimesCalled());
        verify(spyInterceptor).verifyAccessToken(authHeader);
        verify(spyInterceptor).interceptCall(any(ServerCall.class), any(Metadata.class), any(ServerCallHandler.class));
    }

    private synchronized void populateTables(String location, String ip) {
        Optional<MonitoringLocation> dbL = monitoringLocationRepository.findByLocation(location);
        MonitoringLocation dBLocation;
        if(dbL.isEmpty()) {
            MonitoringLocation ml = new MonitoringLocation();
            ml.setLocation(location);
            ml.setTenantId(tenantId);
            dBLocation = monitoringLocationRepository.save(ml);
        } else {
            dBLocation = dbL.get();
        }

        Node node = new Node();
        node.setTenantId(tenantId);
        node.setNodeLabel("label");
        node.setMonitoringLocation(dBLocation);
        node.setCreateTime(LocalDateTime.now());
        Node savedNode = nodeRepository.save(node);

        IpInterface ipInterface = new IpInterface();
        ipInterface.setTenantId(tenantId);
        ipInterface.setIpAddress(new Inet(ip));
        ipInterface.setNode(savedNode);
        IpInterface savedIpInterface = ipInterfaceRepository.save(ipInterface);
    }

    @Test
    void testCreateNodeMissingTenantId() throws Exception {
        setupGrpcWithOutTenantID();
        initStub();

        String label = "label";

        NodeCreateDTO createDTO = NodeCreateDTO.newBuilder()
            .setLocation("location")
            .setLabel(label)
            .setManagementIp("127.0.0.1")
            .build();

        StatusRuntimeException exception = Assertions.assertThrows(StatusRuntimeException.class, ()->serviceStub.createNode(createDTO));
        Status status = StatusProto.fromThrowable(exception);
        assertThat(status.getCode()).isEqualTo(Code.UNAUTHENTICATED_VALUE);
        assertThat(status.getMessage()).isEqualTo("Missing tenant id");
        assertEquals(0, testGrpcService.getTimesCalled());
        verify(spyInterceptor).verifyAccessToken(headerWithoutTenant);
        verify(spyInterceptor).interceptCall(any(ServerCall.class), any(Metadata.class), any(ServerCallHandler.class));
    }

    @Test
    void testCreateNodeBadIPAddress() throws Exception {
        setupGrpc();
        initStub();

        String label = "label";

        NodeCreateDTO createDTO = NodeCreateDTO.newBuilder()
            .setLocation("location")
            .setLabel(label)
            .setManagementIp("BAD")
            .build();

        StatusRuntimeException exception = Assertions.assertThrows(StatusRuntimeException.class, ()->serviceStub.createNode(createDTO));
        Status status = StatusProto.fromThrowable(exception);
        assertThat(status.getCode()).isEqualTo(Code.INVALID_ARGUMENT_VALUE);
        assertThat(status.getMessage()).isEqualTo("Bad management_ip: BAD");
        assertEquals(0, testGrpcService.getTimesCalled());
        verify(spyInterceptor).verifyAccessToken(authHeader);
        verify(spyInterceptor).interceptCall(any(ServerCall.class), any(Metadata.class), any(ServerCallHandler.class));
    }
}
