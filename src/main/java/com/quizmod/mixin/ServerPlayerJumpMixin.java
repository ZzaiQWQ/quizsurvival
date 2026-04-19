package com.quizmod.mixin;

import com.quizmod.QuizMod;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerJumpMixin {

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void onJump(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (player.isCreative() || player.isSpectator()) return;

        // 正在答题中不允许跳跃
        if (QuizMod.quizManager.hasActiveQuiz(player.getUuid())) {
            ci.cancel();
            return;
        }

        // 有免答次数则消耗
        if (QuizMod.quizManager.hasRemainingActions(player.getUuid(), "jump")) {
            QuizMod.quizManager.useAction(player.getUuid(), "jump");
            QuizMod.syncActions(player);
            return;
        }

        // 触发答题，取消跳跃
        ci.cancel();
        QuizMod.triggerQuiz(player, "jump");
    }
}
