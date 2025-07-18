package com.example.OnseiNippou_app.Security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;
    
    @Autowired
    private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/upload-audio", "/submit-text", "/submit-sheet").permitAll()
                .requestMatchers("/onsei-nippou-page", "/register-sheet-page").authenticated()
                .anyRequest().permitAll()
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(customAuthenticationEntryPoint) // ★ここでカスタムEntryPoint適用
            )
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
            .userDetailsService(customUserDetailsService);
        return http.build();
    }


	@Bean
	public PasswordEncoder passwordEncoder() {
	    return new BCryptPasswordEncoder(); // BCrypt推奨
	}
}

/*
 * 要件まとめ
 * - .authenticated()パスはアプリセッション必須なので、未認証なら/loginへ自動リダイレクト
 * - OAuthログイン後、まだアプリユーザ(DB)に登録されていなければこのタイミングで作成
 * - 必ずCustomUserDetails型の認証情報として扱うことで「OAuthだけ認証」状態を防止
 * - /login画面はformもOAuthも共通エントリポイントに
 *
 *
 * Spring Securityの認証フロー概略
 *
 * 1. ログイン（フォーム or OAuth）が完了すると
 *    Authenticationオブジェクト（例：UsernamePasswordAuthenticationTokenやOAuth2AuthenticationToken）が生成される
 *
 * 2. このAuthenticationの「principal」に、CustomUserDetailsが入る
 *    → これが「アプリで認証済み」とみなされる
 *
 * 3. AuthenticationがSecurityContextHolderにセットされ、セッション（HttpSession）と紐づく
 *
 * 4. 以後、SecurityContextHolder.getContext().getAuthentication().getPrincipal()
 *    で「現在ログインしているユーザー情報（CustomUserDetails）」を取得できる
 *    
 * -----
 *
 * 実際に使われるクラス・ポイント
 *
 * 1. CustomUserDetailsService
 *    - フォーム認証（ユーザー名＋パスワード）のとき、
 *      ここでUserDetails（つまりCustomUserDetails）を生成します。
 *
 * 2. CustomOAuth2UserService
 *    - OAuthログイン時に、
 *      OAuth2User（ここでCustomUserDetailsを返すようにカスタム）を生成します。
 *
 * 3. SecurityContextHolder（Spring Securityのコア）
 *    - 認証済みユーザーの情報（Authenticationとprincipal＝CustomUserDetails）を管理。
 */


