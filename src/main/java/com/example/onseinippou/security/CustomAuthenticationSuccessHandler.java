package com.example.onseinippou.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 認証成功後の処理を専門に担当するハンドラー。
 * ログイン方法（フォーム、OAuth2など）にかかわらず、
 * 認証が成功したら必ず指定のページにリダイレクトさせる。
 */
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {

		// 認証が成功したら、問答無用で "/onsei-nippou-page" にリダイレクトさせる
		response.sendRedirect("/onsei-nippou-page");
	}
}