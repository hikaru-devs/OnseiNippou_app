package com.example.OnseiNippou_app.Controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.OnseiNippou_app.Service.RegisterSheetService;

@RestController
public class RegisterSheetController {
	
	@Autowired
	private RegisterSheetService registerSheetService;
	
	@PostMapping("/submit-sheet")
	public String registerSheet(@AuthenticationPrincipal OAuth2User oauth2User, 
			@RequestBody SheetUrlRequest request) {
		String email = oauth2User.getAttribute("email");
		String sheetUrl = request.getSheetUrl();
		String sheetId = extractSheetId(sheetUrl);
		
		if (sheetId == null) {
			return "Invalid sheet URL";
		}
		
		registerSheetService.register(email, sheetId);
		return "Sheet registered successfully";
	}
	
	private String extractSheetId(String url) {
		Pattern pattern = Pattern.compile("/d/([a-zA-Z0-9-_]+)");
		Matcher matcher = pattern.matcher(url);
		return matcher.find() ? matcher.group(1) : null;
	}
	
	// 内部クラスでのDTO定義
	public static class SheetUrlRequest {
		private String sheetUrl;
		public String getSheetUrl() { return sheetUrl; }
		public void setShettUrl(String sheetUrl) { this.sheetUrl = sheetUrl; }
	}

}