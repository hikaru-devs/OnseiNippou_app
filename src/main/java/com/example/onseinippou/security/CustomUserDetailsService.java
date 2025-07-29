package com.example.onseinippou.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.onseinippou.domain.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {
	@Autowired
	private UserRepository userRepository;
	
	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return userRepository.findByEmail(email)
				.map(user -> new CustomUserDetails(user))
				.orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません: " + email));
 }
}

/*
 * CustomUserDetailsServiceのベストプラクティス例
 * - UserDetailsServiceは、フォーム認証やSecurityでユーザー情報を取得するための標準インターフェース
 * - userRepository.findByEmail(email)はOptionalで返すのが現代的
 * - ユーザーが見つからなければUsernameNotFoundExceptionで明示的に例外を投げる
 * - 見つかった場合はCustomUserDetailsを生成して返す（← principalになる）
 *
 * 改善ポイントや注意点
 * 1. CustomUserDetailsのコンストラクタにはUserごと渡す方が柔軟（emailだけでなく他の属性も使えるため）
 * 2. UserのパスワードやロールもCustomUserDetailsで扱えるようにしておく
 * 3. メソッドやフィールドには@Nonnullや@NotNullをつけるとさらに安全
 */
