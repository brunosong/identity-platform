package com.brunosong.identityplatform.auth.service.web.authorization;

import com.brunosong.identityplatform.auth.service.application.authorization.exception.AuthorizationNotFoundException;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.DeleteUrlAccessUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.DescribeUrlAccessUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.FindUrlAccessUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.RegisterUrlAccessUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.SyncPermissionUrlAccessUseCase;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.DescribeUrlAccessCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.RegisterUrlAccessCommand;
import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.command.UrlResourceRef;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageQuery;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PageResult;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.PermissionUrlAccessResult;
import com.brunosong.identityplatform.auth.service.application.authorization.readmodel.UrlAccessView;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * URL 접근 규칙 운영 API. 권한↔URL 매핑 편집도 여기서 맡는다 — 매핑은 URL 리소스 쪽 일이다.
 */
@RestController
@RequestMapping("/api/rbac")
@RequiredArgsConstructor
public class UrlAccessAdminApiController {

    private final RegisterUrlAccessUseCase registerUrlAccess;
    private final DescribeUrlAccessUseCase describeUrlAccess;
    private final DeleteUrlAccessUseCase deleteUrlAccess;
    private final SyncPermissionUrlAccessUseCase syncPermissionUrlAccess;
    private final FindUrlAccessUseCase findUrlAccess;
    private final RbacAdminAccess access;

    @GetMapping("/url-access")
    public PageResult<UrlAccessView> page(HttpServletRequest request,
                                          @RequestParam Realm realm,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        access.require(request);
        return findUrlAccess.page(realm, keyword, category, PageQuery.of(page, size));
    }

    /** 등록된 URL 리소스 전체 — 권한↔URL 매핑 선택 화면이 쓴다. */
    @GetMapping("/registered-urls")
    public List<UrlAccessView> registeredUrls(HttpServletRequest request, @RequestParam Realm realm) {
        access.require(request);
        return findUrlAccess.of(realm);
    }

    @GetMapping("/url-access/{urlAccessId}")
    public UrlAccessView detail(HttpServletRequest request, @PathVariable Long urlAccessId) {
        access.require(request);
        return findUrlAccess.byId(urlAccessId)
                .orElseThrow(() -> AuthorizationNotFoundException.urlAccess(urlAccessId));
    }

    @PostMapping("/url-access")
    @ResponseStatus(HttpStatus.CREATED)
    public UrlAccessView register(HttpServletRequest request,
                                  @RequestParam Realm realm,
                                  @Valid @RequestBody RegisterUrlAccessRequest body) {
        access.require(request);
        return registerUrlAccess.register(new RegisterUrlAccessCommand(realm, body.urlPattern(),
                body.httpMethod(), body.description(), body.sortOrder() == null ? 0 : body.sortOrder()));
    }

    @PutMapping("/url-access/{urlAccessId}")
    public UrlAccessView describe(HttpServletRequest request,
                                  @PathVariable Long urlAccessId,
                                  @Valid @RequestBody DescribeUrlAccessRequest body) {
        access.require(request);
        return describeUrlAccess.describe(new DescribeUrlAccessCommand(urlAccessId, body.description(),
                body.sortOrder() == null ? 0 : body.sortOrder()));
    }

    @DeleteMapping("/url-access/{urlAccessId}")
    public ResponseEntity<Void> delete(HttpServletRequest request,
                                       @RequestParam Realm realm,
                                       @PathVariable Long urlAccessId) {
        access.require(request);
        deleteUrlAccess.delete(realm, urlAccessId);
        return ResponseEntity.noContent().build();
    }

    /** 권한 기준 역방향 — 이 권한에 매핑된 URL 리소스들. */
    @GetMapping("/permissions/{permissionId}/url-access")
    public PermissionUrlAccessResult forPermission(HttpServletRequest request, @PathVariable Long permissionId) {
        access.require(request);
        return findUrlAccess.forPermission(permissionId);
    }

    /** 권한에 매핑된 URL 을 입력 목록과 맞춘다. */
    @PutMapping("/permissions/{permissionId}/url-access")
    public ResponseEntity<Void> sync(HttpServletRequest request,
                                     @RequestParam Realm realm,
                                     @PathVariable Long permissionId,
                                     @Valid @RequestBody UrlMappingRequest body) {
        access.require(request);
        syncPermissionUrlAccess.sync(realm, permissionId, body.urls());
        return ResponseEntity.noContent().build();
    }

    public record RegisterUrlAccessRequest(@NotBlank String urlPattern, String httpMethod,
                                           String description, Integer sortOrder) {
    }

    public record DescribeUrlAccessRequest(String description, Integer sortOrder) {
    }

    public record UrlMappingRequest(@NotNull List<UrlResourceRef> urls) {
    }
}
