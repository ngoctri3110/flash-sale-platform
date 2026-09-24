package com.example.catalog;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CatalogController {
    @GetMapping("/products/{id}")
    Map<String, Object> find(@PathVariable long id) {
        return Map.of("id", id, "name", "Demo product", "price", new BigDecimal("100000.00"));
    }
}
