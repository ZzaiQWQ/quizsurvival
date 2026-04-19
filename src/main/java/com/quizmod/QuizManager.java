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
    private static final Path QUESTIONS_FILE = FabricLoader.getInstance().getConfigDir().resolve("quizsurvival").resolve("quiz_questions.json");

    private final List<Question> questions = new ArrayList<>();
    private final Map<UUID, Integer> activeQuizzes = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Integer> correctAnswers = new java.util.concurrent.ConcurrentHashMap<>();
    private final Random random = new Random();
    // 每个玩家的题目队列（不重复，用完重新洗牌）
    private final Map<UUID, List<Question>> playerQueues = new java.util.concurrent.ConcurrentHashMap<>();

    public QuizManager() {
        if (Files.exists(QUESTIONS_FILE)) {
            loadFromJson();
        } else {
            loadQuestions(); // 硬编码默认题库
            saveToJson();   // 首次运行生成JSON
        }
    }

    private void loadFromJson() {
        try (Reader r = Files.newBufferedReader(QUESTIONS_FILE, StandardCharsets.UTF_8)) {
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

    private void saveToJson() {
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
            Files.createDirectories(QUESTIONS_FILE.getParent());
            Files.writeString(QUESTIONS_FILE, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(arr), StandardCharsets.UTF_8);
            LOGGER.info("[答题生存] 已生成题库JSON: {}", QUESTIONS_FILE);
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
        if (Files.exists(QUESTIONS_FILE)) {
            loadFromJson();
        } else {
            loadQuestions();
            saveToJson();
        }
        return questions.size();
    }

    private void loadQuestions() {
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

    private void q(String text, List<String> options, int correct) {
        questions.add(new Question(text, options, correct));
    }
}
