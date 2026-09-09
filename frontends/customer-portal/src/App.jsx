import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import RequireAuth from './auth/RequireAuth';
import LoginPage from './pages/LoginPage';
import SignUpPage from './pages/SignUpPage';
import MyPage from './pages/MyPage';

export default function App() {
    return (
        <Layout>
            <Routes>
                <Route path="/login" element={<LoginPage />} />
                <Route path="/signup" element={<SignUpPage />} />
                <Route
                    path="/me"
                    element={<RequireAuth><MyPage /></RequireAuth>}
                />
                <Route path="*" element={<Navigate to="/me" replace />} />
            </Routes>
        </Layout>
    );
}
