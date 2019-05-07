package com.gemalto.chaos.container.impl;

import com.amazonaws.services.rds.model.DBClusterNotFoundException;
import com.amazonaws.services.rds.model.DBClusterSnapshot;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gemalto.chaos.constants.AwsRDSConstants;
import com.gemalto.chaos.constants.DataDogConstants;
import com.gemalto.chaos.container.AwsContainer;
import com.gemalto.chaos.container.enums.ContainerHealth;
import com.gemalto.chaos.exception.ChaosException;
import com.gemalto.chaos.experiment.Experiment;
import com.gemalto.chaos.experiment.annotations.StateExperiment;
import com.gemalto.chaos.experiment.enums.ExperimentType;
import com.gemalto.chaos.notification.datadog.DataDogIdentifier;
import com.gemalto.chaos.platform.Platform;
import com.gemalto.chaos.platform.impl.AwsRDSPlatform;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.gemalto.chaos.constants.AwsConstants.NO_AZ_INFORMATION;
import static com.gemalto.chaos.constants.AwsRDSConstants.AWS_RDS_CLUSTER_DATADOG_IDENTIFIER;
import static com.gemalto.chaos.exception.enums.AwsChaosErrorCode.SINGLE_INSTANCE_CLUSTER;
import static net.logstash.logback.argument.StructuredArguments.v;
import static net.logstash.logback.argument.StructuredArguments.value;

public class AwsRDSClusterContainer extends AwsContainer {
    private String dbClusterIdentifier;
    private String engine;
    private transient AwsRDSPlatform awsRDSPlatform;

    public static AwsRDSClusterContainerBuilder builder () {
        return AwsRDSClusterContainerBuilder.anAwsRDSClusterContainer();
    }

    public String getEngine () {
        return engine;
    }

    @Override
    public Platform getPlatform () {
        return awsRDSPlatform;
    }

    @Override
    protected ContainerHealth updateContainerHealthImpl (ExperimentType experimentType) {
        return awsRDSPlatform.getInstanceStatus(getMembers().toArray(new String[0]));
    }

    @Override
    public String getSimpleName () {
        return getDbClusterIdentifier();
    }

    @Override
    public String getAggregationIdentifier () {
        return dbClusterIdentifier;
    }

    @Override
    public DataDogIdentifier getDataDogIdentifier () {
        return DataDogIdentifier.dataDogIdentifier()
                                .withKey(AWS_RDS_CLUSTER_DATADOG_IDENTIFIER)
                                .withValue(dbClusterIdentifier);
    }

    @Override
    protected boolean compareUniqueIdentifierInner (@NotNull String uniqueIdentifier) {
        return uniqueIdentifier.equals(dbClusterIdentifier);
    }

    public String getDbClusterIdentifier () {
        return dbClusterIdentifier;
    }

    @JsonIgnore
    public Set<String> getMembers () {
        return awsRDSPlatform.getClusterInstances(dbClusterIdentifier);
    }

    @StateExperiment
    public void restartInstances (Experiment experiment) {
        final String[] dbInstanceIdentifiers = getSomeMembers().toArray(new String[0]);
        experiment.setCheckContainerHealth(() -> awsRDSPlatform.getInstanceStatus(dbInstanceIdentifiers));
        awsRDSPlatform.restartInstance(dbInstanceIdentifiers);
    }

    /**
     * @return A randomly generated subset of getMembers. This will always return at least 1, and at most N-1 entries.
     */
    Set<String> getSomeMembers () {
        Set<String> someMembers = getSomeMembersInner();
        log.info("Experiment using cluster members {}", value("experimentMembers", someMembers));
        return someMembers;
    }

    /**
     * @return A randomly generated subset of getMembers. This will always return at least 1, and at most N-1 entries.
     */
    Set<String> getSomeMembersInner () {
        Set<String> returnSet;
        // Make members a List instead of Set so it can be sorted.
        List<String> members = new ArrayList<>(getMembers());
        Collections.shuffle(members, ThreadLocalRandom.current());
        // If there are 0 or 1 members in a cluster, we cannot choose a subset.
        if (members.size() <= 1) {
            throw new ChaosException(SINGLE_INSTANCE_CLUSTER);
        } else if (members.size() == 2) {
            // If there are exactly 2 members, the only valid subset is of size 1. Since the set is shuffled,
            // we can just return index 0 (as a set).
            String member = members.get(0);
            return Collections.singleton(member);
        }
        returnSet = new HashSet<>();
        // Offsetting -1/+1 to ensure that a minimum of 1 item is set. nextInt is exclusive on upper bound,
        // so the full size is not an option.
        int upperLimit = ThreadLocalRandom.current().nextInt(members.size() - 1) + 1;
        for (int i = 0; i < upperLimit; i++) {
            returnSet.add(members.get(i));
        }
        return returnSet;
    }

    @StateExperiment
    public void startSnapshot (Experiment experiment) {
        experiment.setCheckContainerHealth(() -> awsRDSPlatform.isClusterSnapshotRunning(dbClusterIdentifier) ? ContainerHealth.RUNNING_EXPERIMENT : ContainerHealth.NORMAL);
        final DBClusterSnapshot dbClusterSnapshot = awsRDSPlatform.snapshotDBCluster(dbClusterIdentifier);
        experiment.setSelfHealingMethod(() -> {
            try {
                awsRDSPlatform.deleteClusterSnapshot(dbClusterSnapshot);
            } catch (DBClusterNotFoundException e) {
                log.warn("Tried to clean up cluster snapshot, but it was already deleted", v(DataDogConstants.RDS_CLUSTER_SNAPSHOT, dbClusterSnapshot), e);
            }
            return null;
        });
        experiment.setFinalizeMethod(experiment.getSelfHealingMethod());
        // On finalize clean up the snapshot.
    }

    @StateExperiment
    public void initiateFailover (Experiment experiment) {
        final String[] members = getMembers().toArray(new String[0]);
        experiment.setCheckContainerHealth(() -> awsRDSPlatform.getInstanceStatus(members));
        awsRDSPlatform.failoverCluster(dbClusterIdentifier);
    }

    public static final class AwsRDSClusterContainerBuilder {
        private final Map<String, String> dataDogTags = new HashMap<>();
        private String dbClusterIdentifier;
        private String engine;
        private String availabilityZone;
        private AwsRDSPlatform awsRDSPlatform;

        private AwsRDSClusterContainerBuilder () {
        }

        static AwsRDSClusterContainerBuilder anAwsRDSClusterContainer () {
            return new AwsRDSClusterContainerBuilder();
        }

        public AwsRDSClusterContainerBuilder withAvailabilityZone (String availabilityZone) {
            this.availabilityZone = availabilityZone;
            return this;
        }

        public AwsRDSClusterContainerBuilder withDbClusterIdentifier (String dbClusterIdentifier) {
            this.dbClusterIdentifier = dbClusterIdentifier;
            return withDataDogTag(AwsRDSConstants.AWS_RDS_CLUSTER_DATADOG_IDENTIFIER, dbClusterIdentifier);
        }

        public AwsRDSClusterContainerBuilder withEngine (String engine) {
            this.engine = engine;
            return this;
        }

        public AwsRDSClusterContainerBuilder withAwsRDSPlatform (AwsRDSPlatform awsRDSPlatform) {
            this.awsRDSPlatform = awsRDSPlatform;
            return this;
        }

        public AwsRDSClusterContainerBuilder withDataDogTag (String key, String value) {
            this.dataDogTags.put(key, value);
            return this;
        }

        public AwsRDSClusterContainerBuilder withDBClusterResourceId (String dbClusterResourceId) {
            return withDataDogTag(DataDogConstants.DEFAULT_DATADOG_IDENTIFIER_KEY, dbClusterResourceId);
        }

        public AwsRDSClusterContainer build () {
            AwsRDSClusterContainer awsRDSClusterContainer = new AwsRDSClusterContainer();
            awsRDSClusterContainer.engine = this.engine;
            awsRDSClusterContainer.dbClusterIdentifier = this.dbClusterIdentifier;
            awsRDSClusterContainer.awsRDSPlatform = this.awsRDSPlatform;
            awsRDSClusterContainer.availabilityZone = this.availabilityZone != null ? this.availabilityZone : NO_AZ_INFORMATION;
            awsRDSClusterContainer.dataDogTags.putAll(this.dataDogTags);
            try {
                awsRDSClusterContainer.setMappedDiagnosticContext();
                awsRDSClusterContainer.log.info("Created new AWS RDS Cluster Container object");
            } finally {
                awsRDSClusterContainer.clearMappedDiagnosticContext();
            }
            return awsRDSClusterContainer;
        }
    }
}