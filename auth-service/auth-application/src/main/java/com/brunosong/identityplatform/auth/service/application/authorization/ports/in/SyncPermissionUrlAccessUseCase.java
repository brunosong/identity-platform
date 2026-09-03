package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.UrlResourceRef;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import java.util.List;

/**
 * 권한에 매핑된 URL 을 입력 목록과 맞춘다. 목록에서 빠진 것은 이 권한의 매핑만 지우고
 * 리소스 자체는 남긴다 — 다른 권한이 같은 리소스를 쓰고 있을 수 있다.
 */
public interface SyncPermissionUrlAccessUseCase {

    void sync(Realm realm, Long permissionId, List<UrlResourceRef> urls);
}
