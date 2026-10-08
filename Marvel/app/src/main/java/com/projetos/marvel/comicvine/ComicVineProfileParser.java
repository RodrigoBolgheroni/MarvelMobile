package com.projetos.marvel.comicvine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ComicVineProfileParser {
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern SCRIPT = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1\\s*>");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]{2,8});");
    private static final List<Spec> SPECS;

    static {
        List<Spec> items = new ArrayList<>();
        items.add(new Spec("iron_man", 1455, "Iron Man", "Anthony Edward Stark", "iron-man"));
        items.add(new Spec("captain_america", 1442, "Captain America",
                "Steven Grant Rogers", "captain-america"));
        items.add(new Spec("thor", 2268, "Thor", "Thor Odinson", "thor"));
        items.add(new Spec("black_widow", 3200, "Black Widow",
                "Natalia Alianovna Romanova", "black-widow"));
        items.add(new Spec("hawkeye", 1475, "Hawkeye",
                "Clinton Francis Barton", "hawkeye"));
        SPECS = Collections.unmodifiableList(items);
    }

    private ComicVineProfileParser() {}

    static List<Spec> specs() { return SPECS; }

    static Spec spec(String characterId) {
        for (Spec spec : SPECS) {
            if (spec.characterId.equals(characterId)) return spec;
        }
        return null;
    }

    static boolean isKnown(String characterId) { return spec(characterId) != null; }

    static CharacterProfile find(String characterId, JSONArray results) throws IOException {
        Spec expected = spec(characterId);
        if (expected == null || results == null) throw new IOException("Invalid profile result.");
        for (int i = 0; i < results.length(); i++) {
            JSONObject json = results.optJSONObject(i);
            if (json == null || json.optInt("id", -1) != expected.comicVineId) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", json.opt("id"));
            item.put("name", json.opt("name"));
            item.put("real_name", json.opt("real_name"));
            item.put("description", json.opt("description"));
            item.put("site_detail_url", json.opt("site_detail_url"));
            JSONObject publisher = json.optJSONObject("publisher");
            if (publisher != null) {
                Map<String, Object> company = new LinkedHashMap<>();
                company.put("id", publisher.opt("id"));
                company.put("name", publisher.opt("name"));
                item.put("publisher", company);
            }
            CharacterProfile profile = parse(characterId, item);
            if (profile == null) throw new IOException("Profile identity did not validate.");
            return profile;
        }
        return null;
    }

    static CharacterProfile parse(String characterId, Map<String, ?> item) {
        Spec expected = spec(characterId);
        if (expected == null || item == null
                || intValue(item.get("id")) != expected.comicVineId
                || !expected.name.equals(text(item.get("name")))
                || !expected.realName.equals(text(item.get("real_name")))) return null;
        Object rawPublisher = item.get("publisher");
        if (!(rawPublisher instanceof Map)) return null;
        Map<?, ?> publisher = (Map<?, ?>) rawPublisher;
        if (!"31".equals(text(publisher.get("id")))
                || !"Marvel".equals(text(publisher.get("name")))) return null;
        String summary = plainText(text(item.get("description")));
        if (summary.isEmpty()) return null;
        String url = sourceUrl(text(item.get("site_detail_url")), expected);
        return new CharacterProfile(expected.characterId, expected.comicVineId,
                text(item.get("name")), text(item.get("real_name")), summary, url);
    }

    static boolean valid(String characterId, CharacterProfile profile) {
        Spec expected = spec(characterId);
        if (expected == null || profile == null
                || !expected.characterId.equals(profile.getCharacterId())
                || expected.comicVineId != profile.getComicVineId()
                || !expected.name.equals(profile.getName())
                || !expected.realName.equals(profile.getRealName())
                || profile.getSummary().isEmpty()) return false;
        try {
            URI uri = new URI(profile.getSourceUrl());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "comicvine.gamespot.com".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null;
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    static String plainText(String html) {
        if (html == null) return "";
        String value = SCRIPT.matcher(html).replaceAll(" ");
        value = value.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|li|h[1-6])\\s*>", "\n");
        value = TAGS.matcher(value).replaceAll(" ");
        value = decode(value).replace('<', '‹').replace('>', '›');
        return value.replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n *", "\n").replaceAll("\\n{2,}", "\n").trim();
    }

    private static String decode(String input) {
        Matcher matcher = ENTITY.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(output,
                    Matcher.quoteReplacement(decodeEntity(matcher.group(1))));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String decodeEntity(String entity) {
        String name = entity.toLowerCase(Locale.ROOT);
        if ("amp".equals(name)) return "&";
        if ("quot".equals(name)) return "\"";
        if ("apos".equals(name) || "#39".equals(name)) return "'";
        if ("lt".equals(name)) return "‹";
        if ("gt".equals(name)) return "›";
        if ("nbsp".equals(name)) return " ";
        if ("ndash".equals(name)) return "–";
        if ("mdash".equals(name)) return "—";
        if ("hellip".equals(name)) return "…";
        if ("rsquo".equals(name)) return "’";
        if ("lsquo".equals(name)) return "‘";
        if ("rdquo".equals(name)) return "”";
        if ("ldquo".equals(name)) return "“";
        if (entity.startsWith("#")) {
            try {
                int codePoint = entity.startsWith("#x") || entity.startsWith("#X")
                        ? Integer.parseInt(entity.substring(2), 16)
                        : Integer.parseInt(entity.substring(1));
                if (Character.isValidCodePoint(codePoint)
                        && (codePoint < 0xD800 || codePoint > 0xDFFF)) {
                    return new String(Character.toChars(codePoint));
                }
            } catch (NumberFormatException ignored) {
                return "";
            }
        }
        return "";
    }

    private static String sourceUrl(String raw, Spec expected) {
        try {
            URI uri = new URI(raw);
            String host = uri.getHost();
            String scheme = uri.getScheme();
            String path = uri.getRawPath();
            if ("comicvine.gamespot.com".equalsIgnoreCase(host) && path != null
                    && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                return "https://comicvine.gamespot.com" + path;
            }
        } catch (URISyntaxException ignored) {
            // Use a canonical Comic Vine profile URL for this validated ID.
        }
        return "https://comicvine.gamespot.com/" + expected.slug
                + "/4005-" + expected.comicVineId + "/";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intValue(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static final class Spec {
        final String characterId;
        final int comicVineId;
        final String name;
        final String realName;
        final String slug;
        Spec(String characterId, int comicVineId, String name, String realName, String slug) {
            this.characterId = characterId;
            this.comicVineId = comicVineId;
            this.name = name;
            this.realName = realName;
            this.slug = slug;
        }
    }
}
