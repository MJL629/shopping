// AI-assisted API client with Codex. Session cookies stay HttpOnly; CSRF token comes from the server.
let csrfToken = "";
let authenticationRequired = () => {};
export function setAuthenticationRequired(handler) {
  authenticationRequired = handler;
}
export async function session() {
  const data = await request("/auth/session");
  csrfToken = data.csrfToken;
  return data.user;
}
export async function request(path, options = {}) {
  const method = options.method || "GET";
  if (!["GET", "HEAD"].includes(method) && !csrfToken) await session();
  const multipart =
    typeof FormData !== "undefined" && options.body instanceof FormData;
  const response = await fetch("/api" + path, {
    credentials: "same-origin",
    ...options,
    headers: {
      ...(options.body && !multipart
        ? { "Content-Type": "application/json" }
        : {}),
      ...(!["GET", "HEAD"].includes(method)
        ? { "X-XSRF-TOKEN": csrfToken }
        : {}),
      ...options.headers,
    },
    body: options.body
      ? multipart
        ? options.body
        : JSON.stringify(options.body)
      : undefined,
  });
  if (response.status === 204) return null;
  if (response.ok && options.responseType === "blob") return response.blob();
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    if (response.status === 401 && !path.startsWith("/auth/"))
      authenticationRequired();
    const error = new Error(data.detail || "请求暂时未完成，请稍后重试。");
    error.status = response.status;
    throw error;
  }
  return data;
}
