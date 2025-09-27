package com.example.onseinippou.service;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.example.onseinippou.domain.model.user.User;
import com.example.onseinippou.security.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

/**
 * ユーザーのスプレッドシートを登録するクラス.
 */
@Service
@RequiredArgsConstructor   // ← Lombok。コンストラクタ DI
@Transactional
public class RegisterSheetService {

	/**
	 * ログインユーザーを“最新状態”で取得する共通クラス.
	 */
	private final CurrentUserProvider currentUserProvider;
	
	
	/**
	 * スプレッドシートを登録する.
	 * @param sheetId シートURL.
	 */
	public void registerSheetId(String sheetId) {
		User user = currentUserProvider.getCurrentUser(); 
		
		// ① バリデーション
		if(sheetId == null || sheetId.isBlank()) {
			throw new IllegalArgumentException("sheetIdが空です。");
		}
		
		// ④ 更新
		user.setSheetId(sheetId);
		// @Transactional により自動 flush
		
	}
}



/*
@Service
public class RegisterSheetService {

	@Autowired
	private UserRepository userRepository;

	
    public void registerSheetId(String sheetId) {
    	
        User user = (User) SecurityContextHolder.getContext()
        		.getAuthentication().getPrincipal();
        
        user.setSheetId(sheetId);
        userRepository.save(user);
    }
}


| 評価項目                       | 配点 | 得点 | コメント                                                                    |
|-------------------------------|-----:|-----:|-----------------------------------------------------------------------------|
| ① 設計（責務分離・依存注入）        | 25  | 15 | *フィールド @Autowired* は可読性◯だが **コンストラクタ注入推奨**。<br>Service 層に SecurityContext 
依存が露出しているため、テスト困難。|

| ② セキュリティ／正当性             | 25  | 10 | `principal` は通常 **CustomUserDetails**。直接 `User` へキャストは **ClassCastException** の恐れ。|

| ③ 永続化＆トランザクション管理     | 20  | 10 | 認証時に保持している User は **Hibernate セッションと分離** している可能性が高い。<br>`@Transactional
` + `findById()` で再取得するのが安全。|

| ④ 可読性・例外ハンドリング         | 15  | 10 | null チェック・例外処理が無い。`sheetId` が空などのバリデーションも欲しい。|

| ⑤ スレッドセーフ／再利用性        | 15  | 10 | シンプルだが `SecurityContextHolder` を直接触るとユニットテストが難しい。DI で `Authentication` を渡す
方法もある。|
*/




