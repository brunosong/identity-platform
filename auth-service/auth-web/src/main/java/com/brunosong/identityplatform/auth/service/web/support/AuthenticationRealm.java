package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.application.identity.SubjectRealm;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 요청 경로의 realm 세그먼트를 {@link Realm} 으로 옮긴다 — {@code /api/auth/realms/{realm}/...}.
 *
 * <p>이 서비스는 두 realm 을 모두 담당하므로 realm 을 설정에서 알 수 없다. 전에는 프로세스마다
 * {@code authorization.realm} 이 하나 박혀 있었고 요청은 realm 을 말하지 않았다. 이제 요청이 지목한다.
 *
 * <p><b>realm 은 비밀이 아니라 어느 서랍을 열지 고르는 값이다.</b> 아무나 employee 를 지목할 수 있지만,
 * 그 서랍에 자기 계정이 없으면 로그인은 실패한다 — 자격증명 조회가 모두 주체 유형으로 좁혀져 있기
 * 때문이다. 그 전제가 깨지면(전역 조회로 되돌아가면) 이 설계도 함께 깨진다.
 *
 * <p>발급된 토큰의 realm 은 이 값이 아니라 인증된 Principal 에서 나온다. 그래서 요청이 realm 을
 * 잘못 지목해도 남의 realm 토큰이 나가지 않는다 — 애초에 인증이 성립하지 않는다.
 *
 * <p>모르는 값은 404 다. 그런 realm 은 이 서비스에 없다.
 */
@Component
public class AuthenticationRealm {

    /** 경로에 적힌 realm. 대소문자는 가리지 않는다(URL 은 소문자가 자연스럽다). */
    public Realm of(String pathValue) {
        if (!StringUtils.hasText(pathValue)) {
            throw new NotFoundException("realm 이 지정되지 않았습니다.");
        }
        try {
            return Realm.valueOf(pathValue.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("알 수 없는 realm 입니다: " + pathValue);
        }
    }

    /** 신원(Principal/자격증명) 조회에 쓰는 주체 유형. */
    public SubjectType subjectTypeOf(String pathValue) {
        return SubjectRealm.subjectTypeOf(of(pathValue));
    }

    /**
     * 셀프 가입이 열려 있는 realm 인지 확인한다. 닫힌 realm 에서는 그 경로가 <b>없는 것</b>으로 다룬다 —
     * 403 이 아니라 404 인 이유는, "여기에도 가입 API 가 있긴 한데 막혀 있다" 를 알려줄 이유가 없기
     * 때문이다. 어느 realm 이 열려 있는지는 {@link Realm} 이 정한다.
     */
    public Realm requireSelfRegistration(String pathValue) {
        Realm realm = of(pathValue);
        if (!realm.allowsSelfRegistration()) {
            throw new NotFoundException("이 realm 은 셀프 가입을 지원하지 않습니다: " + pathValue);
        }
        return realm;
    }

    /**
     * 그 realm 에서만 열리는 인증수단을 위한 확인. 다른 realm 에서는 그 경로가 없는 것으로 다룬다 —
     * 전에는 컨트롤러를 프로퍼티로 껐지만, 한 프로세스가 두 realm 을 담당하면 빈을 껐다 켤 수 없다.
     */
    public Realm requireRealm(String pathValue, Realm expected) {
        Realm realm = of(pathValue);
        if (realm != expected) {
            throw new NotFoundException("이 realm 에서는 지원하지 않는 로그인 방식입니다.");
        }
        return realm;
    }
}
