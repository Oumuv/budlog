package dev.oumuv.budlog.whitenoise;

import dev.oumuv.budlog.access.AccessPasswordService;
import dev.oumuv.budlog.common.ApiResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/white-noise")
public class WhiteNoiseController {

    private final WhiteNoiseService service;
    private final AccessPasswordService passwordService;

    public WhiteNoiseController(WhiteNoiseService service, AccessPasswordService passwordService) {
        this.service = service;
        this.passwordService = passwordService;
    }

    @GetMapping("/playlists")
    public ResponseEntity<ApiResponse<List<WhiteNoisePlaylistResponse>>> list(HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        String password = request.getHeader(AccessPasswordService.HEADER_NAME);
        String cookie = passwordService.matches(password)
                ? passwordService.createMediaSessionCookie(request)
                : null;
        if (cookie != null) {
            response.header(HttpHeaders.SET_COOKIE, cookie);
        }
        return response.body(ApiResponse.success(service.listPlaylists()));
    }

    @GetMapping("/tracks/{id}/stream")
    public ResponseEntity<?> stream(
            @PathVariable String id,
            HttpServletRequest request,
            WebRequest webRequest) throws IOException {
        WhiteNoiseTrackResource track = service.requireTrack(id);
        if (webRequest.checkNotModified(track.getEtag(), track.getLastModified())) {
            return ResponseEntity.status(304).build();
        }
        Resource resource = track.asResource();
        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        boolean honorRange = matchesIfRange(request.getHeader(HttpHeaders.IF_RANGE), track);
        if (honorRange && rangeHeader != null && !rangeHeader.trim().isEmpty()) {
            try {
                HttpRange.toResourceRegions(HttpRange.parseRanges(rangeHeader), resource);
            } catch (IllegalArgumentException exception) {
                return ResponseEntity.status(416)
                        .contentType(MediaType.valueOf("audio/mpeg"))
                        .eTag(track.getEtag())
                        .lastModified(track.getLastModified())
                        .cacheControl(CacheControl.noCache().cachePrivate().mustRevalidate())
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + track.getSizeBytes())
                        .build();
            }
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.valueOf("audio/mpeg"))
                .eTag(track.getEtag())
                .lastModified(track.getLastModified())
                .cacheControl(CacheControl.noCache().cachePrivate().mustRevalidate())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(track.getName() + ".mp3", StandardCharsets.UTF_8)
                        .build().toString());
        if (!honorRange) {
            return response
                    .contentLength(track.getSizeBytes())
                    .body(new InputStreamResource(resource.getInputStream()));
        }
        return response.body(resource);
    }

    private boolean matchesIfRange(String ifRange, WhiteNoiseTrackResource track) {
        if (ifRange == null || ifRange.trim().isEmpty()) {
            return true;
        }
        String value = ifRange.trim();
        if (value.startsWith("\"") || value.startsWith("W/")) {
            return !value.startsWith("W/") && value.equals(track.getEtag());
        }
        try {
            long requestedTime = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli();
            return (track.getLastModified() / 1000) <= (requestedTime / 1000);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

}
