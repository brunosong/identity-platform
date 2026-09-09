import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { register } from '../api/auth';
import Notice from '../components/Notice';

/**
 * 가입 화면.
 *
 * 가입도 <b>realm 경로 위에 있다</b>(`/api/auth/realms/portal/register`). 로그인·로그아웃과 같은
 * 모양이다. 만들 신원의 종류는 요청이 아니라 realm 이 정하고, 어드민 realm 에서는 이 경로가 404 다 —
 * 셀프 가입을 여는지는 realm 의 설정이기 때문이다(Keycloak 의 "User registration" 토글과 같다).
 *
 * 가입이 끝나도 토큰은 안 나온다. 가입과 로그인은 별개다.
 */
export default function SignUpPage() {
    const navigate = useNavigate();
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
        <div className="page narrow">
            <h1>가입</h1>
            <p className="lead">
                고객은 별도 아이디가 없습니다. <b>이메일이 곧 로그인 아이디</b>입니다.
            </p>

            <Notice kind={notice?.kind}>{notice?.text}</Notice>

            <form className="card" onSubmit={onSubmit}>
                <label htmlFor="email">이메일</label>
                <input id="email" type="email" required value={form.email} onChange={update('email')} />

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

            <div className="card muted-card">
                <h2>여기서 무슨 일이 일어나나</h2>
                <ol className="steps">
                    <li><code>POST /api/auth/realms/portal/register</code> — auth-service 가 신원(Principal)과 비밀번호 자격증명을 만듭니다.</li>
                    <li>auth 가 <code>CUSTOMER</code> 역할을 자동으로 붙입니다. 그 역할이 없으면 가입 자체가 롤백됩니다.</li>
                    <li><b>토큰은 나오지 않습니다.</b> 가입과 로그인은 별개의 요청입니다.</li>
                    <li>customer-service 에는 아직 아무것도 없습니다 — 프로필은 마이페이지에서 직접 만듭니다.</li>
                </ol>
            </div>
        </div>
    );
}
