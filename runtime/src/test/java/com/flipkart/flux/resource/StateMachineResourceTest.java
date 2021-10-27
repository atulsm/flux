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

package com.flipkart.flux.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.flux.FluxRuntimeRole;
import com.flipkart.flux.InjectFromRole;
import com.flipkart.flux.api.StateMachineDefinition;
import com.flipkart.flux.client.FluxClientComponentModule;
import com.flipkart.flux.client.FluxClientInterceptorModule;
import com.flipkart.flux.constant.RuntimeConstants;
import com.flipkart.flux.dao.ParallelScatterGatherQueryHelper;
import com.flipkart.flux.dao.TestWorkflow;
import com.flipkart.flux.dao.iface.EventsDAO;
import com.flipkart.flux.dao.iface.StateMachinesDAO;
import com.flipkart.flux.dao.iface.StatesDAO;
import com.flipkart.flux.domain.*;
import com.flipkart.flux.eventscheduler.dao.EventSchedulerDao;
import com.flipkart.flux.eventscheduler.model.ScheduledEvent;
import com.flipkart.flux.guice.module.*;
import com.flipkart.flux.initializer.ExecutionOrderedComponentBooter;
import com.flipkart.flux.initializer.OrchestrationOrderedComponentBooter;
import com.flipkart.flux.module.DeploymentUnitTestModule;
import com.flipkart.flux.module.RuntimeTestModule;
import com.flipkart.flux.representation.StateMachinePersistenceService;
import com.flipkart.flux.rule.DbClearRule;
import com.flipkart.flux.runner.GuiceJunit4Runner;
import com.flipkart.flux.runner.Modules;
import com.flipkart.flux.util.TestUtils;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.*;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(GuiceJunit4Runner.class)
@Modules(orchestrationModules = {FluxClientComponentModule.class, OrchestrationTaskModule.class, ShardModule.class,
        RuntimeTestModule.class, ContainerModule.class, FluxClientInterceptorModule.class},
        executionModules = {FluxClientComponentModule.class,
                DeploymentUnitTestModule.class, AkkaModule.class, ExecutionTaskModule.class,
                ExecutionContainerModule.class,
                FluxClientInterceptorModule.class})
public class StateMachineResourceTest {

    @Rule
    @InjectFromRole(value = FluxRuntimeRole.ORCHESTRATION)
    public DbClearRule dbClearRule;

    @InjectFromRole(value = FluxRuntimeRole.ORCHESTRATION)
    private StateMachinesDAO stateMachinesDAO;

    @InjectFromRole(value = FluxRuntimeRole.ORCHESTRATION)
    private ParallelScatterGatherQueryHelper parallelScatterGatherQueryHelper;

    @InjectFromRole(value = FluxRuntimeRole.ORCHESTRATION)
    private StatesDAO statesDAO;

    @InjectFromRole(value = FluxRuntimeRole.ORCHESTRATION)
    private EventsDAO eventsDAO;

    @InjectFromRole(value = FluxRuntimeRole.ORCHESTRATION)
    private EventSchedulerDao eventSchedulerDao;

    @InjectFromRole(value = FluxRuntimeRole.ORCHESTRATION)
    OrchestrationOrderedComponentBooter orchestrationOrderedComponentBooter;


    @InjectFromRole(value = FluxRuntimeRole.EXECUTION)
    ExecutionApiResource executionApiResource;

    @InjectFromRole(value = FluxRuntimeRole.EXECUTION)
    ExecutionOrderedComponentBooter executionOrderedComponentBooter;

    @InjectFromRole(value = FluxRuntimeRole.ORCHESTRATION)
    StateMachinePersistenceService stateMachinePersistenceService;

    public static final String STATE_MACHINE_RESOURCE_URL = "http://localhost:9998" + RuntimeConstants.API_CONTEXT_PATH + RuntimeConstants.STATE_MACHINE_RESOURCE_RELATIVE_PATH;
    private static final String SLASH = "/";
    private static final String standardStateMachine = "standard-state-machine";
    private ObjectMapper objectMapper;


    @Before
    public void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        dbClearRule.explicitClearTables();
        try {
            Unirest.post("http://localhost:9998/api/client-elb/create")
                    .queryString("clientId", "defaultElbId").queryString("clientElbUrl",
                    "http://localhost:9997").asString();
        } catch (UnirestException e) {
            e.printStackTrace();
        }
    }

    @After
    public void tearDown() throws Exception {
        try {
            Unirest.post("http://localhost:9998/api/client-elb/delete")
                    .queryString("clientId", "defaultElbId").asString();
        } catch (UnirestException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void testCreateStateMachine() throws Exception {
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("state_machine_definition.json"));
        final HttpResponse<String> response = Unirest.post(STATE_MACHINE_RESOURCE_URL).header("Content-Type", "application/json")
                .body(stateMachineDefinitionJson).asString();
        assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
        assertThat(parallelScatterGatherQueryHelper.findStateMachinesByName("test_state_machine")).hasSize(1);
        Thread.sleep(1000);
        TestUtils.assertStateMachineEquality(parallelScatterGatherQueryHelper.findStateMachinesByName("test_state_machine").iterator().next(), TestUtils.getStandardTestMachine());
    }

    @Test
    public void testUnsideline() throws Exception {
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("state_machine_definition.json"));
        final HttpResponse<String> smCreationResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL)
                .header("Content-Type", "application/json").body(stateMachineDefinitionJson).asString();
        Event event = eventsDAO.findBySMIdExecutionVersionAndName(smCreationResponse.getBody(), "event0",
                0L);
        assertThat(event.getStatus()).isEqualTo(Event.EventStatus.pending);

        // ask the task to fail with retriable error.
        TestWorkflow.shouldFail = true;

        try {
            String eventJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("event_data.json"));
            final HttpResponse<String> eventPostResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL + SLASH + smCreationResponse.getBody() + "/context/events")
                    .header("Content-Type", "application/json").body(eventJson).asString();
            assertThat(eventPostResponse.getStatus()).isEqualTo(Response.Status.ACCEPTED.getStatusCode());
            //  give some time to execute
            Thread.sleep(6000);

            //status of state should be sidelined
            String smId = smCreationResponse.getBody();
            State state4 = stateMachinesDAO.findById(smId).getStates().stream().filter(e -> e.getName().equals("test_state4")).findFirst().orElse(null);
            assertThat(state4).isNotNull();
            assertThat(state4.getStatus()).isEqualTo(Status.sidelined);

            TestWorkflow.shouldFail = false;

            // unsideline
            final HttpResponse<String> unsidelineResponse = Unirest.put(STATE_MACHINE_RESOURCE_URL + "/" + smId + "/" + state4.getId() + "/unsideline")
                    .asString();
            assertThat(unsidelineResponse.getStatus()).isEqualTo(Response.Status.ACCEPTED.getStatusCode());
            Thread.sleep(2000);

            state4 = stateMachinesDAO.findById(smId).getStates().stream().filter(e -> e.getName().equals("test_state4")).findFirst().orElse(null);
            assertThat(state4).isNotNull();
            assertThat(state4.getStatus()).isEqualTo(Status.completed);
        } finally {
            TestWorkflow.shouldFail = false;
        }
    }

    @Test
    public void testCreateStateMachine_shouldBombDueToDuplicateCorrelationId() throws Exception {
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("state_machine_definition.json"));
        final HttpResponse<String> response = Unirest.post(STATE_MACHINE_RESOURCE_URL)
                .header("Content-Type", "application/json").body(stateMachineDefinitionJson).asString();
        assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
        assertThat(parallelScatterGatherQueryHelper.findStateMachinesByName("test_state_machine")).hasSize(1);
        final HttpResponse<String> secondResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL).header("Content-Type", "application/json")
                .body(stateMachineDefinitionJson).asString();
        assertThat(secondResponse.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
    }

    @Test
    public void testCreateStateMachine_shouldReturn5xxForNonDuplicateIdConstraintViolation() throws Exception {
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("state_machine_definition_broken.json"));
        final HttpResponse<String> response = Unirest.post(STATE_MACHINE_RESOURCE_URL)
                .header("Content-Type", "application/json").body(stateMachineDefinitionJson).asString();
        assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    @Test
    public void testPostEvent() throws Exception {
        //  doReturn(202).when(spyTaskDispatcher).forwardExecutionMessage(anyString(), anyObject());
        //when(spyTaskDispatcher.forwardExecutionMessage(anyString(), anyObject())).thenReturn(202);
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("state_machine_definition.json"));
        final HttpResponse<String> smCreationResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL).header("Content-Type", "application/json")
                .body(stateMachineDefinitionJson).asString();
        Event event = eventsDAO.findBySMIdExecutionVersionAndName(smCreationResponse.getBody(), "event0",
                0L);
        assertThat(event.getStatus()).isEqualTo(Event.EventStatus.pending);
        String eventJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("event_data.json"));
        final HttpResponse<String> eventPostResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL + SLASH + smCreationResponse.getBody() + "/context/events")
                .header("Content-Type", "application/json").body(eventJson).asString();
        assertThat(eventPostResponse.getStatus()).isEqualTo(Response.Status.ACCEPTED.getStatusCode());
        // give some time to execute
        Thread.sleep(2000);
        event = eventsDAO.findBySMIdExecutionVersionAndName(smCreationResponse.getBody(), "event0",
                0L);
        assertThat(event.getStatus()).isEqualTo(Event.EventStatus.triggered);
        assertThat(event).isEqualToIgnoringGivenFields(TestUtils.getStandardTestEvent(), "stateMachineInstanceId", "createdAt", "updatedAt");

        // event3 was waiting on event1, so event3 should also be triggered
        event = eventsDAO.findBySMIdExecutionVersionAndName(smCreationResponse.getBody(), "event3",
                0L);
        assertThat(event.getStatus()).isEqualTo(Event.EventStatus.triggered);
        Thread.sleep(2000);
        stateMachinesDAO.findById(smCreationResponse.getBody()).getStates().forEach(s -> {
            System.out.println(s.toString());
        });
        boolean anyNotCompleted = stateMachinesDAO.findById(smCreationResponse.getBody()).getStates().stream().anyMatch(e -> !e.getStatus().equals(Status.completed));
        assertThat(anyNotCompleted).isFalse();
    }

    @Test
    public void testPostEvent_withCorrelationId() throws Exception {
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("state_machine_definition.json"));
        final HttpResponse<String> smCreationResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL)
                .header("Content-Type", "application/json").body(stateMachineDefinitionJson).asString();
        Event event = eventsDAO.findBySMIdExecutionVersionAndName(smCreationResponse.getBody(), "event0",
                0L);
        assertThat(event.getStatus()).isEqualTo(Event.EventStatus.pending);

        String eventJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("event_data.json"));
        final HttpResponse<String> eventPostResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL + SLASH + "magic_number_1" + "/context/events?searchField=correlationId")
                .header("Content-Type", "application/json").body(eventJson).asString();
        assertThat(eventPostResponse.getStatus()).isEqualTo(Response.Status.ACCEPTED.getStatusCode());
        // give some time to execute
        Thread.sleep(3000);
        event = eventsDAO.findBySMIdExecutionVersionAndName(smCreationResponse.getBody(), "event0",
                0L);
        assertThat(event.getStatus()).isEqualTo(Event.EventStatus.triggered);
        assertThat(event).isEqualToIgnoringGivenFields(TestUtils.getStandardTestEvent(), "stateMachineInstanceId", "createdAt", "updatedAt");

        // event3 was waiting on event1, so event3 should also be triggered
        event = eventsDAO.findBySMIdExecutionVersionAndName(smCreationResponse.getBody(), "event3",
                0L);
        assertThat(event.getStatus()).isEqualTo(Event.EventStatus.triggered);
        Thread.sleep(3000);
        boolean anyNotCompleted = stateMachinesDAO.findById(smCreationResponse.getBody()).getStates().stream().anyMatch(e -> !e.getStatus().equals(Status.completed));
        assertThat(anyNotCompleted).isFalse();
    }

    @Test
    public void testPostEvent_againstNonExistingCorrelationId() throws Exception {
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("state_machine_definition.json"));
        final HttpResponse<String> smCreationResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL)
                .header("Content-Type", "application/json").body(stateMachineDefinitionJson).asString();
        Event event = eventsDAO.findBySMIdExecutionVersionAndName(smCreationResponse.getBody(), "event0",
                0L);
        assertThat(event.getStatus()).isEqualTo(Event.EventStatus.pending);

        String eventJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("event_data.json"));
        // state machine with correlationId magic_number_2 does not exist. The following call should bomb
        final HttpResponse<String> eventPostResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL + SLASH + "magic_number_2" + "/context/events?searchField=correlationId")
                .header("Content-Type", "application/json").body(eventJson).asString();
        assertThat(eventPostResponse.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testPostScheduledEvent_withoutCorrelationIdTag() throws Exception {
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("state_machine_definition.json"));
        Unirest.post(STATE_MACHINE_RESOURCE_URL).header("Content-Type", "application/json")
                .body(stateMachineDefinitionJson).asString();

        String eventJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("event_data.json"));

        //request with searchField param missing
        final HttpResponse<String> eventPostResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL + SLASH + "magic_number_1" + "/context/events?triggerTime=123")
                .header("Content-Type", "application/json").body(eventJson).asString();
        assertThat(eventPostResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());


        //request with searchField param having value other than correlationId
        final HttpResponse<String> eventPostResponseWithWrongTag = Unirest.post(STATE_MACHINE_RESOURCE_URL + SLASH + "magic_number_1" + "/context/events?searchField=dummy&triggerTime=123")
                .header("Content-Type", "application/json").body(eventJson).asString();
        assertThat(eventPostResponseWithWrongTag.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public void testPostScheduledEvent_withCorrelationId() throws Exception {
        //create state machine
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("state_machine_definition.json"));
        final HttpResponse<String> smCreationResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL)
                .header("Content-Type", "application/json").body(stateMachineDefinitionJson).asString();
        assertThat(smCreationResponse.getStatus()).isEqualTo(org.eclipse.jetty.http.HttpStatus.CREATED_201);
        Thread.sleep(100);

        //post an scheduled event
        long triggerTime = (System.currentTimeMillis() / 1000) + 1;
        String eventJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("event_data.json"));
        Unirest.post(STATE_MACHINE_RESOURCE_URL + SLASH + "magic_number_1" + "/context/events?searchField=correlationId&triggerTime=" + triggerTime)
                .header("Content-Type", "application/json").body(eventJson).asString();

        //verify event has been saved in DB
        assertThat(eventSchedulerDao.retrieveOldest(1).get(0)).isEqualTo(new ScheduledEvent(
                "magic_number_1", "event0", triggerTime,
                "{\"name\":\"event0\",\"type\":\"java.lang.String\",\"data\":\"42\",\"eventSource\":null," +
                        "\"executionVersion\":0,\"cancelled\":null}"));

        //waiting for 7 seconds here, to match Event scheduler thread's initial delay of 10 sec (some boot up time + 7 seconds will surpass 10 sec)
        Thread.sleep(9000);

        //verify that the event has been triggered and scheduled event has been removed from DB
        assertThat(eventsDAO.findBySMIdExecutionVersionAndName(smCreationResponse.getBody(), "event0",
                0L).getStatus()).isEqualTo(Event.EventStatus.triggered);
        assertThat(eventSchedulerDao.retrieveOldest(1)).hasSize(0);
    }

    @SuppressWarnings("unused")
	@Test
    public void testPostScheduledEvent_withTriggerTimeInMilliSeconds() throws Exception {
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("state_machine_definition.json"));
        Unirest.post(STATE_MACHINE_RESOURCE_URL)
                .header("Content-Type", "application/json").body(stateMachineDefinitionJson).asString();

        Thread.sleep(100);
        long triggerTime = System.currentTimeMillis();
        String eventJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("event_data.json"));
        final HttpResponse<String> eventPostResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL + SLASH + "magic_number_1" + "/context/events?searchField=correlationId&triggerTime=" + triggerTime)
                .header("Content-Type", "application/json").body(eventJson).asString();

        assertThat(eventSchedulerDao.retrieveOldest(1).get(0).getScheduledTime()).isEqualTo(triggerTime / 1000);

    }

    @Test
    public void testPostEventUpdate_withNoEventData() throws Exception {
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("state_machine_definition.json"));
        Unirest.post(STATE_MACHINE_RESOURCE_URL)
                 .header("Content-Type", "application/json").body(stateMachineDefinitionJson).asString();
        Thread.sleep(100);
        String eventJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("no_event_data.json"));
        final HttpResponse<String> eventPostResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL + SLASH + "magic_number_1" + "/context/eventupdate")
                 .header("Content-Type", "application/json").body(eventJson).asString();
        assertThat(eventPostResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public void testEventUpdate() throws Exception {
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream(
                "state_machine_definition.json"));
        final HttpResponse<String> smCreationResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL)
                 .header("Content-Type", "application/json").body(stateMachineDefinitionJson).asString();
        Event event = eventsDAO.findBySMIdExecutionVersionAndName(smCreationResponse.getBody(), "event0",
                0L);
        assertThat(event.getStatus()).isEqualTo(Event.EventStatus.pending);

        /* Make the task fail, eventually sidelined. */
        TestWorkflow.shouldFail = true;
        try {
            /* Since event0 is in pending state, updateEvent should fail. */
            String eventJson0 = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream(
                    "updated_event_data.json"));
            final HttpResponse<String> eventPostResponse0 = Unirest.post(
                    STATE_MACHINE_RESOURCE_URL + SLASH + smCreationResponse.getBody() + "/context/eventupdate")
                    .header("Content-Type", "application/json").body(eventJson0).asString();
            assertThat(eventPostResponse0.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());

            String eventJson1 = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("event_data.json"));
            final HttpResponse<String> eventPostResponse1 = Unirest.post(
                    STATE_MACHINE_RESOURCE_URL + SLASH + smCreationResponse.getBody() + "/context/events")
                    .header("Content-Type", "application/json").body(eventJson1).asString();
            assertThat(eventPostResponse1.getStatus()).isEqualTo(Response.Status.ACCEPTED.getStatusCode());
            /* Give some time to task to execute, task thread sleeps for 1000 ms with 1 retry. */
            Thread.sleep(4000);

            //status of state should be sidelined and should be able to update event data now
            String smId = smCreationResponse.getBody();
            State state4 = stateMachinesDAO.findById(smId).getStates().stream().filter(e -> e.getName().equals("test_state4"))
                    .findFirst().orElse(null);
            assertThat(state4).isNotNull();
            assertThat(state4.getStatus()).isEqualTo(Status.sidelined);
            /* Make 'shouldFail' flag to false before update Event. Update Event will unsideline the dependent task
             * after event data update. */
            TestWorkflow.shouldFail = false;
            String eventJson2 = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream(
                    "updated_event_data.json"));
            final HttpResponse<String> eventPostResponse2 = Unirest.post(
                    STATE_MACHINE_RESOURCE_URL + SLASH + smCreationResponse.getBody() + "/context/eventupdate")
                    .header("Content-Type", "application/json").body(eventJson2).asString();
            assertThat(eventPostResponse2.getStatus()).isEqualTo(Response.Status.ACCEPTED.getStatusCode());
            /* Give some time to task to execute, task thread sleeps for 1000 ms */
            Thread.sleep(2000);

            /* Assert for task completed and updated event data.*/
            state4 = stateMachinesDAO.findById(smId).getStates().stream().filter(e -> e.getName().equals("test_state4"))
                    .findFirst().orElse(null);
            assertThat(state4).isNotNull();
            assertThat(state4.getStatus()).isEqualTo(Status.completed);
            Event event0 = eventsDAO.findBySMIdExecutionVersionAndName(smId, "event0",
                    0L);
            assertThat(event0.getEventData()).isEqualTo("50");
        } finally {
            TestWorkflow.shouldFail = false;
        }
    }

    @SuppressWarnings("unused")
	private void testEventUpdate_IneligibleTaskStatus_Util(HttpResponse<String> smCreationResponse) throws Exception {
        String eventJson1 = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("event_data.json"));
        String eventJson2 = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream(
                "updated_event_data.json"));

        final HttpResponse<String> eventPostResponse1 = Unirest.post(
                STATE_MACHINE_RESOURCE_URL + SLASH + smCreationResponse.getBody() + "/context/events")
                .header("Content-Type", "application/json").body(eventJson1).asString();
        final HttpResponse<String> eventPostResponse2 = Unirest.post(
                STATE_MACHINE_RESOURCE_URL + SLASH + smCreationResponse.getBody() + "/context/eventupdate")
                .header("Content-Type", "application/json").body(eventJson2).asString();
        assertThat(eventPostResponse2.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
    }

    @Test
    public void testEventUpdate_taskRunning() throws Exception {
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream(
                "state_machine_definition.json"));
        final HttpResponse<String> smCreationResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL)
                .header("Content-Type", "application/json").body(stateMachineDefinitionJson).asString();
        Event event = eventsDAO.findBySMIdExecutionVersionAndName(smCreationResponse.getBody(), "event0",
                0L);
        assertThat(event.getStatus()).isEqualTo(Event.EventStatus.pending);

        /* Make the task fail, eventually sidelined. */
        TestWorkflow.shouldFail = true;
        try {
            testEventUpdate_IneligibleTaskStatus_Util(smCreationResponse);
        } finally {
            TestWorkflow.shouldFail = false;
        }
    }

    @Test
    public void testEventUpdate_taskCompleted() throws Exception {
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream(
                "state_machine_definition.json"));
        final HttpResponse<String> smCreationResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL)
                .header("Content-Type", "application/json").body(stateMachineDefinitionJson).asString();
        Event event = eventsDAO.findBySMIdExecutionVersionAndName(smCreationResponse.getBody(), "event0",
                0L);
        assertThat(event.getStatus()).isEqualTo(Event.EventStatus.pending);

        /* Make the task complete to test Event update failure after task completion. */
        TestWorkflow.shouldFail = false;
        try {
            testEventUpdate_IneligibleTaskStatus_Util(smCreationResponse);
        } finally {
            TestWorkflow.shouldFail = false;
        }
    }

    @Test
    public void testEventUpdate_taskCancelled() throws Exception {
        String stateMachineDefinitionJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream(
                "state_machine_definition.json"));
        final HttpResponse<String> smCreationResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL)
                .header("Content-Type", "application/json").body(stateMachineDefinitionJson).asString();
        Event event = eventsDAO.findBySMIdExecutionVersionAndName(smCreationResponse.getBody(), "event0",
                0L);
        assertThat(event.getStatus()).isEqualTo(Event.EventStatus.pending);

        /* Mark the task path as cancelled. */
        TestWorkflow.shouldCancel = true;
        try {
            testEventUpdate_IneligibleTaskStatus_Util(smCreationResponse);
        } finally {
            TestWorkflow.shouldCancel = false;
        }
    }

    @Test
    public void testFsmGraphCreation() throws Exception {
        final StateMachine stateMachine = stateMachinePersistenceService.createStateMachine(standardStateMachine, objectMapper.readValue(this.getClass().getClassLoader().getResource("state_machine_definition_fork_join.json"), StateMachineDefinition.class));
        final HttpResponse<String> stringHttpResponse = Unirest.get(STATE_MACHINE_RESOURCE_URL + "/" + stateMachine.getId() + "/fsmdata")
                .header("Content-Type", "application/json").asString();
        assertThat(stringHttpResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        //TODO - we need a better assert here, but since we're using database IDs in the implementation, we cannot simply validate it with a static json
    }

    @Test
    public void testGetErroredStates() throws Exception {
        final StateMachine sm = stateMachinePersistenceService.createStateMachine(standardStateMachine, objectMapper.readValue(this.getClass().getClassLoader().getResource("state_machine_definition.json"), StateMachineDefinition.class));

        /* mark 1 of the state as errored */
        sm.getStates().stream().findFirst().get().setStatus(Status.errored);
        String firstSmId = "state-machine-1";

        sm.getStates().stream().forEach(state -> {
            state.setStateMachineId(firstSmId);
        });
        Set<State> states = new HashSet<>();
        states.addAll(sm.getStates());
        /* persist */
        Timestamp now = new Timestamp(System.currentTimeMillis() - 10 * 1000);
        final StateMachine firstSM = stateMachinesDAO.create(firstSmId, new StateMachine(firstSmId, sm.getVersion(),
                sm.getName(), sm.getDescription(), states, sm.getClientElbId()));
        /* change name and persist as 2nd statemachine */
        final String differentSMName = "state-machine-2";
        sm.getStates().stream().forEach(state -> {
            state.setStateMachineId(differentSMName);
        });
        states.clear();
        states.addAll(sm.getStates());

        final StateMachine secondSM = stateMachinesDAO.create(differentSMName, new StateMachine(
                differentSMName, sm.getVersion(), sm.getName(), sm.getDescription(), states, sm.getClientElbId()));
        Timestamp future = new Timestamp(System.currentTimeMillis() + 3 * 1000);
        /* fetch errored states with name "differentStateMachine" */
        final HttpResponse<String> stringHttpResponse = Unirest.get(
                STATE_MACHINE_RESOURCE_URL + "/" + "test_state_machine" + "/states/errored?fromTime=" + now.toString().replace(' ', '+') + "&toTime=" + future.toString().replace(' ', '+'))
                .header("Content-Type", "application/json").asString();

        assertThat(stringHttpResponse.getStatus()).isEqualTo(200);
        assertThat(stringHttpResponse.getBody()).isEqualTo("[[\"" + firstSM.getId() + "\"," +
                firstSM.getStates().stream().filter(e -> Status.errored.equals(e.getStatus())).findFirst().get().getId() + "," +
                "\"errored\"]," + "[\"" + secondSM.getId() + "\"," +
                secondSM.getStates().stream().filter(e -> Status.errored.equals(e.getStatus())).findFirst().get().getId() + "," +
                "\"errored\"]" + "]");
    }

    @Test
    public void testCancelWorkflow() throws Exception {
        final StateMachine sm = stateMachinePersistenceService.createStateMachine("magic_number_1", objectMapper.readValue(this.getClass().getClassLoader().getResource("state_machine_definition.json"), StateMachineDefinition.class));
        final String stateMachineId = sm.getId();
        State state = sm.getStates().stream().findFirst().get();
        state.setStatus(Status.running);
        statesDAO.updateState(stateMachineId, state);
        Unirest.put(STATE_MACHINE_RESOURCE_URL + SLASH + stateMachineId + "/cancel")
                .asString();

        Thread.sleep(200);
        StateMachine cancelledSM = stateMachinesDAO.findById(stateMachineId);
        assertThat(cancelledSM.getStatus()).isEqualTo(StateMachineStatus.cancelled);

        int cancelledStateCount = 0;
        for (State st : cancelledSM.getStates()) {
            if (st.getStatus() == Status.cancelled)
                cancelledStateCount++;
        }

        // 3 were in initialized state and one in running state before cancel call, after call, all 3 initialized states should be marked as cancelled
        assertThat(cancelledStateCount).isEqualTo(3);
    }

    @Test
    public void testCancelWorkflow_withCorrelationId() throws Exception {
        final StateMachine sm = stateMachinePersistenceService.createStateMachine("magic_number_1", objectMapper.readValue(this.getClass().getClassLoader().getResource("state_machine_definition.json"), StateMachineDefinition.class));
        String stateMachineId = sm.getId();
        Unirest.put(STATE_MACHINE_RESOURCE_URL + SLASH + stateMachineId + "/cancel?searchField=correlationId")
                .asString();

        Thread.sleep(200);
        StateMachine cancelledSM = stateMachinesDAO.findById(stateMachineId);
        assertThat(cancelledSM.getStatus()).isEqualTo(StateMachineStatus.cancelled);

        cancelledSM.getStates().forEach(st -> {
            assertThat(st.getStatus()).isEqualTo(Status.cancelled);
        });
    }

    @Test
    @Ignore("Feature no longer in use")
    public void shouldProxyEventToOldCluster() throws Exception {
        final String stateMachineId = "some_random_machine_do_not_exist";
        final String eventJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("event_data.json"));
        final HttpResponse<String> httpResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL + SLASH + stateMachineId + "/context/events")
                .header("Content-Type", "application/json").body(eventJson).asString();
        assertThat(httpResponse.getStatus()).isEqualTo(HttpStatus.SC_ACCEPTED);
    }

    @Test
    @Ignore("Feature no longer in use")
    public void shouldProxyScheduledEventToOldCluster() throws Exception {
        final String stateMachineId = "some_random_machine_do_not_exist";
        final String eventJson = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("event_data.json"));
        final HttpResponse<String> httpResponse = Unirest.post(STATE_MACHINE_RESOURCE_URL + SLASH + stateMachineId + "/context/events?triggerTime=0")
                .header("Content-Type", "application/json").body(eventJson).asString();
        assertThat(httpResponse.getStatus()).isEqualTo(HttpStatus.SC_ACCEPTED);
    }


}