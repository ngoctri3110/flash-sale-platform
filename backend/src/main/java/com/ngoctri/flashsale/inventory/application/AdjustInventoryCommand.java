package com.ngoctri.flashsale.inventory.application;

public record AdjustInventoryCommand(long productId, long quantityDelta, String reason) {}
