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

package com.flipkart.flux.examples.externalevents;

import com.flipkart.flux.client.model.ExternalEvent;
import com.flipkart.flux.client.model.Task;

import javax.inject.Singleton;

@Singleton
public class ManualSellerVerificationService {
    @Task(version = 1, timeout = 1000l)
    public SellerVerificationStatus waitForVerification(@ExternalEvent("sellerVerification") SellerVerificationStatus verificationStatus) {
        System.out.println("[ManualSellerVerificationService] Received verification status " + verificationStatus.isVerifiedSeller() + " for seller " + verificationStatus.getSellerId());
        return verificationStatus;
    }
}
