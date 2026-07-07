@org.springframework.modulith.ApplicationModule(
    displayName = "Social Engagement Module",
    allowedDependencies = {
        "shared",
        "identity::api-contract",
        "catalog::api-contract"
    })
package com.prishtha.mvp.social;
