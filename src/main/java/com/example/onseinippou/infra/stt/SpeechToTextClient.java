package com.example.onseinippou.infra.stt;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.api.gax.rpc.OutOfRangeException;
import com.google.cloud.speech.v1.LongRunningRecognizeMetadata;
import com.google.cloud.speech.v1.LongRunningRecognizeResponse;
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;

import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SpeechToTextClient {

	private SpeechClient speechClient;

	@PostConstruct
	public void initialize() {
		try {
			log.info("SpeechClientを初期化します...");
			this.speechClient = SpeechClient.create();
			log.info("SpeechClientの初期化が完了しました。");
		} catch (IOException e) {
			log.error("SpeechClientの初期化に失敗しました。", e);
			throw new RuntimeException("SpeechClientの初期化に失敗", e);
		}
	}

	@PreDestroy
	public void shutdown() {
		if (this.speechClient != null) {
			log.info("SpeechClientをシャットダウンします...");
			this.speechClient.close();
			log.info("SpeechClientのシャットダウンが完了しました。");
		}
	}

	/**
	 * ストリーミング音声認識を開始します。
	 *
	 * @param onResult  文字起こし結果を受け取るためのコールバック
	 * @param onIdleTimeout STTのアイドルタイムアウトを通知するためのコールバック
	 * @param onError   エラーが発生した際に呼び出されるコールバック
	 * @param onStreamCompleted サーバー側での完了通知を受け取るコールバック
	 * @return 音声データを送信するためのストリームオブザーバー
	 * 
	 */
	public AudioStreamObserver startStreamingRecognize(
			Consumer<String> onResult,
			Runnable onIdleTimeout,
			Consumer<Throwable> onError,
			Runnable onStreamCompleted) {
		// 1. Googleからのレスポンスを非同期で受け取るためのオブザーバーを作成		
		ResponseObserver responseObserver = new ResponseObserver(onResult, onIdleTimeout, onError, onStreamCompleted);

		// 2. 双方向ストリーミング用のCallableを取得
		BidiStreamingCallable<StreamingRecognizeRequest, StreamingRecognizeResponse> callable = speechClient
				.streamingRecognizeCallable();

		// 3. ストリーミングを開始
		ApiStreamObserver<StreamingRecognizeRequest> requestObserver = callable.bidiStreamingCall(responseObserver);

		// 4. 最初に認識設定を送信
		requestObserver.onNext(StreamingRecognizeRequest.newBuilder()
				.setStreamingConfig(StreamingRecognitionConfig.newBuilder()
						.setConfig(RecognitionConfig.newBuilder()
								.setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
								.setSampleRateHertz(16000)
								.setLanguageCode("ja-JP")
								.setEnableAutomaticPunctuation(true)
								.setModel("latest_long")
								.build())
						.setInterimResults(true)
						.build())
				.build());

		// 5. 音声データを送信するためのコントローラーを返す
		return new AudioStreamObserver(requestObserver);
	}

	/**
	 * Google STT APIとのストリーミングを制御するクラス。
	 * このクラスを介して、音声データをGoogleに送信したり、送信の終了を伝えたりする。
	 */
	public static class AudioStreamObserver {
		// Googleのライブラリが提供する、実際の通信ストリーム（パイプ）本体。
		// このオブジェクトを通じて、実際にGoogleへデータが送られる。
		private final ApiStreamObserver<StreamingRecognizeRequest> requestObserver;

		/**
		 * コンストラクタ.
		 */
		public AudioStreamObserver(ApiStreamObserver<StreamingRecognizeRequest> requestObserver) {
			this.requestObserver = requestObserver;
		}

		/**
		 * 音声データをGoogleに送信するメソッド。
		 * @param audioBytes 送信する音声データ（LINEAR16形式）
		 */
		public void sendAudio(byte[] audioBytes) {
			// 1. 音声データをGoogle APIが理解できるリクエスト形式（StreamingRecognizeRequest）に梱包し、
			// 2. 梱包したデータをパイプに流し込む（onNext）。
			requestObserver.onNext(StreamingRecognizeRequest.newBuilder()
					.setAudioContent(ByteString.copyFrom(audioBytes))
					.build());
		}

		/**
		 * 終了信号をGoogleに送信し、残りの音声を処理し通信を完了させる.
		 */
		public void closeStream() {
			requestObserver.onCompleted();
		}
	}

	/**
	 * Google STT APIからの非同期レスポンスを処理する内部クラス。
	 */
	/**
	 * Google STT APIからの非同期レスポンス（文字起こし結果やエラー通知）を処理する内部クラス。
	 */
	private static class ResponseObserver implements ApiStreamObserver<StreamingRecognizeResponse> {
		// AudioServiceで定義された「文字起こし結果が来たらどうするか」という行動計画。
		private final Consumer<String> onResult;
		// AudioServiceで定義された「無音タイムアウトが起きたらどうするか」という行動計画。
		private final Runnable onIdleTimeout;
		// AudioServiceで定義された「予期せぬエラーが起きたらどうするか」という行動計画。
		private final Consumer<Throwable> onError;
		// AudioServiceで定義された「全ての処理が完了したらどうするか」という行動計画。
		private final Runnable onStreamCompleted;

		/**
		 * コンストラクタ：外部で定義された様々な状況への「行動計画（コールバック）」を受け取り、保持する。
		 */
		public ResponseObserver(Consumer<String> onResult, Runnable onIdleTimeout, Consumer<Throwable> onError,
				Runnable onStreamCompleted) {
			this.onResult = onResult;
			this.onIdleTimeout = onIdleTimeout;
			this.onError = onError;
			this.onStreamCompleted = onStreamCompleted;
		}

		/**
		 * Googleから文字起こし結果（中間結果または最終結果）が届くたびに呼び出されるメソッド。
		 */
		@Override
		public void onNext(StreamingRecognizeResponse response) {
			// もしレスポンスに文字起こし結果が含まれていれば
			if (response.getResultsCount() > 0) {
				StreamingRecognitionResult result = response.getResults(0);
				// もしその結果が「最終版（isFinal=true）」であれば
				if (result.getIsFinal()) {
					// 保持している行動計画（onResult）を実行し、最終結果のテキストを渡す。
					onResult.accept(result.getAlternatives(0).getTranscript());
				}
			}
		}

		@Override
		public void onError(Throwable t) {
			// ★★★ [修正点] 5分制限のエラー(OutOfRangeException)もここで検知する ★★★
			boolean isTimeoutError = false;
			if (t instanceof OutOfRangeException) {
				isTimeoutError = true;
			} else if (t instanceof StatusRuntimeException) {
				if (((StatusRuntimeException) t).getStatus().getCode() == Code.OUT_OF_RANGE) {
					isTimeoutError = true;
				}
			}

			if (isTimeoutError) {
				log.warn("Google STTがストリームの終了を通知しました（5分制限または無音タイムアウト）。再接続を試みます");
				onIdleTimeout.run();
				return; // ここで処理を終了
			}

			// 上記以外の本物のエラーの場合
			log.error("Google STTから予期せぬエラーを受信しました。", t);
			onError.accept(t);
			// AudioServiceで定義したonStreamCompletedを.run()（実行せよ）という命令
			onStreamCompleted.run();
		}

		@Override
		public void onCompleted() {
			// AudioServiceで定義したonStreamCompletedを.run()（実行せよ）という命令
			onStreamCompleted.run();
		}
	}

	// --- ファイルベースの処理（変更なし） ---
	public String recognizeFromWav(String wavFilePath) throws IOException {
		try {
			ByteString audioBytes = ByteString.readFrom(new FileInputStream(wavFilePath));
			RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(audioBytes).build();
			RecognitionConfig config = RecognitionConfig.newBuilder()
					.setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
					.setSampleRateHertz(16000)
					.setLanguageCode("ja-JP")
					.setEnableAutomaticPunctuation(true)
					.setModel("latest_long")
					.build();

			OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> future = speechClient
					.longRunningRecognizeAsync(config, audio);
			LongRunningRecognizeResponse response = future.get();
			StringBuilder resultText = new StringBuilder();
			for (SpeechRecognitionResult result : response.getResultsList()) {
				resultText.append(result.getAlternatives(0).getTranscript());
			}
			return resultText.toString();
		} catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("音声認識の非同期処理に失敗しました。", e);
		}
	}
}