package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.RenameRoleCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.RoleView;

/**
 * 역할의 표시 정보를 고친다.
 */
public interface RenameRoleUseCase {

    RoleView rename(RenameRoleCommand command);
}
