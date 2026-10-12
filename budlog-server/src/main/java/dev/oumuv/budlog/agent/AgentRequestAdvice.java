package dev.oumuv.budlog.agent;

import dev.oumuv.budlog.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keep missing required Agent dates a client error rather than a generic server error. */
@RestControllerAdvice(assignableTypes = AgentController.class)
public class AgentRequestAdvice {
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> missingParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest().body(new ApiResponse<Void>(40001, "缺少必填请求参数", null));
    }
}
