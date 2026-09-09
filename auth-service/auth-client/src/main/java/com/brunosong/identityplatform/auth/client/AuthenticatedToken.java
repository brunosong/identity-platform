package com.brunosong.identityplatform.auth.client;

import java.util.List;

/**
 * 검증을 통과한 토큰에서 소비 서비스가 실제로 쓰는 값들.
 *
 * <p>클레임 맵을 그대로 넘기지 않는다. 그러면 소비 서비스마다 클레임 이름을 문자열로 적게 되고,
 * 발급기가 이름을 바꿀 때 어디가 깨지는지 알 수 없다. 이름을 아는 곳은 이 라이브러리 한 곳이다.
 *
 * @param realm       이 토큰이 속한 영역("ADMIN" / "PORTAL"). 검증기가 상대하는 realm 과 같을
 *                    때만 채워지므로, 여기까지 온 값은 이미 확인된 것이다 — JWKS 가 realm 별로
 *                    나뉘어 있어 다른 realm 토큰은 서명 검증에서 죽고, 설령 주소를 잘못 가리켰더라도
 *                    클레임 대조에서 걸린다.
 * @param subjectId   주체 식별자. realm 마다 의미가 다르다(직원은 사번 성격의 값, 고객은 UUID).
 *                    그래서 realm 없이 이 값만으로 사람을 특정할 수 없다.
 * @param email       로그인 식별자. 표시용이며 바뀔 수 있으므로 키로 쓰지 않는다.
 * @param permissions 발급 시점의 권한 코드. 토큰에 실려 있어 요청마다 auth 에 묻지 않는다.
 *                    바꿔 말해 <b>발급 뒤의 권한 변경은 이 토큰에 반영되지 않는다</b> —
 *                    다음 갱신 때 들어온다.
 * @param rbacRev     realm 전역 인가 정책의 리비전. auth 가 정책을 바꾸면 올라간다.
 */
public record AuthenticatedToken(
        String realm,
        String subjectId,
        List<String> permissions,
        long rbacRev
) {

    public boolean isRealm(String expected) {
        return realm != null && realm.equals(expected);
    }

    public boolean hasPermission(String permissionCode) {
        return permissions.contains(permissionCode);
    }
}
