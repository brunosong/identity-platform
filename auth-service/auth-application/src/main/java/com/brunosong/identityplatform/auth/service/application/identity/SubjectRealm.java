package com.brunosong.identityplatform.auth.service.application.identity;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * {@link Realm}(인가 정책)과 {@link SubjectType}(신원 식별자 의미)의 대응을 잇는 유일한 지점.
 *
 * <p>두 enum 은 관심사가 달라 도메인에서 일부러 분리돼 있다 — Realm 은 fail-open/closed 같은 인가 정책을,
 * SubjectType 은 subjectId 가 무엇을 가리키는지를 담는다. 그 결정을 유지하려면 enum 끼리 서로를 알아선
 * 안 되므로, 변환은 바깥의 이 클래스에만 둔다.
 *
 * <p>한 로그인 흐름이 둘을 모두 쓴다 — 자격증명은 SubjectType 으로 찾고, 권한과 서명키는 Realm 으로
 * 고른다. 변환이 여기저기 흩어지면 한쪽만 바뀌어 자격증명과 토큰의 realm 이 어긋난다.
 */
public final class SubjectRealm {

    private SubjectRealm() {
    }

    public static Realm realmOf(SubjectType subjectType) {
        return switch (subjectType) {
            case EMPLOYEE -> Realm.EMPLOYEE;
            case CUSTOMER -> Realm.CUSTOMER;
        };
    }

    public static SubjectType subjectTypeOf(Realm realm) {
        return switch (realm) {
            case EMPLOYEE -> SubjectType.EMPLOYEE;
            case CUSTOMER -> SubjectType.CUSTOMER;
        };
    }
}
