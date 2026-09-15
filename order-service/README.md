# order-service

주문 API. **포털 realm 의 두 번째 소비 서비스**이고, 이 저장소에서 이 서비스가 있는 이유는
**서비스가 둘이 되고 나서야 보이는 것들**을 드러내려는 것이다.

customer-service 와 같은 레이어 구조, 같은 발급자, 같은 로그인으로 받은 같은 토큰을 쓴다.
그 서비스를 먼저 읽고 오면 여기서는 다른 점만 보면 된다.

## customer-service 와 무엇이 다른가

| | customer-service | order-service |
|---|---|---|
| 발급자(`issuer-uri`) | `.../realms/portal` | **같다** |
| 대상(`audiences`) | `customer-service` | `order-service` |
| 역할 | `PROFILE_READ` 하나 | `ORDER_READ` / `ORDER_WRITE` 둘 |
| 조회 키 | 토큰의 `sub` 뿐 | `sub` + **경로의 주문번호** |
| DB | `customer`(55433) | `orders`(55434) |
| 포트 | 8081 | 8082 |

세 가지가 여기서 처음 보인다.

## 1. 한 번 로그인한 토큰 하나가 두 서비스에 통한다

auth 의 고객 포털 클라이언트가 audience 를 둘 가지고 있다.

```yaml
token:
  clients:
    customer-portal:
      realm: PORTAL
      audiences: customer-service,order-service
```

그래서 로그인 한 번으로 받은 토큰의 `aud` 가 둘이고, 두 서비스가 각각 자기 이름만 요구해도
같은 토큰이 둘 다 통과한다.

```json
{
  "iss": "http://localhost:8080/realms/portal",
  "aud": ["customer-service", "order-service"],
  "sub": "cust-...",
  "resource_access": {
    "customer-service": { "roles": ["PROFILE_READ"] },
    "order-service":    { "roles": ["ORDER_READ", "ORDER_WRITE"] }
  }
}
```

**"한 번만 로그인" 과 "아무 데나 통한다" 는 다른 이야기다.** `aud` 가 없으면 발급자만 맞는
토큰이 realm 의 모든 서비스에 통하고, 서비스 하나가 침해되면 그 서비스로 들어온 토큰을 다른
서비스에 그대로 재생할 수 있다. 서비스가 늘수록 **가장 약한 서비스 하나가 realm 전체의 보안
수준**이 된다. 지금은 나열되지 않은 서비스가 그 토큰을 거부한다.

서비스를 하나 붙이는 일은 auth 쪽에서 설정 두 줄과 시드 한 장이다.

```yaml
token:
  clients:
    customer-portal:
      audiences: customer-service,order-service   # 이 앱이 받을 대상
  realms:
    PORTAL:
      audiences: customer-service,order-service   # 이 realm 에 있는 서비스
```

realm 쪽 목록은 화이트리스트다. 여기 없는 이름을 클라이언트에 적으면 **부팅에서 걸린다.**
서비스는 realm 에 속하고, 다른 realm 의 서비스를 audience 로 적은 토큰은 그 서비스에 닿지도
못한다. 그것을 운영에서 401 로 만나면 원인이 토큰 안에 있어 찾기 번거롭다.

## 2. 남의 주문번호는 403 이 아니라 404 다

`/api/customers/me` 에는 조회 키를 적어 넣을 자리가 **아예 없었다.** 토큰의 `sub` 하나뿐이라
남의 것을 가리킬 방법이 없었다.

주문은 다르다. 사람당 여럿이라 주문번호를 경로로 받고, 그 자리에 **남의 주문번호를 적어 넣을 수
있다.** 그래서 조회 조건에 `sub` 를 함께 건다.

```java
orders.findOwned(token.getSubject(), orderId)
        .map(order -> ResponseEntity.ok(OrderResponse.of(order)))
        .orElseGet(() -> ResponseEntity.notFound().build());
```

주문번호만으로 찾는 길은 이 서비스 어디에도 없다. `OrderRepository` 에 그런 메서드를 두지
않았고, `JpaRepository.findById` 도 부르지 않는다. **잊을 자리를 만들지 않는 편이 확인을 잊지
말자고 다짐하는 것보다 낫다.**

**403 이 아니라 404 인 이유.** 403 은 "그 주문은 있는데 당신 것이 아니다" 를 알려준다.
주문번호를 훑으면서 403 과 404 를 세면 남의 주문이 몇 건인지, 어느 번호가 살아 있는지 알아낼 수
있다. 소유자를 조건에 함께 걸면 "없는 주문" 과 "남의 주문" 이 같은 결과가 되고, 응답은 그 둘을
구분하지 못한다.

**게이트웨이가 있어도 이 확인은 대신해 줄 수 없다.** 게이트웨이는 "이 URL 에 들어와도 되는가"
까지 알지만 "이 데이터가 이 사람 것인가" 는 모른다.

## 3. 역할을 읽기와 쓰기로 가른다

customer-service 는 역할이 하나였다. 여기는 둘이다.

```java
.requestMatchers(HttpMethod.POST, "/api/orders").hasAuthority(ORDER_WRITE)
.requestMatchers("/api/orders/**").hasAuthority(ORDER_READ)
.anyRequest().authenticated()
```

**순서가 중요하다.** 필터체인은 먼저 맞는 규칙에서 멈춘다. POST 규칙을 아래로 내리면
`/api/orders/**` 가 POST 까지 먼저 잡아 쓰기가 `ORDER_READ` 로 열린다.

그리고 여기서 **권한 어휘가 서비스로 갈려 있다는 것이 처음 효력을 낸다.** 같은 토큰에
customer-service 의 칸도 실려 있는데, `ClientRoleAuthorities` 가 자기 칸만 읽으므로 남의 칸에
같은 이름이 있어도 이 서비스의 문은 열리지 않는다. 권한이 realm 전역 평면 목록이었다면
한쪽의 `READ` 가 다른 쪽 문까지 열었을 것이다.

> **이 이름들이 무엇을 여는지는 이 서비스가 정한다.** auth 는 이름만 보관한다
> (`authz_permission` 의 `(PORTAL, order-service, ORDER_READ)` 행). 그래서 엔드포인트를 늘려도
> auth 를 배포하지 않고, "환불 API 를 만들었다" 와 "환불 역할 규칙" 이 같은 PR 에서 리뷰된다.

## API

| | 조건 | 하는 일 |
|---|---|---|
| `GET /api/orders` | `ORDER_READ` | 내 주문 목록 (최근 순) |
| `GET /api/orders/{orderId}` | `ORDER_READ` + 소유권 | 내 주문 하나 (없거나 남의 것이면 404) |
| `POST /api/orders` | `ORDER_WRITE` | 주문 |

주문자를 본문으로 받지 않는다. 받으면 남의 이름으로 주문할 수 있다. 금액도 받지 않는다.
단가 곱하기 수량으로 서버가 계산한다. 받으면 낼 값을 스스로 정할 수 있다.

**주문 취소는 없다.** 이 저장소의 주제가 인증과 인가라 결제나 배송 같은 상태 전이는 다루지
않는다. `OrderStatus` 에 값이 하나뿐인 것이 그 사실을 그대로 말한다.

## 자기 DB 를 쓴다

auth 와도, customer 와도 다른 데이터베이스다. `orders.customer_id` 는 customer-service 의
`customer_profile.customer_id` 와 같은 값이지만 **두 서비스는 서로의 표를 보지 않는다.**
같은 사람을 가리키는 이유는 토큰이 하나이기 때문이지, 어느 한쪽이 상대의 데이터를 읽기
때문이 아니다.

그래서 프로필 없이 주문만 있는 상태도 정상이다. 두 서비스는 서로를 기다리지 않는다.

## 띄우기

```bash
docker compose up -d      # order-pg 가 auth, customer 것과 함께 뜬다

./mvnw -pl order-service/order-bootstrap -am install -DskipTests

cd order-service/order-bootstrap
DB_URL=jdbc:postgresql://localhost:55434/orders \
DB_USERNAME=orders DB_PASSWORD=orders \
AUTH_ISSUER=http://localhost:8080/realms/portal \
SPRING_PROFILES_ACTIVE=local \
java -jar target/order-bootstrap-0.0.1-SNAPSHOT.jar
```

**auth-service 보다 먼저 떠도 된다.** `issuer-uri` 를 쓰면 Spring 이 디코더 생성을 첫 검증까지
미룬다(`SupplierJwtDecoder`). 그동안 들어온 요청은 검증에 실패해 401 이 된다.

> auth 를 다른 포트로 옮겼다면 `AUTH_ISSUER` 도 함께 바꾼다. 발급자는 문자열 그대로
> 대조되므로 auth 의 `TOKEN_ISSUER` 와 **짝**이어야 한다.

## 직접 확인해 보기

auth 와 customer-service, order-service 를 모두 띄우고 시작한다. 고객 계정은 시드에 없으므로
(부트스트랩되는 것은 직원뿐이다) 둘을 가입시켜 놓는다. 하나는 내 것, 하나는 남의 것이다.

```bash
AUTH=http://localhost:8080/api/auth
CUST=http://localhost:8081/api/customers
ORDER=http://localhost:8082/api/orders

login() {
    curl -s -X POST $AUTH/realms/portal/login -H 'Content-Type: application/json' \
        -d "{\"loginId\":\"$1\",\"password\":\"pw12345678\"}" \
        | python -c "import json,sys;print(json.load(sys.stdin)['tokens']['accessToken'])"
}

for who in hong kim; do
    curl -s -o /dev/null -X POST $AUTH/realms/portal/register \
        -H 'Content-Type: application/json' \
        -d "{\"email\":\"$who@example.com\",\"password\":\"pw12345678\",\"name\":\"$who\"}"
done

# 한 번만 로그인한다
TOKEN=$(login hong@example.com)

# aud 가 둘이다
python -c "
import base64, json, sys
p = sys.argv[1].split('.')[1]; p += '=' * (-len(p) % 4)
c = json.loads(base64.urlsafe_b64decode(p))
print('aud             =', c['aud'])
print('resource_access =', json.dumps(c['resource_access'], ensure_ascii=False))
" "$TOKEN"
```

```
aud             = ['customer-service', 'order-service']
resource_access = {"customer-service": {"roles": ["PROFILE_READ"]},
                   "order-service": {"roles": ["ORDER_READ", "ORDER_WRITE"]}}
```

그 토큰 하나로 두 서비스를 부른다.

```bash
curl -s $CUST/me    -H "Authorization: Bearer $TOKEN"    # 8081
curl -s $ORDER      -H "Authorization: Bearer $TOKEN"    # 8082, []

# 주문한다
ORDER_ID=$(curl -s -X POST $ORDER -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"productName":"키보드","quantity":2,"unitPrice":45000}' \
  | python -c "import json,sys;print(json.load(sys.stdin)['orderId'])")

curl -s $ORDER/$ORDER_ID -H "Authorization: Bearer $TOKEN"
```

다른 계정으로 로그인해 그 주문번호를 그대로 넣어 본다. **읽기 권한은 멀쩡히 있는데도 404 다.**

```bash
OTHER=$(login kim@example.com)

curl -s -o /dev/null -w '%{http_code}\n' $ORDER/$ORDER_ID -H "Authorization: Bearer $OTHER"
curl -s -o /dev/null -w '%{http_code}\n' $ORDER/does-not-exist -H "Authorization: Bearer $OTHER"
```

```
404
404
```

둘이 같다. 응답만 봐서는 그 주문번호가 있는지 없는지 알 수 없다.

어드민 토큰은 **401** 이다. customer-service 와 같은 이유로, 이 서비스는 어드민 공개키가 없어
서명조차 확인하지 못한다.

```bash
curl -s -o /dev/null -X POST $AUTH/realms/admin/login/email-otp/send-code \
  -H 'Content-Type: application/json' -d '{"email":"admin@example.com"}'
ADMIN=$(curl -s -X POST $AUTH/realms/admin/login/email-otp \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","verificationCode":"123456"}' \
  | python -c "import json,sys;print(json.load(sys.stdin)['tokens']['accessToken'])")

curl -s -o /dev/null -w '%{http_code}\n' $ORDER -H "Authorization: Bearer $ADMIN"   # 401
```

## 아직 없는 것

- **주문의 생애.** 결제도 배송도 취소도 없다. 이 저장소의 주제가 아니다.
- **customer-service 와의 연결.** 주문 화면에 고객 이름을 보여주려면 서비스 간 호출이 필요하고,
  그때 토큰을 어떻게 넘길지(전파냐 자체 자격증명이냐)는 아직 다루지 않았다. 지금은 두 서비스가
  서로를 부르지 않는다.
- **게이트웨이.** 프론트엔드가 세 서비스를 각각 직접 부른다. 그래서 셋 다 각자 CORS 를 밝혀야
  한다. 서비스를 하나 붙일 때마다 그 비용이 어디서 늘어나는지가 `CorsPolicy` 에 보인다.
- **프론트엔드 화면.** `frontends/customer-portal` 에 주문 화면은 아직 없다.
