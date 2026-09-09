import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { tryOtherRealm } from '../api/auth';
import Notice from '../components/Notice';

/**
 * 로그인 화면.
 *
 * 경로가 realm 을 정한다 — `/api/auth/realms/portal/login`. 이 앱은 포털 앱이므로 고정이다.
 *
 * 아래 "realm 격리 확인" 은 같은 자격증명을 어드민 realm 에 넣어보는 실험이다. 비밀번호가 맞는데도
 * 실패하는 것이 요점이다.
 */
export default function LoginPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const { login, persist, setPersist } = useAuth();

    const signedUpEmail = location.state?.justSignedUp;

    const [form, setForm] = useState({
        loginId: signedUpEmail ?? '',
        password: 'pw12345678',
    });
    const [notice, setNotice] = useState(
        signedUpEmail
            ? { kind: 'ok', text: `가입되었습니다 (principalId ${location.state.principalId}). 이제 로그인해 보세요.` }
            : null,
    );
    const [busy, setBusy] = useState(false);

    const update = (key) => (e) => setForm({ ...form, [key]: e.target.value });

    async function onSubmit(e) {
        e.preventDefault();
        setBusy(true);
        setNotice(null);

        const result = await login(form);
        setBusy(false);

        if (!result.ok) {
            setNotice({ kind: 'err', text: result.message ?? `로그인 실패 (${result.status})` });
            return;
        }
        navigate(location.state?.from ?? '/me', { replace: true });
    }

    /** 같은 아이디·비밀번호로 직원 realm 에 로그인해 본다. 받은 토큰은 쓰지 않는다. */
    async function onTryEmployeeRealm() {
        setBusy(true);
        const result = await tryOtherRealm('admin', form);
        setBusy(false);

        setNotice(result.ok
            ? { kind: 'err', text: '어드민 realm 로그인이 통과했습니다. 이건 문제입니다 — realm 격리가 깨졌습니다.' }
            : {
                kind: 'ok',
                text: `어드민 realm 거부됨 (${result.status}) — "${result.message}". `
                    + '비밀번호는 맞습니다. 자격증명 조회가 (subject_type, login_id) 로 좁혀져 있어 '
                    + '직원 서랍에는 이 계정이 아예 없습니다. 없는 아이디와 같은 메시지로 끝나 '
                    + '계정이 어디에 있는지도 새지 않습니다.',
            });
    }

    return (
        <div className="page narrow">
            <h1>로그인</h1>
            <p className="lead">
                토큰은 쿠키가 아니라 <b>응답 본문</b>으로 옵니다. 이후 요청에는
                <code>Authorization: Bearer</code> 로 직접 싣습니다.
            </p>

            <Notice kind={notice?.kind}>{notice?.text}</Notice>

            <form className="card" onSubmit={onSubmit}>
                <label htmlFor="loginId">이메일</label>
                <input
                    id="loginId" type="email" required autoFocus
                    value={form.loginId} onChange={update('loginId')}
                />

                <label htmlFor="password">비밀번호</label>
                <input
                    id="password" type="password" required
                    value={form.password} onChange={update('password')}
                />

                <div className="row">
                    <button className="primary" type="submit" disabled={busy}>
                        {busy ? '처리 중…' : '로그인'}
                    </button>
                    <Link className="button-like" to="/signup">가입하기</Link>
                </div>
            </form>

            <div className="card">
                <h2>토큰 보관 방식</h2>
                <label className="toggle">
                    <input
                        type="checkbox"
                        checked={persist}
                        onChange={(e) => setPersist(e.target.checked)}
                    />
                    <span>새로고침해도 로그인 유지 (<code>localStorage</code> 사용)</span>
                </label>
                <p className="field-hint">
                    기본은 <b>메모리</b>입니다 — 새로고침하면 로그아웃됩니다. 불편하지만 그게 정직한
                    기본값입니다. 쿠키의 <code>httpOnly</code> 를 포기하고 본문으로 토큰을 받는 순간
                    토큰은 스크립트가 읽을 수 있는 자리에 놓이고, 남은 완화책은 "오래 남는 자리에
                    두지 않기" 뿐이기 때문입니다. <code>localStorage</code> 는 브라우저를 껐다 켜도 남습니다.
                </p>
            </div>

            <div className="card muted-card">
                <h2>realm 격리 확인</h2>
                <p className="field-hint">
                    위에 적은 <b>같은 이메일·비밀번호</b>로 어드민 realm 에 로그인을 시도합니다.
                    자격증명은 맞지만 실패해야 합니다.
                </p>
                <div className="row">
                    <button onClick={onTryEmployeeRealm} disabled={busy || !form.loginId}>
                        어드민 realm 으로 로그인 시도
                    </button>
                </div>
            </div>
        </div>
    );
}
