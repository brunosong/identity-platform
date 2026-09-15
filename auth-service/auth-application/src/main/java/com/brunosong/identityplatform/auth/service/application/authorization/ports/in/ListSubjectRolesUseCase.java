package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;
import java.util.Map;

/**
 * 토큰에 실릴 모양 그대로 읽는다: <b>역할</b>과 <b>서비스별 권한</b>.
 *
 * <p>역할(ADMIN, CUSTOMER)은 "이 사람이 조직에서 맡은 일" 이라 서비스로 갈리지 않는다.
 * 갈리는 것은 권한이다. 역할에서 권한 매핑을 타고 나온 결과를 서비스별로 나눈다.
 *
 * <p>전에는 권한 코드를 평면 목록으로 실었다({@code authLs}). 그러면 어휘가 전역이라 서비스가
 * 늘수록 부딪히고, 남의 서비스 권한이 이 서비스의 문을 열 수 있었다. 이제 서비스로
 * 네임스페이스를 가르고, 그 이름이 무엇을 여는지는 각 서비스가 자기 코드로 정한다.
 *
 * <p><b>좁히는 기준은 시스템이다.</b> 토큰의 {@code aud} 가 시스템 하나를 가리키므로 그 시스템의
 * 서비스들만 읽는다. 전에는 aud 에 적힌 서비스 목록으로 좁혔는데, 그러면 서비스를 붙일 때마다
 * auth 를 재배포해야 했고 이미 발급된 access 토큰은 재발급 전까지 새 서비스에 닿지 못했다.
 */
public interface ListSubjectRolesUseCase {

    SubjectRoles of(Realm realm, String subjectId, String systemId);

    /**
     * @param realmRoles         역할 코드. 토큰의 {@code realm_access.roles}
     * @param servicePermissions 서비스별 권한 코드. 토큰의 {@code resource_access.{service}.roles}
     */
    record SubjectRoles(List<String> realmRoles, Map<String, List<String>> servicePermissions) {
    }
}
