# 🎓 答题生存 (Quiz Survival)

**Minecraft Fabric 模组 | 1.21.11**

> 在生存模式中，所有核心操作都需要答题才能执行！砍树、挖矿、打怪、跳跃、开箱——每一步都是知识的考验。

---

## ✨ 特性

### 🔨 全行为拦截
- **砍伐** — 砍树触发答题
- **挖掘** — 挖矿/石头/泥土分类触发
- **战斗** — 近战和远程攻击都触发答题，答对才能造成伤害
- **跳跃** — 跳跃也要答题
- **交互** — 打开工作台、箱子、熔炉等需要答题

### ⚔️ 战斗系统
- 攻击怪物时，周围怪物全部冻结，安心答题
- 答对后根据怪物血量分级计算伤害（小怪秒杀、中怪半血、Boss固定伤害）
- 弓箭/弩等远程攻击伤害为近战的50%（可配置）
- 答错解冻怪物 + 扣血惩罚，苦力怕答错直接点燃爆炸！
- 支持PvP答题决斗——打对方玩家也触发答题

### 📊 答题机制
- **200+ 道** Minecraft 知识题，涵盖挖矿、战斗、附魔、酿造、红石、生物群系等
- 不重复出题，所有题目用完后自动重新洗牌
- 答对获得 **免答次数**（按分类独立计数），消耗完再触发
- 答错递增扣血（公式：`min(上限, 底数^连错次数)`）
- 倒计时进度条，超时自动判错

### 🎨 界面
- 答题界面带分类标签、扣血预警、倒计时进度条
- HUD左上角实时显示各分类剩余免答次数
- 答对/答错分别播放音效

### ⚙️ 全配置化
- 所有参数均可通过 JSON 配置文件调整，无需重新编译
- 支持 `/quizreload` 热重载配置和题库
- 自定义方块分类、战斗排除实体、伤害系数等
- 题库支持外部 JSON 文件，可自由添加/修改题目

### 🔒 多人安全
- 所有状态按玩家UUID隔离
- ConcurrentHashMap 保证线程安全
- 多人冻结同一怪物时引用计数，避免提前解冻

---

## 📦 安装

1. 安装 [Fabric Loader](https://fabricmc.net/) (1.21.11)
2. 安装 [Fabric API](https://modrinth.com/mod/fabric-api) (0.140.2+)
3. 将模组 JAR 放入 `.minecraft/mods/` 目录
4. 启动游戏，首次运行自动生成配置文件和题库

---

## 🎮 指令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/quizreload` | OP | 热重载配置文件和题库 |
| `/quizstats [玩家]` | 所有人 | 查看答题统计（总数/正确/错误/正确率） |
| `/quiztime <秒>` | OP | 修改答题倒计时，0 = 无限制 |

---

## 📁 配置文件

路径：`config/quizsurvival/quizsurvival_config.json`

```json
{
  "freeActionCount": 5,        // 答对获得的免答次数
  "freezeRadius": 32,          // 战斗时冻结怪物范围（格）
  "quizTimeLimit": 30,         // 倒计时秒数，0=无限
  "wrongDamageBase": 2.0,      // 答错扣血底数
  "wrongDamageMax": 20.0,      // 答错扣血上限
  "smallMobHpThreshold": 20,   // 小怪血量分界线
  "mediumMobHpThreshold": 100, // 中怪/Boss血量分界线
  "meleeDamageSmall": 10.0,    // 小怪伤害倍率（秒杀）
  "meleeDamageMedium": 0.5,    // 中怪伤害倍率（半血）
  "meleeDamageBoss": 20.0,     // Boss固定伤害
  "rangedDamageRatio": 0.5     // 远程伤害比例
}
```

所有参数均带中文注释，删除配置文件重启即可恢复默认值。

---

## 📝 自定义题库

路径：`config/quizsurvival/quiz_questions.json`

```json
[
  {
    "question": "钻石矿最低可以在第几层生成？",
    "options": ["-64层", "0层", "16层", "32层"],
    "answer": 0
  }
]
```

- `answer` 为正确选项的索引（从 0 开始）
- 修改后使用 `/quizreload` 热重载，无需重启

---

## 🛠️ 构建

```bash
./gradlew build
```

构建产物位于 `build/libs/` 目录。

---

## 📜 许可证

MIT License
