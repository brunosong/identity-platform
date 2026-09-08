/** 두 앱이 함께 쓰는 화면 조각. 앱마다 다른 것은 색과 무엇을 부르느냐뿐이다. */

export const $ = (id) => document.getElementById(id);

export function show(el, visible) {
    el.classList.toggle('hidden', !visible);
}

/** 결과 알림. kind 는 ok / err / info. */
export function notify(html, kind = 'info') {
    const box = $('notice');
    box.className = `notice ${kind}`;
    box.innerHTML = html;
    show(box, true);
}

export function clearNotice() {
    show($('notice'), false);
}

/** 오간 요청을 위에서부터 쌓는다. 화면에서 무슨 호출이 나갔는지 보이는 것이 이 데모의 절반이다. */
export function logRequest({ method, path, status }) {
    const cls = status === 0 ? 'blocked' : status >= 500 ? 's5' : status >= 400 ? 's4' : 's2';
    const line = document.createElement('div');
    line.className = 'log-line';
    line.innerHTML =
        `<span class="time">${new Date().toLocaleTimeString()}</span>` +
        `<span class="method">${method}</span>` +
        `<span class="path">${path}</span>` +
        `<span class="status ${cls}">${status === 0 ? 'blocked' : status}</span>`;
    $('log').prepend(line);
}

/** 로그인 상태 머리말 — 토큰에서 읽은 것만 보여준다. */
export function renderSession(claims) {
    $('session-tags').innerHTML =
        tag(`realm ${claims.realm}`, true) +
        tag(claims.email || '(이메일 없음)') +
        tag(`만료 ${new Date(claims.exp * 1000).toLocaleTimeString()}`);
    $('claims').textContent = JSON.stringify(claims, null, 2);
}

export function renderPermissions(permissions) {
    if (!permissions || permissions.length === 0) {
        return '<span class="muted">부여된 권한이 없습니다.</span>';
    }
    return permissions.map(p => tag(p, true)).join('');
}

export function tag(text, accent = false) {
    return `<span class="tag${accent ? ' on' : ''}">${escapeHtml(text)}</span>`;
}

export function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, c =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/** 응답을 그대로 보여줄 때. 길면 자른다. */
export function preview(data, limit = 400) {
    const text = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
    return escapeHtml(text.length > limit ? text.slice(0, limit) + ' …' : text);
}

/**
 * 버튼을 누르는 동안 잠그고, 실패하면 알림으로 끝낸다.
 * 이걸 안 하면 느린 응답에 버튼을 연타해 같은 요청이 여러 번 나간다.
 */
export function bind(id, handler) {
    const button = $(id);
    button.addEventListener('click', async () => {
        button.disabled = true;
        try {
            await handler();
        } catch (error) {
            notify(`처리 중 오류: ${escapeHtml(error.message)}`, 'err');
        } finally {
            button.disabled = false;
        }
    });
}

/** 요청 결과가 실패면 알림을 띄우고 true 를 돌려준다(호출부에서 조기 반환용). */
export function failed(res, what) {
    if (res.ok) return false;
    if (res.blocked) {
        notify(escapeHtml(res.message), 'err');
    } else {
        notify(`${escapeHtml(what)} 실패 (${res.status}) — ${escapeHtml(res.message || '')}`, 'err');
    }
    return true;
}
