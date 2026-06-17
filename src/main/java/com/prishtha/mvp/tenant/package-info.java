@org.springframework.modulith.ApplicationModule(
    displayName = "Tenant Management Module",
    allowedDependencies = {
        "shared",
        "identity::api-contract",
        "identity::api-response-dto",
        "identity::api-request-dto"
    })
package com.prishtha.mvp.tenant;
