package com.example.onseinippou.application.socket;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import com.example.onseinippou.service.AudioService;

/**
 * クライアントからWebSocket接続要求がある場合、以下のライフサイクルを管理する.
 */
@Component
public class TranscriptionSocketHandler extends BinaryWebSocketHandler {

	private static final Logger logger = LoggerFactory.getLogger(TranscriptionSocketHandler.class);

	/** 音声処理を担当するサービス. */
	private final AudioService audioService;

	public TranscriptionSocketHandler(AudioService audioService) {
		this.audioService = audioService;
	}

	/**
	 * 接続確立時、処理を実行する。
	 * @param session 確立されたWebSocketセッション
	 */
	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		try {
			audioService.startStreamingTranscription(session);
		} catch (UncheckedIOException e) {
			logger.error("ストリーミングパイプラインの初期化に失敗しました。 Session: {}", session.getId(), e);
			sendErrorMessageAndClose(session, "文字起こしセッションの初期化に失敗しました。");
		} catch (Exception e) {
			logger.error("予期せぬエラーによりセッションの確立に失敗しました。 Session: {}", session.getId(), e);
			sendErrorMessageAndClose(session, "予期せぬエラーが発生しました。");
		}
	}

	/**
	 * クライアントからバイナリデータを受信した際に処理を実行する.
	 * @param session メッセージを送信したWebSocketセッション
	 * @param message 受信したバイナリメッセージ
	 */
	@Override
	protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
		// 停止信号を受け取ったら、
		if (message.getPayloadLength() < 10) {
			// 最終処理と接続クローズを行うメソッドを呼び出す
			audioService.stopAndFinalizeTranscription(session);

		} else {
			// 通常の音声データ処理
			byte[] payloadCopy = new byte[message.getPayloadLength()];
			message.getPayload().get(payloadCopy);
			audioService.processAudioChunk(session, payloadCopy);
		}
	}

	/**
	 * 接続が遮断した後に必ず呼び出されるメソッド。
	 * 正常に終了した場合は何もせず、クライアントとの接続が予期せず切断された後にセッション停止処理を実行する。
	 * @param session 切断されたWebSocketセッション
	 * @param status  接続のクローズステータス
	 */
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		// 通常のフロー（サーバー側からのクローズ）以外の場合にログを出す
		if (status.getCode() != CloseStatus.NORMAL.getCode()) {
			// AudioService側でリソースのクリーンアップを行う
			audioService.handleAbnormalClosure(session);
		}

	}

	/**
	 * クライアントにエラーメッセージを送信し、セッションを閉じるヘルパーメソッド。
	 * @param session 対象のWebSocketセッション
	 * @param errorMessage クライアントに送信するエラーメッセージ
	 */
	private void sendErrorMessageAndClose(WebSocketSession session, String errorMessage) {
		try {
			if (session.isOpen()) {
				String jsonError = String.format("{\"error\": \"%s\"}", errorMessage);
				session.sendMessage(new TextMessage(jsonError));
				session.close(CloseStatus.SERVER_ERROR);
			}
		} catch (IOException e) {
			logger.error("エラーメッセージの送信とセッションクローズに失敗しました。 Session: {}", session.getId(), e);
		}
	}
}