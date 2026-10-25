package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.bounty.Rogues;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonRogues;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import io.github.jcondedata.aliveworkplace.trainer.MegaShrines;
import io.github.jcondedata.aliveworkplace.work.Bank;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-d-1025-0108): two Mega Shrine (38.7) gaps the main QA class leaves out because the mod fails them today
 * (filed as bugs): a villager at work by the pedestal gets the show's mood, and a broken pedestal still holds the show.
 */
public class QaMegaShrineGapsCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos ORIGIN = new BlockPos(2, 1, 5);
	private static final BlockPos SHRINE_HALL = new BlockPos(1, 2, 1);

	private static BlueprintData.Placement placement(GameTestHelper helper) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
	}

	/** The festival's hours today, the village radius wide enough; with {@code finished} a finished shrine is remembered. */
	private static void festival(GameTestHelper helper, boolean finished) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = placement(helper);
		if (finished) {
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.MEGA_SHRINE.id(), placement, UUID.randomUUID());
		}
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = level.getDayTime();
		MegaShrines.forget();
		level.setDayTime((Chronicle.day(level) - 1) * 24000L + Festivals.START + 100);
		PartnerShowsCompatTests.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(placement);
			VillageHalls.RADIUS = radius;
			MegaBracelets.ENABLED = true;
			Festivals.forget();
			MegaShrines.forget();
			level.setDayTime(time);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(4))) {
				v.discard();
			}
		});
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, boolean trainer) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		if (trainer) {
			v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(3));
		}
		v.setNoAi(true);
		return v;
	}

	/**
	 * 38.7: villagers off work gather and get the mood. A farmer at work by the pedestal is not one of the crowd: the
	 * show is held for the crowd and the farmer gets no mood from it.
	 */
	//$ gametest_batch AREA '"qa_shrine_worker"'
	@GameTest(template = AREA, batch = "qa_shrine_worker")
	public void aVillagerAtWorkGetsNoShowMood(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		festival(helper, true);
		BlockPos hall = helper.absolutePos(SHRINE_HALL);
		villager(helper, new BlockPos(5, 4, 7), true);
		Villager watcher = villager(helper, new BlockPos(5, 2, 2), false);
		Villager worker = villager(helper, new BlockPos(7, 2, 6), false);
		worker.setVillagerData(worker.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(7, 2, 7))));
		worker.getBrain().setActiveActivityIfPossible(Activity.WORK);
		helper.assertTrue(!Festivals.offWork(worker), "the farmer isn't at work: " + worker.getBrain().getActiveNonCoreActivity());
		MegaShrines.tick(level, hall);
		helper.assertTrue(Long.valueOf(Chronicle.day(level)).equals(ModAttachments.MEGA_SHOW_DAY.get(watcher)), "the show wasn't held for the crowd");
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(worker) == null && MegaShrines.mood(level, worker) == 0,
			"a villager at work got the show's mood: " + MegaShrines.mood(level, worker));
		helper.succeed();
	}

	/**
	 * A shrine whose pedestal was broken is no finished shrine: no trainer Mega Evolves "by the pedestal" at nothing,
	 * and nobody gets the mood. With the pedestal there the show is held (the other side of the boundary).
	 */
	//$ gametest_batch AREA '"qa_shrine_broken"'
	@GameTest(template = AREA, batch = "qa_shrine_broken")
	public void aBrokenPedestalHoldsNoShow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		festival(helper, true);
		BlockPos hall = helper.absolutePos(SHRINE_HALL);
		BlockPos pedestal = MegaShrines.pedestal(level, hall).orElseThrow();
		net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(MegaShrines.PEDESTAL);
		helper.assertTrue(block != Blocks.AIR, "no Mega Showdown pedestal block");
		level.setBlockAndUpdate(pedestal, block.defaultBlockState());
		villager(helper, new BlockPos(5, 4, 7), true);
		Villager watcher = villager(helper, new BlockPos(5, 2, 2), false);
		MegaShrines.tick(level, hall);
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(watcher) != null, "no show with the pedestal standing");
		// Next festival: the pedestal has been broken.
		ModAttachments.MEGA_SHOW_DAY.remove(watcher);
		MegaShrines.forget();
		level.setBlockAndUpdate(pedestal, Blocks.AIR.defaultBlockState());
		MegaShrines.tick(level, hall);
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(watcher) == null, "the show was held with the shrine's pedestal broken");
		helper.succeed();
	}
}
