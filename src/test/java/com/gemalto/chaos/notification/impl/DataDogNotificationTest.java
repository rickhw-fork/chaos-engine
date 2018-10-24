package com.gemalto.chaos.notification.impl;

import com.gemalto.chaos.container.Container;
import com.gemalto.chaos.experiment.enums.ExperimentType;
import com.gemalto.chaos.notification.ChaosEvent;
import com.gemalto.chaos.notification.enums.NotificationLevel;
import com.timgroup.statsd.Event;
import com.timgroup.statsd.StatsDClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
public class DataDogNotificationTest {
    private ChaosEvent chaosEvent;
    @Mock
    private Container container;
    private DataDogNotification.DataDogEvent dataDogEvent ;

    private String experimentId=UUID.randomUUID().toString();
    private String experimentMethod=UUID.randomUUID().toString();
    private String message = UUID.randomUUID().toString();

    private String sourcetypeName="Java";
    private String eventPrefix="Chaos Event ";

    private NotificationLevel level = NotificationLevel.WARN;
    private ArrayList<String> expectedTags = new ArrayList<>();
    @Before
    public void setUp () {
        chaosEvent= ChaosEvent.builder()
                              .withMessage(message)
                              .withExperimentId(experimentId)
                              .withMessage(message)
                              .withNotificationLevel(level)
                              .withTargetContainer(container)
                              .withExperimentMethod(experimentMethod)
                              .withExperimentType(ExperimentType.STATE)
                              .build();
        when(container.getSimpleName()).thenReturn(UUID.randomUUID().toString());
        dataDogEvent= new DataDogNotification().new DataDogEvent(chaosEvent);


        expectedTags.add("ExperimentId:" + chaosEvent.getExperimentId());
        expectedTags.add("Method:" + chaosEvent.getExperimentMethod());
        expectedTags.add("Type:" + chaosEvent.getExperimentType().name());
        expectedTags.add("Target:" + chaosEvent.getTargetContainer().getSimpleName());

    }
    @Test
    public void logEvent(){
        StatsDClient client = Mockito.mock(StatsDClient.class);
        DataDogNotification notif = new DataDogNotification(client);
        notif.logEvent(chaosEvent);
        verify(client,times(1)).recordEvent(ArgumentMatchers.any(),ArgumentMatchers.any());
    }

    @Test
    public void mapLevel(){
        assertEquals(Event.AlertType.WARNING,dataDogEvent.mapLevel(NotificationLevel.WARN));
        assertEquals(Event.AlertType.ERROR,dataDogEvent.mapLevel(NotificationLevel.ERROR));
        assertEquals(Event.AlertType.SUCCESS,dataDogEvent.mapLevel(NotificationLevel.GOOD));
    }
    @Test
    public void buildEvent(){
        Event expectedEvent = Event.builder()
                                   .withText(message)
                                   .withTitle(eventPrefix+ experimentMethod)
                                   .withAlertType(Event.AlertType.WARNING)
                                   .withSourceTypeName(sourcetypeName)
                                   .build();
        Event actualEvent =dataDogEvent.buildEvent();
        assertEquals(expectedEvent.getTitle(),actualEvent.getTitle());
        assertEquals(expectedEvent.getText(),actualEvent.getText());
        assertEquals(expectedEvent.getAlertType(),actualEvent.getAlertType());
        assertEquals(expectedEvent.getSourceTypeName(),actualEvent.getSourceTypeName());
    }

    @Test
    public void getTags(){
        ArrayList<String> actualTags= new ArrayList<>(Arrays.asList(dataDogEvent.generateTags()));
        assertThat(expectedTags,is(actualTags));
    }
}