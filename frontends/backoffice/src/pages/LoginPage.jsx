import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { authorizeUrl } from '../api/authorize';
import Notice from '../components/Notice';

/**
 * 로그인 화면 — 버튼 하나뿐이다.
 *
 * <b>이 앱은 자격증명을 받지 않는다.</b> 버튼을 누르면 브라우저가 auth-service 로 떠나고,
 * 돌아올 때 들고 오는 것은 토큰이 아니라 code 한 장이다. 전에는 여기서 이메일과 인증번호를
 * 받아 API 로 넘겼는데(ROPC), 그러면 자격증명이 이 앱을 거치고 로그인 절차를 바꿀 때마다
 * 이 앱을 다시 배포해야 한다.
 *
 * <p>직원이 인증번호로 로그인한다는 사실도 이제 이 앱은 모른다. 어떤 방법으로 사람을 확인할지는
 * auth 가 정하고, 그 화면도 auth 가 그린다.
 */
export default function LoginPage() {
    const location = useLocation();
    const { persist, setPersist } = useAuth();

    const justRegistered = location.state?.justRegistered;
    const [busy, setBusy] = useState(false);

    const notice = justRegistered
        ? {
            kind: 'info',
            text: `방금 만든 계정(${justRegistered})으로 로그인해 봅니다.`,
        }
        : null;

    async function onLogin() {
        setBusy(true);
        window.location.href = await authorizeUrl();
    }


    return (
        <div className="page narrow">
            <h1>로그인</h1>
            <p className="lead">
                로그인은 <b>인증 서버에서</b> 합니다. 이 화면에는 입력칸이 없습니다.
            </p>

            <Notice kind={notice?.kind}>{notice?.text}</Notice>

            <div className="card">
                <div className="row">
                    <button className="primary" onClick={onLogin} disabled={busy}>
                        {busy ? '이동 중…' : 'BrunoSong 로그인'}
                    </button>
                </div>
                <p className="field-hint">
                    누르면 주소창이 <code>localhost:8080/realms/admin/auth</code> 로 바뀝니다.
                    거기서 인증번호를 받아 로그인하면 <code>code</code> 를 들고 이 앱으로 돌아오고,
                    그 code 를 토큰으로 바꿉니다. 비밀번호도 인증번호도 이 앱을 거치지 않습니다.
                    <br /><br />
                    같은 realm 의 다른 앱에서 이미 로그인했다면 화면이 뜨지 않고 바로 돌아옵니다.
                </p>
            </div>

            <div className="card">
                <h2>계정이 없다면</h2>
                <p className="hint">
                    <b>스스로 가입할 수 없습니다.</b> 직원 계정은 <code>AUTHZ_MANAGE</code> 를 가진
                    관리자가 만들어 줍니다(직원 등록 화면). 직원 realm 의 이름은 주소창에 그대로
                    나오므로, 가입을 열어 두면 아무나 직원 신원과 토큰을 얻게 됩니다.
                    <br /><br />
                    최초 한 명은 만들어 줄 사람이 없어서 데이터로 심습니다 — 닭과 달걀 문제입니다.
                    local 시드가 <code>admin@example.com</code> 을 심어둡니다.
                </p>
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
        </div>
    );
}
