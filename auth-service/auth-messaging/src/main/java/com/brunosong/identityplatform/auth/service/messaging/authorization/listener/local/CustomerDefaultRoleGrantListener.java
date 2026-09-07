package com.brunosong.identityplatform.auth.service.messaging.authorization.listener.local;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.GrantRoleUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.event.SubjectRegisteredEvent;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 신규 고객에게 CUSTOMER 역할(authz)을 부여하는 리스너.
 * "로그인한 포털 고객 = CUSTOMER 권한 보유" 라서 가입 시점에 기본 역할을 준다.
 *
 * <p><b>authorization 서브도메인 아래 둔다.</b> 듣는 이벤트는 identity 의 것이지만 하는 일은 인가 정책이고,
 * 부르는 것도 인가의 유스케이스다. 패키지의 서브도메인 세그먼트는 "이 코드가 누구 것인가"를 가리키므로
 * 이벤트가 어디서 왔는지가 아니라 무엇을 하는지를 따른다.
 *
 * <p>realm 별 기본 역할은 정책이 서로 달라 공용 가입 서비스에 넣지 않는다. 그쪽은 realm 을 모른 채로
 * 두고, 이 리스너가 자기 realm 의 이벤트만 골라 받는다 — 아래 subjectType 검사가 그 필터다.
 * 전에는 realm 프로퍼티로 리스너 자체를 껐지만, 한 서비스가 두 realm 을 담당하면 그렇게 가를 수 없다.
 *
 * <p>인가 저장소를 직접 만지지 않고 {@link GrantRoleUseCase} 를 부른다. 다른 서브도메인의
 * 드리븐 포트를 리스너가 직접 잡으면 트랜잭션 경계·검증·로깅이 있는 유스케이스를 건너뛰게 된다.
 *
 * <p>{@code @EventListener} 라 발행자(가입) 트랜잭션 안에서 동기로 돈다. 역할 부여가 실패하면 가입도
 * 롤백된다 — 권한 없는 계정이 남으면 로그인은 되는데 아무 데도 못 들어간다.
 */
@Component
@RequiredArgsConstructor
public class CustomerDefaultRoleGrantListener {

    /** 신규 포털 고객이 기본으로 받는 역할 — 로그인 고객 보호영역 접근 근거. */
    private static final String CUSTOMER_ROLE = "CUSTOMER";

    private final GrantRoleUseCase grantRole;

    @EventListener
    public void onSubjectRegistered(SubjectRegisteredEvent event) {
        if (event.subjectType() != SubjectType.CUSTOMER) {
            return;
        }
        grantRole.grant(Realm.CUSTOMER, event.subjectId(), CUSTOMER_ROLE);
    }
}
