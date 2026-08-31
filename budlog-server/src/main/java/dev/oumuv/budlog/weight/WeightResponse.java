package dev.oumuv.budlog.weight;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class WeightResponse {

    private Long id;
    private UUID clientRequestId;
    private OffsetDateTime measuredAt;
    private LocalDate recordDate;
    private BigDecimal weightKg;
    private String note;
}

