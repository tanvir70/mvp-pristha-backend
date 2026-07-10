@org.springframework.modulith.ApplicationModule(
    displayName = "Catalog Management Module",
    allowedDependencies = {
        "shared",
        "identity::api-contract",
        "identity::api-response-dto",
        "tenant::api-contract",
        "tenant::api-response-dto",
        "tenant::api-request-dto",
        "studio::api-contract",
        "studio::api-response-dto",
        "studio::api-event"
    })
package com.prishtha.mvp.catalog;
