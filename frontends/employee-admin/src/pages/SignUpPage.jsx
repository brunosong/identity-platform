import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { registerWithEmail, sendRegistrationCode, tryPasswordRegister } from '../api/auth';
import Notice from '../components/Notice';

/**
 * 직원 가입 — <b>본인이 이메일만으로 스스로 만든다.</b> 관리자가 필요 없다.
 *
 * <h3>비밀번호 칸이 없다</h3>
 * 직원 계정은 이메일 계정만 갖는다. 정할 비밀번호가 없고, 로그인은 그 주소로 오는 인증번호로 한다.
 * 그래서 어드민 realm 에는 비밀번호 가입이 <b>존재할 수 없다</b> — 아래 버튼으로 확인할 수 있다.
 *
 * <h3>열어도 되는 이유</h3>
 * "아무나 자기 자신을 직원으로 만든다" 는 걱정은 <b>역할이 막는다.</b> 여기서 만든 신원에는 어떤
 * 역할도 붙지 않는다 — 기본 역할을 주는 리스너가 고객 realm 만 상대하기 때문이다. 그래서 스스로
 * 만든 계정은 로그인은 되지만 아무것도 열지 못하는 껍데기이고, 쓸 수 있게 만드는 것은 여전히
 * {@code AUTHZ_MANAGE} 를 가진 운영자다.
 *
 * <p>신원을 만드는 일과 권한을 주는 일이 갈려 있다는 것이 여기서 값을 한다. 둘이 붙어 있었다면
 * 이 화면은 열 수 없다.
 */
export default function SignUpPage() {
    const navigate = useNavigate();

    const [step, setStep] = useState('email');
    const [form, setForm] = useState({
        email: `staff-${Date.now()}@example.com`,
        name: '',
        phoneNumber: '',
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
                + '"이미 가입된 이메일입니다" 를 돌려주면 주소를 넣어보는 것만으로 누가 직원인지 '
                + '훑을 수 있기 때문입니다. 다만 이미 가입된 주소에는 코드가 나가지 않아 '
                + '다음 단계에서 실패합니다. local 프로파일은 고정코드 123456 을 씁니다.',
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
        navigate('/login', { state: { justRegistered: form.email.trim(), selfSignup: true } });
    }

    /** 포털이 쓰는 비밀번호 가입 경로를 어드민으로 불러본다. 404 여야 한다. */
    async function onTryPasswordRegister() {
        setBusy(true);
        setNotice(null);

        const result = await tryPasswordRegister({
            email: form.email.trim(),
            name: form.name.trim() || '아무개',
        });
        setBusy(false);

        setNotice(result.ok
            ? {
                kind: 'err',
                text: '어드민 realm 에서 비밀번호 가입이 통과했습니다. 이건 문제입니다 — '
                    + '이 서비스가 만들 수 없는 계정(직원 + 비밀번호)을 요구하는 경로가 열린 것입니다.',
            }
            : {
                kind: 'ok',
                text: `거부됨 (${result.status}) — "${result.message ?? ''}". `
                    + '403 이 아니라 404 인 것이 요점입니다. "여기에도 그 API 가 있긴 한데 막혀 있다" 를 '
                    + '알려줄 이유가 없기 때문입니다. 어느 realm 이 어떤 방식을 여는지는 컨트롤러가 '
                    + '아니라 Realm enum 이 들고 있습니다.',
            });
    }

    return (
        <div className="page narrow">
            <h1>직원 가입</h1>
            <p className="lead">
                비밀번호를 정하지 않습니다. <b>이메일 인증번호</b>로 주소의 주인임을 확인하고 계정을 만듭니다.
            </p>

            <div className="stepline">
                <span className={`step ${step === 'email' ? 'now' : 'done'}`}>1 · 인증번호 발송</span>
                <span>→</span>
                <span className={`step ${step === 'code' ? 'now' : ''}`}>2 · 확인 → 계정 생성</span>
            </div>

            <Notice kind={notice?.kind}>{notice?.text}</Notice>

            <form className="card" onSubmit={step === 'email' ? onSendCode : onSubmit}>
                <label htmlFor="email">이메일 (로그인 식별자)</label>
                <input
                    id="email" type="email" required autoFocus
                    value={form.email}
                    onChange={(e) => { setForm({ ...form, email: e.target.value }); setStep('email'); }}
                />

                {step === 'code' && (
                    <>
                        <label htmlFor="name">이름</label>
                        <input id="name" required placeholder="김직원" value={form.name} onChange={update('name')} />

                        <label htmlFor="phone">휴대폰 (선택)</label>
                        <input id="phone" value={form.phoneNumber} onChange={update('phoneNumber')} />

                        <label htmlFor="code">인증번호</label>
                        <input
                            id="code" required inputMode="numeric" placeholder="6자리"
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
                <h2>가입하면 무엇이 생기나</h2>
                <ol className="steps">
                    <li>
                        <code>POST /api/auth/realms/admin/register/email/send-code</code> — 가입용
                        인증번호. <b>이미 등록된 주소에는 나가지 않습니다</b>(로그인용과 조건이 반대).
                    </li>
                    <li>
                        <code>POST /api/auth/realms/admin/register/email</code> — 인증번호를 확인하고
                        신원(Principal)과 이메일 계정을 만듭니다. 비밀번호 계정은 만들지 않습니다.
                    </li>
                    <li>
                        <b>역할은 하나도 붙지 않습니다.</b> 로그인은 되지만 관리 화면은 열리지 않습니다 —
                        그것이 이 화면을 열어둘 수 있는 이유입니다.
                    </li>
                    <li><b>토큰은 나오지 않습니다.</b> 가입과 로그인은 별개의 요청입니다.</li>
                </ol>
            </div>

            <div className="card muted-card">
                <h2>비밀번호로는 가입할 수 없다</h2>
                <p className="field-hint">
                    고객 포털이 쓰는 비밀번호 가입 경로를 어드민 realm 으로 불러봅니다
                    (<code>POST /api/auth/realms/admin/register</code>). 포털에서는 열려 있는 그 경로가
                    여기서는 없는 것으로 다뤄집니다.
                </p>
                <div className="row">
                    <button onClick={onTryPasswordRegister} disabled={busy}>
                        어드민 realm 에 비밀번호 가입 시도
                    </button>
                </div>
            </div>
        </div>
    );
}
