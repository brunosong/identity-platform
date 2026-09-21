package com.brunosong.identityplatform.auth.service.application.oauth.service;

import com.brunosong.identityplatform.auth.service.application.oauth.exception.OAuthClientAlreadyExistsException;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.FindOAuthClientsUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.RegisterOAuthClientUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.RegisterOAuthClientCommand;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.out.OAuthClientRepository;
import com.brunosong.identityplatform.auth.service.domain.oauth.OAuthClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 등록된 앱에 관한 유스케이스 구현.
 *
 * <p>주소의 형식을 따지는 일은 도메인이 한다. 여기가 보는 것은 이름이 비어 있는가 하나다 -
 * 그것은 저장소를 봐야 알 수 있는 값이라 도메인이 답할 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OAuthClientService implements RegisterOAuthClientUseCase, FindOAuthClientsUseCase {

    private final OAuthClientRepository clientRepository;

    @Override
    @Transactional
    public OAuthClient register(RegisterOAuthClientCommand command) {
        OAuthClient client = OAuthClient.register(command.clientId(), command.realm(),
                command.redirectUris());
        if (clientRepository.exists(client.getClientId())) {
            throw new OAuthClientAlreadyExistsException(client.getClientId());
        }
        return clientRepository.save(client);
    }

    @Override
    public List<OAuthClient> findAll() {
        return clientRepository.findAll();
    }
}
