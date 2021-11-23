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

import java.io.Serializable;

/**
 * DTO class for holding Task execution updates 
 * @author regunath.balasubramanian
 */
public class ExecutionUpdateData implements Serializable {
	
	/** Default serial version UID*/
	private static final long serialVersionUID = 1L;
	
	/** Member variables*/
	private String stateMachineId;
	private String stateMachineName;
	private String taskName;
	private Long taskId;
	private Status status;
	private long retrycount = 0;
    private long currentRetryCount = 0;
    private String errorMessage;
	private boolean deleteFromRedriver;
	private String dependentAuditEvents;
	private Long taskExecutionVersion;
	
	/** Constructors*/
	/* For use by Jackson for deserialization*/
	public ExecutionUpdateData() {
	}
    public ExecutionUpdateData(String stateMachineId, String stateMachineName, String taskName, Long taskId,
							   Status status, long retrycount, long currentRetryCount, String errorMessage,
							   boolean deleteFromRedriver, String dependentAuditEvents) {
		this(stateMachineId, stateMachineName, taskName, taskId, status, retrycount, currentRetryCount, errorMessage,
				deleteFromRedriver, dependentAuditEvents, 0L);
    }

	public ExecutionUpdateData(String stateMachineId, String stateMachineName, String taskName, Long taskId,
							   Status status, long retrycount, long currentRetryCount, String errorMessage,
							   boolean deleteFromRedriver, String dependentAuditEvents, Long taskExecutionVersion) {
		this.stateMachineId = stateMachineId;
		this.stateMachineName = stateMachineName;
		this.taskName = taskName;
		this.taskId = taskId;
		this.status = status;
		this.retrycount = retrycount;
		this.currentRetryCount = currentRetryCount;
		this.errorMessage = errorMessage;
		this.deleteFromRedriver = deleteFromRedriver;
		this.dependentAuditEvents = dependentAuditEvents;
		this.taskExecutionVersion = taskExecutionVersion;
	}

    /** Accessors*/
	public String getStateMachineId() {
		return stateMachineId;
	}
	public String getStateMachineName() {
		return stateMachineName; }
	public String getTaskName(){
		return taskName;}
	public Long getTaskId() {
		return taskId;
	}
	public Status getStatus() {
		return status;
	}
	public long getRetrycount() {
		return retrycount;
	}
    public long getCurrentRetryCount() {
		return currentRetryCount;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
	public boolean isDeleteFromRedriver() {
		return deleteFromRedriver;
	}
	public Long getTaskExecutionVersion() {
		return taskExecutionVersion;
	}

	public String getDependentAuditEvents() {
		return dependentAuditEvents;
	}

	public void setDependentAuditEvents(String dependentAuditEvents) {
		this.dependentAuditEvents = dependentAuditEvents;
	}
}