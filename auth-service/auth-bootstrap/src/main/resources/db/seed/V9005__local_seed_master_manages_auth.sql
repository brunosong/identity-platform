-- 로컬 개발용: auth 를 관리하는 것은 MASTER realm 이다.
--
-- 전에는 직원 realm(ADMIN) 의 ADMIN 역할이 AUTHZ_MANAGE 를 갖고 모든 realm 의 역할과 권한을 고쳤다.
-- Keycloak 에서 관리 콘솔과 Admin REST API 를 쓰는 것은 master realm 의 관리자다. 직원 realm 은 업무
-- 시스템을 쓰는 사람들의 자리라 auth 를 관리하지 않는다.
--
-- V9000 을 고치지 않고 따로 둔다. 이미 적용된 시드를 고치면 Flyway 가 체크섬이 다르다며 멈춘다.

-- MASTER 의 관리 권한. 서비스(service_id)가 없는 것은 auth 자신의 권한이라는 뜻이다.
INSERT INTO authz_permission (realm, service_id, category, permission_code, permission_name, description, use_yn, created_at, updated_at)
VALUES ('MASTER', NULL, 'AUTHZ', 'AUTHZ_MANAGE', 'Manage authorization',
        'Edit roles/permissions/URL rules, register employees', 'Y', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO authz_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM authz_role r
         JOIN authz_permission p ON p.realm = r.realm
WHERE r.realm = 'MASTER' AND r.role_code = 'ADMIN' AND p.permission_code = 'AUTHZ_MANAGE'
ON CONFLICT DO NOTHING;

-- 직원 realm 의 관리 권한은 걷어 낸다. 역할과의 연결은 외래키(ON DELETE CASCADE)로 함께 지워진다.
DELETE FROM authz_permission WHERE realm = 'ADMIN' AND permission_code = 'AUTHZ_MANAGE';
