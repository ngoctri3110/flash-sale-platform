import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import App from "./App";

describe("App", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("shows that the backend is healthy when its health endpoint is up", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ status: "UP" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    expect(
      screen.getByRole("heading", { name: "Flash Sale Platform" }),
    ).toBeInTheDocument();
    expect(
      await screen.findByText("Backend is healthy"),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/actuator/health");
  });
});
