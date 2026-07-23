import { useEffect, useRef, useState } from "react";

import { AdminInventoryWorkspace } from "./AdminInventoryWorkspace";
import { AdminConcurrencyLab } from "./AdminConcurrencyLab";
import { AdminOrderWorkspace } from "./AdminOrderWorkspace";
import type { Product } from "./api-types";

type CreationState = "idle" | "loading" | "error" | "success";

type ProblemDetail = {
  detail?: string;
  fieldErrors?: { field: string; message: string }[];
};

type ProductPage = {
  content: Product[];
  page?: { totalPages: number };
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

export function AdminProductWorkspace({
  onCreated,
  onUpdated,
  products,
}: {
  onCreated: (product: Product) => void;
  onUpdated: (product: Product) => void;
  products: Product[];
}) {
  const [creationState, setCreationState] = useState<CreationState>("idle");
  const [creationErrors, setCreationErrors] = useState<Record<string, string>>({});
  const [creationMessage, setCreationMessage] = useState("");
  const [createdProduct, setCreatedProduct] = useState<Product | null>(null);
  const [selectedEditId, setSelectedEditId] = useState("");
  const [editState, setEditState] = useState<CreationState>("idle");
  const [editErrors, setEditErrors] = useState<Record<string, string>>({});
  const [editMessage, setEditMessage] = useState("");
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editPrice, setEditPrice] = useState("");
  const [editActive, setEditActive] = useState(false);
  const [editableProducts, setEditableProducts] = useState(products);
  const latestEditRequest = useRef(0);

  useEffect(() => {
    let current = true;
    setEditableProducts(products);

    async function loadAllProducts() {
      try {
        const loaded: Product[] = [];
        let pageNumber = 0;
        let totalPages = 1;
        do {
          const response = await fetch(
            `${apiBaseUrl}/api/v1/products?size=100&page=${pageNumber}&sort=id,asc`,
          );
          if (!response.ok) throw new Error("Product request failed");
          const page = (await response.json()) as ProductPage;
          loaded.push(...page.content);
          totalPages = page.page?.totalPages ?? 1;
          pageNumber += 1;
        } while (pageNumber < totalPages);
        if (current) setEditableProducts(loaded);
      } catch {
        // The Shop page remains a useful fallback if the Admin query is unavailable.
      }
    }

    void loadAllProducts();
    return () => {
      current = false;
    };
  }, [products]);

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
    } catch {
      setCreationMessage("The API is unavailable. Check the local backend and retry.");
      setCreationState("error");
    }
  }

  function selectProductForEdit(productId: string) {
    latestEditRequest.current += 1;
    setSelectedEditId(productId);
    setEditState("idle");
    setEditErrors({});
    setEditMessage("");
    const product = editableProducts.find(
      (candidate) => candidate.id === Number(productId),
    );
    if (!product) return;
    setEditName(product.name);
    setEditDescription(product.description ?? "");
    setEditPrice(String(product.price));
    setEditActive(product.active);
  }

  async function updateProduct(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const original = editableProducts.find(
      (candidate) => candidate.id === Number(selectedEditId),
    );
    if (!original) return;

    const payload: Record<string, string | number | boolean | null> = {};
    if (editName !== original.name) payload.name = editName;
    if (editDescription !== (original.description ?? "")) {
      payload.description = editDescription === "" ? null : editDescription;
    }
    if (Number(editPrice) !== original.price) payload.price = Number(editPrice);
    if (editActive !== original.active) payload.active = editActive;

    if (Object.keys(payload).length === 0) {
      setEditMessage("No product fields have changed.");
      setEditState("success");
      return;
    }

    setEditState("loading");
    setEditErrors({});
    setEditMessage("");
    const requestId = ++latestEditRequest.current;
    try {
      const response = await fetch(
        `${apiBaseUrl}/api/v1/products/${original.id}`,
        {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        },
      );
      if (requestId !== latestEditRequest.current) return;
      if (!response.ok) {
        const problem = (await response.json()) as ProblemDetail;
        setEditErrors(
          Object.fromEntries(
            (problem.fieldErrors ?? []).map((error) => [error.field, error.message]),
          ),
        );
        setEditMessage(problem.detail ?? "The product could not be updated.");
        setEditState("error");
        return;
      }

      const product = (await response.json()) as Product;
      if (requestId !== latestEditRequest.current) return;
      setEditableProducts((current) =>
        current.map((candidate) =>
          candidate.id === product.id ? product : candidate,
        ),
      );
      setEditName(product.name);
      setEditDescription(product.description ?? "");
      setEditPrice(String(product.price));
      setEditActive(product.active);
      setEditMessage(`${product.name} updated. The Shop now uses the new values.`);
      setEditState("success");
      onUpdated(product);
    } catch {
      if (requestId !== latestEditRequest.current) return;
      setEditMessage("The API is unavailable. Check the local backend and retry.");
      setEditState("error");
    }
  }

  return (
    <main className="admin-workspace">
      <section className="admin-form-panel" aria-labelledby="create-product-title">
        <div className="product-editor" aria-labelledby="edit-product-title">
          <div className="section-heading">
            <p className="eyebrow">PATCH /api/v1/products/:id</p>
            <h2 id="edit-product-title">Edit product</h2>
            <p className="section-intro">
              Only changed fields are sent. Inactive Products remain visible but
              unavailable for ordering.
            </p>
          </div>

          <label className="product-picker" htmlFor="edit-product">
            Edit product
            <select
              id="edit-product"
              value={selectedEditId}
              disabled={editState === "loading"}
              onChange={(event) => selectProductForEdit(event.target.value)}
            >
              <option value="">Choose a Product</option>
              {editableProducts.map((product) => (
                <option key={product.id} value={product.id}>
                  #{product.id} · {product.name}
                </option>
              ))}
            </select>
          </label>

          {selectedEditId && (
            <form className="product-form" onSubmit={(event) => void updateProduct(event)}>
              <fieldset disabled={editState === "loading"}>
                <FormField
                  id="edit-product-name"
                  label="Edit product name"
                  error={editErrors.name}
                >
                  <input
                    id="edit-product-name"
                    value={editName}
                    onChange={(event) => setEditName(event.target.value)}
                    required
                    maxLength={120}
                    aria-invalid={Boolean(editErrors.name)}
                    aria-describedby="edit-product-name-help"
                    data-state={fieldState(editState, editErrors.name)}
                  />
                </FormField>
                <FormField
                  id="edit-product-description"
                  label="Edit description"
                  error={editErrors.description}
                >
                  <textarea
                    id="edit-product-description"
                    value={editDescription}
                    onChange={(event) => setEditDescription(event.target.value)}
                    maxLength={1000}
                    aria-invalid={Boolean(editErrors.description)}
                    aria-describedby="edit-product-description-help"
                    data-state={fieldState(editState, editErrors.description)}
                  />
                </FormField>
                <FormField
                  id="edit-product-price"
                  label="Edit price (VND)"
                  error={editErrors.price}
                >
                  <input
                    id="edit-product-price"
                    value={editPrice}
                    onChange={(event) => setEditPrice(event.target.value)}
                    type="number"
                    required
                    min="0.01"
                    step="0.01"
                    inputMode="decimal"
                    aria-invalid={Boolean(editErrors.price)}
                    aria-describedby="edit-product-price-help"
                    data-state={fieldState(editState, editErrors.price)}
                  />
                </FormField>
                <label className="checkbox-field">
                  <input
                    aria-label="Edit active for sale"
                    type="checkbox"
                    checked={editActive}
                    onChange={(event) => setEditActive(event.target.checked)}
                  />
                  <span>
                    <strong>Active for sale</strong>
                    <small>Turn this off to prevent new Orders.</small>
                  </span>
                </label>
                {editState === "error" && (
                  <p className="form-error" role="alert">
                    {editMessage}
                  </p>
                )}
                {editState === "success" && (
                  <p className="form-success" role="status">
                    {editMessage}
                  </p>
                )}
                <button
                  className="submit-product"
                  type="submit"
                  aria-busy={editState === "loading"}
                  data-state={editState}
                >
                  {editState === "loading" ? "Saving changes…" : "Save changes"}
                </button>
              </fieldset>
            </form>
          )}
        </div>

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

      <AdminInventoryWorkspace createdProduct={createdProduct} />
      <AdminConcurrencyLab />
      <AdminOrderWorkspace />
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
