package com.example.onseinippou.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.session.InvalidSessionStrategy;

import com.example.onseinippou.security.CustomAuthenticationEntryPoint;
import com.example.onseinippou.security.CustomOAuth2UserService;
import com.example.onseinippou.security.CustomUserDetailsService;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomUserDetailsService customUserDetailsService;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final InvalidSessionStrategy customInvalidSessionStrategy;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
        // 認可ルール
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/static/**", "/vite.svg", "/favicon.ico").permitAll()
                .requestMatchers("/api/**").permitAll()
                .requestMatchers("/onsei-nippou-page", "/register-sheet-page").authenticated()
                .anyRequest().permitAll()
            )
            // 例外ハンドリング（未認証アクセス時）
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(customAuthenticationEntryPoint) 
            )
            // フォーム & OAuth2 ログイン
            .formLogin(form -> form
                .loginPage("/login")
                .permitAll()
            )
            .oauth2Login(oauth -> oauth
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
            )
            // セッション管理（ラムダ DSL）
            .sessionManagement(sess -> sess
            		// 無効セッション（タイムアウト or 不正JSESSIONID）時の挙動
            		.invalidSessionStrategy(customInvalidSessionStrategy)
            		// 1ユーザー1セッションのみ許可
            		.maximumSessions(1)
            		.maxSessionsPreventsLogin(false)	// = 後勝ち
            		.expiredUrl("/login?expired=true")	// 期限切れ遷移先
            )
            // CSRF は一旦 OFF（必要に応じて ON に）
            .csrf(csrf -> csrf.disable())
            // UserDetailsService 登録
            .userDetailsService(customUserDetailsService);
            
        return http.build();
    }
    
    /** BCrypt パスワードハッシュ  */
	@Bean
	public PasswordEncoder passwordEncoder() {
	    return new BCryptPasswordEncoder();
	}
	
	/** セッション破棄イベントを拾う（maximumSessions が正しく動作するために推奨） */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}



