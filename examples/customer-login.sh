#!/usr/bin/env bash
#
# CUSTOMER 로그인 전 구간을 순서대로 밟아보는 예제.
#
# 가입 -> 로그인 -> 토큰으로 API 호출 -> 재발급 -> 로그아웃, 그리고 realm 격리가 실제로 막는 것까지.
# 각 단계마다 무엇을 보내고 무엇이 돌아오는지 그대로 찍는다.
#
# 실행 전 준비는 examples/README.md 참고. 요약하면 PostgreSQL 을 띄우고 앱을 local 프로파일로 켠다.
#
#   ./examples/customer-login.sh                     # 기본 http://localhost:8080
#   BASE=http://localhost:18080 ./examples/customer-login.sh
#
set -u

BASE="${BASE:-http://localhost:8080}"
API="$BASE/api/auth"

# 매번 새 계정으로 돌 수 있게 이메일에 시각을 섞는다(이메일은 realm 안에서 유일하다).
EMAIL="hong-$(date +%s)@example.com"
PASSWORD="pw12345678"

step()  { printf '\n\033[1;36m── %s\033[0m\n' "$1"; }
note()  { printf '   \033[2m%s\033[0m\n' "$1"; }
req()   { printf '   \033[1;33m%s\033[0m\n' "$1"; }
res()   { printf '   → %s\n' "$1"; }

# JSON 에서 값 하나를 꺼낸다. jq 없이 돌게 python 을 쓴다.
pluck() {
    python -c "
import json,sys
d = json.load(sys.stdin)
for k in sys.argv[1].split('.'):
    d = d.get(k) if isinstance(d, dict) else None
    if d is None: break
print(d if d is not None else '')
" "$1"
}

# JWT 의 payload 를 사람이 읽게 편다. 서명은 확인하지 않는다 — 그건 소비 서비스가 할 일이다.
show_jwt() {
    python -c "
import base64, json, sys
part = sys.argv[1].split('.')[1]
part += '=' * (-len(part) % 4)
print(json.dumps(json.loads(base64.urlsafe_b64decode(part)), indent=2, ensure_ascii=False))
" "$1" | sed 's/^/   /'
}

printf '\033[1m고객(CUSTOMER) 로그인 예제\033[0m\n'
note "대상: $BASE"
note "계정: $EMAIL"

# ---------------------------------------------------------------------------
step "1. 가입 — 신원(Principal)과 비밀번호 자격증명을 만든다"
note "고객은 별도 아이디 없이 이메일이 로그인 식별자다."
note "가입이 끝나면 CustomerDefaultRoleGrantListener 가 CUSTOMER 역할을 붙인다."
req "POST $API/customer/register"

REGISTER=$(curl -s -X POST "$API/customer/register" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"name\":\"Hong\",\"phoneNumber\":\"010-0000-0000\"}")
res "$REGISTER"

PRINCIPAL_ID=$(echo "$REGISTER" | pluck principalId)
if [ -z "$PRINCIPAL_ID" ]; then
    printf '\n\033[1;31m가입 실패. 앱이 떠 있는지, 시드 데이터가 들어갔는지 확인하세요.\033[0m\n'
    exit 1
fi

# ---------------------------------------------------------------------------
step "2. 로그인 — realm 은 경로가 정한다"
note "/realms/customer/ 라서 고객 서랍에서만 계정을 찾는다."
note "토큰은 쿠키가 아니라 응답 본문으로 내려온다(도메인이 다른 프론트에 쿠키는 전달되지 않는다)."
req "POST $API/realms/customer/login"

LOGIN=$(curl -s -X POST "$API/realms/customer/login" \
    -H 'Content-Type: application/json' \
    -d "{\"loginId\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
res "$(echo "$LOGIN" | cut -c1-120)..."

ACCESS=$(echo "$LOGIN" | pluck tokens.accessToken)
REFRESH=$(echo "$LOGIN" | pluck tokens.refreshToken)

step "2-1. 발급된 access 토큰 안에 무엇이 들었나"
note "realm 이 토큰에 실려 있다 — 소비 서비스가 설정이나 요청에 기대지 않고 판단할 수 있다."
note "authLs 는 발급 시점의 권한이다. 이것 때문에 서비스가 요청마다 권한 DB 를 뒤지지 않아도 된다."
show_jwt "$ACCESS"

# ---------------------------------------------------------------------------
step "3. 발급한 쪽이 자기 토큰을 읽는다"
note "이후 모든 요청은 Authorization: Bearer 로 토큰을 싣는다."
req "GET $API/my-permissions"
res "$(curl -s "$API/my-permissions" -H "Authorization: Bearer $ACCESS")"

# ---------------------------------------------------------------------------
step "4. realm 격리 — 같은 자격증명으로 직원 realm 에 로그인해본다"
note "아이디도 비밀번호도 맞지만 직원 서랍에는 이 계정이 없다."
note "조회 자체가 (subject_type, login_id) 로 좁혀져 있어 없는 아이디와 구분되지 않는다."
req "POST $API/realms/employee/login"
res "$(curl -s -w ' [%{http_code}]' -X POST "$API/realms/employee/login" \
    -H 'Content-Type: application/json' \
    -d "{\"loginId\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"

# ---------------------------------------------------------------------------
step "5. 없는 realm / 그 realm 에 없는 로그인 방식"
note "realm 은 비밀이 아니라 어느 서랍을 열지 고르는 값이다. 모르는 값은 404 다."
req "POST $API/realms/martian/login"
res "$(curl -s -w ' [%{http_code}]' -X POST "$API/realms/martian/login" \
    -H 'Content-Type: application/json' -d '{"loginId":"a","password":"b"}')"

note "소셜은 최초 로그인에 신원을 새로 만든다(JIT). 직원 realm 에서 열려 있으면"
note "아무나 소셜 로그인만으로 직원 신원을 만들 수 있어 고객 realm 에서만 연다."
req "POST $API/realms/employee/login/social"
res "$(curl -s -w ' [%{http_code}]' -X POST "$API/realms/employee/login/social" \
    -H 'Content-Type: application/json' -d '{"provider":"KAKAO","authorizationCode":"x"}')"

# ---------------------------------------------------------------------------
step "6. 재발급 — 권한과 리비전이 그 시점 값으로 다시 실린다"
note "refresh 토큰에는 subjectId 만 있어 그것만으로는 주체가 특정되지 않는다(유일키는 유형+식별자)."
note "그래서 재발급도 realm 을 경로로 받는다."
req "POST $API/realms/customer/token/refresh"
REFRESHED=$(curl -s -X POST "$API/realms/customer/token/refresh" \
    -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}")
res "$(echo "$REFRESHED" | cut -c1-120)..."

note "고객 refresh 토큰을 직원 realm 에 내밀면 그 realm 의 공개키로 검증하므로 서명에서 걸린다."
req "POST $API/realms/employee/token/refresh"
res "$(curl -s -w ' [%{http_code}]' -X POST "$API/realms/employee/token/refresh" \
    -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}")"

# ---------------------------------------------------------------------------
step "7. 다른 서비스는 이 공개키로 검증한다 (JWKS)"
note "auth 에 '이 토큰 맞아?' 라고 묻지 않는다. 공개키만 받아다 각자 서명을 확인한다."
note "kid 로 어느 키를 쓸지 고른다. 두 realm 의 키가 함께 있으므로"
note "서명이 맞다고 realm 이 맞는 것은 아니다 — realm 클레임을 따로 확인해야 한다."
req "GET $BASE/.well-known/jwks.json"
curl -s "$BASE/.well-known/jwks.json" | python -c "
import json,sys
for k in json.load(sys.stdin)['keys']:
    print('   kid=%-16s kty=%s alg=%s  n=%s...' % (k['kid'], k['kty'], k['alg'], k['n'][:24]))
"

# ---------------------------------------------------------------------------
step "8. 로그아웃"
note "무효화 대상은 호출자 자신이다 — 무효화할 realm 도 토큰에서 읽는다."
note "단일 세션을 켜지 않았다면 발급된 access 토큰은 만료까지 유효하다(무상태 JWT)."
note "토큰 폐기는 호출자 몫이다."
req "POST $API/logout"
res "HTTP $(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/logout" -H "Authorization: Bearer $ACCESS")"

printf '\n\033[1;32m끝.\033[0m 이 계정은 남아 있습니다: %s\n\n' "$EMAIL"
