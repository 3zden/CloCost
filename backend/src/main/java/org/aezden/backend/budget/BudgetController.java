package org.aezden.backend.budget;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
public class BudgetController {
    private final BudgetService service;
    @GetMapping
    public List<BudgetService.BudgetStatus> list() { return service.list(); }
    @PostMapping
    public Budget create(@RequestBody Budget budget) { return service.create(budget); }
}
