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
        //------------------------------------------------------------------
        // ① 認可ルール
        //------------------------------------------------------------------
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/static/**", "/vite.svg", "/favicon.ico").permitAll()
                .requestMatchers("/api/**").permitAll()
                .requestMatchers("/onsei-nippou-page", "/register-sheet-page").authenticated()
                .anyRequest().permitAll()
            )
            //------------------------------------------------------------------
            // ② 例外ハンドリング（未認証アクセス時）
            //------------------------------------------------------------------
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(customAuthenticationEntryPoint) 
            )

            //------------------------------------------------------------------
            // ③ フォーム & OAuth2 ログイン
            //------------------------------------------------------------------
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
            //------------------------------------------------------------------
            // ④ セッション管理（ラムダ DSL）
            //------------------------------------------------------------------
            .sessionManagement(sess -> sess
            		// 無効セッション（タイムアウト or 不正JSESSIONID）時の挙動
            		.invalidSessionStrategy(customInvalidSessionStrategy)
            		// 1ユーザー1セッションのみ許可
            		.maximumSessions(1)
            		.maxSessionsPreventsLogin(false)	// = 後勝ち
            		.expiredUrl("/login?expired=true")	// 期限切れ遷移先
            )
            //------------------------------------------------------------------
            // ⑤ CSRF は一旦 OFF（必要に応じて ON に）
            //------------------------------------------------------------------
            .csrf(csrf -> csrf.disable())
            //------------------------------------------------------------------
            // ⑥ UserDetailsService 登録
            //------------------------------------------------------------------
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


