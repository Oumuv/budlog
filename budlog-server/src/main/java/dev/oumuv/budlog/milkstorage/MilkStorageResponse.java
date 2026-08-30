package dev.oumuv.budlog.milkstorage;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class MilkStorageResponse {

    private Long id;
    private UUID clientRequestId;
    private OffsetDateTime storedAt;
    private BigDecimal amountMl;
    private String note;
}
