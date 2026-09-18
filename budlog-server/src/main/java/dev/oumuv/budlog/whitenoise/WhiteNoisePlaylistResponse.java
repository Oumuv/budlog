package dev.oumuv.budlog.whitenoise;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WhiteNoisePlaylistResponse {

    private String id;
    private String name;
    private List<WhiteNoiseTrackResponse> tracks;
}
