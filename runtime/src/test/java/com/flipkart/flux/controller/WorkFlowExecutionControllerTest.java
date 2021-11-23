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
 *
 */

package com.flipkart.flux.controller;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActorRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.flux.MockActorRef;
import com.flipkart.flux.api.EventData;
import com.flipkart.flux.api.EventDefinition;
import com.flipkart.flux.api.ExecutionUpdateData;
import com.flipkart.flux.api.VersionedEventData;
import com.flipkart.flux.api.core.TaskExecutionMessage;
import com.flipkart.flux.constant.RuntimeConstants;
import com.flipkart.flux.dao.iface.AuditDAO;
import com.flipkart.flux.dao.iface.EventsDAO;
import com.flipkart.flux.dao.iface.StateMachinesDAO;
import com.flipkart.flux.dao.iface.StateTraversalPathDAO;
import com.flipkart.flux.dao.iface.StatesDAO;
import com.flipkart.flux.domain.*;
import com.flipkart.flux.exception.IllegalEventException;
import com.flipkart.flux.exception.ReplayableRetryExhaustException;
import com.flipkart.flux.exception.TraversalPathException;
import com.flipkart.flux.impl.message.TaskAndEvents;
import com.flipkart.flux.impl.task.registry.RouterRegistry;
import com.flipkart.flux.metrics.iface.MetricsClient;
import com.flipkart.flux.representation.ClientElbPersistenceService;
import com.flipkart.flux.representation.ReplayEventPersistenceService;
import com.flipkart.flux.task.redriver.RedriverRegistry;
import com.flipkart.flux.taskDispatcher.ExecutionNodeTaskDispatcher;
import com.flipkart.flux.util.TestUtils;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class WorkFlowExecutionControllerTest {

    @Mock
    StateMachinesDAO stateMachinesDAO;

    @Mock
    EventsDAO eventsDAO;

    @Mock
    StatesDAO statesDAO;

    @Mock
    AuditDAO auditDAO;

    @Mock
    StateTraversalPathDAO stateTraversalPathDAO;
    TestActorRef<MockActorRef> mockActor;
    ActorSystem actorSystem;

    @Mock
    private RouterRegistry routerRegistry;

    @Mock
    private ExecutionNodeTaskDispatcher executionNodeTaskDispatcher;

    @Mock
    private RedriverRegistry redriverRegistry;

    @Mock
    private MetricsClient metricsClient;

    @Mock
    private ClientElbPersistenceService clientElbPersistenceService;

    @Mock
    private ReplayEventPersistenceService replayEventPersistenceService;

    private WorkFlowExecutionController workFlowExecutionController;
    private ObjectMapper objectMapper;

    @Before
    public void setUp() throws Exception {
        Thread.sleep(1000);
        workFlowExecutionController = new WorkFlowExecutionController(eventsDAO, stateMachinesDAO, statesDAO, auditDAO,
                stateTraversalPathDAO, executionNodeTaskDispatcher, redriverRegistry, metricsClient,
                clientElbPersistenceService, replayEventPersistenceService);
        when(stateMachinesDAO.findById(anyString())).thenReturn(TestUtils.getStandardTestMachineWithId());
        when(clientElbPersistenceService.findByIdClientElb(anyString())).thenReturn("http://localhost:9997");
        actorSystem = ActorSystem.create("testActorSystem",ConfigFactory.load("testAkkaActorSystem"));
        mockActor = TestActorRef.create(actorSystem, Props.create(MockActorRef.class));
        when(routerRegistry.getRouter(anyString())).thenReturn(mockActor);
        objectMapper = new ObjectMapper();
    }

    @After
    public void tearDown() throws Exception {
        actorSystem.terminate();
    }

    @Test
    public void testEventPost_shouldForwardToTaskDispatcher() throws Exception {
        final VersionedEventData testEventData = new VersionedEventData("event0", "java.lang.String",
                "42", "runtime");

        when(eventsDAO.findValidEventsByStateMachineIdAndExecutionVersionAndName("standard-machine", "event0",
                0L)).thenReturn(new Event(
                        "event0", "java.lang.String", Event.EventStatus.pending,
                "standard-machine", null, null));
        VersionedEventData[] expectedEvents = new VersionedEventData[]{new VersionedEventData("event0",
                "java.lang.String", "42", "runtime")};
        when(eventsDAO.findTriggeredOrCancelledEventsNamesBySMId("standard-machine")).thenReturn(Collections.singletonList("event0"));
        when(executionNodeTaskDispatcher.forwardExecutionMessage(anyString(), anyObject())).thenReturn(Response.Status.ACCEPTED.getStatusCode());
        workFlowExecutionController.postEvent(testEventData, "standard-machine");
        State state = stateMachinesDAO.findById("standard-machine").getStates().stream().filter((s) -> s.getId() == 4L).findFirst().orElse(null);

        TaskExecutionMessage msg = new TaskExecutionMessage();
        msg.setRouterName(WorkFlowExecutionController.getRouterName(state.getTask()));
        msg.setAkkaMessage(new TaskAndEvents(state.getName(), state.getTask(), state.getId(), expectedEvents, state.getStateMachineId(), "test_state_machine", state.getOutputEvent(), state.getRetryCount()));
        verify(executionNodeTaskDispatcher, times(1)).forwardExecutionMessage("http://localhost:9997" + "/api/execution", msg);
        verifyNoMoreInteractions(executionNodeTaskDispatcher);
    }

    @Test
    public void testEventPost_shouldNotFetchEventDataFromDBIfStateIsDependantOnSingleEvent() throws Exception {
        final VersionedEventData testEventData = new VersionedEventData("event1", "foo",
                "someStringData", "runtime");
        when(eventsDAO.findValidEventsByStateMachineIdAndExecutionVersionAndName("standard-machine", "event1",
                0L)).thenReturn(new Event(
                        "event1", "foo", Event.EventStatus.pending, "1",
                null, null));
        when(eventsDAO.findTriggeredOrCancelledEventsNamesBySMId("standard-machine")).thenReturn(Collections.singletonList("event1"));
        workFlowExecutionController.postEvent(testEventData, "standard-machine");

        // As states 2 and 3 dependant on single event there should be no more interactions with eventDAO to fetch event data
        verify(eventsDAO, times(0)).findByEventNamesAndSMId("standard-machine", Collections.singletonList("event1"));
    }

    @Test
    public void testEventPost_taskRedriveDelay() throws Exception {
        final VersionedEventData testEventData1 = new VersionedEventData("event1", "java.lang.String",
                "42", "runtime");

        when(eventsDAO.findValidEventsByStateMachineIdAndExecutionVersionAndName("standard-machine", "event1",
                0L)).thenReturn(new Event(
                        "event1", "java.lang.String", Event.EventStatus.pending, "1",
                null, null));
        VersionedEventData[] expectedEvents1 = new VersionedEventData[]{new VersionedEventData("event1",
                "java.lang.String", "42", "runtime")};
        when(eventsDAO.findByEventNamesAndSMId("standard-machine", Collections.singletonList("event1"))).thenReturn(Arrays.asList(expectedEvents1));
        when(eventsDAO.findTriggeredOrCancelledEventsNamesBySMId("standard-machine")).thenReturn(Collections.singletonList("event1"));
        workFlowExecutionController.postEvent(testEventData1, "standard-machine");

        final VersionedEventData testEventData0 = new VersionedEventData("event0", "java.lang.String",
                "42", "runtime");
        when(eventsDAO.findValidEventsByStateMachineIdAndExecutionVersionAndName("standard-machine", "event0",
                0L)).thenReturn(new Event("event0", "java.lang.String",
                Event.EventStatus.pending, "1", null, null));
        VersionedEventData[] expectedEvents0 = new VersionedEventData[]{new VersionedEventData("event0",
                "java.lang.String", "42", "runtime")};
        when(eventsDAO.findByEventNamesAndSMId("standard-machine", Collections.singletonList("event0"))).thenReturn(Arrays.asList(expectedEvents0));
        when(eventsDAO.findTriggeredOrCancelledEventsNamesBySMId("standard-machine")).thenReturn(Collections.singletonList("event0"));
        workFlowExecutionController.postEvent(testEventData0, "standard-machine");

        // give time to execute
        Thread.sleep(2000);

        verify(redriverRegistry).registerTask(2L, "standard-machine", 32800, 0L); //state with id 2 has 3 retries and 100ms timeout
        verify(redriverRegistry).registerTask(4L, "standard-machine", 8400, 0L); //state with id 4 has 1 retries and 100ms timeout
    }

    @Test
    public void testEventPost_shouldNotSendExecuteTaskIfItIsAlreadyCompleted() throws Exception {
        final VersionedEventData testEventData = new VersionedEventData("event0", "java.lang.String",
                "42", "runtime");

        when(eventsDAO.findValidEventsByStateMachineIdAndExecutionVersionAndName("standard-machine", "event0",
                0L)).thenReturn(new Event("event0", "java.lang.String",
                Event.EventStatus.pending, "1", null, null));
        when(eventsDAO.findTriggeredOrCancelledEventsNamesBySMId("standard-machine")).thenReturn(Collections.singletonList("event0"));
        when(executionNodeTaskDispatcher.forwardExecutionMessage(anyString(), anyObject())).thenReturn(Response.Status.ACCEPTED.getStatusCode());
        workFlowExecutionController.postEvent(testEventData, "standard-machine");
        State state = stateMachinesDAO.findById("standard-machine").getStates().stream().filter((s) -> s.getId() == 4L).findFirst().orElse(null);
        state.setStatus(Status.completed);

        //post the event again, this should not send msg to router for execution
        workFlowExecutionController.postEvent(testEventData, "standard-machine");

        verify(executionNodeTaskDispatcher, times(1)).forwardExecutionMessage(anyString(), anyObject());
        verifyNoMoreInteractions(executionNodeTaskDispatcher);
    }

	@Test
    public void testEventPost_shouldSendExecuteTaskIfItIsNotCompleted() throws Exception {
        final VersionedEventData testEventData = new VersionedEventData("event0", "java.lang.String",
                "42", "runtime");

        when(eventsDAO.findValidEventsByStateMachineIdAndExecutionVersionAndName("standard-machine", "event0",
                0L)).thenReturn(new Event("event0", "java.lang.String",
                Event.EventStatus.pending, "1", null, null));
        when(eventsDAO.findTriggeredOrCancelledEventsNamesBySMId("standard-machine")).thenReturn(Collections.singletonList("event0"));
        when(executionNodeTaskDispatcher.forwardExecutionMessage(anyString(), anyObject())).thenReturn(Response.Status.ACCEPTED.getStatusCode());
        workFlowExecutionController.postEvent(testEventData, "standard-machine");
        StateMachine stateMachine = stateMachinesDAO.findById("standard-machine");
        State state = stateMachinesDAO.findById("standard-machine").getStates().stream().filter((s) -> s.getId() == 4L).findFirst().orElse(null);

        state.setStatus(Status.errored);

        //post the event again, this should send msg to router again for execution
        workFlowExecutionController.postEvent(testEventData, "standard-machine");
        verify(executionNodeTaskDispatcher, times(2)).forwardExecutionMessage(anyString(), anyObject());
        verifyNoMoreInteractions(executionNodeTaskDispatcher);
    }

    @Test
    public void testEventPost_shouldNotSendExecuteTaskIfItIsCancelled() throws Exception {
        final VersionedEventData testEventData = new VersionedEventData("event0", "java.lang.String",
                "42", "runtime");

        when(eventsDAO.findValidEventsByStateMachineIdAndExecutionVersionAndName("standard-machine", "event0",
                0L)).thenReturn(new Event("event0", "java.lang.String",
                Event.EventStatus.pending, "1", null, null));
        when(eventsDAO.findTriggeredOrCancelledEventsNamesBySMId("standard-machine")).thenReturn(Collections.singletonList("event0"));
        when(executionNodeTaskDispatcher.forwardExecutionMessage(anyString(), anyObject())).thenReturn(Response.Status.ACCEPTED.getStatusCode());
        workFlowExecutionController.postEvent(testEventData, "standard-machine");
        StateMachine stateMachine = stateMachinesDAO.findById("standard-machine");
        State state = stateMachine.getStates().stream().filter((s) -> s.getId() == 4L).findFirst().orElse(null);
        state.setStatus(Status.cancelled);
        //post the event again, this should not send msg to router for execution
        workFlowExecutionController.postEvent(testEventData, "standard-machine");
        // Dispatcher Thread should only forward the task for Execution only once.
        verify(executionNodeTaskDispatcher, times(1)).forwardExecutionMessage(anyString(), anyObject());
    }

    @Test
    public void testCancelPath_shouldCancelPathTillJoinNode() throws Exception {

        //setup the below state machine, and do cancel with event3, that should cancel till state3
        //
        //   state1 --------(event1)---------> state2 --------(event2)--------------------> state3
        //    |                                                                               ^
        //    |                                                                               |
        //    |                           ---(event3)--> state5 --(event4)---              (event6)
        //    |                          |                                   |                |
        //    |----(event1)---> state4 --                                    |---> state7 ----
        //                               |---(event3)--> state6 --(event5)---|
        //
        HashMap<String, Event.EventStatus> eventStatusHashMap = new HashMap<String, Event.EventStatus>() {{
            put("event1", Event.EventStatus.triggered);
            put("event2", Event.EventStatus.triggered);
            put("event3", Event.EventStatus.pending);
            put("event4", Event.EventStatus.pending);
            put("event5", Event.EventStatus.pending);
        }};

        String outputEvent1 = null;
        String outputEvent2 = null;
        String outputEvent3 = null;
        String outputEvent4 = null;
        String outputEvent5 = null;
        String outputEvent6 = null;
        try {
            outputEvent1 = objectMapper.writeValueAsString(new EventDefinition("event1", "SomeEvent.class"));
            outputEvent2 = objectMapper.writeValueAsString(new EventDefinition("event2", "SomeEvent.class"));
            outputEvent3 = objectMapper.writeValueAsString(new EventDefinition("event3", "SomeEvent.class"));
            outputEvent4 = objectMapper.writeValueAsString(new EventDefinition("event4", "SomeEvent.class"));
            outputEvent5 = objectMapper.writeValueAsString(new EventDefinition("event5", "SomeEvent.class"));
            outputEvent6 = objectMapper.writeValueAsString(new EventDefinition("event6", "SomeEvent.class"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        State state1 = new State(1L, "state1", null, null, null, null, new ArrayList<>(), 0L, 1000L, outputEvent1, Status.initialized, null, 0L, "state-machine-cancel-path", 1L);
        State state2 = new State(1L, "state2", null, null, null, null, new ArrayList<String>() {{
            add("event1");
        }}, 0L, 1000L, outputEvent2, Status.initialized, null, 0L, "state-machine-cancel-path", 2L);
        State state3 = new State(1L, "state3", null, null, null, null, new ArrayList<String>() {{
            add("event2");
            add("event6");
        }}, 0L, 1000L, null, Status.initialized, null, 0L, "state-machine-cancel-path", 3L);
        State state4 = new State(1L, "state4", null, null, null, null, new ArrayList<String>() {{
            add("event1");
        }}, 0L, 1000L, outputEvent3, Status.initialized, null, 0L, "state-machine-cancel-path", 4L);
        State state5 = new State(1L, "state5", null, null, null, null, new ArrayList<String>() {{
            add("event3");
        }}, 0L, 1000L, outputEvent4, Status.initialized, null, 0L, "state-machine-cancel-path", 5L);
        State state6 = new State(1L, "state6", null, null, null, null, new ArrayList<String>() {{
            add("event3");
        }}, 0L, 1000L, outputEvent5, Status.initialized, null, 0L, "state-machine-cancel-path", 6L);
        State state7 = new State(1L, "state7", null, null, null, null, new ArrayList<String>() {{
            add("event4");
            add("event5");
        }}, 0L, 1000L, outputEvent6, Status.initialized, null, 0L, "state-machine-cancel-path", 7L);

        Set<State> states = new HashSet<State>() {{
            add(state1);
            add(state2);
            add(state3);
            add(state4);
            add(state5);
            add(state6);
            add(state7);
        }};
        StateMachine stateMachine = new StateMachine("state-machine-cancel-path", 1L, "state_machine_1",
                null, states, "client_elb_id_1");
        VersionedEventData testEventData = new VersionedEventData("event3", null, null, "runtime", true);
        when(eventsDAO.getAllEventsNameAndStatus("state-machine-cancel-path", true)).thenReturn(eventStatusHashMap);
        when(stateMachinesDAO.findById("state-machine-cancel-path")).thenReturn(stateMachine);
        // invoke cancel
        Set<State> executableStates = workFlowExecutionController.cancelPath(stateMachine.getId(), testEventData);

        assertThat(executableStates.size()).isEqualTo(1);
        assertThat(executableStates.contains("state3"));
        verify(eventsDAO).markEventAsCancelled("state-machine-cancel-path", "event3");
        verify(eventsDAO).markEventAsCancelled("state-machine-cancel-path", "event4");
        verify(eventsDAO).markEventAsCancelled("state-machine-cancel-path", "event5");
        verify(eventsDAO).markEventAsCancelled("state-machine-cancel-path", "event6");

        verify(statesDAO).updateStatus("state-machine-cancel-path", 5L, Status.cancelled);
        verify(statesDAO).updateStatus("state-machine-cancel-path", 6L, Status.cancelled);
        verify(statesDAO).updateStatus("state-machine-cancel-path", 7L, Status.cancelled);


    }

    @Test
    public void testUpdateTaskStatusBehaviourWhenTaskStatusIsUpdatedToRunning() {
        when(statesDAO.findById("random-state-machine", 1L)).thenReturn(
                new State(1L, "random-state", null, null, null, null,
                        new ArrayList<>(), 0L, 1000L, null, Status.initialized,
                        null, 0L, "random-state-machine", 1L));
        workFlowExecutionController.updateTaskStatus("random-state-machine", 1L, 0L,
                new ExecutionUpdateData("random-state-machine", "someStateMachine",
                        "someTask", 1L, com.flipkart.flux.api.Status.running,
                        0, 1, "", false, ""));
        verify(statesDAO).updateStatus("random-state-machine", 1L, Status.running);
        verify(auditDAO).create("random-state-machine", new AuditRecord("random-state-machine", 1L, 1L, Status.running, null , ""));
        verifyNoMoreInteractions(redriverRegistry);
    }

    @Test
    public void testUpdateTaskStatusBehaviourWhenTaskStatusIsUpdatedToCompleted() {
        when(statesDAO.findById("random-state-machine", 1L)).thenReturn(
                new State(1L, "random-state", null, null, null, null,
                        new ArrayList<>(), 0L, 1000L, null, Status.initialized,
                        null, 0L, "random-state-machine", 1L));
        workFlowExecutionController.updateTaskStatus("random-state-machine", 1L, 0L,
                new ExecutionUpdateData("random-state-machine", "someStateMachine",
                        "someTask", 1L, com.flipkart.flux.api.Status.completed,
                        0, 1, "", true, ""));
        verify(statesDAO).updateStatus("random-state-machine", 1L, Status.completed);
        verify(auditDAO).create("random-state-machine", new AuditRecord("random-state-machine", 1L, 1L, Status.completed, null , ""));
        verify(redriverRegistry).deRegisterTask("random-state-machine",1L, 0L);
    }

    @Test(expected = ReplayableRetryExhaustException.class)
    public void testPostReplayEventExhaustedAttemptedRetryCount() throws IllegalEventException, IOException {

        String onEntryHook = "com.flipkart.flux.dao.DummyOnEntryHook";
        String task = "com.flipkart.flux.dao.TestWorkflow_dummyTask";
        String onExitHook = "com.flipkart.flux.dao.DummyOnExitHook";
        final Event TestReplayEvent = new Event("event3", "someType", Event.EventStatus.pending, "ReplayEventTestStateMachine", null, RuntimeConstants.REPLAY_EVENT);
        List<String> dependencies = new ArrayList<>();
        dependencies.add(TestReplayEvent.getName());
        State state1 = new State(2L, "state1", "desc1", onEntryHook, task, onExitHook, Collections.emptyList(), 3L, 60L, null, null, null, 0l, "ReplayEventTestStateMachine", 1L);
        State state2 = new State(2L, "state2", "desc2", onEntryHook, task, onExitHook, dependencies, 3L, 60L, null, Status.completed, null, 0l, "ReplayEventTestStateMachine", 2L, (short) 5, (short) 5, Boolean.TRUE);
        Set<State> states = new HashSet<>();
        states.add(state1);
        states.add(state2);
        StateMachine stateMachine1 = new StateMachine("ReplayEventTestStateMachine", 2L, "SM_name", "SM_desc", states,
                "client_elb_id_1");
        Event event = eventsDAO.create(TestReplayEvent.getStateMachineInstanceId(), TestReplayEvent);
        stateMachinesDAO.create(stateMachine1.getId(), stateMachine1);
        when(statesDAO.findStateIdByEventName("ReplayEventTestStateMachine", TestReplayEvent.getName())).thenReturn(state2.getId());
        when(statesDAO.findById("ReplayEventTestStateMachine", 2L)).thenReturn(state2);
        when(stateTraversalPathDAO.findById(stateMachine1.getId(), state2.getId())).thenReturn(Optional.empty());
        EventData eventData = new EventData(TestReplayEvent.getName(), TestReplayEvent.getType(), TestReplayEvent.getEventData(), TestReplayEvent.getEventSource());
        workFlowExecutionController.postReplayEvent(eventData, stateMachine1);

    }

    @Test(expected = TraversalPathException.class)
    public void testPostReplayEvent() throws IllegalEventException, IOException {

        String onEntryHook = "com.flipkart.flux.dao.DummyOnEntryHook";
        String task = "com.flipkart.flux.dao.TestWorkflow_dummyTask";
        String onExitHook = "com.flipkart.flux.dao.DummyOnExitHook";
        final Event testReplayEvent = new Event("event3", "someType", Event.EventStatus.pending, "ReplayEventTestStateMachine1", null, RuntimeConstants.REPLAY_EVENT);
        final Event event1 = new Event("event1", "someEvent", Event.EventStatus.pending, "ReplayEventTestStateMachine1", null, null);

        List<String> dependencies = new ArrayList<>();
        dependencies.add(testReplayEvent.getName());
        String outputEvent1 = objectMapper.writeValueAsString(new EventDefinition("event1", "SomeEvent"));
        State state1 = new State(2L, "state1", "desc1", onEntryHook, task, onExitHook, Collections.emptyList(), 3L, 60L, outputEvent1, null, null, 0l, "ReplayEventTestStateMachine1", 1L);
        State state2 = new State(2L, "state2", "desc2", onEntryHook, task, onExitHook, dependencies, 3L, 60L, outputEvent1, Status.completed, null, 0l, "ReplayEventTestStateMachine1", 2L, (short) 5, (short) 2, Boolean.TRUE);
        Set<State> states = new HashSet<>();
        states.add(state1);
        states.add(state2);
        StateMachine stateMachine1 = new StateMachine("ReplayEventTestStateMachine1", 2L, "SM_name", "SM_desc", states,
                "client_elb_id_1");
        when(statesDAO.findStateIdByEventName(stateMachine1.getId(), testReplayEvent.getName())).thenReturn(state2.getId());
        when(statesDAO.findById(stateMachine1.getId(), state2.getId())).thenReturn(state2);
        when(eventsDAO.findValidEventBySMIdAndName(stateMachine1.getId(), testReplayEvent.getName())).thenReturn(testReplayEvent);
        when(eventsDAO.findValidEventBySMIdAndName(stateMachine1.getId(), event1.getName())).thenReturn(event1);
        when(stateTraversalPathDAO.findById(stateMachine1.getId(), state2.getId())).thenReturn(Optional.empty());
        EventData eventData = new EventData(testReplayEvent.getName(), testReplayEvent.getType(), testReplayEvent.getEventData(), testReplayEvent.getEventSource());
        workFlowExecutionController.postReplayEvent(eventData, stateMachine1);
        verify(statesDAO).incrementReplayableRetries("ReplayEventTestStateMachine1", 2L, (short) 10);

    }
}