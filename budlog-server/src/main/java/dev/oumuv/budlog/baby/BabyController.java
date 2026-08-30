package dev.oumuv.budlog.baby;

import dev.oumuv.budlog.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/baby")
public class BabyController {

    private final BabyService service;

    public BabyController(BabyService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<BabyResponse> get() {
        return ApiResponse.success(service.getCurrent());
    }

    @PutMapping
    public ApiResponse<BabyResponse> save(@Valid @RequestBody BabyRequest request) {
        return ApiResponse.success(service.save(request));
    }
}

