package com.gemalto.chaos.container.impl;

import com.gemalto.chaos.attack.Attack;
import com.gemalto.chaos.container.enums.ContainerHealth;
import com.gemalto.chaos.platform.impl.AwsPlatform;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
public class AwsEC2ContainerTest {
    private static final String KEY_NAME = randomUUID().toString();
    private static final String NAME = randomUUID().toString();
    private static final String INSTANCE_ID = randomUUID().toString();
    private AwsEC2Container awsEC2Container;
    @MockBean
    private AwsPlatform awsPlatform;
    @Spy
    private Attack attack = new Attack() {
    };

    @Before
    public void setUp () {
        awsEC2Container = AwsEC2Container.builder()
                                         .keyName(KEY_NAME)
                                         .instanceId(INSTANCE_ID)
                                         .awsPlatform(awsPlatform)
                                         .name(NAME)
                                         .build();
    }

    @Test
    public void getPlatform () {
        assertEquals(awsPlatform, awsEC2Container.getPlatform());
    }

    @Test
    public void updateContainerHealthImpl () {
        for (ContainerHealth containerHealth : ContainerHealth.values()) {
            when(awsPlatform.checkHealth(any(String.class))).thenReturn(containerHealth);
            assertEquals(containerHealth, awsEC2Container.updateContainerHealthImpl(null));
        }
    }

    @Test
    public void stopContainer () {
        awsEC2Container.stopContainer(attack);
        verify(attack, times(1)).setCheckContainerHealth(ArgumentMatchers.any());
        verify(attack, times(1)).setSelfHealingMethod(ArgumentMatchers.any());
        Mockito.verify(awsPlatform, times(1)).stopInstance(INSTANCE_ID);
    }

    @Test
    public void restartContainer () {
        awsEC2Container.restartContainer(attack);
        verify(attack, times(1)).setCheckContainerHealth(ArgumentMatchers.any());
        verify(attack, times(1)).setSelfHealingMethod(ArgumentMatchers.any());
        Mockito.verify(awsPlatform, times(1)).restartInstance(INSTANCE_ID);
    }

    @Test
    public void getSimpleName () {
        String EXPECTED_NAME = String.format("%s (%s) [%s]", NAME, KEY_NAME, INSTANCE_ID);
        assertEquals(EXPECTED_NAME, awsEC2Container.getSimpleName());
    }

    @Test
    public void removeSecurityGroups () {
        String chaosSecurityGroupId = UUID.randomUUID().toString();
        List<String> configuredSecurityGroupId = Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID()
                                                                                                 .toString());
        doReturn(configuredSecurityGroupId).when(awsPlatform).getSecurityGroupIds(INSTANCE_ID);
        doReturn(chaosSecurityGroupId).when(awsPlatform).getChaosSecurityGroupId();
        Mockito.verify(awsPlatform, times(0))
               .setSecurityGroupIds(INSTANCE_ID, Collections.singletonList(chaosSecurityGroupId));
        verify(attack, times(0)).setCheckContainerHealth(ArgumentMatchers.any());
        verify(attack, times(0)).setSelfHealingMethod(ArgumentMatchers.any());
        awsEC2Container.removeSecurityGroups(attack);
        verify(attack, times(1)).setCheckContainerHealth(ArgumentMatchers.any());
        verify(attack, times(1)).setSelfHealingMethod(ArgumentMatchers.any());
        Mockito.verify(awsPlatform, times(1))
               .setSecurityGroupIds(INSTANCE_ID, Collections.singletonList(chaosSecurityGroupId));
        Mockito.verify(awsPlatform, times(0)).verifySecurityGroupIds(INSTANCE_ID, configuredSecurityGroupId);
        try {
            attack.getCheckContainerHealth().call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Mockito.verify(awsPlatform, times(1)).verifySecurityGroupIds(INSTANCE_ID, configuredSecurityGroupId);
        Mockito.verify(awsPlatform, times(0)).setSecurityGroupIds(INSTANCE_ID, configuredSecurityGroupId);
        try {
            attack.getSelfHealingMethod().call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Mockito.verify(awsPlatform, times(1)).setSecurityGroupIds(INSTANCE_ID, configuredSecurityGroupId);
    }
}