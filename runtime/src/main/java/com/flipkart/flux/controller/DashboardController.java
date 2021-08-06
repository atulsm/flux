/*
 * Copyright 2012-2016, the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.flipkart.flux.controller;

import static com.flipkart.flux.constant.RuntimeConstants.DASHBOARD_VIEW;
import static com.flipkart.flux.constant.RuntimeConstants.RESOURCE_NOT_AVAILABLE_VIEW;

import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.flipkart.flux.Constants;
import com.flipkart.flux.FluxRuntimeRole;
import com.flipkart.flux.initializer.FluxInitializer;

/**
 * <code>DashboardController</code> is a Spring MVC Controller for the Dashboard
 * 
 * @author regunath.balasubramanian
 * @author kartik.bommepally
 */
@Controller
public class DashboardController {
	
    /**
     * Dashboard page
     */
    @RequestMapping(value = {"/dashboard"}, method = RequestMethod.GET)
    public String dashboard(ModelMap model, HttpServletRequest request) {
		model.addAttribute(Constants.MODE, FluxInitializer.fluxRole);
    		if (FluxInitializer.fluxRole == FluxRuntimeRole.ORCHESTRATION) {
    			model.addAttribute("resource_not_available_message", "'/dashboard' not available in Orchestration mode of Flux. Try '/fsmview'");
    			return RESOURCE_NOT_AVAILABLE_VIEW;
    		}
        return DASHBOARD_VIEW;
    }        
    
}
