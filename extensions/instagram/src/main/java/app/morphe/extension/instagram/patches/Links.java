/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.patches;
import android.os.Environment;
import android.net.Uri;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.settings.ActivityHook;

@SuppressWarnings("unused")
public class Links {
    private static final ThreadLocal<String> LAST_ENDPOINT = new ThreadLocal<>();
    private static final boolean DISABLE_ANALYTICS;
    private static final boolean VIEW_STORIES_ANONYMOUSLY;
    private static final boolean VIEW_LIVE_ANONYMOUSLY;
    private static final boolean DISABLE_STORIES;
    private static final boolean DISABLE_EXPLORE;
    private static final boolean DISABLE_COMMENTS;
    private static final boolean DISABLE_DISCOVER_PEOPLE;
    private static final boolean DISABLE_ADS;
    private static final boolean DISABLE_HIGHLIGHTS;

    static {
        DISABLE_ANALYTICS = Pref.disableAnalytics() && SettingsStatus.disableAnalytics;
        VIEW_STORIES_ANONYMOUSLY = Pref.viewStoriesAnonymously() && SettingsStatus.viewStoriesAnonymously;
        VIEW_LIVE_ANONYMOUSLY = Pref.viewLiveAnonymously() && SettingsStatus.viewLiveAnonymously;
        DISABLE_STORIES = Pref.disableStories() && SettingsStatus.disableStories;
        DISABLE_HIGHLIGHTS = Pref.disableHighlights() && SettingsStatus.disableHighlights;
        DISABLE_EXPLORE = Pref.disableExplore() && SettingsStatus.disableExplore;
        DISABLE_COMMENTS = Pref.disableComments() && SettingsStatus.disableComments;
        DISABLE_DISCOVER_PEOPLE = Pref.disableDiscoverPeople() && SettingsStatus.disableDiscoverPeople;
        DISABLE_ADS = Pref.disableAds() && SettingsStatus.disableAds;
    }

    public static boolean setStorySeen(boolean seenStatus){
        return VIEW_STORIES_ANONYMOUSLY ? true:seenStatus;
    }

    public static boolean openExternally(String url) {
        try {
            if(Pref.openLinksExternally()) {
                // https://l.instagram.com/?u=<actual url>&e=<tracking id>
                String actualUrl = Uri.parse(url).getQueryParameter("u");
                if (actualUrl != null) {
                    String sanitizedUrl = sanitizeUrl(actualUrl);
                    ActivityHook.openLink(sanitizedUrl);
                    return true;
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "openExternally failure", ex);
        }
        return false;
    }

    // Thanks to InstaEclipse and InstaMoon.
    public static void interceptRequest(URI uri) throws IOException {
        try {
            if (uri != null) {
                String endpoint = endpointFileName(uri);
                LAST_ENDPOINT.set(endpoint);
                logRequest(endpoint, uri);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "interceptRequest logging failed", ex);
        }
        interceptUri(uri);
    }

    public static void interceptRequestPayload(Object requestObject, URI uri) {
        try {
            String endpoint = endpointFileName(uri);
            String body = extractPayloadText(requestObject);
            File logDir = resolveLogDir();
            if (!logDir.exists() && !logDir.mkdirs()) {
                return;
            }

            File logFile = new File(logDir, endpoint + "-request.log");
            try (FileWriter writer = new FileWriter(logFile, true)) {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
                if (body == null || body.isEmpty()) {
                    writer.write("[" + timestamp + "] BODY=<not-found>");
                } else {
                    writer.write("[" + timestamp + "] BODY=" + body);
                }
                writer.write("\n");
            }
        } catch (Exception ex) {
            Logger.printException(() -> "interceptRequestPayload failed", ex);
        }
    }

    public static String consumeLastEndpoint() {
        String endpoint = LAST_ENDPOINT.get();
        if (endpoint == null || endpoint.isEmpty()) {
            return "unknown";
        }
        return endpoint;
    }

    public static void interceptUri(URI uri) throws IOException{
       boolean shouldBlockUri = false;
        try {
            if (uri != null && uri.getPath() != null) {
                String host = uri.getHost();
                String path = uri.getPath();

                if (host.contains("graph.instagram.com")
                        || host.contains("graph.facebook.com")
                        || path.contains("/logging_client_events")) {
                    shouldBlockUri = DISABLE_ANALYTICS;
                } else if (path.contains("/api/v2/media/seen/")) {
                    shouldBlockUri = VIEW_STORIES_ANONYMOUSLY;
                } else if (path.contains("/heartbeat_and_get_viewer_count/")) {
                    shouldBlockUri = VIEW_LIVE_ANONYMOUSLY;
                } else if (path.contains("/feed/reels_tray/")
                        || path.contains("feed/get_latest_reel_media/")
                        || path.contains("direct_v2/pending_inbox/?visual_message")
                        || path.contains("stories/hallpass/")
                        || path.contains("/api/v1/feed/reels_media_stream/")) {
                    shouldBlockUri = DISABLE_STORIES;
                } else if (path.contains("/discover/topical_explore")
                        || path.contains("/discover/topical_explore_stream")
                        || (host.contains("i.instagram.com") && path.contains("/fbsearch/recent_searches/"))
                        || (host.contains("i.instagram.com") && path.contains("/fbsearch/top_serp/"))) {
                    shouldBlockUri = DISABLE_EXPLORE;
                } else if (path.contains("/api/v1/media/") && path.contains("comments/")) {
                    shouldBlockUri = DISABLE_COMMENTS;
                } else if (path.contains("/discover/ayml/")) {
                    shouldBlockUri = DISABLE_DISCOVER_PEOPLE;
                } else if (path.contains("profile_ads/get_profile_ads/")
                        || path.contains("/async_ads/")
                        || path.contains("/feed/injected_reels_media/")
                        || path.contains("/api/v1/ads/graphql/")) {
                    shouldBlockUri = DISABLE_ADS;
                } else if (path.contains("/highlights_tray")) {
                    shouldBlockUri = DISABLE_HIGHLIGHTS;
                }

            }

        } catch (Exception ex) {
            Logger.printException(() -> "intercept URI failed: ", ex);
        }
        // Exception is hanndled at call.
        if(shouldBlockUri) {
            throw new IOException("Block uri");
        }
    }

    private static void logRequest(String endpointName, URI uri) {
        FileWriter writer = null;
        try {
            File logDir = resolveLogDir();
            if (!logDir.exists() && !logDir.mkdirs()) {
                return;
            }

            File logFile = new File(logDir, endpointName + "-request.log");
            writer = new FileWriter(logFile, true);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            writer.write("[" + timestamp + "] URL=" + uri.toString());
            writer.write("\n");
        } catch (Exception ex) {
            Logger.printException(() -> "logRequest failed", ex);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static File resolveLogDir() {
        List<File> candidates = new ArrayList<>();
        File externalRoot = Environment.getExternalStorageDirectory();
        candidates.add(new File(externalRoot, "Android/media/com.instagram.android/logpico"));
        candidates.add(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "logpico"));
        candidates.add(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "logpico"));
        candidates.add(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "logpico"));
        candidates.add(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "logpico"));
        candidates.add(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "logpico"));
        candidates.add(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "logpico"));

        File lastCandidate = candidates.get(0);
        for (File candidate : candidates) {
            lastCandidate = candidate;
            try {
                if (candidate.exists() || candidate.mkdirs()) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }

        File appExternal = Utils.getContext().getExternalFilesDir(null);
        if (appExternal != null) {
            File fallback = new File(appExternal, "logpico");
            if (fallback.exists() || fallback.mkdirs()) {
                return fallback;
            }
            return fallback;
        }
        return lastCandidate;
    }

    private static String endpointFileName(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            return "root";
        }
        String clean = path;
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        clean = clean.replace("/", "_");
        clean = clean.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (clean.length() > 120) {
            clean = clean.substring(0, 120);
        }
        return clean.isEmpty() ? "root" : clean;
    }

    private static String extractPayloadText(Object root) {
        try {
            IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
            return extractPayloadTextRecursive(root, visited, 0);
        } catch (Exception ex) {
            Logger.printException(() -> "extractPayloadText failed", ex);
            return null;
        }
    }

    private static String extractPayloadTextRecursive(Object value, IdentityHashMap<Object, Boolean> visited, int depth) {
        if (value == null || depth > 4) {
            return null;
        }
        if (visited.containsKey(value)) {
            return null;
        }
        visited.put(value, true);

        if (value instanceof String) {
            String s = (String) value;
            if (looksLikePayload(s)) return sanitizeBody(s);
            return null;
        }
        if (value instanceof byte[]) {
            return sanitizeBody(new String((byte[]) value, StandardCharsets.UTF_8));
        }
        if (value instanceof ByteBuffer) {
            ByteBuffer duplicate = ((ByteBuffer) value).duplicate();
            byte[] out = new byte[Math.min(duplicate.remaining(), 256 * 1024)];
            duplicate.get(out);
            return sanitizeBody(new String(out, StandardCharsets.UTF_8));
        }
        if (value instanceof Map<?, ?>) {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (count++ > 200) break;
                sb.append(String.valueOf(entry.getKey())).append('=').append(String.valueOf(entry.getValue())).append('&');
            }
            return sanitizeBody(sb.toString());
        }

        Class<?> clazz = value.getClass();
        if (clazz.isArray()) {
            int len = Array.getLength(value);
            if (len > 0 && Array.get(value, 0) instanceof Byte) {
                byte[] bytes = (byte[]) value;
                return sanitizeBody(new String(bytes, StandardCharsets.UTF_8));
            }
        }

        for (Field field : getAllFields(clazz)) {
            try {
                field.setAccessible(true);
                Object nested = field.get(value);
                String candidate = extractPayloadTextRecursive(nested, visited, depth + 1);
                if (candidate != null && !candidate.isEmpty()) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field[] declared = current.getDeclaredFields();
                for (Field field : declared) {
                    fields.add(field);
                }
            } catch (Exception ignored) {
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static boolean looksLikePayload(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty()) return false;
        return t.startsWith("{") || t.startsWith("[") || t.contains("=") || t.contains("&");
    }

    private static String sanitizeBody(String body) {
        if (body == null) return null;
        String clean = body.replace("\n", "\\n").replace("\r", "\\r");
        if (clean.length() > 256 * 1024) {
            clean = clean.substring(0, 256 * 1024) + "...<truncated>";
        }
        return clean;
    }

    public static String sanitizeUrl(String url){
        try{
            return url.replaceAll("([&?])igsh=[^&]*", "")
                    .replaceAll("([&?])utm_source=[^&]*", "")
                    .replaceAll("([&?])utm_medium=[^&]*", "")
                    .replaceAll("([&?])utm_content=[^&]*", "")
                    .replaceAll("([&?])fbclid=[^&]*", "")
                    .replaceAll("([&?])si=[^&]*", "");
        } catch (Exception e) {
            Logger.printException(() -> "sanitizeUrl failed: ", e);
        }
        return url;
    }

    public static boolean signatureCheck(Object appIdentityObject){
        try{
            Entity entity = new Entity(appIdentityObject);
            List<String> packageNames = (List) entity.getField("A02");
            if(packageNames!=null && packageNames.size() == 1){
                // Just in case the packagename is changed by the user.
                String currentAppPackageName = Utils.getContext().getPackageName();

                // The idea behind here is when the package name lists of app identity object
                // contains only one package name and that is similar to the application's package name,
                // we need to return true else false
                if(packageNames.get(0).equals(currentAppPackageName)){
                    return true;
                }
            }
        } catch (Exception e) {
            Logger.printException(() -> "Handle signature failed: ", e);
        }
        return false;
    }

}
