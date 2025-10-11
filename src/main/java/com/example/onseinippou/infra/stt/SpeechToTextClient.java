package com.example.onseinippou.infra.stt;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.BidiStreamingCallable;
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

@Service
public class SpeechToTextClient {

	/**
	 * ストリーミング音声認識を開始します。
	 *
	 * @param onResult  文字起こし結果を受け取るためのコールバック
	 * @param onError   エラーが発生した際に呼び出されるコールバック
	 * @return 音声データを送信するためのストリームオブザーバー
	 * @throws IOException SpeechClientの作成に失敗した場合
	 */
	@SuppressWarnings("deprecation")
	public AudioStreamObserver startStreamingRecognize(Consumer<String> onResult, Consumer<Throwable> onError)
			throws IOException {
		SpeechClient speechClient = SpeechClient.create();

		// 1. Googleからのレスポンスを非同期で受け取るためのオブザーバーを作成
		ResponseObserver responseObserver = new ResponseObserver(onResult, onError);

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
								.build())
						.setInterimResults(true) // 中間結果も取得する
						.build())
				.build());

		// 5. 音声データを送信するためのコントローラーを返す
		return new AudioStreamObserver(requestObserver, speechClient);
	}

	/**
	 * Google STT APIとのストリーミングを制御するクラス。
	 * AudioServiceはこのクラスを介して音声データを送信し、ストリームを閉じる。
	 */
	public static class AudioStreamObserver {
		private final ApiStreamObserver<StreamingRecognizeRequest> requestObserver;
		private final SpeechClient speechClient;

		public AudioStreamObserver(ApiStreamObserver<StreamingRecognizeRequest> requestObserver,
				SpeechClient speechClient) {
			this.requestObserver = requestObserver;
			this.speechClient = speechClient;
		}

		public void sendAudio(byte[] audioBytes) {
			requestObserver.onNext(StreamingRecognizeRequest.newBuilder()
					.setAudioContent(ByteString.copyFrom(audioBytes))
					.build());
		}

		public void closeStream() {
			requestObserver.onCompleted();
			if (speechClient != null) {
				speechClient.close();
			}
		}
	}

	/**
	 * Google STT APIからの非同期レスポンスを処理する内部クラス。
	 */
	private static class ResponseObserver implements ApiStreamObserver<StreamingRecognizeResponse> {
		private final Consumer<String> onResult;
		private final Consumer<Throwable> onError;

		public ResponseObserver(Consumer<String> onResult, Consumer<Throwable> onError) {
			this.onResult = onResult;
			this.onError = onError;
		}

		@Override
		public void onNext(StreamingRecognizeResponse response) {
			if (response.getResultsCount() > 0) {
				StreamingRecognitionResult result = response.getResults(0);
				// is_finalがtrueの場合のみ、または中間結果も表示したい場合は常にコールバックを呼び出す
				if (result.getIsFinal()) {
					onResult.accept(result.getAlternatives(0).getTranscript());
				}
			}
		}

		@Override
		public void onError(Throwable t) {
			onError.accept(t);
		}

		@Override
		public void onCompleted() {
			// ストリームが正常に完了
		}
	}

	// --- 既存のファイルベース処理 ---
	public String recognizeFromWav(String wavFilePath) throws IOException {
		try (SpeechClient speechClient = SpeechClient.create()) {
			// 音声ファイルを読み込み
			ByteString audioBytes = ByteString.readFrom(new FileInputStream(wavFilePath));

			// 音声データ設定
			RecognitionAudio audio = RecognitionAudio.newBuilder()
					.setContent(audioBytes)
					.build();

			// 音声認識の設定（日本語、wav、リニアPCM）
			RecognitionConfig config = RecognitionConfig.newBuilder()
					.setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
					.setSampleRateHertz(16000)
					.setLanguageCode("ja-JP")
					.setEnableAutomaticPunctuation(true)
					.setModel("latest_long") // 高精度・句読点強化・長時間向け
					.build();

			// 非同期で認識を実行
			OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> future = speechClient
					.longRunningRecognizeAsync(config, audio);

			// 処理の完了を待つ
			LongRunningRecognizeResponse response = future.get();

			StringBuilder resultText = new StringBuilder();
			for (SpeechRecognitionResult result : response.getResultsList()) {
				resultText.append(result.getAlternatives(0).getTranscript());
			}
			return resultText.toString();

		} catch (InterruptedException | ExecutionException e) {
			// InterruptedExceptionはスレッドの割り込み、ExecutionExceptionは非同期タスク内での例外
			Thread.currentThread().interrupt(); // スレッドの割り込み状態を復元
			throw new IllegalStateException("音声認識の非同期処理に失敗しました。", e);
		}
	}
}