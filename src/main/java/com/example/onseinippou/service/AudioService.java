package com.example.onseinippou.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
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
	 * ffmpegプロセス、その入力ストリーム、STTクライアントのオブザーバーを保持する。
	 */
	static class StreamingContext {
		final SpeechToTextClient.AudioStreamObserver audioStreamObserver;
		final Process ffmpegProcess;
		final OutputStream ffmpegInput;
		final StringBuilder accumulatedTranscript = new StringBuilder();

		StreamingContext(AudioStreamObserver audioStreamObserver, Process ffmpegProcess) {
			this.audioStreamObserver = audioStreamObserver;
			this.ffmpegProcess = ffmpegProcess;
			this.ffmpegInput = ffmpegProcess.getOutputStream();
		}
	}

	/** WebSocketセッションと、それに対応するストリーミングコンテキストを管理するマップ。 */
	private final Map<WebSocketSession, StreamingContext> sessions = new ConcurrentHashMap<>();
	/** セッションが回復処理中であるかを管理するマップ。 */
	private final Map<WebSocketSession, Boolean> recoveringSessions = new ConcurrentHashMap<>();

	// --- ストリーミング処理メソッド群 ---

	/**
	 * 指定されたWebSocketセッションのストリーミング文字起こしを開始する。
	 * Speech-to-Text APIとの接続を確立し、音声フォーマット変換のためのffmpegプロセスを起動する。
	 * @param session 文字起こしを開始するWebSocketセッション
	 */
	public void startStreamingTranscription(WebSocketSession session) {
		logger.info("▶️ ストリーミングセッション開始処理を開始: {}", session.getId());
		try {
			// STT APIからの文字起こし結果をWebSocketクライアントに送信するコールバックを定義する。
			Consumer<String> onResult = (String transcript) -> {
				// --- リアルタイム送信ロジック (コメントアウトして残す) ---
				/*
				try {
					if (session.isOpen()) {
						session.sendMessage(new TextMessage("{\"transcript\": \"" + transcript + "\"}"));
					}
				} catch (IOException e) {
					logger.error("WebSocketメッセージの送信に失敗: {}", session.getId(), e);
					// このエラーはSTTクライアントに伝播させる
					throw new UncheckedIOException("WebSocketメッセージの送信に失敗", e);
				}
				*/

				// --- 全文一括送信用の追記ロジック ---
				StreamingContext context = sessions.get(session);
				if (context != null) {
					// セッションごとのメモ帳に結果を追記する
					context.accumulatedTranscript.append(transcript);
				}
			};

			// STT APIでエラーが発生した際の処理を定義する。
			Consumer<Throwable> onError = (Throwable error) -> {
				logger.error("STTストリーミングエラーが検知されました: {} - {}", session.getId(), error.getMessage());
				// 回復処理を試みる
				restartStreamingSession(session);
			};

			// Speech-to-Textクライアントのストリーミング認識を開始する。
			AudioStreamObserver sttObserver = speechToTextClient.startStreamingRecognize(onResult, onError);

			// WebMをリニアPCMに変換するため、ffmpegプロセスをストリーミングモードで起動する。
			ProcessBuilder pb = new ProcessBuilder(
					"ffmpeg",
					"-i", "pipe:0",
					"-ar", "16000",
					"-ac", "1",
					"-f", "s16le",
					"pipe:1").redirectErrorStream(true);
			Process ffmpegProcess = pb.start();

			// ffmpegの標準出力を読み取り、STT APIに送信するための別スレッドを開始する。
			Thread sttForwarderThread = new Thread(() -> {
				try (InputStream ffmpegOutput = ffmpegProcess.getInputStream()) {
					byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = ffmpegOutput.read(buffer)) != -1) {
						sttObserver.sendAudio(buffer);
					}
				} catch (IOException e) {
					logger.error("ffmpeg出力の読み取り中にエラーが発生: {}", e.getMessage());
					// このスレッドのエラーも回復処理のトリガーとする
					restartStreamingSession(session);
				}
			});
			sttForwarderThread.start();

			// 作成した各種リソースをセッションコンテキストとして保存する。
			sessions.put(session, new StreamingContext(sttObserver, ffmpegProcess));
			logger.info("✅ ストリーミングセッション準備完了: {}", session.getId());

		} catch (IOException e) {
			logger.error("ストリーミングの開始に致命的な失敗: {}", session.getId(), e);
			// 初期起動の失敗は回復不可能として例外をスローする
			throw new UncheckedIOException("ストリーミングの開始に失敗", e);
		}
	}

	/**
	 * WebSocketから受信した音声データチャンクを処理する。
	 * @param session 音声データを送信したWebSocketセッション
	 * @param audioData 受信した音声データ（WebM形式）
	 */
	public void processAudioChunk(WebSocketSession session, byte[] audioData) {
		StreamingContext context = sessions.get(session);
		if (context != null) {
			try {
				context.ffmpegInput.write(audioData);
				context.ffmpegInput.flush();
			} catch (IOException e) {
				logger.warn("ffmpegへの書き込みに失敗: {}. 回復処理を開始します。", e.getMessage());
				// パイプが壊れた可能性が高いので、回復処理を試みる
				restartStreamingSession(session);
			}
		}
	}

	/**
	 * 指定されたWebSocketセッションのストリーミング文字起こしを停止し、関連リソースを解放する。
	 * @param session 終了するWebSocketセッション
	 */
	public void stopStreamingTranscription(WebSocketSession session) {
		logger.info("⏹️ ストリーミングセッション終了処理を開始: {}", session.getId());
		StreamingContext context = sessions.remove(session);
		recoveringSessions.remove(session); // 回復中だった場合はフラグを解除

		if (context != null) {

			try {
				String finalTranscript = context.accumulatedTranscript.toString();
				// 溜め込んだテキストがあり、セッションが開いている場合のみ送信
				if (session.isOpen() && !finalTranscript.isEmpty()) {
					logger.info("最終的な文字起こし結果を送信: {}文字", finalTranscript.length());
					session.sendMessage(new TextMessage("{\"transcript\": \"" + finalTranscript + "\"}"));
				}
			} catch (IOException e) {
				logger.error("最終的な文字起こし結果の送信に失敗: {}", session.getId(), e);
			}

			try {
				if (context.ffmpegInput != null) {
					context.ffmpegInput.close();
				}
				if (context.ffmpegProcess != null) {
					if (!context.ffmpegProcess.waitFor(3, TimeUnit.SECONDS)) {
						logger.warn("ffmpegプロセスが時間内に終了しませんでした。強制終了します。 Session: {}", session.getId());
						context.ffmpegProcess.destroy();
					}
				}
				if (context.audioStreamObserver != null) {
					context.audioStreamObserver.closeStream();
				}
			} catch (IOException e) {
				logger.error("ストリームのクローズ中にIOエラーが発生: {}", session.getId(), e);
			} catch (InterruptedException e) {
				logger.warn("ffmpegプロセスの待機中に割り込みが発生しました。 Session: {}", session.getId());
				Thread.currentThread().interrupt();
			} finally {
				if (context.ffmpegProcess != null && context.ffmpegProcess.isAlive()) {
					logger.warn("ffmpegプロセスがまだ生存しています。最終手段として強制終了します。 Session: {}", session.getId());
					context.ffmpegProcess.destroyForcibly();
				}
			}
		}
		logger.info("⏹️ ストリーミングセッション終了処理を完了: {}", session.getId());
	}

	/**
	 * エラーが発生したストリーミングセッションを再起動する。
	 * @param session 回復対象のWebSocketセッション
	 */
	void restartStreamingSession(WebSocketSession session) {
		// すでに回復処理中の場合は何もしない
		if (recoveringSessions.putIfAbsent(session, true) != null) {
			logger.info("セッション {} は既に回復処理中のため、スキップします。", session.getId());
			return;
		}

		logger.info("🔄 ストリーミングセッションの回復処理を開始: {}", session.getId());

		try {
			// ユーザーに再接続中であることを通知
			if (session.isOpen()) {
				session.sendMessage(new TextMessage("{\"status\": \"reconnecting\"}"));
			}

			// 既存のリソースをクリーンアップ
			stopStreamingTranscription(session);

			// 少し待機してから再接続
			Thread.sleep(1000); // 1秒待機

			// セッションを再起動
			logger.info("🔄 ストリーミングセッションを再起動します: {}", session.getId());
			startStreamingTranscription(session);

		} catch (Exception e) {
			logger.error("ストリーミングセッションの回復に失敗しました。セッションを終了します。: {}", session.getId(), e);
			// 回復に失敗した場合は、最終的にセッションを閉じる
			try {
				if (session.isOpen()) {
					session.sendMessage(new TextMessage("{\"error\": \"回復不可能なエラーが発生しました。\"}"));
					session.close(org.springframework.web.socket.CloseStatus.SERVER_ERROR);
				}
			} catch (IOException closeException) {
				logger.error("セッションクローズ通知の送信に失敗: {}", session.getId(), closeException);
			}
		} finally {
			// 回復処理の完了（成功・失敗問わず）
			recoveringSessions.remove(session);
		}
	}

	// --- ファイルベースの文字起こし処理 ---
	public String transcribe(MultipartFile file) {
		try {
			File webm = Files.createTempFile("recoding-", ".webm").toFile();
			file.transferTo(webm);
			File wav = new File(webm.getParent(), UUID.randomUUID() + ".wav");
			convertWebmToWav(webm, wav);
			return speechToTextClient.recognizeFromWav(wav.getAbsolutePath());
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("スレッドが中断されました。", ie);
		} catch (IOException ioe) {
			throw new UncheckedIOException("音声ファイル処理に失敗", ioe);
		}
	}

	private void convertWebmToWav(File input, File output) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(
				"ffmpeg", "-i", input.getAbsolutePath(),
				"-ar", "16000", "-ac", "1", "-f", "wav", "-c:a", "pcm_s16le",
				output.getAbsolutePath()).redirectErrorStream(true);
		Process process = pb.start();
		try (var in = process.getInputStream()) {
			in.transferTo(OutputStream.nullOutputStream());
		}
		int exit = process.waitFor();
		if (exit != 0) {
			throw new IllegalStateException("ffmpeg 変換失敗（exit=" + exit + ')');
		}
	}

	/**
	 * ffmpegプロセスを生成する。
	 * ユニットテストでこのメソッドをモック化できるようにprotectedスコープとする。
	 * @return 生成されたffmpegプロセス
	 * @throws IOException プロセスの起動に失敗した場合
	 */
	protected Process createFfmpegProcess() throws IOException {
		ProcessBuilder pb = new ProcessBuilder(
				"ffmpeg",
				"-i", "pipe:0",
				"-ar", "16000",
				"-ac", "1",
				"-f", "s16le",
				"pipe:1").redirectErrorStream(true);
		return pb.start();
	}
}
