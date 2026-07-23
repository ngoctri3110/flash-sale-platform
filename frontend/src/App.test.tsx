import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import App from "./App";

describe("App", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("lets a customer browse products and open a product detail", async () => {
    const product = {
      id: 1,
      name: "Mechanical Keyboard",
      description: "A compact keyboard built for long coding sessions.",
      price: 2490000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    const fetchMock = vi.fn().mockImplementation((input: string | URL | Request) => {
      const url = input.toString();
      if (url.endsWith("/actuator/health")) {
        return Promise.resolve(jsonResponse({ status: "UP" }));
      }
      if (url.endsWith("/api/v1/products/1")) {
        return Promise.resolve(jsonResponse(product));
      }
      return Promise.resolve(
        jsonResponse({
          content: [product],
          page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
        }),
      );
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    const productButton = await screen.findByRole("button", {
      name: /Mechanical Keyboard/,
    });
    fireEvent.click(productButton);

    expect(
      await screen.findByRole("heading", { name: "Mechanical Keyboard" }),
    ).toBeInTheDocument();
    expect(productButton).toHaveAttribute("data-state", "success");
    expect(productButton).toHaveAttribute("aria-pressed", "true");
    expect(screen.getAllByText("₫2,490,000")).toHaveLength(2);
    expect(screen.getByText("Available for ordering")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/products/1");
  });

  it("lets a customer place an Order from the selected Product", async () => {
    const product = {
      id: 1,
      name: "Mechanical Keyboard",
      description: "A compact keyboard built for long coding sessions.",
      price: 2490000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    const acceptedOrder = {
      id: 41,
      customerId: "d97f2e84-3d38-4b2e-9828-56e641c98b88",
      productId: 1,
      productName: "Mechanical Keyboard",
      quantity: 2,
      unitPrice: 2490000,
      currency: "VND",
      totalAmount: 4980000,
      createdAt: "2026-07-23T01:00:00Z",
    };
    const fetchMock = vi.fn().mockImplementation(
      (input: string | URL | Request, init?: RequestInit) => {
        const url = input.toString();
        if (url.endsWith("/actuator/health")) {
          return Promise.resolve(jsonResponse({ status: "UP" }));
        }
        if (url.endsWith("/api/v1/orders") && init?.method === "POST") {
          return Promise.resolve(jsonResponse(acceptedOrder, 201));
        }
        if (url.endsWith("/api/v1/products/1")) {
          return Promise.resolve(jsonResponse(product));
        }
        return Promise.resolve(
          jsonResponse({
            content: [product],
            page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
          }),
        );
      },
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    fireEvent.click(
      await screen.findByRole("button", { name: /Mechanical Keyboard/ }),
    );
    const customerId = (await screen.findByLabelText("Customer ID") as HTMLInputElement)
      .value;
    const idempotencyKey = (screen.getByLabelText(
      "Idempotency key",
    ) as HTMLInputElement).value;
    fireEvent.change(screen.getByLabelText("Quantity"), {
      target: { value: "2" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Place order" }));

    expect(await screen.findByText("Order #41 accepted")).toHaveAttribute(
      "role",
      "status",
    );
    expect(screen.getByText("2 × ₫2,490,000")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/orders",
      expect.objectContaining({
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey,
        },
        body: JSON.stringify({ customerId, productId: 1, quantity: 2 }),
      }),
    );
  });

  it("runs a concurrent Order scenario and reports that Inventory was not oversold", async () => {
    const product = {
      id: 1,
      name: "Mechanical Keyboard",
      description: "A compact keyboard built for long coding sessions.",
      price: 2490000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    let orderRequestCount = 0;
    const fetchMock = vi.fn().mockImplementation(
      (input: string | URL | Request, init?: RequestInit) => {
        const url = input.toString();
        if (url.endsWith("/actuator/health")) {
          return Promise.resolve(jsonResponse({ status: "UP" }));
        }
        if (url.includes("/api/v1/orders") && init?.method === "POST") {
          orderRequestCount += 1;
          return Promise.resolve(
            orderRequestCount === 1
              ? jsonResponse({ id: 1 }, 201)
              : jsonResponse({ code: "INSUFFICIENT_STOCK" }, 409),
          );
        }
        if (url.includes("/api/v1/inventories")) {
          return Promise.resolve(
            jsonResponse({
              content: [
                {
                  productId: 1,
                  productName: product.name,
                  availableQuantity: orderRequestCount === 0 ? 1 : 0,
                  updatedAt: "2026-07-23T00:00:00Z",
                },
              ],
              page: { number: 0, size: 100, totalElements: 1, totalPages: 1 },
            }),
          );
        }
        return Promise.resolve(
          jsonResponse({
            content: [product],
            page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
          }),
        );
      },
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    await screen.findByRole("button", { name: /Mechanical Keyboard/ });
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));
    fireEvent.change(
      await screen.findByRole("combobox", { name: "Concurrency product" }),
      { target: { value: "1" } },
    );
    fireEvent.change(screen.getByLabelText("Concurrent requests"), {
      target: { value: "2" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Run concurrency scenario" }));

    expect(await screen.findByText("1 accepted")).toBeInTheDocument();
    expect(screen.getByText("1 insufficient Inventory")).toBeInTheDocument();
    expect(screen.getByText("Final Available Quantity: 0")).toBeInTheDocument();
    expect(screen.getByText("Oversold: false")).toBeInTheDocument();

    const requests = fetchMock.mock.calls.filter(
      ([input, init]) => input.toString().includes("/api/v1/orders") && init?.method === "POST",
    );
    expect(requests).toHaveLength(2);
    const customerIds = requests.map(([, init]) =>
      JSON.parse((init as RequestInit).body as string).customerId,
    );
    const keys = requests.map(
      ([, init]) => (init as RequestInit).headers as Record<string, string>,
    );
    expect(new Set(customerIds).size).toBe(2);
    expect(new Set(keys.map((headers) => headers["Idempotency-Key"])).size).toBe(2);
  });

  it("replays one request identity without another Inventory deduction", async () => {
    const product = {
      id: 1,
      name: "Mechanical Keyboard",
      description: "A compact keyboard built for long coding sessions.",
      price: 2490000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    let orderRequestCount = 0;
    const fetchMock = vi.fn().mockImplementation(
      (input: string | URL | Request, init?: RequestInit) => {
        const url = input.toString();
        if (url.endsWith("/actuator/health")) {
          return Promise.resolve(jsonResponse({ status: "UP" }));
        }
        if (url.includes("/api/v1/orders") && init?.method === "POST") {
          orderRequestCount += 1;
          return Promise.resolve(jsonResponse({ id: 1 }, orderRequestCount === 1 ? 201 : 200));
        }
        if (url.includes("/api/v1/inventories")) {
          return Promise.resolve(
            jsonResponse({
              content: [
                {
                  productId: 1,
                  productName: product.name,
                  availableQuantity: orderRequestCount === 0 ? 3 : 2,
                  updatedAt: "2026-07-23T00:00:00Z",
                },
              ],
              page: { number: 0, size: 100, totalElements: 1, totalPages: 1 },
            }),
          );
        }
        return Promise.resolve(
          jsonResponse({
            content: [product],
            page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
          }),
        );
      },
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    await screen.findByRole("button", { name: /Mechanical Keyboard/ });
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));
    fireEvent.change(
      await screen.findByRole("combobox", { name: "Concurrency product" }),
      { target: { value: "1" } },
    );
    fireEvent.change(screen.getByLabelText("Concurrent requests"), {
      target: { value: "3" },
    });
    fireEvent.click(screen.getByLabelText("Replay the same request identity"));
    fireEvent.click(screen.getByRole("button", { name: "Run concurrency scenario" }));

    expect(await screen.findByText("1 accepted")).toBeInTheDocument();
    expect(screen.getByText("2 replayed")).toBeInTheDocument();
    expect(screen.getByText("Final Available Quantity: 2")).toBeInTheDocument();
    expect(screen.getByText("Oversold: false")).toBeInTheDocument();

    const requests = fetchMock.mock.calls.filter(
      ([input, init]) => input.toString().includes("/api/v1/orders") && init?.method === "POST",
    );
    expect(new Set(requests.map(([, init]) =>
      JSON.parse((init as RequestInit).body as string).customerId,
    )).size).toBe(1);
    expect(new Set(requests.map(([, init]) =>
      ((init as RequestInit).headers as Record<string, string>)["Idempotency-Key"],
    )).size).toBe(1);
  });

  it("uses an Inventory snapshot taken immediately before the concurrency scenario", async () => {
    const product = {
      id: 1,
      name: "Mechanical Keyboard",
      description: "A compact keyboard built for long coding sessions.",
      price: 2490000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    let ascendingInventoryRequests = 0;
    const fetchMock = vi.fn().mockImplementation(
      (input: string | URL | Request, init?: RequestInit) => {
        const url = input.toString();
        if (url.endsWith("/actuator/health")) {
          return Promise.resolve(jsonResponse({ status: "UP" }));
        }
        if (url.includes("/api/v1/orders") && init?.method === "POST") {
          return Promise.resolve(jsonResponse({ id: 1 }, 201));
        }
        if (url.includes("/api/v1/inventories")) {
          const isLabRequest = url.includes("sort=productId,asc");
          if (isLabRequest) ascendingInventoryRequests += 1;
          const availableQuantity = isLabRequest
            ? [3, 2, 1][ascendingInventoryRequests - 1]
            : 1;
          return Promise.resolve(
            jsonResponse({
              content: [
                {
                  productId: 1,
                  productName: product.name,
                  availableQuantity,
                  updatedAt: "2026-07-23T00:00:00Z",
                },
              ],
              page: { number: 0, size: 100, totalElements: 1, totalPages: 1 },
            }),
          );
        }
        return Promise.resolve(
          jsonResponse({
            content: [product],
            page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
          }),
        );
      },
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    await screen.findByRole("button", { name: /Mechanical Keyboard/ });
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));
    fireEvent.change(
      await screen.findByRole("combobox", { name: "Concurrency product" }),
      { target: { value: "1" } },
    );
    fireEvent.change(screen.getByLabelText("Concurrent requests"), {
      target: { value: "1" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Run concurrency scenario" }));

    expect(await screen.findByText("Final Available Quantity: 1")).toBeInTheDocument();
    expect(screen.getByText("Oversold: false")).toBeInTheDocument();
  });

  it("lets a customer intentionally replay the same Order request", async () => {
    const product = {
      id: 1,
      name: "Mechanical Keyboard",
      description: "A compact keyboard built for long coding sessions.",
      price: 2490000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    const acceptedOrder = {
      id: 41,
      customerId: "d97f2e84-3d38-4b2e-9828-56e641c98b88",
      productId: 1,
      productName: "Mechanical Keyboard",
      quantity: 1,
      unitPrice: 2490000,
      currency: "VND",
      totalAmount: 2490000,
      createdAt: "2026-07-23T01:00:00Z",
    };
    let orderRequests = 0;
    const fetchMock = vi.fn().mockImplementation(
      (input: string | URL | Request, init?: RequestInit) => {
        const url = input.toString();
        if (url.endsWith("/actuator/health")) {
          return Promise.resolve(jsonResponse({ status: "UP" }));
        }
        if (url.endsWith("/api/v1/orders") && init?.method === "POST") {
          orderRequests += 1;
          return Promise.resolve(
            jsonResponse(acceptedOrder, orderRequests === 1 ? 201 : 200),
          );
        }
        if (url.endsWith("/api/v1/products/1")) {
          return Promise.resolve(jsonResponse(product));
        }
        return Promise.resolve(
          jsonResponse({
            content: [product],
            page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
          }),
        );
      },
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    fireEvent.click(
      await screen.findByRole("button", { name: /Mechanical Keyboard/ }),
    );
    fireEvent.click(
      await screen.findByRole("button", { name: "Place order" }),
    );
    fireEvent.click(
      await screen.findByRole("button", { name: "Replay same request" }),
    );

    expect(await screen.findByText("Order #41 replayed")).toBeInTheDocument();
    const submittedOrders = fetchMock.mock.calls.filter(([input, init]) => {
      return (
        input.toString().endsWith("/api/v1/orders") && init?.method === "POST"
      );
    });
    expect(submittedOrders).toHaveLength(2);
    expect(submittedOrders[1]?.[1]).toEqual(submittedOrders[0]?.[1]);
  });

  it("starts a new idempotent request when an accepted Order payload changes", async () => {
    const product = {
      id: 1,
      name: "Mechanical Keyboard",
      description: "A compact keyboard built for long coding sessions.",
      price: 2490000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    let orderRequests = 0;
    const fetchMock = vi.fn().mockImplementation(
      (input: string | URL | Request, init?: RequestInit) => {
        const url = input.toString();
        if (url.endsWith("/actuator/health")) {
          return Promise.resolve(jsonResponse({ status: "UP" }));
        }
        if (url.endsWith("/api/v1/orders") && init?.method === "POST") {
          orderRequests += 1;
          return Promise.resolve(
            jsonResponse(
              {
                id: 40 + orderRequests,
                customerId: "d97f2e84-3d38-4b2e-9828-56e641c98b88",
                productId: 1,
                productName: "Mechanical Keyboard",
                quantity: orderRequests,
                unitPrice: 2490000,
                currency: "VND",
                totalAmount: 2490000 * orderRequests,
                createdAt: "2026-07-23T01:00:00Z",
              },
              201,
            ),
          );
        }
        if (url.endsWith("/api/v1/products/1")) {
          return Promise.resolve(jsonResponse(product));
        }
        return Promise.resolve(
          jsonResponse({
            content: [product],
            page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
          }),
        );
      },
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    fireEvent.click(
      await screen.findByRole("button", { name: /Mechanical Keyboard/ }),
    );
    const keyInput = await screen.findByLabelText("Idempotency key");
    const firstKey = (keyInput as HTMLInputElement).value;
    fireEvent.click(screen.getByRole("button", { name: "Place order" }));
    await screen.findByText("Order #41 accepted");

    fireEvent.change(screen.getByLabelText("Quantity"), {
      target: { value: "2" },
    });
    const secondKey = (keyInput as HTMLInputElement).value;
    fireEvent.click(screen.getByRole("button", { name: "Place order" }));

    expect(await screen.findByText("Order #42 accepted")).toBeInTheDocument();
    expect(secondKey).not.toBe(firstKey);
    const submittedOrders = fetchMock.mock.calls.filter(([input, init]) => {
      return (
        input.toString().endsWith("/api/v1/orders") && init?.method === "POST"
      );
    });
    expect(submittedOrders[1]?.[1]).toEqual(
      expect.objectContaining({
        headers: expect.objectContaining({ "Idempotency-Key": secondKey }),
        body: expect.stringContaining('"quantity":2'),
      }),
    );
  });

  it("shows the RFC 9457 reason when an Order is rejected", async () => {
    const product = {
      id: 1,
      name: "Mechanical Keyboard",
      description: "A compact keyboard built for long coding sessions.",
      price: 2490000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(
        (input: string | URL | Request, init?: RequestInit) => {
          const url = input.toString();
          if (url.endsWith("/actuator/health")) {
            return Promise.resolve(jsonResponse({ status: "UP" }));
          }
          if (url.endsWith("/api/v1/orders") && init?.method === "POST") {
            return Promise.resolve(
              jsonResponse(
                {
                  code: "INSUFFICIENT_STOCK",
                  detail: "Product 1 has insufficient inventory for quantity 5",
                },
                409,
              ),
            );
          }
          if (url.endsWith("/api/v1/products/1")) {
            return Promise.resolve(jsonResponse(product));
          }
          return Promise.resolve(
            jsonResponse({
              content: [product],
              page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
            }),
          );
        },
      ),
    );

    render(<App />);
    fireEvent.click(
      await screen.findByRole("button", { name: /Mechanical Keyboard/ }),
    );
    fireEvent.change(await screen.findByLabelText("Quantity"), {
      target: { value: "5" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Place order" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("INSUFFICIENT_STOCK");
    expect(alert).toHaveTextContent(
      "Product 1 has insufficient inventory for quantity 5",
    );
    expect(screen.getByLabelText("Quantity")).toHaveValue(5);
  });

  it("shows a loading state while the product catalogue is pending", () => {
    const fetchMock = vi.fn().mockImplementation((input: string | URL | Request) => {
      const url = input.toString();
      if (url.endsWith("/actuator/health")) {
        return Promise.resolve(jsonResponse({ status: "UP" }));
      }
      return new Promise<Response>(() => undefined);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    expect(screen.getByRole("status", { name: "" })).toHaveTextContent(
      "Loading products…",
    );
  });

  it("shows which product detail is loading", async () => {
    const product = {
      id: 1,
      name: "Mechanical Keyboard",
      description: "A compact keyboard.",
      price: 2490000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((input: string | URL | Request) => {
        const url = input.toString();
        if (url.endsWith("/actuator/health")) {
          return Promise.resolve(jsonResponse({ status: "UP" }));
        }
        if (url.endsWith("/api/v1/products/1")) {
          return new Promise<Response>(() => undefined);
        }
        return Promise.resolve(
          jsonResponse({
            content: [product],
            page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
          }),
        );
      }),
    );

    render(<App />);
    const productButton = await screen.findByRole("button", {
      name: /Mechanical Keyboard/,
    });
    fireEvent.click(productButton);

    expect(screen.getByText("Loading product details…")).toBeInTheDocument();
    expect(productButton).toHaveAttribute("aria-busy", "true");
    expect(productButton).toHaveAttribute("data-state", "loading");
  });

  it("explains when no active products are available", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((input: string | URL | Request) => {
        const url = input.toString();
        return Promise.resolve(
          url.endsWith("/actuator/health")
            ? jsonResponse({ status: "UP" })
            : jsonResponse({
                content: [],
                page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
              }),
        );
      }),
    );

    render(<App />);

    expect(
      await screen.findByText("No products are available for this sale yet."),
    ).toBeInTheDocument();
  });

  it("shows a recoverable error when the product list request fails", async () => {
    let productRequests = 0;
    const fetchMock = vi.fn().mockImplementation((input: string | URL | Request) => {
      const url = input.toString();
      if (url.endsWith("/actuator/health")) {
        return Promise.resolve(jsonResponse({ status: "UP" }));
      }
      productRequests += 1;
      return Promise.resolve(
        productRequests === 1
          ? jsonResponse({ code: "INTERNAL_ERROR" }, 503)
          : jsonResponse({
              content: [],
              page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
            }),
      );
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    expect(
      await screen.findByRole("alert", { name: "Products unavailable" }),
    ).toHaveTextContent("The catalogue could not be loaded.");

    fireEvent.click(screen.getByRole("button", { name: "Retry products" }));

    expect(
      await screen.findByText("No products are available for this sale yet."),
    ).toBeInTheDocument();
    expect(productRequests).toBe(2);
  });

  it("keeps the catalogue usable when product details fail", async () => {
    const product = {
      id: 1,
      name: "Mechanical Keyboard",
      description: "A compact keyboard built for long coding sessions.",
      price: 2490000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((input: string | URL | Request) => {
        const url = input.toString();
        if (url.endsWith("/actuator/health")) {
          return Promise.resolve(jsonResponse({ status: "UP" }));
        }
        if (url.endsWith("/api/v1/products/1")) {
          return Promise.resolve(jsonResponse({ code: "PRODUCT_NOT_FOUND" }, 404));
        }
        return Promise.resolve(
          jsonResponse({
            content: [product],
            page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
          }),
        );
      }),
    );

    render(<App />);
    fireEvent.click(
      await screen.findByRole("button", { name: /Mechanical Keyboard/ }),
    );

    expect(
      await screen.findByRole("alert", { name: "Product details unavailable" }),
    ).toHaveTextContent("Product details could not be loaded.");
    const productButton = screen.getByRole("button", {
      name: /Mechanical Keyboard/,
    });
    expect(productButton).toBeEnabled();
    expect(productButton).toHaveAttribute("data-state", "error");
  });

  it("keeps the latest product when detail requests resolve out of order", async () => {
    const products = [
      {
        id: 1,
        name: "Mechanical Keyboard",
        description: "First product.",
        price: 2490000,
        currency: "VND",
        active: true,
        createdAt: "2026-07-23T00:00:00Z",
        updatedAt: "2026-07-23T00:00:00Z",
      },
      {
        id: 2,
        name: "Portable SSD",
        description: "Second product.",
        price: 2190000,
        currency: "VND",
        active: true,
        createdAt: "2026-07-23T00:00:00Z",
        updatedAt: "2026-07-23T00:00:00Z",
      },
    ];
    let resolveFirst!: (response: Response) => void;
    let resolveSecond!: (response: Response) => void;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((input: string | URL | Request) => {
        const url = input.toString();
        if (url.endsWith("/actuator/health")) {
          return Promise.resolve(jsonResponse({ status: "UP" }));
        }
        if (url.endsWith("/api/v1/products/1")) {
          return new Promise<Response>((resolve) => {
            resolveFirst = resolve;
          });
        }
        if (url.endsWith("/api/v1/products/2")) {
          return new Promise<Response>((resolve) => {
            resolveSecond = resolve;
          });
        }
        return Promise.resolve(
          jsonResponse({
            content: products,
            page: { number: 0, size: 20, totalElements: 2, totalPages: 1 },
          }),
        );
      }),
    );

    render(<App />);
    fireEvent.click(await screen.findByRole("button", { name: /Mechanical Keyboard/ }));
    fireEvent.click(screen.getByRole("button", { name: /Portable SSD/ }));

    resolveSecond(jsonResponse(products[1]));
    expect(
      await screen.findByRole("heading", { name: "Portable SSD" }),
    ).toBeInTheDocument();

    resolveFirst(jsonResponse(products[0]));
    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Portable SSD" }),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole("heading", { name: "Mechanical Keyboard" }),
      ).not.toBeInTheDocument();
    });
  });

  it("opens and filters the product finder from the keyboard", async () => {
    const products = [
      {
        id: 1,
        name: "Mechanical Keyboard",
        description: "A compact keyboard.",
        price: 2490000,
        currency: "VND",
        active: true,
        createdAt: "2026-07-23T00:00:00Z",
        updatedAt: "2026-07-23T00:00:00Z",
      },
      {
        id: 2,
        name: "Portable SSD",
        description: "Fast storage.",
        price: 2190000,
        currency: "VND",
        active: true,
        createdAt: "2026-07-23T00:00:00Z",
        updatedAt: "2026-07-23T00:00:00Z",
      },
    ];
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((input: string | URL | Request) =>
        Promise.resolve(
          input.toString().endsWith("/actuator/health")
            ? jsonResponse({ status: "UP" })
            : jsonResponse({
                content: products,
                page: { number: 0, size: 20, totalElements: 2, totalPages: 1 },
              }),
        ),
      ),
    );

    render(<App />);
    await screen.findByRole("button", { name: /Mechanical Keyboard/ });
    fireEvent.keyDown(document, { key: "k", ctrlKey: true });

    const dialog = screen.getByRole("dialog", { name: "Product finder" });
    const search = screen.getByRole("searchbox", { name: "Filter products" });
    fireEvent.change(search, { target: { value: "keyboard" } });

    expect(dialog).toHaveTextContent("Mechanical Keyboard");
    expect(dialog).not.toHaveTextContent("Portable SSD");
    expect(search).toHaveAttribute("data-state", "success");

    fireEvent.change(search, { target: { value: "missing" } });
    expect(search).toHaveAttribute("aria-invalid", "true");
    expect(search).toHaveAttribute("data-state", "error");
    expect(dialog).toHaveTextContent("No matching products.");

    fireEvent.keyDown(document, { key: "Escape" });
    expect(
      screen.queryByRole("dialog", { name: "Product finder" }),
    ).not.toBeInTheDocument();
  });

  it("lets an administrator filter and paginate accepted Orders", async () => {
    const customerId = "c440eb9d-71a8-4435-8648-45b5696f9ec6";
    const order = (id: number) => ({
      id,
      customerId,
      productId: 2,
      productName: "Noise-Cancelling Headphones",
      quantity: 2,
      unitPrice: 8990000,
      currency: "VND",
      totalAmount: 17980000,
      createdAt: "2026-07-23T02:00:00Z",
    });
    const fetchMock = vi.fn().mockImplementation((input: string | URL | Request) => {
      const url = input.toString();
      if (url.endsWith("/actuator/health")) {
        return Promise.resolve(jsonResponse({ status: "UP" }));
      }
      if (url.includes("/api/v1/orders")) {
        const pageNumber = new URL(url, "http://local").searchParams.get("page");
        return Promise.resolve(
          jsonResponse({
            content: [order(pageNumber === "1" ? 90 : 91)],
            page: {
              number: Number(pageNumber ?? 0),
              size: 20,
              totalElements: 2,
              totalPages: 2,
            },
          }),
        );
      }
      if (url.includes("/api/v1/inventories")) {
        return Promise.resolve(
          jsonResponse({
            content: [],
            page: { number: 0, size: 100, totalElements: 0, totalPages: 0 },
          }),
        );
      }
      return Promise.resolve(
        jsonResponse({
          content: [],
          page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
        }),
      );
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    await screen.findByText("No products are available for this sale yet.");
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));

    expect(await screen.findByText("Order #91")).toBeInTheDocument();
    expect(screen.getByText("Noise-Cancelling Headphones")).toBeInTheDocument();
    expect(screen.getByText("2 × ₫8,990,000")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Order product ID"), {
      target: { value: "2" },
    });
    fireEvent.change(screen.getByLabelText("Order customer ID"), {
      target: { value: customerId },
    });
    fireEvent.change(screen.getByLabelText("Orders created from"), {
      target: { value: "2026-07-23T01:00" },
    });
    fireEvent.change(screen.getByLabelText("Orders created before"), {
      target: { value: "2026-07-24T00:00" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Apply order filters" }));

    await waitFor(() => {
      const orderCalls = fetchMock.mock.calls.filter(([input]) =>
        input.toString().includes("/api/v1/orders"),
      );
      const requestUrl = new URL(orderCalls.at(-1)?.[0].toString() ?? "", "http://local");
      expect(requestUrl.searchParams.get("productId")).toBe("2");
      expect(requestUrl.searchParams.get("customerId")).toBe(customerId);
      expect(requestUrl.searchParams.get("createdFrom")).toBeTruthy();
      expect(requestUrl.searchParams.get("createdTo")).toBeTruthy();
    });

    fireEvent.click(screen.getByRole("button", { name: "Next orders page" }));
    expect(await screen.findByText("Order #90")).toBeInTheDocument();
    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
  });

  it("maps Order filter validation details back to the affected controls", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((input: string | URL | Request) => {
        const url = input.toString();
        if (url.endsWith("/actuator/health")) {
          return Promise.resolve(jsonResponse({ status: "UP" }));
        }
        if (url.includes("/api/v1/orders")) {
          return Promise.resolve(
            jsonResponse(
              {
                code: "VALIDATION_FAILED",
                detail: "One or more request parameters are invalid",
                fieldErrors: [
                  { field: "customerId", message: "must be a valid UUID" },
                  {
                    field: "createdTo",
                    message: "must be later than createdFrom",
                  },
                ],
              },
              400,
            ),
          );
        }
        return Promise.resolve(
          jsonResponse({
            content: [],
            page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
          }),
        );
      }),
    );

    render(<App />);
    await screen.findByText("No products are available for this sale yet.");
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));

    const orderAlert = await screen.findByRole("alert");
    expect(orderAlert).toHaveTextContent("VALIDATION_FAILED");
    expect(orderAlert).toHaveTextContent(
      "One or more request parameters are invalid",
    );
    expect(screen.getByLabelText("Order customer ID")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.getByText("must be a valid UUID")).toBeInTheDocument();
    expect(screen.getByLabelText("Orders created before")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.getByText("must be later than createdFrom")).toBeInTheDocument();
  });

  it("lets an administrator create a product and then see it in the Shop", async () => {
    const createdProduct = {
      id: 9,
      name: "Standing Desk",
      description: "A height-adjustable desk.",
      price: 15990000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    let productCreated = false;
    const fetchMock = vi.fn().mockImplementation(
      (input: string | URL | Request, init?: RequestInit) => {
        const url = input.toString();
        if (url.endsWith("/actuator/health")) {
          return Promise.resolve(jsonResponse({ status: "UP" }));
        }
        if (url.endsWith("/api/v1/products") && init?.method === "POST") {
          productCreated = true;
          return Promise.resolve(jsonResponse(createdProduct, 201));
        }
        if (url.includes("/api/v1/inventories")) {
          return Promise.resolve(
            jsonResponse({
              content: productCreated
                ? [
                    {
                      productId: 9,
                      productName: "Standing Desk",
                      availableQuantity: 25,
                      updatedAt: "2026-07-23T00:00:00Z",
                    },
                  ]
                : [],
              page: {
                number: 0,
                size: 100,
                totalElements: productCreated ? 101 : 0,
                totalPages: productCreated ? 2 : 0,
              },
            }),
          );
        }
        return Promise.resolve(
          jsonResponse({
            content: [],
            page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
          }),
        );
      },
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    await screen.findByText("No products are available for this sale yet.");
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));

    fireEvent.change(screen.getByLabelText("Product name"), {
      target: { value: "Standing Desk" },
    });
    fireEvent.change(screen.getByLabelText("Description"), {
      target: { value: "A height-adjustable desk." },
    });
    fireEvent.change(screen.getByLabelText("Price (VND)"), {
      target: { value: "15990000" },
    });
    fireEvent.change(screen.getByLabelText("Initial inventory"), {
      target: { value: "25" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create product" }));

    expect(await screen.findByText(/Standing Desk committed/)).toBeInTheDocument();
    expect(await screen.findByText("25 available")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/inventories?size=100&page=0&sort=productId,desc",
    );
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/products",
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: "Standing Desk",
          description: "A height-adjustable desk.",
          price: 15990000,
          active: true,
          initialInventory: 25,
        }),
      }),
    );

    fireEvent.click(screen.getByRole("button", { name: "Shop" }));
    expect(
      screen.getByRole("button", { name: /Standing Desk/ }),
    ).toBeInTheDocument();
  });

  it("lets an administrator edit mutable product fields and reflects them in the Shop", async () => {
    const product = {
      id: 4,
      name: "Smart Desk Lamp",
      description: "An adjustable desk lamp.",
      price: 1290000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    const updatedProduct = {
      ...product,
      name: "Focus Desk Lamp",
      active: false,
      updatedAt: "2026-07-23T01:00:00Z",
    };
    const fetchMock = vi.fn().mockImplementation(
      (input: string | URL | Request, init?: RequestInit) => {
        const url = input.toString();
        if (url.endsWith("/actuator/health")) {
          return Promise.resolve(jsonResponse({ status: "UP" }));
        }
        if (url.endsWith("/api/v1/products/4") && init?.method === "PATCH") {
          return Promise.resolve(jsonResponse(updatedProduct));
        }
        if (url.includes("/api/v1/inventories")) {
          return Promise.resolve(jsonResponse({ content: [] }));
        }
        return Promise.resolve(
          jsonResponse({
            content: [product],
            page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
          }),
        );
      },
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    await screen.findByRole("button", { name: /Smart Desk Lamp/ });
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));

    fireEvent.change(screen.getByRole("combobox", { name: "Edit product" }), {
      target: { value: "4" },
    });
    fireEvent.change(screen.getByLabelText("Edit product name"), {
      target: { value: "Focus Desk Lamp" },
    });
    fireEvent.click(screen.getByLabelText("Edit active for sale"));
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText(/Focus Desk Lamp updated/)).toHaveAttribute(
      "role",
      "status",
    );
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/products/4",
      expect.objectContaining({
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: "Focus Desk Lamp", active: false }),
      }),
    );

    fireEvent.click(screen.getByRole("button", { name: "Shop" }));
    expect(
      screen.getByRole("button", { name: /Focus Desk Lamp.*Currently unavailable/ }),
    ).toBeInTheDocument();
  });

  it("maps product update problem details back to the edit form", async () => {
    const product = {
      id: 1,
      name: "Mechanical Keyboard",
      description: null,
      price: 2490000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(
        (input: string | URL | Request, init?: RequestInit) => {
          const url = input.toString();
          if (url.endsWith("/actuator/health")) {
            return Promise.resolve(jsonResponse({ status: "UP" }));
          }
          if (url.endsWith("/api/v1/products/1") && init?.method === "PATCH") {
            return Promise.resolve(
              jsonResponse(
                {
                  detail: "One or more request parameters are invalid",
                  fieldErrors: [{ field: "name", message: "must not be blank" }],
                },
                400,
              ),
            );
          }
          if (url.includes("/api/v1/inventories")) {
            return Promise.resolve(jsonResponse({ content: [] }));
          }
          return Promise.resolve(
            jsonResponse({
              content: [product],
              page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
            }),
          );
        },
      ),
    );

    render(<App />);
    await screen.findByRole("button", { name: /Mechanical Keyboard/ });
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));
    fireEvent.change(screen.getByRole("combobox", { name: "Edit product" }), {
      target: { value: "1" },
    });
    fireEvent.change(screen.getByLabelText("Edit product name"), {
      target: { value: " " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "One or more request parameters are invalid",
    );
    expect(screen.getByLabelText("Edit product name")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.getByText("must not be blank")).toBeInTheDocument();
  });

  it("does not let an older detail response overwrite an accepted Admin update", async () => {
    const product = {
      id: 2,
      name: "Portable SSD",
      description: "Fast storage.",
      price: 2190000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    const updatedProduct = {
      ...product,
      name: "Portable SSD Pro",
      updatedAt: "2026-07-23T01:00:00Z",
    };
    let resolveOldDetail!: (response: Response) => void;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(
        (input: string | URL | Request, init?: RequestInit) => {
          const url = input.toString();
          if (url.endsWith("/actuator/health")) {
            return Promise.resolve(jsonResponse({ status: "UP" }));
          }
          if (url.endsWith("/api/v1/products/2") && init?.method === "PATCH") {
            return Promise.resolve(jsonResponse(updatedProduct));
          }
          if (url.endsWith("/api/v1/products/2")) {
            return new Promise<Response>((resolve) => {
              resolveOldDetail = resolve;
            });
          }
          if (url.includes("/api/v1/inventories")) {
            return Promise.resolve(jsonResponse({ content: [] }));
          }
          return Promise.resolve(
            jsonResponse({
              content: [product],
              page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
            }),
          );
        },
      ),
    );

    render(<App />);
    fireEvent.click(await screen.findByRole("button", { name: /Portable SSD/ }));
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));
    fireEvent.change(screen.getByRole("combobox", { name: "Edit product" }), {
      target: { value: "2" },
    });
    fireEvent.change(screen.getByLabelText("Edit product name"), {
      target: { value: "Portable SSD Pro" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await screen.findByText(/Portable SSD Pro updated/);
    fireEvent.click(screen.getByRole("button", { name: "Shop" }));

    expect(
      screen.getByRole("heading", { name: "Portable SSD Pro" }),
    ).toBeInTheDocument();
    await act(async () => {
      resolveOldDetail(jsonResponse(product));
    });
    expect(
      screen.getByRole("heading", { name: "Portable SSD Pro" }),
    ).toBeInTheDocument();
  });

  it("loads every Admin product page and locks selection during an update", async () => {
    const firstProduct = {
      id: 1,
      name: "First Product",
      description: null,
      price: 1000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    const laterProduct = {
      ...firstProduct,
      id: 101,
      name: "Later Product",
      active: false,
    };
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(
        (input: string | URL | Request, init?: RequestInit) => {
          const url = input.toString();
          if (url.endsWith("/actuator/health")) {
            return Promise.resolve(jsonResponse({ status: "UP" }));
          }
          if (url.endsWith("/api/v1/products/101") && init?.method === "PATCH") {
            return new Promise<Response>(() => undefined);
          }
          if (url.includes("/api/v1/inventories")) {
            return Promise.resolve(jsonResponse({ content: [] }));
          }
          if (url.includes("/api/v1/products?size=100&page=1")) {
            return Promise.resolve(
              jsonResponse({
                content: [laterProduct],
                page: { number: 1, size: 100, totalElements: 101, totalPages: 2 },
              }),
            );
          }
          if (url.includes("/api/v1/products?size=100&page=0")) {
            return Promise.resolve(
              jsonResponse({
                content: [firstProduct],
                page: { number: 0, size: 100, totalElements: 101, totalPages: 2 },
              }),
            );
          }
          return Promise.resolve(
            jsonResponse({
              content: [firstProduct],
              page: { number: 0, size: 20, totalElements: 101, totalPages: 6 },
            }),
          );
        },
      ),
    );

    render(<App />);
    await screen.findByRole("button", { name: /First Product/ });
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));
    const selector = screen.getByRole("combobox", { name: "Edit product" });
    await screen.findByRole("option", { name: /Later Product/ });
    fireEvent.change(selector, { target: { value: "101" } });
    fireEvent.change(screen.getByLabelText("Edit product name"), {
      target: { value: "Later Product Revised" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(selector).toBeDisabled();
  });

  it("lets an administrator adjust Inventory and shows the committed quantity", async () => {
    const inventory = {
      productId: 1,
      productName: "Mechanical Keyboard",
      availableQuantity: 18,
      updatedAt: "2026-07-23T00:00:00Z",
    };
    const adjusted = {
      ...inventory,
      availableQuantity: 25,
      updatedAt: "2026-07-23T01:00:00Z",
    };
    const fetchMock = vi.fn().mockImplementation(
      (input: string | URL | Request, init?: RequestInit) => {
        const url = input.toString();
        if (url.endsWith("/actuator/health")) {
          return Promise.resolve(jsonResponse({ status: "UP" }));
        }
        if (
          url.endsWith("/api/v1/inventories/1/adjustments") &&
          init?.method === "POST"
        ) {
          return Promise.resolve(jsonResponse(adjusted));
        }
        if (url.includes("/api/v1/inventories")) {
          return Promise.resolve(jsonResponse({ content: [inventory] }));
        }
        return Promise.resolve(
          jsonResponse({
            content: [],
            page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
          }),
        );
      },
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    await screen.findByText("No products are available for this sale yet.");
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));
    fireEvent.change(
      await screen.findByRole("combobox", { name: "Inventory to adjust" }),
      { target: { value: "1" } },
    );
    fireEvent.change(screen.getByLabelText("Quantity delta"), {
      target: { value: "7" },
    });
    fireEvent.change(screen.getByLabelText("Adjustment reason"), {
      target: { value: "Received supplier shipment" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Apply adjustment" }));

    expect(await screen.findByText("25 available")).toBeInTheDocument();
    expect(await screen.findByText(/Mechanical Keyboard adjusted/)).toHaveAttribute(
      "role",
      "status",
    );
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/inventories/1/adjustments",
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          quantityDelta: 7,
          reason: "Received supplier shipment",
        }),
      }),
    );
  });

  it("loads every Inventory page so an administrator can adjust a later record", async () => {
    const firstInventory = {
      productId: 101,
      productName: "Newest Product",
      availableQuantity: 7,
      updatedAt: "2026-07-23T00:00:00Z",
    };
    const laterInventory = {
      productId: 1,
      productName: "Oldest Product",
      availableQuantity: 18,
      updatedAt: "2026-07-23T00:00:00Z",
    };
    const fetchMock = vi.fn().mockImplementation(
      (input: string | URL | Request, init?: RequestInit) => {
        const url = input.toString();
        if (url.endsWith("/actuator/health")) {
          return Promise.resolve(jsonResponse({ status: "UP" }));
        }
        if (
          url.endsWith("/api/v1/inventories/1/adjustments") &&
          init?.method === "POST"
        ) {
          return Promise.resolve(
            jsonResponse({ ...laterInventory, availableQuantity: 20 }),
          );
        }
        if (url.includes("/api/v1/inventories?size=100&page=0")) {
          return Promise.resolve(
            jsonResponse({
              content: [firstInventory],
              page: { number: 0, size: 100, totalElements: 101, totalPages: 2 },
            }),
          );
        }
        if (url.includes("/api/v1/inventories?size=100&page=1")) {
          return Promise.resolve(
            jsonResponse({
              content: [laterInventory],
              page: { number: 1, size: 100, totalElements: 101, totalPages: 2 },
            }),
          );
        }
        return Promise.resolve(
          jsonResponse({
            content: [],
            page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
          }),
        );
      },
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);
    await screen.findByText("No products are available for this sale yet.");
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));
    fireEvent.change(
      await screen.findByRole("combobox", { name: "Inventory to adjust" }),
      { target: { value: "1" } },
    );
    fireEvent.change(screen.getByLabelText("Quantity delta"), {
      target: { value: "2" },
    });
    fireEvent.change(screen.getByLabelText("Adjustment reason"), {
      target: { value: "Cycle count correction" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Apply adjustment" }));

    expect(await screen.findByText("20 available")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/inventories/1/adjustments",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("maps Inventory adjustment Problem Details to the Admin form", async () => {
    const inventory = {
      productId: 1,
      productName: "Mechanical Keyboard",
      availableQuantity: 18,
      updatedAt: "2026-07-23T00:00:00Z",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(
        (input: string | URL | Request, init?: RequestInit) => {
          const url = input.toString();
          if (url.endsWith("/actuator/health")) {
            return Promise.resolve(jsonResponse({ status: "UP" }));
          }
          if (
            url.endsWith("/api/v1/inventories/1/adjustments") &&
            init?.method === "POST"
          ) {
            return Promise.resolve(
              jsonResponse(
                {
                  detail: "One or more request parameters are invalid",
                  fieldErrors: [{ field: "reason", message: "size must be between 3 and 200" }],
                },
                400,
              ),
            );
          }
          if (url.includes("/api/v1/inventories")) {
            return Promise.resolve(jsonResponse({ content: [inventory] }));
          }
          return Promise.resolve(
            jsonResponse({
              content: [],
              page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
            }),
          );
        },
      ),
    );

    render(<App />);
    await screen.findByText("No products are available for this sale yet.");
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));
    fireEvent.change(
      await screen.findByRole("combobox", { name: "Inventory to adjust" }),
      { target: { value: "1" } },
    );
    fireEvent.change(screen.getByLabelText("Quantity delta"), {
      target: { value: "1" },
    });
    fireEvent.change(screen.getByLabelText("Adjustment reason"), {
      target: { value: "ab" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Apply adjustment" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "One or more request parameters are invalid",
    );
    expect(screen.getByLabelText("Adjustment reason")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.getByText("size must be between 3 and 200")).toBeInTheDocument();
  });

  it("locks the submitted form while product creation is pending", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(
        (input: string | URL | Request, init?: RequestInit) => {
          const url = input.toString();
          if (url.endsWith("/actuator/health")) {
            return Promise.resolve(jsonResponse({ status: "UP" }));
          }
          if (url.endsWith("/api/v1/products") && init?.method === "POST") {
            return new Promise<Response>(() => undefined);
          }
          return Promise.resolve(
            jsonResponse({
              content: [],
              page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
            }),
          );
        },
      ),
    );

    render(<App />);
    await screen.findByText("No products are available for this sale yet.");
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));

    const name = screen.getByLabelText("Product name");
    fireEvent.change(name, { target: { value: "Standing Desk" } });
    fireEvent.change(screen.getByLabelText("Price (VND)"), {
      target: { value: "15990000" },
    });
    fireEvent.change(screen.getByLabelText("Initial inventory"), {
      target: { value: "25" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create product" }));

    expect(name).toBeDisabled();
    expect(screen.getByRole("button", { name: "Creating product…" })).toBeDisabled();
  });

  it("keeps the post-create Inventory refresh when an older request finishes later", async () => {
    const createdProduct = {
      id: 101,
      name: "Newest Product",
      description: "Created after the first inventory request.",
      price: 1000,
      currency: "VND",
      active: true,
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    };
    let resolveInitialInventory!: (response: Response) => void;
    let inventoryRequests = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(
        (input: string | URL | Request, init?: RequestInit) => {
          const url = input.toString();
          if (url.endsWith("/actuator/health")) {
            return Promise.resolve(jsonResponse({ status: "UP" }));
          }
          if (url.endsWith("/api/v1/products") && init?.method === "POST") {
            return Promise.resolve(jsonResponse(createdProduct, 201));
          }
          if (url.includes("/api/v1/inventories")) {
            inventoryRequests += 1;
            if (inventoryRequests === 1) {
              return new Promise<Response>((resolve) => {
                resolveInitialInventory = resolve;
              });
            }
            return Promise.resolve(
              jsonResponse({
                content: [
                  {
                    productId: 101,
                    productName: "Newest Product",
                    availableQuantity: 7,
                    updatedAt: "2026-07-23T00:00:00Z",
                  },
                ],
                page: { number: 0, size: 100, totalElements: 101, totalPages: 2 },
              }),
            );
          }
          return Promise.resolve(
            jsonResponse({
              content: [],
              page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
            }),
          );
        },
      ),
    );

    render(<App />);
    await screen.findByText("No products are available for this sale yet.");
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));
    fireEvent.change(screen.getByLabelText("Product name"), {
      target: { value: "Newest Product" },
    });
    fireEvent.change(screen.getByLabelText("Price (VND)"), {
      target: { value: "1000" },
    });
    fireEvent.change(screen.getByLabelText("Initial inventory"), {
      target: { value: "7" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create product" }));

    expect(await screen.findByText("7 available")).toBeInTheDocument();
    resolveInitialInventory(
      jsonResponse({
        content: [],
        page: { number: 0, size: 100, totalElements: 0, totalPages: 0 },
      }),
    );
    await waitFor(() => {
      expect(screen.getByText("7 available")).toBeInTheDocument();
    });
  });

  it("maps product creation problem details back to the Admin form", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(
        (input: string | URL | Request, init?: RequestInit) => {
          const url = input.toString();
          if (url.endsWith("/actuator/health")) {
            return Promise.resolve(jsonResponse({ status: "UP" }));
          }
          if (url.endsWith("/api/v1/products") && init?.method === "POST") {
            return Promise.resolve(
              jsonResponse(
                {
                  detail: "One or more request parameters are invalid",
                  fieldErrors: [
                    {
                      field: "name",
                      message: "must not have leading or trailing whitespace",
                    },
                  ],
                },
                400,
              ),
            );
          }
          return Promise.resolve(
            jsonResponse({
              content: [],
              page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
            }),
          );
        },
      ),
    );

    render(<App />);
    await screen.findByText("No products are available for this sale yet.");
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));

    const name = screen.getByLabelText("Product name");
    fireEvent.change(name, { target: { value: " Standing Desk " } });
    fireEvent.change(screen.getByLabelText("Price (VND)"), {
      target: { value: "15990000" },
    });
    fireEvent.change(screen.getByLabelText("Initial inventory"), {
      target: { value: "25" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create product" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "One or more request parameters are invalid",
    );
    expect(name).toHaveAttribute("aria-invalid", "true");
    expect(name).toHaveAttribute("data-state", "error");
    expect(
      screen.getByText("must not have leading or trailing whitespace"),
    ).toBeInTheDocument();
  });
});

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
