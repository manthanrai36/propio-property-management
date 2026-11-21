package com.propio.config;

import com.propio.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final FileStorageService fileStorageService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path rootUploadPath = fileStorageService.getRootUploadPath();
        String uploadLocation = "file:" + rootUploadPath.toString().replace('\\', '/') + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation);
    }
}
