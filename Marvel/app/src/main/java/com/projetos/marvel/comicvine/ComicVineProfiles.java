package com.projetos.marvel.comicvine;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.projetos.marvel.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads and caches the complete public profile roster. */
public final class ComicVineProfiles {
    private static final String PREFS = "comic_vine_profiles";
    private static final String CACHE_KEY = "profiles_v1";
    private static final long CACHE_TTL = 30L * 24L * 60L * 60L * 1000L;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "comic-vine-profiles");
        thread.setDaemon(true);
        return thread;
    });

    private final ProfileSource source;
    private final ProfileCache cache;
    private final Executor worker;
    private final MainDispatcher main;
    private final Clock clock;

    public ComicVineProfiles(Context context) {
        Context app = context.getApplicationContext();
        Context safeContext = app == null ? context : app;
        Handler handler = new Handler(Looper.getMainLooper());
        source = new ApiSource(BuildConfig.COMIC_VINE_API_KEY);
        cache = new PreferencesCache(safeContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
        worker = WORKER;
        main = handler::post;
        clock = System::currentTimeMillis;
    }

    ComicVineProfiles(ProfileSource source, ProfileCache cache, Executor worker,
                      MainDispatcher main, Clock clock) {
        this.source = source;
        this.cache = cache;
        this.worker = worker;
        this.main = main;
        this.clock = clock;
    }

    /** Calls back on main with all five profiles or an empty map. */
    public void load(boolean forceRefresh, Callback callback) {
        if (callback == null) return;
        try {
            worker.execute(() -> loadInBackground(forceRefresh, callback));
        } catch (RuntimeException ignored) {
            post(callback, Collections.emptyMap(), Origin.NONE,
                    "Não foi possível carregar os perfis Comic Vine. Tente novamente.");
        }
    }

    private void loadInBackground(boolean forceRefresh, Callback callback) {
        CacheEntry cached = null;
        try {
            cached = cache.read();
        } catch (RuntimeException ignored) {
            // An unavailable cache is a cache miss.
        }
        Map<String, CharacterProfile> cachedProfiles =
                cached == null ? Collections.emptyMap() : complete(cached.profiles);
        if (!forceRefresh && !cachedProfiles.isEmpty() && cached.savedAt <= clock.now()
                && clock.now() - cached.savedAt < CACHE_TTL) {
            post(callback, cachedProfiles, Origin.CACHE, null);
            return;
        }
        try {
            Map<String, CharacterProfile> live = complete(source.load());
            if (live.isEmpty()) throw new LoadException(
                    "A Comic Vine não confirmou os cinco perfis esperados.");
            cache.write(new CacheEntry(clock.now(), live));
            post(callback, live, Origin.LIVE, null);
        } catch (LoadException exception) {
            failure(callback, cachedProfiles, exception.safeMessage);
        } catch (Exception exception) {
            failure(callback, cachedProfiles,
                    "Não foi possível acessar a Comic Vine. Confira a conexão e tente novamente.");
        }
    }

    private void failure(Callback callback, Map<String, CharacterProfile> cached, String message) {
        if (!cached.isEmpty()) post(callback, cached, Origin.CACHE, message);
        else post(callback, Collections.emptyMap(), Origin.NONE, message);
    }

    private void post(Callback callback, Map<String, CharacterProfile> profiles,
                      Origin origin, String error) {
        Map<String, CharacterProfile> result = profiles.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
        main.post(() -> callback.onProfiles(result, origin, error));
    }

    private static Map<String, CharacterProfile> complete(Map<String, CharacterProfile> input) {
        if (input == null) return Collections.emptyMap();
        Map<String, CharacterProfile> result = new LinkedHashMap<>();
        for (ComicVineProfileParser.Spec spec : ComicVineProfileParser.specs()) {
            CharacterProfile profile = input.get(spec.characterId);
            if (!ComicVineProfileParser.valid(spec.characterId, profile)) {
                return Collections.emptyMap();
            }
            result.put(spec.characterId, profile);
        }
        return input.size() == result.size()
                ? Collections.unmodifiableMap(result) : Collections.emptyMap();
    }

    public enum Origin { LIVE, CACHE, NONE }

    public interface Callback {
        void onProfiles(Map<String, CharacterProfile> profiles, Origin origin, String errorMessage);
    }

    interface ProfileSource { Map<String, CharacterProfile> load() throws Exception; }
    interface ProfileCache { CacheEntry read(); void write(CacheEntry entry); }
    interface MainDispatcher { void post(Runnable runnable); }
    interface Clock { long now(); }

    static final class CacheEntry {
        final long savedAt;
        final Map<String, CharacterProfile> profiles;
        CacheEntry(long savedAt, Map<String, CharacterProfile> profiles) {
            this.savedAt = savedAt;
            this.profiles = profiles == null ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
        }
    }

    static final class LoadException extends IOException {
        final String safeMessage;
        LoadException(String safeMessage) {
            super(safeMessage);
            this.safeMessage = safeMessage;
        }
    }

    private static final class ApiSource implements ProfileSource {
        private static final String ENDPOINT = "https://comicvine.gamespot.com/api/characters/";
        private static final String AGENT = "AvengersRoom/1.0 (Android; non-commercial fan project)";
        private static final int PAGE_SIZE = 100;
        private static final int MAX_RESULTS = 1000;
        private static final Object RATE_LOCK = new Object();
        private static long lastRequest;
        private final String key;

        ApiSource(String key) { this.key = key == null ? "" : key.trim(); }

        @Override public Map<String, CharacterProfile> load() throws Exception {
            if (key.isEmpty()) throw new LoadException(
                    "COMIC_VINE_API_KEY ausente; configure local.properties ou use o cache.");
            Map<String, CharacterProfile> result = new LinkedHashMap<>();
            for (ComicVineProfileParser.Spec spec : ComicVineProfileParser.specs()) {
                result.put(spec.characterId, loadOne(spec));
            }
            return result;
        }

        private CharacterProfile loadOne(ComicVineProfileParser.Spec spec) throws Exception {
            int total = 1;
            for (int offset = 0; offset < total && offset < MAX_RESULTS; offset += PAGE_SIZE) {
                JSONObject payload = request(spec, offset);
                if (payload.optInt("status_code", -1) != 1) throw new LoadException(
                        "A Comic Vine não confirmou os cinco perfis esperados.");
                total = Math.max(0, payload.optInt("number_of_total_results", 0));
                JSONArray results = payload.optJSONArray("results");
                CharacterProfile found = ComicVineProfileParser.find(spec.characterId, results);
                if (found != null) return found;
                if (results == null || results.length() == 0 || total <= offset + PAGE_SIZE) break;
            }
            throw new LoadException("A Comic Vine não confirmou os cinco perfis esperados.");
        }

        private JSONObject request(ComicVineProfileParser.Spec spec, int offset) throws Exception {
            throttle();
            String filter = URLEncoder.encode("name:" + spec.name, "UTF-8");
            String fields = URLEncoder.encode(
                    "id,name,real_name,description,publisher,site_detail_url", "UTF-8");
            String query = "?api_key=" + URLEncoder.encode(key, "UTF-8")
                    + "&format=json&filter=" + filter + "&field_list=" + fields
                    + "&limit=" + PAGE_SIZE + "&offset=" + offset;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(ENDPOINT + query).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent", AGENT);
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new LoadException(
                        "A Comic Vine não confirmou os cinco perfis esperados.");
                try (InputStream input = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    StringBuilder body = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                    return new JSONObject(body.toString());
                } catch (JSONException exception) {
                    throw new LoadException("A Comic Vine retornou uma resposta inválida.");
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        private static void throttle() throws InterruptedException {
            synchronized (RATE_LOCK) {
                long now = System.currentTimeMillis();
                long delay = 1000L - (now - lastRequest);
                if (lastRequest > 0 && delay > 0) Thread.sleep(delay);
                lastRequest = System.currentTimeMillis();
            }
        }
    }

    private static final class PreferencesCache implements ProfileCache {
        private final SharedPreferences preferences;
        PreferencesCache(SharedPreferences preferences) { this.preferences = preferences; }

        @Override public CacheEntry read() {
            String raw = preferences.getString(CACHE_KEY, null);
            if (raw == null) return null;
            try {
                JSONObject root = new JSONObject(raw);
                JSONObject saved = root.optJSONObject("profiles");
                if (saved == null) return null;
                Map<String, CharacterProfile> result = new LinkedHashMap<>();
                for (ComicVineProfileParser.Spec spec : ComicVineProfileParser.specs()) {
                    JSONObject item = saved.optJSONObject(spec.characterId);
                    if (item == null) return null;
                    CharacterProfile profile = new CharacterProfile(
                            item.optString("characterId", ""), item.optInt("comicVineId", -1),
                            item.optString("name", ""), item.optString("realName", ""),
                            item.optString("summary", ""), item.optString("sourceUrl", ""));
                    if (!ComicVineProfileParser.valid(spec.characterId, profile)) return null;
                    result.put(spec.characterId, profile);
                }
                if (saved.length() != result.size()) return null;
                return new CacheEntry(root.optLong("savedAt", -1L), result);
            } catch (JSONException ignored) {
                return null;
            }
        }

        @Override public void write(CacheEntry entry) {
            try {
                JSONObject root = new JSONObject();
                JSONObject saved = new JSONObject();
                root.put("savedAt", entry.savedAt);
                for (Map.Entry<String, CharacterProfile> pair : entry.profiles.entrySet()) {
                    CharacterProfile profile = pair.getValue();
                    JSONObject item = new JSONObject();
                    item.put("characterId", profile.getCharacterId());
                    item.put("comicVineId", profile.getComicVineId());
                    item.put("name", profile.getName());
                    item.put("realName", profile.getRealName());
                    item.put("summary", profile.getSummary());
                    item.put("sourceUrl", profile.getSourceUrl());
                    saved.put(pair.getKey(), item);
                }
                root.put("profiles", saved);
                preferences.edit().putString(CACHE_KEY, root.toString()).apply();
            } catch (JSONException ignored) {
                // A later call can fetch the profiles again.
            }
        }
    }
}
