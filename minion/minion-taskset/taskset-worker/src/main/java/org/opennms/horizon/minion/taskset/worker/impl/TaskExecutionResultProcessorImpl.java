package org.opennms.horizon.minion.taskset.worker.impl;

import com.google.protobuf.Any;
import org.opennms.horizon.minion.plugin.api.CollectionSet;
import org.opennms.horizon.minion.plugin.api.ServiceDetectorResponse;
import org.opennms.horizon.shared.ipc.rpc.IpcIdentity;
import org.opennms.horizon.shared.ipc.sink.api.SyncDispatcher;
import org.opennms.taskset.contract.CollectorResponse;
import org.opennms.taskset.contract.DetectorResponse;
import org.opennms.taskset.contract.MonitorResponse;
import org.opennms.taskset.contract.TaskResult;
import org.opennms.taskset.contract.TaskSetResults;
import org.opennms.horizon.minion.taskset.worker.TaskExecutionResultProcessor;
import org.opennms.horizon.minion.plugin.api.ServiceMonitorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Optional;

public class TaskExecutionResultProcessorImpl implements TaskExecutionResultProcessor {

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(TaskExecutionResultProcessorImpl.class);

    private Logger log = DEFAULT_LOGGER;

    private final SyncDispatcher<TaskSetResults> taskSetSinkDispatcher;
    private final IpcIdentity identity;

    public TaskExecutionResultProcessorImpl(SyncDispatcher<TaskSetResults> taskSetSinkDispatcher, IpcIdentity identity) {
        this.taskSetSinkDispatcher = taskSetSinkDispatcher;
        this.identity = identity;
    }


//========================================
// API
//----------------------------------------

    @Override
    public void queueSendResult(String id, ServiceDetectorResponse response) {
        log.info("O-Detect STATUS: detected={}; ipaddress ={}", response.isServiceDetected(), response.getIpAddress());

        TaskSetResults taskSetResults = formatTaskSetResults(id, response);

        taskSetSinkDispatcher.send(taskSetResults);
    }

    @Override
    public void queueSendResult(String id, ServiceMonitorResponse response) {
        log.debug("O-POLL STATUS: status={}; reason={}", response.getStatus(), response.getReason());

        TaskSetResults taskSetResults = formatTaskSetResults(id, response);

        taskSetSinkDispatcher.send(taskSetResults);
    }

    @Override
    public void queueSendResult(String uuid, CollectionSet collectionSet) {
        TaskSetResults taskSetResults = formatTaskSetResults(uuid, collectionSet);
        log.info("Collector TaskSet results {}", taskSetResults);
        taskSetSinkDispatcher.send(taskSetResults);
    }

//========================================
// Internals
//----------------------------------------

    private TaskSetResults formatTaskSetResults(String id, ServiceMonitorResponse result) {
        MonitorResponse monitorResponse = formatMonitorResponse(result);

        TaskResult taskResult =
            TaskResult.newBuilder()
                .setId(id)
                .setMonitorResponse(monitorResponse)
                .setLocation(identity.getLocation())
                .setSystemId(identity.getId())
                .build();

        TaskSetResults taskSetResults =
            TaskSetResults.newBuilder()
                .addResults(taskResult)
                .build();

        return taskSetResults;
    }

    private TaskSetResults formatTaskSetResults(String id, ServiceDetectorResponse result) {
        DetectorResponse detectorResponse = formatDetectorResponse(result);

        TaskResult taskResult =
            TaskResult.newBuilder()
                .setId(id)
                .setDetectorResponse(detectorResponse)
                .setLocation(identity.getLocation())
                .setSystemId(identity.getId())
                .build();

        TaskSetResults taskSetResults =
            TaskSetResults.newBuilder()
                .addResults(taskResult)
                .build();

        return taskSetResults;
    }

    private DetectorResponse formatDetectorResponse(ServiceDetectorResponse response) {
        DetectorResponse result =
            DetectorResponse.newBuilder()
                .setDetected(Optional.of(response).map(ServiceDetectorResponse::isServiceDetected).orElse(DetectorResponse.getDefaultInstance().getDetected()))
                .setIpAddress(Optional.of(response).map(ServiceDetectorResponse::getIpAddress).orElse(DetectorResponse.getDefaultInstance().getIpAddress()))
                .setMonitorType(Optional.of(response).map(ServiceDetectorResponse::getMonitorType).orElse(DetectorResponse.getDefaultInstance().getMonitorType()))
                .setReason(Optional.of(response).map(ServiceDetectorResponse::getReason).orElse(DetectorResponse.getDefaultInstance().getReason()))
                .setNodeId(response.getNodeId())
                .build();

        return result;
    }

    private MonitorResponse formatMonitorResponse(ServiceMonitorResponse smr) {
        MonitorResponse result =
            MonitorResponse.newBuilder()
                .setMonitorType(Optional.of(smr).map(ServiceMonitorResponse::getMonitorType).orElse(MonitorResponse.getDefaultInstance().getMonitorType()))
                .setIpAddress(Optional.of(smr).map(ServiceMonitorResponse::getIpAddress).orElse(MonitorResponse.getDefaultInstance().getIpAddress()))
                .setResponseTimeMs(smr.getResponseTime())
                .setStatus(Optional.of(smr).map(ServiceMonitorResponse::getStatus).map(Object::toString).orElse(MonitorResponse.getDefaultInstance().getStatus()))
                .setReason(Optional.of(smr).map(ServiceMonitorResponse::getReason).orElse(MonitorResponse.getDefaultInstance().getReason()))
                .putAllMetrics(Optional.of(smr).map(ServiceMonitorResponse::getProperties).orElse(Collections.EMPTY_MAP))
                .setNodeId(smr.getNodeId())
                .build();

        return result;
    }

    private TaskSetResults formatTaskSetResults(String id, CollectionSet collectionSet) {
        CollectorResponse collectorResponse = formatCollectorResponse(collectionSet);

        TaskResult taskResult =
            TaskResult.newBuilder()
                .setId(id)
                .setCollectorResponse(collectorResponse)
                .setLocation(identity.getLocation())
                .setSystemId(identity.getId())
                .build();

        return TaskSetResults.newBuilder()
            .addResults(taskResult)
            .build();

    }

    private CollectorResponse formatCollectorResponse(CollectionSet collectionSet) {

        return CollectorResponse.newBuilder()
            .setStatus(collectionSet.getStatus())
            .setNodeId(collectionSet.getNodeId())
            .setIpAddress(collectionSet.getIpAddress())
            .setMonitorType(collectionSet.getMonitorType())
            .setResult(Any.pack(collectionSet.getResults())).build();
    }


}
