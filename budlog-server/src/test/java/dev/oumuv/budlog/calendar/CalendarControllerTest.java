package dev.oumuv.budlog.calendar;

import dev.oumuv.budlog.common.BusinessException;
import dev.oumuv.budlog.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarControllerTest {
    private final CalendarService service = mock(CalendarService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CalendarController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private final LocalDate from = LocalDate.parse("2026-08-31");
    private final LocalDate to = LocalDate.parse("2026-10-12");

    @Test
    void acceptsDateOnlyParametersUsedByTheMonthGrid() throws Exception {
        when(service.get(from, to)).thenReturn(new CalendarResponse(from, to, "Asia/Shanghai", Collections.emptyList()));
        mvc.perform(get("/api/v1/calendar").param("from", "2026-08-31").param("to", "2026-10-12"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.data.items").isEmpty());
        verify(service).get(from, to);
    }

    @Test
    void malformedDatesReturnTheExistingFormatError() throws Exception {
        mvc.perform(get("/api/v1/calendar").param("from", "invalid").param("to", "2026-10-12"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void missingDatesReachRangeValidationInsteadOfBecomingServerErrors() throws Exception {
        when(service.get(null, to)).thenThrow(BusinessException.validation("日期范围不能为空"));
        mvc.perform(get("/api/v1/calendar").param("to", "2026-10-12"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
    }
}
