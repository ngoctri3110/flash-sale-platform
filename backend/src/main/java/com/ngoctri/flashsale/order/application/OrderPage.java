package com.ngoctri.flashsale.order.application;

import java.util.List;

public record OrderPage(
        List<OrderView> content,
        int number,
        int size,
        long totalElements,
        int totalPages) {
}
