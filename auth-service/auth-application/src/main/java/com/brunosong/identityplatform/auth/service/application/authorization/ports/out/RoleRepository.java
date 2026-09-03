package com.brunosong.identityplatform.auth.service.application.authorization.ports.out;

import com.brunosong.identityplatform.auth.service.domain.authorization.Role;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.Collection;
import java.util.Optional;

/**
 * 역할 쓰기 포트(SPI) — 애그리거트를 싣고 내린다.
 *
 * <p>조회는 {@link RoleQuery} 가 맡는다. 바꾸려고 읽는 것과 보여주려고 읽는 것을 한 포트에 두면
 * 읽기 쪽을 리플리카나 조회 전용 모델로 옮길 때 쓰기까지 끌려간다. 여기서 읽는 것은 언제나
 * "바꾸기 위해" 읽는 것이라 도메인 객체를 그대로 준다.
 */
public interface RoleRepository {

    /** 수정 대상 애그리거트를 싣는다(권한 매핑 포함). */
    Optional<Role> findById(Long roleId);

    /** role_code 로 애그리거트를 싣는다(realm 내 유일). 역할 부여가 코드로 대상을 찾을 때 쓴다. */
    Optional<Role> findByCode(Realm realm, String roleCode);

    /** 기본 필드 저장(신규/수정). 권한 매핑은 건드리지 않는다. */
    Role save(Role role);

    void deleteById(Long roleId);

    /** 역할의 권한 매핑을 입력 집합으로 교체. */
    void replacePermissions(Long roleId, Collection<Long> permissionIds);
}
