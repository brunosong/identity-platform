import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import RequireAuth from './auth/RequireAuth';
import RequireManage from './auth/RequireManage';
import LoginPage from './pages/LoginPage';
import SignUpPage from './pages/SignUpPage';
import NewEmployeePage from './pages/NewEmployeePage';
import HomePage from './pages/HomePage';
import RbacPage from './pages/RbacPage';

/**
 * 화면 다섯.
 *
 * <b>계정을 만드는 화면이 둘이다</b> — 이것이 고객 포털과 다른 점이다.
 *
 * <ul>
 *   <li>`/signup` — 본인이 이메일 인증번호로 직접 만든다. 로그인 전에 누구나 연다.
 *       권한 없는 껍데기가 생긴다.</li>
 *   <li>`/users/new` — 관리자가 남의 계정을 만든다. `AUTHZ_MANAGE` 가 있어야 열리고,
 *       여기서만 역할을 줄 수 있다.</li>
 * </ul>
 *
 * 신원을 만드는 일과 권한을 주는 일이 갈려 있어서 앞의 화면을 열어둘 수 있다.
 */
export default function App() {
    return (
        <Layout>
            <Routes>
                <Route path="/login" element={<LoginPage />} />
                <Route path="/" element={<RequireAuth><HomePage /></RequireAuth>} />
                <Route path="/signup" element={<SignUpPage />} />
                <Route path="/users/new" element={<RequireManage><NewEmployeePage /></RequireManage>} />
                <Route path="/rbac" element={<RequireManage><RbacPage /></RequireManage>} />
                <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
        </Layout>
    );
}
