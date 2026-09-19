import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { loginToOtherRealm, sendCodeToOtherRealm } from '../api/auth';
import Notice from '../components/Notice';

/**
 * 로그인 화면 — 이메일 OTP.
 *
 * <b>직원은 비밀번호 계정을 갖지 않는다.</b> 그래서 이 화면에는 비밀번호 칸이 없다. 화면의 취향이
 * 아니라 서버가 그렇게 생긴 것이다 — `RegisterEmployeeAccountService` 가 직원에게 이메일 계정만
 * 만든다.
 *
 * <p>로그인이 <b>두 번의 요청</b>인 것이 보이게 단계를 갈라 둔다. 코드 발송에는 토큰이 없고,
 * 토큰은 코드 검증에서만 나온다.
 */
export default function LoginPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const { sendCode, login, persist, setPersist } = useAuth();

    const justRegistered = location.state?.justRegistered;

    // 방금 만든 계정으로 넘어왔어도 1단계부터 시작한다. 그 계정으로는 아직 코드를 보낸 적이 없다.
    const [step, setStep] = useState('email');
    const [email, setEmail] = useState(justRegistered ?? 'admin@example.com');
    const [code, setCode] = useState('123456');
    const [notice, setNotice] = useState(
        justRegistered
            ? {
                kind: 'info',
                text: `방금 만든 계정(${justRegistered})으로 로그인해 봅니다. 인증번호를 먼저 발송하세요.`
                    + (location.state?.selfSignup
                        ? ' 이 계정에는 역할이 없어서, 로그인은 되지만 관리 화면은 열리지 않습니다.'
                        : ''),
            }
            : null,
    );
    const [busy, setBusy] = useState(false);

    async function onSendCode(e) {
        e.preventDefault();
        setBusy(true);
        setNotice(null);

        const result = await sendCode(email.trim());
        setBusy(false);

        if (!result.ok) {
            setNotice({ kind: 'err', text: result.message ?? `발송 실패 (${result.status})` });
            return;
        }
        setStep('code');
        setNotice({
            kind: 'ok',
            text: `발송 요청됨 (${result.status}). 가입 여부와 무관하게 같은 응답을 주므로, `
                + '이 응답만으로는 그 이메일이 직원인지 알 수 없습니다 — 계정 열거를 막기 위해서입니다. '
                + 'local 프로파일은 메일을 보내지 않고 고정코드 123456 을 씁니다.',
        });
    }

    async function onLogin(e) {
        e.preventDefault();
        setBusy(true);
        setNotice(null);

        const result = await login({ email: email.trim(), verificationCode: code.trim() });
        setBusy(false);

        if (!result.ok) {
            setNotice({ kind: 'err', text: result.message ?? `로그인 실패 (${result.status})` });
            return;
        }
        navigate(location.state?.from ?? '/', { replace: true });
    }

    /**
     * 같은 이메일로 포털 realm 에 OTP 로그인을 시도한다.
     *
     * 발송은 어느 쪽이든 202 다(계정 열거 방지). 하지만 고객 서랍에는 이 이메일이 없어서
     * 코드가 만들어지지 않고, 그래서 로그인이 실패한다. 응답만으로는 구분되지 않는 것이 의도고,
     * 실제로 갈리는 곳은 그다음이다.
     */
    async function onTryPortalRealm() {
        setBusy(true);
        setNotice(null);

        const sent = await sendCodeToOtherRealm('portal', email.trim());
        if (sent.blocked) {
            setBusy(false);
            setNotice({ kind: 'err', text: sent.message });
            return;
        }
        const attempt = await loginToOtherRealm('portal', {
            email: email.trim(), verificationCode: code.trim() || '123456',
        });
        setBusy(false);

        if (attempt.ok) {
            setNotice({
                kind: 'err',
                text: '포털 realm 로그인이 통과했습니다. 이건 문제입니다 — realm 격리가 깨졌습니다.',
            });
            return;
        }
        setNotice({
            kind: 'ok',
            text: `발송은 ${sent.status}(계정 열거 방지로 늘 같은 응답), 로그인은 ${attempt.status} — `
                + `"${attempt.message ?? ''}". 이메일은 (realm, email) 로 유일합니다. `
                + '같은 주소라도 직원 계정과 고객 계정은 서로 다른 신원이고, 고객 서랍에는 '
                + '이 주소가 아예 없습니다.',
        });
    }

    return (
        <div className="page narrow">
            <h1>로그인</h1>
            <p className="lead">
                직원은 비밀번호가 없습니다. <b>이메일로 받은 인증번호</b>로 들어옵니다.
            </p>

            <div className="stepline">
                <span className={`step ${step === 'email' ? 'now' : 'done'}`}>1 · 인증번호 발송</span>
                <span>→</span>
                <span className={`step ${step === 'code' ? 'now' : ''}`}>2 · 인증번호 확인 → 토큰</span>
            </div>

            <Notice kind={notice?.kind}>{notice?.text}</Notice>

            <form className="card" onSubmit={step === 'email' ? onSendCode : onLogin}>
                <label htmlFor="email">직원 이메일</label>
                <input
                    id="email" type="email" required autoFocus
                    value={email}
                    onChange={(e) => { setEmail(e.target.value); setStep('email'); }}
                />

                {step === 'code' && (
                    <>
                        <label htmlFor="code">인증번호</label>
                        <input
                            id="code" required inputMode="numeric" placeholder="6자리"
                            value={code} onChange={(e) => setCode(e.target.value)}
                        />
                        <p className="field-hint">
                            <b>local 프로파일은 메일을 보내지 않고 고정코드 <code>123456</code> 을 씁니다.</b>
                        </p>
                    </>
                )}

                <div className="row">
                    <button className="primary" type="submit" disabled={busy}>
                        {busy ? '처리 중…' : step === 'email' ? '인증번호 발송' : '로그인'}
                    </button>
                    {step === 'code' && (
                        <button type="button" onClick={onSendCode} disabled={busy}>
                            다시 발송
                        </button>
                    )}
                </div>
            </form>

            <div className="card">
                <h2>계정이 없다면</h2>
                <p className="hint">
                    <b>스스로 가입할 수 있습니다</b> — 이메일 인증번호만 있으면 됩니다. 다만 그렇게
                    만든 계정에는 <b>역할이 하나도 없어서</b> 로그인은 되지만 아무 관리 화면도
                    열리지 않습니다. 쓸 수 있게 만드는 것은 <code>AUTHZ_MANAGE</code> 를 가진
                    운영자입니다.
                    <br /><br />
                    최초 한 명은 역할을 줄 사람이 없어서 데이터로 심습니다 — 닭과 달걀 문제입니다.
                    local 시드가 <code>admin@example.com</code> 을 심어둡니다.
                </p>
                <div className="row">
                    <Link className="button-like" to="/signup">이메일로 가입</Link>
                </div>
            </div>

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
                <p className="field-hint warn">
                    기본은 <b>메모리</b>입니다. 이 앱의 토큰에는 계정을 만들고 역할을 배정할 수 있는
                    권한이 실립니다 — 고객 토큰이 새면 그 사람 프로필이 새지만, 이 토큰이 새면
                    직원이 만들어집니다. 켜기 전에 무엇을 내주는지 보세요.
                </p>
            </div>

            <div className="card muted-card">
                <h2>realm 격리 확인</h2>
                <p className="field-hint">
                    위에 적은 <b>같은 이메일</b>로 포털 realm 에 인증번호를 요청하고 로그인을
                    시도합니다. 발송은 성공하지만 로그인은 실패해야 합니다.
                </p>
                <div className="row">
                    <button onClick={onTryPortalRealm} disabled={busy || !email}>
                        포털 realm 으로 OTP 로그인 시도
                    </button>
                </div>
            </div>
        </div>
    );
}
