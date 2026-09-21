package com.brunosong.identityplatform.auth.service.application.oauth.ports.in;

import com.brunosong.identityplatform.auth.service.domain.oauth.OAuthClient;

import java.util.List;

/**
 * 등록된 앱 목록.
 *
 * <p>읽기 모델을 따로 두지 않고 애그리거트를 그대로 내준다. 화면이 보여줄 것이 애그리거트가 든
 * 값 전부고(이름, realm, 주소, 사용 여부), 조건으로 거르거나 페이지를 나누는 조회도 아니다.
 * 목록이 커지거나 다른 표를 붙여 보여줄 일이 생기면 그때 읽기 모델을 만든다.
 */
public interface FindOAuthClientsUseCase {

    List<OAuthClient> findAll();
}
