package com.fse.fsenotification.util;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "africastalking")
public class AfricaTalkingProperties {
    private String baseUrl;
    private String username;
    private String apiKey;
}

