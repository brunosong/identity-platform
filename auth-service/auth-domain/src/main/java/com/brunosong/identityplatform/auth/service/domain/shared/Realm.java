package com.brunosong.identityplatform.auth.service.domain.shared;

/**
 * 인가 영역(realm). 한 저장소를 두 realm 이 공유하되, 행은 realm 으로 분리된다.
 *
 * <ul>
 *   <li>{@link #ADMIN} — 어드민 영역. 직원(EMPLOYEE) 주체가 산다. 식별자는 사번 성격의 값이다.</li>
 *   <li>{@link #PORTAL} — 포털 영역. 고객(CUSTOMER) 주체가 산다. 식별자는 채번한 UUID 다.</li>
 * </ul>
 *
 * 역할/권한/URL접근규칙/주체-역할/리비전, 그리고 자격증명과 신원까지 모두 realm 으로 스코프된다.
 *
 * <p>한 auth 프로세스가 두 realm 을 모두 담당한다. 그래서 realm 은 프로세스 설정이 아니라 요청이
 * 지목한다({@code /api/auth/realms/{realm}/...}) — 전에는 프로세스마다 realm 이 하나 박혀 있었고,
 * 상대 realm 의 서명키가 없다는 사실 자체가 격리 장치였다. 이제 그 장치가 없으므로 격리는
 * <b>조회 범위</b>(모든 자격증명 조회가 주체 유형으로 좁혀진다)와 <b>토큰의 realm 클레임</b>이 맡는다.
 *
 * <p>realm 은 비밀이 아니다. 아무나 상대 realm 을 지목할 수 있지만 그 realm 에 계정이 없으면
 * 인증이 성립하지 않고, 발급되는 토큰의 realm 은 요청이 아니라 인증된 주체가 정한다.
 *
 * <p><b>이름이 주체 유형과 다른 이유.</b> identity 서브도메인의 {@code SubjectType}
 * (EMPLOYEE/CUSTOMER)와 지금은 1:1 로 대응하지만, 가리키는 것이 다르다 — SubjectType 은
 * <i>사람</i>이 누구인지를, 이 enum 은 <i>영역</i>이 어떤 정책을 갖는지를 말한다.
 * {@code ADMIN.failOpen()} 은 읽히지만 {@code EMPLOYEE.failOpen()} 은 읽히지 않는다.
 * 사람이 fail-open 일 수는 없기 때문이다.
 *
 * <p>값 이름을 같게 두면 두 enum 이 같은 것으로 보이고, 그 사이 변환({@code SubjectRealm})이
 * 항등 함수처럼 보여 "왜 있지?" 가 된다. 이름을 갈라두면 무엇을 무엇으로 옮기는지가 드러난다.
 *
 * <p>대응이 앞으로도 1:1 이라는 보장은 없다 — 한 영역에 여러 주체 유형이 살거나(포털에 개인·법인),
 * 한 주체 유형이 여러 영역에 걸칠 수 있다(본사·협력사 어드민). 그때 갈라질 자리를 미리 열어둔다.
 * 변환은 {@code SubjectRealm} 한 곳에서만 한다.
 */
public enum Realm {
    /** 어드민: fail-closed(화이트리스트). 매칭 규칙 없는 보호경로(/page,/api)는 거부 — 등록된 것만 허용. */
    ADMIN(false),
    /** 포털: fail-open(블랙리스트). 매칭 규칙 없으면 허용 — authz_url_access 에 등록된 URL 만 차단. */
    PORTAL(true);

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
