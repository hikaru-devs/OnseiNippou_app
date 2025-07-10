package com.example.OnseiNippou_app.Config;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration 
public class ReactRoutingConfig  implements WebMvcConfigurer{
	
	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		// 今後のページ追加も考慮した汎用的ルール（一階層のみ）
		registry.addViewController("/{spring:[a-zA-Z0-9-_]+}")
        .setViewName("forward:/index.html");

	}

}
