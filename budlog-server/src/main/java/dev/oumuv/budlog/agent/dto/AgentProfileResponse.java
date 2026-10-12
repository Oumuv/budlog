package dev.oumuv.budlog.agent.dto;

import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
public class AgentProfileResponse {
    private final String name;
    private final OffsetDateTime birthTime;
    private final String timezone;
    private final long ageDaysElapsed;

    public AgentProfileResponse(String name, OffsetDateTime birthTime, String timezone, long ageDaysElapsed) {
        this.name = name;
        this.birthTime = birthTime;
        this.timezone = timezone;
        this.ageDaysElapsed = ageDaysElapsed;
    }
}
