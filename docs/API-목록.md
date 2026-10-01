# API 목록

auth-service 가 받는 요청 전부. 2026-10-01 에 코드에서 뽑았다. 엔드포인트를 더하거나 바꾸면 이 표도 고친다.

## 한눈에

경로의 첫 마디가 부르는 쪽을 가른다(`docs/모듈-구조.md` 의 채널). 응답 모양도 그것을 따른다.

| 채널 | 경로 | 부르는 쪽 | 인증 | 응답 |
|---|---|---|---|---|
| 로그인 서버 | `/realms/{realm}/...` | 로그인하는 사람의 브라우저, 앱 서버 | 없음, 또는 SSO 쿠키 | 화면, 리다이렉트, 토큰 JSON |
| 공개 문서 | `/realms/{realm}/.well-known/...` | 다른 서비스 | 없음 | JSON, 캐시 허용 |
| 사용자 API | `/api/auth/realms/{realm}/...` | 앱 | Bearer (그 realm). 없으면 빈 응답 | JSON |
| 관리 API | `/api/admin/...` | 백오피스 | Bearer (ADMIN realm) + `AUTHZ_MANAGE` | JSON |
| 운영 화면 | `/page/...`, `/` | 운영자의 브라우저 | MASTER 로그인 쿠키, local 에서만 | 화면 |

`{realm}` 은 경로에서 소문자(`master`, `admin`, `portal`), 관리 API 의 `?realm=` 에서는 대문자(`ADMIN`)다.

## 로그인 서버

OAuth 2.0 과 OIDC 가 정한 자리다. 리소스 API 가 아니라 프로토콜 엔드포인트라 동사 같은 이름과
폼 인코딩을 명세대로 쓴다.

| 메서드 | 경로 | 하는 일 |
|---|---|---|
| GET | `/realms/{realm}/auth` | 인가 요청 입구. 세션이 있으면 code 를 붙여 303, 없으면 로그인 화면. `prompt=none`, `create` |
| POST | `/realms/{realm}/auth/login` | 아이디, 비밀번호 폼. 성공하면 SSO 세션을 심고 code 를 붙여 303 |
| POST | `/realms/{realm}/auth/send-code` | 로그인용 인증번호 발송. 보냈든 아니든 같은 화면 |
| POST | `/realms/{realm}/auth/login/otp` | 인증번호로 로그인. 성공하면 303 |
| GET | `/realms/{realm}/auth/register` | 로그인 흐름 안의 가입 화면 |
| POST | `/realms/{realm}/auth/register/send-code` | 가입용 인증번호 발송 |
| POST | `/realms/{realm}/auth/register` | 가입하고 곧장 로그인, code 를 붙여 303 |
| GET | `/realms/{realm}/register` | 가입만 하는 입구 |
| POST | `/realms/{realm}/register/send-code` | 가입용 인증번호 발송 |
| POST | `/realms/{realm}/register` | 가입하고 완료 화면. 로그인은 하지 않는다 |
| POST | `/realms/{realm}/token` | `grant_type=authorization_code` 는 code 교환, `refresh_token` 은 재발급(회전). 그 밖은 `unsupported_grant_type` |
| GET | `/realms/{realm}/logout` | SSO 세션을 끊고 등록된 `post_logout_redirect_uri` 로 303 |
| GET | `/realms/portal/broker/google/login` | 구글로 보낸다. 포털에서만 연다 |
| GET | `/realms/portal/broker/google/endpoint` | 구글이 돌려보내는 자리. 세션을 심고 원래 인가 요청으로 |

받아줄 수 없는 요청(모르는 realm, 등록되지 않은 앱이나 주소)은 돌려보내지 않고 오류 화면에서 끝난다.
토큰 엔드포인트만 JSON 으로 `{"error": "invalid_grant"}` 같은 OAuth 오류를 준다.

## 공개 문서

| 메서드 | 경로 | 하는 일 |
|---|---|---|
| GET | `/realms/{realm}/.well-known/openid-configuration` | 발급자, 엔드포인트, 지원하는 값. 1시간 캐시 |
| GET | `/realms/{realm}/.well-known/jwks.json` | 그 realm 의 공개키 하나. 합친 JWKS 는 없다. 10분 캐시 |

## 사용자 API

| 메서드 | 경로 | 하는 일 |
|---|---|---|
| GET | `/api/auth/realms/{realm}/my-permissions` | 토큰 주인의 권한 코드 목록. 화면이 메뉴를 감출 때 쓴다. 토큰이 없거나 무효면 401 이 아니라 빈 목록 |

## 관리 API

모두 ADMIN realm 토큰과 `AUTHZ_MANAGE` 가 필요하다. 오류는 `{"message": "..."}` 이다.

**역할** `/api/admin/rbac/roles`

| 메서드 | 경로 | 질의 | 본문 | 응답 |
|---|---|---|---|---|
| GET | `/roles` | `realm`, `keyword` | | 200 목록 |
| GET | `/roles/{roleId}` | | | 200, 없으면 404 |
| POST | `/roles` | `realm` | `roleCode`, `roleName`, `description` | 201, 같은 코드면 409 |
| PUT | `/roles/{roleId}` | | `roleName`, `description` | 200 |
| DELETE | `/roles/{roleId}` | | | 204 |
| GET | `/roles/{roleId}/permissions` | | | 200 |
| PUT | `/roles/{roleId}/permissions` | | `permissionIds` (통째로 교체) | 204 |

**권한** `/api/admin/rbac/permissions`

| 메서드 | 경로 | 질의 | 본문 | 응답 |
|---|---|---|---|---|
| GET | `/permissions` | `realm`, `keyword` | | 200 목록 |
| GET | `/permissions/page` | `realm`, `keyword`, `page`, `size` | | 200 쪽 |
| GET | `/permissions/categories` | `realm` | | 200 |
| GET | `/permissions/{permissionId}` | | | 200, 없으면 404 |
| POST | `/permissions` | `realm` | `permissionCode`, `permissionName`, `category`, `description` | 201, 같은 코드면 409 |
| PUT | `/permissions/{permissionId}` | | `permissionName`, `category`, `description` | 200 |
| DELETE | `/permissions/{permissionId}` | | | 204 |
| GET | `/permissions/{permissionId}/url-access` | | | 200 |
| PUT | `/permissions/{permissionId}/url-access` | `realm` | `urls` (통째로 교체) | 204 |

**역할과 권한 표** `/api/admin/rbac/matrix`

| 메서드 | 경로 | 질의 | 본문 | 응답 |
|---|---|---|---|---|
| GET | `/matrix` | `realm` | | 200 |
| POST | `/matrix/toggle` | | `roleId`, `permissionId`, `assigned` | 204 |

**사람의 역할** `/api/admin/rbac/subjects`

| 메서드 | 경로 | 질의 | 본문 | 응답 |
|---|---|---|---|---|
| GET | `/subjects/{subjectId}/roles` | `realm` | | 200 |
| POST | `/subjects/roles` | `realm` | `subjectIds` (여럿을 한 번에 조회) | 200 |
| PUT | `/subjects/{subjectId}/roles` | `realm` | `roleIds` (통째로 교체) | 204 |

**URL 규칙** `/api/admin/rbac/url-access` (부르는 곳이 없는 판정 엔진의 표, [권한 관리](권한-관리.md))

| 메서드 | 경로 | 질의 | 본문 | 응답 |
|---|---|---|---|---|
| GET | `/url-access` | `realm`, `keyword`, `category`, `page`, `size` | | 200 쪽 |
| GET | `/registered-urls` | `realm` | | 200 전체 |
| GET | `/url-access/{urlAccessId}` | | | 200, 없으면 404 |
| POST | `/url-access` | `realm` | `urlPattern`, `httpMethod`, `description`, `sortOrder` | 201 |
| PUT | `/url-access/{urlAccessId}` | | `description`, `sortOrder` | 200 |
| DELETE | `/url-access/{urlAccessId}` | `realm` | | 204 |
| POST | `/reload-url-rules` | `realm` | | 204 |

**그 밖**

| 메서드 | 경로 | 질의 | 본문 | 응답 |
|---|---|---|---|---|
| GET | `/api/admin/rbac/revision` | `realm` | | 200 |
| POST | `/api/admin/rbac/revision/bump` | `realm` | | 200 |
| POST | `/api/admin/realms/admin/users` | | `employeeId`, `name`, `email`, `mobile`, `roleIds` 등 | 201. 대상 realm 은 지금 admin 뿐 |

## 운영 화면

local 프로파일에서만 뜬다. 로그인과 쿠키는 [운영 화면 로그인](운영-화면-로그인.md).

| 메서드 | 경로 | 하는 일 |
|---|---|---|
| GET | `/` | `/page/oauth-clients` 로 이동 |
| GET, POST | `/page/oauth-clients` | 등록된 앱 목록과 등록 |
| GET, POST | `/page/roles` | 역할 목록(`?realm=`)과 추가 |
| GET | `/page/login/callback` | 로그인이 돌아오는 자리 |
| POST | `/page/logout` | 로그아웃 |

## REST 로 보면

로그인 서버와 공개 문서는 OAuth, OIDC 명세를 따르는 자리라 REST 기준으로 보지 않는다. 관리 API 와
사용자 API 를 본다.

**지켜진 것**
- 자원이 명사 복수형이다(`roles`, `permissions`, `subjects`). 하위 자원도 경로로 잇는다(`/roles/{id}/permissions`)
- 메서드가 뜻대로 쓰인다. 만들기는 POST 201, 바꾸기는 PUT, 지우기는 DELETE 204, 없으면 404
- 집합을 통째로 바꾸는 자리는 PUT 이다(역할의 권한, 사람의 역할). 몇 번 보내도 결과가 같다
- 입력 검증 실패는 400, 인증 없음 401, 권한 없음 403, 같은 코드로 두 번 만들면 409 로 갈린다
- 401 에는 `WWW-Authenticate: Bearer` 가 붙는다(RFC 6750 3절)
- 토큰 엔드포인트의 응답에는 `Cache-Control: no-store` 가 붙는다(RFC 6749 5.1절)

**어긋난 것**

| 어디 | 무엇이 | 이렇게 하면 맞다 |
|---|---|---|
| `POST /matrix/toggle` | 동사다. 같은 요청을 두 번 보내면 결과가 뒤집힌다 | `PUT`, `DELETE /roles/{roleId}/permissions/{permissionId}` |
| `POST /revision/bump`, `POST /reload-url-rules` | 동사 경로(RPC) | 관리 동작이라 남겨도 된다. 남길 거면 이름을 `/actions/...` 처럼 한곳에 모은다 |
| `GET /permissions/page`, `GET /registered-urls` | 같은 집합을 두 경로로 준다 | `GET /permissions?page=&size=` 하나로. 쪽 질의가 없으면 전체 |
| realm 표기 | 경로는 `/realms/admin`, 질의는 `?realm=ADMIN`. 대소문자가 다르다 | 한쪽으로 맞춘다. 질의도 소문자를 받게 하는 쪽이 쉽다 |
| realm 을 받는 자리 | `DELETE /url-access/{id}` 는 `realm` 을 받고 `DELETE /roles/{id}` 는 안 받는다 | id 로 찾는 자원은 realm 을 받지 않게 맞춘다 |
| `POST /subjects/roles` | 조회인데 POST 다 | id 목록이 길 수 있어 흔히 쓰는 예외다. 남긴다면 이유를 컨트롤러에 적는다 |
| 버전 | 경로에 버전이 없다 | 밖에 공개할 API 가 생기면 그때 `/v1` 을 붙인다. 지금은 우리 앱만 부른다 |

## 어디에 있나

| 무엇 | 어디 |
|---|---|
| 로그인 서버, 가입, 토큰, 로그아웃 | `auth-web/.../web/authorize/` |
| 구글 브로커 | `auth-web/.../web/broker/` |
| 공개 문서 | `auth-web/.../web/wellknown/` |
| 사용자 API | `auth-web/.../web/client/` |
| 관리 API | `auth-web/.../web/admin/authorization/`, `web/admin/identity/` |
| 운영 화면 | `auth-web/.../web/admin/console/` |
| 관리 API 의 오류 응답 | `auth-web/.../web/support/AuthApiExceptionHandler.java` |
| 화면 쪽 오류 응답 | `auth-web/.../web/authorize/AuthorizationErrorScreen.java` |

## 아직 없는 것

- **서비스의 권한을 만드는 길.** `POST /permissions` 에 서비스(`service_id`)를 적을 칸이 없다. 이
  API 로 만든 권한은 모두 어드민 콘솔 자신의 권한이 된다. 서비스의 권한은 시드로만 들어간다
- **포털 계정을 관리자가 만드는 길.** `POST /api/admin/realms/{realm}/users` 의 대상은 admin 뿐이다
- **MASTER 토큰으로 관리 API 부르기.** 관리 API 는 호출자를 ADMIN realm 으로 못박고 있다
- **앱이 부르는 토큰 폐기(RFC 7009).** 계보를 끊는 유스케이스는 있지만 엔드포인트가 없다
