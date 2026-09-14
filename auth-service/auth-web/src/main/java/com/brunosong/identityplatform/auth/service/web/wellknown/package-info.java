/**
 * <b>공개 메타데이터</b> — 사람이 아니라 <b>다른 서비스</b>가 부른다.
 *
 * <p>채널이 {@code admin}/{@code client} 둘뿐이 아닌 이유가 여기 있다. JWKS 와 discovery 문서를
 * 받아 가는 쪽은 브라우저도 운영자도 아니라 <b>토큰을 검증하려는 리소스 서버</b>다
 * (customer-service 가 그렇게 쓴다). 부르는 주체가 다르면 채널이 다르다.
 *
 * <h2>여기의 규칙</h2>
 * <ul>
 *   <li><b>인증이 없다.</b> 담긴 것이 모두 공개 정보다 — 공개키와 발급자 주소.
 *       비밀이 아니므로 막을 이유도 없다.</li>
 *   <li><b>거짓을 적지 않는다.</b> 기계가 읽고 그대로 믿는 문서다. 없는 엔드포인트를 적으면
 *       그것을 믿고 호출한 쪽이 404 를 만난다.</li>
 *   <li><b>경로가 발급자 기준이다</b> — {@code iss} 가 곧 이 문서의 위치이고, 이 문서가 JWKS
 *       위치를 가리킨다. 그래서 소비 서비스는 설정 한 줄({@code issuer-uri})만 갖는다.</li>
 * </ul>
 */
package com.brunosong.identityplatform.auth.service.web.wellknown;
