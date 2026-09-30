package org.aezden.backend.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {}
