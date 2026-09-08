import { AuthClient } from '/shared/auth-client.js';
import { $, show, bind, notify, failed, logRequest, renderSession, renderPermissions, preview }
    from '/shared/ui.js';

// 이 앱은 고객 realm 만 상대한다. 화면에서 고르게 두지 않는다 —
// 고르게 두면 고객 화면에서 직원으로 로그인하는 길이 생긴다.
const REALM = 'customer';

let auth = client();

function client() {
    const c = new AuthClient($('base').value, REALM);
    c.onRequest(logRequest);
    return c;
}

// 주소를 바꾸면 그 주소로 다시 붙는다(로그인 상태는 버린다 — 다른 서버의 토큰이므로).
$('base').addEventListener('change', () => {
    auth = client();
    renderLoggedOut();
    notify('연결 대상이 바뀌어 로그인 상태를 비웠습니다.', 'info');
});

function renderLoggedIn() {
    renderSession(auth.claims);
    show($('panel-session'), true);
    show($('panel-login'), false);
}

function renderLoggedOut() {
    show($('panel-session'), false);
    show($('panel-login'), true);
}

// ── 가입 / 로그인 ──────────────────────────────────────────────────────────

bind('btn-register', async () => {
    const email = $('email').value.trim();
    const res = await auth.request('POST', '/api/auth/customer/register', {
        body: { email, password: $('password').value, name: 'Hong', phoneNumber: '010-0000-0000' },
    });
    if (failed(res, '가입')) return;
    notify(`가입되었습니다. principalId=<code>${res.data.principalId}</code> — 이제 로그인해 보세요.`, 'ok');
});

bind('btn-login', async () => {
    const res = await auth.loginWithPassword($('email').value.trim(), $('password').value);
    if (failed(res, '로그인')) return;
    renderLoggedIn();
    notify('로그인되었습니다. 토큰은 이 페이지의 메모리에만 있습니다(새로고침하면 사라집니다).', 'ok');
});

bind('btn-new-email', async () => {
    $('email').value = `hong-${Date.now()}@example.com`;
    notify('새 이메일을 채웠습니다. 가입부터 해보세요.', 'info');
});

// ── 로그인 후 ─────────────────────────────────────────────────────────────

bind('btn-perms', async () => {
    const res = await auth.myPermissions();
    if (failed(res, '권한 조회')) return;
    notify(`realm <b>${res.data.realm}</b> · ${renderPermissions(res.data.permissions)}`, 'ok');
});

bind('btn-refresh', async () => {
    const res = await auth.refresh();
    if (failed(res, '재발급')) return;
    renderLoggedIn();
    notify('새 토큰을 받았습니다. 권한과 리비전이 그 시점 값으로 다시 실렸습니다.', 'ok');
});

bind('btn-jwks', async () => {
    const res = await auth.jwks();
    if (failed(res, 'JWKS 조회')) return;
    const keys = res.data.keys.map(k => `<code>${k.kid}</code>`).join(', ');
    notify(`다른 서비스가 이 공개키로 서명을 검증합니다 — ${keys}. `
        + `<b>두 realm 의 키가 함께 있으므로 서명이 맞다고 realm 이 맞는 것은 아닙니다.</b> `
        + `소비 서비스는 realm 클레임을 따로 확인해야 합니다.`, 'ok');
});

bind('btn-logout', async () => {
    await auth.logout();
    renderLoggedOut();
    notify('로그아웃했습니다. 단일 세션을 켜지 않았다면 방금 그 access 토큰은 만료까지 '
        + '서버에서 여전히 유효합니다 — 그래서 클라이언트가 직접 버립니다.', 'ok');
});

// ── realm 격리 실험 ────────────────────────────────────────────────────────

bind('btn-cross-realm', async () => {
    const res = await auth.tryLoginInOtherRealm(
        'employee', $('email').value.trim(), $('password').value);

    if (res.ok) {
        notify('직원 realm 로그인이 통과했습니다. <b>이건 문제입니다</b> — realm 격리가 깨졌습니다.', 'err');
        return;
    }
    notify(`직원 realm 로그인 거부됨 (<code>${res.status}</code>) — "${res.message}"<br>`
        + `자격증명은 맞지만 직원 서랍에 이 계정이 없습니다. `
        + `없는 아이디와 같은 메시지로 끝나 계정이 어디에 있는지도 새지 않습니다.`, 'ok');
});

bind('btn-unknown-realm', async () => {
    const res = await auth.request('POST', '/api/auth/realms/martian/login',
        { body: { loginId: 'a', password: 'b' } });
    notify(`(<code>${res.status}</code>) ${preview(res.data)}<br>`
        + `realm 은 비밀이 아니라 어느 서랍을 열지 고르는 값입니다. 모르는 값은 404 입니다.`, 'ok');
});

// ── 시작 ──────────────────────────────────────────────────────────────────

$('origin').textContent = location.origin;
$('email').value = `hong-${Date.now()}@example.com`;
