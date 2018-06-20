package com.gemalto.chaos.health.impl;

import com.gemalto.chaos.health.SystemHealth;
import com.gemalto.chaos.health.enums.SystemHealthState;
import com.gemalto.chaos.platform.impl.CloudFoundryPlatform;
import com.gemalto.chaos.services.impl.CloudFoundryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(CloudFoundryService.class)
public class CloudFoundryHealth implements SystemHealth {

    private static final Logger log = LoggerFactory.getLogger(CloudFoundryHealth.class);

    @Autowired
    CloudFoundryHealth() {
        log.debug("Using CloudFoundry API check for health check.");
    }

    CloudFoundryHealth(CloudFoundryPlatform cloudFoundryPlatform) {
        this.cloudFoundryPlatform = cloudFoundryPlatform;
    }

    @Autowired(required = false)
    private CloudFoundryPlatform cloudFoundryPlatform;


    @Override
    public SystemHealthState getHealth() {
        try {
            switch (cloudFoundryPlatform.getApiStatus()) {
                case OK:
                    return SystemHealthState.OK;
                case ERROR:
                    return SystemHealthState.ERROR;
                default:
                    return SystemHealthState.UNKNOWN;
            }
        } catch (RuntimeException e) {
            log.error("Could not resolve API Status. Returning error", e);
            return SystemHealthState.UNKNOWN;
        }
    }
}
