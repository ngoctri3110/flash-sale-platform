import { useEffect, useState } from "react";

type HealthState = "checking" | "healthy" | "unavailable";

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

function App() {
  const [health, setHealth] = useState<HealthState>("checking");

  useEffect(() => {
    let isActive = true;

    async function checkBackendHealth() {
      try {
        const response = await fetch(`${apiBaseUrl}/actuator/health`);
        const body = (await response.json()) as { status?: string };

        if (isActive) {
          setHealth(
            response.ok && body.status === "UP" ? "healthy" : "unavailable",
          );
        }
      } catch {
        if (isActive) {
          setHealth("unavailable");
        }
      }
    }

    void checkBackendHealth();

    return () => {
      isActive = false;
    };
  }, []);

  return (
    <main className="app-shell">
      <section className="hero" aria-labelledby="page-title">
        <p className="eyebrow">Java 21 / Spring Boot / React</p>
        <h1 id="page-title">Flash Sale Platform</h1>
        <p className="subtitle">
          A learning platform for safe ordering under high concurrency.
        </p>

        <div className={`health-card health-card--${health}`} aria-live="polite">
          <span className="health-dot" aria-hidden="true" />
          {health === "checking" && "Checking backend..."}
          {health === "healthy" && "Backend is healthy"}
          {health === "unavailable" && "Backend is unavailable"}
        </div>
      </section>

      <section className="module-grid" aria-label="MVP modules">
        <article>
          <span>01</span>
          <h2>Products</h2>
          <p>Browse products prepared for a flash sale.</p>
        </article>
        <article>
          <span>02</span>
          <h2>Inventory</h2>
          <p>Observe limited stock and concurrency-safe updates.</p>
        </article>
        <article>
          <span>03</span>
          <h2>Orders</h2>
          <p>Place and inspect orders without overselling.</p>
        </article>
      </section>
    </main>
  );
}

export default App;
