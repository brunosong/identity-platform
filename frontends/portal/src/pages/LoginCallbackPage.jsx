import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { consumeReturnTo, consumeState, forgetLoggedIn } from '../api/authorize';
import DevPanel from '../components/DevPanel';
import Notice from '../components/Notice';

/**
 * auth-service 가 로그인을 마치고 브라우저를 돌려보내는 자리.
 *
 * 주소창에 실려 오는 것은 code 와 state 뿐이다. 토큰이 아니다. 여기서 code 를 토큰으로 바꾸고,
 * 그때 비로소 로그인이 끝난다.
 *
 * 구글 콜백(/callback)과 경로를 나눈 이유는 한 자리가 두 종류의 code 를 받으면 어느 쪽인지
 * 가려내야 하기 때문이다. 나중에 구글을 auth 뒤로 넣으면 그쪽 경로는 사라진다.
 */
export default function LoginCallbackPage() {
    const [params] = useSearchParams();
    const navigate = useNavigate();
    const { loginWithCode } = useAuth();
    const [failure, setFailure] = useState(null);

    // 개발 모드는 이 효과를 두 번 돌린다. state 도 PKCE 원본도 code 도 전부 한 번 쓰고 버리는
    // 값이라, 두 번째 실행은 멀쩡한 로그인을 실패로 만든다.
    const started = useRef(false);

    useEffect(() => {
        if (started.current) return;
        started.current = true;

        const returnTo = consumeReturnTo();
        const stateMatched = consumeState(params.get('state'));
        const error = params.get('error');

        // 조용히 시도해 봤는데 세션이 없었다. 오류가 아니라 답이다 - 아무 일 없던 것처럼 보낸다.
        // state 를 여기서도 대조한다. 남이 열게 만든 콜백이면 우리 깃발을 건드리지 않아야 한다.
        if (error === 'login_required' && stateMatched) {
            forgetLoggedIn();
            navigate(returnTo ?? '/', { replace: true });
            return;
        }

        if (error) {
            setFailure(`인증 서버가 거절했습니다: ${error}`);
            return;
        }

        const code = params.get('code');
        if (!code) {
            setFailure('code 가 없습니다. 로그인을 다시 시작해야 합니다.');
            return;
        }

        if (!stateMatched) {
            setFailure('state 가 맞지 않습니다. 이 브라우저가 시작한 로그인이 아닙니다.');
            return;
        }

        loginWithCode(code).then((result) => {
            if (result.ok) navigate(returnTo ?? '/', { replace: true });
            else setFailure(result.message ?? `토큰 교환 실패 (${result.status})`);
        });
    }, [params, loginWithCode, navigate]);

    return (
        <div className="auth-page">
            <div className="auth-card">
                <h1>로그인 마무리</h1>
                <p className="auth-sub">받은 code 를 토큰으로 바꾸는 중</p>

                <Notice kind={failure ? 'err' : null}>{failure}</Notice>

                {failure && (
                    <p className="auth-foot">
                        <Link to="/login">로그인 화면으로</Link>
                    </p>
                )}
            </div>

            <DevPanel title="여기서 무슨 일이 일어나나">
                <h3>주소창에 실려 온 것</h3>
                <p className="field-hint">
                    <code>code</code> 와 <code>state</code> 뿐입니다. 토큰이 주소창에 실리면 브라우저
                    기록과 리퍼러에 남기 때문에, 오는 것은 한 번 쓰고 버리는 code 한 장입니다.
                </p>
                <p className="field-hint">
                    조용히 시도한 경우(<code>prompt=none</code>)에는 <code>code</code> 대신
                    <code>error=login_required</code> 가 올 수 있습니다. 세션이 없었다는 뜻이고
                    오류가 아니라 답입니다. 그때는 이 화면이 아무것도 보여주지 않고 원래 보던
                    자리로 돌려보냅니다.
                </p>

                <h3>교환 요청</h3>
                <p className="field-hint">
                    이 화면이 <code>POST /realms/portal/token</code> 을 부릅니다. 시크릿 대신
                    로그인을 시작할 때 만들어 둔 <code>code_verifier</code> 원본을 냅니다. 서버는
                    그것을 해시해서 시작할 때 받아둔 값과 맞춰봅니다. code 를 주운 쪽은 원본을
                    모르니 여기서 걸립니다.
                </p>

                <h3>이 앱이 모르는 것</h3>
                <p className="field-hint">
                    비밀번호입니다. 방금 로그인 화면은 <code>localhost:8080</code> 이 그렸고,
                    이 앱의 코드에는 그것을 받는 자리가 없습니다.
                </p>
            </DevPanel>
        </div>
    );
}
