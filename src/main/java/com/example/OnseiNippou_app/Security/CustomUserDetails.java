package com.example.OnseiNippou_app.Security;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.example.OnseiNippou_app.Entity.User;


public class CustomUserDetails implements UserDetails, OAuth2User {

    private final User user;
    private final Map<String, Object> attributes;

    // 通常ログイン用（Userのみ）
    public CustomUserDetails(User user) {
        this.user = user;
        this.attributes = Collections.emptyMap();
    }

    // OAuth2ログイン用（User+OAuth2属性）
    public CustomUserDetails(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes != null ? attributes : Collections.emptyMap();
    }
    
    
    // Userエンティティへの共通アクセス
    public User getUser() {
        return user;
    }
    // sheetId取得
    public String getSheetId() {
 	   return user.getSheetId();
    }

    // UserDetails実装（パスワードはUserから取得）
    @Override
    public String getPassword() {
        return user.getPassword(); // フォーム認証なら値あり、OAuthのみならnullでもOK
    }

    @Override
    public String getUsername() {
        return user.getEmail(); // ログインIDやユーザー名。ユニークなものを
    }

    // 権限付与（現場ではロールから生成する場合が多い）
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 例: Userにrolesフィールドがあればそこから生成
        // return user.getRoles().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        return Collections.emptyList();
    }

    // アカウント状態（将来の拡張に備えUserの値を返す）
    @Override
    public boolean isAccountNonExpired() {
        return true; // user.isAccountNonExpired() などにしても良い
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // user.isAccountNonLocked() などにしても良い
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true; // user.isEnabled() などにしても良い
    }

    // OAuth2User実装
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    // 公式ガイド推奨：getNameはDBの一意なIDを返す
    @Override
    public String getName() {
        return String.valueOf(user.getId());
    }


}

/*
 * CustomUserDetailsのベストプラクティス（公式・現場共通）
 * ----------------------------------------------------------
 * 1. UserDetails, OAuth2Userの両方を実装し、アプリ/外部認証を統一管理
 * 2. Userエンティティを必ず全体で保持する（emailだけでなく、id, roles, passwordなども使える）
 * 3. OAuth2属性（Map<String, Object> attributes）はOAuth2認証時のみ必要。通常はnullまたは空MapでOK
 * 4. getAuthorities()はUserエンティティやロール情報から生成できる設計にしておくと拡張性が高い
 * 5. getPassword()はUserエンティティから取得。OAuthユーザーはnullも許容
 * 6. getName()はUserの一意なIDを返すのが公式ガイド推奨（emailは重複可能性があるため、DBのid推奨）
 * 7. アカウント状態(isEnabled等)もUserエンティティの値に合わせて返すと拡張しやすい
 * 8. コンストラクタはUser単体・User+OAuth2属性の両方に対応
 */