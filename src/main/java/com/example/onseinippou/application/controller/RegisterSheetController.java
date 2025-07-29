package com.example.onseinippou.application.controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.onseinippou.service.RegisterSheetService;

@RestController
@RequestMapping("/api")
public class RegisterSheetController {
	
	@Autowired
	private RegisterSheetService registerSheetService;
	
	
	// 内部クラスでのDTO定義
	public static class SheetUrlRequest {
		private String sheetUrl;
		public String getSheetUrl() { return sheetUrl; }
		public void setShettUrl(String sheetUrl) { this.sheetUrl = sheetUrl; }
	}

	@PostMapping("/submit-sheet")
	public ResponseEntity<String> registerSheet(@RequestBody SheetUrlRequest request) {
		String sheetUrl = request.getSheetUrl();
		String sheetId = extractSheetId(sheetUrl);

		registerSheetService.registerSheetId(sheetId);
		return ResponseEntity.ok("シート登録が完了しました！");
	}
	
	private String extractSheetId(String url) {
		Pattern pattern = Pattern.compile("/d/([a-zA-Z0-9-_]+)");
		Matcher matcher = pattern.matcher(url);
		return matcher.find() ? matcher.group(1) : null;
	}
	

}