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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

@SuppressWarnings("unused")
public class ResponseLogger {
    private static final int MAX_LOG_BYTES = 512 * 1024;
    private static final String HOOK_PRIMARY = "primary_fasterxml";
    private static final String HOOK_BY_NAME = "fallback_createparser_name";
    private static final String HOOK_BY_RETURN = "fallback_returntype";

    public static InputStream saveInputStream(InputStream inputStream) {
        return saveWithHook("legacy", inputStream);
    }

    public static InputStream saveInputStreamPrimary(InputStream inputStream) {
        return saveWithHook(HOOK_PRIMARY, inputStream);
    }

    public static InputStream saveInputStreamByName(InputStream inputStream) {
        return saveWithHook(HOOK_BY_NAME, inputStream);
    }

    public static InputStream saveInputStreamByReturnType(InputStream inputStream) {
        return saveWithHook(HOOK_BY_RETURN, inputStream);
    }

    public static byte[] saveByteArray(byte[] body) {
        return saveByteArrayWithHook("fallback_bytearray", body);
    }

    public static String saveString(String body) {
        return saveStringWithHook("fallback_string", body);
    }

    private static InputStream saveWithHook(String hookSource, InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }
        try {
            byte[] data = readAllBytes(inputStream);
            String endpoint = Links.consumeLastEndpoint();
            logHookPing(hookSource, endpoint, data.length);
            logResponse(hookSource, endpoint, data);
            return new ByteArrayInputStream(data);
        } catch (Exception ex) {
            Logger.printException(() -> "ResponseLogger.saveInputStream failed", ex);
            return inputStream;
        }
    }

    private static byte[] saveByteArrayWithHook(String hookSource, byte[] body) {
        if (body == null) {
            return null;
        }
        try {
            String endpoint = Links.consumeLastEndpoint();
            logHookPing(hookSource, endpoint, body.length);
            logResponse(hookSource, endpoint, body);
        } catch (Exception ex) {
            Logger.printException(() -> "ResponseLogger.saveByteArray failed", ex);
        }
        return body;
    }

    private static String saveStringWithHook(String hookSource, String body) {
        if (body == null) {
            return null;
        }
        try {
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            String endpoint = Links.consumeLastEndpoint();
            logHookPing(hookSource, endpoint, data.length);
            logResponse(hookSource, endpoint, data);
        } catch (Exception ex) {
            Logger.printException(() -> "ResponseLogger.saveString failed", ex);
        }
        return body;
    }

    private static void logHookPing(String hookSource, String endpoint, int totalBytes) {
        FileWriter writer = null;
        try {
            File logDir = resolveLogDir();
            if (!logDir.exists() && !logDir.mkdirs()) {
                return;
            }
            File trace = new File(logDir, "_response_hook_trace.log");
            writer = new FileWriter(trace, true);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            writer.write("[" + timestamp + "] HOOK=" + hookSource + " ENDPOINT=" + endpoint + " BYTES=" + totalBytes);
            writer.write("\n");
        } catch (Exception ignored) {
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void logResponse(String hookSource, String endpoint, byte[] data) {
        FileWriter writer = null;
        try {
            File logDir = resolveLogDir();
            if (!logDir.exists() && !logDir.mkdirs()) {
                return;
            }

            String safeEndpoint = sanitizeName(endpoint);
            File logFile = new File(logDir, safeEndpoint + "-response.log");
            writer = new FileWriter(logFile, true);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            int totalBytes = data.length;
            int writeBytes = Math.min(totalBytes, MAX_LOG_BYTES);
            String text = new String(data, 0, writeBytes, StandardCharsets.UTF_8).replace("\n", "\\n");

            writer.write("[" + timestamp + "] HOOK=" + hookSource + " BYTES=" + totalBytes + " PREVIEW_UTF8=" + text);
            writer.write("\n");

            if (totalBytes > MAX_LOG_BYTES) {
                File rawFile = new File(logDir, safeEndpoint + "-response.raw");
                try (FileOutputStream fos = new FileOutputStream(rawFile, true)) {
                    fos.write(data);
                    fos.write('\n');
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "ResponseLogger.logResponse failed", ex);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
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

    private static String sanitizeName(String value) {
        if (value == null || value.isEmpty()) {
            return "unknown";
        }
        String clean = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (clean.length() > 120) {
            clean = clean.substring(0, 120);
        }
        return clean.isEmpty() ? "unknown" : clean;
    }
}
