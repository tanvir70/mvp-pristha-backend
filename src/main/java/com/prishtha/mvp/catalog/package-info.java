@org.springframework.modulith.ApplicationModule(
    displayName = "Catalog Management Module",
    allowedDependencies = {
        "shared",
        "identity::api-contract",
        "tenant::api-contract",
        "tenant::api-response-dto",
        "tenant::api-request-dto",
        "studio::api-event"
    })
package com.prishtha.mvp.catalog;
