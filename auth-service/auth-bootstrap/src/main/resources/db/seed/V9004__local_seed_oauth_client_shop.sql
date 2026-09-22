-- 로컬 개발용: 같은 realm 의 두 번째 앱.
--
-- 통합 로그인을 눈으로 보려고 세운다. 포털에서 로그인한 브라우저로 이 앱의 로그인 버튼을 누르면
-- 로그인 화면이 뜨지 않아야 한다. 앱이 둘이어야 확인되는 것이라 시드에도 둘이 필요하다.
--
-- realm 이 포털과 같다. 같은 사람이 쓰는 같은 영역의 다른 앱이기 때문이다. realm 이 다르면
-- (예: 어드민) 세션이 공유되지 않는 것이 정상이고, 그것도 이 앱으로 확인해 볼 수 있다.

-- 이름을 shop 이 아니라 shop-web 으로 둔다. shop 은 이미 포털 realm 의 시스템 이름이고
-- (token.realms.PORTAL.system), 그 값이 토큰의 aud 로 나간다. 앱 이름과 시스템 이름이 같은
-- 글자면 토큰을 열어봤을 때 aud 가 무엇을 가리키는지 헷갈린다. 앱이 몇 개든 aud 는 하나다.

INSERT INTO oauth_client (client_id, realm, enabled, created_at, updated_at)
VALUES ('shop-web', 'PORTAL', true, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO oauth_client_redirect_uri (client_id, redirect_uri)
VALUES ('shop-web', 'http://localhost:5176/callback.html')
ON CONFLICT DO NOTHING;
