package dev.oumuv.budlog.setting;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SettingResponse {

    private Integer defaultFeedingIntervalMin;
    private String feedingIntervalAnchor;
    private Boolean reminderSoundEnabled;
    private Boolean reminderVibrateEnabled;
}

