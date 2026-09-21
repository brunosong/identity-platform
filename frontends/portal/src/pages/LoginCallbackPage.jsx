import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { consumeState } from '../api/authorize';
import DevPanel from '../components/DevPanel';
import Notice from '../components/Notice';

/**
 * auth-service 가 로그인을 마치고 브라우저를 돌려보내는 자리.
 *
 * 주소창에 실려 오는 것은 code 와 state 뿐이다. 토큰이 아니다. 그 code 를 토큰으로 바꾸는 것은
 * 아직 만들지 않았다 - 지금은 받은 값을 눈으로 확인하는 데까지다.
 *
 * 구글 콜백(/callback)과 경로를 나눈 이유는 한 자리가 두 종류의 code 를 받으면 어느 쪽인지
 * 가려내야 하기 때문이다. 나중에 구글을 auth 뒤로 넣으면 그쪽 경로는 사라진다.
 */
export default function LoginCallbackPage() {
    const [params] = useSearchParams();
    const [result, setResult] = useState(null);

    // StrictMode 는 이 효과를 두 번 돌린다. state 는 한 번 쓰고 버리는 값이라
    // 두 번째에는 이미 없어서 "시작하지 않은 로그인" 으로 보인다.
    const checked = useRef(false);

    useEffect(() => {
        if (checked.current) return;
        checked.current = true;

        const error = params.get('error');
        if (error) {
            setResult({ ok: false, text: `인증 서버가 거절했습니다: ${error}` });
            return;
        }

        const code = params.get('code');
        if (!code) {
            setResult({ ok: false, text: 'code 가 없습니다. 로그인을 다시 시작해야 합니다.' });
            return;
        }

        if (!consumeState(params.get('state'))) {
            setResult({ ok: false, text: 'state 가 맞지 않습니다. 이 브라우저가 시작한 로그인이 아닙니다.' });
            return;
        }

        setResult({ ok: true, code });
    }, [params]);

    return (
        <div className="auth-page">
            <div className="auth-card">
                <h1>로그인 콜백</h1>
                <p className="auth-sub">auth-service 가 돌려보낸 자리</p>

                <Notice kind={result?.ok ? 'ok' : 'err'}>
                    {result?.ok ? 'code 를 받았습니다. state 도 맞습니다.' : result?.text}
                </Notice>

                {result?.ok && (
                    <>
                        <label>받은 code</label>
                        <input type="text" readOnly value={result.code} />
                        <p className="field-hint">
                            아직 토큰으로 바꾸지 않았습니다. 교환 엔드포인트(<code>/token</code>)가
                            다음 차례입니다.
                        </p>
                    </>
                )}

                <p className="auth-foot">
                    <Link to="/login">로그인 화면으로</Link>
                </p>
            </div>

            <DevPanel title="여기까지 온 길">
                <h3>주소창에 무엇이 실려 왔나</h3>
                <p className="field-hint">
                    <code>code</code> 와 <code>state</code> 뿐입니다. 토큰이 주소창에 실리면
                    브라우저 기록과 리퍼러에 남기 때문에, 받는 것은 한 번 쓰고 버리는 code 한 장입니다.
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
