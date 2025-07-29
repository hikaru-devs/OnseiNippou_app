package com.example.onseinippou.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.example.onseinippou.domain.model.user.User;
import com.example.onseinippou.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;

//---------------------------------------------------------------------
// ログインユーザーを“最新状態”で取得する共通クラス
// Serviceでは、
// User user = currentUserProvider.getCurrentUser(); 
// のように使う。
//---------------------------------------------------------------------

@Component
@RequiredArgsConstructor
public class CurrentUserProvider {
	
	private final UserRepository userRepository;
	
	public User getCurrentUser() {
		// ① Authentication 取得
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()) {
			throw new AuthenticationCredentialsNotFoundException("未認証です。ログインしてください。");
		}
		
		// ② principal → ID → 再読込
		Object principal = auth.getPrincipal();
		if (!(principal instanceof CustomUserDetails details)) {
			throw new AuthenticationCredentialsNotFoundException("未認証です。ログインしてください。");
		}
		
		return userRepository.findById(details.getUser().getId())
				// ? IllegalStateExceptionはどういうときに使うのか？
				/*  */
				.orElseThrow(() -> new IllegalStateException("ユーザーが存在しません。"));
	}

}
