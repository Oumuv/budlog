package dev.oumuv.budlog.diaper;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class DiaperResponse {

    private Long id;
    private UUID clientRequestId;
    private DiaperType recordType;
    private OffsetDateTime recordTime;
    private String note;
}

