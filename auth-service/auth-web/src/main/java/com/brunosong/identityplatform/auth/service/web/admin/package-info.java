/**
 * <b>admin 채널</b> — 운영자가 부르는 관리 API. 계정을 만들고 인가 정책을 바꾼다.
 *
 * <h2>여기의 규칙</h2>
 * <ul>
 *   <li><b>인증되지 않은 호출자가 없다.</b> 모든 엔드포인트가 MASTER realm 토큰과
 *       {@code AUTHZ_MANAGE} 권한을 요구한다. 컨트롤러가 아니라 스프링 시큐리티의 관리 API 체인이
 *       막는다({@code web.support.SecurityConfiguration}). 경로가 체인을 고르니 새 엔드포인트도 빠지지 않는다.
 *       앞단 게이트웨이가 걸러줄 것이라 믿고 두면, 이 서비스를 독립 배포하는 순간
 *       인증 없는 관리 API 가 그대로 열린다.</li>
 *   <li><b>남의 데이터를 다루는 것이 정상이다.</b> 자기 정보만 주는 userinfo 와 정확히 반대다.
 *       여기서는 조회 키를 요청이 받는다. 그래서 권한 검사가 더 무겁다.</li>
 *   <li><b>호출자 realm 과 대상 realm 이 다르다.</b> 호출자는 언제나 MASTER 관리자이고(토큰이 정한다),
 *       경로·파라미터의 realm 은 <b>편집 대상</b>이다. MASTER 관리자가 포털의 정책을 고치는 것이 정상이다.
 *       둘을 한 값으로 묶으면 안 된다. (Keycloak 의 {@code /admin/realms/{realm}/...} 도 같다.)</li>
 * </ul>
 *
 * <h2>경로가 채널을 말한다 — {@code /api/admin/**}</h2>
 * <pre>
 * /api/admin/realms/{realm}/users   계정 등록 (경로의 realm 은 대상)
 * /api/admin/rbac/**                역할·권한·URL규칙·리비전·캐시 리로드
 * </pre>
 *
 * <b>게이트웨이는 경로로 라우팅한다.</b> 그래서 채널이 접두어 하나로 모여 있어야
 * "admin 은 사내망에만 연다" 를 규칙 한 줄로 쓸 수 있다. 전에는
 * {@code /api/auth/admin/...} 과 {@code /api/rbac/**} 두 곳에 흩어져 있어서 규칙이 두 개였고,
 * admin 엔드포인트가 하나 늘 때마다 규칙이 함께 늘 판이었다.
 *
 * <h2>이 채널에는 화면도 있다 — {@code console}</h2>
 * 운영자가 브라우저로 여는 서버 렌더 화면이 여기 속한다. 부르는 주체가 같으면 같은 채널이다 —
 * 화면이냐 JSON 이냐는 채널을 가르는 축이 아니다(그 축이었으면 {@code broker} 와
 * {@code wellknown} 이 한 묶음이어야 한다).
 *
 * <p>화면은 Bearer 헤더를 실을 수 없어서 MASTER realm 으로 로그인하고 토큰을 쿠키에 둔다
 * ({@code console.login}). 아직 권한은 보지 않아서 {@code @Profile("local")} 로 로컬에서만 뜬다.
 *
 * <p>경로도 아직 {@code /api/admin/**} 밖이다({@code /page/oauth-clients}). 게이트웨이의 채널
 * 규칙 한 줄에 걸리지 않는다는 뜻이다. 화면 접두어를 어떻게 가져갈지는 로그인·동의 화면까지
 * 나온 뒤 한 번에 정한다.
 *
 * <p>게이트웨이는 {@code /api/admin/**} 한 줄로 이 채널을 가를 수 있다. 다만 게이트웨이에만 기대지 않고
 * 서비스 안의 시큐리티 체인으로 한 번 더 막는다. auth 를 홀로 배포해도 관리 API 가 열리지 않는다.
 */
package com.brunosong.identityplatform.auth.service.web.admin;
