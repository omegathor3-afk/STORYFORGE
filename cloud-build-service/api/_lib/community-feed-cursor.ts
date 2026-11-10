export interface CommunityFeedCursor {
  readonly time: string;
  readonly id: string;
}

/**
 * Parse the canonical cursor emitted by the feed endpoint.
 * Requiring an exact ISO-8601 round trip prevents permissive Date.parse()
 * inputs from changing the keyset-pagination boundary.
 */
export function parseCommunityFeedCursor(raw: string): CommunityFeedCursor | null {
  const separator = raw.indexOf("|");
  if (separator <= 0 || separator !== raw.lastIndexOf("|")) return null;

  const time = raw.slice(0, separator);
  const id = raw.slice(separator + 1);
  if (!/^[0-9a-f-]{36}$/i.test(id)) return null;

  const timestamp = Date.parse(time);
  if (!Number.isFinite(timestamp) || new Date(timestamp).toISOString() !== time) return null;

  return { time, id };
}
