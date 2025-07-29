package com.example.onseinippou.common.interceptor;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.onseinippou.domain.model.user.User;
import com.example.onseinippou.security.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LoggingInterceptor implements HandlerInterceptor{
	
	private final CurrentUserProvider currentUserProvider;
	
	@Override
	public boolean preHandle(HttpServletRequest request,
			HttpServletResponse response,
			Object handler) throws IOException {
		User user = currentUserProvider.getCurrentUser();
		
		if (user.getSheetId() == null) {
			 // sheetId が無い ⇒ /register-sheet-page へ 302
			response.sendRedirect("/register-sheet-page");
			return false; // これ以上のハンドラ呼び出しは行わない
		}
		return true; // sheetIdあり ⇒ 通常処理
	}
	
	
}


