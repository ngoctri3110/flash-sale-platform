import { useCallback, useEffect, useRef, useState } from "react";

import type { Product } from "./api-types";

type RequestState = "idle" | "loading" | "error" | "success";
type LoadState = "loading" | "ready" | "error";

type ProblemDetail = {
  detail?: string;
  fieldErrors?: { field: string; message: string }[];
};

type Inventory = {
  productId: number;
  productName: string;
  availableQuantity: number;
  updatedAt: string;
};

type InventoryPage = {
  content: Inventory[];
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

export function AdminInventoryWorkspace({
  createdProduct,
}: {
  createdProduct: Product | null;
}) {
  const [inventory, setInventory] = useState<Inventory[]>([]);
  const [inventoryState, setInventoryState] = useState<LoadState>("loading");
  const [selectedProductId, setSelectedProductId] = useState("");
  const [adjustmentState, setAdjustmentState] = useState<RequestState>("idle");
  const [adjustmentErrors, setAdjustmentErrors] = useState<Record<string, string>>(
    {},
  );
  const [adjustmentMessage, setAdjustmentMessage] = useState("");
  const latestInventoryRequest = useRef(0);

  const loadInventory = useCallback(async () => {
    const requestId = ++latestInventoryRequest.current;
    setInventoryState("loading");
    try {
      const response = await fetch(
        `${apiBaseUrl}/api/v1/inventories?size=100&sort=productId,desc`,
      );
      if (!response.ok) throw new Error("Inventory request failed");
      const page = (await response.json()) as InventoryPage;
      if (requestId !== latestInventoryRequest.current) return;
      setInventory(page.content);
      setInventoryState("ready");
    } catch {
      if (requestId !== latestInventoryRequest.current) return;
      setInventoryState("error");
    }
  }, []);

  useEffect(() => {
    void loadInventory();
  }, [createdProduct?.id, loadInventory]);

  async function adjustInventory(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const productId = Number(selectedProductId);
    const payload = {
      quantityDelta: Number(data.get("quantityDelta")),
      reason: String(data.get("reason")),
    };

    setAdjustmentState("loading");
    setAdjustmentErrors({});
    setAdjustmentMessage("");
    try {
      const response = await fetch(
        `${apiBaseUrl}/api/v1/inventories/${productId}/adjustments`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        },
      );
      if (!response.ok) {
        const problem = (await response.json()) as ProblemDetail;
        setAdjustmentErrors(
          Object.fromEntries(
            (problem.fieldErrors ?? []).map((error) => [error.field, error.message]),
          ),
        );
        setAdjustmentMessage(
          problem.detail ?? "The Inventory Adjustment could not be applied.",
        );
        setAdjustmentState("error");
        return;
      }

      const adjusted = (await response.json()) as Inventory;
      setInventory((current) =>
        current.map((item) =>
          item.productId === adjusted.productId ? adjusted : item,
        ),
      );
      setAdjustmentMessage(
        `${adjusted.productName} adjusted to ${adjusted.availableQuantity.toLocaleString(
          "en-US",
        )} available.`,
      );
      setAdjustmentState("success");
      form.reset();
    } catch {
      setAdjustmentMessage("The API is unavailable. Check the local backend and retry.");
      setAdjustmentState("error");
    }
  }

  return (
    <aside className="creation-ledger" aria-live="polite">
      <p className="eyebrow">Inventory control</p>
      <h2>Available Quantity</h2>
      <p>
        Apply a signed delta. PostgreSQL rejects any result below zero and records
        every accepted adjustment.
      </p>

      {createdProduct && (
        <p role="status">
          {createdProduct.name} committed. The inventory snapshot was refreshed.
        </p>
      )}
      {inventoryState === "loading" && <p role="status">Loading inventory…</p>}
      {inventoryState === "error" && (
        <div role="alert">
          <p>Inventory could not be loaded.</p>
          <button type="button" onClick={() => void loadInventory()}>
            Retry inventory
          </button>
        </div>
      )}
      {inventoryState === "ready" && inventory.length === 0 && (
        <p>No Inventory records exist yet.</p>
      )}
      {inventoryState === "ready" && inventory.length > 0 && (
        <>
          <form
            className="inventory-adjustment-form"
            onSubmit={(event) => void adjustInventory(event)}
          >
            <fieldset disabled={adjustmentState === "loading"}>
              <div className="inventory-adjustment-field">
                <label htmlFor="adjustment-product">Inventory to adjust</label>
                <select
                  id="adjustment-product"
                  value={selectedProductId}
                  onChange={(event) => {
                    setSelectedProductId(event.target.value);
                    setAdjustmentState("idle");
                    setAdjustmentErrors({});
                    setAdjustmentMessage("");
                  }}
                  required
                >
                  <option value="">Choose Inventory</option>
                  {inventory.map((item) => (
                    <option key={item.productId} value={item.productId}>
                      #{item.productId} · {item.productName} ·{" "}
                      {item.availableQuantity} available
                    </option>
                  ))}
                </select>
              </div>
              <div className="inventory-adjustment-field">
                <label htmlFor="quantity-delta">Quantity delta</label>
                <input
                  id="quantity-delta"
                  name="quantityDelta"
                  type="number"
                  step="1"
                  required
                  aria-invalid={Boolean(adjustmentErrors.quantityDelta)}
                  aria-describedby="quantity-delta-help"
                  data-state={adjustmentErrors.quantityDelta ? "error" : adjustmentState}
                />
                <small id="quantity-delta-help">
                  {adjustmentErrors.quantityDelta ?? "Use a positive or negative integer."}
                </small>
              </div>
              <div className="inventory-adjustment-field">
                <label htmlFor="adjustment-reason">Adjustment reason</label>
                <textarea
                  id="adjustment-reason"
                  name="reason"
                  required
                  minLength={3}
                  maxLength={200}
                  aria-invalid={Boolean(adjustmentErrors.reason)}
                  aria-describedby="adjustment-reason-help"
                  data-state={adjustmentErrors.reason ? "error" : adjustmentState}
                />
                <small id="adjustment-reason-help">
                  {adjustmentErrors.reason ??
                    "Required for the audit trail (3–200 characters)."}
                </small>
              </div>
              {adjustmentState === "error" && (
                <p className="form-error" role="alert">
                  {adjustmentMessage}
                </p>
              )}
              {adjustmentState === "success" && (
                <p className="inventory-adjustment-success" role="status">
                  {adjustmentMessage}
                </p>
              )}
              <button
                className="apply-adjustment"
                type="submit"
                aria-busy={adjustmentState === "loading"}
                data-state={adjustmentState}
              >
                {adjustmentState === "loading"
                  ? "Applying adjustment…"
                  : "Apply adjustment"}
              </button>
            </fieldset>
          </form>

          <div className="inventory-list">
            {inventory.map((item) => (
              <article
                key={item.productId}
                className={item.productId === createdProduct?.id ? "is-new" : undefined}
              >
                <div>
                  <strong>{item.productName}</strong>
                  <small>Product #{item.productId}</small>
                </div>
                <span>{item.availableQuantity.toLocaleString("en-US")} available</span>
              </article>
            ))}
          </div>
        </>
      )}
    </aside>
  );
}
