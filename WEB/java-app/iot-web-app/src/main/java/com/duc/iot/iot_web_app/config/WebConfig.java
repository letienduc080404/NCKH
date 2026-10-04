package com.duc.iot.iot_web_app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get("uploads/firmware");
        String firmwarePath = uploadPath.toFile().getAbsolutePath();

        // Cấu hình để truy cập file qua URL /firmware/**
        registry.addResourceHandler("/firmware/**")
                .addResourceLocations("file:/" + firmwarePath + "/");
    }
}
