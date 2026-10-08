package com.projetos.marvel.comicvine;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ComicVineProfilesTest {
    private static final long NOW = 1800000000000L;
    private static final Executor DIRECT = Runnable::run;

    @Test public void parserChecksIdPublisherIdentityAndPlainText() {
        String[][] rows = {
                {"iron_man", "1455", "Iron Man", "Anthony Edward Stark"},
                {"captain_america", "1442", "Captain America", "Steven Grant Rogers"},
                {"thor", "2268", "Thor", "Thor Odinson"},
                {"black_widow", "3200", "Black Widow", "Natalia Alianovna Romanova"},
                {"hawkeye", "1475", "Hawkeye", "Clinton Francis Barton"}
        };
        for (String[] row : rows) {
            CharacterProfile profile = ComicVineProfileParser.parse(row[0],
                    candidate(Integer.parseInt(row[1]), row[2], row[3], "31", "Marvel",
                            "<p>Public &amp; short biography.</p>"));
            assertNotNull(profile);
            assertEquals(row[0], profile.getCharacterId());
            assertTrue(profile.getSummary().contains("&"));
            assertFalse(profile.getSummary().contains("<"));
            assertEquals("https://comicvine.gamespot.com/hero/4005-" + row[1] + "/",
                    profile.getSourceUrl());
        }
        assertNull(ComicVineProfileParser.parse("iron_man",
                candidate(1455, "Iron Man", "Anthony Edward Stark", "9",
                        "Other Publisher", "Description")));
        assertNull(ComicVineProfileParser.parse("iron_man",
                candidate(1455, "Iron Man", "Anthony Edward Stark", "31", "Marvel", "")));
    }

    @Test public void freshCacheAvoidsSourceAndCallbackUsesMainDispatcher() {
        FakeSource source = new FakeSource(profiles());
        FakeCache cache = new FakeCache(new ComicVineProfiles.CacheEntry(NOW, profiles()));
        Dispatcher dispatcher = new Dispatcher();
        ComicVineProfiles loader = loader(source, cache, dispatcher);
        List<Result> results = new ArrayList<>();
        loader.load(false, (items, origin, error) -> results.add(new Result(items, origin, error)));
        assertEquals(0, source.calls);
        assertTrue(results.isEmpty());
        dispatcher.runAll();
        assertEquals(1, results.size());
        assertEquals(ComicVineProfiles.Origin.CACHE, results.get(0).origin);
        assertNull(results.get(0).error);
        assertEquals(5, results.get(0).profiles.size());
    }

    @Test public void forcedRefreshWritesCacheAndReturnsLive() {
        FakeSource source = new FakeSource(profiles());
        FakeCache cache = new FakeCache(null);
        Dispatcher dispatcher = new Dispatcher();
        ComicVineProfiles loader = loader(source, cache, dispatcher);
        List<Result> results = new ArrayList<>();
        loader.load(true, (items, origin, error) -> results.add(new Result(items, origin, error)));
        dispatcher.runAll();
        assertEquals(1, source.calls);
        assertNotNull(cache.entry);
        assertEquals(ComicVineProfiles.Origin.LIVE, results.get(0).origin);
        assertNull(results.get(0).error);
    }

    @Test public void failureReturnsFullCacheAndSafeErrorOrNone() {
        FakeSource source = new FakeSource(null);
        source.failure = new IllegalStateException("private transport detail");
        FakeCache cache = new FakeCache(new ComicVineProfiles.CacheEntry(NOW - 1, profiles()));
        Dispatcher dispatcher = new Dispatcher();
        List<Result> results = new ArrayList<>();
        loader(source, cache, dispatcher).load(true,
                (items, origin, error) -> results.add(new Result(items, origin, error)));
        dispatcher.runAll();
        assertEquals(ComicVineProfiles.Origin.CACHE, results.get(0).origin);
        assertEquals(5, results.get(0).profiles.size());
        assertNotNull(results.get(0).error);
        assertFalse(results.get(0).error.contains("private transport detail"));

        results.clear();
        cache.entry = null;
        loader(source, cache, dispatcher).load(false,
                (items, origin, error) -> results.add(new Result(items, origin, error)));
        dispatcher.runAll();
        assertEquals(ComicVineProfiles.Origin.NONE, results.get(0).origin);
        assertTrue(results.get(0).profiles.isEmpty());
        assertNotNull(results.get(0).error);
    }

    private static ComicVineProfiles loader(FakeSource source, FakeCache cache,
                                             Dispatcher dispatcher) {
        return new ComicVineProfiles(source, cache, DIRECT, dispatcher, () -> NOW);
    }

    private static Map<String, CharacterProfile> profiles() {
        String[][] rows = {
                {"iron_man", "1455", "Iron Man", "Anthony Edward Stark", "iron-man"},
                {"captain_america", "1442", "Captain America",
                        "Steven Grant Rogers", "captain-america"},
                {"thor", "2268", "Thor", "Thor Odinson", "thor"},
                {"black_widow", "3200", "Black Widow",
                        "Natalia Alianovna Romanova", "black-widow"},
                {"hawkeye", "1475", "Hawkeye", "Clinton Francis Barton", "hawkeye"}
        };
        Map<String, CharacterProfile> result = new LinkedHashMap<>();
        for (String[] row : rows) {
            result.put(row[0], new CharacterProfile(row[0], Integer.parseInt(row[1]),
                    row[2], row[3], "Public biography.",
                    "https://comicvine.gamespot.com/" + row[4] + "/4005-" + row[1] + "/"));
        }
        return result;
    }

    private static Map<String, Object> candidate(int id, String name, String realName,
                                                  String publisherId, String publisherName,
                                                  String description) {
        Map<String, Object> publisher = new HashMap<>();
        publisher.put("id", publisherId);
        publisher.put("name", publisherName);
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("real_name", realName);
        item.put("publisher", publisher);
        item.put("description", description);
        item.put("site_detail_url", "https://comicvine.gamespot.com/hero/4005-" + id
                + "/?tracking=test");
        return item;
    }

    private static final class FakeSource implements ComicVineProfiles.ProfileSource {
        final Map<String, CharacterProfile> result;
        int calls;
        Exception failure;
        FakeSource(Map<String, CharacterProfile> result) { this.result = result; }
        @Override public Map<String, CharacterProfile> load() throws Exception {
            calls++;
            if (failure != null) throw failure;
            return result;
        }
    }

    private static final class FakeCache implements ComicVineProfiles.ProfileCache {
        ComicVineProfiles.CacheEntry entry;
        FakeCache(ComicVineProfiles.CacheEntry entry) { this.entry = entry; }
        @Override public ComicVineProfiles.CacheEntry read() { return entry; }
        @Override public void write(ComicVineProfiles.CacheEntry entry) { this.entry = entry; }
    }

    private static final class Dispatcher implements ComicVineProfiles.MainDispatcher {
        final List<Runnable> queued = new ArrayList<>();
        @Override public void post(Runnable runnable) { queued.add(runnable); }
        void runAll() {
            List<Runnable> copy = new ArrayList<>(queued);
            queued.clear();
            for (Runnable runnable : copy) runnable.run();
        }
    }

    private static final class Result {
        final Map<String, CharacterProfile> profiles;
        final ComicVineProfiles.Origin origin;
        final String error;
        Result(Map<String, CharacterProfile> profiles, ComicVineProfiles.Origin origin,
               String error) {
            this.profiles = profiles;
            this.origin = origin;
            this.error = error;
        }
    }
}
