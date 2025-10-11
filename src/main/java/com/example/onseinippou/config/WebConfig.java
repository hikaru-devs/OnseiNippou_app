package com.example.onseinippou.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

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

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/{path:^(?!assets|static|.*\\..*$).*$}")
				.setViewName("forward:/index.html");
		registry.addViewController("/{path:^(?!assets|static|.*\\..*$).*$}/**")
				.setViewName("forward:/index.html");
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
}
