import { useState } from "react";

import type { Product } from "./api-types";

type RequestState = "idle" | "loading" | "error" | "success";

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

type ProblemDetail = {
  code?: string;
  detail?: string;
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

export function ShopOrderForm({ product }: { product: Product }) {
  const [customerId] = useState(() => crypto.randomUUID());
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const [quantity, setQuantity] = useState("1");
  const [requestState, setRequestState] = useState<RequestState>("idle");
  const [acceptedOrder, setAcceptedOrder] = useState<Order | null>(null);
  const [wasReplay, setWasReplay] = useState(false);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);

  async function placeOrder(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setRequestState("loading");
    setAcceptedOrder(null);
    setWasReplay(false);
    setProblem(null);

    try {
      const response = await fetch(`${apiBaseUrl}/api/v1/orders`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey,
        },
        body: JSON.stringify({
          customerId,
          productId: product.id,
          quantity: Number(quantity),
        }),
      });

      if (!response.ok) {
        setProblem((await response.json()) as ProblemDetail);
        setRequestState("error");
        return;
      }

      setAcceptedOrder((await response.json()) as Order);
      setWasReplay(response.status === 200);
      setRequestState("success");
    } catch {
      setProblem({
        code: "API_UNAVAILABLE",
        detail: "The API is unavailable. Check the local backend and retry.",
      });
      setRequestState("error");
    }
  }

  return (
    <form className="shop-order-form" onSubmit={(event) => void placeOrder(event)}>
      <div className="order-form-heading">
        <p className="eyebrow">POST /api/v1/orders</p>
        <h3>Place an order</h3>
      </div>
      <fieldset disabled={requestState === "loading"}>
        <label htmlFor={`order-quantity-${product.id}`}>Quantity</label>
        <input
          id={`order-quantity-${product.id}`}
          name="quantity"
          type="number"
          min="1"
          max="5"
          step="1"
          required
          value={quantity}
          onChange={(event) => {
            setQuantity(event.target.value);
            setRequestState("idle");
            setAcceptedOrder(null);
            setWasReplay(false);
            setProblem(null);
          }}
        />
        <div className="request-identity">
          <label htmlFor={`customer-id-${product.id}`}>Customer ID</label>
          <input
            id={`customer-id-${product.id}`}
            value={customerId}
            readOnly
          />
          <label htmlFor={`idempotency-key-${product.id}`}>Idempotency key</label>
          <input
            id={`idempotency-key-${product.id}`}
            value={idempotencyKey}
            readOnly
          />
        </div>
        {requestState === "error" && problem && (
          <div className="order-outcome order-outcome--error" role="alert">
            <strong>{problem.code ?? "ORDER_REJECTED"}</strong>
            <span>{problem.detail ?? "The Order could not be accepted."}</span>
          </div>
        )}
        {requestState === "success" && acceptedOrder && (
          <div className="order-outcome order-outcome--success">
            <strong role="status">
              Order #{acceptedOrder.id} {wasReplay ? "replayed" : "accepted"}
            </strong>
            <span>
              {acceptedOrder.quantity} ×{" "}
              {formatMoney(acceptedOrder.unitPrice, acceptedOrder.currency)}
            </span>
          </div>
        )}
        <button
          className="place-order"
          type="submit"
          aria-busy={requestState === "loading"}
          data-state={requestState}
        >
          {requestState === "loading"
            ? "Placing order…"
            : requestState === "success"
              ? "Replay same request"
              : "Place order"}
        </button>
      </fieldset>
    </form>
  );
}

function formatMoney(amount: number, currency: string) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}
