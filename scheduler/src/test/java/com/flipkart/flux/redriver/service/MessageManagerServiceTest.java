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

package com.flipkart.flux.redriver.service;

import com.flipkart.flux.redriver.dao.MessageDao;
import com.flipkart.flux.redriver.model.SmIdAndTaskIdPair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MessageManagerServiceTest {
    private final String sampleMachineId = "test-machine-id";
    @Mock
    MessageDao messageDao;
    private MessageManagerService messageManagerService;

    @Before
    public void setup() {

    }

    @Test
    public void testRemoval_shouldDeferRemoval() throws Exception {
        messageManagerService = new MessageManagerService(messageDao, 50, 500, 10, 500, 10);
        messageManagerService.initialize(); // Will be called by polyguice in the production env

        messageManagerService.scheduleForRemoval(sampleMachineId, 123l);
        messageManagerService.scheduleForRemoval(sampleMachineId, 123l);
        messageManagerService.scheduleForRemoval(sampleMachineId, 123l);

        verifyZeroInteractions(messageDao);

        Thread.sleep(700l);
        ArrayList<SmIdAndTaskIdPair> firstBatch = new ArrayList<SmIdAndTaskIdPair>();
        firstBatch.add(new SmIdAndTaskIdPair(sampleMachineId, 123l));
        firstBatch.add(new SmIdAndTaskIdPair(sampleMachineId, 123l));
        firstBatch.add(new SmIdAndTaskIdPair(sampleMachineId, 123l));

        verify(messageDao,times(1)).deleteInBatch(firstBatch);

    }

    @Test
    public void testRemoval_shouldDeleteInBatches() throws Exception {
        messageManagerService = new MessageManagerService(messageDao, 50, 500, 2, 500, 10);
        messageManagerService.initialize(); // Will be called by polyguice in the production env

        messageManagerService.scheduleForRemoval(sampleMachineId, 121l);
        messageManagerService.scheduleForRemoval(sampleMachineId, 122l);
        messageManagerService.scheduleForRemoval(sampleMachineId, 123l);

        verifyZeroInteractions(messageDao);

        Thread.sleep(700l);
        ArrayList<SmIdAndTaskIdPair> firstBatch = new ArrayList<SmIdAndTaskIdPair>();
        firstBatch.add(new SmIdAndTaskIdPair(sampleMachineId, 121l));
        firstBatch.add(new SmIdAndTaskIdPair(sampleMachineId, 122l));

        verify(messageDao,times(1)).deleteInBatch(firstBatch);

        Thread.sleep(700l);
        ArrayList<SmIdAndTaskIdPair> secondBatch = new ArrayList<SmIdAndTaskIdPair>();
        secondBatch.add(new SmIdAndTaskIdPair(sampleMachineId, 123l));
        verify(messageDao,times(1)).deleteInBatch(secondBatch);
    }
}