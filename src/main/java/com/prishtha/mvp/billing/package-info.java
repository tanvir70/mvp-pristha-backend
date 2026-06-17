@org.springframework.modulith.ApplicationModule(
    displayName = "Billing & Payments Module",
    allowedDependencies = {
        "shared",
        "identity::api-contract",
        "identity::api-response-dto"
    })
package com.prishtha.mvp.billing;
