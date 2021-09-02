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

package com.flipkart.flux.metrics.iface;

import com.codahale.metrics.Timer;


/**
 * <code>MetricsClient</code> provides metrics related functionality, like rate meters or counters.
 *
 * @author kaushal.hooda
 */
public interface MetricsClient {

	/**
     * Mark the occurence of an event, which is used to measure its rate.
     * @param key Specifies the type of event.
     */
    public void markMeter(String key);

    /**
     * Increment a counter.
     * @param key The name of the counter.
     */
    public void incCounter(String key);

    /**
     * Decrement a counter.
     * @param key The name of the counter.
     */
    public void decCounter(String key);

    /**
     * Provides a timer with name, creates if absent
     */
    public Timer getTimer(String key);
    
}
