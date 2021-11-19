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

package com.flipkart.flux.api;

/**
 * DTO class for transferring task generated event and execution data
 * @author shyam.akirala
 */
public class EventAndExecutionData {

    private VersionedEventData versionedEventData;

    private ExecutionUpdateData executionUpdateData;

    /** Constructors*/
	/* For use by Jackson for deserialization*/
    public EventAndExecutionData() {}

    public EventAndExecutionData(VersionedEventData versionedEventData, ExecutionUpdateData executionUpdateData) {
        this.versionedEventData = versionedEventData;
        this.executionUpdateData = executionUpdateData;
    }

    /** Accessors*/
    public VersionedEventData getVersionedEventData() {
        return versionedEventData;
    }
    public ExecutionUpdateData getExecutionUpdateData() {
        return executionUpdateData;
    }
}
