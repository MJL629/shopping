// AI-assisted regression coverage for directly opening and refreshing protected pages.
import test from "node:test";
import assert from "node:assert/strict";
import { routeFromHash } from "../src/lib/route.js";
test("direct cart, order and admin routes survive initialization", () => {
  for (const path of ["/cart", "/orders", "/admin", "/"]) {
    assert.equal(routeFromHash("#" + path), path);
    assert.equal(routeFromHash("#" + path + "?page=2"), path);
  }
  for (const hash of ["", "#", "#collection"]) assert.equal(routeFromHash(hash), "/");
});
