package com.brunosong.identityplatform.auth.service.application.authorization.service;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.CreatePermissionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.DeletePermissionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.RenamePermissionUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.CreatePermissionCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.RenamePermissionCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.PermissionQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.PermissionSearchQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.out.PermissionRepository;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageResult;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionView;
import com.brunosong.identityplatform.auth.service.domain.authorization.Permission;
import com.brunosong.identityplatform.auth.service.domain.authorization.UrlAccess;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 권한에 관한 유스케이스 구현.
 *
 * <p>쓰기는 {@link PermissionRepository}(애그리거트)로, 읽기는 {@link PermissionQuery}(읽기 모델)로 간다.
 *
 * <p>조건으로 거르는 조회는 {@link PermissionSearchQuery} 로 간다 — 검색은 조건이 늘고 저장소가
 * 갈릴 여지가 커서 포트를 따로 둔다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService implements CreatePermissionUseCase, RenamePermissionUseCase,
        DeletePermissionUseCase, FindPermissionsUseCase {

    private final PermissionRepository permissionRepository;
    private final PermissionQuery permissionQuery;
    private final PermissionSearchQuery permissionSearchQuery;

    @Override
    @Transactional
    public PermissionView create(CreatePermissionCommand command) {
        Permission permission = Permission.create(command.realm(), command.permissionCode(),
                command.permissionName(), command.category(), command.description());
        return PermissionView.from(permissionRepository.save(permission));
    }

    /** 저장본을 읽어 표시 정보만 바꾼다 — realm/permissionCode 는 도메인이 불변으로 잡고 있다. */
    @Override
    @Transactional
    public PermissionView rename(RenamePermissionCommand command) {
        Permission permission = permissionRepository.findById(command.permissionId())
                .orElseThrow(() -> AuthorizationNotFoundException.permission(command.permissionId()));
        permission.describeAs(command.permissionName(), command.category(), command.description());
        return PermissionView.from(permissionRepository.save(permission));
    }

    @Override
    @Transactional
    public void delete(Long permissionId) {
        permissionRepository.deleteById(permissionId);
    }

    @Override
    public List<PermissionView> of(Realm realm, String keyword) {
        return permissionSearchQuery.search(realm, keyword);
    }

    @Override
    public PageResult<PermissionView> page(Realm realm, String keyword, PageQuery pageQuery) {
        return permissionSearchQuery.searchPage(realm, keyword, pageQuery);
    }

    @Override
    public Optional<PermissionView> byId(Long permissionId) {
        return permissionQuery.findById(permissionId);
    }

    @Override
    public List<String> categories(Realm realm) {
        // 페이지 호출 URL 은 권한 그룹과 별개로 다루므로 그 분류를 맨 앞에 붙인다.
        List<String> categories = new ArrayList<>();
        categories.add(UrlAccess.PAGE_CATEGORY);
        categories.addAll(permissionSearchQuery.listActiveCategories(realm));
        return List.copyOf(categories);
    }
}
