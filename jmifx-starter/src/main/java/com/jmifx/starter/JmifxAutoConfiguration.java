package com.jmifx.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the compiled WebFX/Wasm client from the classpath at
 * {@code ${jmifx.path}/**}. Assets are packaged by the jmifx Gradle plugin
 * into {@code jmifx-web/} — this starter only maps them; missing assets 404
 * and never prevent startup.
 */
@AutoConfiguration
@ConditionalOnWebApplication
@EnableConfigurationProperties(JmifxProperties.class)
@ConditionalOnProperty(name = "jmifx.enabled", havingValue = "true", matchIfMissing = true)
public class JmifxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "jmifxResourceConfigurer")
    public WebMvcConfigurer jmifxResourceConfigurer(JmifxProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler(properties.getPath() + "/**")
                        .addResourceLocations("classpath:/jmifx-web/")
                        // asset filenames (app.wasm) are stable across builds —
                        // force revalidation so clients never run a stale wasm
                        // after a redeploy (conditional 304s keep it cheap)
                        .setCacheControl(CacheControl.noCache());
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(name = "jmifxIndexForwardMapping")
    public org.springframework.web.servlet.handler.SimpleUrlHandlerMapping jmifxIndexForwardMapping(
            JmifxProperties properties) {
        return JmifxIndexMappings.indexForwardMapping(properties);
    }
}
