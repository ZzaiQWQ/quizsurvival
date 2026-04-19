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

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class QuizMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("quizsurvival");
    public static QuizManager quizManager;
    public static QuizConfig config;
    // 记录每个玩家当前答题触发的类型
    public static final Map<UUID, String> pendingQuizType = new ConcurrentHashMap<>();
    // 连续答错计数(用于递增扣血)
    private static final Map<UUID, Integer> wrongCount = new ConcurrentHashMap<>();
    // 战斗目标实体
    private static final Map<UUID, Entity> pendingCombatTarget = new ConcurrentHashMap<>();
    // 被冻结的怪物列表
    private static final Map<UUID, java.util.List<MobEntity>> frozenMobs = new ConcurrentHashMap<>();
    // 是否为远程战斗(弓/弩)
    private static final Map<UUID, Boolean> isRangedCombat = new ConcurrentHashMap<>();
    // 答题开始时间(用于倒计时超时)
    private static final Map<UUID, Long> quizStartTime = new ConcurrentHashMap<>();

    /** 当前配置是否为英文 */
    private static boolean isEn() {
        return config != null && "en".equals(config.questionLanguage);
    }

    /** 根据方块ID判断属于哪个分类（从配置文件读取） */
    private static String getBlockCategory(String blockId) {
        if (config.chopBlocks.stream().anyMatch(blockId::contains))
            return "chop";
        if (config.oreBlocks.stream().anyMatch(blockId::contains))
            return "ore";
        if (config.stoneBlocks.stream().anyMatch(blockId::contains))
            return "stone";
        if (config.dirtBlocks.stream().anyMatch(blockId::contains))
            return "dirt";
        if (config.freeBlocks.stream().anyMatch(blockId::contains))
            return "free";
        return "other";
    }

    /** 判断攻击的实体是否需要答题（排除列表中的不触发） */
    private static boolean shouldQuizCombat(String entityId) {
        return config.combatExcludeEntities.stream().noneMatch(entityId::contains);
    }

    @Override
    public void onInitialize() {
        config = QuizConfig.load();
        quizManager = new QuizManager();

        // 注册网络包
        PayloadTypeRegistry.playS2C().register(QuizPayloads.OpenQuizS2C.ID, QuizPayloads.OpenQuizS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(QuizPayloads.QuizResultS2C.ID, QuizPayloads.QuizResultS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(QuizPayloads.SyncActionsS2C.ID, QuizPayloads.SyncActionsS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(QuizPayloads.AnswerQuizC2S.ID, QuizPayloads.AnswerQuizC2S.CODEC);

        // 收到客户端答案
        ServerPlayNetworking.registerGlobalReceiver(QuizPayloads.AnswerQuizC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                boolean correct = quizManager.checkAnswer(player.getUuid(), payload.quizId(), payload.answerIndex());
                quizManager.recordAnswer(player.getUuid(), correct);
                quizStartTime.remove(player.getUuid());
                if (correct) {
                    player.removeStatusEffect(StatusEffects.MINING_FATIGUE);
                    player.removeStatusEffect(StatusEffects.SLOWNESS);
                    player.removeStatusEffect(StatusEffects.WEAKNESS);
                    // 答对重置错误计数
                    wrongCount.remove(player.getUuid());
                    String type = pendingQuizType.remove(player.getUuid());
                    if (type != null) {
                        if ("combat".equals(type)) {
                            // 战斗答对：分级击杀怪物
                            Entity target = pendingCombatTarget.remove(player.getUuid());
                            unfreezeAll(player.getUuid());
                            boolean rangedFlag = isRangedCombat.getOrDefault(player.getUuid(), false);
                            isRangedCombat.remove(player.getUuid());
                            if (target != null && target.isAlive() && target instanceof LivingEntity le) {
                                float maxHp = le.getMaxHealth();
                                float damage;
                                String msg;
                                if (maxHp <= config.smallMobHpThreshold) {
                                    damage = maxHp * config.meleeDamageSmall;
                                    if (rangedFlag) damage *= config.rangedDamageRatio;
                                    if (damage >= le.getHealth() && !rangedFlag) {
                                        msg = isEn() ? "§a✔ Correct! One-hit kill!" : "§a✔ 回答正确！一击必杀！";
                                    } else {
                                        String atkType = isEn() ? (rangedFlag ? "Ranged" : "Melee") : (rangedFlag ? "弓箭" : "近战");
                                        int remaining = (int) Math.max(0, le.getHealth() - damage);
                                        msg = isEn()
                                            ? "§a✔ Correct! " + atkType + " dealt " + (int) damage + " damage! (" + remaining + " left)"
                                            : "§a✔ 回答正确！" + atkType + "造成 " + (int) damage + " 点伤害！(剩余 " + remaining + ")";
                                    }
                                } else if (maxHp <= config.mediumMobHpThreshold) {
                                    damage = maxHp * config.meleeDamageMedium;
                                    if (rangedFlag) damage *= config.rangedDamageRatio;
                                    int rem = (int) Math.max(0, le.getHealth() - damage);
                                    msg = isEn()
                                        ? "§a✔ Correct! Dealt " + (int) damage + " damage! (" + rem + " left)"
                                        : "§a✔ 回答正确！对怪物造成 " + (int) damage + " 点伤害！(剩余 " + rem + ")";
                                } else {
                                    damage = config.meleeDamageBoss;
                                    if (rangedFlag) damage *= config.rangedDamageRatio;
                                    int rem = (int) Math.max(0, le.getHealth() - damage);
                                    msg = isEn()
                                        ? "§a✔ Correct! Dealt " + (int) damage + " to Boss! (" + rem + " left)"
                                        : "§a✔ 回答正确！对Boss造成 " + (int) damage + " 点伤害！(剩余 " + rem + ")";
                                }
                                ServerWorld sw = (ServerWorld) le.getEntityWorld();
                                le.damage(sw, player.getDamageSources().playerAttack(player), damage);
                                ServerPlayNetworking.send(player, new QuizPayloads.QuizResultS2C(true, msg));
                            } else {
                                ServerPlayNetworking.send(player,
                                        new QuizPayloads.QuizResultS2C(true, isEn() ? "§a✔ Correct! Target gone" : "§a✔ 回答正确！目标已消失"));
                            }
                        } else {
                            int count = config.freeActionCount;
                            quizManager.grantActions(player.getUuid(), type, count);
                            ServerPlayNetworking.send(player, new QuizPayloads.QuizResultS2C(true,
                                    isEn() ? "§a✔ Correct! " + count + " free actions granted!"
                                           : "§a✔ 回答正确！获得 " + count + " 次免答机会！"));
                        }
                        syncActions(player);
                    } else {
                        ServerPlayNetworking.send(player, new QuizPayloads.QuizResultS2C(true, isEn() ? "§a✔ Correct!" : "§a✔ 回答正确！"));
                    }
                } else {
                    // 答错
                    String curType = pendingQuizType.getOrDefault(player.getUuid(), "");
                    int wc = wrongCount.getOrDefault(player.getUuid(), 0);
                    float damage = Math.min(config.wrongDamageMax, (float) Math.pow(config.wrongDamageBase, wc));
                    wrongCount.put(player.getUuid(), wc + 1);

                    // 战斗答错：解冻怪物 + 移除效果 + 苦力怕爆炸
                    if ("combat".equals(curType)) {
                        player.removeStatusEffect(StatusEffects.MINING_FATIGUE);
                        player.removeStatusEffect(StatusEffects.SLOWNESS);
                        player.removeStatusEffect(StatusEffects.WEAKNESS);
                        Entity target = pendingCombatTarget.remove(player.getUuid());
                        unfreezeAll(player.getUuid());
                        isRangedCombat.remove(player.getUuid());
                        if (target instanceof CreeperEntity creeper && creeper.isAlive()) {
                            creeper.ignite();
                            ServerPlayNetworking.send(player, new QuizPayloads.QuizResultS2C(false,
                                    isEn() ? "§c✘ Wrong! Creeper ignited! RUN!" : "§c✘ 回答错误！苦力怕开始爆炸！快跑！"));
                            pendingQuizType.remove(player.getUuid());
                        } else {
                            ServerPlayNetworking.send(player, new QuizPayloads.QuizResultS2C(false,
                                    isEn() ? "§c✘ Wrong! Mobs unfrozen! -" + (int) damage + " HP!"
                                           : "§c✘ 回答错误！怪物已解冻！扣除 " + (int) damage + " 点血！"));
                            float newHealth = player.getHealth() - damage;
                            player.setHealth(Math.max(0.0f, newHealth));
                            pendingQuizType.remove(player.getUuid());
                        }
                    } else {
                        // 非战斗类型的答错逻辑
                        float newHealth = player.getHealth() - damage;
                        player.setHealth(Math.max(0.0f, newHealth));
                        LOGGER.info("[答题生存] 玩家 {} 第{}次答错，扣{}血，剩余: {}",
                                player.getName().getString(), wc + 1, damage, Math.max(0, newHealth));
                        if (newHealth <= 0) {
                            ServerPlayNetworking.send(player, new QuizPayloads.QuizResultS2C(false,
                                    isEn() ? "§c✘ Wrong! -" + (int) damage + " HP! You died!"
                                           : "§c✘ 回答错误！扣除 " + (int) damage + " 点血！你死了！"));
                        } else {
                            ServerPlayNetworking.send(player, new QuizPayloads.QuizResultS2C(false,
                                    isEn() ? "§c✘ Wrong! -" + (int) damage + " HP! (" + (int) newHealth + " left)"
                                           : "§c✘ 回答错误！扣除 " + (int) damage + " 点血！剩余 " + (int) newHealth + " 点！"));
                            sendQuiz(player);
                        }
                    }
                }
            });
        });

        // 挖方块事件 - 按材料分类计数
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!(player instanceof ServerPlayerEntity sp))
                return true;
            if (sp.isCreative())
                return true;
            if (quizManager.hasActiveQuiz(sp.getUuid()))
                return false;
            String blockId = Registries.BLOCK.getId(state.getBlock()).getPath();
            String category = getBlockCategory(blockId);
            if ("free".equals(category))
                return true;
            // 按分类计数：木头=chop, 矿物=ore, 石头=stone, 泥土=dirt, 其他=other
            if (quizManager.hasRemainingActions(sp.getUuid(), category)) {
                quizManager.useAction(sp.getUuid(), category);
                syncActions(sp);
                return true;
            }
            triggerQuiz(sp, category);
            return false;
        });

        // 交互方块事件 - 统一interact类
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity sp) || sp.isCreative())
                return ActionResult.PASS;
            if (quizManager.hasActiveQuiz(sp.getUuid()))
                return ActionResult.FAIL;
            Block block = world.getBlockState(hitResult.getBlockPos()).getBlock();
            String blockId = Registries.BLOCK.getId(block).getPath();
            boolean needsQuiz = config.interactBlocks.stream().anyMatch(blockId::contains);
            if (needsQuiz) {
                if (quizManager.hasRemainingActions(sp.getUuid(), "interact")) {
                    quizManager.useAction(sp.getUuid(), "interact");
                    syncActions(sp);
                    return ActionResult.PASS;
                }
                triggerQuiz(sp, "interact");
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // 攻击实体事件 - 战斗答题
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity sp) || sp.isCreative())
                return ActionResult.PASS;
            if (quizManager.hasActiveQuiz(sp.getUuid()))
                return ActionResult.FAIL;
            // 检查实体是否在排除列表中
            String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).getPath();
            if (!shouldQuizCombat(entityId))
                return ActionResult.PASS;
            // 冻结附近所有怪物
            freezeNearbyMobs(sp, world);
            pendingCombatTarget.put(sp.getUuid(), entity);
            isRangedCombat.put(sp.getUuid(), false);
            triggerQuiz(sp, "combat");
            return ActionResult.FAIL;
        });

        // 投射物伤害事件 - 弓/弩/三叉戟等远程攻击触发答题
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (source.getAttacker() instanceof ServerPlayerEntity sp
                    && source.getSource() != source.getAttacker()) { // 投射物伤害: source≠attacker
                if (sp.isCreative()) return true;
                if (quizManager.hasActiveQuiz(sp.getUuid())) return false;
                String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).getPath();
                if (!shouldQuizCombat(entityId)) return true;
                // 冻结怪物 + 触发答题
                freezeNearbyMobs(sp, (net.minecraft.world.World) entity.getEntityWorld());
                pendingCombatTarget.put(sp.getUuid(), entity);
                isRangedCombat.put(sp.getUuid(), true);
                triggerQuiz(sp, "combat");
                return false; // 取消本次伤害
            }
            return true;
        });

        // 玩家重生时清除答题状态
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            UUID uuid = newPlayer.getUuid();
            quizManager.clearPlayer(uuid);
            pendingQuizType.remove(uuid);
            pendingCombatTarget.remove(uuid);
            isRangedCombat.remove(uuid);
            unfreezeAll(uuid);
            // wrongCount 不清除！只有答对才清
            // 同步空数据给客户端，清除HUD
            syncActions(newPlayer);
            LOGGER.info("[答题生存] 玩家 {} 重生，已清除答题状态", newPlayer.getName().getString());
        });

        // 玩家退出时清除所有状态
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUuid();
            quizManager.clearPlayer(uuid);
            pendingQuizType.remove(uuid);
            pendingCombatTarget.remove(uuid);
            isRangedCombat.remove(uuid);
            wrongCount.remove(uuid);
            quizStartTime.remove(uuid);
            unfreezeAll(uuid);
        });

        // 服务端Tick：检测答题超时
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (config.quizTimeLimit <= 0)
                return;
            long now = System.currentTimeMillis();
            long limitMs = config.quizTimeLimit * 1000L;
            for (var entry : new java.util.ArrayList<>(quizStartTime.entrySet())) {
                if (now - entry.getValue() >= limitMs) {
                    UUID uuid = entry.getKey();
                    ServerPlayerEntity sp = server.getPlayerManager().getPlayer(uuid);
                    if (sp != null && quizManager.hasActiveQuiz(uuid)) {
                        // 超时=答错
                        quizStartTime.remove(uuid);
                        quizManager.recordAnswer(uuid, false);
                        String curType = pendingQuizType.getOrDefault(uuid, "");
                        int wc = wrongCount.getOrDefault(uuid, 0);
                        float damage = Math.min(config.wrongDamageMax, (float) Math.pow(config.wrongDamageBase, wc));
                        wrongCount.put(uuid, wc + 1);
                        if ("combat".equals(curType)) {
                            sp.removeStatusEffect(StatusEffects.MINING_FATIGUE);
                            sp.removeStatusEffect(StatusEffects.SLOWNESS);
                            sp.removeStatusEffect(StatusEffects.WEAKNESS);
                            unfreezeAll(uuid);
                            pendingCombatTarget.remove(uuid);
                            isRangedCombat.remove(uuid);
                            pendingQuizType.remove(uuid);
                            ServerPlayNetworking.send(sp, new QuizPayloads.QuizResultS2C(true,
                                    isEn() ? "§c✘ Time's up! Mobs unfrozen! -" + (int) damage + " HP!"
                                           : "§c✘ 答题超时！怪物已解冻！扣除 " + (int) damage + " 点血！"));
                            sp.setHealth(Math.max(0.0f, sp.getHealth() - damage));
                        } else {
                            float newHealth = sp.getHealth() - damage;
                            sp.setHealth(Math.max(0.0f, newHealth));
                            if (newHealth <= 0) {
                                ServerPlayNetworking.send(sp, new QuizPayloads.QuizResultS2C(true,
                                        isEn() ? "§c✘ Time's up! -" + (int) damage + " HP! You died!"
                                               : "§c✘ 答题超时！扣除 " + (int) damage + " 点血！你死了！"));
                            } else {
                                ServerPlayNetworking.send(sp, new QuizPayloads.QuizResultS2C(false,
                                        isEn() ? "§c✘ Time's up! -" + (int) damage + " HP!"
                                               : "§c✘ 答题超时！扣除 " + (int) damage + " 点血！"));
                                sendQuiz(sp);
                            }
                        }
                    } else {
                        quizStartTime.remove(uuid);
                    }
                }
            }
        });

        // 注册命令: /quizsurvival <子命令>
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("quizsurvival")
                    // /quizsurvival help - 显示帮助
                    .executes(ctx -> {
                        boolean en = isEn();
                        ctx.getSource().sendFeedback(() -> Text.literal(
                                en ? "§e§l===== Quiz Survival Commands =====\n"
                                        + "§a/quizsurvival reload §7- Reload config & questions\n"
                                        + "§a/quizsurvival stats [player] §7- View quiz statistics\n"
                                        + "§a/quizsurvival time <seconds> §7- Set quiz timer (0=off)\n"
                                        + "§a/quizsurvival lang <zh|en> §7- Switch question language"
                                   : "§e§l===== 答题生存 指令帮助 =====\n"
                                        + "§a/quizsurvival reload §7- 重载配置和题库\n"
                                        + "§a/quizsurvival stats [玩家] §7- 查看答题统计\n"
                                        + "§a/quizsurvival time <秒数> §7- 设置倒计时(0=关闭)\n"
                                        + "§a/quizsurvival lang <zh|en> §7- 切换题库语言"
                        ), false);
                        return 1;
                    })
                    // /quizsurvival reload - 热重载
                    .then(CommandManager.literal("reload")
                            .requires(src -> {
                                try {
                                    var player = src.getPlayerOrThrow();
                                    return src.getServer().getPlayerManager().isOperator(
                                            new net.minecraft.server.PlayerConfigEntry(player.getGameProfile()));
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                            .executes(ctx -> {
                                config = QuizConfig.load();
                                int count = quizManager.reload();
                                String feedback = isEn()
                                        ? "§a[Quiz Survival] Config & questions reloaded! " + count + " questions, timer: " + config.quizTimeLimit + "s"
                                        : "§a[答题生存] 配置和题库已重载！当前共 " + count + " 道题目，倒计时 " + config.quizTimeLimit + " 秒";
                                ctx.getSource().sendFeedback(() -> Text.literal(feedback), true);
                                return 1;
                            }))
                    // /quizsurvival stats [player] - 答题统计
                    .then(CommandManager.literal("stats")
                            .executes(ctx -> {
                                if (ctx.getSource().getPlayer() != null) {
                                    showStats(ctx.getSource().getPlayer(), ctx.getSource().getPlayer());
                                }
                                return 1;
                            })
                            .then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
                                    .executes(ctx -> {
                                        ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType
                                                .getPlayer(ctx, "player");
                                        showStats(ctx.getSource().getPlayer(), target);
                                        return 1;
                                    })))
                    // /quizsurvival time <seconds> - 修改倒计时
                    .then(CommandManager.literal("time")
                            .requires(src -> {
                                try {
                                    var p = src.getPlayerOrThrow();
                                    return src.getServer().getPlayerManager().isOperator(
                                            new net.minecraft.server.PlayerConfigEntry(p.getGameProfile()));
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                            .then(CommandManager
                                    .argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 300))
                                    .executes(ctx -> {
                                        int seconds = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx,
                                                "seconds");
                                        config.quizTimeLimit = seconds;
                                        config.save();
                                        String msg;
                                        if (isEn()) {
                                            msg = seconds == 0 ? "§a[Quiz Survival] Timer disabled!" : "§a[Quiz Survival] Timer set to " + seconds + "s!";
                                        } else {
                                            msg = seconds == 0 ? "§a[答题生存] 已关闭答题倒计时！" : "§a[答题生存] 答题倒计时已设为 " + seconds + " 秒！";
                                        }
                                        ctx.getSource().sendFeedback(() -> Text.literal(msg), true);
                                        return 1;
                                    })))
                    // /quizsurvival lang <zh|en> - 切换语言
                    .then(CommandManager.literal("lang")
                            .requires(src -> {
                                try {
                                    var p = src.getPlayerOrThrow();
                                    return src.getServer().getPlayerManager().isOperator(
                                            new net.minecraft.server.PlayerConfigEntry(p.getGameProfile()));
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                            .then(CommandManager.literal("zh").executes(ctx -> switchLang(ctx, "zh")))
                            .then(CommandManager.literal("en").executes(ctx -> switchLang(ctx, "en"))))
            );
        });

        LOGGER.info("[答题生存] 已加载 {} 道题目！倒计时 {} 秒", quizManager.getQuestionCount(), config.quizTimeLimit);
    }

    private static int switchLang(com.mojang.brigadier.context.CommandContext<net.minecraft.server.command.ServerCommandSource> ctx, String lang) {
        config.questionLanguage = lang;
        config.save();
        int count = quizManager.reload();
        String feedback = "en".equals(lang)
                ? "§a[Quiz Survival] Language switched to English! " + count + " questions loaded"
                : "§a[答题生存] 语言已切换为中文！已加载 " + count + " 道题目";
        ctx.getSource().sendFeedback(() -> Text.literal(feedback), true);
        return 1;
    }

    private static void showStats(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        int[] stats = quizManager.getStats(target.getUuid());
        String rate = stats[0] > 0 ? String.format("%.1f%%", stats[1] * 100.0 / stats[0]) : "N/A";
        String name = target.getName().getString();
        if (isEn()) {
            viewer.sendMessage(Text.literal("§e§l===== Quiz Stats: §f" + name + " §e§l====="));
            viewer.sendMessage(Text.literal(
                    "§7Total: §f" + stats[0] + " §7| Correct: §a" + stats[1] + " §7| Wrong: §c" + stats[2] + " §7| Rate: §b" + rate));
        } else {
            viewer.sendMessage(Text.literal("§e§l===== 答题统计: §f" + name + " §e§l====="));
            viewer.sendMessage(Text.literal(
                    "§7总答题: §f" + stats[0] + " §7| 正确: §a" + stats[1] + " §7| 错误: §c" + stats[2] + " §7| 正确率: §b" + rate));
        }
    }

    public static void triggerQuiz(ServerPlayerEntity player, String type) {
        pendingQuizType.put(player.getUuid(), type);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, config.effectDuration, config.miningFatigueLevel, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, config.effectDuration, config.slownessLevel, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, config.effectDuration, 255, false, false));
        player.sendMessage(Text.literal(isEn() ? "§e§l⚠ Complete the quiz to continue!" : "§e§l⚠ 请完成答题后继续游戏！"), true);
        sendQuiz(player);
    }

    public static void sendQuiz(ServerPlayerEntity player) {
        Question q = quizManager.getRandomQuestion(player.getUuid());
        int quizId = quizManager.startQuiz(player.getUuid(), q);
        String type = pendingQuizType.getOrDefault(player.getUuid(), "other");
        String typeName = getTypeName(type);
        int wc = wrongCount.getOrDefault(player.getUuid(), 0);
        int timeLimit = config.quizTimeLimit;
        quizStartTime.put(player.getUuid(), System.currentTimeMillis());
        ServerPlayNetworking.send(player,
                new QuizPayloads.OpenQuizS2C(q.text(), q.options(), quizId, typeName, wc, timeLimit, config.wrongDamageBase, config.wrongDamageMax));
    }

    public static String getTypeName(String type) {
        boolean en = config != null && "en".equals(config.questionLanguage);
        return switch (type) {
            case "chop" -> en ? "§a[Chop]§r Wood" : "§a[砍伐]§r 木头";
            case "ore" -> en ? "§b[Mine]§r Ore" : "§b[挖掘]§r 矿物";
            case "stone" -> en ? "§7[Mine]§r Stone" : "§7[挖掘]§r 石头";
            case "dirt" -> en ? "§6[Mine]§r Dirt" : "§6[挖掘]§r 泥土";
            case "interact" -> en ? "§d[Interact]§r Block" : "§d[交互]§r 方块";
            case "combat" -> en ? "§c[Combat]§r Attack" : "§c[战斗]§r 攻击";
            case "jump" -> en ? "§3[Jump]§r Jump" : "§3[跳跃]§r 跳跃";
            case "other" -> en ? "§f[Mine]§r Block" : "§f[挖掘]§r 方块";
            default -> "§f[?]§r " + type;
        };
    }

    /** 解冻某玩家冻结的所有怪物 */
    private static void unfreezeAll(UUID playerUuid) {
        java.util.List<MobEntity> frozen = frozenMobs.remove(playerUuid);
        if (frozen == null)
            return;
        for (MobEntity mob : frozen) {
            if (mob.isAlive()) {
                // 多人：如果其他玩家也冻结了这只怪，不要解冻
                boolean frozenByOther = false;
                for (var entry : frozenMobs.entrySet()) {
                    if (!entry.getKey().equals(playerUuid) && entry.getValue().contains(mob)) {
                        frozenByOther = true;
                        break;
                    }
                }
                if (!frozenByOther) {
                    mob.setAiDisabled(false);
                    mob.setNoGravity(false);
                }
            }
        }
    }

    /** 冻结玩家附近的所有怪物 */
    private static void freezeNearbyMobs(ServerPlayerEntity player, net.minecraft.world.World world) {
        int radius = config.freezeRadius;
        java.util.List<MobEntity> frozen = new java.util.ArrayList<>();
        for (Entity e : world.getOtherEntities(player, player.getBoundingBox().expand(radius),
                ent -> ent instanceof MobEntity)) {
            MobEntity mob = (MobEntity) e;
            mob.setAiDisabled(true);
            mob.setNoGravity(true);
            mob.setVelocity(0, 0, 0);
            if (mob instanceof CreeperEntity cr)
                cr.setFuseSpeed(-1);
            frozen.add(mob);
        }
        frozenMobs.put(player.getUuid(), frozen);
    }

    public static void syncActions(ServerPlayerEntity player) {
        var map = quizManager.getActionsMap(player.getUuid());
        StringBuilder sb = new StringBuilder();
        for (var entry : map.entrySet()) {
            if (sb.length() > 0)
                sb.append(",");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }

        ServerPlayNetworking.send(player, new QuizPayloads.SyncActionsS2C(sb.toString()));
    }
}
