@org.springframework.modulith.ApplicationModule(
    displayName = "Catalog Management Module",
    allowedDependencies = {
        "shared",
        "tenant::api-contract",
        "tenant::api-response-dto",
        "tenant::api-request-dto",
        "studio::api-event"
    })
package com.prishtha.mvp.catalog;
