package com.example.onseinippou.common.exception;

import java.io.UncheckedIOException;
import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** I/O 系 or 外部プロセス失敗 */
    @ExceptionHandler({UncheckedIOException.class, IllegalStateException.class})
    // ① ApiError を組み立て
    // ② ResponseEntity.status(400).body(ApiError) を返却
    public ResponseEntity<ApiError> handleTechnical(RuntimeException ex, WebRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex, req);
    }

    /** バリデーション・パラメータ不足など (例示) */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, WebRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex, req);
    }

    /** 最後の砦 (想定外) */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, WebRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex, req);
    }

    /* 共通ビルダ */
    private ResponseEntity<ApiError> build(HttpStatus status, Exception ex, WebRequest req) {
        ApiError body = new ApiError(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                req.getDescription(false).replace("uri=", ""),   // "/api/upload-audio" など
                OffsetDateTime.now()
        );
        return ResponseEntity.status(status).body(body);
    }
}

