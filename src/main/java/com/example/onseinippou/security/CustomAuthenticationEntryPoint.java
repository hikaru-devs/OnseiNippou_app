package com.example.onseinippou.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

//---------------------------------------------------------------------
// 認証前のページ情報を保持するクラス
//---------------------------------------------------------------------

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         org.springframework.security.core.AuthenticationException authException) throws IOException {
        // リダイレクト先URLをパラメータとして付与
        String redirectUrl = request.getRequestURI();
        String loginUrl = "/login?redirect=" + java.net.URLEncoder.encode(redirectUrl, "UTF-8");
        response.sendRedirect(loginUrl);
    }
}

