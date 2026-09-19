import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { consumeState } from '../api/google';
import Notice from '../components/Notice';

/**
 * 구글이 브라우저를 돌려보내는 자리.
 *
 * 주소창에 code 가 실려 온다. 이 페이지는 그것을 읽어 auth-service 에 넘기고, 응답 본문으로
 * 토큰을 받는다. 토큰이 주소창에 실려 오지 않는다는 것이 요점이다 - code 는 혼자서는 쓸모가
 * 없고, 토큰으로 바꾸는 일은 시크릿을 쥔 서버만 할 수 있다.
 */
export default function CallbackPage() {
    const navigate = useNavigate();
    const { loginWithSocial } = useAuth();
    const [error, setError] = useState(null);

    // 개발 모드의 StrictMode 는 이 훅을 일부러 두 번 돌린다. 그대로 두면 code 교환이 두 번
    // 나가고, 구글 code 는 1회용이라 두 번째가 반드시 실패한다. 로그인은 됐는데 화면에는
    // 실패가 뜨는 헷갈리는 상태가 된다.
    const handled = useRef(false);

    useEffect(() => {
        if (handled.current) return;
        handled.current = true;

        const params = new URLSearchParams(window.location.search);
        const code = params.get('code');
        const state = params.get('state');
        const denied = params.get('error');

        // 동의 화면에서 취소하면 code 대신 error 가 온다. 사고가 아니라 정상적인 선택이다.
        if (denied) {
            setError('구글 로그인이 취소되었습니다.');
            return;
        }
        if (!consumeState(state)) {
            setError('이 브라우저에서 시작한 로그인이 아닙니다.');
            return;
        }

        loginWithSocial(code).then((result) => {
            if (result.ok) navigate('/me', { replace: true });
            else setError(result.message ?? `로그인 실패 (${result.status})`);
        });
    }, [loginWithSocial, navigate]);

    return (
        <div className="page narrow">
            <h1>로그인 중</h1>
            {error
                ? <Notice kind="err">{error}</Notice>
                : <p className="lead">구글에서 받은 코드를 토큰으로 바꾸는 중입니다.</p>}
        </div>
    );
}
