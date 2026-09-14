package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 주체가 가진 역할을 <b>realm 공통</b>과 <b>서비스별</b>로 갈라 읽는다. 토큰에 실릴 모양 그대로다.
 *
 * <p>전에는 권한 코드를 평면 목록으로 실었다({@code authLs}). 그러면 어휘가 전역이라 서비스가
 * 늘수록 부딪히고, auth 가 모든 서비스의 권한 이름을 알아야 했다. 이제 서비스로 네임스페이스를
 * 가르고, 그 이름이 무엇을 여는지는 각 서비스가 자기 코드로 정한다.
 *
 * <p>요청한 서비스({@code clientIds})의 역할만 읽는다 — 토큰의 {@code aud} 에 있는 서비스들이다.
 * 관계없는 서비스의 역할을 실으면 토큰이 realm 전체 크기로 자란다.
 */
public interface ListSubjectRolesUseCase {

    SubjectRoles of(Realm realm, String subjectId, Collection<String> clientIds);

    /**
     * @param realmRoles  realm 공통 역할 코드 — 토큰의 {@code realm_access.roles}
     * @param clientRoles 서비스별 역할 코드 — 토큰의 {@code resource_access.{client}.roles}
     */
    record SubjectRoles(List<String> realmRoles, Map<String, List<String>> clientRoles) {
    }
}
