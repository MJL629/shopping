// AI-assisted hash parsing; initialize before HomeView can rewrite catalog state.
export function routeFromHash(hash = "") {
  const path = hash.replace(/^#/, "").split("?")[0];
  return path.startsWith("/") ? path : "/";
}
