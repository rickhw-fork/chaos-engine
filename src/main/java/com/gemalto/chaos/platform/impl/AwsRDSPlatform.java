package com.gemalto.chaos.platform.impl;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.rds.AmazonRDS;
import com.amazonaws.services.rds.model.*;
import com.gemalto.chaos.ChaosException;
import com.gemalto.chaos.constants.AwsRDSConstants;
import com.gemalto.chaos.constants.DataDogConstants;
import com.gemalto.chaos.container.AwsContainer;
import com.gemalto.chaos.container.Container;
import com.gemalto.chaos.container.enums.ContainerHealth;
import com.gemalto.chaos.container.impl.AwsRDSClusterContainer;
import com.gemalto.chaos.container.impl.AwsRDSInstanceContainer;
import com.gemalto.chaos.platform.Platform;
import com.gemalto.chaos.platform.enums.ApiStatus;
import com.gemalto.chaos.platform.enums.PlatformHealth;
import com.gemalto.chaos.platform.enums.PlatformLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.gemalto.chaos.constants.AwsConstants.NO_AZ_INFORMATION;
import static com.gemalto.chaos.constants.AwsRDSConstants.*;
import static com.gemalto.chaos.constants.DataDogConstants.DATADOG_PLATFORM_KEY;
import static com.gemalto.chaos.container.enums.ContainerHealth.*;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static net.logstash.logback.argument.StructuredArguments.keyValue;
import static net.logstash.logback.argument.StructuredArguments.value;

@ConditionalOnProperty("aws.rds")
@ConfigurationProperties("aws.rds")
@Component
public class AwsRDSPlatform extends Platform {
    @Autowired
    private AmazonRDS amazonRDS;
    @Autowired
    private AmazonEC2 amazonEC2;
    private String defaultVpcId;
    private String chaosSecurityGroup;

    @Autowired
    public AwsRDSPlatform () {
    }

    AwsRDSPlatform (AmazonRDS amazonRDS, AmazonEC2 amazonEC2) {
        this.amazonRDS = amazonRDS;
        this.amazonEC2 = amazonEC2;
    }

    @EventListener(ApplicationReadyEvent.class)
    private void applicationReadyEvent () {
        log.info("Created AWS RDS Platform");
    }

    @Override
    public ApiStatus getApiStatus () {
        try {
            amazonRDS.describeDBInstances();
            amazonRDS.describeDBClusters();
            return ApiStatus.OK;
        } catch (RuntimeException e) {
            log.error("API for AWS RDS failed to resolve.", e);
            return ApiStatus.ERROR;
        }
    }

    @Override
    public PlatformLevel getPlatformLevel () {
        return PlatformLevel.IAAS;
    }

    @Override
    public PlatformHealth getPlatformHealth () {
        Supplier<Stream<String>> dbInstanceStatusSupplier = () -> getAllDBInstances()
                                                                           .stream()
                                                                           .map(DBInstance::getDBInstanceStatus);
        if (!dbInstanceStatusSupplier.get().allMatch(s -> s.equals(AwsRDSConstants.AWS_RDS_AVAILABLE))) {
            if (dbInstanceStatusSupplier.get().anyMatch(s -> s.equals(AwsRDSConstants.AWS_RDS_AVAILABLE))) {
                return PlatformHealth.DEGRADED;
            }
            return PlatformHealth.FAILED;
        }
        return PlatformHealth.OK;
    }

    @Override
    protected List<Container> generateRoster () {
        Collection<Container> dbInstanceContainers = getAllDBInstances().stream()
                                                                        .filter(dbInstance -> dbInstance.getDBClusterIdentifier() == null)
                                                                        .map(this::createContainerFromDBInstance)
                                                                        .collect(toSet());
        Collection<Container> dbClusterContainers = getAllDBClusters().stream()
                                                                      .map(this::createContainerFromDBCluster)
                                                                      .collect(toSet());
        return Stream.of(dbClusterContainers, dbInstanceContainers)
                     .flatMap(Collection::stream)
                     .collect(Collectors.toList());
    }

    @Override
    public List<Container> generateExperimentRoster () {
        Map<String, List<AwsContainer>> availabilityZoneMap = getRoster().stream()
                                                                         .map(AwsContainer.class::cast)
                                                                         .collect(groupingBy(AwsContainer::getAvailabilityZone));
        final String[] availabilityZones = availabilityZoneMap.keySet()
                                                              .stream()
                                                              .filter(s -> !s.equals(NO_AZ_INFORMATION))
                                                              .collect(toSet())
                                                              .toArray(new String[]{});
        final String randomAvailabilityZone = availabilityZones[new Random().nextInt(availabilityZones.length)];
        log.debug("Experiment on {} will use {}", keyValue(DATADOG_PLATFORM_KEY, this.getPlatformType()), keyValue(DataDogConstants.AVAILABILITY_ZONE, randomAvailabilityZone));
        List<Container> chosenSet = new ArrayList<>();
        chosenSet.addAll(availabilityZoneMap.get(randomAvailabilityZone));
        chosenSet.addAll(availabilityZoneMap.get(NO_AZ_INFORMATION));
        return chosenSet;
    }

    private Collection<DBInstance> getAllDBInstances () {
        Collection<DBInstance> dbInstances = new HashSet<>();
        DescribeDBInstancesRequest describeDBInstancesRequest = new DescribeDBInstancesRequest();
        DescribeDBInstancesResult describeDBInstancesResult;
        int i = 0;
        do {
            log.debug("Running describeDBInstances, page {}", ++i);
            describeDBInstancesResult = amazonRDS.describeDBInstances(describeDBInstancesRequest);
            dbInstances.addAll(describeDBInstancesResult.getDBInstances());
            describeDBInstancesRequest.setMarker(describeDBInstancesResult.getMarker());
        } while (describeDBInstancesRequest.getMarker() != null);
        return dbInstances;
    }

    private Collection<DBCluster> getAllDBClusters () {
        Collection<DBCluster> dbClusters = new HashSet<>();
        DescribeDBClustersRequest describeDBClustersRequest = new DescribeDBClustersRequest();
        DescribeDBClustersResult describeDBClustersResult;
        int i = 0;
        do {
            log.debug("Running describeDBClusters, page {}", ++i);
            describeDBClustersResult = amazonRDS.describeDBClusters(describeDBClustersRequest);
            dbClusters.addAll(describeDBClustersResult.getDBClusters());
            describeDBClustersRequest.setMarker(describeDBClustersResult.getMarker());
        } while (describeDBClustersRequest.getMarker() != null);
        return dbClusters;
    }

    private Container createContainerFromDBInstance (DBInstance dbInstance) {
        log.debug("Creating RDS Instance Container object from {}", keyValue("dbInstance", dbInstance));
        return AwsRDSInstanceContainer.builder()
                                      .withAwsRDSPlatform(this).withAvailabilityZone(dbInstance.getAvailabilityZone())
                                      .withDbInstanceIdentifier(dbInstance.getDBInstanceIdentifier())
                                      .withEngine(dbInstance.getEngine())
                                      .build();
    }

    private Container createContainerFromDBCluster (DBCluster dbCluster) {
        log.debug("Creating RDS Cluster Container object from {}", keyValue("dbCluster", dbCluster));
        return AwsRDSClusterContainer.builder()
                                     .withAwsRDSPlatform(this)
                                     .withDbClusterIdentifier(dbCluster.getDBClusterIdentifier())
                                     .withEngine(dbCluster.getEngine())
                                     .build();
    }

    ContainerHealth getDBInstanceHealth (AwsRDSInstanceContainer awsRDSInstanceContainer) {
        String instanceId = awsRDSInstanceContainer.getDbInstanceIdentifier();
        return getDBInstanceHealth(instanceId);
    }

    private ContainerHealth getDBInstanceHealth (String instanceId) {
        DBInstance dbInstance;
        try {
            dbInstance = amazonRDS.describeDBInstances(new DescribeDBInstancesRequest().withDBInstanceIdentifier(instanceId))
                                  .getDBInstances()
                                  .get(0);
        } catch (IndexOutOfBoundsException e) {
            return DOES_NOT_EXIST;
        }
        return dbInstance.getDBInstanceStatus().equals(AwsRDSConstants.AWS_RDS_AVAILABLE) ? NORMAL : RUNNING_EXPERIMENT;
    }

    ContainerHealth getDBClusterHealth (AwsRDSClusterContainer awsRDSClusterContainer) {
        String clusterInstanceId = awsRDSClusterContainer.getDbClusterIdentifier();
        return getContainerHealth(clusterInstanceId);
    }

    private ContainerHealth getContainerHealth (String clusterInstanceId) {
        DBCluster dbCluster;
        try {
            dbCluster = amazonRDS.describeDBClusters(new DescribeDBClustersRequest().withDBClusterIdentifier(clusterInstanceId))
                                 .getDBClusters()
                                 .get(0);
        } catch (IndexOutOfBoundsException e) {
            return DOES_NOT_EXIST;
        }
        if (!dbCluster.getStatus().equals(AWS_RDS_AVAILABLE)) return RUNNING_EXPERIMENT;
        if (dbCluster.getDBClusterMembers()
                     .stream()
                     .map(DBClusterMember::getDBInstanceIdentifier)
                     .map(this::getDBInstanceHealth)
                     .allMatch(containerHealth -> containerHealth.equals(ContainerHealth.NORMAL))) return NORMAL;
        return RUNNING_EXPERIMENT;
    }


    public void failoverCluster (String dbClusterIdentifier) {
        log.info("Initiating failover request for {}", keyValue(AWS_RDS_CLUSTER_DATADOG_IDENTIFIER, dbClusterIdentifier));
        amazonRDS.failoverDBCluster(new FailoverDBClusterRequest().withDBClusterIdentifier(dbClusterIdentifier));
    }

    public void restartInstance (String... dbInstanceIdentifiers) {
        asList(dbInstanceIdentifiers).parallelStream().forEach(this::restartInstance);
    }

    private void restartInstance (String dbInstanceIdentifier) {
        log.info("Initiating Reboot Database Instance request for {}", keyValue(AWS_RDS_INSTANCE_DATADOG_IDENTIFIER, dbInstanceIdentifier));
        amazonRDS.rebootDBInstance(new RebootDBInstanceRequest(dbInstanceIdentifier));
    }

    public Set<String> getClusterInstances (String dbClusterIdentifier) {
        log.info("Getting cluster instances for {}", keyValue(AWS_RDS_CLUSTER_DATADOG_IDENTIFIER, dbClusterIdentifier));
        return amazonRDS.describeDBClusters(new DescribeDBClustersRequest().withDBClusterIdentifier(dbClusterIdentifier))
                        .getDBClusters()
                        .stream()
                        .map(DBCluster::getDBClusterMembers)
                        .flatMap(Collection::stream)
                        .map(DBClusterMember::getDBInstanceIdentifier)
                        .collect(toSet());
    }

    public ContainerHealth getInstanceStatus (String... dbInstanceIdentifiers) {
        log.info("Checking health of instances {}", value("dbInstanceIdentifiers", dbInstanceIdentifiers));
        Collection<ContainerHealth> containerHealthCollection = new HashSet<>();
        for (String dbInstanceIdentifier : dbInstanceIdentifiers) {
            ContainerHealth instanceStatus = getInstanceStatus(dbInstanceIdentifier);
            containerHealthCollection.add(instanceStatus);
            switch (instanceStatus) {
                case NORMAL:
                    log.debug("Container {} returned health {}", value(AWS_RDS_INSTANCE_DATADOG_IDENTIFIER, dbInstanceIdentifier), value("ContainerHealth", instanceStatus));
                    break;
                case DOES_NOT_EXIST:
                case RUNNING_EXPERIMENT:
                    log.warn("Container {} returned health {}", value(AWS_RDS_INSTANCE_DATADOG_IDENTIFIER, dbInstanceIdentifier), value("ContainerHealth", instanceStatus));
                    break;
            }
        }
        if (containerHealthCollection.stream()
                                     .anyMatch(containerHealth -> containerHealth.equals(ContainerHealth.DOES_NOT_EXIST))) {
            return ContainerHealth.DOES_NOT_EXIST;
        } else if (containerHealthCollection.stream()
                                            .anyMatch(containerHealth -> containerHealth.equals(ContainerHealth.RUNNING_EXPERIMENT))) {
            return ContainerHealth.RUNNING_EXPERIMENT;
        }
        return ContainerHealth.NORMAL;
    }

    private ContainerHealth getInstanceStatus (String dbInstanceIdentifier) {
        List<DBInstance> dbInstances = amazonRDS.describeDBInstances(new DescribeDBInstancesRequest().withDBInstanceIdentifier(dbInstanceIdentifier))
                                                .getDBInstances();
        Supplier<Stream<String>> dbInstanceStatusSupplier = () -> dbInstances.stream()
                                                                             .map(DBInstance::getDBInstanceStatus);
        if (dbInstanceStatusSupplier.get().count() == 0) {
            return ContainerHealth.DOES_NOT_EXIST;
        } else if (dbInstanceStatusSupplier.get().noneMatch(s -> s.equals(AwsRDSConstants.AWS_RDS_AVAILABLE))) {
            return ContainerHealth.RUNNING_EXPERIMENT;
        }
        return ContainerHealth.NORMAL;
    }

    public void setVpcSecurityGroupIds (String dbInstanceIdentifier, String vpcSecurityGroupId) {
        setVpcSecurityGroupIds(dbInstanceIdentifier, Collections.singleton(vpcSecurityGroupId));
    }

    public void setVpcSecurityGroupIds (String dbInstanceIdentifier, Collection<String> vpcSecurityGroupIds) {
        log.info("Setting VPC Security Group ID for {} to {}", value(AWS_RDS_INSTANCE_DATADOG_IDENTIFIER, dbInstanceIdentifier), value(AWS_RDS_VPC_SECURITY_GROUP_ID, vpcSecurityGroupIds));
        amazonRDS.modifyDBInstance(new ModifyDBInstanceRequest(dbInstanceIdentifier).withVpcSecurityGroupIds(vpcSecurityGroupIds));
    }

    ContainerHealth checkVpcSecurityGroupIds (String dbInstanceIdentifier, String vpcSecurityGroupId) {
        return checkVpcSecurityGroupIds(dbInstanceIdentifier, Collections.singleton(vpcSecurityGroupId));
    }

    public ContainerHealth checkVpcSecurityGroupIds (String dbInstanceIdentifier, Collection<String> vpcSecurityGroupIds) {
        Collection<String> actualVpcSecurityGroupIds = getVpcSecurityGroupIds(dbInstanceIdentifier);
        log.info("Comparing VPC Security Group IDs for {}, {}, {}", value(AWS_RDS_INSTANCE_DATADOG_IDENTIFIER, dbInstanceIdentifier), keyValue("expectedVpcSecurityGroupIds", vpcSecurityGroupIds), keyValue("actualSecurityGroupIds", actualVpcSecurityGroupIds));
        return actualVpcSecurityGroupIds.containsAll(vpcSecurityGroupIds) && vpcSecurityGroupIds.containsAll(actualVpcSecurityGroupIds) ? ContainerHealth.NORMAL : ContainerHealth.RUNNING_EXPERIMENT;
    }

    public Collection<String> getVpcSecurityGroupIds (String dbInstanceIdentifier) {
        return amazonRDS.describeDBInstances(new DescribeDBInstancesRequest().withDBInstanceIdentifier(dbInstanceIdentifier))
                        .getDBInstances()
                        .stream()
                        .map(DBInstance::getVpcSecurityGroups)
                        .flatMap(Collection::stream)
                        .map(VpcSecurityGroupMembership::getVpcSecurityGroupId).collect(toSet());
    }

    public String getChaosSecurityGroup () {
        if (chaosSecurityGroup == null) initChaosSecurityGroup();
        return chaosSecurityGroup;
    }

    void initChaosSecurityGroup () {
        log.debug("Retrieving a VPC Security Group to use for Chaos");
        amazonEC2.describeSecurityGroups()
                 .getSecurityGroups()
                 .stream()
                 .filter(securityGroup -> securityGroup.getGroupName().equals(AWS_RDS_CHAOS_SECURITY_GROUP))
                 .findFirst()
                 .ifPresent(securityGroup -> {
                     chaosSecurityGroup = securityGroup.getGroupId();
                     log.info("Found existing VPC Security Group ID {}", value(AWS_RDS_VPC_SECURITY_GROUP_ID, chaosSecurityGroup));
                 });
        if (chaosSecurityGroup == null) {
            log.debug("No existing VPC Security Group for Chaos found");
            chaosSecurityGroup = createChaosSecurityGroup();
        }
    }

    private String createChaosSecurityGroup () {
        log.debug("Creating a VPC Security Group for Chaos");
        amazonEC2.describeVpcs().getVpcs().stream().filter(Vpc::isDefault).findFirst().ifPresent(vpc -> {
            defaultVpcId = vpc.getVpcId();
            log.debug("Using {}", keyValue("defaultVpcId", defaultVpcId));
        });
        if (defaultVpcId == null) {
            throw new ChaosException("No Default VPC Found");
        }
        String groupId = amazonEC2.createSecurityGroup(new CreateSecurityGroupRequest().withVpcId(defaultVpcId)
                                                                                       .withDescription(AwsRDSConstants.AWS_RDS_CHAOS_SECURITY_GROUP_DESCRIPTION)
                                                                                       .withGroupName(AWS_RDS_CHAOS_SECURITY_GROUP))
                                  .getGroupId();
        log.info("Created VPC Security Group {}", value(AWS_RDS_VPC_SECURITY_GROUP_ID, groupId));
        return groupId;
    }
}
