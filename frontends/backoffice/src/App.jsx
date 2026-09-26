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
 * <b>계정을 만드는 길이 둘이다</b> — 이것이 고객 포털과 다른 점이다.
 *
 * <ul>
 *   <li>본인 가입 — auth 의 가입 화면에서 이메일 인증번호로 만든다(로그인 화면의 "이메일로 가입").
 *       권한 없는 껍데기가 생긴다.</li>
 *   <li>`/users/new` — 관리자가 남의 계정을 만든다. `AUTHZ_MANAGE` 가 있어야 열리고,
 *       여기서만 역할을 줄 수 있다.</li>
 * </ul>
 *
 * 신원을 만드는 일과 권한을 주는 일이 갈려 있어서 본인 가입을 열어둘 수 있다.
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
