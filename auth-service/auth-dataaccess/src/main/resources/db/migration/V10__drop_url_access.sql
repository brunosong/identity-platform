-- URL 접근 규칙 표를 걷어 낸다.
--
-- 서비스가 auth 에 판정을 맡기던 구성의 흔적이다. 지금은 각 서비스가 토큰의 자기 칸을 읽어 자기 코드로
-- 판정하고(hasAuthority), 이 표를 읽던 판정 엔진과 관리 API 는 지웠다. 연결 표가 규칙 표를 가리키므로
-- 연결 표부터 지운다.

DROP TABLE authz_url_permission;
DROP TABLE authz_url_access;
