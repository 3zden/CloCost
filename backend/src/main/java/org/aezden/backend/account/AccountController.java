package org.aezden.backend.account;

import lombok.RequiredArgsConstructor;
import org.aezden.backend.cost.Provider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService service;

    @PostMapping
    public CloudAccount connect(@AuthenticationPrincipal Jwt jwt, @RequestBody ConnectAccountRequest request) {
        return service.connect(userId(jwt), request.provider(), request.displayName(), request.secretRef());
    }

    @GetMapping
    public List<CloudAccount> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record ConnectAccountRequest(Provider provider, String displayName, String secretRef) {}
}
