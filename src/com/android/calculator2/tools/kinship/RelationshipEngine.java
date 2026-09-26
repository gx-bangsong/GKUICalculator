/*
 * SPDX-FileCopyrightText: 2016 Haole Zheng
 * SPDX-License-Identifier: MIT
 *
 * Chain resolution ported from https://github.com/mumuy/relationship (MIT), the engine behind
 * MIUI's calculator. A chain of relationship tokens (f, m, ob, lb, os, ls, h, w, s, d) is reduced
 * by the rewriting rules, expanded into every chain it can mean, and then looked up in the
 * relation table. See the module headers of the reference project for the data syntax.
 */

package com.android.calculator2.tools.kinship;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves relationship chains into the terms used to address the relative.
 *
 * <p>The relation table is built once per region and then kept, so the first look-up pays for the
 * expansion and every later one is a hash look-up.
 */
public final class RelationshipEngine {

    /** Longest chain the pad can produce; longer table entries can never be reached. */
    private static final int MAX_TOKENS = 10;

    private static final Pattern SEX_MARKER = Pattern.compile(",[01]");
    private static final Pattern MALE_SELECTOR = Pattern.compile("^,[w1]");
    private static final Pattern FEMALE_SELECTOR = Pattern.compile("^,[h0]");
    private static final Pattern SAME_SEX_PAIR =
            Pattern.compile(",[mwd0](&[ol\\d]+)?,w|,[hfs1](&[ol\\d]+)?,h");
    private static final Pattern MALE_ENDING =
            Pattern.compile("([fhs1](&[ol\\d]+)?|[olx]b)(&[ol\\d]+)?$");
    private static final Pattern RANK = Pattern.compile("&(\\d+)(,[hw])?$");
    private static final Pattern RANK_MARKER = Pattern.compile("&\\d+");
    private static final Pattern AGE_MARKER = Pattern.compile("&[ol]");
    private static final Pattern SIBLING_AGE = Pattern.compile("[ol](b|s)");
    private static final Pattern YOUNGER_ENDING = Pattern.compile("&o$");
    private static final Pattern OLDER_ENDING = Pattern.compile("&l$");
    private static final Pattern AGE_OR_RANK = Pattern.compile("&[ol\\d]+");
    private static final Pattern MALE_SEX_STEP = Pattern.compile(",[fhs]|,[olx]b");
    private static final Pattern FEMALE_SEX_STEP = Pattern.compile(",[mwd]|,[olx]s");
    private static final Pattern CONTAINS_PARENT_STEP = Pattern.compile("[fm]");
    private static final Pattern CONTAINS_SPOUSE_STEP = Pattern.compile("[hw],");
    private static final Pattern COLLECTIVE_HEAD =
            Pattern.compile("^[olx][bs]$|^[olx][bs],[^mf]");
    private static final Pattern SPOUSE_NORMALIZE =
            Pattern.compile(",[ol]([sb])(,[wh])?$");
    private static final Pattern SPOUSE_NORMALIZE_AGE =
            Pattern.compile("(,[sd])&[ol](,[wh])?$");
    private static final Pattern CHILD_WITH_SPOUSE = Pattern.compile("(,[sd])(,[wh])?$");

    private static final Map<String, List<String>> SEX_EXPANSION = new HashMap<>();
    static {
        SEX_EXPANSION.put("f", Arrays.asList("d", "s"));
        SEX_EXPANSION.put("m", Arrays.asList("d", "s"));
        SEX_EXPANSION.put("h", Arrays.asList("w", ""));
        SEX_EXPANSION.put("w", Arrays.asList("", "h"));
        SEX_EXPANSION.put("s", Arrays.asList("m", "f"));
        SEX_EXPANSION.put("d", Arrays.asList("m", "f"));
        SEX_EXPANSION.put("lb", Arrays.asList("os", "ob"));
        SEX_EXPANSION.put("ob", Arrays.asList("ls", "lb"));
        SEX_EXPANSION.put("xb", Arrays.asList("xs", "xb"));
        SEX_EXPANSION.put("ls", Arrays.asList("os", "ob"));
        SEX_EXPANSION.put("os", Arrays.asList("ls", "lb"));
        SEX_EXPANSION.put("xs", Arrays.asList("xs", "xb"));
    }

    private static final Map<String, List<String>> MATE = new HashMap<>();
    static {
        MATE.put("w", Arrays.asList("妻", "内", "岳", "岳家", "丈人"));
        MATE.put("h", Arrays.asList("夫", "外", "公", "婆家", "婆婆"));
    }

    private static final Map<String, Integer> GENERATION = new HashMap<>();
    static {
        GENERATION.put("f", 1);
        GENERATION.put("m", 1);
        GENERATION.put("s", -1);
        GENERATION.put("d", -1);
    }

    private static final String[] RANK_NAMES = {"零", "一", "二", "三", "四", "五", "六", "七", "八",
            "九", "十"};

    private static final Map<String, RelationshipEngine> CACHE = new HashMap<>();

    @NonNull
    private final RelationshipData mData;
    @NonNull
    private final Map<String, String> mTerms;

    private RelationshipEngine(@NonNull RelationshipData data) {
        mData = data;
        mTerms = buildTable(null);
    }

    private RelationshipEngine(@NonNull RelationshipData data, @NonNull String locale) {
        mData = data;
        mTerms = buildTable(locale);
    }

    /** Engine for the common wording, or {@code null} when the data cannot be read. */
    @Nullable
    public static RelationshipEngine get(@NonNull RelationshipData data) {
        return get(data, null);
    }

    /** Engine for one region, or the common wording when {@code locale} is null or unknown. */
    @Nullable
    public static RelationshipEngine get(@NonNull RelationshipData data,
            @Nullable String locale) {
        final String key = locale == null ? "" : locale;
        synchronized (CACHE) {
            final RelationshipEngine cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            try {
                final RelationshipEngine engine = locale == null
                        ? new RelationshipEngine(data)
                        : new RelationshipEngine(data, locale);
                CACHE.put(key, engine);
                return engine;
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    /**
     * Resolve a chain of tokens.
     *
     * @param tokens  relationship steps, e.g. {@code ["f", "ob", "s"]} for 爸爸的哥哥的儿子
     * @param reverse {@code true} to answer how that person addresses the user
     * @return every applicable term, in the reference's own order; empty when the chain names
     *         nobody (爸爸的丈夫, for example)
     */
    @NonNull
    public List<String> resolve(@NonNull List<String> tokens, boolean reverse) {
        final List<String> result = new ArrayList<>();
        if (tokens.isEmpty()) {
            return result;
        }
        final String selector = join(tokens);
        final int sex = sexOfChain(selector);
        final List<String> ids = new ArrayList<>();
        for (String first : selector2id(selector, sex)) {
            ids.addAll(selector2id("," + first, sex));
        }
        for (String id : ids) {
            final List<String> temps;
            int fallbackSex = -1;
            if (reverse) {
                temps = reverseId(id, sex);
                fallbackSex = MALE_ENDING.matcher(id).find() ? 1 : 0;
            } else {
                temps = new ArrayList<>();
                temps.add(id);
            }
            for (String temp : temps) {
                List<String> items = getItemsById(temp);
                if (items.isEmpty() && fallbackSex > -1) {
                    items = getItemsById(fallbackSex + "," + temp);
                }
                for (String item : items) {
                    if (!result.contains(item)) {
                        result.add(item);
                    }
                }
            }
        }
        return result;
    }

    // ---- Table -----------------------------------------------------------------------------

    @NonNull
    private Map<String, String> buildTable(@Nullable String locale) {
        final Map<String, List<String>> table = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : mData.multiple.entrySet()) {
            table.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        final Map<String, Map<String, List<String>>> prefixMap = new HashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> tag : mData.prefix.entrySet()) {
            final Map<String, List<String>> expanded = new HashMap<>();
            for (Map.Entry<String, List<String>> selector : tag.getValue().entrySet()) {
                for (String each : expandSelector(selector.getKey())) {
                    expanded.put(each, selector.getValue());
                }
            }
            prefixMap.put(tag.getKey(), expanded);
        }
        final Map<String, List<String>> branchMap = new HashMap<>();
        for (Map.Entry<String, List<String>> selector : mData.branch.entrySet()) {
            for (String each : expandSelector(selector.getKey())) {
                branchMap.put(each, selector.getValue());
            }
        }
        for (Map.Entry<String, List<String>> entry : branchMap.entrySet()) {
            final String tag = placeholder(entry.getKey());
            if (tag == null) {
                continue;
            }
            final Map<String, List<String>> prefixes = prefixMap.get(tag);
            if (prefixes == null) {
                continue;
            }
            for (Map.Entry<String, List<String>> prefix : prefixes.entrySet()) {
                final String newKey = entry.getKey().replace(tag, prefix.getKey());
                if (newKey.contains("h,h") || newKey.contains("w,w")
                        || newKey.contains("w,h") || newKey.contains("h,w")) {
                    continue;
                }
                final List<String> names = new ArrayList<>();
                for (String prefixName : prefix.getValue()) {
                    for (String name : entry.getValue()) {
                        names.add(name.contains("?") ? name.replace("?", prefixName)
                                : prefixName + name);
                    }
                }
                final List<String> existing = firstNonNull(table.get(newKey),
                        mData.multiple.get(newKey));
                table.put(newKey, concat(names, existing));
            }
        }
        for (Map.Entry<String, List<String>> entry : mData.main.entrySet()) {
            table.put(entry.getKey(), concat(entry.getValue(), table.get(entry.getKey())));
        }
        addSpouseKeys(table);
        if (locale != null) {
            final Map<String, List<String>> overrides = mData.locales.get(locale);
            if (overrides != null) {
                for (Map.Entry<String, List<String>> entry : overrides.entrySet()) {
                    table.put(entry.getKey(),
                            concat(entry.getValue(), table.get(entry.getKey())));
                }
            }
        }
        final Map<String, String> terms = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : table.entrySet()) {
            final List<String> names = entry.getValue();
            if (names.isEmpty()) {
                continue;
            }
            if (countTokens(entry.getKey()) <= MAX_TOKENS) {
                terms.put(entry.getKey(), names.get(0));
            }
        }
        return terms;
    }

    /** Adds 岳X / 公X style keys for the in-laws of blood relatives. */
    private void addSpouseKeys(@NonNull Map<String, List<String>> table) {
        final java.util.Set<String> known = new java.util.HashSet<>();
        for (List<String> names : table.values()) {
            known.addAll(names);
        }
        for (String key : new ArrayList<>(table.keySet())) {
            final boolean blood = key.startsWith("f") || key.startsWith("m")
                    || COLLECTIVE_HEAD.matcher(key).find();
            if (!blood) {
                continue;
            }
            for (Map.Entry<String, List<String>> mate : MATE.entrySet()) {
                final String newKey = mate.getKey() + "," + key;
                if (CONTAINS_PARENT_STEP.matcher(key).find()) {
                    String normalized = SPOUSE_NORMALIZE.matcher(newKey)
                            .replaceAll(",x$1$2");
                    normalized = SPOUSE_NORMALIZE_AGE.matcher(normalized).replaceAll("$1$2");
                    if (!normalized.equals(newKey) && table.containsKey(normalized)) {
                        continue;
                    }
                }
                if (!table.containsKey(newKey)) {
                    table.put(newKey, new ArrayList<String>());
                }
                final List<String> target = table.get(newKey);
                for (String prefix : mate.getValue()) {
                    for (String name : table.get(key)) {
                        final String newName = prefix + name;
                        if (!known.contains(newName)) {
                            target.add(newName);
                        }
                    }
                }
            }
        }
    }

    // ---- Selectors -------------------------------------------------------------------------

    /** A chain that opens with 丈夫 or 妻子 tells us the user's own sex. */
    private static int sexOfChain(@NonNull String chain) {
        final String selector = chain.startsWith(",") ? chain : "," + chain;
        if (MALE_SELECTOR.matcher(selector).find()) {
            return 1;
        }
        if (FEMALE_SELECTOR.matcher(selector).find()) {
            return 0;
        }
        return -1;
    }

    @NonNull
    private List<String> selector2id(@NonNull String selector, int sex) {
        String current = selector.startsWith(",") ? selector : "," + selector;
        if (sex < 0) {
            if (MALE_SELECTOR.matcher(current).find()) {
                sex = 1;
            } else if (FEMALE_SELECTOR.matcher(current).find()) {
                sex = 0;
            }
        } else if ((sex == 1 && FEMALE_SELECTOR.matcher(current).find())
                || (sex == 0 && MALE_SELECTOR.matcher(current).find())) {
            return new ArrayList<>();
        }
        if (sex > -1 && !current.contains(",1") && !current.contains(",0")) {
            current = "," + sex + current;
        }
        if (SAME_SEX_PAIR.matcher(current).find()) {
            return new ArrayList<>();
        }
        final List<String> result = new ArrayList<>();
        for (String each : expandSelector(current)) {
            final String id = SEX_MARKER.matcher(each).replaceAll("");
            result.add(id.startsWith(",") ? id.substring(1) : id);
        }
        return filterId(result);
    }

    /** Applies the rewriting rules until the chain settles, splitting on '#' into alternatives. */
    @NonNull
    private List<String> expandSelector(@NonNull String selector) {
        final List<String> result = new ArrayList<>();
        expandInto(selector, result, new java.util.HashSet<String>());
        return result;
    }

    private void expandInto(@NonNull String selector, @NonNull List<String> out,
            @NonNull java.util.Set<String> seen) {
        if (!seen.add(selector)) {
            return;
        }
        String current = selector;
        while (true) {
            final String before = current;
            for (RelationshipData.Rule rule : mData.rules) {
                final Pattern pattern = Pattern.compile(rule.exp);
                final Matcher matcher = pattern.matcher(current);
                current = rule.global
                        ? matcher.replaceAll(rule.str)
                        : matcher.replaceFirst(rule.str);
                if (current.contains("#")) {
                    for (String part : current.split("#")) {
                        expandInto(part, out, seen);
                    }
                    return;
                }
            }
            if (current.equals(before)) {
                break;
            }
        }
        if (SAME_SEX_PAIR.matcher(current).find()) {
            return;
        }
        if (!out.contains(current)) {
            out.add(current);
        }
    }

    /** Drops chains that are only a less specific spelling of another one in the list. */
    @NonNull
    private static List<String> filterId(@NonNull List<String> items) {
        final List<String> already = new ArrayList<>();
        for (String item : items) {
            if (item.equals(normalize(item))) {
                already.add(item);
            }
        }
        final List<String> out = new ArrayList<>();
        final java.util.Set<String> seen = new java.util.HashSet<>();
        for (String item : items) {
            final String normalized = normalize(item);
            final boolean keep = already.contains(item)
                    || (!item.equals(normalized) && !already.contains(normalized));
            if (keep && seen.add(item)) {
                out.add(item);
            }
        }
        return out;
    }

    @NonNull
    private static String normalize(@NonNull String item) {
        final String collective = item.replaceAll("[ol](?=[s|b])", "x");
        return AGE_MARKER.matcher(collective).replaceFirst("");
    }

    // ---- Identifiers -----------------------------------------------------------------------

    @NonNull
    private List<String> reverseId(@NonNull String id, int sex) {
        if (id.isEmpty()) {
            final List<String> empty = new ArrayList<>();
            empty.add("");
            return empty;
        }
        String age = "";
        if (YOUNGER_ENDING.matcher(id).find()) {
            age = "&l";
        } else if (OLDER_ENDING.matcher(id).find()) {
            age = "&o";
        }
        final String stripped = AGE_OR_RANK.matcher(id).replaceAll("");
        if (sex < 0) {
            if (stripped.startsWith("w")) {
                sex = 1;
            } else if (stripped.startsWith("h")) {
                sex = 0;
            }
        }
        final List<String> out = new ArrayList<>();
        if (sex < 0) {
            out.add(reverse(stripped, 1, age));
            out.add(reverse(stripped, 0, age));
        } else {
            out.add(reverse(stripped, sex, age));
        }
        return out;
    }

    @NonNull
    private static String reverse(@NonNull String id, int sex, @NonNull String age) {
        String sid = MALE_SEX_STEP.matcher("," + sex + "," + id).replaceAll(",1");
        sid = FEMALE_SEX_STEP.matcher(sid).replaceAll(",0");
        final String[] steps = sid.substring(0, Math.max(0, sid.length() - 2)).split(",");
        final String[] tokens = id.split(",");
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            final List<String> options = SEX_EXPANSION.get(tokens[tokens.length - 1 - i]);
            final int index = steps.length - 1 - i;
            final String sexStep = index >= 0 && index < steps.length ? steps[index] : "";
            final int pick = "1".equals(sexStep) ? 1 : 0;
            out.append(options == null ? "" : options.get(pick));
        }
        return generationOf(out.toString()) == 0 ? out.append(age).toString() : out.toString();
    }

    private static int generationOf(@NonNull String id) {
        int generation = 0;
        for (String token : id.split(",")) {
            final Integer value = GENERATION.get(AGE_OR_RANK.matcher(token).replaceAll(""));
            if (value != null) {
                generation += value;
            }
        }
        return generation;
    }

    // ---- Look-up ---------------------------------------------------------------------------

    @NonNull
    private List<String> getItemsById(@NonNull String id) {
        List<String> items = new ArrayList<>();
        final Matcher rank = RANK.matcher(id);
        final int number = rank.find() ? Integer.parseInt(rank.group(1)) : 0;
        String key = id;
        if (number > 0) {
            key = RANK_MARKER.matcher(key).replaceAll("");
            final List<String> ranked = mData.sort.get(key);
            if (ranked != null && !ranked.isEmpty()) {
                items.add(ranked.get(0).replace("几", rankName(number)));
            } else {
                String name = mTerms.get(key);
                if (name == null) {
                    name = mTerms.get(key.replaceAll("[ol](?=[s|b])", "x"));
                }
                if (name != null && generationOf(key) < 4
                        && !CONTAINS_SPOUSE_STEP.matcher(key).find()) {
                    items.add(name.startsWith("大") || name.startsWith("小")
                            ? rankName(number) + name.substring(1)
                            : rankName(number) + name);
                }
            }
        }
        if (items.isEmpty()) {
            items = lookup(RANK_MARKER.matcher(key).replaceAll(""));
        }
        if (items.isEmpty()) {
            String narrowed = AGE_MARKER.matcher(key).replaceAll("");
            items = lookup(narrowed);
            if (items.isEmpty()) {
                narrowed = SIBLING_AGE.matcher(narrowed).replaceAll("x$1");
                items = lookup(narrowed);
            }
            if (items.isEmpty()) {
                final List<String> both = new ArrayList<>();
                both.addAll(lookup(narrowed.replace("x", "o")));
                both.addAll(lookup(narrowed.replace("x", "l")));
                items = both;
            }
        }
        return items;
    }

    @NonNull
    private List<String> lookup(@NonNull String key) {
        final String older = CHILD_WITH_SPOUSE.matcher(key).replaceAll("$1&o$2");
        final String younger = CHILD_WITH_SPOUSE.matcher(key).replaceAll("$1&l$2");
        final List<String> ids = new ArrayList<>();
        if (mTerms.containsKey(older) && mTerms.containsKey(younger)) {
            ids.add(older);
            ids.add(younger);
        } else if (mTerms.containsKey(key)) {
            ids.add(key);
        }
        final List<String> out = new ArrayList<>();
        for (String id : filterId(ids)) {
            final String term = mTerms.get(id);
            if (term != null) {
                out.add(term);
            }
        }
        return out;
    }

    // ---- Helpers ---------------------------------------------------------------------------

    @Nullable
    private static String placeholder(@NonNull String key) {
        final int start = key.indexOf('{');
        final int end = key.indexOf('}');
        if (start < 0 || end < start) {
            return null;
        }
        return key.substring(start, end + 1);
    }

    private static int countTokens(@NonNull String id) {
        return id.isEmpty() ? 0 : id.split(",").length;
    }

    @NonNull
    private static String join(@NonNull List<String> tokens) {
        final StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(token);
        }
        return sb.toString();
    }

    @NonNull
    private static List<String> firstNonNull(@Nullable List<String> first,
            @Nullable List<String> second) {
        if (first != null) {
            return first;
        }
        return second == null ? new ArrayList<String>() : second;
    }

    @NonNull
    private static List<String> concat(@NonNull List<String> first,
            @Nullable List<String> second) {
        final List<String> out = new ArrayList<>(first);
        if (second != null) {
            out.addAll(second);
        }
        return out;
    }

    @NonNull
    private static String rankName(int number) {
        return number >= 0 && number < RANK_NAMES.length
                ? RANK_NAMES[number] : String.valueOf(number);
    }
}
