package dev.oumuv.budlog.event;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class EventResponse {

    private Long id;
    private UUID clientRequestId;
    private EventType eventType;
    private String title;
    private OffsetDateTime occurredAt;
    private String note;
}

