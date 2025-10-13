package com.example.onseinippou.application.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.onseinippou.application.dto.report.ReportRequest;
import com.example.onseinippou.service.ReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

	private final ReportService reportService;

	/** テキストをスプレッドシートへ送信 */
	@PostMapping("/submit-report")
	public ResponseEntity<String> submitReport(@RequestBody @Valid ReportRequest reportRequest) {
		reportService.sendToSheets(reportRequest.text());
		return ResponseEntity.ok("スプレッドシートに送信しました！");
	}

	/* 応答 DTO */
	public record TranscriptResponse(String text) {
	}
}
