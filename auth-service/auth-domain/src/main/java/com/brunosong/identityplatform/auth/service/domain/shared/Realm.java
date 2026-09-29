package com.brunosong.identityplatform.auth.service.domain.shared;

import java.util.Set;

/**
 * 인가 영역(realm). 한 저장소를 두 realm 이 공유하되, 행은 realm 으로 분리된다.
 *
 * <ul>
 *   <li>{@link #MASTER}: 관리 영역. auth 를 운영하는 관리자가 산다. Keycloak 의 master realm 이다.</li>
 *   <li>{@link #ADMIN} — 어드민 영역. 직원이 산다. 식별자는 사번 성격의 값이다.</li>
 *   <li>{@link #PORTAL} — 포털 영역. 고객이 산다. 식별자는 채번한 UUID 다.</li>
 * </ul>
 *
 * 역할/권한/URL접근규칙/주체-역할/리비전, 그리고 자격증명과 신원까지 모두 realm 으로 스코프된다.
 *
 * <h2>파티션 키는 이것 하나다</h2>
 * 전에는 {@code SubjectType}(EMPLOYEE/CUSTOMER)이 따로 있었다. identity 쪽 테이블은 그것으로,
 * authz 쪽 테이블은 realm 으로 행을 나눴는데, 두 값은 끝까지 1:1 이었고 그 사이 변환
 * ({@code SubjectRealm})은 항등 함수였다 — <b>같은 분할선에 이름이 둘이었을 뿐이다.</b>
 *
 * <p>{@code authz_subject_role.realm} 은 그대로 둔다. 없애려면 주체의 realm 을 알기 위해 authz 가
 * identity 를 조인해야 하는데, 그것은 서브도메인 경계를 넘는 일이다(Keycloak 은 한 서비스라
 * {@code USER_ROLE_MAPPING} 에 realm 컬럼이 없어도 된다 — 우리는 그 전제가 다르다).
 * 대신 얻은 것은 <b>두 쪽이 같은 단어를 쓰게 된 것</b>이다: 전에는 authz 의 realm 과 identity 의
 * 주체 유형 사이에 변환이 있었고, 이제는 값이 직접 비교된다.
 *
 * <p>한 realm 안에서 주체를 더 갈라야 할 날이 오면(포털에 개인·법인) <b>두 번째 파티션 키를 만들지
 * 않는다.</b> 속성이나 그룹으로 가른다 — 파티션 키를 늘리면 유일 제약·조회 범위·인덱스가 전부
 * 그것을 따라가야 한다. Keycloak 도 같은 답을 쓴다.
 *
 * <h2>realm 은 프로세스가 아니라 요청이 지목한다</h2>
 * 한 auth 프로세스가 두 realm 을 모두 담당한다({@code /api/auth/realms/{realm}/...}). 전에는
 * 프로세스마다 realm 이 하나 박혀 있었고, 상대 realm 의 서명키가 없다는 사실 자체가 격리 장치였다.
 * 지금도 격리는 <b>키</b>가 맡는다: 검증하는 쪽은 realm 을 먼저 정하고 그 realm 의 공개키 하나로만
 * 확인하며, 발급자 이름({@code iss = .../realms/portal})이 어느 realm 인지를 밝힌다. 여기에
 * <b>조회 범위</b>(모든 자격증명 조회가 realm 으로 좁혀진다)가 더해진다.
 *
 * <p>realm 은 비밀이 아니다. 아무나 상대 realm 을 지목할 수 있지만 그 realm 에 계정이 없으면
 * 인증이 성립하지 않고, 발급되는 토큰의 realm 은 요청이 아니라 인증된 주체가 정한다.
 *
 * <h2>realm 은 정책 묶음이다</h2>
 * 이 enum 이 들고 있는 값이 그 정책이다 — 지금은 <b>어떤 방식으로 스스로 가입할 수 있는가</b>
 * 하나다. Keycloak 의 realm 설정 화면에 있는 것들과 같은 성격이고, 그래서 "고객 가입 API" 와
 * "직원 등록 API" 를 따로 두는 대신 <b>가입 경로는 하나로 두고 realm 이 그것을 여는지 닫는지를
 * 정한다.</b>
 *
 * <p><b>규칙 없는 URL 을 어떻게 할지는 realm 마다 다르지 않다.</b> 두 realm 모두
 * fail-closed(화이트리스트)다 — 등록된 규칙만 통과하고, 규칙 없는 보호 경로는 거부한다. 전에는
 * 포털이 fail-open 이었는데, 그러면 규칙을 등록하지 않은 고객 API 가 조용히 열린 채로 남는다.
 * <b>잊었을 때의 결과가 "막힌다" 여야지 "열린다" 여서는 안 된다.</b> 값이 갈리지 않으므로 그 정책은
 * 필드로 남기지 않았다 — {@code AccessControlService} 가 한 가지 규칙만 시행한다.
 */
public enum Realm {

    /**
     * 관리 영역. Keycloak 의 master realm 과 같은 자리다.
     *
     * <p>여기 사는 사람은 업무 시스템을 쓰지 않고 auth 자체를 운영한다. 다른 realm 의 역할과 앱을
     * 관리하는 것이 이 realm 관리자의 일이라 직원(ADMIN)과 따로 둔다.
     *
     * <p><b>셀프 가입이 닫혀 있다.</b> 최초 관리자는 데이터로 심는다.
     */
    MASTER(Set.of()),

    /**
     * 어드민 영역.
     *
     * <p><b>셀프 가입이 닫혀 있다.</b> 직원 계정은 다른 관리자가 만들고({@code /api/admin/realms/admin/users}),
     * 최초 한 명은 데이터로 심는다. realm 이름은 주소창과 발급자 문서에 그대로 나와서 숨길 수 없다.
     * 열어 두면 아무나 직원 realm 의 신원과 {@code aud=backoffice} 토큰을 손에 넣는다.
     *
     * <p>한때 인증번호 가입을 열어 두고 "역할이 없으니 아무것도 못 연다" 에 기댔다. 그러면 방어가
     * 권한 검사 한 겹뿐이다. 관리 API 하나가 로그인 여부만 보고 권한 확인을 빠뜨리면 그대로 뚫린다.
     * 아무 주소로 인증번호 메일을 보내게 하는 길이기도 했다.
     */
    ADMIN(Set.of()),

    /**
     * 포털 영역.
     *
     * <p>셀프 가입이 <b>두 방식 모두</b> 열려 있다. 고객은 비밀번호를 정해 가입하거나, 비밀번호
     * 없이 이메일 인증번호로 가입할 수 있다 — 고객은 두 자격증명을 다 가질 수 있기 때문이다
     * (직원과 다른 점이다). 어느 쪽으로 들어와도 가입과 동시에 {@code CUSTOMER} 역할을 받는다.
     *
     * <p>두 방식이 겹치지는 않는다. 비밀번호 가입도 <b>이메일 계정을 함께</b> 만들기 때문에,
     * 비밀번호로 가입한 사람은 이미 이메일 OTP 로도 로그인할 수 있다 — 이메일로 "다시 가입" 할
     * 일이 없다. 가입용 인증번호가 이미 등록된 주소로는 나가지 않는 것도 같은 이야기다.
     */
    PORTAL(Set.of(RegistrationMethod.PASSWORD, RegistrationMethod.EMAIL_OTP));

    /**
     * 인증 없이 스스로 계정을 만들 수 있는 방식들. Keycloak 의 realm 설정 "User registration" 이
     * 켜짐/꺼짐 하나인 것과 달리, <b>어떤 방식으로</b> 까지 함께 말한다.
     *
     * <p>이 값이 realm 에 있는 것이 요점이다. 전에는 {@code /api/auth/customer/register} 와
     * {@code /api/auth/employee/register} 라는 <b>서로 다른 경로</b>가 그 차이를 표현했다. 그러면
     * 경로 이름이 정책이 되어, realm 이 하나 늘 때마다 경로를 새로 만들어야 하고 무엇이 열려 있는지
     * 한눈에 보이지 않는다. 경로를 하나로 두고 realm 이 열고 닫으면 정책이 한 자리에 모인다.
     *
     * <p>켜짐/꺼짐을 따로 두지 않는다. <b>비어 있으면 닫힌 것</b>이다 — 두 값을 따로 두면
     * "열려 있는데 방식이 없다" 거나 "닫혀 있는데 방식이 있다" 는 모순된 상태가 표현된다.
     */
    private final Set<RegistrationMethod> registrationMethods;

    Realm(Set<RegistrationMethod> registrationMethods) {
        this.registrationMethods = registrationMethods;
    }

    /** 어떤 방식으로든 스스로 가입할 수 있는지. */
    public boolean allowsSelfRegistration() {
        return !registrationMethods.isEmpty();
    }

    /** 이 realm 이 여는 가입 방식들. 화면이 무엇을 보여줄지 정할 때 쓴다. */
    public Set<RegistrationMethod> registrationMethods() {
        return registrationMethods;
    }

    /** 그 방식의 셀프 가입이 이 realm 에 열려 있는지. */
    public boolean allowsSelfRegistrationWith(RegistrationMethod method) {
        return registrationMethods.contains(method);
    }
}
