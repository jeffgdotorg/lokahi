package org.opennms.horizon.minion.taskset.worker;

import org.opennms.horizon.minion.plugin.api.registries.MonitorRegistry;
import org.opennms.taskset.model.TaskDefinition;

public interface TaskExecutorLocalServiceFactory {
    TaskExecutorLocalService create(TaskDefinition taskDefinition, MonitorRegistry monitorRegistry);
}
