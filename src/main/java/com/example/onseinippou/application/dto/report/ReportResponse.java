// application/dto/report/ReportResponse.java
package com.example.onseinippou.application.dto.report;

import java.time.LocalDateTime;

public record ReportResponse(
        Long id,
        Long userId,
        String text,
        LocalDateTime createdAt
) {}
