package com.example.onseinippou.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.onseinippou.common.interceptor.LoggingInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	
	@Autowired
	private LoggingInterceptor loggingInterceptor;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOrigins("http://localhost:5173", "https://onsei-nippou-app-207055258179.asia-northeast1.run.app")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowCredentials(true);
  }
  
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
	  registry.addInterceptor(loggingInterceptor)
	  		.addPathPatterns("/onsei-nippou-page/**"); // 監視対象
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
      registry.addViewController("/{path:^(?!assets|static|.*\\..*$).*$}")
              .setViewName("forward:/index.html");
      registry.addViewController("/{path:^(?!assets|static|.*\\..*$).*$}/**")
              .setViewName("forward:/index.html");
  }
  
}
