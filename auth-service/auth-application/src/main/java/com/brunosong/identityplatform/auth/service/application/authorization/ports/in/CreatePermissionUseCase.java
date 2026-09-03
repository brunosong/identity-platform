package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.CreatePermissionCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionView;

/**
 * 권한을 새로 만든다.
 */
public interface CreatePermissionUseCase {

    PermissionView create(CreatePermissionCommand command);
}
