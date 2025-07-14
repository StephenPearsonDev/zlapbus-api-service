package dev.stephenpearson.ztm_tracker_api_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class AppConfig implements WebFluxConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
      .addMapping("/**")
      .allowedOriginPatterns("http://localhost:*", "http://192.168.*:*")
      .allowedMethods("GET")
      .allowedHeaders("*")
      .allowCredentials(true);
  }

  
}
