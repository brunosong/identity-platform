import { endpoints } from './config';
import { request } from './http';

/**
 * customer-service 호출.
 *
 * <b>auth 가 아닌 다른 서비스다.</b> 그런데 부르는 방법은 똑같다 — 로그인할 때 받은 access 토큰을
 * Authorization 헤더에 싣기만 하면 된다. customer-service 는 그 토큰을 auth 에 물어보지 않고
 * 공개키(JWKS)로 직접 검증한다.
 *
 * 조회 키를 보내지 않는 것에 주목할 것. `/me` 는 토큰의 subjectId 로만 찾는다 — 경로나 본문으로
 * 받으면 남의 식별자를 적어 넣는 것으로 남의 프로필이 열린다.
 */

const customerUrl = () => endpoints().customer;

/** 내 프로필. 아직 만들지 않았으면 404 다(빈 프로필을 지어내지 않는다). */
export function myProfile(accessToken) {
    return request(customerUrl(), 'GET', '/api/customers/me', { token: accessToken });
}

/** 없으면 만들고 있으면 고친다. 프로필은 주체당 하나뿐이라 생성과 수정을 가르지 않는다. */
export function saveMyProfile(accessToken, { name, phoneNumber, email }) {
    return request(customerUrl(), 'PUT', '/api/customers/me', {
        token: accessToken,
        body: { name, phoneNumber, email },
    });
}
