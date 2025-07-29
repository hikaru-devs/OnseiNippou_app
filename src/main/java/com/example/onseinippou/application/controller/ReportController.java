package com.example.onseinippou.application.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.onseinippou.application.dto.report.ReportRequest;
import com.example.onseinippou.service.AudioService;
import com.example.onseinippou.service.ReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
	
	private final AudioService audioService;
	private final ReportService reportService;
	
	/** 音声ファイルを受け取りテキスト化 */
	@PostMapping("/audio-transcribe")
	public ResponseEntity<TranscriptResponse> audioTranscribe(@RequestParam("audio") MultipartFile file) {
		String transcript = audioService.transcribe(file);
		return ResponseEntity.ok().body(new TranscriptResponse(transcript));
	}
	
	/** テキストをスプレッドシートへ送信 */
	@PostMapping("/submit-report")
	public ResponseEntity<String> submitReport(@RequestBody @Valid ReportRequest reportRequest) {
		reportService.sendToSheets(reportRequest.text());
		return ResponseEntity.ok("スプレッドシートに送信しました！");
	}
	
	/* 応答 DTO */
    public record TranscriptResponse(String text) {}
}
