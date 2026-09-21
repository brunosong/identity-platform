package com.brunosong.identityplatform.auth.service.application.oauth.service;

import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidAuthorizationRequestException;
import com.brunosong.identityplatform.auth.service.application.oauth.exception.OAuthClientAlreadyExistsException;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.FindOAuthClientsUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.RegisterOAuthClientUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.StartAuthorizationUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.AuthorizationRequestCommand;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.RegisterOAuthClientCommand;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.out.OAuthClientRepository;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
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
public class OAuthClientService implements RegisterOAuthClientUseCase, FindOAuthClientsUseCase,
        StartAuthorizationUseCase {

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

    /**
     * 규약을 먼저 보고(도메인), 그다음 등록된 앱인지 본다.
     *
     * <p>실패 사유를 나누지 않는다. 모르는 앱이든, 꺼진 앱이든, 등록되지 않은 주소든 요청자에게는
     * 같은 말로 끝난다 - 어느 쪽인지 알려주면 등록된 앱과 주소를 찍어보는 수단이 된다.
     */
    @Override
    public AuthorizationRequest start(AuthorizationRequestCommand command) {
        AuthorizationRequest request = AuthorizationRequest.of(command.responseType(),
                command.clientId(), command.redirectUri(), command.scope(), command.state(),
                command.codeChallenge(), command.codeChallengeMethod(), command.nonce());

        clientRepository.findByClientId(command.realm(), request.getClientId())
                .filter(OAuthClient::isEnabled)
                .filter(found -> found.allowsRedirect(request.getRedirectUri()))
                .orElseThrow(() -> new InvalidAuthorizationRequestException(
                        "등록되지 않은 앱이거나 등록되지 않은 주소입니다."));

        return request;
    }
}
