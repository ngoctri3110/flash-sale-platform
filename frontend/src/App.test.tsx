import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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
                totalElements: productCreated ? 1 : 0,
                totalPages: productCreated ? 1 : 0,
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
