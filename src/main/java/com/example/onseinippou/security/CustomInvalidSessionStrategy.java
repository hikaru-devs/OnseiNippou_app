package com.example.onseinippou.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.session.InvalidSessionStrategy;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

//------------------------------------------------------------------
//無効セッション時にリダイレクトする戦略
//------------------------------------------------------------------

@Slf4j
@Component
public class CustomInvalidSessionStrategy implements InvalidSessionStrategy {
	
	@Override
	/* 無効セッション検知時のコールバックを実装 */
	public void onInvalidSessionDetected(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		/* セッションタイムアウトをサーバーログに出力する */
		log.info("⚠️ 無効セッション検知: {}", request.getRequestURI());
		
		String msg = URLEncoder.encode("セッションがタイムアウトしました。再度ログインしてください。", StandardCharsets.UTF_8);
		/* /login画面にリダイレクトさせる */
		response.sendRedirect(request.getContextPath() + "/login?timeout=" + msg);
	}

}


//---------------------------------------------------------------------
//HttpServletRequest, HttpServletResponseはどういうときに使うのか
//---------------------------------------------------------------------
/**
* サーブレット／フィルタ／@Controller などで HTTP を処理するときに使用。
* HttpServletRequest：クライアントからのURI・パラメータ・セッション等の「受信データ」へアクセスする。
* HttpServletResponse：サーバーからクライアントへ返すステータス・ヘッダ・Cookie・リダイレクト等の「送信データ」を設定する。
*/

//---------------------------------------------------------------------
//log.info の表示場所と使い方
//---------------------------------------------------------------------
/**
* Spring Boot のデフォルト設定では INFO 以上のログはコンソールと
* Logback の出力先（例: application.log）に書き出される。
* {} プレースホルダに可変引数を渡すことで、連結より高速かつ安全に
* ログを整形できる。
*/

//---------------------------------------------------------------------
//getContextPath() の使い方は？
//---------------------------------------------------------------------
/**
* HttpServletRequest#getContextPath() はアプリケーションのコンテキスト
* ルート（例: /myapp）を返すメソッド。
* デプロイ先のパスが変わっても URL を正しく組み立てられるよう、
* sendRedirect や HTML リンク生成時などで先頭に付与して利用する。
*/