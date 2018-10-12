package com.gemalto.chaos.ssh.impl.experiments;

import com.gemalto.chaos.ssh.ShellSessionCapability;
import com.gemalto.chaos.ssh.SshExperiment;
import com.gemalto.chaos.ssh.enums.ShellCapabilityType;
import com.gemalto.chaos.ssh.enums.ShellSessionCapabilityOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForkBomb extends SshExperiment {
    private static final Logger log = LoggerFactory.getLogger(ForkBomb.class);

    public ForkBomb () {
        super();
        buildRequiredCapabilities();
    }

    @Override
    protected void buildRequiredCapabilities () {
        requiredCapabilities.add(new ShellSessionCapability(ShellCapabilityType.SHELL).addCapabilityOption(ShellSessionCapabilityOption.BASH)
                                                                                      .addCapabilityOption(ShellSessionCapabilityOption.ASH)
                                                                                      .addCapabilityOption(ShellSessionCapabilityOption.SH));
    }

    @Override
    protected String getExperimentName () {
        return "Fork Bomb";
    }

    @Override
    protected String getExperimentCommand () {
        return "bomb() { bomb | bomb & }; bomb; sleep 60; exit;";
    }

    @Override
    protected int getSshSessionMaxDuration () {
        return 60;
    }
}