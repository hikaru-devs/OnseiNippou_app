package com.example.onseinippou.service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.onseinippou.infra.stt.SpeechToTextClient;

import lombok.RequiredArgsConstructor;

/**
 * googleSpeechToTextAPIで文字起こしを実行するクラス.
 */
@Service
@RequiredArgsConstructor
public class AudioService {
	
	/**
	 * googleSpeechToTextの仕様や取り決めをまとめた設定クラス.
	 */
	private final SpeechToTextClient speechToTextClient;
	
	/**
	 * @param file 受信したレコードファイル.
	 * @return googleSpeechToTextで変換されたテキスト.
	 */
	public String transcribe(MultipartFile file) {
		try {
			System.out.println("🔔 音声ファイル受信: " + file.getOriginalFilename());
			// 一時WEBMファイルに保存
			File webm =Files.createTempFile("recoding-", ".webm").toFile();
			file.transferTo(webm);
			System.out.println("一時WEBMファイルパス: " + webm.getAbsolutePath());
			
			// WAV へ変換
			File wav = new File(webm.getParent(), UUID.randomUUID() + ".wav");
			convertWebmToWav(webm, wav);
			System.out.println("✅ 変換完了: " + wav.getAbsolutePath());
			
			// Google STT を呼び出し
			return speechToTextClient.recognizeFromWav(wav.getAbsolutePath());
		} catch (InterruptedException ie) {
			// --- 割り込みを復元してからラップ ---
			Thread.currentThread().interrupt();
			throw new IllegalStateException("スレッドが中断されました。", ie);
			
		} catch (IOException ioe) {
			// --- I/O 系は UncheckedIOException でラップ ---
			throw new UncheckedIOException("音声ファイル処理に失敗", ioe);
		}
	}
	
    /**
     * ffmpegを使って入力WEBMファイルをWAVへ変換する.
     *
     * @param input  入力のWEBMファイル
     * @param output 出力先のWAVファイル
     * @throws IOException          ファイルI/Oエラー
     * @throws InterruptedException プロセス実行中の中断エラー
     */
	private void convertWebmToWav(File input, File output)
			throws IOException, InterruptedException {
		
		ProcessBuilder pb = new ProcessBuilder(
				"ffmpeg", "-i", input.getAbsolutePath(),
				"-ar", "16000", "-ac", "1", "-f", "wav", "-c:a", "pcm_s16le",
				output.getAbsolutePath()
		).redirectErrorStream(true);
		
		Process process = pb.start();
		
		// ストリームを必ず消費しないと ffmpeg がハングする場合がある
		try (var in = process.getInputStream()) { in.transferTo(OutputStream.nullOutputStream()); }
		
		int exit = process.waitFor();
		if (exit != 0) {
			throw new IllegalStateException("ffmpeg 変換失敗（exit=" + exit + ')');
		}
	}
}




