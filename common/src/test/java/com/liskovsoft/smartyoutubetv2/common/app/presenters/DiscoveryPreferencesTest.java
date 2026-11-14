package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.content.Context;
import android.content.SharedPreferences;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DiscoveryPreferencesTest {
    private Account account(String email, String name) {
        return new Account() {
            public int getId() { return 0; }
            public String getName() { return name; }
            public String getEmail() { return email; }
            public String getAvatarImageUrl() { return null; }
            public boolean isSelected() { return true; }
            public boolean isEmpty() { return false; }
        };
    }

    @Test public void accountsWithSharedNamesOrEmailRemainSeparateAndResetIsLocal() {
        Context app = RuntimeEnvironment.getApplication();
        String a = DiscoverPresenter.accountKey(account("a@example.test", "Shared name"));
        String b = DiscoverPresenter.accountKey(account("b@example.test", "Shared name"));
        String channel = DiscoverPresenter.accountKey(account("a@example.test", "Another channel"));
        String anonymous = DiscoverPresenter.accountKey(null);
        assertNotEquals(a, b);
        assertNotEquals(a, channel);
        assertNotEquals(a, anonymous);
        assertEquals(a, DiscoverPresenter.accountKey(account("a@example.test", "Shared name")));
        assertFalse(a.contains("example"));
        SharedPreferences first = DiscoverPresenter.prefs(app, a);
        SharedPreferences second = DiscoverPresenter.prefs(app, b);
        first.edit().clear().putInt("weight_History", 3).putString("focus_topic", "History").commit();
        second.edit().clear().putInt("weight_History", -2).commit();
        assertEquals(3, first.getInt("weight_History", 0));
        assertEquals(-2, second.getInt("weight_History", 0));
        assertEquals("", second.getString("focus_topic", ""));
        first.edit().clear().commit();
        assertEquals(-2, second.getInt("weight_History", 0));
        second.edit().clear().commit();
    }
}
