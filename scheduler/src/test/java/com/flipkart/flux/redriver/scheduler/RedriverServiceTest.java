/*
 * Copyright 2012-2016, the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flipkart.flux.redriver.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.flipkart.flux.redriver.model.ScheduledMessage;
import com.flipkart.flux.redriver.service.MessageManagerService;
import com.flipkart.flux.redriver.service.RedriverService;
import com.flipkart.flux.task.redriver.RedriverRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RedriverServiceTest {
    @Mock
    MessageManagerService messageManagerService;

    @Mock
    RedriverRegistry redriverRegistry;

    private RedriverService redriverService;

    private int batchSize = 2;

    @Before
    public void setUp() throws Exception {
        redriverService = new RedriverService(messageManagerService, redriverRegistry, 500, batchSize);
        redriverService.setInitialDelay(0L);
    }

    @Test
    public void testIdle() throws Exception {
        Thread.sleep(200);
        Assert.assertFalse(redriverService.isRunning());
        verifyZeroInteractions(messageManagerService);
        verifyZeroInteractions(redriverRegistry);
    }

    @Test
    public void testRedriveMessage_shouldRedriveWhenOldMessageFound() throws Exception {
        long now = System.currentTimeMillis();
        when(messageManagerService.retrieveOldest(0, batchSize))
                .thenReturn(Arrays.asList(new ScheduledMessage(1l, "sample-state-machine-uuid", now - 2, 0l)))
                .thenReturn(Arrays.asList());
        redriverService.start();
        Thread.sleep(300);
        verify(redriverRegistry).redriveTask("sample-state-machine-uuid", 1l,0l);
        Thread.sleep(500);
        verifyNoMoreInteractions(redriverRegistry);
    }

    @Test
    public void testLargeNoOfRedriveMessages_allShouldBeRedrived() throws Exception {
        ArrayList<ScheduledMessage> scheduledMessages = new ArrayList<>();
        int batchSize = 100;
        long now = System.currentTimeMillis();
        for (long i = 0; i < 1000; i++)
            scheduledMessages.add(new ScheduledMessage(i, "sample-state-machine-uuid", now - 100,0l));
        redriverService = new RedriverService(messageManagerService, redriverRegistry, 100, batchSize);
        redriverService.setInitialDelay(0L);
        for (int i = 0; i < 10; i++)
            when(messageManagerService.retrieveOldest(i * 100, batchSize)).thenReturn(scheduledMessages.subList(i * 100, (i + 1) * 100)).thenReturn(null);
        redriverService.start();
        Thread.sleep(1000);
        redriverService.stop();
        for (long i = 0; i < 100; i++) {
            long x = (long) (Math.random() * 1000.0);
            verify(redriverRegistry).redriveTask("sample-state-machine-uuid", x,0l);
        }
    }

    @Test
    public void testStartStopCycle() throws Exception {
        when(messageManagerService.retrieveOldest(0, batchSize)).
                thenReturn(Arrays.asList(new ScheduledMessage(3l, "sample-state-machine-uuid", 1l,0l))).
                thenReturn(Arrays.asList(new ScheduledMessage(4l, "sample-state-machine-uuid", 2l,0l)));

        redriverService.start();
        Thread.sleep(100);
        Assert.assertTrue(redriverService.isRunning());
        verify(redriverRegistry).redriveTask("sample-state-machine-uuid", 3l,0l);

        redriverService.stop();
        Thread.sleep(600);
        Assert.assertFalse(redriverService.isRunning());
        verifyNoMoreInteractions(redriverRegistry);

        redriverService.start();
        Thread.sleep(100);

        verify(redriverRegistry).redriveTask("sample-state-machine-uuid", 4l,0l);
        verifyNoMoreInteractions(redriverRegistry);
    }
}