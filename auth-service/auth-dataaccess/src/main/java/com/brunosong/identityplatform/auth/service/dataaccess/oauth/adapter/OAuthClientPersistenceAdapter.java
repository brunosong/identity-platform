package com.brunosong.identityplatform.auth.service.dataaccess.oauth.adapter;

import com.brunosong.identityplatform.auth.service.application.oauth.ports.out.OAuthClientRepository;
import com.brunosong.identityplatform.auth.service.dataaccess.oauth.entity.OAuthClientJpaEntity;
import com.brunosong.identityplatform.auth.service.dataaccess.oauth.repository.OAuthClientJpaRepository;
import com.brunosong.identityplatform.auth.service.domain.oauth.OAuthClient;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * {@link OAuthClientRepository} 영속성 어댑터.
 *
 * <p>꺼진 앱도 그대로 도메인으로 옮긴다. 조회에서 걸러내면 "그런 앱이 없다" 와 "잠깐 꺼둔 앱이다" 가
 * 구분되지 않아, 운영에서 앱을 껐을 때 무슨 일이 벌어지는지 로그로도 알 수 없다.
 */
@Component
@RequiredArgsConstructor
public class OAuthClientPersistenceAdapter implements OAuthClientRepository {

    private final OAuthClientJpaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<OAuthClient> findByClientId(Realm realm, String clientId) {
        return repository.findByClientIdAndRealm(clientId, realm.name())
                .map(OAuthClientPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OAuthClient> findAll() {
        return repository.findAll().stream()
                .map(OAuthClientPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String clientId) {
        return repository.existsById(clientId);
    }

    @Override
    @Transactional
    public OAuthClient save(OAuthClient client) {
        return toDomain(repository.save(toEntity(client)));
    }

    private static OAuthClientJpaEntity toEntity(OAuthClient client) {
        OAuthClientJpaEntity e = new OAuthClientJpaEntity();
        e.setClientId(client.getClientId());
        e.setRealm(client.getRealm().name());
        e.setEnabled(client.isEnabled());
        e.setRedirectUris(new HashSet<>(client.getRedirectUris()));
        return e;
    }

    private static OAuthClient toDomain(OAuthClientJpaEntity e) {
        return OAuthClient.restore(e.getClientId(), Realm.valueOf(e.getRealm()),
                e.getRedirectUris(), e.isEnabled());
    }
}
