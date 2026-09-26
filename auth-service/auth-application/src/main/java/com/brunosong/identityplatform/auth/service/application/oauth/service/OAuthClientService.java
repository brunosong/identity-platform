package com.brunosong.identityplatform.auth.service.application.oauth.service;

import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidAuthorizationRequestException;
import com.brunosong.identityplatform.auth.service.application.oauth.exception.OAuthClientAlreadyExistsException;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.FindOAuthClientsUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.RegisterOAuthClientUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.StartAuthorizationUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.ValidateRedirectUriUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.AuthorizationRequestCommand;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.RegisterOAuthClientCommand;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.result.RegisteredOAuthClient;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.out.OAuthClientRepository;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationRequest;
import com.brunosong.identityplatform.auth.service.domain.oauth.OAuthClient;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
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
        StartAuthorizationUseCase, ValidateRedirectUriUseCase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OAuthClientRepository clientRepository;

    /**
     * 등록한다. confidential 이면 시크릿을 뽑아 붙이고 원문을 한 번 돌려준다.
     *
     * <p>시크릿은 32바이트 난수다. 사람이 고른 값을 받지 않는다. 그래야 해시 한 번으로 저장해도
     * 되돌릴 수 없다는 판단({@link OAuthClient})이 성립한다.
     *
     * <p>client_id 는 도메인이 난수로 뽑는다. 겹칠 일은 사실상 없지만 저장 전에 한 번 더 본다.
     * 겹치면 다시 뽑지 않고 실패로 끝낸다. 그런 일이 생겼다면 난수 쪽이 고장 난 것이다.
     */
    @Override
    @Transactional
    public RegisteredOAuthClient register(RegisterOAuthClientCommand command) {
        OAuthClient client = OAuthClient.register(command.realm(), command.name(), command.redirectUris());
        if (clientRepository.exists(client.getClientId())) {
            throw new OAuthClientAlreadyExistsException(client.getClientId());
        }
        String secret = command.confidential() ? randomSecret() : null;
        if (secret != null) {
            client = client.withSecret(secret);
        }
        return new RegisteredOAuthClient(clientRepository.save(client), secret);
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public List<OAuthClient> findAll() {
        return clientRepository.findAll();
    }

    /**
     * 등록된 앱의 등록된 주소인가. 로그아웃이 돌려보낼 곳을 고를 때 쓴다.
     *
     * <p>꺼진 앱은 통과시키지 않는다. 로그인을 시작할 수 없는 앱이라면 돌려보낼 이유도 없다.
     */
    @Override
    public boolean isRegistered(Realm realm, String clientId, String redirectUri) {
        if (clientId == null || redirectUri == null) {
            return false;
        }
        return clientRepository.findByClientId(realm, clientId)
                .filter(OAuthClient::isEnabled)
                .filter(client -> client.allowsRedirect(redirectUri))
                .isPresent();
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
