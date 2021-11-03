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

package com.flipkart.flux.representation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.flux.api.EventData;
import com.flipkart.flux.api.EventDefinition;
import com.flipkart.flux.api.StateDefinition;
import com.flipkart.flux.api.StateMachineDefinition;
import com.flipkart.flux.dao.iface.AuditDAO;
import com.flipkart.flux.dao.iface.StateMachinesDAO;
import com.flipkart.flux.domain.*;
import com.flipkart.flux.persistence.DataSourceType;
import com.flipkart.flux.persistence.SelectDataSource;
import com.flipkart.flux.persistence.Storage;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.transaction.Transactional;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <code>StateMachinePersistenceService</code> class converts user provided state machine entity definition to domain type object and stores in DB.
 *
 * @author shyam.akirala
 */
@Singleton
public class StateMachinePersistenceService {

    private StateMachinesDAO stateMachinesDAO;
    private AuditDAO auditDAO;

    private EventPersistenceService eventPersistenceService;
    private final ObjectMapper objectMapper;

    private Integer maxRetryCount;

    @Inject
    public StateMachinePersistenceService(StateMachinesDAO stateMachinesDAO, AuditDAO auditDAO,
                                          EventPersistenceService eventPersistenceService, @Named("task.maxTaskRetryCount") Integer maxRetryCount) {
        this.stateMachinesDAO = stateMachinesDAO;
        this.auditDAO = auditDAO;
        this.eventPersistenceService = eventPersistenceService;
        this.maxRetryCount = maxRetryCount;
        objectMapper = new ObjectMapper();
    }

    /**
     * Converts state machine definition to state machine domain object and saves in DB.
     *
     * @param stateMachineDefinition
     * @return saved state machine object
     */
    @Transactional
    @SelectDataSource(type = DataSourceType.READ_WRITE, storage = Storage.SHARDED)
    public StateMachine createStateMachine(String stateMachineId, StateMachineDefinition stateMachineDefinition) {

        final Map<EventDefinition, EventData> eventDataMap = stateMachineDefinition.getEventDataMap();
        Set<Event> allEvents = createAllEvents(eventDataMap);
        Set<StateDefinition> stateDefinitions = stateMachineDefinition.getStates();
        Set<State> states = new HashSet<>();

        AtomicInteger stateId = new AtomicInteger(1);
        for (StateDefinition stateDefinition : stateDefinitions) {
            State state = convertStateDefinitionToState(stateDefinition, stateMachineId, stateId.longValue());
            states.add(state);
            stateId.incrementAndGet();
        }

        StateMachine stateMachine = new StateMachine(stateMachineId, stateMachineDefinition.getVersion(),
                stateMachineDefinition.getName(),
                stateMachineDefinition.getDescription(),
                states, stateMachineDefinition.getClientElbId());

        stateMachinesDAO.create(stateMachineId, stateMachine);
        for (Event event : allEvents) {
            event.setStateMachineInstanceId(stateMachine.getId());
            eventPersistenceService.persistEvent(event);
        }

        //create audit records for all the states
        for (State state : stateMachine.getStates()) {
            auditDAO.create(stateMachine.getId(), new AuditRecord(stateMachine.getId(), state.getId(), 0L, Status.initialized, null, null));
        }
        return stateMachine;
    }

    /**
     * creates event domain objects from event definitions.
     *
     * @param eventDataMap
     * @return set of events
     */
    private Set<Event> createAllEvents(Map<EventDefinition, EventData> eventDataMap) {
        Set<Event> allEvents = new HashSet<>();
        for (Map.Entry<EventDefinition, EventData> currentEventDefinitionAndData : eventDataMap.entrySet()) {
            final Event currentEvent = eventPersistenceService.convertEventDefinitionToEvent(currentEventDefinitionAndData.getKey());
            if (currentEventDefinitionAndData.getValue() != null) {
                currentEvent.setEventData(currentEventDefinitionAndData.getValue().getData());
                currentEvent.setEventSource(currentEventDefinitionAndData.getValue().getEventSource());
                currentEvent.setStatus(Event.EventStatus.triggered);
            }
            allEvents.add(currentEvent);
        }
        return allEvents;
    }

    /**
     * Converts state definition to state domain object.
     *
     * @param stateDefinition
     * @return state
     */
    private State convertStateDefinitionToState(StateDefinition stateDefinition, String stateMachineId, Long id) {
        try {
            List<EventDefinition> eventDefinitions = stateDefinition.getDependencies();
            List<String> events = new LinkedList<>();
            if (eventDefinitions != null) {
                for (EventDefinition e : eventDefinitions) {
                    events.add(e.getName());
                }
            }
            return new State(stateDefinition.getVersion(),
                    stateDefinition.getName(),
                    stateDefinition.getDescription(),
                    stateDefinition.getOnEntryHook(),
                    stateDefinition.getTask(),
                    stateDefinition.getOnExitHook(),
                    events,
                    Math.min(stateDefinition.getRetryCount(), maxRetryCount),
                    stateDefinition.getTimeout(),
                    stateDefinition.getOutputEvent() != null ? objectMapper.writeValueAsString(
                            stateDefinition.getOutputEvent()) : null,
                    Status.initialized, null, 0L, stateMachineId, id);
        } catch (Exception e) {
            throw new IllegalRepresentationException("Unable to create state domain object", e);
        }
    }
}
