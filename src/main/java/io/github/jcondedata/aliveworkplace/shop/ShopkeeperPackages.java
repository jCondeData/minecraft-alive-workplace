package io.github.jcondedata.aliveworkplace.shop;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

/** The Shopkeeper's WORK activity: mind the counter. */
public final class ShopkeeperPackages {
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, BehaviorBuilder.<Villager>triggerIf(v -> {
				WorkerStatus.set(v, Component.translatable("message.aliveworkplace.shop.title", v.getAttachedOrElse(ModAttachments.SHOP_SALES, 0)), -1f,
					Component.translatable("message.aliveworkplace.shop.state.open").withStyle(ChatFormatting.GRAY));
				return false;
			})),
			Pair.of(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.JOB_SITE, speed, 3, 100, 1200)),
			Pair.of(5, StrollAroundPoi.create(MemoryModuleType.JOB_SITE, 0.4f, 2)),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	private ShopkeeperPackages() {
	}
}
