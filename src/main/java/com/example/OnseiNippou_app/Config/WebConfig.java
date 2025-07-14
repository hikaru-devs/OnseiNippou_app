package com.example.OnseiNippou_app.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowCredentials(true);
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
      registry.addViewController("/{path:^(?!assets|static|.*\\..*$).*$}")
              .setViewName("forward:/index.html");
      registry.addViewController("/{path:^(?!assets|static|.*\\..*$).*$}/**")
              .setViewName("forward:/index.html");
  }

}
