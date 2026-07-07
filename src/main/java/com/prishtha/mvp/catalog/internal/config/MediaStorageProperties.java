package com.prishtha.mvp.catalog.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "catalog.media")
public class MediaStorageProperties {

    private String uploadDir = "uploads";
    private long maxFileSizeBytes = 5 * 1024 * 1024;
}
