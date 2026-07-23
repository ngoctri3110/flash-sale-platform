import { useEffect, useRef, useState } from "react";

type HealthState = "checking" | "healthy" | "unavailable";
type LoadState = "loading" | "ready" | "error";

type Product = {
  id: number;
  name: string;
  description: string;
  price: number;
  currency: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

type ProductPage = {
  content: Product[];
  page: {
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

function App() {
  const [health, setHealth] = useState<HealthState>("checking");
  const [products, setProducts] = useState<Product[]>([]);
  const [listState, setListState] = useState<LoadState>("loading");
  const [retryState, setRetryState] = useState<LoadState>("ready");
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [selectedProductId, setSelectedProductId] = useState<number | null>(null);
  const [detailState, setDetailState] = useState<LoadState>("ready");
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [productQuery, setProductQuery] = useState("");
  const [activeCommand, setActiveCommand] = useState(0);
  const latestDetailRequest = useRef(0);

  const filteredProducts = products.filter((product) =>
    product.name.toLocaleLowerCase().includes(productQuery.toLocaleLowerCase()),
  );

  async function loadProducts(
    isCurrent: () => boolean = () => true,
    isRetry = false,
  ) {
    if (isRetry) {
      setRetryState("loading");
    } else {
      setListState("loading");
    }
    try {
      const response = await fetch(`${apiBaseUrl}/api/v1/products`);
      if (!response.ok) throw new Error("Product list request failed");
      const body = (await response.json()) as ProductPage;
      if (isCurrent()) {
        setProducts(body.content);
        setListState("ready");
        setRetryState("ready");
      }
    } catch {
      if (isCurrent()) {
        setListState("error");
        setRetryState("error");
      }
    }
  }

  useEffect(() => {
    let isActive = true;

    void fetch(`${apiBaseUrl}/actuator/health`)
      .then(async (response) => {
        const body = (await response.json()) as { status?: string };
        if (isActive) {
          setHealth(response.ok && body.status === "UP" ? "healthy" : "unavailable");
        }
      })
      .catch(() => {
        if (isActive) setHealth("unavailable");
      });

    void loadProducts(() => isActive);

    return () => {
      isActive = false;
    };
  }, []);

  useEffect(() => {
    function handleShortcut(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLocaleLowerCase() === "k") {
        event.preventDefault();
        setPaletteOpen(true);
      }
      if (event.key === "Escape") {
        setPaletteOpen(false);
      }
    }

    document.addEventListener("keydown", handleShortcut);
    return () => document.removeEventListener("keydown", handleShortcut);
  }, []);

  async function selectProduct(productId: number) {
    const requestId = latestDetailRequest.current + 1;
    latestDetailRequest.current = requestId;
    setSelectedProductId(productId);
    setDetailState("loading");
    setSelectedProduct(null);
    try {
      const response = await fetch(`${apiBaseUrl}/api/v1/products/${productId}`);
      if (!response.ok) throw new Error("Product detail request failed");
      const product = (await response.json()) as Product;
      if (latestDetailRequest.current !== requestId) return;
      setSelectedProduct(product);
      setDetailState("ready");
    } catch {
      if (latestDetailRequest.current !== requestId) return;
      setDetailState("error");
    }
  }

  function runProductCommand(productId: number) {
    setPaletteOpen(false);
    setProductQuery("");
    setActiveCommand(0);
    void selectProduct(productId);
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand-cluster">
          <a className="wordmark" href="/" aria-label="Flash Sale Platform home">
            Flash Sale
          </a>
          <div className={`health-status health-status--${health}`} aria-live="polite">
            <span className="health-dot" aria-hidden="true" />
            {health === "checking" && "Connecting"}
            {health === "healthy" && "API online"}
            {health === "unavailable" && "API offline"}
          </div>
        </div>
        <button
          className="command-trigger"
          type="button"
          onClick={() => setPaletteOpen(true)}
          aria-label="Open product finder"
          aria-controls="product-finder"
          aria-expanded={paletteOpen}
          data-state={paletteOpen ? "success" : "idle"}
        >
          Find product <kbd>Ctrl K</kbd>
        </button>
      </header>

      <main className="workspace">
        <section className="catalogue" aria-labelledby="catalogue-title">
          <div className="section-heading">
            <p className="eyebrow">GET /api/v1/products</p>
            <h1 id="catalogue-title">Available products</h1>
          </div>

          {listState === "loading" && <p role="status">Loading products…</p>}
          {listState === "error" && (
            <div className="error-state" role="alert" aria-label="Products unavailable">
              <p>The catalogue could not be loaded.</p>
              <button
                type="button"
                onClick={() => void loadProducts(() => true, true)}
                disabled={retryState === "loading"}
                aria-busy={retryState === "loading"}
                data-state={retryState}
              >
                {retryState === "loading" ? "Retrying productsâ€¦" : "Retry products"}
              </button>
            </div>
          )}
          {listState === "ready" && (
            products.length === 0 ? (
              <p className="empty-state">No products are available for this sale yet.</p>
            ) : (
              <div className="product-list">
                {products.map((product) => (
                  <button
                    className="product-row"
                    key={product.id}
                    type="button"
                    onClick={() => void selectProduct(product.id)}
                    disabled={
                      selectedProductId === product.id && detailState === "loading"
                    }
                    aria-busy={
                      selectedProductId === product.id && detailState === "loading"
                    }
                    aria-pressed={selectedProductId === product.id}
                    data-state={
                      selectedProductId !== product.id
                        ? "idle"
                        : detailState === "ready"
                          ? "success"
                          : detailState
                    }
                  >
                    <span className="product-id">#{product.id.toString().padStart(3, "0")}</span>
                    <span className="product-name">{product.name}</span>
                    <span className="product-price">{formatMoney(product)}</span>
                    <span aria-hidden="true">→</span>
                  </button>
                ))}
              </div>
            )
          )}
        </section>

        <aside className="detail-panel" aria-live="polite">
          {detailState === "loading" && <p role="status">Loading product details…</p>}
          {detailState === "error" && (
            <div role="alert" aria-label="Product details unavailable">
              <p>Product details could not be loaded.</p>
              <p>Select another product or try this one again.</p>
            </div>
          )}
          {detailState === "ready" && selectedProduct && (
            <>
              <p className="eyebrow">Product #{selectedProduct.id}</p>
              <h2>{selectedProduct.name}</h2>
              <p>{selectedProduct.description}</p>
              <dl>
                <div>
                  <dt>Price</dt>
                  <dd>{formatMoney(selectedProduct)}</dd>
                </div>
                <div>
                  <dt>Availability</dt>
                  <dd>
                    {selectedProduct.active
                      ? "Available for ordering"
                      : "Currently unavailable"}
                  </dd>
                </div>
              </dl>
            </>
          )}
          {detailState === "ready" && !selectedProduct && (
            <p>Select a product to inspect its public API response.</p>
          )}
        </aside>
      </main>

      {paletteOpen && (
        <div
          className="palette-backdrop"
          onMouseDown={(event) => {
            if (event.currentTarget === event.target) setPaletteOpen(false);
          }}
        >
          <section
            id="product-finder"
            className="command-palette"
            role="dialog"
            aria-modal="true"
            aria-label="Product finder"
          >
            <label htmlFor="product-search">Filter products</label>
            <input
              autoFocus
              id="product-search"
              type="search"
              value={productQuery}
              onChange={(event) => {
                setProductQuery(event.target.value);
                setActiveCommand(0);
              }}
              onKeyDown={(event) => {
                if (event.key === "ArrowDown") {
                  event.preventDefault();
                  setActiveCommand((current) =>
                    Math.min(current + 1, filteredProducts.length - 1),
                  );
                }
                if (event.key === "ArrowUp") {
                  event.preventDefault();
                  setActiveCommand((current) => Math.max(current - 1, 0));
                }
                if (event.key === "Enter" && filteredProducts[activeCommand]) {
                  runProductCommand(filteredProducts[activeCommand].id);
                }
              }}
              aria-label="Filter products"
              aria-invalid={productQuery.length > 0 && filteredProducts.length === 0}
              data-state={
                productQuery.length === 0
                  ? "idle"
                  : filteredProducts.length === 0
                    ? "error"
                    : "success"
              }
            />
            <div className="command-results">
              {filteredProducts.map((product, index) => (
                <button
                  className={index === activeCommand ? "is-active" : undefined}
                  key={product.id}
                  type="button"
                  onMouseEnter={() => setActiveCommand(index)}
                  onClick={() => runProductCommand(product.id)}
                  disabled={
                    selectedProductId === product.id && detailState === "loading"
                  }
                  aria-busy={
                    selectedProductId === product.id && detailState === "loading"
                  }
                  data-state={
                    selectedProductId !== product.id
                      ? "idle"
                      : detailState === "ready"
                        ? "success"
                        : detailState
                  }
                >
                  <span>{product.name}</span>
                  <span>{formatMoney(product)}</span>
                </button>
              ))}
              {filteredProducts.length === 0 && <p>No matching products.</p>}
            </div>
          </section>
        </div>
      )}

      <footer className="app-footer">
        <p>Learning build · Java 21 + Spring Boot + React</p>
      </footer>
    </div>
  );
}

function formatMoney(product: Pick<Product, "price" | "currency">) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: product.currency,
    maximumFractionDigits: 0,
  }).format(product.price);
}

export default App;
