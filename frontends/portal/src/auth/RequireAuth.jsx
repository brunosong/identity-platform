import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * 로그인해야 볼 수 있는 화면을 감싼다.
 *
 * <b>이건 방어가 아니라 안내다.</b> 여기서 막아도 API 는 여전히 열려 있고, 실제 방어는 서버가 한다
 * (customer-service 의 AuthenticatedCaller). 브라우저 콘솔에서 이 검사를 우회해도 API 는 401 이다.
 *
 * 화면 제어와 접근 제어를 헷갈리면 "프론트에서 버튼을 숨겼으니 안전하다" 는 착각이 생긴다.
 */
export default function RequireAuth({ children }) {
    const { isLoggedIn } = useAuth();
    const location = useLocation();

    if (!isLoggedIn) {
        // 어디로 가려 했는지 기억해 두고, 로그인 후 되돌려준다.
        return <Navigate to="/login" state={{ from: location.pathname }} replace />;
    }
    return children;
}
