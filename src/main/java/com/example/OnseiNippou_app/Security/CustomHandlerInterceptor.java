package com.example.OnseiNippou_app.Security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class CustomHandlerInterceptor implements HandlerInterceptor{
	
	@Override
	public boolean preHandle(HttpServletRequest request,
			HttpServletResponse response,
			Object handler) throws IOException {
		
		// 認証ユーザを取得
		CustomUserDetails user = (CustomUserDetails) SecurityContextHolder
				.getContext().getAuthentication().getPrincipal();
		if (user.getSheetId() == null) {
			 // sheetId が無い ⇒ /register-sheet-page へ 302
			response.sendRedirect("/register-sheet-page");
			return false; // これ以上のハンドラ呼び出しは行わない
		}
		
		return true; // sheetIdあり ⇒ 通常処理
	}
	
	
}

/* ② (CustomUserDetails) ...                       ← 明示的キャスト
 *   - getPrincipal() は戻り値の型が Object なので
 *    “アプリ固有の認証ユーザクラス” (ここでは CustomUserDetails) に
 *     強制的に変換している。
 *     
 *【よくある安全な書き方（instanceof パターン）】
 *
 * Object principal = SecurityContextHolder.getContext()
 *                                         .getAuthentication()
 *                                         .getPrincipal();
 * if (principal instanceof CustomUserDetails user) {
 *     // user は CustomUserDetails 型として安全に扱える
 * }
 *
 * 【まとめ】
 *  - (CustomUserDetails) は “明示的ダウンキャスト” を示す Java の文法
 *  - Spring Security で現在ログイン中のユーザ情報を
 *    自分のカスタムクラスとして取り出す定番パターン
 */






