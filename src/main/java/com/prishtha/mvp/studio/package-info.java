@org.springframework.modulith.ApplicationModule(
    displayName = "Author Studio Module",
    allowedDependencies = {
        "shared",
        "tenant::api-contract",
        "tenant::api-response-dto",
        "tenant::api-request-dto"
    })
package com.prishtha.mvp.studio;
