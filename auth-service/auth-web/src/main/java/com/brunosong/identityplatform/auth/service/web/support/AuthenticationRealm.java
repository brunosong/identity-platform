package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 이 호스트가 시행하는 realm — 설정({@code authorization.realm}) 한 곳에서 읽어 웹 계층에 넘긴다.
 *
 * <p>realm 은 요청이 정할 값이 아니다. 한 호스트 프로세스는 자기 realm 만 시행하고, 토큰 발급기도
 * 같은 프로퍼티로 realm 별 키페어를 잡는다({@code RbacJwtTokenIssuer}). 그런데 로그인 컨트롤러들은
 * 각자 다른 방식으로 realm 을 정하고 있었다 — OTP 는 URL 경로(/employee/login)로, 소셜은 컨트롤러
 * 상수로. 인증하는 쪽과 발급하는 쪽이 서로 다른 걸 보고 있었으므로 둘이 어긋날 수 있었다.
 * (실제로 고객 호스트에도 직원 OTP 로그인 URL 이 떠 있어서, 직원이 그 경로로 고객 realm 토큰을 받을 수 있었다.)
 *
 * <p>설정이 없으면 부팅이 실패한다(기본값 없음). realm 을 모르는 호스트는 어차피 토큰을 발급할 수 없고,
 * 조용히 아무 realm 으로 도는 것보다 뜨지 않는 편이 낫다.
 *
 * <p>{@link Realm}(인가 정책)과 {@link SubjectType}(주체 식별자 의미)은 관심사가 달라 도메인에서 일부러
 * 분리돼 있다. 그 둘을 잇는 변환은 여기 한 군데에만 둔다.
 */
@Component
public class AuthenticationRealm {

    private final Realm realm;

    public AuthenticationRealm(@Value("${authorization.realm}") Realm realm) {
        this.realm = realm;
    }

    /** 인가(역할/권한/URL규칙) 조회에 쓰는 realm. */
    public Realm realm() {
        return realm;
    }

    /** 신원(Principal/자격증명) 조회에 쓰는 주체 유형. */
    public SubjectType subjectType() {
        return switch (realm) {
            case EMPLOYEE -> SubjectType.EMPLOYEE;
            case CUSTOMER -> SubjectType.CUSTOMER;
        };
    }
}
