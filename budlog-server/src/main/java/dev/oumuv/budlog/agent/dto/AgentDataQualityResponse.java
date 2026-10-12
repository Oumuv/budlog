package dev.oumuv.budlog.agent.dto;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class AgentDataQualityResponse {
    private final boolean completenessVerified = false;
    private final List<String> missingRecordCategories = Collections.emptyList();
    private final List<String> notes = Collections.singletonList("仅表示系统已录入内容，不能保证家长没有漏记；无记录不等于没有发生");
}
