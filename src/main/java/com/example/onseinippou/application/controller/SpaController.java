package com.example.onseinippou.application.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * シングルページアプリケーション(SPA)の画面遷移を制御するコントローラー。
 * APIや静的リソース以外のすべてのリクエストをindex.htmlに転送する。
 */
@Controller
public class SpaController {

	/**
	 * APIエンドポイント(api, ws)や静的ファイルに一致しないすべてのパスをキャッチする。
	 * この正規表現は、自分自身の転送先である "index.html" を含まないため、無限ループを防ぐ。
	 * @return 転送先のパス "forward:/index.html"
	 */
	@RequestMapping(value = { "/", "/{path:^(?!api|ws|static|assets|index\\.html).*$}/**" })
	public String forwardSpa() {
		return "forward:/index.html";
	}
}