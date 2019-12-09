/*
 *    Copyright (c) 2019 Thales Group
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package com.thales.chaos.admin.enums;

import java.util.EnumSet;
import java.util.Set;

public enum AdminState {
    STARTING,
    STARTED,
    PAUSED,
    DRAIN,
    ABORT,
    ;

    public static Set<AdminState> getExperimentStates () {
        return EnumSet.of(STARTED);
    }

    public static Set<AdminState> getSelfHealingStates () {
        return EnumSet.of(STARTED, DRAIN, ABORT);
    }

    public static Set<AdminState> getHealthyStates () {
        return EnumSet.of(STARTED, DRAIN, PAUSED, ABORT);
    }
}