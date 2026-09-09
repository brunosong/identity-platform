# customer-service

고객 프로필 API. **auth-service 가 발급한 토큰을 검증해 쓰는 소비 서비스**이고,
`auth-client` 를 어떻게 붙이는지 보여주는 것이 이 서비스의 목적이다.

## auth 와 무엇을 주고받나

```
                  ┌───────────────┐
  ① 로그인 ────────>│ auth-service  │ 포털 개인키로 서명
                  └───────┬───────┘
  ② 토큰 <────────────────┘
                          │  ④ GET /realms/portal/.well-known/jwks.json
  ③ 토큰 ──────────┐      │       (포털 공개키만, 10분 캐시)
                  ▼      ▼
            ┌─────────────────────┐
            │  customer-service   │ 서명을 스스로 검증
            └──────────┬──────────┘
                       └─> 자기 DB (customer)
```

**요청마다 auth 를 부르지 않는다.** 공개키만 받아다 서명을 직접 확인한다. 그래서

- 요청당 왕복이 없다
- auth 가 잠시 죽어도 이미 발급된 토큰은 계속 통과한다
- auth 가 모든 요청 경로에 놓이지 않는다

이 서비스가 auth 에 대해 아는 것은 설정 두 줄뿐이다.

```yaml
auth:
  client:
    realm: PORTAL
    jwks-uri: http://localhost:8080/realms/portal/.well-known/jwks.json
```

이 두 줄이 **이 서비스의 realm 경계 전부**다.

`auth-domain`, `auth-application` 을 의존하지 않는다. **`auth-client` 하나뿐이다.**
주고받는 것은 토큰 문자열과 그 안의 클레임이고, 그것이 이 경계의 계약이다.

## auth-client 를 붙이는 자리

`customer-web` 의 `AuthenticatedCaller` **한 곳**이다. 이 클래스 밖에서는 토큰이라는 말이
나오지 않는다 — 컨트롤러는 `AuthenticatedToken` 만 받고, 응용·도메인 계층은 그것조차 모른다.

```java
AuthenticatedToken me = caller.require(request);   // 401 아니면 통과
```

### 세 겹으로 막는다

| | 무엇을 보나 | 어디서 |
|---|---|---|
| **서명·만료·용도** | 이 토큰이 진짜 auth 가 만든 access 토큰인가 | `auth-client` |
| **realm** | 포털 것인가 | **설정** (`auth.client.realm` + JWKS 주소) |
| **소유권** | 이 데이터가 이 사람 것인가 | 컨트롤러 |

**realm 확인이 코드에 없다.** 사라진 것이 아니라 설정으로 옮겨갔다. JWKS 가 realm 별로 나뉘어 있어
이 서비스는 어드민 공개키를 아예 갖지 못하고, 그래서 어드민 토큰은 서명 검증에서 죽는다 —
이 서비스의 코드가 한 줄도 돌기 전에.

토큰에 `realm` 클레임은 없다. realm 마다 서명키가 다르니 *어느 키로 검증됐는지가 곧 realm* 이다.
다만 그래서 **`jwks-uri` 를 잘못 적으면 조용히 뚫린다** — 그 그물은 `iss` 검증으로 채워야 한다.

**소유권은 토큰이 답할 수 없다.** 그래서 `/api/customers/me` 는 조회 키를 요청에서 받지 않고
토큰의 `subjectId` 로만 찾는다. 경로나 본문으로 받으면 남의 식별자를 적어 넣는 것으로 남의
프로필이 열린다. 게이트웨이가 있어도 이 확인은 대신해 줄 수 없다 — 게이트웨이는 "이 URL 에
들어와도 되는가"까지 알지만 "이 데이터가 이 사람 것인가"는 모른다.

## API

| | 조건 | 하는 일 |
|---|---|---|
| `GET /api/customers/me` | PORTAL 토큰 | 내 프로필 (없으면 404) |
| `PUT /api/customers/me` | PORTAL 토큰 | 내 프로필 생성/수정 |

**직원이 고객을 조회하는 API 는 여기 없다.** 그런 것이 필요해지면 **같은 코드를 어드민 realm
설정으로 한 벌 더 띄우는** 것이 표준적인 방법이다. 한 프로세스가 두 realm 의 키를 다 쥐기
시작하면 경계는 다시 코드의 몫이 되고, 한 곳에서 잊으면 뚫린다.

> 그 배포를 위한 권한 코드 `CUSTOMER_PROFILE_READ` 는 auth 시드(`V9002`)에 남겨 뒀다.
> 권한 코드는 auth 가 보관하지만 그 의미는 **자원을 가진 서비스**가 정한다 — auth 는 그 코드가
> 무엇을 여는지 모르고, 이 서비스는 그 코드가 어떤 역할에 붙어 있는지 모른다. 그래서 코드
> 이름에 이 서비스의 자원을 넣는다.

## 자기 DB 를 쓴다

auth 와 **다른 데이터베이스**다. 서비스가 남의 테이블을 직접 읽기 시작하면 나눈 의미가 없어지고,
스키마를 바꿀 때마다 남의 배포를 기다려야 한다.

대가도 있다. `customer_profile.customer_id` 는 auth 의 `identity_principal` 을 가리키지만
**외래키를 걸 수 없다** — 다른 DB 이기 때문이다. 서비스를 나눈다는 것은 참조 무결성을 DB 에
맡기지 못하게 된다는 뜻이기도 하다. 그래서 "토큰이 가리키는 주체"만 믿고, 그 토큰은 auth 의
서명으로 확인한다.

## 띄우기

```bash
# 자기 DB — 저장소 루트의 docker compose 가 auth 것과 함께 띄운다
docker compose up -d

./mvnw -pl customer-service/customer-bootstrap -am install -DskipTests

cd customer-service/customer-bootstrap
DB_URL=jdbc:postgresql://localhost:55433/customer \
DB_USERNAME=customer DB_PASSWORD=customer \
AUTH_JWKS_URI=http://localhost:8080/realms/portal/.well-known/jwks.json \
SPRING_PROFILES_ACTIVE=local \
java -jar target/customer-bootstrap-0.0.1-SNAPSHOT.jar
```

**auth-service 보다 먼저 떠도 된다.** 공개키는 첫 검증 때 받아온다. 그동안 들어온 요청은
검증에 실패해 401 이 된다 — 열린 채로 남지 않는 것이 중요하다.

> 포트가 겹치면 `SERVER_PORT` 로 옮기고, auth 를 옮겼다면 `AUTH_JWKS_URI` 도 함께 바꾼다.

## 직접 확인해 보기

```bash
AUTH=http://localhost:8080/api/auth
CUST=http://localhost:8081/api/customers

# 고객으로 로그인해 토큰을 받는다
TOKEN=$(curl -s -X POST $AUTH/realms/portal/login \
  -H 'Content-Type: application/json' \
  -d '{"loginId":"hong@example.com","password":"pw12345678"}' \
  | python -c "import json,sys;print(json.load(sys.stdin)['tokens']['accessToken'])")

# 그 토큰으로 이 서비스를 부른다 — auth 는 이 호출에 관여하지 않는다
curl -s $CUST/me -H "Authorization: Bearer $TOKEN"                       # 404 (아직 프로필 없음)
curl -s -X PUT $CUST/me -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Hong"}'               # 생성
curl -s $CUST/me -H "Authorization: Bearer $TOKEN"                       # 조회

curl -s -w ' [%{http_code}]' $CUST/me                                    # 401 토큰 없음
```

어드민 토큰으로 불러보면 **401** 이다. 이 서비스는 어드민 공개키가 없어 서명조차 확인하지 못한다.

```bash
curl -s -o /dev/null -X POST $AUTH/realms/admin/login/email-otp/send-code \
  -H 'Content-Type: application/json' -d '{"email":"admin@example.com"}'
ADMIN=$(curl -s -X POST $AUTH/realms/admin/login/email-otp \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","verificationCode":"123456"}' \
  | python -c "import json,sys;print(json.load(sys.stdin)['tokens']['accessToken'])")

curl -s -w ' [%{http_code}]' $CUST/me -H "Authorization: Bearer $ADMIN"   # 401
```

## 아직 없는 것

- **프로필이 만들어지는 경로** — 지금은 고객이 `PUT /me` 로 직접 만든다. 원래 설계는 auth 의
  `SubjectRegisteredEvent` 를 받아 만드는 것인데, 서비스 간 이벤트 전달(브로커)이 아직 없다.
  그래서 `customer-messaging` 모듈도 없다.
- **게이트웨이** — 프론트엔드가 auth 와 이 서비스를 각각 직접 부른다. 그래서 두 서비스가
  각자 CORS 를 밝혀야 한다.
- **서비스 간 호출** — 이 서비스가 또 다른 서비스를 부를 때 토큰을 어떻게 넘길지(전파냐
  자체 자격증명이냐)는 아직 다루지 않았다.
