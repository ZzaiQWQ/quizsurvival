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

public class QuizManager {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("quizsurvival");
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("quizsurvival");

    private static Path getQuestionsFile() {
        String lang = QuizMod.config != null ? QuizMod.config.questionLanguage : "zh";
        return CONFIG_DIR.resolve("quiz_questions_" + lang + ".json");
    }

    private final List<Question> questions = new ArrayList<>();
    private final Map<UUID, Integer> activeQuizzes = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Integer> correctAnswers = new java.util.concurrent.ConcurrentHashMap<>();
    private final Random random = new Random();
    // 每个玩家的题目队列（不重复，用完重新洗牌）
    private final Map<UUID, List<Question>> playerQueues = new java.util.concurrent.ConcurrentHashMap<>();

    public QuizManager() {
        Path file = getQuestionsFile();
        if (Files.exists(file)) {
            loadFromJson(file);
        } else {
            loadQuestions(); // 硬编码默认题库
            saveToJson(file);   // 首次运行生成JSON
        }
    }

    private void loadFromJson(Path file) {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (JsonElement el : JsonParser.parseReader(r).getAsJsonArray()) {
                JsonObject o = el.getAsJsonObject();
                List<String> opts = new ArrayList<>();
                o.getAsJsonArray("options").forEach(e -> opts.add(e.getAsString()));
                questions.add(new Question(o.get("question").getAsString(), opts, o.get("answer").getAsInt()));
            }
            LOGGER.info("[答题生存] 从JSON加载了 {} 道题目", questions.size());
        } catch (Exception e) {
            LOGGER.error("[答题生存] JSON题库读取失败，使用默认题库", e);
            loadQuestions();
        }
    }

    private void saveToJson(Path file) {
        JsonArray arr = new JsonArray();
        for (Question q : questions) {
            JsonObject o = new JsonObject();
            o.addProperty("question", q.text());
            JsonArray opts = new JsonArray();
            q.options().forEach(opts::add);
            o.add("options", opts);
            o.addProperty("answer", q.correctIndex());
            arr.add(o);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(arr), StandardCharsets.UTF_8);
            LOGGER.info("[答题生存] 已生成题库JSON: {}", file);
        } catch (Exception e) {
            LOGGER.error("[答题生存] 题库JSON保存失败", e);
        }
    }

    /** 获取玩家的下一道不重复题目，所有题用完后重新洗牌 */
    public Question getRandomQuestion(UUID player) {
        List<Question> queue = playerQueues.get(player);
        if (queue == null || queue.isEmpty()) {
            queue = new ArrayList<>(questions);
            Collections.shuffle(queue, random);
            playerQueues.put(player, queue);
        }
        return queue.remove(queue.size() - 1);
    }

    public int startQuiz(UUID player, Question q) {
        int id = random.nextInt(999999);
        activeQuizzes.put(player, id);
        correctAnswers.put(player, q.correctIndex());
        return id;
    }

    public boolean checkAnswer(UUID player, int quizId, int answer) {
        Integer active = activeQuizzes.get(player);
        if (active == null || active != quizId) return false;
        boolean correct = correctAnswers.getOrDefault(player, -1) == answer;
        activeQuizzes.remove(player);
        correctAnswers.remove(player);
        return correct;
    }

    public boolean hasActiveQuiz(UUID player) {
        return activeQuizzes.containsKey(player);
    }

    // 剩余免答次数 (按类型分)
    private final Map<UUID, Map<String, Integer>> remainingActions = new java.util.concurrent.ConcurrentHashMap<>();

    public boolean hasRemainingActions(UUID player, String type) {
        var actions = remainingActions.getOrDefault(player, Map.of());
        Integer count = actions.get(type);
        return count != null && count > 0;
    }

    public void useAction(UUID player, String type) {
        var actions = remainingActions.get(player);
        if (actions != null && actions.containsKey(type)) {
            actions.put(type, actions.get(type) - 1);
        }
    }

    public void grantActions(UUID player, String type, int count) {
        remainingActions.computeIfAbsent(player, k -> new HashMap<>())
                .put(type, count);
    }

    /** 清除玩家所有答题状态(死亡/重生时调用) */
    public void clearPlayer(UUID player) {
        activeQuizzes.remove(player);
        correctAnswers.remove(player);
        remainingActions.remove(player);
    }

    public int getQuestionCount() { return questions.size(); }

    public Map<String, Integer> getActionsMap(UUID player) {
        return remainingActions.getOrDefault(player, Map.of());
    }

    // === 答题统计 ===
    // stats[0]=总答题数, stats[1]=正确数, stats[2]=错误数
    private final Map<UUID, int[]> playerStats = new java.util.concurrent.ConcurrentHashMap<>();

    public void recordAnswer(UUID player, boolean correct) {
        int[] stats = playerStats.computeIfAbsent(player, k -> new int[3]);
        stats[0]++;
        if (correct) stats[1]++; else stats[2]++;
    }

    public int[] getStats(UUID player) {
        return playerStats.getOrDefault(player, new int[3]);
    }

    // === 热重载 ===
    public int reload() {
        questions.clear();
        playerQueues.clear();
        Path file = getQuestionsFile();
        if (Files.exists(file)) {
            loadFromJson(file);
        } else {
            loadQuestions();
            saveToJson(file);
        }
        return questions.size();
    }

    private void loadQuestions() {
        String lang = QuizMod.config != null ? QuizMod.config.questionLanguage : "zh";
        if ("en".equals(lang)) {
            loadQuestionsEn();
        } else {
            loadQuestionsZh();
        }
    }

    private void loadQuestionsZh() {
        q("钻石矿最低可以在第几层生成？", List.of("-64层","0层","16层","32层"), 0);
        q("至少需要什么镐子才能挖铁矿？", List.of("木镐","石镐","铁镐","钻石镐"), 1);
        q("一组方块最多堆叠多少个？", List.of("16个","32个","64个","128个"), 2);
        q("末影龙在哪个维度？", List.of("主世界","下界","末地","深暗之域"), 2);
        q("下界合金碎片来源于什么？", List.of("远古残骸","黑石","玄武岩","岩浆块"), 0);
        q("信标需要什么物品激活？", List.of("钻石","下界之星","末影之眼","金苹果"), 1);
        q("哪种生物掉落火药？", List.of("僵尸","骷髅","苦力怕","蜘蛛"), 2);
        q("附魔台最高等级需要多少书架？", List.of("10个","12个","15个","20个"), 2);
        q("铁傀儡用什么方块制作？", List.of("铁块","金块","钻石块","铜块"), 0);
        q("召唤凋灵需要几个凋灵骷髅头？", List.of("1个","2个","3个","4个"), 2);
        q("MC中一天等于现实多少秒？", List.of("600秒","1200秒","1800秒","2400秒"), 1);
        q("潜影贝在哪里可以找到？", List.of("下界要塞","末地城","海底神殿","林地府邸"), 1);
        q("红石信号最远传多少格？", List.of("10格","12格","15格","20格"), 2);
        q("合成工作台需要几个木板？", List.of("2个","4个","6个","8个"), 1);
        q("合成铁剑需要几个铁锭？", List.of("2个","3个","4个","5个"), 0);
        q("村民被闪电击中变成什么？", List.of("僵尸村民","骷髅","掠夺者","女巫"), 3);
        q("黑曜石至少需要什么镐子？", List.of("石镐","铁镐","金镐","钻石镐"), 3);
        q("合成弓需要几根线？", List.of("1根","2根","3根","4根"), 2);
        q("一个铁块需要几个铁锭？", List.of("3个","4个","6个","9个"), 3);
        q("僵尸在阳光下会怎样？", List.of("燃烧","加速","隐身","无影响"), 0);
        q("鸡蛋最多堆叠多少个？", List.of("8个","16个","32个","64个"), 1);
        q("金苹果需要几个金锭？", List.of("2个","4个","6个","8个"), 3);
        q("猪被闪电击中变成什么？", List.of("猪灵","骷髅马","僵尸猪灵","疣猪兽"), 2);
        q("三叉戟从哪个生物获得？", List.of("守卫者","溺尸","远古守卫者","末影人"), 1);
        q("床在下界使用会怎样？", List.of("正常睡觉","无法使用","爆炸","传送回主世界"), 2);
        q("哪种镐子挖掘速度最快？", List.of("金镐","钻石镐","铁镐","下界合金镐"), 0);
        q("石头被镐子挖掉后变成什么？", List.of("圆石","砂砾","碎石","泥土"), 0);
        q("僵尸村民可以被治愈吗？", List.of("可以","不可以","只在困难模式","只在和平模式"), 0);
        q("绿宝石矿只在什么群系生成？", List.of("山地","丛林","沙漠","平原"), 0);
        q("用精准采集挖钻石矿会掉什么？", List.of("钻石","钻石矿石方块","圆石","什么都不掉"), 1);
        q("时运III最多让钻石掉几颗？", List.of("1颗","2颗","3颗","4颗"), 3);
        q("远古残骸最佳挖掘高度？", List.of("Y=15","Y=30","Y=50","Y=80"), 0);
        q("铁矿石烧炼后得到什么？", List.of("粗铁","铁锭","铁粒","铁块"), 1);
        q("砂砾有几率掉落什么？", List.of("沙子","燧石","泥土","石头"), 1);
        q("萤石在哪个维度？", List.of("主世界","末地","月球","下界"), 3);
        q("哪种矿石不能用石镐挖？", List.of("煤矿","铁矿","钻石矿","铜矿"), 2);
        q("海晶石在哪个结构中？", List.of("末地城","海底神殿","沉船","海底废墟"), 1);
        q("紫水晶簇在哪里生成？", List.of("紫晶洞","末地","深暗之域","繁茂洞穴"), 0);
        q("铜块放置后会怎样？", List.of("氧化变绿","生锈消失","变成铁块","无变化"), 0);
        q("钻石矿在哪个Y坐标最密集？", List.of("Y=-30","Y=-59","Y=12","Y=0"), 1);
        q("挖冰块不碎需要什么附魔？", List.of("精准采集","时运","效率","耐久"), 0);
        q("铜矿石是在哪个版本加入的？", List.of("1.14","1.16","1.17","1.19"), 2);
        q("深板岩从Y坐标多少开始？", List.of("Y=16以下","Y=0以下","Y=-32以下","Y=-48以下"), 1);
        q("哪种工具挖矿耐久最高？", List.of("钻石镐","金镐","铁镐","下界合金镐"), 3);
        q("下界石英矿在哪个维度？", List.of("下界","末地","主世界","所有维度"), 0);
        q("方解石包裹着什么方块？", List.of("深板岩","凝灰岩","紫水晶","铜矿"), 2);
        q("涂蜡可以防止什么氧化？", List.of("铁块","铜块","金块","钻石块"), 1);
        q("挖黑曜石用钻石镐需要多久？", List.of("5秒","7.5秒","9.4秒","15秒"), 2);
        q("红石矿掉落多少红石粉？", List.of("1个","2个","3个","4-5个"), 3);
        q("粘土块最常见于什么地方？", List.of("山顶","沙漠","河流底部","地下洞穴"), 2);
        // === 生物与战斗 ===
        q("苦力怕爆炸前倒计时约多久？", List.of("0.5秒","1.5秒","3秒","5秒"), 1);
        q("末影人最怕什么？", List.of("火","水","岩浆","光"), 1);
        q("制作铁傀儡需要几个铁块？", List.of("1个","2个","3个","4个"), 3);
        q("掠夺者手持什么武器？", List.of("弩","弓","剑","斧头"), 0);
        q("卫道士手持什么武器？", List.of("弓","剑","铁斧","弩"), 2);
        q("唤魔者会召唤什么生物？", List.of("骷髅","恼鬼","蜘蛛","僵尸"), 1);
        q("幻翼在玩家几天没睡后出现？", List.of("1天","2天","3天","5天"), 2);
        q("远古守卫者给什么负面效果？", List.of("中毒","失明","虚弱","挖掘疲劳"), 3);
        q("狼用什么驯服？", List.of("骨头","肉","鱼","小麦"), 0);
        q("鹦鹉用什么驯服？", List.of("面包","苹果","种子","甜浆果"), 2);
        q("马怎么驯服？", List.of("用苹果","用胡萝卜","用鞍","空手骑乘"), 3);
        q("烈焰人掉落什么物品？", List.of("烈焰棒","烈焰粉","火焰弹","岩浆膏"), 0);
        q("恶魂掉落什么物品？", List.of("恶魂头","火药","恶魂之泪","末影珍珠"), 2);
        q("末影螨从什么产生？", List.of("末影箱","末影人死亡","末地传送门","末影珍珠"), 3);
        q("美西螈在哪里自然生成？", List.of("繁茂洞穴","海洋","河流","沼泽"), 0);
        q("发光鱿鱼掉落什么？", List.of("墨囊","海晶碎片","荧光墨囊","萤石粉"), 2);
        q("蜘蛛白天会主动攻击吗？", List.of("会","不会","只在困难模式","随机"), 1);
        q("治愈僵尸村民需要什么？", List.of("金苹果+力量药水","虚弱药水+金苹果","牛奶+面包","附魔金苹果"), 1);
        q("猫可以吓走哪些生物？", List.of("蜘蛛和骷髅","苦力怕和幻翼","僵尸和骷髅","蜘蛛和苦力怕"), 1);
        q("海豚会给玩家什么效果？", List.of("海豚的恩惠","水下呼吸","夜视","速度"), 0);
        q("骷髅马在什么天气出现？", List.of("下雨","下雪","雷暴","晴天"), 2);
        q("蜜蜂蛰人后会怎样？", List.of("死亡","逃跑","继续攻击","变友好"), 0);
        q("羊驼会攻击什么生物？", List.of("苦力怕","狼","僵尸","玩家"), 1);
        q("雪傀儡在什么群系会融化？", List.of("平原","丛林","沙漠","针叶林"), 2);
        q("凋灵骷髅在哪里生成？", List.of("深暗之域","下界荒地","堡垒遗迹","下界要塞"), 3);
        // === 附魔与药水 ===
        q("经验修补和无限能共存吗？", List.of("可以","不可以","只在基岩版","看版本"), 1);
        q("精准采集和时运能共存吗？", List.of("可以","不可以","看工具","看等级"), 1);
        q("引雷附魔加在什么武器上？", List.of("弓","剑","弩","三叉戟"), 3);
        q("忠诚附魔有什么效果？", List.of("增加伤害","三叉戟自动返回","减少耐久消耗","穿透敌人"), 1);
        q("水下呼吸附魔最高几级？", List.of("I级","II级","III级","V级"), 2);
        q("保护附魔最高几级？", List.of("I级","II级","III级","IV级"), 3);
        q("酿造台需要什么燃料？", List.of("烈焰粉","煤炭","红石粉","萤石粉"), 0);
        q("夜视药水需要什么材料？", List.of("蜘蛛眼","烈焰粉","金胡萝卜","发光墨囊"), 2);
        q("所有药水的基础是什么？", List.of("粗制的药水","水瓶","岩浆膏","蜘蛛眼"), 0);
        q("红石粉加入药水有什么效果？", List.of("增强效果","延长时间","变成喷溅","无效果"), 1);
        q("萤石粉加入药水有什么效果？", List.of("增强效果","延长时间","变成喷溅","反转效果"), 0);
        q("火药加入药水有什么效果？", List.of("增强效果","延长时间","变成喷溅药水","变成滞留药水"), 2);
        q("合成熔炉需要几个圆石？", List.of("4个","5个","6个","8个"), 3);
        q("合成箱子需要几个木板？", List.of("8个","6个","4个","9个"), 0);
        q("合成一张床需要几个羊毛？", List.of("1个","2个","3个","4个"), 2);
        q("锋利附魔最高几级？", List.of("II级","III级","IV级","V级"), 3);
        q("合成末影之眼需要什么？", List.of("烈焰粉+末影珍珠","末影珍珠+金粒","烈焰棒+末影珍珠","钻石+末影珍珠"), 0);
        q("合成TNT需要几个火药？", List.of("3个","4个","5个","6个"), 2);
        q("合成铁砧需要几个铁块？", List.of("2个","3个","4个","5个"), 1);
        q("粗制的药水用什么酿造？", List.of("地狱疣+水瓶","蜘蛛眼+水瓶","烈焰粉+水瓶","金粒+水瓶"), 0);
        q("力量药水需要什么材料？", List.of("蜘蛛眼","金胡萝卜","烈焰粉","恶魂之泪"), 2);
        q("再生药水需要什么材料？", List.of("金胡萝卜","蜘蛛眼","烈焰粉","恶魂之泪"), 3);
        q("合成漏斗需要几个铁锭？", List.of("5个","4个","3个","6个"), 0);
        q("合成盾牌需要什么？", List.of("铁锭+皮革","铁锭+木板","铁锭+羊毛","铁块+木板"), 1);
        q("龙息可以用来做什么？", List.of("酿造力量药水","激活信标","制作滞留药水","合成末影箱"), 2);
        // === 生物群系与结构 ===
        q("末地传送门需要几个末影之眼？", List.of("8个","10个","12个","16个"), 2);
        q("下界传送门最小尺寸是？", List.of("4x5","3x3","5x5","2x3"), 0);
        q("深暗之域有什么特殊方块？", List.of("末地石","黑曜石","基岩","幽匿块"), 3);
        q("监守者在哪里生成？", List.of("深暗之域","末地","下界","海底神殿"), 0);
        q("林地府邸里有什么特殊生物？", List.of("掠夺者","女巫","唤魔者","凋灵"), 2);
        q("蘑菇岛有什么特别之处？", List.of("很多钻石","不刷怪","永远白天","没有树"), 1);
        q("沉船里通常有什么宝藏？", List.of("钻石块","附魔金苹果","下界之星","藏宝图"), 3);
        q("堡垒遗迹在哪个维度？", List.of("下界","主世界","末地","所有维度"), 0);
        q("要塞(末地传送门)在哪？", List.of("海底","山顶","地下","丛林"), 2);
        q("樱花树在什么群系？", List.of("平原","樱花树林","丛林","针叶林"), 1);
        q("繁茂洞穴有什么特殊植物？", List.of("垂根和孢子花","仙人掌","甘蔗","大型蘑菇"), 0);
        q("下界有几种生物群系？", List.of("2种","3种","4种","5种"), 3);
        q("掠夺者前哨站有什么旗帜？", List.of("骷髅旗帜","花纹旗帜","不祥旗帜","龙旗帜"), 2);
        q("废弃矿井有什么危险？", List.of("洞穴蜘蛛刷怪笼","TNT陷阱","凋灵","岩浆陷阱"), 0);
        q("冰刺之地有什么特殊方块？", List.of("蓝冰","浮冰","霜冰","雪块"), 1);
        q("沙漠神殿的陷阱是什么？", List.of("箭矢陷阱","岩浆陷阱","落穴陷阱","TNT+压力板"), 3);
        q("丛林神殿有什么机关？", List.of("发射器+绊线","TNT陷阱","岩浆池","仙人掌墙"), 0);
        q("主世界最高建筑高度是？", List.of("128格","256格","320格","512格"), 2);
        q("主世界最低Y坐标是？", List.of("Y=0","Y=-64","Y=-128","Y=-256"), 1);
        q("打败末影龙后出现什么？", List.of("末地折跃门","宝箱","传送门回下界","信标"), 0);
        q("下界的天花板高度是？", List.of("64格","100格","200格","128格"), 3);
        q("末影龙蛋可以直接挖吗？", List.of("可以用镐子","可以用铲子","不能直接挖","可以用手"), 2);
        q("下界1格等于主世界几格？", List.of("4格","8格","12格","16格"), 1);
        q("海底神殿的Boss是？", List.of("远古守卫者","守卫者","溺尸","海洋之心"), 0);
        q("什么方块可以阻止幽匿尖啸体？", List.of("玻璃","木板","羊毛","铁块"), 2);
        // === 红石与农业 ===
        q("红石中继器最大延迟几刻？", List.of("2刻","4刻","6刻","8刻"), 1);
        q("红石比较器有什么功能？", List.of("放大信号","延迟信号","比较/检测信号强度","反转信号"), 2);
        q("粘性活塞可以推拉几个方块？", List.of("1个","2个","3个","无限"), 0);
        q("活塞最多能推几个方块？", List.of("6个","8个","10个","12个"), 3);
        q("观察者检测什么？", List.of("红石信号","方块状态变化","玩家移动","光照变化"), 1);
        q("小麦种子从哪里获得？", List.of("打草","砍树","挖土","钓鱼"), 0);
        q("用什么让牛繁殖？", List.of("面包","小麦","苹果","胡萝卜"), 1);
        q("用什么让猪繁殖？", List.of("小麦","面包","苹果","胡萝卜"), 3);
        q("用什么让鸡繁殖？", List.of("种子","面包","小麦","苹果"), 0);
        q("甘蔗最高能长几格？", List.of("1格","2格","3格","4格"), 2);
        q("仙人掌只能种在什么上？", List.of("泥土","沙子","砂砾","石头"), 1);
        q("骨粉可以加速植物生长吗？", List.of("可以","不可以","只对花有效","只对树有效"), 0);
        q("牛排回复多少饥饿值？", List.of("4格","6格","8格","10格"), 2);
        q("蜂蜜瓶可以解除什么效果？", List.of("中毒","虚弱","失明","所有效果"), 0);
        q("发射器和投掷器有什么区别？", List.of("没区别","发射器能使用物品","投掷器更远","发射器更便宜"), 1);
        q("红石灯需要什么才能发光？", List.of("萤石粉","火把","红石信号","阳光"), 2);
        q("可可豆种在什么方块上？", List.of("丛林原木","橡木原木","任何原木","泥土"), 0);
        q("耕地被踩踏后会变成什么？", List.of("草方块","泥土","砂砾","沙子"), 1);
        q("堆肥桶用来做什么？", List.of("把植物变成骨粉","储存食物","种植作物","酿造药水"), 0);
        q("牛奶可以清除所有效果吗？", List.of("只清负面","清除所有","不能清除","只清正面"), 1);
        // === 下界与末地 ===
        q("下界之星从哪个Boss掉落？", List.of("凋灵","末影龙","远古守卫者","监守者"), 0);
        q("猪灵用什么物品交易？", List.of("钻石","铁锭","金锭","绿宝石"), 2);
        q("猪灵看到穿什么不攻击？", List.of("钻石装备","金装备","铁装备","下界合金装备"), 1);
        q("灵魂沙有什么效果？", List.of("加速移动","弹跳","发光","减速移动"), 3);
        q("灵魂沙在水中产生什么？", List.of("向上的气泡柱","向下的气泡柱","无效果","岩浆"), 0);
        q("疣猪兽怕什么方块？", List.of("岩浆块","灵魂沙","诡异菌","萤石"), 2);
        q("鞘翅在哪里找到？", List.of("末地城展示框中","末影龙掉落","下界要塞","海底神殿"), 0);
        q("鞘翅用什么修复？", List.of("铁锭","皮革","幻翼膜","钻石"), 2);
        q("玩家默认生命值是多少？", List.of("10颗心","20点(10颗心)","30点","40点"), 1);
        q("饥饿值耗尽后会怎样？", List.of("持续扣血","无法移动","视野变暗","无影响"), 0);
        q("满血掉落多少格会摔死？", List.of("10格","15格","20格","23格以上"), 3);
        q("水桶可以消除摔落伤害吗？", List.of("不可以","只能减半","可以完全消除","看高度"), 2);
        q("下界合金装备掉岩浆会烧吗？", List.of("不会","会","看时间","看深度"), 0);
        q("地狱疣种在什么方块上？", List.of("泥土","下界岩","灵魂沙","黑石"), 2);
        q("MC一共有几个维度？", List.of("1个","2个","4个","3个"), 3);
        q("末影箱被破坏后物品会消失吗？", List.of("会","不会","看难度","随机"), 1);
        q("名牌可以让生物不消失吗？", List.of("可以","不可以","只对动物","只对怪物"), 0);
        q("附魔金苹果可以合成吗？", List.of("8金块+苹果","可以","不能只能找到","8金锭+苹果"), 2);
        q("不死图腾从哪获得？", List.of("末地城","下界要塞","海底神殿","唤魔者掉落"), 3);
        q("潮涌核心有什么用？", List.of("照明","水下攻击和呼吸","传送","储物"), 1);
        // === 进阶知识 ===
        q("Minecraft最初的名字？", List.of("MineCraft","CraftMine","Cave Game","BlockWorld"), 2);
        q("Minecraft的创始人是谁？", List.of("Notch","Jeb","Dinnerbone","Herobrine"), 0);
        q("1.16更新叫什么？", List.of("洞穴更新","下界更新","海洋更新","村庄更新"), 1);
        q("1.17-1.18更新叫什么？", List.of("下界更新","荒野更新","海洋更新","洞穴与山崖"), 3);
        q("1.19更新叫什么？", List.of("荒野更新","洞穴更新","村庄更新","战斗更新"), 0);
        q("1.13更新叫什么？", List.of("村庄更新","下界更新","海洋更新","战斗更新"), 2);
        q("苦力怕的设计灵感来源？", List.of("有意设计","猪的模型做错了","玩家建议","蜘蛛改的"), 1);
        q("把羊命名为什么会变彩虹？", List.of("Rainbow","Color","Dinnerbone","jeb_"), 3);
        q("命名Dinnerbone会怎样？", List.of("上下颠倒","变彩色","消失","变巨大"), 0);
        q("下界合金锭怎么合成？", List.of("4金锭+4下界合金碎片","8金锭+1碎片","1金锭+1碎片","熔炉烧炼"), 0);
        q("哪个方块可以储存9个钻石？", List.of("箱子","末影箱","潜影盒","钻石块"), 3);
        q("一个原木合成几个木板？", List.of("1个","2个","4个","8个"), 2);
        q("钓鱼可以钓到附魔书吗？", List.of("可以","不可以","只在下雨时","只有海之眷顾"), 0);
        q("指南针指向什么？", List.of("北方","世界出生点","最近村庄","玩家的床"), 1);
        q("磁石指南针指向什么？", List.of("绑定的磁石","北方","出生点","最近玩家"), 0);
        q("潜影盒有什么特殊功能？", List.of("无限储存","传送物品","自动分类","破坏后保留物品"), 3);
        q("1.14更新叫什么？", List.of("海洋更新","战斗更新","村庄与掠夺","色彩更新"), 2);
        q("旗帜最多能加几层图案？", List.of("3层","6层","8层","16层"), 1);
        q("拴绳可以拴住什么？", List.of("大多数被动生物","所有生物","只有马","只有狼"), 0);
        q("避雷针用什么合成？", List.of("铁锭","铜锭","金锭","红石"), 1);
        q("蜂箱装满蜂蜜用什么收集？", List.of("桶","碗","玻璃瓶","空手"), 2);
        q("充能铁轨需要什么激活？", List.of("红石信号","自动激活","放置即激活","矿车通过"), 0);
        q("合成地图需要什么？", List.of("纸+木棍","皮革+纸","指南针+纸","红石+纸"), 2);
        q("火把可以融化附近的雪吗？", List.of("可以","不可以","只融雪层","看距离"), 0);
        q("讲台上的书可以发出什么？", List.of("光","声音","粒子","红石信号"), 3);
        q("效率附魔最高几级？", List.of("II级","III级","IV级","V级"), 3);
        q("合成望远镜需要什么？", List.of("玻璃+金锭","紫水晶碎片+铜锭","钻石+铁锭","萤石+铜锭"), 1);
        q("刷怪笼能用精准采集挖吗？", List.of("可以","不可以","只在创造模式","看版本"), 1);
        q("猫用什么驯服？", List.of("骨头","肉","生鱼","种子"), 2);
        q("村民晚上会做什么？", List.of("继续工作","回家睡觉","消失","变成僵尸"), 1);
    }

    private void loadQuestionsEn() {
        // === Mining & Ores ===
        q("What is the lowest Y level where diamonds can generate?", List.of("Y=-64","Y=0","Y=16","Y=32"), 0);
        q("What is the minimum pickaxe required to mine iron ore?", List.of("Wooden","Stone","Iron","Diamond"), 1);
        q("What is the max stack size for most blocks?", List.of("16","32","64","128"), 2);
        q("Which dimension is the Ender Dragon in?", List.of("Overworld","Nether","The End","Deep Dark"), 2);
        q("What ore yields Netherite Scrap?", List.of("Ancient Debris","Blackstone","Basalt","Magma Block"), 0);
        q("What item is needed to activate a Beacon?", List.of("Diamond","Nether Star","Eye of Ender","Golden Apple"), 1);
        q("Which mob drops Gunpowder?", List.of("Zombie","Skeleton","Creeper","Spider"), 2);
        q("How many bookshelves for max enchanting?", List.of("10","12","15","20"), 2);
        q("What blocks are used to build an Iron Golem?", List.of("Iron Blocks","Gold Blocks","Diamond Blocks","Copper Blocks"), 0);
        q("How many Wither Skeleton Skulls to summon the Wither?", List.of("1","2","3","4"), 2);
        q("How many real-life seconds is one MC day?", List.of("600","1200","1800","2400"), 1);
        q("Where can Shulkers be found?", List.of("Nether Fortress","End City","Ocean Monument","Woodland Mansion"), 1);
        q("How far can a Redstone signal travel?", List.of("10 blocks","12 blocks","15 blocks","20 blocks"), 2);
        q("How many planks to craft a Crafting Table?", List.of("2","4","6","8"), 1);
        q("How many iron ingots to craft an Iron Sword?", List.of("2","3","4","5"), 0);
        q("What happens when a Villager is struck by lightning?", List.of("Zombie Villager","Skeleton","Pillager","Witch"), 3);
        q("What is the minimum pickaxe for Obsidian?", List.of("Stone","Iron","Gold","Diamond"), 3);
        q("How many strings to craft a Bow?", List.of("1","2","3","4"), 2);
        q("How many iron ingots make an Iron Block?", List.of("3","4","6","9"), 3);
        q("What happens to Zombies in sunlight?", List.of("Burn","Speed up","Turn invisible","Nothing"), 0);
        q("What is the max stack size for Eggs?", List.of("8","16","32","64"), 1);
        q("How many gold ingots for a Golden Apple?", List.of("2","4","6","8"), 3);
        q("What does a Pig turn into when struck by lightning?", List.of("Piglin","Skeleton Horse","Zombified Piglin","Hoglin"), 2);
        q("Which mob drops the Trident?", List.of("Guardian","Drowned","Elder Guardian","Enderman"), 1);
        q("What happens when you use a Bed in the Nether?", List.of("Sleep normally","Cannot use","Explodes","Teleports to Overworld"), 2);
        q("Which pickaxe has the fastest mining speed?", List.of("Gold","Diamond","Iron","Netherite"), 0);
        q("What does Stone drop when mined with a pickaxe?", List.of("Cobblestone","Gravel","Crushed Stone","Dirt"), 0);
        q("Can Zombie Villagers be cured?", List.of("Yes","No","Only on Hard mode","Only on Peaceful"), 0);
        q("In which biome does Emerald Ore generate?", List.of("Mountains","Jungle","Desert","Plains"), 0);
        q("What drops when mining Diamond Ore with Silk Touch?", List.of("Diamond","Diamond Ore Block","Cobblestone","Nothing"), 1);
        q("Max diamonds from Fortune III on one ore?", List.of("1","2","3","4"), 3);
        q("Best Y level for Ancient Debris?", List.of("Y=15","Y=30","Y=50","Y=80"), 0);
        q("What does smelting Iron Ore give?", List.of("Raw Iron","Iron Ingot","Iron Nugget","Iron Block"), 1);
        q("What can Gravel drop?", List.of("Sand","Flint","Dirt","Stone"), 1);
        q("Which dimension has Glowstone?", List.of("Overworld","The End","Moon","Nether"), 3);
        q("Which ore can't be mined with a Stone Pickaxe?", List.of("Coal","Iron","Diamond","Copper"), 2);
        q("Where is Prismarine found?", List.of("End City","Ocean Monument","Shipwreck","Underwater Ruins"), 1);
        q("Where do Amethyst Clusters generate?", List.of("Amethyst Geode","The End","Deep Dark","Lush Cave"), 0);
        q("What happens to Copper Blocks over time?", List.of("Oxidize to green","Rust and break","Turn to Iron","No change"), 0);
        q("At which Y coordinate are diamonds most common?", List.of("Y=-30","Y=-59","Y=12","Y=0"), 1);
        q("What enchantment lets you mine Ice without breaking it?", List.of("Silk Touch","Fortune","Efficiency","Unbreaking"), 0);
        q("In which version was Copper Ore added?", List.of("1.14","1.16","1.17","1.19"), 2);
        q("Below which Y level does Deepslate start?", List.of("Y=16","Y=0","Y=-32","Y=-48"), 1);
        q("Which tool has the highest durability?", List.of("Diamond Pickaxe","Gold Pickaxe","Iron Pickaxe","Netherite Pickaxe"), 3);
        q("Which dimension has Nether Quartz Ore?", List.of("Nether","The End","Overworld","All dimensions"), 0);
        q("What block does Calcite surround?", List.of("Deepslate","Tuff","Amethyst","Copper Ore"), 2);
        q("Waxing prevents what from happening to Copper?", List.of("Iron Block","Copper Block","Gold Block","Diamond Block"), 1);
        q("How long to mine Obsidian with a Diamond Pickaxe?", List.of("5 sec","7.5 sec","9.4 sec","15 sec"), 2);
        q("How much Redstone Dust does Redstone Ore drop?", List.of("1","2","3","4-5"), 3);
        q("Where is Clay most commonly found?", List.of("Mountain tops","Deserts","Riverbeds","Underground caves"), 2);
        // === Mobs & Combat ===
        q("How long is a Creeper's fuse before exploding?", List.of("0.5 sec","1.5 sec","3 sec","5 sec"), 1);
        q("What is the Enderman's weakness?", List.of("Fire","Water","Lava","Light"), 1);
        q("How many Iron Blocks to build an Iron Golem?", List.of("1","2","3","4"), 3);
        q("What weapon does a Pillager carry?", List.of("Crossbow","Bow","Sword","Axe"), 0);
        q("What weapon does a Vindicator carry?", List.of("Bow","Sword","Iron Axe","Crossbow"), 2);
        q("What mob does an Evoker summon?", List.of("Skeleton","Vex","Spider","Zombie"), 1);
        q("After how many days without sleep do Phantoms appear?", List.of("1","2","3","5"), 2);
        q("What debuff does an Elder Guardian give?", List.of("Poison","Blindness","Weakness","Mining Fatigue"), 3);
        q("What do you use to tame a Wolf?", List.of("Bone","Meat","Fish","Wheat"), 0);
        q("What do you use to tame a Parrot?", List.of("Bread","Apple","Seeds","Sweet Berries"), 2);
        q("How do you tame a Horse?", List.of("With Apples","With Carrots","With a Saddle","Mount it repeatedly"), 3);
        q("What does a Blaze drop?", List.of("Blaze Rod","Blaze Powder","Fire Charge","Magma Cream"), 0);
        q("What does a Ghast drop?", List.of("Ghast Head","Gunpowder","Ghast Tear","Ender Pearl"), 2);
        q("What spawns Endermites?", List.of("Ender Chest","Enderman Death","End Portal","Ender Pearl"), 3);
        q("Where do Axolotls naturally spawn?", List.of("Lush Caves","Ocean","River","Swamp"), 0);
        q("What does a Glow Squid drop?", List.of("Ink Sac","Prismarine Shard","Glow Ink Sac","Glowstone Dust"), 2);
        q("Do Spiders attack during the day?", List.of("Yes","No","Only on Hard","Random"), 1);
        q("What is needed to cure a Zombie Villager?", List.of("Golden Apple + Strength Potion","Weakness Potion + Golden Apple","Milk + Bread","Enchanted Golden Apple"), 1);
        q("What mobs can Cats scare away?", List.of("Spiders & Skeletons","Creepers & Phantoms","Zombies & Skeletons","Spiders & Creepers"), 1);
        q("What effect do Dolphins give players?", List.of("Dolphin's Grace","Water Breathing","Night Vision","Speed"), 0);
        q("In what weather do Skeleton Horses spawn?", List.of("Rain","Snow","Thunderstorm","Clear"), 2);
        q("What happens to a Bee after it stings?", List.of("Dies","Runs away","Keeps attacking","Becomes friendly"), 0);
        q("What mob do Llamas attack?", List.of("Creeper","Wolf","Zombie","Player"), 1);
        q("In which biome do Snow Golems melt?", List.of("Plains","Jungle","Desert","Taiga"), 2);
        q("Where do Wither Skeletons spawn?", List.of("Deep Dark","Nether Wastes","Bastion Remnant","Nether Fortress"), 3);
        // === Enchanting & Brewing ===
        q("Can Mending and Infinity coexist?", List.of("Yes","No","Only on Bedrock","Depends on version"), 1);
        q("Can Silk Touch and Fortune coexist?", List.of("Yes","No","Depends on tool","Depends on level"), 1);
        q("Which weapon gets the Channeling enchantment?", List.of("Bow","Sword","Crossbow","Trident"), 3);
        q("What does the Loyalty enchantment do?", List.of("Increase damage","Trident returns automatically","Reduce durability loss","Pierce enemies"), 1);
        q("What is the max level of Respiration?", List.of("I","II","III","V"), 2);
        q("What is the max level of Protection?", List.of("I","II","III","IV"), 3);
        q("What fuel does a Brewing Stand use?", List.of("Blaze Powder","Coal","Redstone Dust","Glowstone Dust"), 0);
        q("What ingredient makes a Night Vision Potion?", List.of("Spider Eye","Blaze Powder","Golden Carrot","Glow Ink Sac"), 2);
        q("What is the base for all potions?", List.of("Awkward Potion","Water Bottle","Magma Cream","Spider Eye"), 0);
        q("What does Redstone Dust do to a potion?", List.of("Enhance effect","Extend duration","Make splash","No effect"), 1);
        q("What does Glowstone Dust do to a potion?", List.of("Enhance effect","Extend duration","Make splash","Invert effect"), 0);
        q("What does Gunpowder do to a potion?", List.of("Enhance effect","Extend duration","Make splash potion","Make lingering potion"), 2);
        q("How many Cobblestone to craft a Furnace?", List.of("4","5","6","8"), 3);
        q("How many planks to craft a Chest?", List.of("8","6","4","9"), 0);
        q("How many Wool to craft a Bed?", List.of("1","2","3","4"), 2);
        q("What is the max level of Sharpness?", List.of("II","III","IV","V"), 3);
        q("What is needed to craft an Eye of Ender?", List.of("Blaze Powder + Ender Pearl","Ender Pearl + Gold Nugget","Blaze Rod + Ender Pearl","Diamond + Ender Pearl"), 0);
        q("How many Gunpowder to craft TNT?", List.of("3","4","5","6"), 2);
        q("How many Iron Blocks to craft an Anvil?", List.of("2","3","4","5"), 1);
        q("What brews an Awkward Potion?", List.of("Nether Wart + Water Bottle","Spider Eye + Water Bottle","Blaze Powder + Water Bottle","Gold Nugget + Water Bottle"), 0);
        q("What ingredient makes a Strength Potion?", List.of("Spider Eye","Golden Carrot","Blaze Powder","Ghast Tear"), 2);
        q("What ingredient makes a Regeneration Potion?", List.of("Golden Carrot","Spider Eye","Blaze Powder","Ghast Tear"), 3);
        q("How many Iron Ingots to craft a Hopper?", List.of("5","4","3","6"), 0);
        q("What is needed to craft a Shield?", List.of("Iron Ingot + Leather","Iron Ingot + Planks","Iron Ingot + Wool","Iron Block + Planks"), 1);
        q("What can Dragon's Breath be used for?", List.of("Brew Strength Potion","Activate Beacon","Craft Lingering Potion","Craft Ender Chest"), 2);
        // === Biomes & Structures ===
        q("How many Eyes of Ender for an End Portal?", List.of("8","10","12","16"), 2);
        q("What is the minimum Nether Portal size?", List.of("4x5","3x3","5x5","2x3"), 0);
        q("What special block is in the Deep Dark?", List.of("End Stone","Obsidian","Bedrock","Sculk"), 3);
        q("Where does the Warden spawn?", List.of("Deep Dark","The End","Nether","Ocean Monument"), 0);
        q("What special mob is in a Woodland Mansion?", List.of("Pillager","Witch","Evoker","Wither"), 2);
        q("What is special about Mushroom Islands?", List.of("Lots of diamonds","No mob spawning","Always daytime","No trees"), 1);
        q("What treasure is usually in Shipwrecks?", List.of("Diamond Block","Enchanted Golden Apple","Nether Star","Treasure Map"), 3);
        q("Which dimension has Bastion Remnants?", List.of("Nether","Overworld","The End","All dimensions"), 0);
        q("Where is the Stronghold (End Portal)?", List.of("Underwater","Mountain top","Underground","Jungle"), 2);
        q("In which biome are Cherry Trees found?", List.of("Plains","Cherry Grove","Jungle","Taiga"), 1);
        q("What special plant is in Lush Caves?", List.of("Hanging Roots & Spore Blossom","Cactus","Sugar Cane","Huge Mushroom"), 0);
        q("How many Nether biomes are there?", List.of("2","3","4","5"), 3);
        q("What banner is at a Pillager Outpost?", List.of("Skull Banner","Patterned Banner","Ominous Banner","Dragon Banner"), 2);
        q("What danger is in Abandoned Mineshafts?", List.of("Cave Spider Spawners","TNT Traps","Wither","Lava Traps"), 0);
        q("What special block is in Ice Spikes biome?", List.of("Blue Ice","Packed Ice","Frosted Ice","Snow Block"), 1);
        q("What is the trap in Desert Temples?", List.of("Arrow Trap","Lava Trap","Pit Trap","TNT + Pressure Plate"), 3);
        q("What mechanism is in Jungle Temples?", List.of("Dispenser + Tripwire","TNT Trap","Lava Pool","Cactus Wall"), 0);
        q("What is the max build height in the Overworld?", List.of("128","256","320","512"), 2);
        q("What is the lowest Y coordinate in the Overworld?", List.of("Y=0","Y=-64","Y=-128","Y=-256"), 1);
        q("What appears after defeating the Ender Dragon?", List.of("End Gateway","Treasure Chest","Nether Portal","Beacon"), 0);
        q("What is the Nether ceiling height?", List.of("64","100","200","128"), 3);
        q("Can you mine the Dragon Egg directly?", List.of("Yes, with a pickaxe","Yes, with a shovel","No, not directly","Yes, by hand"), 2);
        q("1 block in the Nether = how many in the Overworld?", List.of("4","8","12","16"), 1);
        q("What is the boss in an Ocean Monument?", List.of("Elder Guardian","Guardian","Drowned","Heart of the Sea"), 0);
        q("What block can prevent Sculk Shriekers?", List.of("Glass","Planks","Wool","Iron Block"), 2);
        // === Redstone & Farming ===
        q("What is the max delay of a Redstone Repeater?", List.of("2 ticks","4 ticks","6 ticks","8 ticks"), 1);
        q("What does a Redstone Comparator do?", List.of("Amplify signal","Delay signal","Compare/detect signal strength","Invert signal"), 2);
        q("How many blocks can a Sticky Piston pull?", List.of("1","2","3","Unlimited"), 0);
        q("How many blocks can a Piston push?", List.of("6","8","10","12"), 3);
        q("What does an Observer detect?", List.of("Redstone signal","Block state changes","Player movement","Light changes"), 1);
        q("How do you get Wheat Seeds?", List.of("Break grass","Chop trees","Dig dirt","Fishing"), 0);
        q("What do you use to breed Cows?", List.of("Bread","Wheat","Apple","Carrot"), 1);
        q("What do you use to breed Pigs?", List.of("Wheat","Bread","Apple","Carrot"), 3);
        q("What do you use to breed Chickens?", List.of("Seeds","Bread","Wheat","Apple"), 0);
        q("How tall can Sugar Cane grow?", List.of("1 block","2 blocks","3 blocks","4 blocks"), 2);
        q("What block can Cactus only be placed on?", List.of("Dirt","Sand","Gravel","Stone"), 1);
        q("Can Bone Meal speed up plant growth?", List.of("Yes","No","Only flowers","Only trees"), 0);
        q("How many hunger points does Steak restore?", List.of("4","6","8","10"), 2);
        q("What effect can a Honey Bottle cure?", List.of("Poison","Weakness","Blindness","All effects"), 0);
        q("What is the difference between Dispenser and Dropper?", List.of("No difference","Dispenser uses items","Dropper is farther","Dispenser is cheaper"), 1);
        q("What does a Redstone Lamp need to glow?", List.of("Glowstone Dust","Torch","Redstone signal","Sunlight"), 2);
        q("What block do Cocoa Beans grow on?", List.of("Jungle Log","Oak Log","Any Log","Dirt"), 0);
        q("What does Farmland turn into when trampled?", List.of("Grass Block","Dirt","Gravel","Sand"), 1);
        q("What does a Composter do?", List.of("Turn plants into Bone Meal","Store food","Grow crops","Brew potions"), 0);
        q("Can Milk clear all status effects?", List.of("Only negative","Clears all","Cannot clear","Only positive"), 1);
        // === Nether & End ===
        q("Which boss drops the Nether Star?", List.of("Wither","Ender Dragon","Elder Guardian","Warden"), 0);
        q("What item do Piglins barter with?", List.of("Diamond","Iron Ingot","Gold Ingot","Emerald"), 2);
        q("What armor stops Piglins from attacking?", List.of("Diamond","Gold","Iron","Netherite"), 1);
        q("What effect does Soul Sand have?", List.of("Speed up","Bounce","Glow","Slow down"), 3);
        q("What does Soul Sand create in water?", List.of("Upward bubble column","Downward bubble column","No effect","Lava"), 0);
        q("What block are Hoglins afraid of?", List.of("Magma Block","Soul Sand","Warped Fungus","Glowstone"), 2);
        q("Where is the Elytra found?", List.of("End City item frame","Ender Dragon drop","Nether Fortress","Ocean Monument"), 0);
        q("What repairs the Elytra?", List.of("Iron Ingot","Leather","Phantom Membrane","Diamond"), 2);
        q("What is the player's default health?", List.of("10 hearts","20 points (10 hearts)","30 points","40 points"), 1);
        q("What happens when hunger is depleted?", List.of("Continuous health drain","Cannot move","Vision darkens","No effect"), 0);
        q("How many blocks to fall to die from full health?", List.of("10","15","20","23+"), 3);
        q("Can a Water Bucket negate fall damage?", List.of("No","Only halves it","Fully negates","Depends on height"), 2);
        q("Does Netherite gear burn in lava?", List.of("No","Yes","Depends on time","Depends on depth"), 0);
        q("What block do Nether Warts grow on?", List.of("Dirt","Netherrack","Soul Sand","Blackstone"), 2);
        q("How many dimensions does MC have?", List.of("1","2","4","3"), 3);
        q("Do items disappear when an Ender Chest is broken?", List.of("Yes","No","Depends on difficulty","Random"), 1);
        q("Can a Name Tag prevent mob despawning?", List.of("Yes","No","Only animals","Only monsters"), 0);
        q("Can Enchanted Golden Apples be crafted?", List.of("8 Gold Blocks + Apple","Yes","No, loot only","8 Gold Ingots + Apple"), 2);
        q("Where is the Totem of Undying obtained?", List.of("End City","Nether Fortress","Ocean Monument","Evoker drop"), 3);
        q("What does a Conduit do?", List.of("Lighting","Underwater attack & breathing","Teleportation","Storage"), 1);
        // === Trivia ===
        q("What was Minecraft originally called?", List.of("MineCraft","CraftMine","Cave Game","BlockWorld"), 2);
        q("Who created Minecraft?", List.of("Notch","Jeb","Dinnerbone","Herobrine"), 0);
        q("What is the 1.16 update called?", List.of("Caves Update","Nether Update","Ocean Update","Village Update"), 1);
        q("What is the 1.17-1.18 update called?", List.of("Nether Update","Wild Update","Ocean Update","Caves & Cliffs"), 3);
        q("What is the 1.19 update called?", List.of("The Wild Update","Caves Update","Village Update","Combat Update"), 0);
        q("What is the 1.13 update called?", List.of("Village Update","Nether Update","Update Aquatic","Combat Update"), 2);
        q("What inspired the Creeper's design?", List.of("Intentional design","Failed Pig model","Player suggestion","Modified Spider"), 1);
        q("What name makes a Sheep rainbow?", List.of("Rainbow","Color","Dinnerbone","jeb_"), 3);
        q("What happens when you name a mob Dinnerbone?", List.of("Flips upside down","Turns rainbow","Disappears","Grows huge"), 0);
        q("How do you craft a Netherite Ingot?", List.of("4 Gold Ingots + 4 Netherite Scrap","8 Gold Ingots + 1 Scrap","1 Gold Ingot + 1 Scrap","Furnace smelting"), 0);
        q("Which block stores 9 Diamonds?", List.of("Chest","Ender Chest","Shulker Box","Diamond Block"), 3);
        q("How many planks from one Log?", List.of("1","2","4","8"), 2);
        q("Can you get Enchanted Books from fishing?", List.of("Yes","No","Only in rain","Only with Luck of the Sea"), 0);
        q("What does a Compass point to?", List.of("North","World spawn point","Nearest village","Player's bed"), 1);
        q("What does a Lodestone Compass point to?", List.of("Bound Lodestone","North","Spawn point","Nearest player"), 0);
        q("What is special about Shulker Boxes?", List.of("Infinite storage","Teleport items","Auto-sort","Keep items when broken"), 3);
        q("What is the 1.14 update called?", List.of("Ocean Update","Combat Update","Village & Pillage","Color Update"), 2);
        q("How many banner pattern layers max?", List.of("3","6","8","16"), 1);
        q("What can a Lead be attached to?", List.of("Most passive mobs","All mobs","Only horses","Only wolves"), 0);
        q("What is a Lightning Rod crafted from?", List.of("Iron Ingots","Copper Ingots","Gold Ingots","Redstone"), 1);
        q("What collects honey from a full Beehive?", List.of("Bucket","Bowl","Glass Bottle","Bare hand"), 2);
        q("What activates a Powered Rail?", List.of("Redstone signal","Auto-activates","Activates on placement","Minecart passing"), 0);
        q("What is needed to craft a Map?", List.of("Paper + Sticks","Leather + Paper","Compass + Paper","Redstone + Paper"), 2);
        q("Can Torches melt nearby Snow?", List.of("Yes","No","Only snow layers","Depends on distance"), 0);
        q("What signal can a Lectern with a book emit?", List.of("Light","Sound","Particles","Redstone signal"), 3);
        q("What is the max level of Efficiency?", List.of("II","III","IV","V"), 3);
        q("What is needed to craft a Spyglass?", List.of("Glass + Gold Ingot","Amethyst Shard + Copper Ingot","Diamond + Iron Ingot","Glowstone + Copper Ingot"), 1);
        q("Can a Spawner be mined with Silk Touch?", List.of("Yes","No","Only in Creative","Depends on version"), 1);
        q("What do you use to tame a Cat?", List.of("Bone","Meat","Raw Fish","Seeds"), 2);
        q("What do Villagers do at night?", List.of("Keep working","Go home to sleep","Disappear","Turn into Zombies"), 1);
    }

    private void q(String text, List<String> options, int correct) {
        questions.add(new Question(text, options, correct));
    }
}
