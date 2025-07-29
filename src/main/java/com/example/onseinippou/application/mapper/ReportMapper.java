package com.example.onseinippou.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.example.onseinippou.application.dto.report.ReportResponse;
import com.example.onseinippou.domain.model.report.ReportMeta;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ReportMapper {
    ReportResponse toResponse(ReportMeta entity);
}
