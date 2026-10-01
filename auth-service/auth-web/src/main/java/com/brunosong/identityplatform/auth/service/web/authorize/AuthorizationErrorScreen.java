package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidAuthorizationRequestException;
import com.brunosong.identityplatform.auth.service.web.broker.GoogleBrokerController;
import com.brunosong.identityplatform.auth.service.web.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * 로그인, 가입, 로그아웃 화면에서 받아줄 수 없는 요청을 우리 오류 화면으로 끝낸다.
 *
 * <p>여기 도착하는 것은 API 호출이 아니라 브라우저다. JSON 이 아니라 사람이 읽을 화면을 준다. 그리고
 * <b>돌려보내지 않는다.</b> 이 단계에서는 적혀 온 돌아갈 주소를 아직 믿을 수 없다.
 *
 * <p>적용 대상을 화면 컨트롤러로 좁힌다. {@code IllegalArgumentException} 은 여기저기서 던지므로, 범위를
 * 넓히면 JSON 을 기대하는 API 의 실패까지 화면으로 바뀐다. 그쪽은 {@code AuthApiExceptionHandler} 가
 * 맡는다. 같은 예외를 두 처리기가 다 받을 수 있어 이쪽을 먼저 보게 순서를 앞에 둔다.
 *
 * <p>컨트롤러 안에서 잡아 폼을 다시 그리는 실패(틀린 비밀번호, 틀린 인증번호)는 여기까지 오지 않는다.
 */
@ControllerAdvice(assignableTypes = {
        AuthorizationEndpointController.class,
        LoginSubmissionController.class,
        OtpLoginController.class,
        RegistrationController.class,
        SignUpController.class,
        LogoutEndpointController.class,
        GoogleBrokerController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthorizationErrorScreen {

    /** 모르는 realm, 그 realm 에서 열지 않는 기능. */
    @ExceptionHandler(NotFoundException.class)
    public ModelAndView notFound(NotFoundException e) {
        return of(e.getMessage(), HttpStatus.NOT_FOUND);
    }

    /** 등록되지 않은 앱이나 주소, PKCE 가 없는 요청, 모르는 prompt. */
    @ExceptionHandler({InvalidAuthorizationRequestException.class, IllegalArgumentException.class})
    public ModelAndView badRequest(RuntimeException e) {
        return of(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /** 컨트롤러가 직접 오류 화면으로 끝낼 때. */
    public static ModelAndView of(String reason, HttpStatus status) {
        ModelAndView screen = new ModelAndView("oauth/error").addObject("reason", reason);
        screen.setStatus(status);
        return screen;
    }
}
