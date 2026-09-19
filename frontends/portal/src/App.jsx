import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import RequireAuth from './auth/RequireAuth';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import CallbackPage from './pages/CallbackPage';
import SignUpPage from './pages/SignUpPage';
import MyPage from './pages/MyPage';

export default function App() {
    return (
        <Layout>
            <Routes>
                <Route path="/" element={<HomePage />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/signup" element={<SignUpPage />} />
                {/* 구글이 브라우저를 돌려보내는 주소. 구글 콘솔에 등록된 값과 같아야 한다. */}
                <Route path="/callback" element={<CallbackPage />} />
                <Route
                    path="/me"
                    element={<RequireAuth><MyPage /></RequireAuth>}
                />
                <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
        </Layout>
    );
}
