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

        // ===== 基础设置 =====
        json.addProperty("_说明_基础", "===== 基础设置 =====");

        json.addProperty("_freeActionCount说明", "答对一题获得的免答操作次数");
        json.addProperty("freeActionCount", freeActionCount);

        json.addProperty("_freezeRadius说明", "战斗答题时冻结周围怪物的范围(格)");
        json.addProperty("freezeRadius", freezeRadius);

        json.addProperty("_quizTimeLimit说明", "答题倒计时秒数, 0表示无限制");
        json.addProperty("quizTimeLimit", quizTimeLimit);

        // ===== 答错惩罚 =====
        json.addProperty("_说明_惩罚", "===== 答错惩罚设置 =====");

        json.addProperty("_wrongDamageBase说明", "答错扣血底数, 公式: min(上限, 底数^连错次数), 第1次错扣1, 第2次扣2, 第3次扣4...");
        json.addProperty("wrongDamageBase", wrongDamageBase);

        json.addProperty("_wrongDamageMax说明", "答错扣血上限, 无论错多少次最多扣这么多");
        json.addProperty("wrongDamageMax", wrongDamageMax);

        // ===== 战斗伤害 =====
        json.addProperty("_说明_战斗伤害", "===== 战斗伤害设置 =====");

        json.addProperty("_smallMobHpThreshold说明", "小怪血量分界线, 最大血量<=此值的为小怪(默认20, 即僵尸/骷髅/苦力怕)");
        json.addProperty("smallMobHpThreshold", smallMobHpThreshold);

        json.addProperty("_mediumMobHpThreshold说明", "中怪血量分界线, 最大血量<=此值的为中怪, >此值的为Boss(默认100)");
        json.addProperty("mediumMobHpThreshold", mediumMobHpThreshold);

        json.addProperty("_meleeDamageSmall说明", "近战打小怪伤害倍率, 实际伤害=怪物最大血量*此值, 默认10即秒杀");
        json.addProperty("meleeDamageSmall", meleeDamageSmall);

        json.addProperty("_meleeDamageMedium说明", "近战打中怪伤害倍率, 实际伤害=怪物最大血量*此值, 默认0.5即半血");
        json.addProperty("meleeDamageMedium", meleeDamageMedium);

        json.addProperty("_meleeDamageBoss说明", "近战打Boss固定伤害值(如末影龙/凋灵)");
        json.addProperty("meleeDamageBoss", meleeDamageBoss);

        json.addProperty("_rangedDamageRatio说明", "弓箭伤害比例, 弓箭伤害=近战伤害*此值, 默认0.5即一半");
        json.addProperty("rangedDamageRatio", rangedDamageRatio);

        // ===== 效果设置 =====
        json.addProperty("_说明_效果", "===== 答题期间效果设置 =====");

        json.addProperty("_effectDuration说明", "答题期间负面效果持续时间(tick), 20tick=1秒, 72000=1小时");
        json.addProperty("effectDuration", effectDuration);

        json.addProperty("_slownessLevel说明", "缓慢效果等级, 0=I级, 1=II级, 5=VI级(几乎不能动)");
        json.addProperty("slownessLevel", slownessLevel);

        json.addProperty("_miningFatigueLevel说明", "挖掘疲劳等级, 255=最高(完全无法挖掘)");
        json.addProperty("miningFatigueLevel", miningFatigueLevel);

        // ===== 方块分类 =====
        json.addProperty("_说明_方块分类", "===== 方块分类(关键词匹配方块ID, 包含该关键词即归入对应分类) =====");

        json.add("chopBlocks", toJsonArray(chopBlocks));
        json.add("oreBlocks", toJsonArray(oreBlocks));
        json.add("stoneBlocks", toJsonArray(stoneBlocks));
        json.add("dirtBlocks", toJsonArray(dirtBlocks));
        json.add("freeBlocks", toJsonArray(freeBlocks));
        json.add("interactBlocks", toJsonArray(interactBlocks));

        // ===== 战斗排除 =====
        json.addProperty("_说明_战斗排除", "===== 攻击以下实体时不触发答题(关键词匹配实体ID) =====");
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
