package com.example.OnseiNippou_app.Security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
	
	@Autowired
	private CustomOAuth2SuccessHandler successHandler;
	
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
		.csrf(csrf -> csrf.disable()) // ← これを追加
		  .authorizeHttpRequests(auth -> auth
		    .requestMatchers(
		        "/upload-audio", 
		        "/submit-text", 
		        "/register-sheet"
		    ).permitAll() // ← 🔑 APIは「許可」
		    .requestMatchers(
		        "/OnseiNippou_app", 
		        "/sheet-register"
		    ).authenticated() // ← 🔑 UIだけ認証要求
		    .anyRequest().permitAll()
		  )
			.oauth2Login(oauth -> oauth
					.successHandler(successHandler)
			);
		return http.build();
	}

}
