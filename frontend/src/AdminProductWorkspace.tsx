import { useCallback, useEffect, useState } from "react";

import type { Product } from "./App";

type CreationState = "idle" | "loading" | "error" | "success";
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

export function AdminProductWorkspace({
  onCreated,
}: {
  onCreated: (product: Product) => void;
}) {
  const [creationState, setCreationState] = useState<CreationState>("idle");
  const [creationErrors, setCreationErrors] = useState<Record<string, string>>({});
  const [creationMessage, setCreationMessage] = useState("");
  const [createdProduct, setCreatedProduct] = useState<Product | null>(null);
  const [inventory, setInventory] = useState<Inventory[]>([]);
  const [inventoryState, setInventoryState] = useState<LoadState>("loading");

  const loadInventory = useCallback(async () => {
    setInventoryState("loading");
    try {
      const response = await fetch(`${apiBaseUrl}/api/v1/inventories?size=100`);
      if (!response.ok) throw new Error("Inventory request failed");
      const page = (await response.json()) as InventoryPage;
      setInventory(page.content);
      setInventoryState("ready");
    } catch {
      setInventoryState("error");
    }
  }, []);

  useEffect(() => {
    void loadInventory();
  }, [loadInventory]);

  async function createProduct(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const payload = {
      name: String(data.get("name")),
      description: String(data.get("description")),
      price: Number(data.get("price")),
      active: data.get("active") === "on",
      initialInventory: Number(data.get("initialInventory")),
    };

    setCreationState("loading");
    setCreationErrors({});
    setCreationMessage("");

    try {
      const response = await fetch(`${apiBaseUrl}/api/v1/products`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        const problem = (await response.json()) as ProblemDetail;
        setCreationErrors(
          Object.fromEntries(
            (problem.fieldErrors ?? []).map((error) => [error.field, error.message]),
          ),
        );
        setCreationMessage(problem.detail ?? "The product could not be created.");
        setCreationState("error");
        return;
      }

      const product = (await response.json()) as Product;
      setCreatedProduct(product);
      setCreationState("success");
      form.reset();
      onCreated(product);
      await loadInventory();
    } catch {
      setCreationMessage("The API is unavailable. Check the local backend and retry.");
      setCreationState("error");
    }
  }

  return (
    <main className="admin-workspace">
      <section className="admin-form-panel" aria-labelledby="create-product-title">
        <div className="section-heading">
          <p className="eyebrow">POST /api/v1/products</p>
          <h1 id="create-product-title">Create product</h1>
          <p className="section-intro">
            Product and initial Inventory commit together in one transaction.
          </p>
        </div>

        <form className="product-form" onSubmit={(event) => void createProduct(event)}>
          <fieldset disabled={creationState === "loading"}>
            <FormField id="product-name" label="Product name" error={creationErrors.name}>
              <input
                id="product-name"
                name="name"
                required
                maxLength={120}
                aria-invalid={Boolean(creationErrors.name)}
                aria-describedby="product-name-help"
                data-state={fieldState(creationState, creationErrors.name)}
              />
            </FormField>

            <FormField
              id="product-description"
              label="Description"
              error={creationErrors.description}
            >
              <textarea
                id="product-description"
                name="description"
                maxLength={1000}
                aria-invalid={Boolean(creationErrors.description)}
                aria-describedby="product-description-help"
                data-state={fieldState(creationState, creationErrors.description)}
              />
            </FormField>

            <div className="form-row">
              <FormField id="product-price" label="Price (VND)" error={creationErrors.price}>
                <input
                  id="product-price"
                  name="price"
                  type="number"
                  required
                  min="0.01"
                  step="0.01"
                  inputMode="decimal"
                  aria-invalid={Boolean(creationErrors.price)}
                  aria-describedby="product-price-help"
                  data-state={fieldState(creationState, creationErrors.price)}
                />
              </FormField>
              <FormField
                id="initial-inventory"
                label="Initial inventory"
                error={creationErrors.initialInventory}
              >
                <input
                  id="initial-inventory"
                  name="initialInventory"
                  type="number"
                  required
                  min="0"
                  max="1000000"
                  step="1"
                  inputMode="numeric"
                  aria-invalid={Boolean(creationErrors.initialInventory)}
                  aria-describedby="initial-inventory-help"
                  data-state={fieldState(
                    creationState,
                    creationErrors.initialInventory,
                  )}
                />
              </FormField>
            </div>

            <label className="checkbox-field">
              <input name="active" type="checkbox" defaultChecked />
              <span>
                <strong>Active for sale</strong>
                <small>Active Products appear in the Shop immediately.</small>
              </span>
            </label>

            {creationState === "error" && (
              <p className="form-error" role="alert">
                {creationMessage}
              </p>
            )}

            <button
              className="submit-product"
              type="submit"
              aria-busy={creationState === "loading"}
              data-state={creationState}
            >
              {creationState === "loading" ? "Creating product…" : "Create product"}
            </button>
          </fieldset>
        </form>
      </section>

      <aside className="creation-ledger" aria-live="polite">
        <p className="eyebrow">Persisted inventory</p>
        <h2>Inventory view</h2>
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
        )}
      </aside>
    </main>
  );
}

function FormField({
  id,
  label,
  error,
  children,
}: {
  id: string;
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="form-field">
      <label htmlFor={id}>{label}</label>
      {children}
      <small id={`${id}-help`}>{error ?? " "}</small>
    </div>
  );
}

function fieldState(state: CreationState, error?: string) {
  if (error) return "error";
  if (state === "loading") return "loading";
  if (state === "success") return "success";
  return "idle";
}
