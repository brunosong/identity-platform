import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import RequireAuth from './auth/RequireAuth';
import RequireManage from './auth/RequireManage';
import LoginPage from './pages/LoginPage';
import LoginCallbackPage from './pages/LoginCallbackPage';
import NewEmployeePage from './pages/NewEmployeePage';
import HomePage from './pages/HomePage';
import RbacPage from './pages/RbacPage';

/**
 * 화면 넷.
 *
 * <b>스스로 가입하는 길이 없다</b> — 이것이 고객 포털과 다른 점이다. 직원 계정은
 * `/users/new` 에서 관리자가 만들고(`AUTHZ_MANAGE` 가 있어야 열린다), 역할도 거기서 준다.
 * 최초 관리자는 데이터로 심는다.
 */
export default function App() {
    return (
        <Layout>
            <Routes>
                <Route path="/login" element={<LoginPage />} />
                {/* auth-service 가 돌려보내는 주소. oauth_client 에 등록된 값과 같아야 한다. */}
                <Route path="/login/callback" element={<LoginCallbackPage />} />
                <Route path="/" element={<RequireAuth><HomePage /></RequireAuth>} />
                <Route path="/users/new" element={<RequireManage><NewEmployeePage /></RequireManage>} />
                <Route path="/rbac" element={<RequireManage><RbacPage /></RequireManage>} />
                <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
        </Layout>
    );
}
