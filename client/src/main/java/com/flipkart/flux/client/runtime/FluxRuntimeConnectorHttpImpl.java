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

package com.flipkart.flux.client.runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.ws.rs.core.Response;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.flux.api.EventAndExecutionData;
import com.flipkart.flux.api.EventData;
import com.flipkart.flux.api.ExecutionUpdateData;
import com.flipkart.flux.api.StateMachineDefinition;
import com.flipkart.flux.api.VersionedEventData;
import com.flipkart.flux.client.constant.ClientConstants;
import com.google.common.annotations.VisibleForTesting;

/**
 * RuntimeConnector that connects to runtime over HTTP
 * Internally, this uses Unirest as of now. This makes it difficult to write unit tests for this class,
 * but it does very little so its okay
 *
 * @author yogesh.nachnani
 */
public class FluxRuntimeConnectorHttpImpl implements FluxRuntimeConnector {


    private static final int MAX_TOTAL = 400;
    private static final int MAX_PER_ROUTE = 50;
    private static final String EXTERNAL = "external";
    private static Logger logger = LogManager.getLogger(FluxRuntimeConnectorHttpImpl.class);
    private final CloseableHttpClient closeableHttpClient;
    private final String fluxEndpoint;
    private final ObjectMapper objectMapper;
    private final MetricRegistry metricRegistry;
    private String authnTargetClientId;

    @VisibleForTesting
    public FluxRuntimeConnectorHttpImpl(Long connectionTimeout, Long socketTimeout, String fluxEndpoint,
                                        String targetClientId) {
        this(connectionTimeout, socketTimeout, fluxEndpoint, new ObjectMapper(), SharedMetricRegistries.getOrCreate("mainMetricRegistry")
        , targetClientId);
    }

    public FluxRuntimeConnectorHttpImpl(Long connectionTimeout, Long socketTimeout, String fluxEndpoint, ObjectMapper objectMapper,
                                        MetricRegistry metricRegistry, String targetClientId) {
        this.fluxEndpoint = fluxEndpoint;
        this.objectMapper = objectMapper;
        this.authnTargetClientId = targetClientId;
        RequestConfig clientConfig = RequestConfig.custom()
            .setConnectTimeout((connectionTimeout).intValue())
            .setSocketTimeout((socketTimeout).intValue())
            .setConnectionRequestTimeout((socketTimeout).intValue())
            .build();
        PoolingHttpClientConnectionManager syncConnectionManager = new PoolingHttpClientConnectionManager();
        syncConnectionManager.setMaxTotal(MAX_TOTAL);
        syncConnectionManager.setDefaultMaxPerRoute(MAX_PER_ROUTE);

        closeableHttpClient = HttpClientBuilder.create().setDefaultRequestConfig(clientConfig)
            .setConnectionManager(syncConnectionManager)
            .build();

        Runtime.getRuntime()
            .addShutdownHook(new Thread(() -> HttpClientUtils.closeQuietly(closeableHttpClient)));
        this.metricRegistry = metricRegistry;
    }

    /***
     * The method submits a new workflow after validating states
     * Two validations have been added
     * One validates that a state should have only one replay event
     * Other validates that no two states can have same replay event as its dependency
     * @param stateMachineDef
     */
    @Override
    public void submitNewWorkflow(StateMachineDefinition stateMachineDef) {
        CloseableHttpResponse httpResponse = null;
        try {
            httpResponse = postOverHttp(stateMachineDef, "");
            if (logger.isDebugEnabled()) {
                try {
                    logger.debug("Flux returned response: {}", EntityUtils.toString(httpResponse.getEntity()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            HttpClientUtils.closeQuietly(httpResponse);
        }
    }

    @Override
    public void submitEventAndUpdateStatus(VersionedEventData versionedEventData, String stateMachineId,
                                           ExecutionUpdateData executionUpdateData) {
        CloseableHttpResponse httpResponse = null;
        try {
            EventAndExecutionData eventAndExecutionData = new EventAndExecutionData(versionedEventData, executionUpdateData);
            httpResponse = postOverHttp(eventAndExecutionData, "/" + stateMachineId + "/context/eventandstatus");
        } finally {
            HttpClientUtils.closeQuietly(httpResponse);
        }
    }

    @Override
    public void submitEvent(String name, Object data, String correlationId, String eventSource) {
        final String eventType = data.getClass().getName();
        if (eventSource == null) {
            eventSource = EXTERNAL;
        }
        CloseableHttpResponse httpResponse = null;
        try {
            final EventData eventData = new EventData(name, eventType, objectMapper.writeValueAsString(data), eventSource);
            httpResponse = postOverHttp(eventData, "/" + correlationId + "/context/events?searchField=correlationId");
        } catch (JsonProcessingException e) {
            logger.error("Posting over http errored. Message: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            HttpClientUtils.closeQuietly(httpResponse);
        }
    }

    @Override
    public void submitReplayEvent(String name, Object data, String correlationId) {
        final String eventType = data.getClass().getName();
        String eventSource = ClientConstants.REPLAY_EVENT;
        CloseableHttpResponse httpResponse = null;
        try {
            final EventData eventData = new EventData(name, eventType, objectMapper.writeValueAsString(data), eventSource);
            httpResponse = postOverHttp(eventData, "/" + correlationId + "/context/replayevent?searchField=correlationId");
        } catch (JsonProcessingException e) {
            logger.error("Posting over http errored. Message: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            HttpClientUtils.closeQuietly(httpResponse);
        }
    }

    @Override
    public void submitScheduledEvent(String name, Object data, String correlationId, String eventSource, Long triggerTime) {
        final String eventType = data.getClass().getName();
        if (eventSource == null) {
            eventSource = EXTERNAL;
        }
        CloseableHttpResponse httpResponse = null;
        try {
            if (triggerTime != null) {
                final EventData eventData = new EventData(name, eventType, objectMapper.writeValueAsString(data), eventSource);
                httpResponse = postOverHttp(eventData, "/" + correlationId + "/context/events?searchField=correlationId&triggerTime=" + triggerTime);
            } else {
                //this block is used by flux to trigger the event when the time has arrived, send the data as plain string without serializing,
                // as the data is already in serialized form (in ScheduledEvents table the data stored in serialized form)
                final EventData eventData = new EventData(name, eventType, (String) data, eventSource);
                httpResponse = postOverHttp(eventData, "/" + correlationId + "/context/events?searchField=correlationId");
            }
        } catch (JsonProcessingException e) {
            logger.error("Posting over http errored. Message: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            HttpClientUtils.closeQuietly(httpResponse);
        }
    }

    @Override
    public void submitEventUpdate(String name, Object data, String correlationId, String eventSource) {
        final String eventType = data.getClass().getName();
        if (eventSource == null) {
            eventSource = EXTERNAL;
        }
        CloseableHttpResponse httpResponse = null;
        try {
            final EventData eventData = new EventData(name, eventType, objectMapper.writeValueAsString(data), eventSource);
            httpResponse = postOverHttp(eventData, "/" + correlationId + "/context/eventupdate");

        } catch (JsonProcessingException e) {
            logger.error("Posting over http errored. Message: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            HttpClientUtils.closeQuietly(httpResponse);
        }
    }

    @Override
    public void cancelEvent(String eventName, String correlationId) {
        CloseableHttpResponse httpResponse = null;
        try {
            final EventData eventData = new EventData(eventName, null, null, null, true);
            httpResponse = postOverHttp(eventData, "/" + correlationId + "/context/events?searchField=correlationId");
        } catch (Exception e) {
            logger.error("Posting over http errored. Message: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            HttpClientUtils.closeQuietly(httpResponse);
        }
    }

    /**
     * Interface method implementation. Updates the status in persistence store by invoking suitable Flux runtime API
     *
     * @see com.flipkart.flux.client.runtime.FluxRuntimeConnector#updateExecutionStatus(ExecutionUpdateData)
     */
    public void updateExecutionStatus(ExecutionUpdateData executionUpdateData) {
        CloseableHttpResponse httpResponse = null;
        httpResponse = postOverHttp(executionUpdateData, "/" + executionUpdateData.getStateMachineId() +
                "/" + executionUpdateData.getTaskId() + "/" + executionUpdateData.getTaskExecutionVersion() + "/status");
        HttpClientUtils.closeQuietly(httpResponse);
    }

    /**
     * Interface method implementation. Increments the execution retries in persistence by invoking suitable Flux runtime API
     *
     * @see com.flipkart.flux.client.runtime.FluxRuntimeConnector#incrementExecutionRetries(String, Long, Long)
     */
    @Override
    public void incrementExecutionRetries(String stateMachineId, Long taskId, Long taskExecutionVersion) {
        CloseableHttpResponse httpResponse = null;
        httpResponse = postOverHttp(null, "/" + stateMachineId + "/" + taskId +
                "/" + taskExecutionVersion + "/retries/inc");
        HttpClientUtils.closeQuietly(httpResponse);
    }

    /**
     * Interface method implementation. Posts to Flux Runtime API to redrive a task.
     */
    @Override
    public void redriveTask(String stateMachineId, Long taskId, Long executionVersion) {
        CloseableHttpResponse httpResponse = null;
        httpResponse = postOverHttp(null, "/redrivetask/" + stateMachineId + "/taskId/" + taskId + "/taskExecutionVersion/" + executionVersion);
        HttpClientUtils.closeQuietly(httpResponse);
    }

    /**
     * Helper method to post data over Http
     */
    protected CloseableHttpResponse postOverHttp(Object dataToPost, String pathSuffix) {
        CloseableHttpResponse httpResponse = null;
        HttpPost httpPostRequest;
        httpPostRequest = new HttpPost(fluxEndpoint + pathSuffix);
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            objectMapper.writeValue(byteArrayOutputStream, dataToPost);
            logger.info("Posting data: {} over http to Flux Endpoint : {} with authN targetClientId : {}",dataToPost, fluxEndpoint, authnTargetClientId);
            httpPostRequest.setEntity(new ByteArrayEntity(byteArrayOutputStream.toByteArray(), ContentType.APPLICATION_JSON));
            httpResponse = closeableHttpClient.execute(httpPostRequest);
            final int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode >= Response.Status.OK.getStatusCode() && statusCode < Response.Status.MOVED_PERMANENTLY.getStatusCode()) {
                logger.trace("Posting over http is successful. Status code: {}", statusCode);
                metricRegistry.meter(new StringBuilder().
                        append("stateMachines.forwardToOrchestrator.2xx").toString()).mark();
            } else {
                logger.error("Did not receive a valid response from Flux core. Status code: {}, message: {}", statusCode, EntityUtils.toString(httpResponse.getEntity()));
                /* 400 <= defaultStatusCode < 500 */
                if (statusCode >= Response.Status.BAD_REQUEST.getStatusCode()
                        && statusCode < Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
                    metricRegistry.meter(new StringBuilder().
                            append("stateMachines.forwardToOrchestrator.4xx").toString()).mark();
                }
                /* 500 <= defaultStatusCode <= 505 */
                else if (statusCode >= Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()
                        && statusCode < Response.Status.HTTP_VERSION_NOT_SUPPORTED.getStatusCode()) {
                    metricRegistry.meter(new StringBuilder().
                            append("stateMachines.forwardToOrchestrator.5xx").toString()).mark();
                }
                HttpClientUtils.closeQuietly(httpResponse);
                throw new RuntimeCommunicationException("Did not receive a valid response from Flux core");
            }
        } catch (IOException e) {
            logger.error("Posting over http errored. Message: {}", e.getMessage(), e);
            HttpClientUtils.closeQuietly(httpResponse);
            throw new RuntimeCommunicationException("Could not communicate with Flux runtime: " + fluxEndpoint);
        }
        return httpResponse;
    }

}