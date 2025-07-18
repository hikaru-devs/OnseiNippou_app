package com.example.OnseiNippou_app.Service;

import jakarta.transaction.Transactional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.OnseiNippou_app.Entity.User;
import com.example.OnseiNippou_app.Repository.UserRepository;
import com.example.OnseiNippou_app.Security.CustomUserDetails;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor   // ← Lombok。コンストラクタ DI
@Transactional
public class RegisterSheetService {
	
	private final UserRepository userRepository;
	
	
	public void registerSheetId(String sheetId) {
		// ① Authentication から CustomUserDetails を取得
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (!(principal instanceof CustomUserDetails details)) {
			throw new IllegalStateException("認証情報が取得できません。もう一度/onsei-nippou-pageへアクセスください。");
		}
		
		// ② エンティティは改めて DB から取得（永続化コンテキスト下）
		Long userId = details.getUser().getId();
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalStateException("ユーザーが存在しません。本当に登録処理がうまくいっているのか確認してください。"));
		
		// ③ バリデーション
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




