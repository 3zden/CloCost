package org.aezden.backend.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {
    private final AlertRepository repository;
    @GetMapping
    public List<Alert> list() { return repository.findAllByOrderByTriggeredAtDesc(); }
}
