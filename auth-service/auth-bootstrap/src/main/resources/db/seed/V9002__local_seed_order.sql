-- 로컬 개발용: order-service 권한.
--
-- V9000 과 나눠 둔 것은 이 권한들이 나중에 붙은 서비스의 것이기 때문이다. 서비스를 하나 더 붙일 때
-- auth 쪽에서 무엇을 해야 하는지가 이 파일 한 장에 다 들어 있다.
--
-- 이 서비스가 오기 전에 이미 뜬 DB 도 있으므로 새 파일로 얹는다. 나간 마이그레이션은 고치지 않는다.

-- 서비스 ------------------------------------------------------------------
-- portal 시스템에 서비스가 하나 늘었다. 이 한 줄이 전부다. 토큰의 aud 는 여전히 portal 이라
-- 이미 발급된 토큰도 그대로 이 서비스에 닿는다. 예전처럼 auth 설정을 고치고 재로그인을
-- 시킬 일이 없다.
INSERT INTO authz_service (service_id, realm, system_id, service_name, description, use_yn, created_at, updated_at)
VALUES ('order-service', 'PORTAL', 'portal', 'Order service', '주문 API', 'Y', now(), now())
ON CONFLICT DO NOTHING;

-- 권한 --------------------------------------------------------------------
-- service_id 가 order-service 다. 어휘가 서비스로 갈려 있어 customer-service 에 같은 이름의 권한이
-- 생겨도 서로 다른 행이고, 한쪽에 준 것이 다른 쪽 문을 열지 않는다.
--
-- auth 는 이름만 보관한다. ORDER_READ 가 어떤 URL 을 여는지는 order-service 의
-- SecurityConfiguration 이 정하고, 그 규칙은 그 서비스와 함께 배포된다.
INSERT INTO authz_permission (realm, service_id, category, permission_code, permission_name, description, use_yn, created_at, updated_at)
VALUES
    ('PORTAL', 'order-service', 'ORDER', 'ORDER_READ', 'Read own orders',
     'GET /api/orders', 'Y', now(), now()),
    -- 읽기와 쓰기를 가른다. 목록을 보여주는 화면에 주문 권한까지 딸려가지 않게 하려는 것이다.
    ('PORTAL', 'order-service', 'ORDER', 'ORDER_WRITE', 'Place orders',
     'POST /api/orders', 'Y', now(), now())
ON CONFLICT DO NOTHING;

-- 역할-권한 ---------------------------------------------------------------
-- 둘 다 CUSTOMER 에 붙인다. 고객이 자기 주문을 보고 자기 이름으로 주문하는 것이라 역할을
-- 더 쪼갤 이유가 없다. 권한을 가른 것은 역할을 나누려는 것이 아니라, 화면이나 다른 앱에
-- 읽기만 주고 싶어질 때 그 선택지를 남겨두려는 것이다.
INSERT INTO authz_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM authz_role r
         JOIN authz_permission p ON p.realm = r.realm
WHERE r.realm = 'PORTAL'
  AND r.role_code = 'CUSTOMER'
  AND p.service_id = 'order-service'
ON CONFLICT DO NOTHING;
