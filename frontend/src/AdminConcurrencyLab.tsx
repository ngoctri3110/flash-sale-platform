import { useCallback, useEffect, useState } from "react";

type LoadState = "loading" | "ready" | "error";
type ScenarioState = "idle" | "running" | "complete" | "error";

type Inventory = {
  productId: number;
  productName: string;
  availableQuantity: number;
};

type InventoryPage = {
  content: Inventory[];
  page?: { totalPages: number };
};

type ScenarioResult = {
  accepted: number;
  replayed: number;
  insufficient: number;
  failed: number;
  initialQuantity: number;
  finalQuantity: number;
  durationMs: number;
  oversold: boolean;
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

export function AdminConcurrencyLab() {
  const [inventory, setInventory] = useState<Inventory[]>([]);
  const [inventoryState, setInventoryState] = useState<LoadState>("loading");
  const [productId, setProductId] = useState("");
  const [requestCount, setRequestCount] = useState("100");
  const [quantity, setQuantity] = useState("1");
  const [replayIdentity, setReplayIdentity] = useState(false);
  const [scenarioState, setScenarioState] = useState<ScenarioState>("idle");
  const [scenarioError, setScenarioError] = useState("");
  const [result, setResult] = useState<ScenarioResult | null>(null);

  const loadInventory = useCallback(async () => {
    setInventoryState("loading");
    try {
      const loaded: Inventory[] = [];
      let pageNumber = 0;
      let totalPages = 1;
      do {
        const response = await fetch(
          `${apiBaseUrl}/api/v1/inventories?size=100&page=${pageNumber}&sort=productId,asc`,
        );
        if (!response.ok) throw new Error("Inventory request failed");
        const page = (await response.json()) as InventoryPage;
        loaded.push(...page.content);
        totalPages = page.page?.totalPages ?? 1;
        pageNumber += 1;
      } while (pageNumber < totalPages);
      setInventory(loaded);
      setInventoryState("ready");
      return loaded;
    } catch {
      setInventoryState("error");
      return null;
    }
  }, []);

  useEffect(() => {
    void loadInventory();
  }, [loadInventory]);

  async function runScenario(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const selectedProductId = Number(productId);
    const totalRequests = Number(requestCount);
    const unitsPerRequest = Number(quantity);
    if (
      !Number.isInteger(totalRequests) ||
      totalRequests < 1 ||
      totalRequests > 100 ||
      !Number.isInteger(unitsPerRequest) ||
      unitsPerRequest < 1 ||
      unitsPerRequest > 5
    ) {
      setScenarioError("Choose 1–100 requests and a quantity from 1 to 5.");
      setScenarioState("error");
      return;
    }

    setScenarioState("running");
    setScenarioError("");
    setResult(null);
    const inventoryAtStart = await loadInventory();
    const selectedInventory = inventoryAtStart?.find(
      (item) => item.productId === selectedProductId,
    );
    if (!selectedInventory) {
      setScenarioError("The selected Inventory could not be refreshed.");
      setScenarioState("error");
      return;
    }
    const startedAt = performance.now();
    const replayCustomerId = crypto.randomUUID();
    const replayKey = crypto.randomUUID();
    const identities = Array.from({ length: totalRequests }, () =>
      replayIdentity
        ? { customerId: replayCustomerId, idempotencyKey: replayKey }
        : { customerId: crypto.randomUUID(), idempotencyKey: crypto.randomUUID() },
    );

    try {
      const responses = await Promise.all(
        identities.map(async (identity) => {
          const response = await fetch(`${apiBaseUrl}/api/v1/orders`, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              "Idempotency-Key": identity.idempotencyKey,
            },
            body: JSON.stringify({
              customerId: identity.customerId,
              productId: selectedProductId,
              quantity: unitsPerRequest,
            }),
          });
          if (response.status === 201) return "accepted";
          if (response.status === 200) return "replayed";
          if (response.status === 409) {
            const problem = (await response.json()) as { code?: string };
            if (problem.code === "INSUFFICIENT_STOCK") return "insufficient";
          }
          return "failed";
        }),
      );
      const refreshedInventory = await loadInventory();
      const finalQuantity = refreshedInventory?.find(
        (item) => item.productId === selectedProductId,
      )?.availableQuantity;
      if (finalQuantity === undefined) throw new Error("Final Inventory could not be loaded");

      const accepted = responses.filter((response) => response === "accepted").length;
      const replayed = responses.filter((response) => response === "replayed").length;
      const insufficient = responses.filter((response) => response === "insufficient").length;
      const failed = responses.filter((response) => response === "failed").length;
      setResult({
        accepted,
        replayed,
        insufficient,
        failed,
        initialQuantity: selectedInventory.availableQuantity,
        finalQuantity,
        durationMs: performance.now() - startedAt,
        oversold:
          finalQuantity < 0 ||
          selectedInventory.availableQuantity - finalQuantity !== accepted * unitsPerRequest,
      });
      setScenarioState("complete");
    } catch {
      setScenarioError("The scenario could not be completed. Check the local backend and retry.");
      setScenarioState("error");
    }
  }

  return (
    <section className="concurrency-lab" aria-labelledby="concurrency-lab-title">
      <div className="section-heading">
        <p className="eyebrow">POST /api/v1/orders × N</p>
        <h2 id="concurrency-lab-title">Concurrency Lab</h2>
        <p className="section-intro">
          Send simultaneous Order requests and verify the Available Quantity invariant.
        </p>
      </div>

      {inventoryState === "loading" && <p role="status">Loading lab Inventory…</p>}
      {inventoryState === "error" && (
        <div className="concurrency-lab-error" role="alert">
          <p>Lab Inventory could not be loaded.</p>
          <button type="button" onClick={() => void loadInventory()}>
            Retry lab Inventory
          </button>
        </div>
      )}
      {inventoryState === "ready" && inventory.length === 0 && (
        <p className="empty-state">Create Inventory before running a scenario.</p>
      )}
      {inventoryState === "ready" && inventory.length > 0 && (
        <form className="concurrency-form" onSubmit={(event) => void runScenario(event)}>
          <fieldset disabled={scenarioState === "running"}>
            <label htmlFor="concurrency-product">
              Concurrency product
              <select
                id="concurrency-product"
                value={productId}
                onChange={(event) => setProductId(event.target.value)}
                required
              >
                <option value="">Choose Inventory</option>
                {inventory.map((item) => (
                  <option key={item.productId} value={item.productId}>
                    #{item.productId} · {item.productName} · {item.availableQuantity} available
                  </option>
                ))}
              </select>
            </label>
            <label htmlFor="concurrent-requests">
              Concurrent requests
              <input
                id="concurrent-requests"
                type="number"
                min="1"
                max="100"
                value={requestCount}
                onChange={(event) => setRequestCount(event.target.value)}
                required
              />
            </label>
            <label htmlFor="concurrency-quantity">
              Quantity per request
              <input
                id="concurrency-quantity"
                type="number"
                min="1"
                max="5"
                value={quantity}
                onChange={(event) => setQuantity(event.target.value)}
                required
              />
            </label>
            <label className="concurrency-replay" htmlFor="replay-request-identity">
              <input
                id="replay-request-identity"
                type="checkbox"
                checked={replayIdentity}
                onChange={(event) => setReplayIdentity(event.target.checked)}
              />
              Replay the same request identity
            </label>
            <button type="submit" aria-busy={scenarioState === "running"}>
              {scenarioState === "running" ? "Running scenario…" : "Run concurrency scenario"}
            </button>
          </fieldset>
        </form>
      )}

      {scenarioState === "error" && <p className="form-error" role="alert">{scenarioError}</p>}
      {result && (
        <dl className="concurrency-result" aria-live="polite">
          <div><dt>Accepted Orders</dt><dd>{result.accepted} accepted</dd></div>
          <div><dt>Insufficient Inventory</dt><dd>{result.insufficient} insufficient Inventory</dd></div>
          <div><dt>Idempotent replays</dt><dd>{result.replayed} replayed</dd></div>
          <div><dt>Unexpected results</dt><dd>{result.failed} failed</dd></div>
          <div><dt>Final Available Quantity</dt><dd>Final Available Quantity: {result.finalQuantity}</dd></div>
          <div><dt>Duration</dt><dd>{Math.round(result.durationMs)} ms</dd></div>
          <div><dt>Invariant</dt><dd>Oversold: {String(result.oversold)}</dd></div>
        </dl>
      )}
    </section>
  );
}
