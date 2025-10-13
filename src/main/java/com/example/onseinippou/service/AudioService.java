package com.example.onseinippou.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.example.onseinippou.infra.stt.SpeechToTextClient;
import com.example.onseinippou.infra.stt.SpeechToTextClient.AudioStreamObserver;

import lombok.RequiredArgsConstructor;

/**
 * 音声データに関するビジネスロジックを処理するサービスクラス。
 * リアルタイムのストリーミング文字起こしと、ファイルベースの文字起こしの両方を担当する。
 * エラーからの自己回復機能を備える。
 */
@Service
@RequiredArgsConstructor
public class AudioService {

	private static final Logger logger = LoggerFactory.getLogger(AudioService.class);
	/** Speech-to-Text APIとの通信を行うクライアント。 */
	private final SpeechToTextClient speechToTextClient;

	/**
	 * WebSocketセッションごとのストリーミング状態を管理する内部クラス。
	 */
	static class StreamingContext {
		// Google APIへの音声送信用パイプ.
		final AudioStreamObserver audioStreamObserver;
		//これまで文字起こしした結果を記録する蓄積変換テキスト.
		final StringBuilder accumulatedTranscript = new StringBuilder();
		// ユーザーが停止を要求したかを記録するフラグ.
		volatile boolean stopRequested = false;

		StreamingContext(AudioStreamObserver audioStreamObserver) {
			this.audioStreamObserver = audioStreamObserver;
		}
	}

	/** WebSocketセッションと、それに対応するストリーミングコンテキストを管理するマップ。 */
	// 「どの利用者 (WebSocketSession) が、どの作業台 (StreamingContext) を使っているか」を記録している.
	private final Map<WebSocketSession, StreamingContext> sessionMap = new ConcurrentHashMap<>();
	/** セッションが回復処理中であるかを管理するマップ。 */
	private final Map<WebSocketSession, Boolean> recoveringSessionMap = new ConcurrentHashMap<>();

	// --- ストリーミング処理メソッド群 ---
	/**
	 * このメソッドは外部からのエントリーポイントになる.
	 * @param session WebSocketセッション
	 */
	public void startStreamingTranscription(WebSocketSession session) {
		// 新しく作成する、引数が2つのメソッドを空のテキストで呼び出す
		startStreamingTranscription(session, "");
	}

	/**
	 * セッションの開始を宣言し、すべての準備を整えるメソッド。引き継ぎテキストを受け取ることができる.
	 * WebSocketでメッセージ（音声データなど）が届くと、Springはそのメッセージに「誰から送られてきたか」という情報 (session) を付けてくれる.
	 * @param session WebSocketセッション
	 * @param initialTranscript 引き継ぐ初期テキスト
	 */
	private void startStreamingTranscription(WebSocketSession session, String initialTranscript) {
		logger.info("▶️ ストリーミングセッション開始処理を開始: {}", session.getId());
		try {
			// STT APIからの文字起こし結果を蓄積変換テキストに追記する.
			Consumer<String> onResult = transcript -> {
				// セッションマップからStreamingContextを取り出す.
				StreamingContext context = sessionMap.get(session);
				if (context != null) {
					// StreamingContextの蓄積変換テキスト追加する.
					context.accumulatedTranscript.append(transcript);
				}
			};
			// STT APIでアイドルタイムアウトが発生した際の処理.
			Runnable onIdleTimeout = () -> handleSttIdleTimeout(session);
			// 予期せぬSTT APIエラーが発生した際の処理.
			Consumer<Throwable> onError = error -> performFullSessionRecovery(session);

			// Googleとの通信完了時に呼び出される処理.
			Runnable onStreamCompleted = () -> {
				StreamingContext context = sessionMap.get(session);
				// もしユーザーが停止ボタンを押していたら
				if (context != null && context.stopRequested) {
					logger.info("Google STTとのストリームが正常に完了しました。最終処理を実行します。 Session: {}", session.getId());
					// 最終的なテキストを送信して、WebSocket接続を切断する
					sendFinalTranscriptAndClose(session);
				}
			};

			// 新しい利用者が接続してきた際に、その人のためのをStreamingContextを準備してMapに保管する.
			// 上記で準備した変数を渡してGoogleへの専用回線を開くよう依頼し、音声送信用パイプを受け取る
			AudioStreamObserver sttObserver = speechToTextClient.startStreamingRecognize(onResult, onIdleTimeout,
					onError, onStreamCompleted);
			// 新しいStreamingContextを用意し、受け取った音声送信用パイプを設置する
			StreamingContext newContext = new StreamingContext(sttObserver);
			// これから文字起こしするテキストをメモするメモ帳に、前のセッションからの引き継ぎ内容を書き込む
			newContext.accumulatedTranscript.append(initialTranscript);
			// セッションマップに保管する.
			sessionMap.put(session, newContext);

			logger.info("✅ ストリーミングセッション準備完了: {}", session.getId());

		} catch (Exception e) {
			logger.error("ストリーミングの開始に致命的な失敗: {}", session.getId(), e);
			throw new RuntimeException("ストリーミングの開始に失敗", e);
		}
	}

	/**
	 * WebSocketから受信した音声データチャンクを処理する。
	 * @param session 音声データを送信したWebSocketセッション
	 * @param audioData 受信した音声データ（LINEAR16形式）
	 */
	public void processAudioChunk(WebSocketSession session, byte[] audioData) {
		StreamingContext context = sessionMap.get(session);
		if (context != null) {
			// 音声チャンクをgoogleへのパイプに投入する.
			context.audioStreamObserver.sendAudio(audioData);
		}
	}

	/**
	 * フロントからの停止信号を受け取り、最終処理の準備をするメソッド.
	 */
	public void stopAndFinalizeTranscription(WebSocketSession session) {
		StreamingContext context = sessionMap.get(session);
		if (context != null) {
			logger.info("クライアントからの停止要求を受信。Google STTへのストリームを閉じます。 Session: {}", session.getId());
			context.stopRequested = true;
			// Googleへの音声送信を完了させる。これにより、最終的にonCompletedコールバックがトリガーされる。
			context.audioStreamObserver.closeStream();
		}
	}

	/**
	 * ユーザーが「録音停止」ボタンを押す以外の、あらゆる異常セッション終了を処理する.
	 * TODO 今後はセッションに紐づくユーザーに対して変換成功していたテキストをアプリ再訪時取得できるようにする.
	 * @param session 終了するWebSocketセッション
	 */
	public void handleAbnormalClosure(WebSocketSession session) {
		logger.warn("予期せぬセッションクローズを検知。リソースをクリーンアップします。 Session: {}", session.getId());
		StreamingContext context = sessionMap.remove(session);
		recoveringSessionMap.remove(session);

		if (context != null && context.audioStreamObserver != null) {
			context.audioStreamObserver.closeStream();
		}
	}

	/**
	 * ★★★ [修正点] 最終テキスト送信とセッションクローズを責務とするメソッド ★★★
	 */
	private void sendFinalTranscriptAndClose(WebSocketSession session) {
		// このセッションのすべてのリソースを削除し、削除した値を返す.
		StreamingContext context = sessionMap.remove(session);
		recoveringSessionMap.remove(session);

		if (context != null) {
			try {
				String finalTranscript = context.accumulatedTranscript.toString();
				if (session.isOpen()) {
					logger.info("最終的な文字起こし結果を送信: {}文字", finalTranscript.length());
					session.sendMessage(new TextMessage("{\"transcript\": \"" + finalTranscript + "\"}"));
					// サーバー側から正常に接続を閉じる
					session.close(CloseStatus.NORMAL);
				}
			} catch (IOException e) {
				logger.error("最終的な文字起こし結果の送信またはセッションクローズに失敗: {}", session.getId(), e);
			}
		}
		logger.info("⏹️ ストリーミングセッション終了処理を完了: {}", session.getId());
	}

	/**
	 * STTのアイドルタイムアウト時に、ユーザーに通知せず裏側で静かに接続を再確立する。
	 * テキストデータは維持される。
	 * @param session 回復対象のWebSocketセッション
	 */
	private void handleSttIdleTimeout(WebSocketSession session) {
		logger.info("STTアイドルタイムに入りました。処理は継続しています。: {}", session.getId());
		// 現在のテキストを退避
		String currentText = "";
		StreamingContext oldContext = sessionMap.get(session);
		if (oldContext != null) {
			currentText = oldContext.accumulatedTranscript.toString();
		} else {
			// コンテキストが存在しない場合は何もしない
			return;
		}
		// 既存のリソースをクリーンアップ
		if (oldContext.audioStreamObserver != null) {
			oldContext.audioStreamObserver.closeStream();
		}
		// 新しいSTTストリームを開始し、退避したテキストを引き継ぐ

		startStreamingTranscription(session, currentText);
		logger.info("STT再接続の引継ぎ処理が完了しました: {}", session.getId());
	}

	/**
	 * Google STT APIとの通信中に致命的なエラーが発生した際に、
	 * セッション全体を再起動して文字起こしを継続させるための、全面的な回復処理。
	 * @param session 回復対象のWebSocketセッション
	 */
	private void performFullSessionRecovery(WebSocketSession session) {
		if (recoveringSessionMap.putIfAbsent(session, true) != null) {
			return;
		}
		// 回復処理を始める前に蓄積変換テキストのデータを取得する.
		String previousText = "";
		StreamingContext oldContext = sessionMap.get(session);
		if (oldContext != null) {
			previousText = oldContext.accumulatedTranscript.toString();
		}
		try {
			logger.info("🔄 Google STT APIとの通信中にエラーが発生しました。ストリーミングセッションの回復処理を開始します: {}", session.getId());
			if (session.isOpen()) {
				session.sendMessage(new TextMessage("{\"status\": \"reconnecting\"}"));
			}
			handleAbnormalClosure(session);
			Thread.sleep(1000);
			startStreamingTranscription(session, previousText);
			logger.info("✅ ストリーミングセッションの回復に成功しました: {}", session.getId());
			// 回復完了をフロントに通知.
			if(session.isOpen()){
                session.sendMessage(new TextMessage("{\"status\": \"recovered\"}"));
            }
		} catch (Exception e) {
			logger.error("セッションの回復に失敗しました。最終処理を実行します。: {}", session.getId(), e);
			try {
                if (session.isOpen()) {
                    // ★★★ [修正点] 回復失敗の専用エラーと、それまでのテキストを送信 ★★★
                    String jsonError = String.format(
                        "{\"error\": \"RECOVERY_FAILED\", \"transcript\": \"%s\"}",
                        previousText.replace("\"", "\\\"") // JSONエスケープ
                    );
                    session.sendMessage(new TextMessage(jsonError));
                    session.close(CloseStatus.SERVER_ERROR);
                }
            } catch (IOException closeException) {
                logger.error("回復失敗の通知とセッションクローズに失敗しました", closeException);
            }
		} finally {
			recoveringSessionMap.remove(session);
		}
	}
}