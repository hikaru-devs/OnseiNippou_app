// application/dto/report/ReportRequest.java
package com.example.onseinippou.application.dto.report;

import jakarta.validation.constraints.NotBlank;

public record ReportRequest(
        @NotBlank String text
) {}
