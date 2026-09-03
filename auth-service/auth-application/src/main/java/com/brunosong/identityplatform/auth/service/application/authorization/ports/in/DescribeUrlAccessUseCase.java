package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.DescribeUrlAccessCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlAccessView;

/**
 * 등록된 URL 리소스의 설명과 정렬 순서를 고친다.
 */
public interface DescribeUrlAccessUseCase {

    UrlAccessView describe(DescribeUrlAccessCommand command);
}
