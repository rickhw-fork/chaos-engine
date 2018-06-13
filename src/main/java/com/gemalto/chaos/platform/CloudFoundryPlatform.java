package com.gemalto.chaos.platform;

import com.gemalto.chaos.ChaosException;
import com.gemalto.chaos.container.CloudFoundryContainer;
import com.gemalto.chaos.container.Container;
import org.cloudfoundry.operations.DefaultCloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnBean(DefaultCloudFoundryOperations.class)
public class CloudFoundryPlatform implements Platform {

    private static final Logger log = LoggerFactory.getLogger(CloudFoundryPlatform.class);

    @Autowired
    private DefaultCloudFoundryOperations cloudFoundryOperations;

    @Override
    public void degrade(Container container) {
        if (!(container instanceof CloudFoundryContainer)) {
            throw new ChaosException("Expected to be passed a Cloud Foundry container");
        }
        log.info("Attempting to degrade performance on {}", container);

    }

    @Override
    public List<Container> getRoster() {
        List<Container> containers = new ArrayList<>();
        Flux<ApplicationSummary> apps = cloudFoundryOperations.applications().list();
        for (ApplicationSummary app : apps.toIterable()) {
            Integer instances = app.getInstances();
            for (Integer i = 0; i < instances; i++) {
                CloudFoundryContainer c = CloudFoundryContainer
                        .builder()
                        .applicationId(app.getId())
                        .name(app.getName())
                        .instance(i)
                        .maxInstances(instances)
                        .build();
                containers.add(c);
                log.info("Added container {}", c);

            }
        }
        return containers;
    }

    @Override
    public void destroy(Container container) {
        if (!(container instanceof CloudFoundryContainer)) {
            throw new ChaosException("Expected to be passed a Cloud Foundry container");
        }

        cloudFoundryOperations.applications().restartInstance(
                ((CloudFoundryContainer) container).getRestartApplicationInstanceRequest());


    }

}
