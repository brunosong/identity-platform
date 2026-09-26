import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { authorizeUrl } from '../api/authorize';
import DevPanel from '../components/DevPanel';
import Notice from '../components/Notice';

/**
 * 로그인 화면. 버튼 하나뿐이다.
 *
 * 비밀번호도, 구글도, 인증번호도 이 앱이 받지 않는다. 버튼을 누르면 브라우저가 auth-service 의
 * 로그인 화면으로 가고, 어떤 방법으로 사람을 확인할지는 거기서 정한다. 이 앱이 돌려받는 것은
 * code 한 장이다.
 */
export default function LoginPage() {
    const location = useLocation();

    const signedUpEmail = location.state?.justSignedUp;
    const [notice] = useState(
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

    async function onLogin() {
        // 어디서 왔는지 넘긴다. 브라우저가 앱을 떠나므로 이 화면의 상태로는 안 된다.
        window.location.href = await authorizeUrl({ returnTo: location.state?.from });
    }

    return (
        <div className="auth-page">
            <div className="auth-card">
                <h1>로그인</h1>
                <p className="auth-sub">PORTAL 계정으로 계속하기</p>

                <Notice kind={notice?.kind}>{notice?.text}</Notice>

                <button type="button" className="btn btn-primary btn-block" onClick={onLogin}>
                    BrunoSong 로그인
                </button>

                <p className="auth-foot">
                    계정이 없으신가요? <Link to="/signup">회원가입</Link>
                </p>
            </div>

            <DevPanel title="이 화면에서 무슨 일이 일어나나">
                <h3>왜 입력칸이 없나</h3>
                <p className="field-hint">
                    버튼을 누르면 주소창이 auth-service 로 바뀝니다. 비밀번호, 구글, 인증번호는 모두
                    그 화면에서 고릅니다. 이 앱의 코드에는 비밀번호를 다루는 자리가 없고, 돌아올 때
                    실려 오는 것은 토큰이 아니라 <code>code</code> 입니다. 그것을 토큰으로 바꿀 때
                    PKCE 원본을 함께 내므로, <code>code</code> 를 주운 쪽은 쓰지 못합니다.
                </p>

                <h3>토큰 보관 방식</h3>
                <p className="field-hint">
                    <b>메모리에만 둡니다.</b> 새로고침하면 로그아웃됩니다. 쿠키의
                    <code>httpOnly</code> 를 포기하고 본문으로 토큰을 받는 순간 토큰은 스크립트가
                    읽을 수 있는 자리에 놓이고, 남은 완화책은 오래 남는 자리에 두지 않는 것뿐입니다.
                </p>
            </DevPanel>
        </div>
    );
}
