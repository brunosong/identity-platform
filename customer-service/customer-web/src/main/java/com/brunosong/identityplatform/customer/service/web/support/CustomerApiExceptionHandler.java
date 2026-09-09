package com.brunosong.identityplatform.customer.service.web.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 공통 예외 매핑. auth-service 의 그것과 같은 모양이다 — 서비스가 달라도 실패를 알리는 방식은 같아야
 * 호출자가 한 가지 규칙으로 다룰 수 있다.
 *
 * <p>서버 오류의 원인 메시지는 응답에 싣지 않는다. 자세한 내용은 로그에만 남긴다.
 *
 * <p><b>401/403 은 여기 없다.</b> 인증 실패는 컨트롤러에 닿기 전에 시큐리티 필터가 끝내고,
 * 표준대로 {@code WWW-Authenticate: Bearer} 헤더를 붙여 응답한다. 본문은 비어 있다 —
 * 왜 실패했는지 자세히 알려주는 것은 공격자에게만 이득이다.
 */
@RestControllerAdvice
@Slf4j
public class CustomerApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> onIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidationFailed(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((left, right) -> left + ", " + right)
                .orElse("요청 값이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(new ApiError(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception e) {
        log.error("customer API 처리 실패", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("요청을 처리하지 못했습니다."));
    }
}
