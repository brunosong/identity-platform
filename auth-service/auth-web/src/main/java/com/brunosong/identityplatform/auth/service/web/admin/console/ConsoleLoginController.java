package com.brunosong.identityplatform.auth.service.web.admin.console;

import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidGrantException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * 운영 화면의 로그인이 돌아오는 자리. 앱으로 치면 {@code /login/callback} 이다.
 *
 * <p>실패는 로그인 화면이 쓰는 오류 화면에 그린다. 여기 도착하는 것은 API 호출이 아니라 브라우저다.
 */
@Controller
@Profile("local")
@RequiredArgsConstructor
public class ConsoleLoginController {

    private final ConsoleLogin consoleLogin;

    @GetMapping(ConsoleLogin.CALLBACK_PATH)
    public ModelAndView callback(@RequestParam(required = false) String code,
                                 @RequestParam(required = false) String state,
                                 HttpServletRequest request) {
        try {
            return new ModelAndView("redirect:" + consoleLogin.finish(request, code, state));
        } catch (ConsoleLogin.ConsoleLoginException | InvalidGrantException e) {
            ModelAndView error = new ModelAndView("oauth/error");
            error.addObject("reason", e.getMessage());
            error.setStatus(HttpStatus.BAD_REQUEST);
            return error;
        }
    }
}
