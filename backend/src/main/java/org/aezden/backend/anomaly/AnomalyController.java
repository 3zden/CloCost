package org.aezden.backend.anomaly;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/anomalies")
@RequiredArgsConstructor
public class AnomalyController {
    private final AnomalyRepository repository;
    @GetMapping
    public List<Anomaly> list(@RequestParam(defaultValue = "OPEN") AnomalyStatus status) {
        return repository.findByStatusOrderByDetectedAtDesc(status);
    }
}
