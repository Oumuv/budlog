const CACHE_PREFIX = "budlog-shell";
const CACHE_NAME = `${CACHE_PREFIX}-v1`;
const MEDIA_CACHE_NAME = "budlog-white-noise-v1";
const MEDIA_SESSION_PATH = "/api/v1/white-noise/session";
const MAX_MEDIA_CACHE_BYTES = 512 * 1024 * 1024;
const MEDIA_PATH_PATTERN = /^\/api\/v1\/white-noise\/tracks\/[a-f0-9]{64}\/stream$/;
const APP_SHELL = [
  "/",
  "/manifest.webmanifest",
  "/icons/icon-192.png",
  "/icons/icon-512.png",
  "/icons/icon-maskable-512.png",
  "/icons/apple-touch-icon.png",
  "/icons/favicon-32.png",
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key.startsWith(CACHE_PREFIX) && key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

async function networkFirst(request) {
  const cache = await caches.open(CACHE_NAME);
  try {
    const response = await fetch(request);
    if (response.ok) await cache.put("/", response.clone());
    return response;
  } catch (error) {
    const cached = await cache.match("/");
    if (cached) return cached;
    throw error;
  }
}

async function cacheFirst(request) {
  const cache = await caches.open(CACHE_NAME);
  const cached = await cache.match(request);
  if (cached) return cached;
  const response = await fetch(request);
  if (response.ok) await cache.put(request, response.clone());
  return response;
}

async function networkFirstAsset(request) {
  const cache = await caches.open(CACHE_NAME);
  try {
    const response = await fetch(request);
    if (response.ok) await cache.put(request, response.clone());
    return response;
  } catch (error) {
    const cached = await cache.match(request);
    if (cached) return cached;
    throw error;
  }
}

function fullMediaRequest(request) {
  const headers = new Headers(request.headers);
  headers.delete("range");
  headers.delete("if-range");
  headers.delete("if-none-match");
  headers.delete("if-modified-since");
  return new Request(request.url, {
    method: "GET",
    headers,
    credentials: "same-origin",
    redirect: "error",
    cache: "no-store",
  });
}

function parseMediaRange(value, size) {
  const match = /^bytes=(\d*)-(\d*)$/.exec(value || "");
  if (!match || (!match[1] && !match[2]) || size <= 0) return null;

  let start;
  let end;
  if (!match[1]) {
    const suffixLength = Number(match[2]);
    if (!Number.isSafeInteger(suffixLength) || suffixLength <= 0) return null;
    start = Math.max(0, size - suffixLength);
    end = size - 1;
  } else {
    start = Number(match[1]);
    end = match[2] ? Number(match[2]) : size - 1;
    if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end)) return null;
  }
  if (start < 0 || start >= size || end < start) return null;
  return { start, end: Math.min(end, size - 1) };
}

function mediaSize(response) {
  const value = response.headers.get("Content-Length");
  if (!value || !/^\d+$/.test(value)) return null;
  const size = Number(value);
  return Number.isSafeInteger(size) && size > 0 ? size : null;
}

function rangeBody(body, start, end) {
  const reader = body.getReader();
  let offset = 0;
  let remaining = end - start + 1;
  return new ReadableStream({
    async pull(controller) {
      while (remaining > 0) {
        const result = await reader.read();
        if (result.done) {
          controller.error(new Error("Cached audio ended before the requested range."));
          return;
        }
        const chunk = result.value;
        const chunkOffset = offset;
        offset += chunk.byteLength;
        const from = Math.max(0, start - chunkOffset);
        const to = Math.min(chunk.byteLength, end + 1 - chunkOffset);
        if (from >= to) continue;

        const slice = chunk.subarray(from, to);
        remaining -= slice.byteLength;
        controller.enqueue(slice);
        if (remaining === 0) {
          await reader.cancel();
          controller.close();
        }
        return;
      }
    },
    cancel(reason) {
      return reader.cancel(reason);
    },
  });
}

async function mediaResponse(fullResponse, rangeHeader, size) {
  if (!rangeHeader) return fullResponse;
  const range = parseMediaRange(rangeHeader, size);
  if (!range) {
    return new Response(null, {
      status: 416,
      headers: {
        "Accept-Ranges": "bytes",
        "Content-Range": `bytes */${size}`,
      },
    });
  }

  if (!fullResponse.body) return new Response(null, { status: 500 });
  const body = rangeBody(fullResponse.body, range.start, range.end);
  const headers = new Headers(fullResponse.headers);
  headers.delete("content-encoding");
  headers.set("Accept-Ranges", "bytes");
  headers.set("Content-Length", String(range.end - range.start + 1));
  headers.set("Content-Range", `bytes ${range.start}-${range.end}/${size}`);
  headers.set("Content-Type", fullResponse.headers.get("Content-Type") || "audio/mpeg");
  return new Response(body, { status: 206, statusText: "Partial Content", headers });
}

async function cacheFirstMedia(request) {
  const cache = await caches.open(MEDIA_CACHE_NAME);
  const cacheKey = new Request(request.url, { credentials: "same-origin" });
  let response = await cache.match(cacheKey);
  if (response) {
    const session = await fetch(MEDIA_SESSION_PATH, {
      method: "GET",
      credentials: "same-origin",
      redirect: "error",
      cache: "no-store",
    });
    if (!session.ok) {
      await caches.delete(MEDIA_CACHE_NAME);
      return session;
    }
  } else {
    response = await fetch(fullMediaRequest(request));
    const contentType = response.headers.get("Content-Type") || "";
    const size = mediaSize(response);
    if (
      response.status === 200
      && response.ok
      && contentType.toLowerCase().startsWith("audio/")
      && size !== null
      && size <= MAX_MEDIA_CACHE_BYTES
    ) {
      try {
        await cache.put(cacheKey, response);
        response = await cache.match(cacheKey);
        if (!response) return fetch(request);
      } catch (error) {
        console.warn("Budlog white-noise cache write failed", error);
        return fetch(request);
      }
    } else if (request.headers.has("Range")) {
      await response.body?.cancel();
      return fetch(request);
    }
  }
  if (response.status !== 200 || !response.ok) return response;
  const contentType = response.headers.get("Content-Type") || "";
  if (!contentType.toLowerCase().startsWith("audio/")) return response;
  const size = mediaSize(response);
  if (size === null) {
    await cache.delete(cacheKey);
    return fetch(request);
  }
  return mediaResponse(response, request.headers.get("Range"), size);
}

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  if (MEDIA_PATH_PATTERN.test(url.pathname)) {
    event.respondWith(cacheFirstMedia(request));
    return;
  }

  if (url.pathname.startsWith("/api/")) return;

  if (request.mode === "navigate") {
    event.respondWith(networkFirst(request));
    return;
  }

  if (url.pathname.startsWith("/assets/")) {
    event.respondWith(cacheFirst(request));
    return;
  }

  if (
    url.pathname.startsWith("/icons/") ||
    url.pathname.startsWith("/static/") ||
    url.pathname === "/manifest.webmanifest"
  ) {
    event.respondWith(networkFirstAsset(request));
  }
});
