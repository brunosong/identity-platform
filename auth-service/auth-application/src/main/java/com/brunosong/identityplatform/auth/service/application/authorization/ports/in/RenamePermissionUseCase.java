package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.RenamePermissionCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionView;

/**
 * 권한의 표시 정보를 고친다.
 */
public interface RenamePermissionUseCase {

    PermissionView rename(RenamePermissionCommand command);
}
