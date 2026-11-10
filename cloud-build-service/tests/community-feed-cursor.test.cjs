const test = require("node:test");
const assert = require("node:assert/strict");
const { parseCommunityFeedCursor } = require("../node_modules/.cache/community-feed-cursor-test/community-feed-cursor.js");

const id = "123e4567-e89b-42d3-a456-426614174000";
const validTime = "2026-11-10T09:30:45.123Z";

test("accepts the canonical cursor emitted by the feed endpoint", () => {
  assert.deepEqual(parseCommunityFeedCursor(validTime + "|" + id), {
    time: validTime,
    id
  });
});

test("rejects malformed or non-canonical timestamps", () => {
  for (const time of ["2026-11-10", "2026-13-10T09:30:45.123Z", "2026-11-10T09:30:45+00:00"]) {
    assert.equal(parseCommunityFeedCursor(time + "|" + id), null);
  }
});

test("rejects malformed UUIDs and ambiguous separators", () => {
  assert.equal(parseCommunityFeedCursor(validTime + "|not-a-uuid"), null);
  assert.equal(parseCommunityFeedCursor(validTime + "|" + id + "|extra"), null);
  assert.equal(parseCommunityFeedCursor("|" + id), null);
});
