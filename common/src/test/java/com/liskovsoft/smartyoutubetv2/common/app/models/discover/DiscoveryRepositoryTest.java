package com.liskovsoft.smartyoutubetv2.common.app.models.discover;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DiscoveryRepositoryTest {
    @Test public void privateLiteralEndpointOnly() {
        assertTrue(DiscoveryRepository.validEndpoint("http://192.168.1.2:8766/pool.json"));
        assertTrue(DiscoveryRepository.validEndpoint("http://10.0.0.2/pool.json"));
        for (String bad : new String[]{"http://8.8.8.8/pool.json", "http://localhost/pool.json",
                "https://192.168.1.2/pool.json", "http://192.168.1.2/state.json",
                "http://user:pass@10.0.0.2/pool.json", "http://10.0.0.2/pool.json?key=secret",
                "http://2130706433/pool.json", "http://010.0.0.2/pool.json"}) {
            assertFalse(bad, DiscoveryRepository.validEndpoint(bad));
        }
    }

    @Test public void corruptCacheRecoversWithValidBundledPool() {
        Context app = RuntimeEnvironment.getApplication();
        DiscoveryRepository.settings(app).edit().putString("pool", "broken").commit();
        DiscoveryPool pool = DiscoveryRepository.load(app);
        assertEquals(216, pool.entries.size());
        assertEquals(12, pool.select(0, java.util.Collections.emptyMap(),
                java.util.Collections.emptySet(), "", "").size());
        DiscoveryRepository.settings(app).edit().clear().commit();
    }
}
