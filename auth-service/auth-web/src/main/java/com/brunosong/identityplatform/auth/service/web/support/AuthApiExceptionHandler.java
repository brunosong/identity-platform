package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.application.authorization.exception.PermissionAlreadyExistsException;
import com.brunosong.identityplatform.auth.service.application.authorization.exception.RoleAlreadyExistsException;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * auth 웹 어댑터 공통 예외 매핑. 컨트롤러마다 흩어져 있던 핸들러를 한곳으로 모은다.
 *
 * <p>예전에는 컨트롤러별로 같은 핸들러가 복사돼 있었고, 핸들러가 없는 컨트롤러는 같은 실패를 500 으로
 * 냈다. 또 한 컨트롤러는 모든 예외를 200 + {@code status:error} 로 감쌌다 — 호출자와 모니터링 어느
 * 쪽도 실패를 알 수 없었다. 매핑을 한 자리에 두면 그 어긋남이 생기지 않는다.
 *
 * <p>서버 오류의 원인 메시지는 응답에 싣지 않는다. 스택이나 SQL 문구가 그대로 나가면 구조가 노출된다.
 * 자세한 내용은 로그에만 남긴다.
 */
@RestControllerAdvice(basePackages = "com.brunosong.identityplatform.auth.service.web")
@Slf4j
public class AuthApiExceptionHandler {

    /** 자격증명 실패 — 아이디 미존재/비번 불일치는 같은 메시지로 나간다(계정 열거 방지). */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiError> onAuthenticationFailed(AuthenticationFailedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(e.getMessage()));
    }

    /**
     * 토큰이 없거나 무효. 어떤 방식으로 다시 와야 하는지 {@code WWW-Authenticate} 로 알린다(RFC 6750 3절).
     * 이 헤더가 없는 401 은 명세상 401 이 아니다.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> onUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(new ApiError(e.getMessage()));
    }

    /** 같은 코드가 이미 있다. 요청이 틀린 게 아니라 지금 상태와 부딪힌 것이라 400 이 아니라 409 다. */
    @ExceptionHandler({RoleAlreadyExistsException.class, PermissionAlreadyExistsException.class})
    public ResponseEntity<ApiError> onConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> onForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(AuthorizationNotFoundException.class)
    public ResponseEntity<ApiError> onNotFound(AuthorizationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
    }

    /** 없는 경로/realm — 그 realm 에서 지원하지 않는 로그인 방식도 여기로 온다. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> onNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
    }

    /** 잘못된 입력값 — 도메인 불변식 위반과 응용 계층의 인자 검증이 여기로 온다. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> onIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
    }

    /** 요청 본문 검증 실패(@Valid). 어느 필드가 왜 틀렸는지까지 준다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidationFailed(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((left, right) -> left + ", " + right)
                .orElse("요청 값이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(new ApiError(message));
    }

    /** 파라미터 타입 불일치 — 예를 들어 realm 에 정의되지 않은 값이 오면 500 이 아니라 400 이다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> onTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("요청 파라미터 값이 올바르지 않습니다: " + e.getName()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception e) {
        log.error("auth API 처리 실패", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("요청을 처리하지 못했습니다."));
    }
}
