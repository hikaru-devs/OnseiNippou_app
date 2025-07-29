package com.example.onseinippou.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.example.onseinippou.domain.model.user.User;
import com.example.onseinippou.domain.repository.UserRepository;

//OAuth2認証（例：Googleログイン）で利用されるカスタムUserService
//DefaultOAuth2UserServiceを継承
	@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
	@Autowired
	private UserRepository userRepository;
	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2User oauth2User = super.loadUser(userRequest);
		System.out.println(oauth2User.getAttributes()); 
		
		// ユーザのemailなどでDB検索
		String email = oauth2User.getAttribute("email");
		User user = userRepository.findByEmail(email)
				.orElseGet(() -> {
					// DBになければ新規登録
					User newUser = new User();
					newUser.setEmail(email);
					// ✅必要な項目セット
					return userRepository.saveAndFlush(newUser);
				});
		
		// アプリのCustomUserDetailsで返す
		return new CustomUserDetails(user, oauth2User.getAttributes());
	}
}
