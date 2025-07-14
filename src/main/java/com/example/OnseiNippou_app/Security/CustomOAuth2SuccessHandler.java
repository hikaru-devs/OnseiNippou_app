// OAuth2認証成功後のロジックを担当するクラス

package com.example.OnseiNippou_app.Security;


import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.example.OnseiNippou_app.Service.RegisterSheetService;

@Component
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {
	
	@Autowired
	private RegisterSheetService registerSheetService;
	
	@Override
	public void onAuthenticationSuccess(HttpServletRequest request,
			HttpServletResponse response,
			Authentication authentication) throws IOException {
		
		OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
		String email = (String) oauth2User.getAttributes().get("email");
		
		boolean hasSheet = registerSheetService.hasSheetId(email);
		
		if (hasSheet) {
			response.sendRedirect("/onsei-nippou-page");
		} else {
			response.sendRedirect("/register-sheet-page");
		}
	}

}
