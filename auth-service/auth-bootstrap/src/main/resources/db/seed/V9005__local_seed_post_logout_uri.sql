-- 로컬 개발용: 로그아웃 뒤 돌아갈 주소.
--
-- 로그아웃도 돌려보낼 주소를 요청자가 적어 보내므로 등록된 것과 대조한다. 그 목록이 지금은
-- 로그인 때 쓰는 목록과 같은 표라서, 앱의 홈 주소를 여기 넣어 둔다.
--
-- 대가가 있다. 이 목록에 있는 주소는 인가 코드도 받을 수 있는 주소다. 홈 화면이 code 를 받아도
-- 하는 일이 없으니 지금은 문제가 아니지만, OIDC 가 로그아웃용 주소를 따로 등록하게 하는 이유가
-- 정확히 이것이다. 목록을 가를 때가 오면 이 두 줄이 그쪽으로 옮겨간다.

INSERT INTO oauth_client_redirect_uri (client_id, redirect_uri)
VALUES ('portal', 'http://localhost:5173/'),
       ('shop-web', 'http://localhost:5176/')
ON CONFLICT DO NOTHING;
