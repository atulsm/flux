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

package com.flipkart.flux.persistence;

import com.flipkart.flux.shard.ShardId;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Context to get a particular {@link SessionFactory} for the ongoing transaction.
 *
 * @see com.flipkart.flux.guice.interceptor.TransactionInterceptor
 * <p>
 * Created by gaurav.ashok on 23/11/16.
 */
public interface SessionFactoryContext {

    /**
     * Get the Scheduler sessionFactory for the current thread context to be used for the ongoing transaction.
     *
     * @return {@link SessionFactory}
     */

    SessionFactory getSchedulerSessionFactory();

    /**
     * Get the sessionFactory for the current thread context to be used for the ongoing transaction.
     *
     * @return {@link SessionFactory}
     */

    Session getThreadLocalSession();

    /**
     * Set the sessionFactory for the current thread context to be used for the next transaction
     *
     * @param session
     */
    void setThreadLocalSession(Session session);

    /**
     * Get Session Factory for the given shardId from RWSessionFactoryMap
     *
     * @param shardKey
     * @return
     */
    SessionFactory getRWSessionFactory(String shardKey);

    /**
     * Get Session Factory for the given  shardId from ROSessionFactoryMap
     *
     * @return
     */
    SessionFactory getROSessionFactory(ShardId shardId);

    /**
     * Clear the context.
     */
    void clear();
}
