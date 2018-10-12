package com.gemalto.chaos.ssh.impl.experiments;

import com.gemalto.chaos.ssh.ShellSessionCapability;
import com.gemalto.chaos.ssh.SshExperiment;
import com.gemalto.chaos.ssh.enums.ShellCapabilityType;
import com.gemalto.chaos.ssh.enums.ShellSessionCapabilityOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomProcessTermination extends SshExperiment {
    private static final Logger log = LoggerFactory.getLogger(RandomProcessTermination.class);

    public RandomProcessTermination () {
        super();
        buildRequiredCapabilities();
    }

    @Override
    protected void buildRequiredCapabilities () {
        requiredCapabilities.add(new ShellSessionCapability(ShellCapabilityType.SHELL).addCapabilityOption(ShellSessionCapabilityOption.BASH)
                                                                                      .addCapabilityOption(ShellSessionCapabilityOption.ASH)
                                                                                      .addCapabilityOption(ShellSessionCapabilityOption.SH));
        requiredCapabilities.add(new ShellSessionCapability(ShellCapabilityType.BINARY).addCapabilityOption(ShellSessionCapabilityOption.TYPE));
        requiredCapabilities.add(new ShellSessionCapability(ShellCapabilityType.BINARY).addCapabilityOption(ShellSessionCapabilityOption.GREP));
        requiredCapabilities.add(new ShellSessionCapability(ShellCapabilityType.BINARY).addCapabilityOption(ShellSessionCapabilityOption.KILL));
        requiredCapabilities.add(new ShellSessionCapability(ShellCapabilityType.BINARY).addCapabilityOption(ShellSessionCapabilityOption.SORT));
        requiredCapabilities.add(new ShellSessionCapability(ShellCapabilityType.BINARY).addCapabilityOption(ShellSessionCapabilityOption.HEAD));
    }

    @Override
    protected String getExperimentName () {
        return "Random Process Termination";
    }

    @Override
    protected String getExperimentCommand () {
        return "kill $(cd /proc;ls -1 | grep '[0-9]' |sort -R | head -1)";
    }

    @Override
    protected int getSshSessionMaxDuration () {
        return 10;
    }
}