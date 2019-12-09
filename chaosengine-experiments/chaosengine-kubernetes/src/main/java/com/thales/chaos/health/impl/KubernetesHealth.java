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

package com.thales.chaos.health.impl;

import com.thales.chaos.platform.Platform;
import com.thales.chaos.platform.impl.KubernetesPlatform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty({ "kubernetes" })
public class KubernetesHealth extends AbstractPlatformHealth {
    @Autowired
    private KubernetesPlatform kubernetesPlatform;

    KubernetesHealth (KubernetesPlatform kubernetesPlatform) {
        this();
        this.kubernetesPlatform = kubernetesPlatform;
    }

    @Autowired
    KubernetesHealth () {
        log.debug("Using Kubernetes API check for health check.");
    }

    @Override
    Platform getPlatform () {
        return kubernetesPlatform;
    }
}