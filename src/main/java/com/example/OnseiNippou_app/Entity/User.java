package com.example.OnseiNippou_app.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // ログインID用のemail。ユニーク必須
    @Column(nullable = false, unique = true)
    private String email;
    
    // パスワード（ハッシュ化推奨）。OAuth認証だけのユーザーはnull可
    @Column(nullable = true)
    private String password;
    
    // 権限/ロール。単純な用途ならString（複数ロールや権限がある場合は別テーブル推奨）
    @Column(nullable = false)
    private String role = "ROLE_USER"; // デフォルト値
    
    // アプリ独自の項目
    private String sheetId;

    // 登録・更新日時
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 新規作成時にcreatedAt自動セット
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // 更新時にupdateAt自動セット
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

/*
 * 【Userエンティティ設計・ベストプラクティス】
 * 
 * - email: ログインIDとしてユニーク制約
 * - password: フォーム認証やパスワードリセット機能用。OAuth認証ユーザーはnull可。
 * - role: シンプルなロール管理（例: "ROLE_USER", "ROLE_ADMIN"等）
 * - createdAt, updatedAt: 監査用
 * - sheetId: アプリ独自項目
 * - @PrePersist/@PreUpdateで自動的に日時をセット
 */
