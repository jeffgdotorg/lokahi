/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
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

package org.opennms.horizon.flows.classification.api;

import java.util.List;

import org.opennms.horizon.flows.classification.api.model.Rule;

public interface ClassificationEngine {
    interface ClassificationRulesReloadedListener {
        void classificationRulesReloaded(final List<Rule> rules);
    }

    String classify(ClassificationRequest classificationRequest);

    List<Rule> getInvalidRules();

    void reload() throws InterruptedException;

    void addClassificationRulesReloadedListener(final ClassificationRulesReloadedListener classificationRulesReloadedListener);

    void removeClassificationRulesReloadedListener(final ClassificationRulesReloadedListener classificationRulesReloadedListener);
}
