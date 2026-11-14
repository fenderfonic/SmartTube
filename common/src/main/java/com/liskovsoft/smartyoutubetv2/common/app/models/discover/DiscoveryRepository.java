package com.liskovsoft.smartyoutubetv2.common.app.models.discover;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared public pool, private per-account choices. Never uploads anything. */
public final class DiscoveryRepository {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static boolean busy;
    private static long nextAttempt;
    private static final String PREFS = "adnada_discover_pool_v2";

    public static SharedPreferences settings(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static DiscoveryPool load(Context context) {
        String cache = settings(context).getString("pool", "");
        try {
            return new DiscoveryPool(cache);
        } catch (Exception ignored) {
            try (InputStream stream = context.getAssets().open("discover-pool-v2.json")) {
                return new DiscoveryPool(read(stream));
            } catch (Exception error) {
                throw new IllegalStateException("Invalid bundled Discover pool", error);
            }
        }
    }

    public static boolean validEndpoint(String text) {
        try {
            URL url = new URL(text);
            if (!url.getProtocol().equals("http") || url.getUserInfo() != null
                    || url.getRef() != null || url.getQuery() != null
                    || !url.getPath().equals("/pool.json")) return false;
            String[] octets = url.getHost().split("\\.");
            if (octets.length != 4) return false;
            int[] a = new int[4];
            for (int i = 0; i < 4; i++) {
                if (!octets[i].matches("0|[1-9][0-9]{0,2}")) return false;
                a[i] = Integer.parseInt(octets[i]);
                if (a[i] > 255) return false;
            }
            return a[0] == 10 || (a[0] == 172 && a[1] >= 16 && a[1] <= 31)
                    || (a[0] == 192 && a[1] == 168);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static synchronized void fetch(Context context, boolean force, Runnable completion) {
        SharedPreferences prefs = settings(context);
        String endpoint = prefs.getString("endpoint", "");
        long now = android.os.SystemClock.elapsedRealtime();
        if (busy || !validEndpoint(endpoint) || (!force && now < nextAttempt)) return;
        busy = true;
        nextAttempt = now + 15 * 60 * 1000L;
        IO.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setInstanceFollowRedirects(false);
                if (connection.getResponseCode() != 200) throw new IllegalArgumentException("HTTP status");
                if (connection.getContentLength() > DiscoveryPool.MAX_BYTES) throw new IllegalArgumentException("size");
                String raw;
                try (InputStream stream = connection.getInputStream()) { raw = read(stream); }
                new DiscoveryPool(raw); // Validate fully before replacing last-good data.
                // A request started before disabling/changing endpoint must not overwrite the cache.
                if (endpoint.equals(prefs.getString("endpoint", ""))) {
                    prefs.edit().putString("pool", raw).putLong("fetched_at", System.currentTimeMillis()).commit();
                }
            } catch (Exception error) {
                Log.w("Discover", "Keeping last-good pool: " + error.getClass().getSimpleName());
            } finally {
                if (connection != null) connection.disconnect();
                synchronized (DiscoveryRepository.class) { busy = false; }
                if (completion != null) new Handler(Looper.getMainLooper()).post(completion);
            }
        });
    }

    private static String read(InputStream stream) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        long deadline = android.os.SystemClock.elapsedRealtime() + 8000;
        int length;
        while ((length = stream.read(buffer)) != -1) {
            if (bytes.size() + length > DiscoveryPool.MAX_BYTES
                    || android.os.SystemClock.elapsedRealtime() > deadline) throw new IllegalArgumentException("size/time");
            bytes.write(buffer, 0, length);
        }
        return bytes.toString("UTF-8");
    }
}
