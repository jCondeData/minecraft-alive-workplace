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
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;

/**
 * The retired Crafter (ROADMAP 37.14 made it, 37.14a retired it, owner 2026-10-21: "each job should craft what the
 * builder or other villagers need when they need it"). Its whole-chain way of working moved into the jobs that already
 * craft for builders ({@link CrafterWork#chains}); what none of them makes is named on the builder's shortage line
 * ({@link JobShortages}). The profession, the Crafter's Workbench and its ids stay registered so old saves load, but no
 * villager takes the workbench, it isn't in the creative tab or any trade, and a Crafter from an old save becomes
 * jobless the first moment it ticks: what it carried is dropped at its feet, the job it had in hand let go.
 */
public final class Crafters {
	/** Off for good (37.14a): kept so the places that asked still read as "no Crafter". */
	public static final boolean ENABLED = false;

	public static boolean isCrafter(Villager villager) {
		return villager.getVillagerData().getProfession() == ModVillagers.CRAFTER;
	}

	/** A Crafter's WORK activity, should one ever get there before {@link #tick}: nothing but standing about. */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	/** Every villager's AI step (mixin/VillagerMixin): a Crafter from an old save is made jobless. */
	public static void tick(Villager villager) {
		if (villager.level() instanceof ServerLevel level && isCrafter(villager)) {
			retire(level, villager);
		}
	}

	/**
	 * Makes a Crafter jobless (37.14a): the workbench let go, the job in hand dropped (its claim on a request too), the bag
	 * dropped where they stand, the trades and levels gone; a jobless villager takes another job by itself.
	 */
	public static void retire(ServerLevel level, Villager villager) {
		villager.releasePoi(MemoryModuleType.JOB_SITE); // the workbench's ticket let go, and the memory with it
		villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		CrafterJob job = ModAttachments.CRAFTER_JOB.get(villager);
		if (job != null && !job.claim().isEmpty()) {
			CrafterWork.release(job.claim());
		}
		ModAttachments.CRAFTER_JOB.remove(villager);
		BuilderBag bag = ModAttachments.BUILDER_BAG.get(villager);
		if (bag != null) {
			for (ItemStack stack : bag.takeAll()) {
				Block.popResource(level, villager.blockPosition(), stack);
			}
		}
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE).setLevel(1));
		villager.setVillagerXp(0);
		villager.setOffers(new MerchantOffers());
		villager.refreshBrain(level);
	}

	private Crafters() {
	}
}
