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
 * <h3>401 을 대신 처리하지 않는다</h3>
 * access 가 만료되면 받은 401 을 그대로 호출자에게 돌려준다. 뒤에서 조용히 재발급하고 원래
 * 요청을 다시 보내는 장치를 두지 않는다. <b>만료가 눈에 보여야 무엇이 언제 죽는지 알 수 있다.</b>
 * 재발급은 마이페이지의 버튼으로만 돈다.
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
export async function request(baseUrl, method, path, { body, form, token, query } = {}) {
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
