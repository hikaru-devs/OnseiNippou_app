package com.example.onseinippou.domain.model.report;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.example.onseinippou.domain.model.user.User;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity


@Table(name = "reports_meta")
@Getter @Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ReportMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    /* シートID */
    @Column(nullable = false)
    private String sheetId;
    
    /* 書き込んだ行番号 */
    @Column(nullable = false)
    private Integer sheetRow;
    private LocalDateTime createdAt;
}


/**
 * fetch = FetchType.LAZY：関連先の User は実際にアクセスするまで DB から読み込まない（パフォーマンス最適化）
 * optional = false：この関連は必ず存在しなければならない（user が null だと例外）
 */





