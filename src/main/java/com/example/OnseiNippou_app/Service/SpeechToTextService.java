package com.example.OnseiNippou_app.Service;


import java.io.FileInputStream;
import java.io.IOException;

import org.springframework.stereotype.Service;

import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.protobuf.ByteString;

@Service
public class SpeechToTextService {
	
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
					.build();
			
			//認識を実行
			RecognizeResponse response = speechClient.recognize(config, audio);
			StringBuilder resultText = new StringBuilder();
			for (SpeechRecognitionResult result : response.getResultsList()) {
				resultText.append(result.getAlternatives(0).getTranscript());
			}
			return resultText.toString();
		}
	}
}