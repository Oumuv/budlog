package dev.oumuv.budlog.setting;

import dev.oumuv.budlog.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingController {

    private final SettingService service;

    public SettingController(SettingService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<SettingResponse> get() {
        return ApiResponse.success(service.get());
    }

    @PutMapping
    public ApiResponse<SettingResponse> update(@Valid @RequestBody SettingRequest request) {
        return ApiResponse.success(service.update(request));
    }
}

