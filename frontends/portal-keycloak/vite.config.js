import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 5175 는 Keycloak 의 portal-keycloak client 에 Valid redirect URIs 와 Web origins 로
// 등록해 둔 포트다. 여기를 바꾸면 Keycloak 쪽 두 곳도 함께 바꿔야 한다.
// 등록되지 않은 주소로 돌아오려 하면 Keycloak 이 로그인 화면 대신 오류를 띄운다.
export default defineConfig({
    plugins: [react()],
    server: {
        port: 5175,
        strictPort: true,
    },
});
