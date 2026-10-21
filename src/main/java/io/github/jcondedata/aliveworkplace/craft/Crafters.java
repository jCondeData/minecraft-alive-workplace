package io.github.jcondedata.aliveworkplace.craft;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The retired Crafter (ROADMAP 37.14, retired by 37.14a, owner 2026-10-21: "each job should craft what the builder or
 * other villagers need when they need it"). Its whole-chain planning lives on in the jobs that craft for the builders
 * ({@link Chains}). No villager takes a Crafter's Workbench any more, and a Crafter in an older save becomes jobless the
 * first time they tick, their bag dropped at their feet. The block, its item, the job and the workshop's structures stay
 * registered so old saves load.
 */
public final class Crafters {
	/** The Crafter's job is retired: no new hires, no workshop in the Blueprint Table, the Steward's plans or villages. */
	public static final boolean ENABLED = false;

	public static boolean isCrafter(Villager villager) {
		return villager.getVillagerData().getProfession() == ModVillagers.CRAFTER;
	}

	/** A Crafter's brain before they're let go (the first tick does it): nothing but the schedule. */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	/**
	 * Every tick (mixin/VillagerMixin): a Crafter from an older save is let go. Jobless again (their trades, levels and
	 * the workbench's ticket gone), the job in hand forgotten and what they carried dropped where they stand, so nothing
	 * is lost.
	 */
	public static void retire(Villager villager) {
		if (!isCrafter(villager) || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		villager.releasePoi(MemoryModuleType.JOB_SITE); // (before the job goes: it checks the job holds the block)
		villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE).setLevel(1));
		villager.setVillagerXp(0);
		ModAttachments.CRAFTER_JOB.remove(villager);
		BuilderBag bag = ModAttachments.BUILDER_BAG.get(villager);
		if (bag != null) {
			for (ItemStack stack : bag.takeAll()) {
				Block.popResource(level, villager.blockPosition(), stack);
			}
		}
		villager.refreshBrain(level);
	}

	private Crafters() {
	}
}
