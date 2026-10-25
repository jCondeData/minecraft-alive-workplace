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
 * QA (lane-d-1025-0108): Mega Shrine (38.7) gaps the main QA class left out because the mod failed them (filed as bugs).
 * B420: a villager at work by the pedestal got the show's mood though he never joined the crowd.
 * B421: a shrine whose pedestal was broken still held the show at the empty spot.
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
			// The shrine's pedestal stands, as a builder leaves it (B421: without it there is no show).
			level.setBlockAndUpdate(io.github.jcondedata.aliveworkplace.hall.Arenas.world(placement, MegaShrines.PEDESTAL_SPOT),
				net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(MegaShrines.PEDESTAL).defaultBlockState());
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

	/** The mood's reasons {@code villager} reads now, as translation keys. */
	private static List<String> reasons(ServerLevel level, Villager villager) {
		return Moods.work(level, villager).good().stream()
			.map(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : c.getString())
			.toList();
	}

	/**
	 * B420, the other side: a villager off work who came to the show reads +3 and "watched a Mega Evolution at the shrine",
	 * a farmer at his job beside him reads neither, and both still read so after a save and reload.
	 */
	//$ gametest_batch AREA '"qa_shrine_worker_reload"'
	@GameTest(template = AREA, batch = "qa_shrine_worker_reload")
	public void theCrowdKeepsTheShowMoodAndTheWorkerNoneAfterReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		festival(helper, true);
		BlockPos hall = helper.absolutePos(SHRINE_HALL);
		villager(helper, new BlockPos(5, 4, 7), true);
		Villager watcher = villager(helper, new BlockPos(5, 2, 2), false);
		Villager worker = villager(helper, new BlockPos(7, 2, 6), false);
		worker.setVillagerData(worker.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(7, 2, 7))));
		worker.getBrain().setActiveActivityIfPossible(Activity.WORK);
		helper.assertTrue(Festivals.offWork(watcher) && !Festivals.offWork(worker), "the watcher must be off work and the farmer at work");
		MegaShrines.tick(level, hall);
		String key = "mood.aliveworkplace.reason.mega_show";
		helper.assertTrue(MegaShrines.mood(level, watcher) == MegaShrines.MOOD && reasons(level, watcher).contains(key),
			"the crowd's watcher didn't get the show's mood: " + MegaShrines.mood(level, watcher) + " " + reasons(level, watcher));
		helper.assertTrue(MegaShrines.mood(level, worker) == 0 && !reasons(level, worker).contains(key),
			"the farmer at work reads the show's mood: " + reasons(level, worker));
		helper.assertTrue(Language.getInstance().has(key) && Language.getInstance().getOrDefault(key).equals("watched a Mega Evolution at the shrine"),
			"the reason's text: " + Language.getInstance().getOrDefault(key));
		for (Villager v : List.of(watcher, worker)) {
			CompoundTag saved = v.saveWithoutId(new CompoundTag());
			Villager loaded = EntityType.VILLAGER.create(level);
			helper.assertTrue(loaded != null, "no villager to load into");
			loaded.load(saved);
			int expected = v == watcher ? MegaShrines.MOOD : 0;
			helper.assertTrue(MegaShrines.mood(level, loaded) == expected,
				(v == watcher ? "the watcher" : "the farmer") + " reads " + MegaShrines.mood(level, loaded) + " after a reload, expected " + expected);
			loaded.discard();
		}
		helper.succeed();
	}

	/**
	 * B421: a shrine whose pedestal was broken is no finished shrine: no trainer Mega Evolves "by the pedestal" at nothing,
	 * no player reads the show's line and nobody gets the mood. With the pedestal there the show is held (the other side
	 * of the boundary), and once it is put back the next festival's show is held again.
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
		// Next festival: the pedestal has been broken. A player stands by to read any show's line.
		ModAttachments.MEGA_SHOW_DAY.remove(watcher);
		MegaShrines.forget();
		level.setBlockAndUpdate(pedestal, Blocks.AIR.defaultBlockState());
		List<Component> read = new java.util.ArrayList<>();
		ServerPlayer player = QaCampHabitatCompatTests.listener(helper, read, "ShrineWatcher");
		player.teleportTo(pedestal.getX() + 0.5, pedestal.getY(), pedestal.getZ() - 4.5);
		String line = "Mega Evolves their ace at the Mega Shrine";
		helper.assertTrue(MegaShrines.pedestal(level, hall).isEmpty(), "a broken pedestal is still the shrine's pedestal");
		helper.assertTrue(!MegaShrines.tick(level, hall), "the festival's crowd taken to a shrine with its pedestal broken");
		helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(watcher) == null && MegaShrines.mood(level, watcher) == 0,
			"the show was held with the shrine's pedestal broken: mood " + MegaShrines.mood(level, watcher));
		helper.assertTrue(read.stream().noneMatch(c -> c.getString().contains(line)), "a player read the show's line at a broken pedestal: " + read);
		// The pedestal put back: the shrine holds its show again.
		level.setBlockAndUpdate(pedestal, block.defaultBlockState());
		helper.assertTrue(MegaShrines.pedestal(level, hall).equals(java.util.Optional.of(pedestal)), "the rebuilt pedestal isn't the shrine's");
		MegaShrines.tick(level, hall);
		helper.assertTrue(Long.valueOf(Chronicle.day(level)).equals(ModAttachments.MEGA_SHOW_DAY.get(watcher)) && MegaShrines.mood(level, watcher) == MegaShrines.MOOD,
			"no show once the pedestal was put back");
		helper.assertTrue(read.stream().anyMatch(c -> c.getString().contains(line)), "the player didn't read the show's line once the pedestal was back: " + read);
		helper.succeed();
	}
}
