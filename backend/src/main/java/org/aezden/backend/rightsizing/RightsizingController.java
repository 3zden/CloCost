package org.aezden.backend.rightsizing;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rightsizing")
@RequiredArgsConstructor
public class RightsizingController {
    private final RightsizingService service;
    @GetMapping
    public List<RightsizingRecommendation> list() { return service.list(); }
}
