/**
 * 모든 HTTP 호출이 지나는 한 자리.
 *
 * 여기에 모아두는 이유는 세 가지다 — 토큰을 싣는 규칙을 한 곳에서 정하고, 오간 요청을 화면에
 * 기록하고, 실패를 한 가지 모양으로 만들기 위해서다.
 *
 * <b>토큰은 보내는 쪽이 명시적으로 싣는다.</b> access 는 Authorization 헤더에, refresh 는
 * 재발급 요청의 폼 본문에 넣는다. 그래서 "이 요청이 인증된 요청인가" 가 코드에 드러난다.
 * 브라우저가 알아서 붙여주는 값은 하나도 없다.
 *
 * <h3>401 을 만나면 한 번 되살려 본다</h3>
 * access 토큰은 짧게 쓰는 값이라 화면을 열어둔 채로 만료될 수 있다. 그때마다 사람에게 오류를
 * 보여주는 대신, 재발급을 한 번 부르고 원래 요청을 다시 보낸다. <b>재시도는 한 번뿐이다.</b>
 * 되살리지 못하면 받은 401 을 그대로 호출자에게 돌려준다.
 */

/** 화면 하단 요청 로그를 위한 구독자들. */
const listeners = new Set();

export function onRequest(listener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
}

function emit(entry) {
    listeners.forEach((fn) => fn({ ...entry, at: new Date() }));
}

/**
 * @param baseUrl  어느 서버로
 * @param method   HTTP 메서드
 * @param path     경로
 * @param body     JSON 으로 실을 본문 (없으면 생략)
 * @param form     폼 인코딩으로 실을 본문. OAuth 토큰 엔드포인트가 이 모양을 요구한다
 * @param token    있으면 Authorization: Bearer 로 싣는다
 * @param query    쿼리 파라미터
 * @returns {{ok, status, data, message, blocked}}
 */
export async function request(baseUrl, method, path, options = {}) {
    const result = await send(baseUrl, method, path, options);

    // 토큰을 실어 보낸 요청의 401 만 만료로 본다. 로그인 전 호출의 401 은 다른 얘기고,
    // 재발급 요청 자체는 token 을 안 쓰므로(쿠키로 돈다) 여기서 재귀하지 않는다.
    if (result.status !== 401 || !options.token || !refreshAccessToken) return result;

    const fresh = await refreshAccessToken();
    if (!fresh) return result;    // 되살리지 못했다. 원래 401 을 그대로 돌려준다

    // 재시도는 한 번뿐이다. send 는 다시 감싸지 않는다.
    return send(baseUrl, method, path, { ...options, token: fresh });
}

/**
 * 만료된 access 토큰을 되살리는 방법을 등록한다. {@link AuthProvider} 가 앱이 뜰 때 넣는다.
 *
 * 이 파일이 AuthContext 를 직접 부르지 않는 이유는 순환 때문이다 - AuthContext 가 쓰는
 * api/auth.js 가 이미 이 파일을 쓴다. 그래서 방향을 뒤집어 등록으로 받는다.
 *
 * 돌려주는 것은 <b>새 access 토큰</b>이다. 재발급은 새 쌍을 주므로 refresh 토큰도 함께 갈리는데,
 * 그 보관은 AuthProvider 가 한다. 이 파일이 알아야 하는 것은 다시 보낼 때 실을 값 하나뿐이다.
 */
let refreshAccessToken = null;

export function setAccessTokenRefresher(refresher) {
    refreshAccessToken = refresher;
}

async function send(baseUrl, method, path, { body, form, token, query } = {}) {
    const headers = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    // 폼 인코딩은 브라우저가 "단순 요청" 으로 쳐서 preflight 가 나가지 않는다.
    if (form !== undefined) headers['Content-Type'] = 'application/x-www-form-urlencoded';
    if (token) headers.Authorization = `Bearer ${token}`;

    const url = baseUrl + path + (query ? `?${new URLSearchParams(query)}` : '');

    let response;
    try {
        response = await fetch(url, {
            method,
            headers,
            body: form !== undefined
                ? new URLSearchParams(form)
                : (body === undefined ? undefined : JSON.stringify(body)),
        });
    } catch (cause) {
        // fetch 자체가 실패하면 서버가 없거나 CORS 에서 막힌 것이다.
        // 브라우저는 둘을 구분해 알려주지 않는다 — 그것이 CORS 의 취지다(정보를 흘리지 않는다).
        emit({ method, path, status: 0, baseUrl });
        return {
            ok: false,
            status: 0,
            blocked: true,
            data: null,
            message:
                `요청이 브라우저에서 막혔습니다. ${baseUrl} 가 떠 있는지, ` +
                `그 서버의 app.cors.allowed-origins 에 ${location.origin} 이 있는지 확인하세요.`,
        };
    }

    emit({ method, path, status: response.status, baseUrl });

    const text = await response.text();
    const data = text ? safeJson(text) : null;

    return {
        ok: response.ok,
        status: response.status,
        data,
        message: data?.message ?? null,
    };
}

function safeJson(text) {
    try {
        return JSON.parse(text);
    } catch {
        return { message: text };
    }
}
