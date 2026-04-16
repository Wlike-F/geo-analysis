package com.xy.welllog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final String[] STATIC_LOCATIONS = {
            "file:front-end/dist_v3/",
            "classpath:/static/"
    };

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_LOCATIONS)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }

                        if (isSpaRoute(resourcePath)) {
                            Resource indexResource = location.createRelative("index.html");
                            if (indexResource.exists() && indexResource.isReadable()) {
                                return indexResource;
                            }
                        }
                        return null;
                    }
                });
    }

    private boolean isSpaRoute(String resourcePath) {
        return !resourcePath.startsWith("api/")
                && !resourcePath.startsWith("assets/")
                && !resourcePath.contains(".");
    }
}
