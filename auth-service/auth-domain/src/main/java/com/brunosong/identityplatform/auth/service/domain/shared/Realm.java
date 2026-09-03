package com.brunosong.identityplatform.auth.service.domain.shared;

/**
 * 인가 영역(realm). 한 인가 저장소를 여러 호스트가 공유하되, 행은 realm 으로 분리된다.
 *
 * <ul>
 *   <li>{@link #EMPLOYEE} — admin-service(관리자/직원). 주체 식별자는 esntlId.</li>
 *   <li>{@link #CUSTOMER} — portal-service(고객). 주체 식별자는 customerUuid.</li>
 * </ul>
 *
 * 역할/권한/URL접근규칙/주체-역할/리비전은 모두 realm 으로 스코프된다.
 * 한 호스트는 자기 realm 만 읽고 시행한다(설정 프로퍼티 authorization.realm).
 *
 * <p>identity 서브도메인의 {@code SubjectType} 와 값이 대응하지만(EMPLOYEE/CUSTOMER),
 * 이 enum 은 인가 정책(fail-open/closed)을 담고 SubjectType 은 주체 식별자 의미를 담아
 * 관심사가 다르다. 두 서브도메인은 분리 유지한다.
 */
public enum Realm {
    /** 직원(admin): fail-closed(화이트리스트). 매칭 규칙 없는 보호경로(/page,/api)는 거부 — 등록된 것만 허용. */
    EMPLOYEE(false),
    /** 고객(portal): fail-open(블랙리스트). 매칭 규칙 없으면 허용 — authz_url_access 에 등록된 URL 만 차단. */
    CUSTOMER(true);

    /**
     * 매칭되는 URL 접근 규칙이 없을 때의 기본 결정.
     * true=허용(fail-open/블랙리스트), false=보호경로 거부(fail-closed/화이트리스트).
     */
    private final boolean failOpen;

    Realm(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public boolean failOpen() {
        return failOpen;
    }
}
