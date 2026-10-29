import type { WhiteNoisePlaylist } from "../types";
import { dispatchNativeMedia, isNativeMediaExpected } from "./nativeMedia";

const CACHE_NAME = "budlog-white-noise-v1";
export const WHITE_NOISE_CACHE_CLEARED_EVENT = "budlog-white-noise-cache-cleared";

function webCacheAvailable(): boolean {
  return !isNativeMediaExpected() && typeof window !== "undefined" && "caches" in window;
}

function absoluteStreamUrls(playlists: WhiteNoisePlaylist[]): Set<string> {
  const result = new Set<string>();
  if (typeof window === "undefined") return result;
  for (const playlist of playlists) {
    for (const track of playlist.tracks) {
      const url = new URL(track.streamUrl, window.location.origin);
      if (url.origin === window.location.origin) result.add(url.href);
    }
  }
  return result;
}

export async function pruneWhiteNoiseCache(playlists: WhiteNoisePlaylist[]): Promise<void> {
  if (!webCacheAvailable()) return;
  try {
    const activeUrls = absoluteStreamUrls(playlists);
    const cache = await caches.open(CACHE_NAME);
    const requests = await cache.keys();
    await Promise.all(requests
      .filter((request) => !activeUrls.has(request.url))
      .map((request) => cache.delete(request)));
  } catch (error) {
    console.warn("Budlog white-noise cache cleanup failed", error);
  }
}

export async function clearWhiteNoiseCaches(): Promise<void> {
  dispatchNativeMedia({ action: "clearCache" });
  if (typeof window !== "undefined") window.dispatchEvent(new Event(WHITE_NOISE_CACHE_CLEARED_EVENT));
  if (!webCacheAvailable()) return;
  try {
    await caches.delete(CACHE_NAME);
  } catch (error) {
    console.warn("Budlog white-noise cache clearing failed", error);
  }
}
