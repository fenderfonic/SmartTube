package com.liskovsoft.smartyoutubetv2.common.app.models.discover;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DiscoveryPoolTest {
    private JSONObject body() throws Exception {
        JSONArray entries = new JSONArray();
        for (int t = 0; t < 18; t++) {
            for (int q = 0; q < 12; q++) entries.put(new JSONObject()
                    .put("id", t + "-" + q).put("topic", "Topic " + t)
                    .put("region", q % 2 == 0 ? "Asia" : "Africa")
                    .put("query", "Subject " + t + " investigation " + q));
        }
        return new JSONObject().put("schema_version", 2).put("revision", "test")
                .put("generated_at", "2026-11-14T00:00:00Z").put("entries", entries);
    }

    private List<String> queries(List<DiscoveryPool.Entry> entries) {
        List<String> result = new ArrayList<>();
        for (DiscoveryPool.Entry e : entries) result.add(e.query);
        return result;
    }

    @Test public void deterministicDiverseAndSuppressesRecent() throws Exception {
        DiscoveryPool pool = new DiscoveryPool(body().toString());
        Map<String, Integer> weights = new HashMap<>();
        List<DiscoveryPool.Entry> first = pool.select(0, weights, Collections.emptySet(), "", "");
        assertEquals(12, first.size());
        Set<String> topics = new HashSet<>();
        for (DiscoveryPool.Entry e : first) topics.add(e.topic);
        assertEquals(12, topics.size());
        assertEquals(queries(first), queries(pool.select(0, weights, Collections.emptySet(), "", "")));
        Set<String> recent = new HashSet<>(queries(first));
        List<String> next = queries(pool.select(1, weights, recent, "", ""));
        assertTrue(Collections.disjoint(recent, next));
        assertTrue(weights.isEmpty());
    }

    @Test public void focusReservesUnfamiliarDirectionsAndWeightsAreNotMutated() throws Exception {
        DiscoveryPool pool = new DiscoveryPool(body().toString());
        Map<String, Integer> weights = new HashMap<>();
        weights.put("Topic 1", 3);
        List<DiscoveryPool.Entry> entries = pool.select(7, weights, Collections.emptySet(), "Topic 1", "Asia");
        int focused = 0, outside = 0;
        for (DiscoveryPool.Entry e : entries) {
            if (e.topic.equals("Topic 1") && e.region.equals("Asia")) focused++;
            else outside++;
        }
        assertEquals(12, entries.size());
        assertEquals(6, focused);
        assertTrue(outside >= 3);
        assertEquals(Integer.valueOf(3), weights.get("Topic 1"));
    }

    @Test public void allDownvotedAndExhaustedStillRecover() throws Exception {
        DiscoveryPool pool = new DiscoveryPool(body().toString());
        Map<String, Integer> weights = new HashMap<>();
        Set<String> recent = new HashSet<>();
        for (DiscoveryPool.Entry e : pool.entries) { weights.put(e.topic, -3); recent.add(e.query); }
        assertEquals(12, pool.select(0, weights, recent, "", "").size());
    }

    @Test public void rejectsWrongSchemaDuplicatesAndHostileFields() throws Exception {
        JSONObject wrong = body().put("schema_version", 3);
        try { new DiscoveryPool(wrong.toString()); fail(); } catch (IllegalArgumentException expected) {}
        JSONObject duplicate = body();
        duplicate.getJSONArray("entries").put(duplicate.getJSONArray("entries").getJSONObject(0));
        try { new DiscoveryPool(duplicate.toString()); fail(); } catch (IllegalArgumentException expected) {}
        JSONObject invalid = body();
        invalid.getJSONArray("entries").getJSONObject(0).put("query", "bad\nquery");
        try { new DiscoveryPool(invalid.toString()); fail(); } catch (IllegalArgumentException expected) {}
    }
}
