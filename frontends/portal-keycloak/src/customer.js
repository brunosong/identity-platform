/**
 * customer-service 호출.
 *
 * 이 앱이 상대하는 두 번째 서버다. Keycloak 이 준 access_token 을 그대로 들고 간다.
 * 토큰을 만든 곳과 받는 곳이 다르다는 것이 여기서 처음 드러난다.
 *
 * 이 호출이 통과하려면 customer-service 쪽에서 세 가지가 다 맞아야 한다.
 *
 *   1. 서명   issuer-uri 가 가리키는 곳의 JWKS 에 이 토큰의 kid 가 있어야 한다
 *   2. iss    토큰의 발급자가 issuer-uri 와 같아야 한다
 *   3. aud    spring.security.oauth2.resourceserver.jwt.audiences 와 겹쳐야 한다
 *
 * 3번은 Keycloak 기본값으로는 맞지 않는다. Keycloak 이 넣어주는 aud 는 account 이고
 * customer-service 가 요구하는 것은 shop 이다.
 */

const BASE_URL = (import.meta.env.VITE_CUSTOMER_BASE_URL ?? 'http://localhost:8081').replace(/\/+$/, '');

export const customerBaseUrl = BASE_URL;

export async function myProfile(accessToken) {
    let response;
    try {
        response = await fetch(`${BASE_URL}/api/customers/me`, {
            headers: { Authorization: `Bearer ${accessToken}` },
        });
    } catch {
        // fetch 자체가 실패하면 서버가 없거나 CORS 에서 막힌 것이다. 브라우저는 둘을 구분해
        // 알려주지 않는다. 그것이 CORS 의 취지다. 정보를 흘리지 않는다.
        return {
            status: 0,
            body: `요청이 브라우저에서 막혔습니다. ${BASE_URL} 가 떠 있는지, `
                + `그 서버의 app.cors.allowed-origins 에 ${location.origin} 이 있는지 확인하세요.`,
        };
    }

    const text = await response.text();
    return { status: response.status, body: text || '(본문 없음)' };
}
