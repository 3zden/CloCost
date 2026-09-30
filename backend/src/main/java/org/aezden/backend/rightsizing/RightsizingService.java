package org.aezden.backend.rightsizing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RightsizingService {
    private final RightsizingRepository repository;

    public List<RightsizingRecommendation> list() {
        return repository.findAllByOrderByCreatedAtDesc();
    }
}
