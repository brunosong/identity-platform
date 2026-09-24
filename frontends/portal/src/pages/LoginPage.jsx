import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { tryOtherRealm } from '../api/auth';
import { authorizeUrl } from '../api/google';
import { authorizeUrl as brunosongAuthorizeUrl } from '../api/authorize';
import DevPanel from '../components/DevPanel';
import Notice from '../components/Notice';

/**
 * 로그인 화면.
 *
 * realm 은 경로가 정한다 - `/api/auth/realms/portal/login`. 이 앱은 포털 앱이므로 고정이다.
 * 화면에서 고르게 두면 고객 화면에서 직원으로 로그인하는 길이 생긴다.
 *
 * 구글을 위에 둔 것은 그쪽이 더 안전해서다. 이 화면에 비밀번호를 적지 않는 길이 하나 있으면
 * 사람들은 대개 그쪽을 고른다.
 */
export default function LoginPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const { login, persist, setPersist } = useAuth();

    const signedUpEmail = location.state?.justSignedUp;

    const [form, setForm] = useState({ loginId: signedUpEmail ?? '', password: '' });
    const [notice, setNotice] = useState(
        signedUpEmail
            ? {
                kind: 'ok',
                text: `가입되었습니다. `
                    + (location.state?.passwordless
                        ? '비밀번호를 정하지 않은 계정이라 이메일 인증번호로 로그인해야 합니다.'
                        : '이제 로그인해 보세요.'),
            }
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

    /** 같은 아이디·비밀번호로 어드민 realm 에 로그인해 본다. 받은 토큰은 쓰지 않는다. */
    async function onTryAdminRealm() {
        setBusy(true);
        const result = await tryOtherRealm('admin', form);
        setBusy(false);

        setNotice(result.ok
            ? { kind: 'err', text: '어드민 realm 로그인이 통과했습니다. 이건 문제입니다 - realm 격리가 깨졌습니다.' }
            : {
                kind: 'ok',
                text: `어드민 realm 거부됨 (${result.status}) - "${result.message}". `
                    + '비밀번호는 맞습니다. 자격증명 조회가 (realm, login_id) 로 좁혀져 있어 '
                    + '직원 서랍에는 이 계정이 아예 없습니다.',
            });
    }

    return (
        <div className="auth-page">
            <div className="auth-card">
                <h1>로그인</h1>
                <p className="auth-sub">PORTAL 계정으로 계속하기</p>

                <Notice kind={notice?.kind}>{notice?.text}</Notice>

                <button
                    type="button"
                    className="btn btn-google btn-block"
                    onClick={() => { window.location.href = authorizeUrl(); }}
                >
                    <GoogleMark />
                    구글로 로그인
                </button>

                <button
                    type="button"
                    className="btn btn-ghost btn-block btn-brunosong"
                    onClick={async () => {
                        // 어디서 왔는지 넘긴다. 브라우저가 앱을 떠나므로 이 화면의 상태로는 안 된다.
                        window.location.href = await brunosongAuthorizeUrl({
                            returnTo: location.state?.from,
                        });
                    }}
                >
                    BrunoSong 로그인
                </button>

                <div className="divider"><span>또는</span></div>

                <form onSubmit={onSubmit}>
                    <label htmlFor="loginId">이메일</label>
                    <input
                        id="loginId" type="email" required autoFocus autoComplete="username"
                        placeholder="you@example.com"
                        value={form.loginId} onChange={update('loginId')}
                    />

                    <label htmlFor="password">비밀번호</label>
                    <input
                        id="password" type="password" required autoComplete="current-password"
                        value={form.password} onChange={update('password')}
                    />

                    <button className="btn btn-primary btn-block" type="submit" disabled={busy}>
                        {busy ? '처리 중…' : '로그인'}
                    </button>
                </form>

                <p className="auth-foot">
                    계정이 없으신가요? <Link to="/signup">회원가입</Link>
                </p>
            </div>

            <DevPanel title="이 화면에서 무슨 일이 일어나나">
                <h3>구글 로그인은 왜 이 앱을 떠나나</h3>
                <p className="field-hint">
                    버튼을 누르면 주소창이 <code>accounts.google.com</code> 으로 바뀝니다.
                    비밀번호를 받는 것이 이 앱이 아니라서 이 앱의 코드에는 그것을 다루는 자리가
                    없습니다. 돌아올 때 실려 오는 것은 토큰이 아니라 <code>code</code> 이고,
                    그것을 토큰으로 바꾸는 일은 시크릿을 쥔 auth-service 가 구글과 직접 합니다.
                </p>

                <h3>토큰 보관 방식</h3>
                <label className="toggle">
                    <input
                        type="checkbox"
                        checked={persist}
                        onChange={(e) => setPersist(e.target.checked)}
                    />
                    <span>새로고침해도 로그인 유지 (<code>localStorage</code> 사용)</span>
                </label>
                <p className="field-hint">
                    기본은 <b>메모리</b>입니다. 새로고침하면 로그아웃됩니다. 쿠키의
                    <code>httpOnly</code> 를 포기하고 본문으로 토큰을 받는 순간 토큰은 스크립트가
                    읽을 수 있는 자리에 놓이고, 남은 완화책은 오래 남는 자리에 두지 않는 것뿐입니다.
                </p>

                <h3>realm 격리 확인</h3>
                <p className="field-hint">
                    위에 적은 <b>같은 이메일·비밀번호</b>로 어드민 realm 에 로그인을 시도합니다.
                    자격증명은 맞지만 실패해야 합니다.
                </p>
                <div className="row">
                    <button onClick={onTryAdminRealm} disabled={busy || !form.loginId}>
                        어드민 realm 으로 로그인 시도
                    </button>
                </div>
            </DevPanel>
        </div>
    );
}

/** 구글 로고. 구글 브랜드 가이드가 원본 색을 그대로 쓰라고 해서 경로를 그대로 넣는다. */
function GoogleMark() {
    return (
        <svg className="google-mark" viewBox="0 0 48 48" aria-hidden="true">
            <path fill="#4285F4" d="M45.1 24.5c0-1.6-.1-3.2-.4-4.7H24v8.9h11.8c-.5 2.7-2 5-4.4 6.6v5.5h7.1c4.1-3.8 6.6-9.4 6.6-16.3z" />
            <path fill="#34A853" d="M24 46c5.9 0 10.9-2 14.5-5.3l-7.1-5.5c-2 1.3-4.5 2.1-7.4 2.1-5.7 0-10.6-3.9-12.3-9.1H4.3v5.7C7.9 41.1 15.4 46 24 46z" />
            <path fill="#FBBC05" d="M11.7 28.2c-.4-1.3-.7-2.7-.7-4.2s.2-2.9.7-4.2v-5.7H4.3C2.8 17.1 2 20.4 2 24s.8 6.9 2.3 9.9l7.4-5.7z" />
            <path fill="#EA4335" d="M24 10.7c3.2 0 6.1 1.1 8.4 3.3l6.3-6.3C34.9 4.1 29.9 2 24 2 15.4 2 7.9 6.9 4.3 14.1l7.4 5.7c1.7-5.2 6.6-9.1 12.3-9.1z" />
        </svg>
    );
}
