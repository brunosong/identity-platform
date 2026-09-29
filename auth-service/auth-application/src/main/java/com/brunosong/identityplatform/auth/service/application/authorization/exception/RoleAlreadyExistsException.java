package com.brunosong.identityplatform.auth.service.application.authorization.exception;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * 같은 realm 에 이미 있는 역할 코드로 만들려 했을 때.
 *
 * <p>DB 의 유일 제약도 막지만 그건 저장하는 순간의 예외라 무엇이 겹쳤는지 말해 주지 못한다.
 * 저장 전에 먼저 보고 코드를 짚어 준다.
 */
public class RoleAlreadyExistsException extends RuntimeException {

    public RoleAlreadyExistsException(Realm realm, String roleCode) {
        super(realm + " 에 이미 있는 역할 코드입니다: " + roleCode);
    }
}
