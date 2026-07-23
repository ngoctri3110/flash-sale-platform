# Flash Sale Ordering

This context sells a limited quantity of products and records accepted orders without overselling. It deliberately keeps the first domain small so concurrency and transactional correctness remain visible.

## Language

**Product**:
An item offered for purchase at its current price. A Product has no variants and may be active or unavailable for sale.
_Avoid_: Item, SKU, Flash Sale

**Inventory**:
The sellable quantity associated with exactly one Product. It does not include reservations, warehouses, or transfers.
_Avoid_: Stock record, Warehouse inventory

**Available Quantity**:
The number of units that may still be accepted into new Orders.
_Avoid_: Reserved quantity, On-hand quantity

**Order**:
An accepted purchase of one Product in a quantity from one to five at the price agreed when the purchase was accepted. A rejected purchase attempt is not an Order.
_Avoid_: Cart, Purchase attempt, Payment

**Customer**:
The party placing an Order. In the MVP, a Customer is identified for request correlation but is not an authenticated account.
_Avoid_: User, Account, Buyer

**Inventory Adjustment**:
An administrator-directed increase or decrease to Inventory with a recorded reason. An Order's inventory deduction is not an Inventory Adjustment.
_Avoid_: Stock overwrite, Order deduction

**Order Created**:
The fact that an Order was accepted and its Inventory deduction completed.
_Avoid_: Order requested, Payment completed
