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
 * 상대 realm 의 서명키가 없다는 사실 자체가 격리 장치였다. 지금도 격리는 <b>키</b>가 맡는다:
 * 검증하는 쪽은 realm 을 먼저 정하고 그 realm 의 공개키 하나로만 확인하며, 발급자 이름
 * ({@code iss = .../realms/portal})이 어느 realm 인지를 밝힌다. 여기에 <b>조회 범위</b>(모든 자격증명
 * 조회가 주체 유형으로 좁혀진다)가 더해진다.
 *
 * <p><b>realm 은 정책 묶음이다.</b> 이 enum 이 들고 있는 값들이 그 정책이다 — 규칙 없는 URL 을
 * 어떻게 할지({@code failOpen}), 아무나 가입할 수 있는지({@code selfRegistration}).
 * Keycloak 의 realm 설정 화면에 있는 것들과 같은 성격이다. 그래서 "고객 가입 API" 와 "직원 등록 API"
 * 를 따로 두는 대신, <b>가입 경로는 하나로 두고 realm 이 그것을 여는지 닫는지를 정한다.</b>
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
    /**
     * 어드민: fail-closed(화이트리스트). 매칭 규칙 없는 보호경로(/page,/api)는 거부 — 등록된 것만 허용.
     * 셀프 가입은 닫혀 있다 — 열면 아무나 자기 자신을 직원으로 만든다. 계정은 운영자가 만든다.
     */
    ADMIN(false, false),
    /**
     * 포털: fail-open(블랙리스트). 매칭 규칙 없으면 허용 — authz_url_access 에 등록된 URL 만 차단.
     * 셀프 가입이 열려 있다 — 고객은 스스로 가입한다.
     */
    PORTAL(true, true);

    /**
     * 매칭되는 URL 접근 규칙이 없을 때의 기본 결정.
     * true=허용(fail-open/블랙리스트), false=보호경로 거부(fail-closed/화이트리스트).
     */
    private final boolean failOpen;

    /**
     * 인증 없이 스스로 계정을 만들 수 있는지. Keycloak 의 realm 설정 "User registration" 과 같다.
     *
     * <p>이 값이 realm 에 있는 것이 요점이다. 전에는 {@code /api/auth/customer/register} 와
     * {@code /api/auth/employee/register} 라는 <b>서로 다른 경로</b>가 그 차이를 표현했다. 그러면
     * 경로 이름이 정책이 되어, realm 이 하나 늘 때마다 경로를 새로 만들어야 하고 무엇이 열려 있는지
     * 한눈에 보이지 않는다. 경로를 하나로 두고 realm 이 열고 닫으면 정책이 한 자리에 모인다.
     */
    private final boolean selfRegistration;

    Realm(boolean failOpen, boolean selfRegistration) {
        this.failOpen = failOpen;
        this.selfRegistration = selfRegistration;
    }

    public boolean failOpen() {
        return failOpen;
    }

    public boolean allowsSelfRegistration() {
        return selfRegistration;
    }
}
