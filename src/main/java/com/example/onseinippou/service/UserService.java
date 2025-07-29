package com.example.onseinippou.service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.onseinippou.domain.model.user.User;
import com.example.onseinippou.domain.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // Spring Beanで注入

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 新規ユーザー登録例
    public User simpleRegister(String email, String rawPassword) {
    	if (userRepository.findByEmail(email).isPresent()) {
    		throw new IllegalArgumentException("このメールアドレスは既に登録されています。");
    	}
        String encodedPassword = passwordEncoder.encode(rawPassword);
        User user = new User();
        user.setEmail(email);
        user.setPassword(encodedPassword); // ハッシュ済み値をセット
        user.setRole("ROLE_USER");
        return userRepository.save(user);
    }
}
