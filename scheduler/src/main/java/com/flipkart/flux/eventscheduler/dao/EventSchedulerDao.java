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

package com.flipkart.flux.eventscheduler.dao;

import com.flipkart.flux.eventscheduler.model.ScheduledEvent;
import com.flipkart.flux.persistence.*;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.criterion.Order;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.transaction.Transactional;
import java.util.List;

/**
 * <code>EventSchedulerDao</code> handles all Db interactions for {@link ScheduledEvent}(s)
 *
 * @author shyam.akirala
 */
@Singleton
public class EventSchedulerDao {

    private SessionFactoryContext sessionFactoryContext;

    @Inject
    public EventSchedulerDao(@Named("schedulerSessionFactoriesContext") SessionFactoryContext sessionFactoryContext) {
        this.sessionFactoryContext = sessionFactoryContext;
    }

    @Transactional
    @SelectDataSource(storage = Storage.SCHEDULER)
    public void save(ScheduledEvent scheduledEvent) {
        currentSession().saveOrUpdate(scheduledEvent);
    }

    @Transactional
    @SelectDataSource(storage = Storage.SCHEDULER)
    public void delete(String correlationId, String eventName) {
        final Query deleteQuery = currentSession().createQuery("delete ScheduledEvent s where s.correlationId=:correlationId " +
                "and s.eventName=:eventName");
        deleteQuery.setString("correlationId", correlationId);
        deleteQuery.setString("eventName", eventName);
        deleteQuery.executeUpdate();
    }

    /**
     * Retrieves rowCount number of rows from ScheduledEvents table ordered by scheduledTime ascending.
     *
     * @param rowCount
     */
    @SuppressWarnings("unchecked")
	@Transactional
    @SelectDataSource(storage = Storage.SCHEDULER)
    public List<ScheduledEvent> retrieveOldest(int rowCount) {
        return currentSession()
                .createCriteria(ScheduledEvent.class)
                .addOrder(Order.asc("scheduledTime"))
                .setMaxResults(rowCount)
                .list();
    }

    /**
     * Provides the session which is bound to current thread.
     *
     * @return Session
     */
    private Session currentSession() {
        return sessionFactoryContext.getThreadLocalSession();
    }
}
