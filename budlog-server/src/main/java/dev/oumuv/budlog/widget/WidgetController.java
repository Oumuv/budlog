package dev.oumuv.budlog.widget;

import dev.oumuv.budlog.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/widget")
public class WidgetController {

    private final WidgetService widgetService;

    public WidgetController(WidgetService widgetService) {
        this.widgetService = widgetService;
    }

    @GetMapping("/summary")
    public ApiResponse<WidgetSummaryResponse> summary() {
        return ApiResponse.success(widgetService.getSummary());
    }
}
