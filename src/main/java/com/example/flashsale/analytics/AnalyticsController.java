package com.example.flashsale.analytics;

import com.example.flashsale.analytics.AnalyticsModels.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

/** 路径位于 /api/admin 下，自动先校验 JWT，再校验管理员身份。 */
@RestController
@RequestMapping("/api/admin/analytics")
public class AnalyticsController {
    private final AnalyticsService service;
    public AnalyticsController(AnalyticsService service) { this.service = service; }

    @GetMapping("/overview")
    public Overview overview() { return service.overview(); }

    @GetMapping("/products")
    public List<ProductStatistics> products() { return service.products(); }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductStatistics> product(@PathVariable Long id) {
        Optional<ProductStatistics> result = service.product(id);
        if (result.isPresent()) {
            return ResponseEntity.ok(result.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
