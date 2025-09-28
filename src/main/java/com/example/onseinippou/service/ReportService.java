package com.example.onseinippou.service;

import java.time.LocalDateTime;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.example.onseinippou.domain.model.report.ReportMeta;
import com.example.onseinippou.domain.model.user.User;
import com.example.onseinippou.domain.repository.ReportMetaRepository;
import com.example.onseinippou.infra.google.sheets.GoogleSheetsClient;
import com.example.onseinippou.security.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final CurrentUserProvider currentUserProvider;      // ← Security 層
    private final GoogleSheetsClient googleSheetsClient;              // ← Infra 層
    private final ReportMetaRepository reportMetaRepository;

    @Transactional
    public void sendToSheets(String text) {

        // ログインユーザー情報を取得.
        User user = currentUserProvider.getCurrentUser();
        String sheetId = user.getSheetId();

        // Sheets へ追記して行番号を取得 .
        GoogleSheetsClient.AppendResult appendResult =
        		googleSheetsClient.appendNippou(sheetId, text);

        // メタを保存.
        ReportMeta meta = ReportMeta.builder()
                .user(user)
                .sheetId(appendResult.sheetId())
                .sheetRow(appendResult.rowNumber())
                .createdAt(LocalDateTime.now())
                .build();
        reportMetaRepository.save(meta);
    }
}

