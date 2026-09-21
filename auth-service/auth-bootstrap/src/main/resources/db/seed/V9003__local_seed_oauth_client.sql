-- 로컬 개발용: 인가 요청을 시작할 수 있는 앱.
--
-- 이 행이 없으면 로컬에서 /realms/{realm}/auth 는 아무 요청도 받지 않는다. 등록되지 않은 앱이기
-- 때문이다. 운영에는 앱을 붙이는 사람이 직접 넣는다.

INSERT INTO oauth_client (client_id, realm, enabled, created_at, updated_at)
VALUES ('portal', 'PORTAL', true, now(), now())
ON CONFLICT DO NOTHING;

-- 돌아갈 주소. /callback 이 아닌 것은 그 경로를 구글 콜백이 이미 쓰고 있기 때문이다
-- (앱이 구글에서 직접 돌아오는 경로). 한 경로가 두 종류의 code 를 받으면 프론트가 어느 쪽인지
-- 가려내야 하고, 그 분기는 나중에 구글을 우리 뒤로 넣으면 지워질 코드다.
INSERT INTO oauth_client_redirect_uri (client_id, redirect_uri)
VALUES ('portal', 'http://localhost:5173/login/callback')
ON CONFLICT DO NOTHING;
