-- MASTER realm 을 들인다. auth 를 운영하는 관리자가 사는 영역이다(Keycloak 의 master).
--
-- realm 은 표가 아니라 코드(Realm enum)라서 값이 늘면 realm 을 가진 모든 표의 CHECK 를 함께 연다.
-- 하나라도 빠뜨리면 그 표에 MASTER 행을 넣는 순간에야 드러난다.

ALTER TABLE identity_principal DROP CONSTRAINT ck_identity_principal_realm;
ALTER TABLE identity_principal ADD CONSTRAINT ck_identity_principal_realm CHECK (realm IN ('MASTER', 'ADMIN', 'PORTAL'));

ALTER TABLE identity_password_account DROP CONSTRAINT ck_identity_password_account_realm;
ALTER TABLE identity_password_account ADD CONSTRAINT ck_identity_password_account_realm CHECK (realm IN ('MASTER', 'ADMIN', 'PORTAL'));

ALTER TABLE identity_email_account DROP CONSTRAINT ck_identity_email_account_realm;
ALTER TABLE identity_email_account ADD CONSTRAINT ck_identity_email_account_realm CHECK (realm IN ('MASTER', 'ADMIN', 'PORTAL'));

ALTER TABLE identity_social_account DROP CONSTRAINT ck_identity_social_account_realm;
ALTER TABLE identity_social_account ADD CONSTRAINT ck_identity_social_account_realm CHECK (realm IN ('MASTER', 'ADMIN', 'PORTAL'));

ALTER TABLE authz_role DROP CONSTRAINT ck_authz_role_realm;
ALTER TABLE authz_role ADD CONSTRAINT ck_authz_role_realm CHECK (realm IN ('MASTER', 'ADMIN', 'PORTAL'));

ALTER TABLE authz_permission DROP CONSTRAINT ck_authz_permission_realm;
ALTER TABLE authz_permission ADD CONSTRAINT ck_authz_permission_realm CHECK (realm IN ('MASTER', 'ADMIN', 'PORTAL'));

ALTER TABLE authz_url_access DROP CONSTRAINT ck_authz_url_access_realm;
ALTER TABLE authz_url_access ADD CONSTRAINT ck_authz_url_access_realm CHECK (realm IN ('MASTER', 'ADMIN', 'PORTAL'));

ALTER TABLE authz_subject_role DROP CONSTRAINT ck_authz_subject_role_realm;
ALTER TABLE authz_subject_role ADD CONSTRAINT ck_authz_subject_role_realm CHECK (realm IN ('MASTER', 'ADMIN', 'PORTAL'));

ALTER TABLE authz_revision DROP CONSTRAINT ck_authz_revision_realm;
ALTER TABLE authz_revision ADD CONSTRAINT ck_authz_revision_realm CHECK (realm IN ('MASTER', 'ADMIN', 'PORTAL'));

ALTER TABLE authz_service DROP CONSTRAINT ck_authz_service_realm;
ALTER TABLE authz_service ADD CONSTRAINT ck_authz_service_realm CHECK (realm IN ('MASTER', 'ADMIN', 'PORTAL'));

ALTER TABLE oauth_client DROP CONSTRAINT ck_oauth_client_realm;
ALTER TABLE oauth_client ADD CONSTRAINT ck_oauth_client_realm CHECK (realm IN ('MASTER', 'ADMIN', 'PORTAL'));

ALTER TABLE oauth_authorization_code DROP CONSTRAINT ck_oauth_authorization_code_realm;
ALTER TABLE oauth_authorization_code ADD CONSTRAINT ck_oauth_authorization_code_realm CHECK (realm IN ('MASTER', 'ADMIN', 'PORTAL'));

ALTER TABLE identity_login_session DROP CONSTRAINT ck_identity_login_session_realm;
ALTER TABLE identity_login_session ADD CONSTRAINT ck_identity_login_session_realm CHECK (realm IN ('MASTER', 'ADMIN', 'PORTAL'));

ALTER TABLE identity_refresh_chain DROP CONSTRAINT ck_identity_refresh_chain_realm;
ALTER TABLE identity_refresh_chain ADD CONSTRAINT ck_identity_refresh_chain_realm CHECK (realm IN ('MASTER', 'ADMIN', 'PORTAL'));
