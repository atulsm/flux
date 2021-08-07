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

package com.flipkart.flux.deploymentunit;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

/**
 * <code>ClassLoaderInjector</code> is used by Flux Runtime during Executable Registry population to provide injected objects of User classes.
 *
 * If user wants to use guice injection in his code, he has to create a guice module in his code which goes into production jar.
 * Ex:
 *  public class UserModule extends AbstractModule {
 *      @Override
 *      protected void configure() {
 *          install(new UserModule1());
 *          install(new UserModule2());
 *      }
 *  }
 *
 * And in the deployment unit config file (flux_config.yml) user has to put an entry "guiceModuleClass: user_guice_module_class_fqn"
 *
 * @author shyam.akirala
 */
public class ClassLoaderInjector {

    private static Injector injector;

    /** This constructor would be called if used doesn't provide any injector class in the user config file */
    public ClassLoaderInjector() {
        injector = Guice.createInjector();
    }

    /** Creates guice injector with specified module */
    public ClassLoaderInjector(Module module){
        injector = Guice.createInjector(module);
    }

    /** Given a class, returns its instance */
    public Object getInstance(Class clazz) {
        return injector.getInstance(clazz);
    }

    /** Returns injector instance which user can use for his purposes */
    public static Injector getInjector() {
        return injector;
    }
}
