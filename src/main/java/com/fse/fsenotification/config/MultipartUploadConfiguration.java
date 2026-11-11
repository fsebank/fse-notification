package com.fse.fsenotification.config;

import com.fse.fsenotification.modules.message.dto.MessageDtos;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultipartUploadConfiguration {

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        return new MultipartConfigElement("", MessageDtos.MAX_EMAIL_ATTACHMENT_BYTES,
                12L * 1024 * 1024, 0);
    }
}
