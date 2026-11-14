package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * A deliberately small, local-only exploration shelf.
 *
 * No user behaviour is collected from playback or clicks as a preference.
 * Only explicit positive/negative feedback changes topic weights.
 * YouTube is used for search results, never for generating this shelf.
 *
 * Later, a private owner-operated service may provide fresh query suggestions
 * without placing third-party API keys in this Android application.
 */
public final class DiscoverPresenter {
    // MediaGroup built-in section IDs currently end at 22.
    public static final int SECTION_ID = 23;

    private static final String PREFS = "adnada_discover";
    private static final String LAST_TOPIC = "last_topic";
    private static final String RECENT_QUERIES = "recent_queries";
    private static final int RECENT_LIMIT = 48;
    private static final int DISPLAY_COUNT = 8;
    private static final Random RANDOM = new Random();

    // Independently curated search directions; no personal watch history or
    // existing recommendation categories are used to generate these.
    private static final String[][] TOPICS = {
        {"Engineering",
            "Fred Dibnah steam engineering and industrial heritage documentary",
            "How mechanical clocks are restored by expert craftsmen",
            "The engineering of historical bridges explained",
            "How early computers were built by hand"},
        {"History",
            "Forgotten cities recovered by archaeologists documentary",
            "How ordinary people lived in medieval cities",
            "The history of navigation before GPS explained",
            "How historians reconstruct ancient trade routes"},
        {"Music",
            "Unusual instruments and how they work documentary",
            "Traditional music traditions around the world explained",
            "The craft of building a pipe organ documentary",
            "Early electronic music pioneers documentary"},
        {"Science",
            "Beautiful experiments that changed physics",
            "How scientists discovered the age of Earth",
            "The strange physics of everyday materials",
            "Unsolved questions in ocean science explained"},
        {"Craft",
            "Master craftsperson restoring old tools documentary",
            "Traditional Japanese woodworking techniques explained",
            "How stained glass windows are made by hand",
            "Forgotten industrial crafts and techniques"},
        {"Language",
            "How linguists decipher unknown scripts",
            "Languages that developed extraordinary writing systems",
            "Why different languages describe colours differently",
            "The history of constructed languages explained"},
        {"Architecture",
            "Vernacular architecture adapted to extreme climates",
            "The engineering behind ancient aqueducts",
            "How cathedral builders solved structural problems",
            "Brutalist architecture explained by architects"},
        {"Mathematics",
            "Unexpected uses of number theory explained",
            "Beautiful mathematics visual proofs",
            "How people measured the Earth before satellites",
            "The mathematics behind musical harmony"},
        {"Medicine",
            "Medical discoveries made by accident history",
            "How the human immune system was discovered",
            "The history of medical imaging explained",
            "What medical historians learn from ancient skeletons"},
        {"Geography",
            "The world's most unusual geological formations explained",
            "How ancient maps changed exploration",
            "Why cities are built where they are geography",
            "How rivers reshape continents documentary"},
        {"Space",
            "Forgotten robotic space missions documentary",
            "How radio astronomers observe invisible galaxies",
            "What spacecraft engineering learned from early failures",
            "The history of planetary mapping explained"},
        {"Biology",
            "Extraordinary animal adaptations explained by scientists",
            "How mushrooms communicate with ecosystems",
            "The evolution of unusual senses in animals",
            "How natural history museums preserve specimens"},
        {"Computing",
            "History of early home computing pioneers documentary",
            "How analogue computers actually worked",
            "The story of forgotten operating systems",
            "Old computer restoration and reverse engineering"},
        {"Art",
            "How conservators restore Renaissance paintings",
            "Forgotten art movements explained documentary",
            "How pigments were made in the ancient world",
            "Artists using mathematics and geometry"},
        {"Food",
            "Traditional food preservation methods around the world",
            "How fermentation works microbiology documentary",
            "The cultural history of bread making",
            "History of spices and global trade routes"},
        {"People",
            "Unknown inventors who changed everyday life",
            "Remarkable oral histories from working people",
            "The lives of overlooked women scientists",
            "History of independent documentary filmmakers"},
        {"Ocean",
            "Deep sea exploration unexpected discoveries documentary",
            "How underwater archaeology works",
            "Strange deep ocean ecosystems scientists",
            "The engineering of historical diving equipment"},
        {"Transport",
            "History of airship engineering documentary",
            "How narrow gauge mountain railways work",
            "Unusual maritime engineering innovations",
            "The history of bicycle design explained"},
        {"Culture",
            "Forgotten festivals and their origins documentary",
            "How traditional games developed around the world",
            "The history of everyday objects museum documentary",
            "Cultural history of public libraries documentary"},
        {"Ideas",
            "How scientific ideas spread before the internet",
            "History of measurement and standard units",
            "Unexpected connections between archaeology and astronomy",
            "Experiments that overturned accepted beliefs"}
    };

    private DiscoverPresenter() {}

    public static List<SettingsItem> items(Context context) {
        SharedPreferences prefs = prefs(context);
        List<String> recentlyShown = readRecent(prefs);
        List<Choice> candidates = new ArrayList<>();
        for (int topic = 0; topic < TOPICS.length; topic++) {
            int weight = prefs.getInt("weight_" + topic, 0);
            if (weight <= -3) {
                continue;
            }
            for (int query = 1; query < TOPICS[topic].length; query++) {
                String text = TOPICS[topic][query];
                // Explicit feedback is a slight nudge, not a gravity well.
                double score = RANDOM.nextDouble() + weight * 0.075;
                if (recentlyShown.contains(text)) {
                    score -= 2.0;
                }
                candidates.add(new Choice(topic, text, score));
            }
        }
        // If every subject was explicitly downvoted, recover with all subjects.
        if (candidates.isEmpty()) {
            for (int topic = 0; topic < TOPICS.length; topic++) {
                for (int q = 1; q < TOPICS[topic].length; q++) {
                    candidates.add(new Choice(topic, TOPICS[topic][q], RANDOM.nextDouble()));
                }
            }
        }

        Collections.sort(candidates, (first, second) -> Double.compare(second.score, first.score));
        List<Choice> picked = new ArrayList<>();
        boolean[] usedTopics = new boolean[TOPICS.length];
        for (Choice candidate : candidates) {
            if (!usedTopics[candidate.topic]) {
                usedTopics[candidate.topic] = true;
                picked.add(candidate);
                if (picked.size() == DISPLAY_COUNT) break;
            }
        }

        List<SettingsItem> result = new ArrayList<>();
        result.add(new SettingsItem("Pivot — give me different directions", () -> {
            BrowsePresenter.instance(context).refresh(false);
        }));

        if (prefs.contains(LAST_TOPIC)) {
            result.add(new SettingsItem("👍 Worth exploring — more variety like that", () -> {
                adjustLastTopic(context, 1);
            }));
            result.add(new SettingsItem("👎 Not my thing — less of that", () -> {
                adjustLastTopic(context, -1);
            }));
        }

        result.add(new SettingsItem("Reset my discovery preferences", () -> {
            // Only discovery preferences are cleared, never SmartTube history.
            prefs.edit().clear().apply();
            MessageHelpers.showMessage(context, "Discovery preferences reset");
            BrowsePresenter.instance(context).refresh(false);
        }));

        for (Choice choice : picked) {
            result.add(new SettingsItem("Explore · " + choice.query, () -> {
                prefs.edit().putInt(LAST_TOPIC, choice.topic).apply();
                SearchPresenter.instance(context).startSearch(choice.query);
            }));
            recentlyShown.add(choice.query);
        }
        writeRecent(prefs, recentlyShown);
        return result;
    }

    private static void adjustLastTopic(Context context, int step) {
        SharedPreferences prefs = prefs(context);
        int topic = prefs.getInt(LAST_TOPIC, -1);
        if (topic < 0 || topic >= TOPICS.length) return;
        int oldWeight = prefs.getInt("weight_" + topic, 0);
        int weight = Math.max(-3, Math.min(3, oldWeight + step));
        prefs.edit().putInt("weight_" + topic, weight).apply();
        MessageHelpers.showMessage(context, step > 0
                ? "Noted. More worthwhile discoveries, less repetition."
                : "Noted. We'll move away from that direction.");
        BrowsePresenter.instance(context).refresh(false);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static List<String> readRecent(SharedPreferences prefs) {
        String raw = prefs.getString(RECENT_QUERIES, "");
        return raw.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(raw.split("\\n")));
    }

    private static void writeRecent(SharedPreferences prefs, List<String> recent) {
        int from = Math.max(0, recent.size() - RECENT_LIMIT);
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < recent.size(); i++) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(recent.get(i));
        }
        prefs.edit().putString(RECENT_QUERIES, builder.toString()).apply();
    }

    private static final class Choice {
        final int topic;
        final String query;
        final double score;
        Choice(int topic, String query, double score) {
            this.topic = topic;
            this.query = query;
            this.score = score;
        }
    }
}
