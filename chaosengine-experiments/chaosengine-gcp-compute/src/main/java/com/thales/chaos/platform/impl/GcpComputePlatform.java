/*
 *    Copyright (c) 2018 - 2020, Thales DIS CPL Canada, Inc
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

package com.thales.chaos.platform.impl;

import com.google.cloud.compute.v1.*;
import com.thales.chaos.constants.DataDogConstants;
import com.thales.chaos.constants.GcpConstants;
import com.thales.chaos.container.Container;
import com.thales.chaos.container.impl.GcpComputeInstanceContainer;
import com.thales.chaos.platform.Platform;
import com.thales.chaos.platform.enums.ApiStatus;
import com.thales.chaos.platform.enums.PlatformHealth;
import com.thales.chaos.platform.enums.PlatformLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.thales.chaos.services.impl.GcpComputeService.COMPUTE_PROJECT;
import static java.util.Collections.emptyList;
import static net.logstash.logback.argument.StructuredArguments.v;

@ConditionalOnProperty("gcp.compute")
@ConfigurationProperties("gcp.compute")
@Component
public class GcpComputePlatform extends Platform {
    private static final Logger log = LoggerFactory.getLogger(GcpComputePlatform.class);
    @Autowired
    private InstanceClient instanceClient;
    @Autowired
    private InstanceGroupClient instanceGroupClient;
    @Autowired
    private InstanceGroupManagerClient instanceGroupManagerClient;
    @Autowired
    private RegionInstanceGroupClient regionInstanceGroupClient;
    @Autowired
    private RegionInstanceGroupManagerClient regionInstanceGroupManagerClient;
    @Autowired
    @Qualifier(COMPUTE_PROJECT)
    private ProjectName projectName;
    private Map<String, String> includeFilter = Collections.emptyMap();
    private Map<String, String> excludeFilter = Collections.emptyMap();

    @Override
    public ApiStatus getApiStatus () {
        try {
            instanceClient.aggregatedListInstances(projectName);
        } catch (RuntimeException e) {
            log.error("Caught error when evaluating API Status of Google Cloud Platform", e);
            return ApiStatus.ERROR;
        }
        return ApiStatus.OK;
    }

    @Override
    public PlatformLevel getPlatformLevel () {
        return PlatformLevel.IAAS;
    }

    @Override
    public PlatformHealth getPlatformHealth () {
        return null;
    }

    @Override
    protected List<Container> generateRoster () {
        InstanceClient.AggregatedListInstancesPagedResponse instances = instanceClient.aggregatedListInstances(
                projectName);
        return StreamSupport.stream(instances.iterateAll().spliterator(), false)
                            .map(InstancesScopedList::getInstancesList)
                            .filter(Objects::nonNull)
                            .flatMap(Collection::stream)
                            .filter(this::isNotFiltered)
                            .map(this::createContainerFromInstance)
                            .peek(container -> log.info("Created container {}",
                                    v(DataDogConstants.DATADOG_CONTAINER_KEY, container)))
                            .collect(Collectors.toList());
    }

    boolean isNotFiltered (Instance instance) {
        List<Items> itemsList = Optional.of(instance)
                                        .map(Instance::getMetadata)
                                        .map(Metadata::getItemsList)
                                        .orElse(emptyList());
        Collection<Items> includeFilter = getIncludeFilter();
        Collection<Items> excludeFilter = getExcludeFilter();
        boolean hasAllMustIncludes = includeFilter.isEmpty() || itemsList.stream().anyMatch(includeFilter::contains);
        boolean hasNoMustNotIncludes = itemsList.stream().noneMatch(excludeFilter::contains);
        return hasAllMustIncludes && hasNoMustNotIncludes;
    }

    GcpComputeInstanceContainer createContainerFromInstance (Instance instance) {
        String id = instance.getId();
        String name = instance.getName();
        Tags tags = instance.getTags();
        String zone = instance.getZone();
        String createdBy = Optional.of(instance)
                                   .map(Instance::getMetadata)
                                   .map(Metadata::getItemsList)
                                   .stream()
                                   .flatMap(Collection::stream)
                                   .filter(GcpComputePlatform::isCreatedByItem)
                                   .map(Items::getValue)
                                   .findFirst()
                                   .orElse(null);
        return GcpComputeInstanceContainer.builder()
                                          .withFirewallTags(tags != null ? tags.getItemsList() : emptyList())
                                          .withInstanceName(name)
                                          .withUniqueIdentifier(id)
                                          .withPlatform(this)
                                          .withCreatedBy(createdBy)
                                          .withZone(zone)
                                          .build();
    }

    public Collection<Items> getIncludeFilter () {
        return asItemCollection(includeFilter);
    }

    public void setIncludeFilter (Map<String, String> includeFilter) {
        this.includeFilter = includeFilter;
    }

    public Collection<Items> getExcludeFilter () {
        return asItemCollection(excludeFilter);
    }

    public void setExcludeFilter (Map<String, String> excludeFilter) {
        this.excludeFilter = excludeFilter;
    }

    private static boolean isCreatedByItem (Items items) {
        return items.getKey().equals(GcpConstants.CREATED_BY_METADATA_KEY);
    }

    private static Collection<Items> asItemCollection (Map<String, String> itemMap) {
        return itemMap.entrySet()
                      .stream()
                      .map(entrySet -> Items.newBuilder()
                                            .setKey(entrySet.getKey())
                                            .setValue(entrySet.getValue())
                                            .build())
                      .collect(Collectors.toSet());
    }

    @Override
    public boolean isContainerRecycled (Container container) {
        return false;
    }

    public void stopInstance (GcpComputeInstanceContainer container) {
        ProjectZoneInstanceName instance = getProjectZoneInstanceNameOfContainer(container, projectName);
        instanceClient.stopInstance(instance);
    }

    static ProjectZoneInstanceName getProjectZoneInstanceNameOfContainer (GcpComputeInstanceContainer container,
                                                                          ProjectName projectName) {
        return ProjectZoneInstanceName.newBuilder()
                                      .setInstance(container.getUniqueIdentifier())
                                      .setZone(container.getZone())
                                      .setProject(projectName.getProject())
                                      .build();
    }

    public void setTags (GcpComputeInstanceContainer container, List<String> tags) {
        ProjectZoneInstanceName instance = getProjectZoneInstanceNameOfContainer(container, projectName);
        Tags newTags = Tags.newBuilder().addAllItems(tags).build();
        instanceClient.setTagsInstance(instance, newTags);
    }

    public void startInstance (GcpComputeInstanceContainer container) {
        ProjectZoneInstanceName instance = getProjectZoneInstanceNameOfContainer(container, projectName);
        instanceClient.startInstance(instance);
    }
}
