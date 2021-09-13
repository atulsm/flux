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
package com.flipkart.flux.guice.module;

import com.flipkart.flux.annotation.ManagedEnv;
import com.flipkart.flux.client.registry.ExecutableRegistry;
import com.flipkart.flux.impl.task.AkkaTask;
import com.flipkart.flux.impl.task.registry.EagerInitRouterRegistryImpl;
import com.flipkart.flux.impl.task.registry.RouterRegistry;
import com.flipkart.flux.registry.TaskExecutableRegistryImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * <code>ExecutionTaskModule</code> is a Guice {@link AbstractModule} implementation used for wiring task execution classes
 * @author regu.b
 *
 */
public class ExecutionTaskModule extends AbstractModule {

    public ExecutionTaskModule() {
    }

    @Override
    protected void configure() {
        bind(RouterRegistry.class).to(EagerInitRouterRegistryImpl.class).in(Singleton.class);
        bind(ExecutableRegistry.class).annotatedWith(ManagedEnv.class).to(TaskExecutableRegistryImpl.class);
        requestStaticInjection(AkkaTask.class);
    }
}
