package com.liskovsoft.smartyoutubetv2.common.app.models.discover;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Validated wire format and deterministic local selection. No account or playback dependencies. */
public final class DiscoveryPool {
    public static final int MAX_BYTES = 512 * 1024;
    public final String revision;
    public final List<Entry> entries;

    public static final class Entry {
        public final String id, topic, region, query;
        Entry(JSONObject item) throws Exception {
            id = field(item, "id", 64);
            topic = field(item, "topic", 60);
            region = field(item, "region", 40);
            query = field(item, "query", 240);
        }
    }

    private static String field(JSONObject object, String key, int max) throws Exception {
        Object raw = object.get(key);
        if (!(raw instanceof String)) throw new IllegalArgumentException(key);
        String value = (String) raw;
        if (value.trim().isEmpty() || value.length() > max) throw new IllegalArgumentException(key);
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) throw new IllegalArgumentException(key);
        }
        return value;
    }

    public DiscoveryPool(String json) throws Exception {
        if (json.getBytes("UTF-8").length > MAX_BYTES) throw new IllegalArgumentException("size");
        JSONObject body = new JSONObject(json);
        if (body.getInt("schema_version") != 2) throw new IllegalArgumentException("schema");
        revision = field(body, "revision", 64);
        field(body, "generated_at", 64);
        JSONArray array = body.getJSONArray("entries");
        if (array.length() < 8 || array.length() > 600) throw new IllegalArgumentException("count");
        List<Entry> parsed = new ArrayList<>();
        Set<String> ids = new HashSet<>(), queries = new HashSet<>(), topics = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            Entry entry = new Entry(array.getJSONObject(i));
            String normalized = entry.query.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
            if (!ids.add(entry.id) || !queries.add(normalized)) throw new IllegalArgumentException("duplicate");
            topics.add(entry.topic);
            parsed.add(entry);
        }
        if (topics.size() < 8) throw new IllegalArgumentException("diversity");
        entries = Collections.unmodifiableList(parsed);
    }

    public List<Entry> select(long turn, Map<String, Integer> weights, Set<String> recent,
                              String focusTopic, String focusRegion) {
        List<Entry> shuffled = new ArrayList<>(entries);
        Collections.shuffle(shuffled, new Random(31L * revision.hashCode() + turn));
        List<Entry> unfamiliar = new ArrayList<>(shuffled);
        Collections.sort(unfamiliar, (a, b) -> Boolean.compare(recent.contains(a.query), recent.contains(b.query)));
        Map<String, Double> scores = new HashMap<>();
        Random jitter = new Random(turn ^ revision.hashCode());
        for (Entry e : shuffled) scores.put(e.id, score(e, weights, recent) + jitter.nextDouble());
        Collections.sort(shuffled, (a, b) -> Double.compare(scores.get(b.id), scores.get(a.id)));
        List<Entry> result = new ArrayList<>();
        Set<String> topics = new HashSet<>(), queries = new HashSet<>();
        // Reserve three of twelve slots for neutral/unfamiliar directions, outside Focus.
        pick(unfamiliar, result, topics, queries, 3, weights, focusTopic, focusRegion, 0);
        // If every topic has feedback, still reserve neutral, unweighted slots.
        pick(unfamiliar, result, topics, queries, 3, weights, "", "", 2);
        pick(shuffled, result, topics, queries, 9, weights, focusTopic, focusRegion, 1);
        pick(shuffled, result, topics, queries, 12, weights, "", "", 2);
        return result;
    }

    private static double score(Entry e, Map<String, Integer> weights, Set<String> recent) {
        int weight = Math.max(-3, Math.min(3, weights.containsKey(e.topic) ? weights.get(e.topic) : 0));
        return (recent.contains(e.query) ? -100 : 0) + weight * 0.075;
    }

    private static void pick(List<Entry> source, List<Entry> result, Set<String> topics,
                             Set<String> queries, int limit, Map<String, Integer> weights,
                             String focusTopic, String focusRegion, int mode) {
        boolean focused = !focusTopic.isEmpty() || !focusRegion.isEmpty();
        for (Entry e : source) {
            if (result.size() >= limit) return;
            boolean match = (focusTopic.isEmpty() || e.topic.equals(focusTopic))
                    && (focusRegion.isEmpty() || e.region.equals(focusRegion));
            if (mode == 0 && ((weights.containsKey(e.topic) && weights.get(e.topic) > 0)
                    || (focused && match))) continue;
            if (mode == 1 && !match) continue;
            // Focus may use several queries in the selected topic; general exploration spans topics.
            if ((mode != 1 || focusTopic.isEmpty()) && topics.contains(e.topic)) continue;
            if (!queries.add(e.query)) continue;
            topics.add(e.topic);
            result.add(e);
        }
    }
}
