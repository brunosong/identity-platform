-- 앱에 사람이 알아보는 이름 칸을 붙인다.
--
-- client_id 를 더는 사람이 짓지 않는다. 등록할 때 우리가 portal-17kqqi85h2ks 처럼 발급한다.
-- 그러면 목록에서 어느 앱인지 알아볼 수 없으니 이름을 따로 둔다. 이름은 화면과 로그에만 쓰고
-- 토큰에는 싣지 않는다. 그래서 바꿔도 앱은 그대로다.
--
-- 이미 있는 행은 client_id 를 이름으로 옮겨 적는다. 그때까지는 client_id 가 사람이 지은 이름이었다.

ALTER TABLE oauth_client ADD COLUMN client_name varchar(100);
UPDATE oauth_client SET client_name = client_id;
ALTER TABLE oauth_client ALTER COLUMN client_name SET NOT NULL;
