-- 로컬 개발용: 프로필 저장 권한.
--
-- customer-service 에는 조회(PROFILE_READ)만 있었다. 프로필을 저장하는 PUT 도 권한으로 판정하려면
-- 따로 코드가 있어야 한다. 주문처럼 읽기와 쓰기를 가른다. 보여주기만 하는 화면에 고치는 권한까지
-- 딸려가지 않게 하려는 것이다.
--
-- V9000 을 고치지 않고 따로 둔다. 이미 적용된 시드를 고치면 Flyway 가 체크섬이 다르다며 멈춘다.
INSERT INTO authz_permission (realm, service_id, category, permission_code, permission_name, description, use_yn, created_at, updated_at)
VALUES ('PORTAL', 'customer-service', 'CUSTOMER', 'PROFILE_WRITE', 'Save own profile',
        'PUT /api/customers/me', 'Y', now(), now())
ON CONFLICT DO NOTHING;

-- 고객은 자기 프로필을 고칠 수 있다.
INSERT INTO authz_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM authz_role r
         JOIN authz_permission p ON p.realm = r.realm
WHERE r.realm = 'PORTAL' AND r.role_code = 'CUSTOMER' AND p.permission_code = 'PROFILE_WRITE'
ON CONFLICT DO NOTHING;
