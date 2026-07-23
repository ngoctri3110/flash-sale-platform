import { useCallback, useEffect, useRef, useState } from "react";

type LoadState = "loading" | "ready" | "error";
type OrderSort = "createdAt,desc" | "createdAt,asc" | "id,desc" | "id,asc";

type Order = {
  id: number;
  customerId: string;
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  currency: string;
  totalAmount: number;
  createdAt: string;
};

type OrderPage = {
  content: Order[];
  page: {
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
};

type OrderFilters = {
  productId: string;
  customerId: string;
  createdFrom: string;
  createdTo: string;
  sort: OrderSort;
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";
const emptyFilters: OrderFilters = {
  productId: "",
  customerId: "",
  createdFrom: "",
  createdTo: "",
  sort: "createdAt,desc",
};

export function AdminOrderWorkspace() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [pageNumber, setPageNumber] = useState(0);
  const [page, setPage] = useState<OrderPage["page"]>({
    number: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });
  const [draftFilters, setDraftFilters] = useState(emptyFilters);
  const [filters, setFilters] = useState(emptyFilters);
  const latestRequest = useRef(0);

  const loadOrders = useCallback(async () => {
    const requestId = ++latestRequest.current;
    setLoadState("loading");
    const query = new URLSearchParams({
      page: String(pageNumber),
      size: "20",
      sort: filters.sort,
    });
    if (filters.productId) query.set("productId", filters.productId);
    if (filters.customerId) query.set("customerId", filters.customerId);
    if (filters.createdFrom) {
      query.set("createdFrom", new Date(filters.createdFrom).toISOString());
    }
    if (filters.createdTo) {
      query.set("createdTo", new Date(filters.createdTo).toISOString());
    }

    try {
      const response = await fetch(`${apiBaseUrl}/api/v1/orders?${query}`);
      if (!response.ok) throw new Error("Order list request failed");
      const loaded = (await response.json()) as OrderPage;
      if (requestId !== latestRequest.current) return;
      setOrders(loaded.content);
      setPage(loaded.page);
      setLoadState("ready");
    } catch {
      if (requestId !== latestRequest.current) return;
      setLoadState("error");
    }
  }, [filters, pageNumber]);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  function applyFilters(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPageNumber(0);
    setFilters(draftFilters);
  }

  return (
    <section className="order-browser" aria-labelledby="order-browser-title">
      <div className="section-heading">
        <p className="eyebrow">GET /api/v1/orders</p>
        <h2 id="order-browser-title">Accepted Orders</h2>
        <p className="section-intro">
          Query the immutable purchase snapshots used for investigation.
        </p>
      </div>

      <form className="order-filters" onSubmit={applyFilters}>
        <fieldset disabled={loadState === "loading"}>
          <FilterInput
            id="order-product-filter"
            label="Order product ID"
            type="number"
            min="1"
            value={draftFilters.productId}
            onChange={(value) =>
              setDraftFilters((current) => ({ ...current, productId: value }))
            }
          />
          <FilterInput
            id="order-customer-filter"
            label="Order customer ID"
            value={draftFilters.customerId}
            onChange={(value) =>
              setDraftFilters((current) => ({ ...current, customerId: value }))
            }
          />
          <FilterInput
            id="order-created-from"
            label="Orders created from"
            type="datetime-local"
            value={draftFilters.createdFrom}
            onChange={(value) =>
              setDraftFilters((current) => ({ ...current, createdFrom: value }))
            }
          />
          <FilterInput
            id="order-created-to"
            label="Orders created before"
            type="datetime-local"
            value={draftFilters.createdTo}
            onChange={(value) =>
              setDraftFilters((current) => ({ ...current, createdTo: value }))
            }
          />
          <label htmlFor="order-sort">
            Order sort
            <select
              id="order-sort"
              value={draftFilters.sort}
              onChange={(event) =>
                setDraftFilters((current) => ({
                  ...current,
                  sort: event.target.value as OrderSort,
                }))
              }
            >
              <option value="createdAt,desc">Newest first</option>
              <option value="createdAt,asc">Oldest first</option>
              <option value="id,desc">Highest ID first</option>
              <option value="id,asc">Lowest ID first</option>
            </select>
          </label>
          <button type="submit">Apply order filters</button>
        </fieldset>
      </form>

      {loadState === "loading" && <p role="status">Loading Orders…</p>}
      {loadState === "error" && (
        <div className="order-browser-error" role="alert">
          <p>Orders could not be loaded.</p>
          <button type="button" onClick={() => void loadOrders()}>
            Retry Orders
          </button>
        </div>
      )}
      {loadState === "ready" && orders.length === 0 && (
        <p className="empty-state">No accepted Orders match these filters.</p>
      )}
      {loadState === "ready" && orders.length > 0 && (
        <div className="order-list">
          {orders.map((order) => (
            <article key={order.id}>
              <div className="order-list-heading">
                <strong>Order #{order.id}</strong>
                <time dateTime={order.createdAt}>{formatDate(order.createdAt)}</time>
              </div>
              <h3>{order.productName}</h3>
              <p>
                {order.quantity} × {formatMoney(order.unitPrice, order.currency)}
              </p>
              <dl>
                <div>
                  <dt>Total</dt>
                  <dd>{formatMoney(order.totalAmount, order.currency)}</dd>
                </div>
                <div>
                  <dt>Product</dt>
                  <dd>#{order.productId}</dd>
                </div>
                <div>
                  <dt>Customer</dt>
                  <dd>{order.customerId}</dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
      )}

      <nav className="order-pagination" aria-label="Order pages">
        <button
          type="button"
          aria-label="Previous orders page"
          disabled={loadState === "loading" || pageNumber === 0}
          onClick={() => setPageNumber((current) => Math.max(0, current - 1))}
        >
          Previous
        </button>
        <span>
          Page {page.totalPages === 0 ? 0 : page.number + 1} of {page.totalPages}
        </span>
        <button
          type="button"
          aria-label="Next orders page"
          disabled={loadState === "loading" || pageNumber + 1 >= page.totalPages}
          onClick={() => setPageNumber((current) => current + 1)}
        >
          Next
        </button>
      </nav>
    </section>
  );
}

function FilterInput({
  id,
  label,
  value,
  onChange,
  type = "text",
  min,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  min?: string;
}) {
  return (
    <label htmlFor={id}>
      {label}
      <input
        id={id}
        type={type}
        min={min}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function formatMoney(amount: number, currency: string) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}
