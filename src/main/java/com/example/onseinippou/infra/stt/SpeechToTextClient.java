package com.example.onseinippou.infra.stt;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.springframework.stereotype.Service;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.speech.v1.LongRunningRecognizeMetadata;
import com.google.cloud.speech.v1.LongRunningRecognizeResponse;
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.protobuf.ByteString;





@Service
public class SpeechToTextClient {
	
	public String recognizeFromWav(String wavFilePath) throws IOException {
		try (SpeechClient speechClient = SpeechClient.create()) {
			//音声ファイルを読み込み
			ByteString audioBytes = ByteString.readFrom(new FileInputStream(wavFilePath));
			
			//音声データ設定
			RecognitionAudio audio = RecognitionAudio.newBuilder()
					.setContent(audioBytes)
					.build();
			
			//音声認識の設定（日本語、wav、リニアPCM）
			RecognitionConfig config = RecognitionConfig.newBuilder()
					.setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
					.setSampleRateHertz(16000)
					.setLanguageCode("ja-JP")
					.setEnableAutomaticPunctuation(true)
					.setModel("latest_long") // 高精度・句読点強化・長時間向け
					.build();
			
			// 非同期で認識を実行
			OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> future =
			              speechClient.longRunningRecognizeAsync(config, audio);
			
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