/*******************************************************************************
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
 *******************************************************************************/

package org.opennms.horizon.alertservice.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.opennms.horizon.alerts.proto.AlertEventDefinitionProto;
import org.opennms.horizon.alerts.proto.AlertEventDefinitionServiceGrpc;
import org.opennms.horizon.alerts.proto.ListAlertEventDefinitionsRequest;
import org.opennms.horizon.alerts.proto.ListAlertEventDefinitionsResponse;
import org.opennms.horizon.alertservice.db.entity.EventDefinition;
import org.opennms.horizon.alertservice.db.repository.EventDefinitionRepository;
import org.opennms.horizon.alertservice.mapper.EventDefinitionMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AlertEventDefinitionGrpcService extends AlertEventDefinitionServiceGrpc.AlertEventDefinitionServiceImplBase {

    private final EventDefinitionRepository eventDefinitionRepository;

    private final EventDefinitionMapper eventDefinitionMapper;

    @Override
    public void listAlertEventDefinitions(ListAlertEventDefinitionsRequest request, StreamObserver<ListAlertEventDefinitionsResponse> responseObserver) {
        List<EventDefinition> eventDefinitions = eventDefinitionRepository.findByEventType(request.getEventType());
        List<AlertEventDefinitionProto> alertEventDefinitionProtos = eventDefinitions.stream()
            .map(eventDefinitionMapper::entityToProto)
            .toList();
        ListAlertEventDefinitionsResponse result = ListAlertEventDefinitionsResponse.newBuilder()
            .addAllAlertEventDefinitions(alertEventDefinitionProtos).build();
        responseObserver.onNext(result);
        responseObserver.onCompleted();
    }
}
