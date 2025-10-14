package com.example.onseinippou.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.example.onseinippou.application.socket.TranscriptionSocketHandler;
import com.example.onseinippou.common.interceptor.LoggingInterceptor;

@Configuration
@EnableWebSocket
public class WebConfig implements WebMvcConfigurer, WebSocketConfigurer {

	@Autowired
	private LoggingInterceptor loggingInterceptor;

	@Autowired
	private TranscriptionSocketHandler transcriptionSocketHandler;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOrigins("http://localhost:5173",
						"https://onsei-nippou-app-207055258179.asia-northeast1.run.app")
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowCredentials(true);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(loggingInterceptor)
				.addPathPatterns("/onsei-nippou-page/**"); // 監視対象
	}

	/**
	 * WebSocketハンドラーをアプリケーションに登録する。
	 * この設定により、WebSocket接続のエンドポイント（接続先URL）が作成される。
	 * @param registry WebSocketハンドラーを登録するためのレジストリ
	 */
	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		// "/ws/transcribe" というパス（エンドポイント）にWebSocket接続が来た際に、
		// transcriptionSocketHandler クラスが処理を担当するように設定する。
		registry.addHandler(transcriptionSocketHandler, "/ws/transcribe").setAllowedOrigins("*");
	}

	@Bean
	public ServletServerContainerFactoryBean createWebSocketContainer() {
		ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();

		// バイナリメッセージ(音声データ)のバッファーサイズを 512KB に設定
		container.setMaxBinaryMessageBufferSize(512 * 1024);

		// テキストメッセージのバッファーサイズも念のため設定（512KBは文庫本２冊に相当するテキストデータ。将来チャット機能を実装するときなどに有効）
		container.setMaxTextMessageBufferSize(512 * 1024);

		// クライアントの予期せぬ離脱に備えた接続遮断措置（３０秒）
		// ユーザーがブラウザを閉じる
		// ブラウザ自体がクラッシュした。
		// PCの電源が突然落ちた、またはネットワークが瞬時に切断された。
		// OSがリソース不足でブラウザのプロセスを強制終了させた。
		// ユーザーがPCをスリープさせた、またはシャットダウンした。
		// 入力しているにもかかわらず音声が届かない場合は５分で接続遮断するようにAudioServiceで実装済み
		container.setMaxSessionIdleTimeout(30000L);

		return container;
	}
}
