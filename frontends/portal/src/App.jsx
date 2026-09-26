import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import RequireAuth from './auth/RequireAuth';
import HomePage from './pages/HomePage';
import ProductDetailPage from './pages/ProductDetailPage';
import LoginPage from './pages/LoginPage';
import LoginCallbackPage from './pages/LoginCallbackPage';
import MyPage from './pages/MyPage';

export default function App() {
    return (
        <Layout>
            <Routes>
                <Route path="/" element={<HomePage />} />
                <Route path="/products/:id" element={<ProductDetailPage />} />
                <Route path="/login" element={<LoginPage />} />
                {/* 가입도 auth 가 그린다. 로그인과 같은 문으로 나가되 가입 화면을 열어 달라고 한다. */}
                <Route path="/signup" element={<LoginPage prompt="create" />} />
                {/* auth-service 가 돌려보내는 주소. oauth_client 에 등록된 값과 같아야 한다. */}
                <Route path="/login/callback" element={<LoginCallbackPage />} />
                <Route
                    path="/me"
                    element={<RequireAuth><MyPage /></RequireAuth>}
                />
                <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
        </Layout>
    );
}
