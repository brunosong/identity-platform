import { AuthClient } from '/shared/auth-client.js';
import { $, show, bind, notify, failed, logRequest, renderSession, renderPermissions, escapeHtml, preview }
    from '/shared/ui.js';

// 이 앱은 직원 realm 만 상대한다.
const REALM = 'employee';
const MANAGE_PERMISSION = 'AUTHZ_MANAGE';

let auth = client();

function client() {
    const c = new AuthClient($('base').value, REALM);
    c.onRequest(logRequest);
    return c;
}

$('base').addEventListener('change', () => {
    auth = client();
    renderLoggedOut();
    notify('연결 대상이 바뀌어 로그인 상태를 비웠습니다.', 'info');
});

function renderLoggedIn() {
    const claims = auth.claims;
    renderSession(claims);
    show($('panel-session'), true);
    show($('panel-login'), false);

    // 관리 화면은 권한이 있을 때만 연다. 화면을 감추는 것은 편의일 뿐이고 —
    // 실제 방어는 서버가 한다(RbacAdminAccess). 여기만 고치면 아무것도 통과하지 않는다.
    const permissions = (claims.authLs || '').split(',').filter(Boolean);
    const canManage = permissions.includes(MANAGE_PERMISSION);
    show($('panel-rbac'), canManage);
    show($('panel-register-employee'), canManage);

    if (!canManage) {
        notify(`로그인은 되었지만 <code>${MANAGE_PERMISSION}</code> 권한이 없어 관리 기능은 보이지 않습니다. `
            + `관리자가 역할을 부여해야 합니다.`, 'info');
    }
}

function renderLoggedOut() {
    show($('panel-session'), false);
    show($('panel-rbac'), false);
    show($('panel-register-employee'), false);
    show($('panel-login'), true);
}

// ── 로그인 (이메일 OTP) ────────────────────────────────────────────────────

bind('btn-send-code', async () => {
    const res = await auth.sendEmailCode($('email').value.trim());
    if (failed(res, '인증번호 발송')) return;
    notify(`발송 요청됨 (<code>${res.status}</code>). 가입 여부와 무관하게 같은 응답을 주므로 `
        + `이 응답만으로는 그 이메일이 직원인지 알 수 없습니다.<br>`
        + `<b>local 프로파일에서는 메일을 보내지 않고 고정코드 123456 을 씁니다.</b>`, 'ok');
});

bind('btn-login', async () => {
    const res = await auth.loginWithEmailCode($('email').value.trim(), $('code').value.trim());
    if (failed(res, '로그인')) return;
    renderLoggedIn();
    notify(`로그인되었습니다. 응답에 권한 목록도 함께 옵니다 — `
        + `${renderPermissions(res.data.permissions)}`, 'ok');
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
    notify('새 토큰을 받았습니다.', 'ok');
});

bind('btn-logout', async () => {
    await auth.logout();
    renderLoggedOut();
    notify('로그아웃했습니다.', 'ok');
});

// ── 인가 정책 조회 ─────────────────────────────────────────────────────────

function renderTable(columns, rows) {
    if (!rows || rows.length === 0) {
        $('rbac-result').innerHTML = '<p class="hint">결과가 없습니다.</p>';
        return;
    }
    const head = columns.map(c => `<th>${escapeHtml(c.label)}</th>`).join('');
    const body = rows.map(row =>
        '<tr>' + columns.map(c => `<td>${escapeHtml(row[c.key] ?? '')}</td>`).join('') + '</tr>').join('');
    $('rbac-result').innerHTML = `<table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`;
}

bind('btn-roles', async () => {
    const res = await auth.request('GET', '/api/rbac/roles', { auth: true, query: { realm: 'EMPLOYEE' } });
    if (failed(res, '역할 조회')) return;
    renderTable(
        [{ key: 'roleId', label: 'ID' }, { key: 'roleCode', label: '코드' },
         { key: 'roleName', label: '이름' }, { key: 'description', label: '설명' }],
        res.data);
    notify(`직원 realm 의 역할 ${res.data.length}개.`, 'ok');
});

bind('btn-permissions', async () => {
    const res = await auth.request('GET', '/api/rbac/permissions', { auth: true, query: { realm: 'EMPLOYEE' } });
    if (failed(res, '권한 조회')) return;
    renderTable(
        [{ key: 'permissionId', label: 'ID' }, { key: 'permissionCode', label: '코드' },
         { key: 'permissionName', label: '이름' }, { key: 'category', label: '분류' }],
        res.data);
    notify(`직원 realm 의 권한 ${res.data.length}개. 이 코드가 토큰의 <code>authLs</code> 에 실립니다.`, 'ok');
});

bind('btn-revision', async () => {
    const res = await auth.request('GET', '/api/rbac/revision', { auth: true, query: { realm: 'EMPLOYEE' } });
    if (failed(res, '리비전 조회')) return;
    $('rbac-result').innerHTML = `<pre>${preview(res.data)}</pre>`;
    notify(`리비전은 realm 전역 정책이 바뀔 때 올라갑니다. 발급된 토큰에 <code>rbacRev</code> 로 실려 `
        + `옛 토큰을 가려내는 데 씁니다.`, 'ok');
});

// ── 직원 등록 ─────────────────────────────────────────────────────────────

bind('btn-register-employee', async () => {
    const employeeId = $('new-employee-id').value.trim();
    const name = $('new-name').value.trim();
    const email = $('new-email').value.trim();
    if (!employeeId || !name || !email) {
        notify('사번, 이름, 이메일을 모두 입력하세요.', 'err');
        return;
    }

    const res = await auth.request('POST', '/api/auth/employee/register', {
        auth: true,
        body: { employeeId, name, email },
    });
    if (failed(res, '직원 등록')) return;

    notify(`등록되었습니다. esntlId=<code>${res.data.essentialId}</code><br>`
        + `이 사람도 이제 <code>${escapeHtml(email)}</code> 로 OTP 로그인할 수 있습니다 `
        + `(역할이 없어 관리 화면은 열리지 않습니다).`, 'ok');
});

// ── realm 격리 실험 ────────────────────────────────────────────────────────

bind('btn-cross-realm', async () => {
    const email = $('email').value.trim();

    // 고객 realm 으로 같은 이메일에 코드를 요청한다.
    const customer = new AuthClient($('base').value, 'customer');
    customer.onRequest(logRequest);

    const sent = await customer.sendEmailCode(email);
    if (sent.blocked) { failed(sent, '발송'); return; }

    // 로컬 고정코드로 고객 realm 로그인을 시도한다. 고객 서랍에 이 이메일이 없으므로 실패해야 한다.
    const login = await customer.loginWithEmailCode(email, '123456');

    if (login.ok) {
        notify('고객 realm 로그인이 통과했습니다. <b>이건 문제입니다</b> — realm 격리가 깨졌습니다.', 'err');
        return;
    }
    notify(`발송은 <code>${sent.status}</code>(계정 열거 방지로 늘 같은 응답), `
        + `로그인은 <code>${login.status}</code> — "${escapeHtml(login.message || '')}"<br>`
        + `이메일은 <code>(subject_type, email)</code> 로 유일합니다. `
        + `같은 주소라도 직원 계정과 고객 계정은 서로 다른 신원입니다.`, 'ok');
});

bind('btn-social-here', async () => {
    const res = await auth.request('POST', `/api/auth/realms/${REALM}/login/social`,
        { body: { provider: 'KAKAO', authorizationCode: 'x' } });
    notify(`(<code>${res.status}</code>) ${escapeHtml(res.message || '')}<br>`
        + `소셜은 최초 로그인에 신원을 새로 만듭니다(JIT). 직원 realm 에서 열려 있으면 `
        + `<b>아무나 소셜 로그인만으로 직원 신원을 만들 수 있어</b> 고객 realm 에서만 엽니다.`, 'ok');
});

// ── 시작 ──────────────────────────────────────────────────────────────────

$('origin').textContent = location.origin;
$('new-employee-id').value = `E${Date.now().toString().slice(-6)}`;
$('new-email').value = `kim-${Date.now()}@example.com`;
