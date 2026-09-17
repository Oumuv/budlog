package dev.oumuv.budlog.whitenoise;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WhiteNoiseTrackResponse {

    private String id;
    private String name;
    private String format;
    private long sizeBytes;
    private String streamUrl;
}
