import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { consumeState } from '../api/authorize';
import Notice from '../components/Notice';

/**
 * auth-service 가 로그인을 마치고 브라우저를 돌려보내는 자리.
 *
 * 주소창에 실려 오는 것은 code 와 state 뿐이다. 토큰이 아니다. 여기서 code 를 토큰으로 바꾸고,
 * 그때 비로소 로그인이 끝난다.
 */
export default function LoginCallbackPage() {
    const [params] = useSearchParams();
    const navigate = useNavigate();
    const { loginWithCode } = useAuth();
    const [failure, setFailure] = useState(null);

    // 개발 모드는 이 효과를 두 번 돌린다. state 도 PKCE 원본도 code 도 한 번 쓰고 버리는
    // 값이라, 두 번째 실행은 멀쩡한 로그인을 실패로 만든다.
    const started = useRef(false);

    useEffect(() => {
        if (started.current) return;
        started.current = true;

        const error = params.get('error');
        if (error) {
            setFailure(`인증 서버가 거절했습니다: ${error}`);
            return;
        }

        const code = params.get('code');
        if (!code) {
            setFailure('code 가 없습니다. 로그인을 다시 시작해야 합니다.');
            return;
        }

        if (!consumeState(params.get('state'))) {
            setFailure('state 가 맞지 않습니다. 이 브라우저가 시작한 로그인이 아닙니다.');
            return;
        }

        loginWithCode(code).then((result) => {
            if (result.ok) navigate('/', { replace: true });
            else setFailure(result.message ?? `토큰 교환 실패 (${result.status})`);
        });
    }, [params, loginWithCode, navigate]);

    return (
        <div className="page narrow">
            <h1>로그인 마무리</h1>
            <p className="lead">받은 code 를 토큰으로 바꾸는 중입니다.</p>

            <Notice kind={failure ? 'err' : null}>{failure}</Notice>

            {failure && (
                <div className="row">
                    <Link className="button-like" to="/login">로그인 화면으로</Link>
                </div>
            )}
        </div>
    );
}
