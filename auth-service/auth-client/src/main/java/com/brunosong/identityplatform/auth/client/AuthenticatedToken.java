package com.brunosong.identityplatform.auth.client;

import java.util.List;

/**
 * 검증을 통과한 토큰에서 소비 서비스가 실제로 쓰는 값들.
 *
 * <p>클레임 맵을 그대로 넘기지 않는다. 그러면 소비 서비스마다 클레임 이름을 문자열로 적게 되고,
 * 발급기가 이름을 바꿀 때 어디가 깨지는지 알 수 없다. 이름을 아는 곳은 이 라이브러리 한 곳이다.
 *
 * @param issuer      이 토큰을 만든 곳 — {@code {auth 주소}/realms/{realm}}. 검증기에 설정된 값과
 *                    같을 때만 여기까지 온다. realm 이 이 안에 들어 있으므로 별도의 realm 클레임은 없다:
 *                    realm 마다 서명키가 달라서 <b>어느 키로 검증됐는지가 곧 realm</b> 이고, 그것을
 *                    표준 자리에서 밝히는 값이 {@code iss} 다.
 * @param subjectId   주체 식별자. realm 마다 의미가 다르다(직원은 사번 성격의 값, 고객은 UUID).
 *                    그래서 발급자 없이 이 값만으로 사람을 특정할 수 없다.
 * @param permissions 발급 시점의 권한 코드. 토큰에 실려 있어 요청마다 auth 에 묻지 않는다.
 *                    바꿔 말해 <b>발급 뒤의 권한 변경은 이 토큰에 반영되지 않는다</b> —
 *                    다음 갱신 때 들어온다.
 * @param rbacRev     realm 전역 인가 정책의 리비전. auth 가 정책을 바꾸면 올라간다.
 */
public record AuthenticatedToken(
        String issuer,
        String subjectId,
        List<String> permissions,
        long rbacRev
) {

    public boolean hasPermission(String permissionCode) {
        return permissions.contains(permissionCode);
    }
}
