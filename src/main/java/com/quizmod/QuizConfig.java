/*
 * Quiz Survival Mod
 * Copyright (c) 2026 oaoi
 * https://github.com/ZzaiQWQ/quizsurvival
 *
 * This software is licensed under a custom non-commercial license.
 * You may NOT sell or commercially distribute this software.
 * You may NOT remove or alter this copyright notice.
 * See LICENSE file for full terms.
 */
package com.quizmod;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class QuizConfig {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("quizsurvival");
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("quizsurvival").resolve("quizsurvival_config.json");

    // ========== 基础设置 ==========
    public String questionLanguage = "zh"; // 题库语言: zh=中文, en=英文
    public int freeActionCount = 5;
    public int freezeRadius = 32;
    public int quizTimeLimit = 30; // 答题倒计时(秒), 0=无限制

    // ========== 答错惩罚 ==========
    public float wrongDamageBase = 2.0f;   // 答错扣血底数 (实际公式: base^连错次数)
    public float wrongDamageMax = 20.0f;   // 答错扣血上限

    // ========== 战斗伤害设置 ==========
    public float smallMobHpThreshold = 20.0f;   // 小怪血量分界线(<=此值为小怪)
    public float mediumMobHpThreshold = 100.0f;  // 中怪血量分界线(<=此值为中怪, >此值为Boss)
    public float meleeDamageSmall = 10.0f;        // 近战小怪伤害倍率(maxHp * 此值, 即秒杀)
    public float meleeDamageMedium = 0.5f;        // 近战中怪伤害倍率(maxHp * 此值, 即半血)
    public float meleeDamageBoss = 20.0f;         // 近战Boss固定伤害
    public float rangedDamageRatio = 0.5f;        // 弓箭伤害比例(近战伤害 * 此值)

    // ========== 效果设置 ==========
    public int effectDuration = 72000;     // 答题期间效果持续时间(tick, 72000=1小时)
    public int slownessLevel = 5;          // 缓慢效果等级(0=I级, 5=VI级)
    public int miningFatigueLevel = 255;   // 挖掘疲劳等级(255=最高)

    // ========== 方块分类关键词 ==========
    public List<String> chopBlocks = List.of("log", "wood", "stem", "hyphae", "bamboo_block");
    public List<String> oreBlocks = List.of("ore", "raw_iron_block", "raw_gold_block", "raw_copper_block", "ancient_debris", "nether_gold_ore");
    public List<String> stoneBlocks = List.of("stone", "cobblestone", "deepslate", "andesite", "granite", "diorite",
            "tuff", "calcite", "basalt", "netherrack", "obsidian", "blackstone",
            "end_stone", "sandstone", "prismarine", "purpur", "bricks", "terracotta");
    public List<String> dirtBlocks = List.of("dirt", "sand", "gravel", "clay", "mud", "soul_sand", "soul_soil",
            "mycelium", "podzol", "rooted_dirt", "coarse_dirt", "farmland",
            "grass_block", "snow_block", "ice", "packed_ice", "blue_ice");
    public List<String> freeBlocks = List.of("grass", "fern", "flower", "dandelion", "poppy", "orchid", "allium",
            "tulip", "daisy", "cornflower", "lily", "wither_rose", "sunflower",
            "lilac", "rose_bush", "peony", "torch", "snow_layer", "dead_bush",
            "mushroom", "sugar_cane", "vine", "tall_grass", "seagrass", "kelp",
            "sweet_berry", "cave_vines", "hanging_roots", "spore_blossom",
            "moss_carpet", "glow_lichen", "leaf_litter", "short_grass");
    public List<String> interactBlocks = List.of("crafting_table", "furnace", "chest", "anvil", "enchanting_table",
            "brewing_stand", "smithing_table", "stonecutter", "barrel",
            "blast_furnace", "smoker", "grindstone", "loom", "cartography_table",
            "shulker_box", "ender_chest", "trapped_chest", "hopper",
            "dropper", "dispenser", "lectern", "beacon", "jukebox",
            "note_block", "bell", "campfire", "soul_campfire", "composter",
            "respawn_anchor", "lodestone", "bed");

    // 战斗排除实体（这些实体攻击时不触发答题）
    public List<String> combatExcludeEntities = List.of("villager", "wandering_trader", "iron_golem", "snow_golem",
            "cat", "wolf", "horse", "donkey", "mule", "llama", "parrot",
            "fox", "bee", "dolphin", "ocelot", "panda", "polar_bear",
            "rabbit", "turtle", "axolotl", "frog", "allay", "camel", "sniffer",
            "chicken", "cow", "pig", "sheep", "goat", "mooshroom",
            "squid", "glow_squid", "bat", "cod", "salmon", "tropical_fish", "pufferfish",
            "strider", "armor_stand", "item_frame", "painting", "boat", "minecart");

    public static QuizConfig load() {
        QuizConfig config = new QuizConfig();
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                // 基础设置
                if (json.has("questionLanguage")) config.questionLanguage = json.get("questionLanguage").getAsString();
                if (json.has("freeActionCount")) config.freeActionCount = json.get("freeActionCount").getAsInt();
                if (json.has("freezeRadius")) config.freezeRadius = json.get("freezeRadius").getAsInt();
                if (json.has("quizTimeLimit")) config.quizTimeLimit = json.get("quizTimeLimit").getAsInt();
                // 答错惩罚
                if (json.has("wrongDamageBase")) config.wrongDamageBase = json.get("wrongDamageBase").getAsFloat();
                if (json.has("wrongDamageMax")) config.wrongDamageMax = json.get("wrongDamageMax").getAsFloat();
                // 战斗伤害
                if (json.has("smallMobHpThreshold")) config.smallMobHpThreshold = json.get("smallMobHpThreshold").getAsFloat();
                if (json.has("mediumMobHpThreshold")) config.mediumMobHpThreshold = json.get("mediumMobHpThreshold").getAsFloat();
                if (json.has("meleeDamageSmall")) config.meleeDamageSmall = json.get("meleeDamageSmall").getAsFloat();
                if (json.has("meleeDamageMedium")) config.meleeDamageMedium = json.get("meleeDamageMedium").getAsFloat();
                if (json.has("meleeDamageBoss")) config.meleeDamageBoss = json.get("meleeDamageBoss").getAsFloat();
                if (json.has("rangedDamageRatio")) config.rangedDamageRatio = json.get("rangedDamageRatio").getAsFloat();
                // 效果设置
                if (json.has("effectDuration")) config.effectDuration = json.get("effectDuration").getAsInt();
                if (json.has("slownessLevel")) config.slownessLevel = json.get("slownessLevel").getAsInt();
                if (json.has("miningFatigueLevel")) config.miningFatigueLevel = json.get("miningFatigueLevel").getAsInt();
                // 方块分类
                if (json.has("chopBlocks")) config.chopBlocks = readStringList(json, "chopBlocks");
                if (json.has("oreBlocks")) config.oreBlocks = readStringList(json, "oreBlocks");
                if (json.has("stoneBlocks")) config.stoneBlocks = readStringList(json, "stoneBlocks");
                if (json.has("dirtBlocks")) config.dirtBlocks = readStringList(json, "dirtBlocks");
                if (json.has("freeBlocks")) config.freeBlocks = readStringList(json, "freeBlocks");
                if (json.has("interactBlocks")) config.interactBlocks = readStringList(json, "interactBlocks");
                if (json.has("combatExcludeEntities")) config.combatExcludeEntities = readStringList(json, "combatExcludeEntities");
            } catch (Exception e) {
                LOGGER.warn("[答题生存] 配置文件读取失败, 使用默认值", e);
            }
        } else {
            config.save();
            LOGGER.info("[答题生存] 已生成默认配置文件: {}", CONFIG_FILE);
        }
        return config;
    }

    private static List<String> readStringList(JsonObject json, String key) {
        JsonArray arr = json.getAsJsonArray(key);
        List<String> list = new ArrayList<>();
        for (JsonElement el : arr) list.add(el.getAsString());
        return list;
    }

    public void save() {
        JsonObject json = new JsonObject();

        // ===== 基础设置 / Basic Settings =====
        json.addProperty("_section_basic", "===== 基础设置 / Basic Settings =====");

        json.addProperty("_questionLanguage", "题库语言: zh=中文, en=English / Question language: zh=Chinese, en=English. Delete quiz_questions_xx.json and /quizreload after changing");
        json.addProperty("questionLanguage", questionLanguage);

        json.addProperty("_freeActionCount", "答对获得的免答次数 / Free actions granted per correct answer");
        json.addProperty("freeActionCount", freeActionCount);

        json.addProperty("_freezeRadius", "战斗答题时冻结周围怪物的范围(格) / Mob freeze radius during combat quiz (blocks)");
        json.addProperty("freezeRadius", freezeRadius);

        json.addProperty("_quizTimeLimit", "答题倒计时秒数, 0=无限制 / Quiz countdown in seconds, 0=unlimited");
        json.addProperty("quizTimeLimit", quizTimeLimit);

        // ===== 答错惩罚 / Wrong Answer Penalty =====
        json.addProperty("_section_penalty", "===== 答错惩罚 / Wrong Answer Penalty =====");

        json.addProperty("_wrongDamageBase", "答错扣血底数, 公式: min(上限, 底数^连错次数) / Wrong answer damage base, formula: min(max, base^consecutive_errors)");
        json.addProperty("wrongDamageBase", wrongDamageBase);

        json.addProperty("_wrongDamageMax", "答错扣血上限 / Max damage per wrong answer");
        json.addProperty("wrongDamageMax", wrongDamageMax);

        // ===== 战斗伤害 / Combat Damage =====
        json.addProperty("_section_combat", "===== 战斗伤害 / Combat Damage =====");

        json.addProperty("_smallMobHpThreshold", "小怪血量分界线, <=此值为小怪 / Small mob HP threshold, maxHP<=this is small mob (default 20: zombie/skeleton/creeper)");
        json.addProperty("smallMobHpThreshold", smallMobHpThreshold);

        json.addProperty("_mediumMobHpThreshold", "中怪血量分界线, >此值为Boss / Medium mob HP threshold, maxHP>this is Boss (default 100)");
        json.addProperty("mediumMobHpThreshold", mediumMobHpThreshold);

        json.addProperty("_meleeDamageSmall", "近战小怪伤害倍率, 伤害=最大血量*此值, 10=秒杀 / Melee small mob damage multiplier, damage=maxHP*this, 10=one-shot");
        json.addProperty("meleeDamageSmall", meleeDamageSmall);

        json.addProperty("_meleeDamageMedium", "近战中怪伤害倍率, 0.5=半血 / Melee medium mob damage multiplier, 0.5=half HP");
        json.addProperty("meleeDamageMedium", meleeDamageMedium);

        json.addProperty("_meleeDamageBoss", "近战Boss固定伤害(如末影龙/凋灵) / Melee Boss fixed damage (e.g. Ender Dragon/Wither)");
        json.addProperty("meleeDamageBoss", meleeDamageBoss);

        json.addProperty("_rangedDamageRatio", "弓箭伤害=近战伤害*此值, 0.5=一半 / Ranged damage = melee damage * this, 0.5=half");
        json.addProperty("rangedDamageRatio", rangedDamageRatio);

        // ===== 效果设置 / Status Effects =====
        json.addProperty("_section_effects", "===== 答题期间效果 / Quiz Status Effects =====");

        json.addProperty("_effectDuration", "负面效果持续时间(tick), 20tick=1秒, 72000=1小时 / Effect duration in ticks, 20ticks=1sec, 72000=1hour");
        json.addProperty("effectDuration", effectDuration);

        json.addProperty("_slownessLevel", "缓慢等级, 0=I级, 5=VI级 / Slowness level, 0=I, 5=VI (nearly immobile)");
        json.addProperty("slownessLevel", slownessLevel);

        json.addProperty("_miningFatigueLevel", "挖掘疲劳等级, 255=最高 / Mining Fatigue level, 255=max (cannot mine)");
        json.addProperty("miningFatigueLevel", miningFatigueLevel);

        // ===== 方块分类 / Block Categories =====
        json.addProperty("_section_blocks", "===== 方块分类(关键词匹配方块ID) / Block Categories (keyword matching block ID) =====");

        json.add("chopBlocks", toJsonArray(chopBlocks));
        json.add("oreBlocks", toJsonArray(oreBlocks));
        json.add("stoneBlocks", toJsonArray(stoneBlocks));
        json.add("dirtBlocks", toJsonArray(dirtBlocks));
        json.add("freeBlocks", toJsonArray(freeBlocks));
        json.add("interactBlocks", toJsonArray(interactBlocks));

        // ===== 战斗排除 / Combat Exclusions =====
        json.addProperty("_section_combat_exclude", "===== 攻击以下实体不触发答题 / Attacking these entities won't trigger quiz (keyword matching entity ID) =====");
        json.add("combatExcludeEntities", toJsonArray(combatExcludeEntities));

        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE,
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("[答题生存] 配置文件保存失败", e);
        }
    }

    private static JsonArray toJsonArray(List<String> list) {
        JsonArray arr = new JsonArray();
        for (String s : list) arr.add(s);
        return arr;
    }
}
