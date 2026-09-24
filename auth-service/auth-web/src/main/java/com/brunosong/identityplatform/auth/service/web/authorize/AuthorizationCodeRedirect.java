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

import java.util.function.UnaryOperator;

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

        return new ModelAndView(redirectWith(request,
                builder -> builder.queryParam("code", code.getCode())));
    }

    /**
     * 화면을 띄우지 말라는 요청({@code prompt=none})인데 세션이 없다 - 앱으로 그렇게 돌려보낸다.
     *
     * <p><b>화면 대신 값으로 답하는 것이 요점이다.</b> 조용히 시도해 보는 쪽은 "되면 좋고 아니면
     * 말고" 를 원하는데, 로그인 화면을 그려버리면 "아니면" 을 표현할 방법이 없다. 상품을 구경하러
     * 온 사람이 로그인 화면에 갇히거나, 보이지 않는 프레임 안에서 답이 영영 오지 않는다.
     *
     * <p>오류 이름은 OIDC 가 정한 {@code login_required} 다. 우리가 지은 단어를 쓰면 표준
     * 라이브러리가 못 알아듣는다.
     *
     * <p>이 자리에서 앱 주소로 돌려보내도 되는 이유는 <b>호출한 쪽이 이미 요청을 검증했기</b>
     * 때문이다. 등록되지 않은 주소였다면 여기까지 오지 못한다.
     */
    ModelAndView loginRequired(AuthorizationRequest request) {
        return new ModelAndView(redirectWith(request,
                builder -> builder.queryParam("error", "login_required")));
    }

    /**
     * 결과를 주소창에 실어 앱으로 돌려보낸다. <b>토큰은 여기 없다.</b>
     *
     * <p>303 을 쓴다. 302 도 브라우저는 GET 으로 따라가지만 그것은 관행이고, POST 뒤에 쓰라고
     * 명세에 적힌 것은 303 이다.
     *
     * <p>state 는 앱이 시작할 때 준 값을 그대로 돌려준다. 앱은 그것으로 자기가 시작한 로그인이
     * 맞는지 확인한다 - 남이 시작한 로그인의 콜백을 열게 만드는 공격을 여기서 거른다.
     * <b>성공이든 실패든 함께 실어야 한다</b> - 실패에서 빠뜨리면 앱이 그 응답을 자기 것으로
     * 확인할 수 없다.
     */
    private static RedirectView redirectWith(AuthorizationRequest request,
                                             UnaryOperator<UriComponentsBuilder> outcome) {
        UriComponentsBuilder location = outcome.apply(
                UriComponentsBuilder.fromUriString(request.getRedirectUri()));
        if (request.getState() != null) {
            location.queryParam("state", request.getState());
        }

        RedirectView redirect = new RedirectView(location.encode().toUriString());
        redirect.setStatusCode(HttpStatus.SEE_OTHER);
        return redirect;
    }
}
