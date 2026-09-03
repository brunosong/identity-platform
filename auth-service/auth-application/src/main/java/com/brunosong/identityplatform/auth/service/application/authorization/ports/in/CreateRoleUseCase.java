package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.CreateRoleCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;

/**
 * 역할을 새로 만든다.
 */
public interface CreateRoleUseCase {

    RoleView create(CreateRoleCommand command);
}
