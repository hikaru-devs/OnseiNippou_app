package com.example.onseinippou.config;

import java.io.IOException;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.sheets.v4.Sheets;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;  

@Configuration
public class GoogleClientsConfig {

    /** Cloud Console で登録しているアプリ名（任意） */
    private static final String APP_NAME = "OnseiNippou_app";

    /** シート書き込み＋ファイル操作に必要な最小スコープ */
    private static final List<String> SCOPES = List.of(
            "https://www.googleapis.com/auth/drive",
            "https://www.googleapis.com/auth/spreadsheets"
    );

    // ---------------------------------------------------------------------
    // 共通 Credential
    // ---------------------------------------------------------------------

    /**
     * Application Default Credentials を取得し、
     * Drive・Sheets 両方のスコープでラップして返す。
     * Spring が DI コンテナに 1 つだけ保持する。
     */
    @Bean
    public HttpRequestInitializer googleCredential() throws IOException {
        GoogleCredentials cred = GoogleCredentials.getApplicationDefault()
                .createScoped(SCOPES);
        return new HttpCredentialsAdapter(cred);
    }


    // ---------------------------------------------------------------------
    // Sheets クライアント
    // ---------------------------------------------------------------------

    /**
     * Sheets API v4 のクライアントを生成して Bean 登録。
     * controller / service から@Autowiredで注入して利用する。
     */
    @Bean
    public Sheets sheets(HttpRequestInitializer cred) throws Exception {
        return new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    cred)
                .setApplicationName(APP_NAME)
                .build();
    }

    // ---------------------------------------------------------------------
    // Drive クライアント
    // ---------------------------------------------------------------------

    /**
     * Drive API v3 のクライアントを生成し Bean 登録。
     * ファイル作成・共有権限付与などに利用する。
     */
    @Bean
    public Drive drive(HttpRequestInitializer cred) throws Exception {
        return new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    cred)
                .setApplicationName(APP_NAME)
                .build();
    }
}