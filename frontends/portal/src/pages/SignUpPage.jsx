import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { register, registerWithEmail, sendRegistrationCode } from '../api/auth';
import Notice from '../components/Notice';

/**
 * 가입 화면 — <b>방식이 둘이다.</b>
 *
 * 가입도 realm 경로 위에 있다(`/api/auth/realms/portal/register...`). 로그인·로그아웃과 같은
 * 모양이다. 만들 신원의 종류는 요청이 아니라 realm 이 정하고, <b>어떤 방식이 열리는지도 realm 이
 * 정한다</b> — 포털은 둘 다, 어드민은 이메일 가입만 열려 있다.
 *
 * <h3>두 방식의 진짜 차이</h3>
 * 비밀번호 가입은 <b>이메일 소유를 확인하지 않는다.</b> 남의 주소를 적어도 계정이 만들어진다.
 * 이메일 가입은 인증번호를 받아내야 하므로 그 주소의 주인임이 증명된다. 편의(즉시 가입)와
 * 확실함(주소 확인)을 맞바꾸는 것이고, 그것을 고르게 두는 화면이다.
 *
 * <p>어느 쪽이든 가입이 끝나도 토큰은 안 나온다. 가입과 로그인은 별개다.
 */
export default function SignUpPage() {
    const navigate = useNavigate();
    const [method, setMethod] = useState('password');

    return (
        <div className="page narrow">
            <h1>가입</h1>
            <p className="lead">
                고객은 별도 아이디가 없습니다. <b>이메일이 곧 로그인 아이디</b>입니다.
            </p>

            <div className="stepline" role="tablist">
                <button
                    className={`step ${method === 'password' ? 'now' : ''}`}
                    role="tab" aria-selected={method === 'password'}
                    onClick={() => setMethod('password')}
                >
                    비밀번호로 가입
                </button>
                <button
                    className={`step ${method === 'email' ? 'now' : ''}`}
                    role="tab" aria-selected={method === 'email'}
                    onClick={() => setMethod('email')}
                >
                    이메일 인증으로 가입
                </button>
            </div>

            {method === 'password' ? <PasswordSignUp navigate={navigate} /> : <EmailSignUp navigate={navigate} />}

            <div className="card muted-card">
                <h2>두 방식은 무엇이 다른가</h2>
                <table className="claims">
                    <tbody>
                    <tr>
                        <td className="claim-key"><code>비밀번호</code><span className="claim-title">정하는가</span></td>
                        <td className="claim-value">정합니다 / <b>정하지 않습니다</b></td>
                    </tr>
                    <tr>
                        <td className="claim-key"><code>이메일 소유</code><span className="claim-title">확인하는가</span></td>
                        <td className="claim-value">
                            <b>확인하지 않습니다</b> / 확인합니다
                            <p className="claim-note">
                                비밀번호 가입은 남의 주소를 적어도 계정이 만들어집니다. 이메일 가입은
                                인증번호를 받아내야 하므로 그 주소의 주인만 가입할 수 있습니다.
                            </p>
                        </td>
                    </tr>
                    <tr>
                        <td className="claim-key"><code>로그인 방법</code><span className="claim-title">이후</span></td>
                        <td className="claim-value">
                            비밀번호 또는 이메일 OTP / 이메일 OTP
                            <p className="claim-note">
                                비밀번호로 가입해도 <b>이메일 계정이 함께 만들어져서</b> OTP 로도
                                로그인됩니다. 반대로 이메일로 가입한 사람에게는 정한 비밀번호가 없습니다.
                            </p>
                        </td>
                    </tr>
                    <tr>
                        <td className="claim-key"><code>CUSTOMER</code><span className="claim-title">기본 역할</span></td>
                        <td className="claim-value">
                            둘 다 자동으로 받습니다
                            <p className="claim-note">
                                역할을 주는 것은 가입 서비스가 아니라 등록 이벤트를 받는 realm 별
                                리스너입니다. 그래서 방식이 늘어도 이 부분은 그대로입니다.
                            </p>
                        </td>
                    </tr>
                    </tbody>
                </table>
            </div>
        </div>
    );
}

/** 비밀번호로 가입. 즉시 끝나지만 이 주소의 주인인지는 아무도 확인하지 않는다. */
function PasswordSignUp({ navigate }) {
    const [form, setForm] = useState({
        email: `hong-${Date.now()}@example.com`,
        password: 'pw12345678',
        name: '홍길동',
        phoneNumber: '010-0000-0000',
    });
    const [notice, setNotice] = useState(null);
    const [busy, setBusy] = useState(false);

    const update = (key) => (e) => setForm({ ...form, [key]: e.target.value });

    async function onSubmit(e) {
        e.preventDefault();
        setBusy(true);
        setNotice(null);

        const result = await register(form);
        setBusy(false);

        if (!result.ok) {
            setNotice({ kind: 'err', text: result.message ?? `가입 실패 (${result.status})` });
            return;
        }
        // 가입 직후 자동 로그인하지 않는다. 서버가 토큰을 주지 않기 때문이다 —
        // 로그인은 별도 요청이고, 그 경계를 화면에서도 유지한다.
        navigate('/login', {
            state: { justSignedUp: form.email, principalId: result.data.principalId },
        });
    }

    return (
        <>
            <Notice kind={notice?.kind}>{notice?.text}</Notice>

            <form className="card" onSubmit={onSubmit}>
                <label htmlFor="email">이메일</label>
                <input id="email" type="email" required value={form.email} onChange={update('email')} />
                <p className="field-hint warn">
                    이 주소로 인증번호를 보내지 않습니다 — <b>주인인지 확인하지 않는다</b>는 뜻입니다.
                </p>

                <label htmlFor="password">비밀번호</label>
                <input
                    id="password" type="password" required minLength={8}
                    value={form.password} onChange={update('password')}
                />
                <p className="field-hint">8자 이상</p>

                <label htmlFor="name">이름</label>
                <input id="name" required value={form.name} onChange={update('name')} />

                <label htmlFor="phone">휴대폰</label>
                <input id="phone" value={form.phoneNumber} onChange={update('phoneNumber')} />

                <div className="row">
                    <button className="primary" type="submit" disabled={busy}>
                        {busy ? '처리 중…' : '가입'}
                    </button>
                    <Link className="button-like" to="/login">이미 계정이 있어요</Link>
                </div>
            </form>
        </>
    );
}

/** 이메일 인증번호로 가입. 비밀번호를 정하지 않는 대신 주소의 주인임이 증명된다. */
function EmailSignUp({ navigate }) {
    const [step, setStep] = useState('email');
    const [form, setForm] = useState({
        email: `otp-${Date.now()}@example.com`,
        name: '홍길동',
        phoneNumber: '010-0000-0000',
        verificationCode: '123456',
    });
    const [notice, setNotice] = useState(null);
    const [busy, setBusy] = useState(false);

    const update = (key) => (e) => setForm({ ...form, [key]: e.target.value });

    async function onSendCode(e) {
        e.preventDefault();
        setBusy(true);
        setNotice(null);

        const result = await sendRegistrationCode(form.email.trim());
        setBusy(false);

        if (!result.ok) {
            setNotice({ kind: 'err', text: result.message ?? `발송 실패 (${result.status})` });
            return;
        }
        setStep('code');
        setNotice({
            kind: 'ok',
            text: `발송 요청됨 (${result.status}). 이미 가입된 주소여도 같은 응답이 옵니다 — `
                + '"이미 가입된 이메일입니다" 를 돌려주면 주소를 넣어보는 것만으로 누가 가입돼 '
                + '있는지 훑을 수 있기 때문입니다. 대신 그런 주소에는 코드가 나가지 않아 다음 '
                + '단계에서 실패합니다. local 프로파일은 고정코드 123456 을 씁니다.',
        });
    }

    async function onSubmit(e) {
        e.preventDefault();
        setBusy(true);
        setNotice(null);

        const result = await registerWithEmail({
            email: form.email.trim(),
            name: form.name.trim(),
            phoneNumber: form.phoneNumber.trim() || null,
            verificationCode: form.verificationCode.trim(),
        });
        setBusy(false);

        if (!result.ok) {
            setNotice({ kind: 'err', text: result.message ?? `가입 실패 (${result.status})` });
            return;
        }
        navigate('/login', {
            state: { justSignedUp: form.email.trim(), principalId: result.data.principalId, passwordless: true },
        });
    }

    return (
        <>
            <Notice kind={notice?.kind}>{notice?.text}</Notice>

            <form className="card" onSubmit={step === 'email' ? onSendCode : onSubmit}>
                <label htmlFor="otp-email">이메일</label>
                <input
                    id="otp-email" type="email" required
                    value={form.email}
                    onChange={(e) => { setForm({ ...form, email: e.target.value }); setStep('email'); }}
                />

                {step === 'code' && (
                    <>
                        <label htmlFor="otp-name">이름</label>
                        <input id="otp-name" required value={form.name} onChange={update('name')} />

                        <label htmlFor="otp-phone">휴대폰</label>
                        <input id="otp-phone" value={form.phoneNumber} onChange={update('phoneNumber')} />

                        <label htmlFor="otp-code">인증번호</label>
                        <input
                            id="otp-code" required inputMode="numeric" placeholder="6자리"
                            value={form.verificationCode} onChange={update('verificationCode')}
                        />
                        <p className="field-hint">
                            <b>local 프로파일은 메일을 보내지 않고 고정코드 <code>123456</code> 을 씁니다.</b>
                        </p>
                    </>
                )}

                <div className="row">
                    <button className="primary" type="submit" disabled={busy}>
                        {busy ? '처리 중…' : step === 'email' ? '인증번호 발송' : '가입'}
                    </button>
                    <Link className="button-like" to="/login">이미 계정이 있어요</Link>
                </div>
            </form>

            <div className="card muted-card">
                <h2>가입 뒤 로그인은 어떻게</h2>
                <p className="hint">
                    정한 비밀번호가 없으므로 <b>이메일 OTP 로 로그인</b>합니다. 로그인 화면의
                    비밀번호 칸은 이 계정에는 쓰이지 않습니다.
                    <br /><br />
                    방금 받은 인증번호는 가입에 쓰여 <b>소모</b>됩니다. 로그인하려면 새 코드를
                    받아야 하고, 재발송 쿨다운(60초)이 지나야 나갑니다.
                </p>
            </div>
        </>
    );
}
