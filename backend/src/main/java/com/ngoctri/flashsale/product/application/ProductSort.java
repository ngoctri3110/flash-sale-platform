package com.ngoctri.flashsale.product.application;

import java.util.Arrays;

public enum ProductSort {
    ID_ASC("id,asc", "id", Direction.ASC),
    ID_DESC("id,desc", "id", Direction.DESC),
    NAME_ASC("name,asc", "name", Direction.ASC),
    NAME_DESC("name,desc", "name", Direction.DESC),
    PRICE_ASC("price,asc", "price", Direction.ASC),
    PRICE_DESC("price,desc", "price", Direction.DESC),
    CREATED_AT_ASC("createdAt,asc", "createdAt", Direction.ASC),
    CREATED_AT_DESC("createdAt,desc", "createdAt", Direction.DESC);

    private final String apiValue;
    private final String property;
    private final Direction direction;

    ProductSort(String apiValue, String property, Direction direction) {
        this.apiValue = apiValue;
        this.property = property;
        this.direction = direction;
    }

    public String property() {
        return property;
    }

    public Direction direction() {
        return direction;
    }

    public static ProductSort fromApiValue(String value) {
        return Arrays.stream(values())
                .filter(sort -> sort.apiValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new UnsupportedProductSortException(value));
    }

    public enum Direction {
        ASC,
        DESC
    }
}
