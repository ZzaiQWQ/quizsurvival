# 🎓 Quiz Survival

**Minecraft Fabric Mod | 1.21.11**

> Who says playing Minecraft is just playing games?

Want to chop a tree? Answer a question first. Mine ores? Answer first. Fight mobs? Answer first. Even jumping requires a quiz.

When Minecraft meets quizzes, everything changes. The question bank is fully open — it ships with Minecraft trivia by default, but you can swap it out for **advanced math, physics, English, history** — anything you want to learn. Play and learn at the same time. Want to play? Then answer correctly first.

**This isn't just a mod. It's a game-driven learning system.**

[中文文档](README_CN.md)

---

## ✨ Features

### 🔨 Full Action Interception
- **Chopping** — Cutting trees triggers a quiz
- **Mining** — Mining ores, stone, and dirt each have separate categories
- **Combat** — Both melee and ranged attacks trigger quizzes; damage is only dealt after a correct answer
- **Jumping** — Even jumping requires a quiz
- **Interaction** — Opening crafting tables, chests, furnaces, etc. requires a quiz

### ⚔️ Combat System
- When attacking a mob, all nearby monsters are frozen so you can answer safely
- Damage is calculated based on mob HP tier (small mobs = one-shot, medium = half HP, boss = fixed damage)
- Ranged attacks (bow/crossbow) deal 50% of melee damage (configurable)
- Wrong answer = mobs unfreeze + health penalty; Creepers ignite on wrong answer!
- PvP quiz duels supported — attacking another player triggers a quiz too

### 📊 Quiz Mechanics
- **200+ questions** covering Minecraft knowledge: mining, combat, enchanting, brewing, redstone, biomes, and more
- Non-repeating questions; deck reshuffles when all questions are used
- Correct answers grant **free action charges** (tracked per category)
- Wrong answers deal escalating damage (formula: `min(max, base^consecutive_errors)`)
- Countdown timer with progress bar; auto-submits wrong answer at 0

### 🎨 User Interface
- Quiz screen with category label, damage warning, and countdown progress bar
- HUD overlay showing remaining free action charges per category
- Sound effects for correct/wrong answers

### ⚙️ Fully Configurable
- All parameters adjustable via JSON config — no recompilation needed
- Hot-reload with `/quizsurvival reload` command
- Customizable block categories, combat exclusions, damage multipliers, and more
- External JSON question bank — add/modify questions freely

### 🌐 Multi-Language Support
- Built-in Chinese and English question banks (200+ questions each)
- Switch language in-game with `/quizsurvival lang <zh|en>`
- All UI text, HUD, chat messages, and config comments are bilingual

### 🔒 Multiplayer Safe
- All state is isolated per player UUID
- ConcurrentHashMap ensures thread safety
- Reference-counted mob freezing prevents premature unfreeze in multiplayer

---

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/) (1.21.11)
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) (0.140.2+)
3. Place the mod JAR in `.minecraft/mods/`
4. Launch the game — config files and question bank are auto-generated on first run

---

## 🎮 Commands

All commands use the unified `/quizsurvival` root:

| Command | Permission | Description |
|---------|-----------|-------------|
| `/quizsurvival` | Everyone | Show command help |
| `/quizsurvival reload` | OP | Hot-reload config and question bank |
| `/quizsurvival stats [player]` | Everyone | View quiz statistics (total/correct/wrong/accuracy) |
| `/quizsurvival time <seconds>` | OP | Change quiz time limit (0 = unlimited) |
| `/quizsurvival lang <zh\|en>` | OP | Switch question language |

---

## 📁 Configuration

Path: `config/quizsurvival/quizsurvival_config.json`

```json
{
  "questionLanguage": "zh",      // zh=Chinese, en=English
  "freeActionCount": 5,          // Free actions granted per correct answer
  "freezeRadius": 32,            // Mob freeze radius during combat (blocks)
  "quizTimeLimit": 30,           // Countdown in seconds, 0 = unlimited
  "wrongDamageBase": 2.0,        // Wrong answer damage base
  "wrongDamageMax": 20.0,        // Wrong answer damage cap
  "smallMobHpThreshold": 20,     // Small mob HP threshold
  "mediumMobHpThreshold": 100,   // Medium mob / Boss HP threshold
  "meleeDamageSmall": 10.0,      // Small mob damage multiplier (one-shot)
  "meleeDamageMedium": 0.5,      // Medium mob damage multiplier (half HP)
  "meleeDamageBoss": 20.0,       // Boss fixed damage
  "rangedDamageRatio": 0.5       // Ranged damage ratio (vs melee)
}
```

All config comments are bilingual (Chinese/English). Delete the config file and restart to regenerate defaults.

---

## 📝 Custom Questions

Path: `config/quizsurvival/quiz_questions_zh.json` (Chinese) or `quiz_questions_en.json` (English)

```json
[
  {
    "question": "What is the lowest Y level where diamonds can generate?",
    "options": ["-64", "0", "16", "32"],
    "answer": 0
  }
]
```

- `answer` is the zero-based index of the correct option
- Use `/quizsurvival reload` to hot-reload after editing — no restart needed
- Switch language with `/quizsurvival lang <zh|en>` — missing question bank files are auto-generated

### 💡 Not Just Minecraft Trivia

The default question bank covers Minecraft knowledge, but the JSON format means you can use **any content**:

| Use Case | Question Bank |
|----------|---------------|
| 🎓 Student Review | Calculus, physics formulas, chemistry equations |
| 🌍 Language Learning | English vocabulary, grammar, translations |
| 📚 Exam Prep | Driver's license, standardized test prep |
| 🏫 Classroom | Teachers create custom quizzes, students learn while playing |
| 🎮 Server Events | Custom trivia contests for your server community |

Just edit the JSON file — 4 options, 1 answer. No programming required.

An **Advanced Math question bank (50 questions)** is included as a sample in the `optional_question_banks/` directory:

```
optional_question_banks/
├── quiz_questions_zh.json   ← Chinese math questions
└── quiz_questions_en.json   ← English math questions
```

To use: copy directly to `config/quizsurvival/` to replace the default files, then `/quizsurvival reload`.

---

## 📣 Answer Feedback

Quiz results appear in real-time in the chat:

| Situation | Message Example |
|-----------|-----------------|
| ✅ Correct | `§a✔ Correct! 5 free actions granted!` |
| ✅ Combat Correct | `§a✔ Correct! Melee dealt 20 damage! (0 left)` |
| ❌ Wrong | `§c✘ Wrong! -2 HP! (18 left)` |
| ❌ Combat Wrong | `§c✘ Wrong! Mobs unfrozen! -4 HP!` |
| 💀 Creeper Wrong | `§c✘ Wrong! Creeper ignited! RUN!` |
| ⏰ Timeout | `§c✘ Time's up! -2 HP!` |

---

## 🎯 Tips & Strategy

1. **Answer correctly** — Each correct answer grants 5 free actions = extended freedom
2. **Don't streak wrong** — Wrong answer damage escalates (2→4→8→16→20 cap)
3. **Watch out for Creepers** — Wrong answer on a Creeper = instant ignition. Use ranged attacks!
4. **Don't wait for timeout** — Guess if unsure, timeout counts as wrong too
5. **Ranged = half damage** — Bows are safer but deal only 50% of melee damage

---

## ❓ FAQ

**Q: Questions too hard/easy?**
A: Edit `quiz_questions_zh.json` or `quiz_questions_en.json` with your own questions, then `/quizreload`.

**Q: How to disable quizzes for a specific action?**
A: Set free action count to a very large number (e.g. 99999) — effectively disables it.

**Q: Does it work on servers?**
A: Yes! Both client and server need the mod installed. All player states are isolated.

**Q: What happens when the timer hits 0?**
A: Auto-judged as wrong answer — health penalty + next question (combat mode also unfreezes mobs).

**Q: Compatible with other mods?**
A: Compatible with most Fabric mods. Uses standard event APIs, no core overwrites.
