-- 앱에 시크릿 칸을 붙인다.
--
-- V3 에서는 일부러 두지 않았다. 등록되는 앱이 전부 브라우저 앱이었고, 브라우저는 시크릿을 지킬 수
-- 없다. 이제 서버에서 도는 앱도 받는다. 그런 앱은 시크릿을 지킬 수 있으니, 토큰을 바꾸러 올 때
-- "진짜 그 앱인가" 를 시크릿으로도 확인한다. PKCE 는 그대로 요구한다.
--
-- 비어 있으면 public client 다. 종류를 가르는 칸을 따로 두지 않는다. 두 칸이면 "confidential 인데
-- 해시가 없다" 같은 어긋난 행이 생길 수 있다.

-- 원문이 아니라 해시다. base64url(sha256(secret)) 이라 43글자다.
ALTER TABLE oauth_client ADD COLUMN client_secret_hash varchar(64);
