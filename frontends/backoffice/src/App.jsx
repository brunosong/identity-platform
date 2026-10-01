import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import RequireAuth from './auth/RequireAuth';
import LoginPage from './pages/LoginPage';
import LoginCallbackPage from './pages/LoginCallbackPage';
import HomePage from './pages/HomePage';

/**
 * 화면 셋. 직원이 업무를 보는 앱이다.
 *
 * <b>스스로 가입하는 길이 없다.</b> 이것이 고객 포털과 다른 점이다. 직원 계정과 역할은 auth 의 운영 화면에서
 * MASTER 관리자가 만든다. auth 를 관리하는 것은 MASTER realm 이지 직원 realm 이 아니라서, 그 화면을 이 앱에
 * 두지 않는다.
 */
export default function App() {
    return (
        <Layout>
            <Routes>
                <Route path="/login" element={<LoginPage />} />
                {/* auth-service 가 돌려보내는 주소. oauth_client 에 등록된 값과 같아야 한다. */}
                <Route path="/login/callback" element={<LoginCallbackPage />} />
                <Route path="/" element={<RequireAuth><HomePage /></RequireAuth>} />
                <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
        </Layout>
    );
}
