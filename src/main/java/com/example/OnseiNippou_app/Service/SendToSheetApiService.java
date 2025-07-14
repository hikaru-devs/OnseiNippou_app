package com.example.OnseiNippou_app.Service;

import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

@Service
public class SendToSheetApiService {
	
	@Value("${GOOGLE_APPLICATION_CREDENTIALS}")
	private String credentialsPath;
	
	private static final String APPLICATION_NAME = "OnseiNippou_app";
	private static final String SPREADSHEET_ID = "14TlJW7inQHIc3fse1D7IFongyXqyDdS8mkJAMMWd9GQ";
	private static final String RANGE = "シート2!A:C";
	
	public void appendNippou(String text) throws Exception {
		GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath))
				.createScoped(Collections.singletonList("https://www.googleapis.com/auth/spreadsheets"));
		
		Sheets sheetsService = new Sheets.Builder(
				GoogleNetHttpTransport.newTrustedTransport(),
				GsonFactory.getDefaultInstance(),
				new HttpCredentialsAdapter(credentials)
		).setApplicationName(APPLICATION_NAME).build();
		
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		
		List<List<Object>> values = List.of(
				List.of(timestamp, text)
		);
		
		ValueRange body = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values()
                .append(SPREADSHEET_ID, RANGE, body)
                .setValueInputOption("RAW")
                .execute();
				
	}

}
