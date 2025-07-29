package com.example.onseinippou.infra.google.sheets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import jakarta.annotation.Nullable;

import org.springframework.stereotype.Service;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

import lombok.RequiredArgsConstructor;


//---------------------------------------------------------------------
// スプレッドシートに内容を送信するクラス
//---------------------------------------------------------------------

@Service
@RequiredArgsConstructor
public class GoogleSheetsClient {
	
    /** GoogleClientsConfig からコンストラクタ Injection される Sheets クライアント */
    private final Sheets sheets;

    /** GoogleClientsConfig からコンストラクタ Injection される Drive クライアント */
    // ファイル操作が必要なら利用可能
    private final Drive drive;
    
	
	 /** 日報を書き込むレンジ（シート1!A:C） */
	private static final String RANGE = "シート1!A:C";
	
	private static final String SHEET_MIME = "application/vnd.google-apps.spreadsheet";
	
	
	
	// -----------------------------------------------------------------
    //  スプレッドシートへデータ行を追加してsheetIdと行番号を返す
    // -----------------------------------------------------------------
	
	/* --- 追記結果を返す DTO --- */
	public record AppendResult(String sheetId, int rowNumber) {}
    /**
     * @param spreadsheetId 対象スプレッドシートの ID
     * @param text          ユーザーが入力した日報テキスト
     */

	public AppendResult appendNippou(String sheetId, String text) {
		
		try {

			/* タイムスタンプ生成 */
	        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
	        
	        /* シートへ追記する情報をList化してSheetsApiのbodyにつめる */
	        List<List<Object>> values = List.of(List.of(timestamp, text));

	        ValueRange body = new ValueRange().setValues(values);
	        /* Sheets API 呼び出し */	        
	        AppendValuesResponse res = sheets.spreadsheets().values()
	              .append(sheetId, RANGE, body)
	              .setValueInputOption("USER_ENTERED")
	              .execute();
	        
	        /* 例: updatedRange = "Sheet1!A12:B12" */
	        String updatedRange = res.getUpdates().getUpdatedRange();
	        int row = Integer.parseInt(updatedRange.replaceAll(".*!(?:[A-Z]+)(\\d+):.*", "$1"));
	        
	        return new AppendResult(sheetId, row);
	        
		} catch (IOException ioe) {
			throw new UncheckedIOException("スプレッドシート書き込み失敗", ioe);
			
		} catch (Exception e) {
			throw new IllegalStateException("Google API 呼び出し失敗", e);
		}
 
    }
	

	
	
	
	 // ---------------------------------------------------------------------
	 //  サービスアカウント B オーナーでスプレッドシートを作成するユーティリティ。
	 //  会社のWorkSpaceの共有フォルダにアクセスするため、会社関係者かどうか判別するロジックが必要になった。
	 //  会社関係者マスタがない、会社のworkSpaceに会社関係者が全員加入しているか分からない以上、現状実装すべき機能ではない。
	 // ---------------------------------------------------------------------

    /**
     * @param title        シート名
     * @param parentFolder フォルダ ID（null ならマイドライブ直下）
     * @param sharedUsers  初期共有するユーザーのメール一覧（null/空 = 共有なし）
     * @return             WebViewLink（ユーザーへ返す URL）
     */
	public String createownedSpreadsheet(String title,
    		@Nullable String parentFolder,
    		@Nullable List<String> sharedUsers) throws IOException {
        // -----------------------------------------------------------------
        //  スプレッドシートファイルを Drive API で作成 
        // -----------------------------------------------------------------    	
    	/* スプレッドシートのメタデータを生成（ファイル名・種類） */
    	File meta = new File();
    	meta.setName(title);
    	meta.setMimeType(SHEET_MIME);
    	/* 親フォルダIDが指定されていれば、フォルダに格納 */
    	if (parentFolder != null && !parentFolder.isBlank()) {
    		meta.setParents(List.of(parentFolder));
    	}
    	/* Drive API でファイル（スプレッドシート）を新規作成 */
    	File created = drive.files()
    			.create(meta)
    			.setFields("id, webViewLink")
    			.execute();
    	/* 作成したファイルのIDを取得 */
    	String fileId = created.getId();
    	
        // -----------------------------------------------------------------
        //  必要ならファイルにメールアドレス追加
        // -----------------------------------------------------------------
    	if (sharedUsers != null && !sharedUsers.isEmpty()) {
    		for (String email : sharedUsers) {
    			Permission permission = new Permission()
    					.setType("user")
    					.setRole("writer")
    					.setEmailAddress(email);
		        // -----------------------------------------------------------------
		        // 既に同じEmailが共有されている場合は409 Conflictが返るので握りつぶす 
		        // -----------------------------------------------------------------
    			try {
    				/* 対象のスプレッドシートにEmailを追加。追加したEmailアドレス宛てに通知を送信しない */
        			drive.permissions().create(fileId, permission).setSendNotificationEmail(false)
        			.execute();
    			} catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
    				if (e.getStatusCode() == 409) {
    					/* 409: Permission already exists → 無視してOK */
    				} else {
    					/* それ以外は再スロー */
    					throw e;
    				}
    			}
    			
    		}
    	}
    	return created.getWebViewLink(); // ブラウザで開ける URL
    
    }

}


