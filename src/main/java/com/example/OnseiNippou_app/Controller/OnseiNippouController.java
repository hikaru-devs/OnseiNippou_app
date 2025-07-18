package com.example.OnseiNippou_app.Controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.OnseiNippou_app.Service.SendToSheetApiService;
import com.example.OnseiNippou_app.Service.SpeechToTextApiService;

@RestController
@RequestMapping("/api")
public class OnseiNippouController {
	
	@Autowired
	private SpeechToTextApiService speechToTextApiService;
	@Autowired
	private SendToSheetApiService sendToSheetApiService;
	
	@Value("${GOOGLE_APPLICATION_CREDENTIALS}")
	private String googleCredentialsPath;

	@PostMapping("/upload-audio")
	public ResponseEntity<TranscriptResponse> uploadAudio(@RequestParam("audio") MultipartFile file) {
		try {
			System.out.println("🔔 音声ファイル受信: " + file.getOriginalFilename());
			// 一時ファイルに保存（webm形式）
			File webmFile = Files.createTempFile("recording-", ".webm").toFile();
			file.transferTo(webmFile);
			
			//ffmpegでwav形式に変換（16kHz, mono, LINEAR16）
			File wavFile = new File(webmFile.getParent(), UUID.randomUUID() + ".wav");			
			convertWebmToWav(webmFile, wavFile);
			
			// 変換完了したかのテスト
			System.out.println("✅ 変換完了: " + wavFile.getAbsolutePath());
			System.out.println("webmファイルパス: " + webmFile.getAbsolutePath());
			
			//GoogleColud STTで文字起こし
			String transcript = speechToTextApiService.recognizeFromWav(wavFile.getAbsolutePath());
			
			// 結果を返す
			return ResponseEntity.ok().body(new TranscriptResponse(transcript));
			
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(new TranscriptResponse("エラー：" + e.getMessage()));
		}
	}
	
	@PostMapping("/submit-text")
	public ResponseEntity<String> submitText(@RequestBody Map<String, String> payload) {
		try {
			String text = payload.get("text");
			sendToSheetApiService.appendNippou(text);
			return ResponseEntity.ok("スプレッドシートに送信しました！");
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body("送信エラー： " + e.getMessage());
		}
	}
	
	private void convertWebmToWav(File input,  File output) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(
				"ffmpeg",
				"-i", input.getAbsolutePath(),
				"-ar", "16000",
				"-ac", "1",
				"-f", "wav",
				"-c:a", "pcm_s16le",
				output.getAbsolutePath()
				);
		pb.redirectErrorStream(true);
		Process process = pb.start();
		
		// ログ出力（省略可）
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.lines().forEach(System.out::println);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("ffmpegによる変換に失敗しました");
        }
    }

    // レスポンス用の内部クラス
    record TranscriptResponse(String text) {}
	
}
