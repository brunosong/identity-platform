package com.brunosong.identityplatform.auth.service.web.authorize;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.IssueAuthorizationCodeUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.IssueAuthorizationCodeCommand;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationCode;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 사람이 확인된 뒤의 마무리 - 코드를 발급해 앱 주소로 돌려보낸다.
 *
 * <p>여기 이르는 길이 둘이다. 방금 로그인 화면에서 비밀번호를 확인했거나, 쿠키의 세션이 살아
 * 있어 화면을 건너뛰었거나. <b>두 길의 끝이 같아야 한다</b> - 앱 입장에서는 어느 쪽이든
 * 주소창에 코드가 실려 돌아오는 것으로 똑같이 보여야 하기 때문이다. 그래서 이 일을 한 자리에 둔다.
 *
 * <p>받는 것이 {@link AuthenticatedSubject} 인 것도 같은 이야기다. 비밀번호로 확인했든 세션으로
 * 확인했든 결과가 같은 타입이라, 여기서는 어느 쪽인지 알 수 없다.
 */
@Component
@RequiredArgsConstructor
class AuthorizationCodeRedirect {

    private final IssueAuthorizationCodeUseCase issueAuthorizationCode;

    ModelAndView issueAndRedirect(AuthorizationRequest request, AuthenticatedSubject subject) {
        AuthorizationCode code = issueAuthorizationCode.issue(new IssueAuthorizationCodeCommand(
                request, subject.realm(), subject.principalId()));

        return new ModelAndView(redirectWith(request, code));
    }

    /**
     * 코드와 state 를 주소창에 실어 앱으로 돌려보낸다. <b>토큰은 여기 없다.</b>
     *
     * <p>303 을 쓴다. 302 도 브라우저는 GET 으로 따라가지만 그것은 관행이고, POST 뒤에 쓰라고
     * 명세에 적힌 것은 303 이다.
     *
     * <p>state 는 앱이 시작할 때 준 값을 그대로 돌려준다. 앱은 그것으로 자기가 시작한 로그인이
     * 맞는지 확인한다 - 남이 시작한 로그인의 콜백을 열게 만드는 공격을 여기서 거른다.
     */
    private static RedirectView redirectWith(AuthorizationRequest request, AuthorizationCode code) {
        UriComponentsBuilder location = UriComponentsBuilder.fromUriString(request.getRedirectUri())
                .queryParam("code", code.getCode());
        if (request.getState() != null) {
            location.queryParam("state", request.getState());
        }

        RedirectView redirect = new RedirectView(location.encode().toUriString());
        redirect.setStatusCode(HttpStatus.SEE_OTHER);
        return redirect;
    }
}
