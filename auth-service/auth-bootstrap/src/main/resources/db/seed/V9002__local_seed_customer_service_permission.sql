-- customer-service 가 요구하는 권한을 직원 realm 에 등록한다.
--
-- 권한 코드는 auth 가 보관하지만 그 의미는 그 자원을 가진 서비스가 정한다 — auth 는
-- CUSTOMER_PROFILE_READ 가 무엇을 여는지 모르고, customer-service 는 이 코드가 어떤 역할에
-- 붙어 있는지 모른다. 양쪽이 아는 것은 문자열 하나뿐이고 그것이 이 경계의 계약이다.
--
-- 그래서 코드 이름에 그 서비스의 자원을 넣는다. 이름이 겹치면 두 서비스가 같은 권한을 서로 다른
-- 뜻으로 쓰게 되고, 한쪽에 권한을 준 것이 다른 쪽 문까지 연다.

INSERT INTO authz_permission (realm, category, permission_code, permission_name, description, use_yn, created_at, updated_at)
VALUES ('ADMIN', 'CUSTOMER', 'CUSTOMER_PROFILE_READ', 'Read customer profiles',
        'View customer profiles in customer-service', 'Y', now(), now())
ON CONFLICT (realm, permission_code) DO NOTHING;

-- 개발용 ADMIN 역할에 붙인다. 이 역할을 가진 부트스트랩 관리자(V9001)가 바로 써볼 수 있게 한다.
INSERT INTO authz_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM authz_role r
         JOIN authz_permission p ON p.realm = r.realm
WHERE r.realm = 'ADMIN'
  AND r.role_code = 'ADMIN'
  AND p.permission_code = 'CUSTOMER_PROFILE_READ'
ON CONFLICT DO NOTHING;
