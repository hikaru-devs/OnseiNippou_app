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
		        "/submit-sheet",
		        "/onsei-nippou-page",
		        "/register-sheet-page"
		    ).permitAll() // ← 🔑 APIは「許可」
		   /* .requestMatchers(
		        "/onsei-nippou-page", 
		        "/register-sheet-page"
		    ).authenticated()*/ // ← 🔑 UIだけ認証要求
		    .anyRequest().permitAll()
		  )
			.oauth2Login(oauth -> oauth
					.successHandler(successHandler) // ←GoogleOAthログイン後に、CustomOAuth2SuccessHandler を使って、リダイレクト制御している
			);
		return http.build();
	}

}
