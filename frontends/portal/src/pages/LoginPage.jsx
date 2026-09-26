import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { authorizeUrl } from '../api/authorize';

/**
 * 로그인과 가입. 화면이 없다. 들어오자마자 브라우저를 auth-service 로 보낸다.
 *
 * 가입도 여기로 온다({@code prompt="create"}). 그러면 auth 가 로그인 화면 대신 가입 화면을 그리고,
 * 가입이 끝나면 로그인된 채로 code 를 들고 돌아온다. 가입 폼과 비밀번호도 이 앱을 거치지 않는다.
 *
 * 로그인 화면은 인증 서버에 하나만 있다. 비밀번호든 구글이든 어떤 방법으로 사람을 확인할지는
 * 거기서 정하고, 이 앱이 돌려받는 것은 code 한 장이다. 헤더의 버튼이든 보호된 화면이든 로그인이
 * 필요한 곳은 모두 이 주소로 오므로, 여기가 앱에서 auth 로 나가는 문 하나다.
 *
 * <b>replace 로 떠난다.</b> 이 주소를 방문 기록에 남기면 auth 에서 뒤로 가기를 눌렀을 때 여기로
 * 돌아와 곧바로 다시 auth 로 튕긴다. 사람은 뒤로 갈 수 없게 된다.
 */
export default function LoginPage({ prompt = null }) {
    const location = useLocation();
    const returnTo = location.state?.from;

    useEffect(() => {
        let cancelled = false;
        authorizeUrl({ returnTo, prompt }).then((url) => {
            if (!cancelled) window.location.replace(url);
        });
        // 개발 모드의 StrictMode 는 효과를 두 번 돌린다. 두 번 떠나면 state 가 어긋난다.
        return () => { cancelled = true; };
    }, [returnTo, prompt]);

    return null;
}
