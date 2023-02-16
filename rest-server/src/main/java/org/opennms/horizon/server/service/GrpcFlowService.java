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

package org.opennms.horizon.server.service;

import java.time.Instant;
import java.util.stream.Collectors;

import org.opennms.dataplatform.flows.querier.Querier;
import org.opennms.horizon.server.model.flows.FlowSummary;
import org.opennms.horizon.server.model.flows.FlowingPoint;
import org.opennms.horizon.server.model.flows.TrafficSummary;
import org.opennms.horizon.server.service.grpc.FlowClient;
import org.opennms.horizon.server.utils.ServerHeaderUtil;
import org.springframework.stereotype.Service;

import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLEnvironment;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.ResolutionEnvironment;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@GraphQLApi
@Service
public class GrpcFlowService {
    private final FlowClient client;

    private final ServerHeaderUtil headerUtil;

    @GraphQLQuery(name = "getFlowSummary")
    public Mono<FlowSummary> getFlowSummary(@GraphQLEnvironment ResolutionEnvironment env) {
        FlowSummary flowSummary = new FlowSummary();
        flowSummary.setNumFlows(client.getNumFlows(headerUtil.getAuthHeader(env)));
        return Mono.just(flowSummary);
    }

    @GraphQLQuery(name = "getTopNHostSummaries")
    public Flux<TrafficSummary> getTopNHostSummaries(@GraphQLArgument(name = "hours") Long hours, @GraphQLEnvironment ResolutionEnvironment env) {
        return Flux.fromIterable(client.getTopNHostSummaries(hours,10, headerUtil.getAuthHeader(env)).stream().map(this::toTrafficSummary).collect(Collectors.toList()));
    }

    @GraphQLQuery(name = "getTopNHostSeries")
    public Flux<FlowingPoint> getTopNHostSeries(@GraphQLArgument(name = "hours") Long hours, @GraphQLEnvironment ResolutionEnvironment env) {
        return Flux.fromIterable(client.getTopNHostSeries(hours,10, headerUtil.getAuthHeader(env)).stream().map(this::toFlowingPoint).collect(Collectors.toList()));
    }

    @GraphQLQuery(name = "getTopNApplicationSummaries")
    public Flux<TrafficSummary> getTopNApplicationSummaries(@GraphQLArgument(name = "hours") Long hours, @GraphQLEnvironment ResolutionEnvironment env) {
        return Flux.fromIterable(client.getTopNApplicationSummaries(hours,10, headerUtil.getAuthHeader(env)).stream().map(this::toTrafficSummary).collect(Collectors.toList()));
    }

    @GraphQLQuery(name = "getTopNApplicationSeries")
    public Flux<FlowingPoint> getTopNApplicationSeries(@GraphQLArgument(name = "hours") Long hours, @GraphQLEnvironment ResolutionEnvironment env) {
        return Flux.fromIterable(client.getTopNApplicationSeries(hours,10, headerUtil.getAuthHeader(env)).stream().map(this::toFlowingPoint).collect(Collectors.toList()));
    }

    @GraphQLQuery(name = "getTopNConversationSummaries")
    public Flux<TrafficSummary> getTopNConversationSummaries(@GraphQLArgument(name = "hours") Long hours, @GraphQLEnvironment ResolutionEnvironment env) {
        return Flux.fromIterable(client.getTopNConversationSummaries(hours,10, headerUtil.getAuthHeader(env)).stream().map(this::toTrafficSummary).collect(Collectors.toList()));
    }

    @GraphQLQuery(name = "getTopNConversationSeries")
    public Flux<FlowingPoint> getTopNConversationSeries(@GraphQLArgument(name = "hours") Long hours, @GraphQLEnvironment ResolutionEnvironment env) {
        return Flux.fromIterable(client.getTopNConversationSeries(hours,10, headerUtil.getAuthHeader(env)).stream().map(this::toFlowingPoint).collect(Collectors.toList()));
    }

    private TrafficSummary toTrafficSummary(Querier.TrafficSummary summary) {
        TrafficSummary trafficSummary = new TrafficSummary();
        trafficSummary.setBytesIn(summary.getBytesIn());
        trafficSummary.setBytesOut(summary.getBytesOut());
        if (summary.hasApplication()) {
            trafficSummary.setLabel(summary.getApplication());
        } else if (summary.hasHost()) {
            trafficSummary.setLabel(summary.getHost());
        } else if (summary.hasConversation()) {
            trafficSummary.setLabel(conversationToLabel(summary.getConversation()));
        }
        return trafficSummary;
    }

    private FlowingPoint toFlowingPoint(Querier.FlowingPoint point) {
        final var flowingPoint = new FlowingPoint();
        flowingPoint.setTimestamp(Instant.ofEpochSecond(point.getTimestamp().getSeconds(), point.getTimestamp().getNanos()));
        flowingPoint.setDirection(switch (point.getDirection()) {
            case INGRESS -> "INGRESS";
            case EGRESS -> "EGRESS";
            case UNKNOWN -> "UNKNOWN";
            case UNRECOGNIZED -> null;
        });
        if (point.hasApplication()) {
            flowingPoint.setLabel(point.getApplication());
        } else if (point.hasHost()) {
            flowingPoint.setLabel(point.getHost());
        } else if (point.hasConversation()) {
            flowingPoint.setLabel(conversationToLabel(point.getConversation()));
        }
        flowingPoint.setValue(point.getValue());
        return flowingPoint;
    }

    private String conversationToLabel(final Querier.Conversation conversation) {
        return String.format("%s <-> %s (%s)",
            conversation.getLowerHost(),
            conversation.getUpperHost(),
            conversation.getApplication());
    }
}
