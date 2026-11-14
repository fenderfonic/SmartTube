package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.discover.DiscoveryPool;
import com.liskovsoft.smartyoutubetv2.common.app.models.discover.DiscoveryRepository;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Native search shelf. Only explicit thumbs change preferences; no playback hooks. */
public final class DiscoverPresenter {
    public static final int SECTION_ID = 23;
    private DiscoverPresenter() {}

    static String accountKey() {
        return accountKey(MediaServiceManager.instance().getSelectedAccount());
    }

    static String accountKey(Account a) {
        if (a == null || a.isEmpty()) return "anonymous";
        try {
            String identity = a.getId() + "\n" + a.getEmail() + "\n" + a.getName();
            return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(
                    identity.getBytes(StandardCharsets.UTF_8)), Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    static SharedPreferences prefs(Context context, String account) {
        return context.getSharedPreferences("adnada_discover_v2_" + account, Context.MODE_PRIVATE);
    }

    private static void add(List<SettingsItem> items, Context context, String account, String label, Runnable action) {
        items.add(new SettingsItem(label, () -> {
            if (account.equals(accountKey())) action.run();
            else refresh(context); // Ignore callbacks left over from another account's screen.
        }));
    }

    public static List<SettingsItem> items(Context context) {
        Context app = context.getApplicationContext();
        String account = accountKey();
        SharedPreferences p = prefs(app, account);
        DiscoveryPool pool = DiscoveryRepository.load(app);
        DiscoveryRepository.fetch(app, false, null);
        Set<String> recent = new HashSet<>(Arrays.asList(p.getString("recent", "").split("\n")));
        Map<String, Integer> weights = new HashMap<>();
        for (DiscoveryPool.Entry e : pool.entries) weights.put(e.topic, p.getInt("weight_" + e.topic, 0));
        long turn = p.getLong("turn", 0);
        List<DiscoveryPool.Entry> selected = pool.select(turn, weights, recent,
                p.getString("focus_topic", ""), p.getString("focus_region", ""));
        List<SettingsItem> result = new ArrayList<>();
        add(result, context, account, "Pivot · twelve different directions", () -> refresh(context));
        add(result, context, account, "Focus · subject or region", () -> focus(context, account, pool));
        add(result, context, account, "Explore · clear Focus", () -> {
            p.edit().remove("focus_topic").remove("focus_region").apply();
            refresh(context);
        });
        String last = p.getString("last_topic", "");
        if (!last.isEmpty()) {
            add(result, context, account, "👍 Worth exploring · " + last, () -> feedback(context, p, last, 1));
            add(result, context, account, "👎 Less of this · " + last, () -> feedback(context, p, last, -1));
            add(result, context, account, "Focus · last subject: " + last, () -> {
                p.edit().putString("focus_topic", last).apply();
                refresh(context);
            });
        }
        for (DiscoveryPool.Entry e : selected) {
            add(result, context, account, "Explore · " + e.topic + " · " + e.query, () -> {
                // Selection only names the explicit feedback target. It never alters weights.
                p.edit().putString("last_topic", e.topic).apply();
                SearchPresenter.instance(context).startSearch(e.query);
            });
        }
        add(result, context, account, "Pool · " + pool.revision + " · private host settings", () -> endpoint(context));
        add(result, context, account, "Refresh pool from host", () -> DiscoveryRepository.fetch(app, true,
                () -> MessageHelpers.showMessage(app, "Pool refresh finished; Pivot to use cached results")));
        add(result, context, account, "Reset this account's discovery preferences", () -> {
            p.edit().clear().apply();
            refresh(context);
        });
        List<String> history = new ArrayList<>(Arrays.asList(p.getString("recent", "").split("\n")));
        for (DiscoveryPool.Entry e : selected) history.add(e.query);
        int from = Math.max(0, history.size() - 96);
        StringBuilder stored = new StringBuilder();
        for (String query : history.subList(from, history.size())) {
            if (stored.length() > 0) stored.append('\n');
            stored.append(query);
        }
        p.edit().putLong("turn", turn + 1).putString("recent", stored.toString()).apply();
        return result;
    }

    private static void feedback(Context context, SharedPreferences p, String topic, int step) {
        int weight = Math.max(-3, Math.min(3, p.getInt("weight_" + topic, 0) + step));
        p.edit().putInt("weight_" + topic, weight).apply();
        MessageHelpers.showMessage(context, "Noted for " + topic + "; unfamiliar directions remain reserved");
        refresh(context);
    }

    private static void focus(Context context, String account, DiscoveryPool pool) {
        SharedPreferences p = prefs(context, account);
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        Set<String> topics = new TreeSet<>(), regions = new TreeSet<>();
        for (DiscoveryPool.Entry e : pool.entries) { topics.add(e.topic); regions.add(e.region); }
        for (String topic : topics) {
            dialog.appendSingleButton(UiOptionItem.from("Subject · " + topic, option -> {
                if (account.equals(accountKey())) p.edit().putString("focus_topic", topic).apply();
                dialog.closeDialog();
                refresh(context);
            }));
        }
        for (String region : regions) {
            dialog.appendSingleButton(UiOptionItem.from("Region · " + region, option -> {
                if (account.equals(accountKey())) p.edit().putString("focus_region", region).apply();
                dialog.closeDialog();
                refresh(context);
            }));
        }
        dialog.showDialog("Focus · subjects and regions (combine them; Explore clears both)");
    }

    private static void endpoint(Context context) {
        SharedPreferences config = DiscoveryRepository.settings(context);
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        dialog.appendSingleButton(UiOptionItem.from("Set private host URL", option -> {
            dialog.closeDialog();
            SimpleEditDialog.show(context, "http://192.168.x.x:8766/pool.json",
                    config.getString("endpoint", "http://192.168.1.2:8766/pool.json"), value -> {
                        String url = value.trim();
                        if (!DiscoveryRepository.validEndpoint(url)) {
                            MessageHelpers.showMessage(context, "Use a private IPv4 HTTP address and /pool.json");
                            return false;
                        }
                        config.edit().putString("endpoint", url).apply();
                        DiscoveryRepository.fetch(context.getApplicationContext(), true, null);
                        return true;
                    });
        }));
        dialog.appendSingleButton(UiOptionItem.from("Disable host · keep last-good pool", option -> {
            config.edit().remove("endpoint").apply();
            dialog.closeDialog();
        }));
        dialog.showDialog("Discover private host · shared pool, account-local feedback");
    }

    private static void refresh(Context context) { BrowsePresenter.instance(context).refresh(false); }
}
