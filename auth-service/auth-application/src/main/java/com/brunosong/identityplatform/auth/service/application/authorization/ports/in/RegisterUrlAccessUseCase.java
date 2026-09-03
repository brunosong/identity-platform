package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.RegisterUrlAccessCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlAccessView;

/**
 * 보호할 URL 리소스를 등록한다. 등록 즉시 인가 캐시를 갱신한다.
 */
public interface RegisterUrlAccessUseCase {

    UrlAccessView register(RegisterUrlAccessCommand command);
}
