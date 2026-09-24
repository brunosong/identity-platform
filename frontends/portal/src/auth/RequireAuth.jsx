import { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { authorizeUrl, markSilentTried, shouldTrySilently } from '../api/authorize';

/**
 * 로그인해야 볼 수 있는 화면을 감싼다.
 *
 * <b>이건 방어가 아니라 안내다.</b> 여기서 막아도 API 는 여전히 열려 있고, 실제 방어는 서버가 한다
 * (customer-service 의 AuthenticatedCaller). 브라우저 콘솔에서 이 검사를 우회해도 API 는 401 이다.
 *
 * 화면 제어와 접근 제어를 헷갈리면 "프론트에서 버튼을 숨겼으니 안전하다" 는 착각이 생긴다.
 *
 * <h3>로그인 화면으로 보내기 전에 한 번 조용히 시도한다</h3>
 * 토큰은 메모리에 있어서 새로고침 한 번에 날아간다. 그런데 auth 의 세션 쿠키는 그대로 살아 있다.
 * 앱이 그 쿠키를 읽을 수는 없지만, 브라우저를 auth 로 보내면 브라우저가 알아서 들고 간다.
 * 세션이 있으면 화면 없이 코드가 돌아오고, 없으면 login_required 가 돌아온다.
 *
 * <p><b>여기가 그 시도를 거는 자리인 이유</b>는 토큰이 필요해지는 곳이 여기이기 때문이다.
 * 앱이 뜰 때마다 걸면 상품만 구경하는 사람에게도 auth 왕복을 먹인다.
 */
export default function RequireAuth({ children }) {
    const { isLoggedIn } = useAuth();
    const location = useLocation();

    // 첫 렌더에서 정한다. 도중에 바뀌면 화면이 깜빡이거나 시도가 두 번 나간다.
    const [restoring] = useState(() => !isLoggedIn && shouldTrySilently());

    useEffect(() => {
        if (!restoring) return;
        // 떠나기 전에 적어둔다. 세션이 정말 없으면 돌아와서 또 시도하게 되고, 그러면 무한 왕복이다.
        markSilentTried();
        authorizeUrl({ silent: true, returnTo: location.pathname })
            .then((url) => { window.location.href = url; });
    }, [restoring, location.pathname]);

    if (isLoggedIn) return children;

    if (restoring) {
        // 어차피 곧 이 문서를 떠난다. 로그인 화면을 스쳐 보여주지 않으려고 자리만 채운다.
        return (
            <div className="auth-page">
                <div className="auth-card">
                    <h1>로그인 확인 중</h1>
                    <p className="auth-sub">인증 서버에 세션이 남아 있는지 보고 있습니다.</p>
                </div>
            </div>
        );
    }

    // 어디로 가려 했는지 기억해 두고, 로그인 후 되돌려준다.
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
}
