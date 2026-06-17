@org.springframework.modulith.ApplicationModule(
    displayName = "Analytics & Scouting Module",
    allowedDependencies = {
        "shared",
        "catalog::api-contract",
        "catalog::api-response-dto",
        "identity::api-contract",
        "identity::api-response-dto"
    })
package com.prishtha.mvp.analytics;
