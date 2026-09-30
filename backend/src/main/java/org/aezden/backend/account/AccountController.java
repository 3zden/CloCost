package org.aezden.backend.account;

import lombok.RequiredArgsConstructor;
import org.aezden.backend.cost.Provider;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService service;

    @PostMapping
    public CloudAccount connect(@RequestBody ConnectAccountRequest request) {
        return service.connect(request.provider(), request.displayName(), request.secretRef());
    }

    @GetMapping
    public List<CloudAccount> list() {
        return service.list();
    }

    public record ConnectAccountRequest(Provider provider, String displayName, String secretRef) {}
}
