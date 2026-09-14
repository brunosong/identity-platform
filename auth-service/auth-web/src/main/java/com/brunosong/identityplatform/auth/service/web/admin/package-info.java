/**
 * <b>admin 채널</b> — 운영자가 부르는 관리 API. 계정을 만들고 인가 정책을 바꾼다.
 *
 * <h2>여기의 규칙</h2>
 * <ul>
 *   <li><b>인증되지 않은 호출자가 없다.</b> 모든 엔드포인트가 어드민 realm 토큰과
 *       {@code AUTHZ_MANAGE} 권한을 요구한다({@code RbacAdminAccess}).
 *       앞단 게이트웨이가 걸러줄 것이라 믿고 두면, 이 서비스를 독립 배포하는 순간
 *       인증 없는 관리 API 가 그대로 열린다.</li>
 *   <li><b>남의 데이터를 다루는 것이 정상이다.</b> client 채널과 정확히 반대다 —
 *       여기서는 조회 키를 요청이 받는다. 그래서 권한 검사가 더 무겁다.</li>
 *   <li><b>호출자 realm 과 대상 realm 이 다르다.</b> 호출자는 언제나 어드민이고(토큰이 정한다),
 *       경로·파라미터의 realm 은 <b>편집 대상</b>이다 — 어드민이 포털의 정책을 고치는 것이 정상이다.
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
 * <p>짝이 되는 규칙은 {@code client} 쪽에 있다 — <b>{@code /api/admin} 이 아니면 client 다.</b>
 * 접두어를 둘 다 두지 않고 한쪽만 둔 것은, 그래야 "어느 쪽도 아닌" 경로가 생기지 않기 때문이다.
 * 새 엔드포인트는 접두어를 붙이거나 안 붙이거나 둘 중 하나이고, 안 붙이면 공개되는 쪽이다 —
 * <b>잊었을 때 더 아픈 쪽이 기본값이 되면 안 되지만</b>, 여기서는 그 대가로 규칙이 단순해진다.
 * 그래서 admin 채널은 경로에만 기대지 않고 {@code RbacAdminAccess} 로 서비스 안에서 한 번 더 막는다.
 */
package com.brunosong.identityplatform.auth.service.web.admin;
