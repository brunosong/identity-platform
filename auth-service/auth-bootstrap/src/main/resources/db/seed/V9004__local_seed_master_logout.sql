-- 로컬 개발용: 운영 화면이 로그아웃 뒤 돌아올 주소.
--
-- 로그아웃은 등록된 주소로만 돌려보낸다. 루트로 돌아오면 토큰이 없으니 로그인 화면이 뜬다.
-- V9003 에 넣지 않고 따로 둔다. 이미 적용된 시드를 고치면 Flyway 가 체크섬이 다르다며 멈춘다.
--
-- 지금은 로그인 뒤 돌아갈 주소와 같은 목록을 쓴다. 그래서 이 주소로 로그인 code 를 받는 것도 허용된다.
-- Keycloak 은 두 목록을 따로 둔다.
INSERT INTO oauth_client_redirect_uri (client_id, redirect_uri)
VALUES ('auth-console', 'http://localhost:8080/')
ON CONFLICT DO NOTHING;
