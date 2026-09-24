package com.example.order;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class CatalogClient {
    private final RestClient client;

    CatalogClient(@Value("${catalog.base-url}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    Map findProduct(long productId) {
        return client.get().uri("/products/{id}", productId).retrieve().body(Map.class);
    }
}
