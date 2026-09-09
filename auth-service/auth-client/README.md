# auth-client

다른 서비스가 auth-service 가 발급한 토큰을 검증할 때 쓰는 라이브러리.

> **⚠️ customer-service 는 이제 이 모듈을 쓰지 않는다.** 토큰에 표준 `iss` 를 싣고 realm 별
> discovery 문서를 내면서, 그 서비스는 **Spring Security 리소스 서버**로 옮겨갔다 — 설정 한 줄
> (`spring.security.oauth2.resourceserver.jwt.issuer-uri`)이 이 모듈이 하던 일을 전부 대신한다.
>
> 이 모듈은 두 가지 이유로 남아 있다. ① Spring Security 를 쓰지 않는(쓸 수 없는) 서비스가 무엇을
> 직접 해야 하는지 보여준다 — JWKS 조회, 캐시, `kid` 선택, 키 교체, auth 장애 때의 처리. ②
> `JwksVerificationIntegrationTest` 가 발급기와 이 검증기를 함께 돌려, 클레임 이름이 바뀌면
> 곧바로 걸리게 한다.
>
> **새 서비스라면 이 모듈 대신 `spring-boot-starter-oauth2-resource-server` 를 쓴다.**

## 어떻게 신뢰가 서는가

```
                    ┌───────────────┐
                    │  auth-service │  realm 마다 다른 개인키로 서명
                    └───────┬───────┘
                            │ GET /realms/{realm}/.well-known/jwks.json
                            │      (그 realm 의 공개키만, 인증 불필요)
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ order-svc│  │ pay-svc  │  │ ship-svc │   각자 서명을 검증
        │ (portal) │  │ (portal) │  │ (admin)  │   ← 서비스마다 realm 하나
        └──────────┘  └──────────┘  └──────────┘
```

**소비 서비스는 요청마다 auth 에 묻지 않는다.** 공개키만 받아다 자기가 서명을 확인한다.

- 요청당 왕복이 없다
- auth 가 잠시 죽어도 이미 발급된 토큰은 계속 검증된다
- auth 가 모든 요청의 경로에 놓이지 않는다(단일 장애점이 되지 않는다)

공개키를 설정 파일로 뿌리지 않는 이유는 **키 교체**다. 파일로 배포하면 키를 바꿀 때 모든 서비스를
다시 배포해야 한다. JWKS 로 두면 auth 만 바꾸고, 소비 서비스는 모르는 `kid` 를 만났을 때 다시 받아온다.

## 쓰는 법

```xml
<dependency>
    <groupId>com.brunosong</groupId>
    <artifactId>auth-client</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

```yaml
auth:
  client:
    issuer: http://auth-service:8080/realms/portal
```

**이 한 줄이 그 서비스의 경계 전부**다. 발급자 이름에 realm 이 들어 있고, 공개키를 받아올 주소도
여기서 유도된다(`issuer + /.well-known/jwks.json`). 그래서 "어느 서버의 어느 realm 인가"를 한 값이
다 말한다 — Spring Security 의 `spring.security.oauth2.resourceserver.jwt.issuer-uri` 와 같은 자리다.

이 값이 없으면 자동설정이 켜지지 않으므로, auth 토큰을 쓰지 않는 서비스에 이 의존이 섞여도 부팅이
깨지지 않는다.

> 발급자 이름과 실제 주소가 다른 배치(리버스 프록시 뒤, 테스트의 랜덤 포트)에서만 `jwks-uri` 를
> 따로 적는다. `iss` 는 **식별자**라 문자열로 대조하고, `jwks-uri` 는 **주소**라 실제로 접속한다.

```java
@RestController
@RequiredArgsConstructor
class OrderController {

    private final AuthTokenVerifier verifier;

    @GetMapping("/api/orders")
    List<Order> myOrders(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        AuthenticatedToken token = verifier.verifyAuthorizationHeader(authorization)
                .orElseThrow(() -> new UnauthorizedException("로그인이 필요합니다."));

        if (!token.hasPermission("ORDER_READ")) {
            throw new ForbiddenException("권한이 없습니다.");
        }
        // 조회 키는 요청이 아니라 토큰에서 온다 — 이것만은 라이브러리가 대신해 줄 수 없다.
        return orders.findBySubjectId(token.subjectId());
    }
}
```

**realm 확인 코드가 없다.** 검증기가 설정된 realm 만 통과시키므로 여기까지 온 토큰은 이미 그 realm 이다.

## 반드시 알아야 할 것 세 가지

### 1. 한 서비스는 발급자 하나만 상대한다

JWKS 가 realm 별로 나뉘어 있으므로, 설정한 realm 의 키만 갖는다. 다른 realm 의 토큰은 `kid` 를
찾지 못해 **서명 검증에서 죽는다** — 서비스의 코드가 한 줄도 돌기 전에.

그래서 realm 확인이 코드에서 사라졌다. 없어진 것이 아니라 **설정으로 옮겨간 것**이다.

토큰에는 `realm` 클레임이 **없다.** realm 마다 서명키가 다르므로 *어느 키로 검증됐는지가 곧 realm*
이고, 그것을 표준 자리에서 밝히는 값이 `iss` 다.

### 1-2. 그리고 발급자를 대조한다

JWKS 분리가 막지 못하는 것이 하나 있다. **다른 배포**다.

```
staging 의 포털 토큰   {"iss": "https://stg.auth/realms/portal", "sub": ..., "authLs": ...}
prod 의 포털 토큰      {"iss": "https://auth/realms/portal",     "sub": ..., "authLs": ...}
```

realm 도 같고, 서명 알고리즘도 같고, 클레임 이름도 전부 같다. **다른 것은 발급자 이름 하나뿐이다.**
그래서 소비 서비스가 주소를 staging 쪽으로 잘못 적으면 staging 계정으로 prod 가 열린다 —
`realm` 클레임이 있던 시절에도 막지 못하던 구멍이다. RFC 8725 가 발급자 검증을 요구하는 이유가
이 cross-JWT confusion 이다.

직원용과 고객용을 모두 제공해야 한다면 **같은 코드를 realm 별 설정으로 두 벌 띄운다.**
한 프로세스가 두 realm 의 키를 다 쥐기 시작하면 경계는 다시 코드의 몫이 되고, 한 곳에서 잊으면 뚫린다.

### 2. subjectId 는 realm 안에서만 유일하다

realm 마다 다른 체계에서 발급된다(직원은 사번 성격의 값, 고객은 UUID). **realm 없이 subjectId 만으로
사람을 특정하지 말 것.** 저장할 때도 `(realm, subjectId)` 를 함께 두는 편이 안전하다.

### 3. 권한은 발급 시점의 값이다

`permissions` 는 토큰에 실려 있다. 그래서 빠르지만, **발급 뒤의 권한 변경은 이 토큰에 반영되지 않는다** —
다음 갱신 때 들어온다. 즉시 회수가 필요하면 access 토큰 TTL 을 짧게 잡아야 한다.

`rbacRev` 는 realm 전역 정책의 리비전이다. auth 가 역할-권한 매핑을 바꾸면 올라가므로, 엄격한 서비스는
이 값이 자기가 아는 최신보다 낮으면 갱신을 요구할 수 있다.

## 키 교체를 어떻게 견디나

`JwksKeySource` 는 **모르는 `kid` 를 만나면 캐시 TTL 과 무관하게 한 번 다시 받아온다.** auth 가 새 키로
서명하기 시작하면 소비 서비스는 처음 보는 kid 를 만나는데, 그때 바로 새 키를 얻는다. TTL 만료를
기다렸다면 그 사이 모든 요청이 401 이 된다.

교체 중에는 한 realm 의 JWKS 에 키가 둘 나온다(새 키와, 아직 만료되지 않은 토큰들을 위한 옛 키).
그래서 응답이 배열이다.

다만 재조회에는 최소 간격(30초)이 있다. 없으면 아무 문자열이나 kid 로 넣어 보내는 것만으로 auth 에
요청을 무한히 발생시킬 수 있다.

받아온 키에서 사라진 kid 도 지우지 않는다 — 교체 중에는 옛 kid 로 서명된 토큰이 아직 만료 전이다.

## 이 라이브러리가 auth 의 도메인을 의존하지 않는 이유

`auth-domain`, `auth-application` 을 하나도 의존하지 않는다. 소비 서비스는 auth 의 도메인 모델
(`Principal`, `Realm`, `SubjectType`…)을 알 필요가 없고, 알게 되면 auth 의 내부 변경이 남의 서비스
컴파일을 깨뜨린다. 주고받는 것은 **토큰 문자열과 그 안의 클레임뿐**이고 그것이 이 경계의 계약이다.
그래서 realm 도 enum 이 아니라 문자열이다.

저장소 안에 두는 이유는 토큰 형식을 **발급하는 쪽이 소유**하기 때문이다. 클레임 이름이 바뀌면 발급기와
이 검증기가 같은 커밋에서 함께 바뀌어야 한다. 그것을 `JwksVerificationIntegrationTest` 가 지킨다 —
실제로 발급하고 실제 HTTP 로 JWKS 를 받아 검증한다.

## 아직 없는 것

- **서블릿 필터 / Spring Security 연동** — 지금은 검증기만 준다. 인증 결과를 `SecurityContext` 에
  넣는 방식은 서비스마다 달라 일부러 정하지 않았다.
- **토큰 폐기 확인** — 로그아웃해도 access 토큰은 만료까지 유효하다(무상태 JWT). 즉시 회수가 필요하면
  auth 에 폐기 목록이 필요하고, 그러면 이 라이브러리도 그것을 물어봐야 한다.
