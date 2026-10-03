package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/** Bugs the tester found in ROADMAP 21.1a (fewer job blocks). Each fails until it's fixed. */
public class StationsBugGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos STATION = new BlockPos(3, 2, 3);
	private static final BlockPos STANDING = new BlockPos(4, 2, 4);

	/**
	 * README: "Shepherd | Loom (vanilla) | ... | nothing — or sneak-right-click with shears to hire them", and "hiring is as
	 * before". Shears also pick the Beekeeper, so the job picker takes the click first: with no beehive near, the player
	 * gets "The Beekeeper job needs a Beehive" and the shepherd isn't hired (with a beehive near, the shepherd becomes a
	 * beekeeper).
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aShepherdIsStillHiredWithShears(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.LOOM);
		Villager shepherd = helper.spawn(EntityType.VILLAGER, STANDING);
		Jobs.employ(helper.getLevel(), shepherd, helper.absolutePos(STATION), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		StationsSpecGameTests.rightClick(player, shepherd, new ItemStack(Items.SHEARS), true);
		helper.assertTrue(StationsSpecGameTests.job(shepherd) == VillagerProfession.SHEPHERD,
			"the shepherd became a " + StationsSpecGameTests.name(StationsSpecGameTests.job(shepherd)));
		helper.assertTrue(ModAttachments.BUILDER_EMPLOYER.get(shepherd) != null, "sneak-right-clicking the shepherd with shears didn't hire them");
		helper.succeed();
	}

	/**
	 * The owner's live server: a Crafting Table, Jukebox, Mailbox or Blueprint Table placed before 21.1a had no point of
	 * interest then. Where the same 16-block chunk section already had one (a bed, a composter, any workstation: most
	 * houses), the game marks that section's records complete and never looks at its blocks again when the chunk loads
	 * (PoiManager.checkConsistencyWithBlocks only refreshes invalid sections). So those blocks stay invisible: "The
	 * Carpenter job needs a Crafting Table" beside one, builders don't take the Blueprint Table, the mailbox is no post
	 * office. Here: the block without its record, as 0.137.0 saved it, then exactly what loading the chunk does.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void oldWorldsBlocksBecomeWorkstationsWhenTheChunkLoads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PoiManager poi = level.getPoiManager();
		List<Block> blocks = List.of(Blocks.CRAFTING_TABLE, Blocks.JUKEBOX, ModBlocks.MAILBOX, ModBlocks.BLUEPRINT_TABLE);
		List<String> invisible = new ArrayList<>();
		for (int i = 0; i < blocks.size(); i++) {
			BlockPos spot = new BlockPos(3 + 4 * i, 2, 12);
			BlockPos at = helper.absolutePos(spot);
			// A composter beside it in the same chunk section: the section already has a point of interest.
			BlockPos composter = SectionPos.of(at).equals(SectionPos.of(helper.absolutePos(spot.east()))) ? spot.east() : spot.west();
			helper.setBlock(composter, Blocks.COMPOSTER);
			helper.setBlock(spot, blocks.get(i));
			helper.assertTrue(SectionPos.of(at).equals(SectionPos.of(helper.absolutePos(composter))), "setup: not one chunk section");
			poi.remove(at); // 0.137.0 had no point of interest for this block
			LevelChunk chunk = level.getChunkAt(at);
			poi.checkConsistencyWithBlocks(SectionPos.of(at), chunk.getSection(chunk.getSectionIndex(at.getY()))); // the chunk loads
			if (poi.getType(at).isEmpty()) {
				invisible.add(BuiltInRegistries.BLOCK.getKey(blocks.get(i)).toString());
			}
		}
		helper.assertTrue(invisible.isEmpty(), "old blocks that never become workstations after the upgrade: " + invisible);
		helper.succeed();
	}

	/**
	 * Two villagers standing by the first of two beehives, both sneak-right-clicked with a glass bottle: both are made
	 * beekeepers at the same hive (a hive is free for anyone), then vanilla's check for two workers at one block takes
	 * the hive from one of them, and a beekeeper can't take a hive again by themselves (hives have no free places), so
	 * they never work (their work needs a workstation) though the second hive stands free.
	 */
	//$ gametest_ticks_batch AREA '400' '"stationsBugBeekeepers"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "stationsBugBeekeepers")
	public void twoBeekeepersByTwoHivesBothKeepAHive(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, Blocks.BEEHIVE);
		helper.setBlock(new BlockPos(6, 2, 3), Blocks.BEEHIVE);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager first = helper.spawn(EntityType.VILLAGER, STANDING);
		Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 5));
		StationsSpecGameTests.rightClick(player, first, new ItemStack(Items.GLASS_BOTTLE), true);
		StationsSpecGameTests.rightClick(player, second, new ItemStack(Items.GLASS_BOTTLE), true);
		helper.assertTrue(StationsSpecGameTests.job(first) == ModVillagers.BEEKEEPER && StationsSpecGameTests.job(second) == ModVillagers.BEEKEEPER,
			"setup: both should be beekeepers");
		helper.runAfterDelay(300, () -> {
			Optional<BlockPos> a = StationsSpecGameTests.site(first);
			Optional<BlockPos> b = StationsSpecGameTests.site(second);
			helper.assertTrue(StationsSpecGameTests.job(first) == ModVillagers.BEEKEEPER && StationsSpecGameTests.job(second) == ModVillagers.BEEKEEPER,
				"a beekeeper lost the job");
			helper.assertTrue(a.isPresent() && b.isPresent(), "a beekeeper has no hive to work at: first " + a + ", second " + b);
			helper.succeed();
		});
	}

	/**
	 * Decision (1) of 21.1a: our workers take a free block of their kind again by themselves, as vanilla workers do (an
	 * orchard keeper whose composter is moved takes the new one: StationsFixesGameTests). A player moves the beehive
	 * (silk touch, a few blocks over): the beekeeper never takes it, because a hive has no free places for anyone
	 * (bees don't take them), so they stand jobless for good. Before 21.1a the Apiary had a place, so this worked.
	 */
	//$ gametest_ticks_batch AREA '800' '"stationsBugHiveMoved"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "stationsBugHiveMoved")
	public void aBeekeeperWhoseHiveMovedTakesTheNewOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, Blocks.BEEHIVE);
		Villager keeper = helper.spawn(EntityType.VILLAGER, STANDING);
		StationsSpecGameTests.rightClick(StationsSpecGameTests.player(helper), keeper, new ItemStack(Items.GLASS_BOTTLE), true);
		helper.assertTrue(StationsSpecGameTests.job(keeper) == ModVillagers.BEEKEEPER, "setup: not a beekeeper");
		helper.setBlock(STATION, Blocks.AIR);
		BlockPos moved = new BlockPos(7, 2, 6);
		helper.setBlock(moved, Blocks.BEEHIVE);
		helper.succeedWhen(() -> helper.assertTrue(StationsSpecGameTests.site(keeper).equals(Optional.of(helper.absolutePos(moved)))
			&& StationsSpecGameTests.job(keeper) == ModVillagers.BEEKEEPER,
			"the beekeeper works at " + StationsSpecGameTests.site(keeper) + " as " + StationsSpecGameTests.name(StationsSpecGameTests.job(keeper))));
	}

	/**
	 * Two beekeepers by two hives, where the first beekeeper is away from their hive (more than 48 blocks: asleep in a bed
	 * across the village) when the player makes the second one: each must end up with their own hive and keep it once
	 * the first comes back. The fix counts a hive as taken only if its worker is within 48 blocks, so the second is given
	 * the first's hive; when they meet, vanilla takes it from one of them, who never gets a hive again (above).
	 */
	//$ gametest_ticks_batch AREA '500' '"stationsBugBeekeeperAway"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "stationsBugBeekeeperAway")
	public void aBeekeeperAwayFromTheirHiveStillKeepsIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, Blocks.BEEHIVE);
		helper.setBlock(new BlockPos(8, 2, 3), Blocks.BEEHIVE);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager first = helper.spawn(EntityType.VILLAGER, STANDING);
		StationsSpecGameTests.rightClick(player, first, new ItemStack(Items.GLASS_BOTTLE), true);
		Optional<BlockPos> a = StationsSpecGameTests.site(first);
		helper.assertTrue(a.equals(Optional.of(helper.absolutePos(STATION))), "setup: the first beekeeper works at " + a);
		// Away: 55 blocks straight up, held still (the same chunk, so it stays loaded).
		first.setNoAi(true);
		first.setNoGravity(true);
		first.teleportTo(first.getX(), first.getY() + 55, first.getZ());
		Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 4));
		StationsSpecGameTests.rightClick(player, second, new ItemStack(Items.GLASS_BOTTLE), true);
		helper.assertTrue(StationsSpecGameTests.job(second) == ModVillagers.BEEKEEPER, "setup: the second isn't a beekeeper");
		Optional<BlockPos> given = StationsSpecGameTests.site(second);
		// Back home.
		BlockPos home = helper.absolutePos(STANDING);
		first.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
		first.setNoGravity(false);
		first.setNoAi(false);
		helper.runAfterDelay(400, () -> {
			Optional<BlockPos> x = StationsSpecGameTests.site(first);
			Optional<BlockPos> y = StationsSpecGameTests.site(second);
			helper.assertTrue(x.isPresent() && y.isPresent() && !x.equals(y),
				"the beekeepers don't each have a hive: first " + x + ", second " + y + " (the second was given " + given + " at the click)");
			helper.succeed();
		});
	}

	/**
	 * Round 3. "A beekeeper who has no hive takes the nearest beehive or bee nest nobody works at": a hive whose beekeeper
	 * is more than 48 blocks off (asleep in a bed across the village, or in a chunk that isn't loaded) counts as nobody's,
	 * so a beekeeper whose own hive broke takes it. When the owner comes back, vanilla's competitor scan takes the hive
	 * from one of them; with equal experience that is whoever is checked first, here the owner, who is left without one.
	 */
	//$ gametest_ticks_batch AREA '800' '"stationsBugRetakeAway"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "stationsBugRetakeAway")
	public void aBeekeeperWithoutAHiveDoesntTakeTheHiveOfOneAway(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, Blocks.BEEHIVE);
		BlockPos theirs = new BlockPos(8, 2, 3);
		helper.setBlock(theirs, Blocks.BEEHIVE);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager owner = helper.spawn(EntityType.VILLAGER, STANDING);
		StationsSpecGameTests.rightClick(player, owner, new ItemStack(Items.GLASS_BOTTLE), true);
		Villager other = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 4));
		StationsSpecGameTests.rightClick(player, other, new ItemStack(Items.GLASS_BOTTLE), true);
		BlockPos hive = helper.absolutePos(STATION);
		helper.assertTrue(StationsSpecGameTests.site(owner).equals(Optional.of(hive)) && StationsSpecGameTests.site(other).equals(Optional.of(helper.absolutePos(theirs))),
			"setup: owner at " + StationsSpecGameTests.site(owner) + ", other at " + StationsSpecGameTests.site(other));
		// The owner is away: 55 blocks straight up, held still (the same chunk, so it stays loaded).
		owner.setNoAi(true);
		owner.setNoGravity(true);
		owner.teleportTo(owner.getX(), owner.getY() + 55, owner.getZ());
		helper.setBlock(theirs, Blocks.AIR);
		helper.runAfterDelay(250, () -> {
			helper.assertTrue(!StationsSpecGameTests.site(other).equals(Optional.of(hive)),
				"the beekeeper whose hive broke took the hive " + hive + " that the other beekeeper (away, in bed) works at");
			BlockPos home = helper.absolutePos(STANDING);
			owner.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
			owner.setNoGravity(false);
			owner.setNoAi(false);
			helper.runAfterDelay(300, () -> {
				helper.assertTrue(StationsSpecGameTests.site(owner).equals(Optional.of(hive)),
					"back home, the owner lost their hive: owner at " + StationsSpecGameTests.site(owner) + ", other at " + StationsSpecGameTests.site(other));
				helper.succeed();
			});
		});
	}

	/**
	 * A worker whose block is gone while they are more than 16 blocks from it still remembers it (vanilla only checks a
	 * workstation from up close): a farmer's composter blown up while they sleep across the village. The player puts a
	 * composter by them and sneak-right-clicks them with bone meal: Stations.assign lets go of the old block with
	 * PoiManager.release, which throws "POI never registered" where no block is left (vanilla's Villager.releasePoi
	 * checks first). In the game that exception is thrown while the server handles the click packet.
	 */
	//$ gametest_ticks_batch HUGE '200' '"stationsBugGoneOldBlock"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "stationsBugGoneOldBlock")
	public void aWorkerWhoseOldBlockIsGoneFarAwayCanBeGivenAJob(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos old = new BlockPos(3, 2, 3);
		helper.setBlock(old, Blocks.COMPOSTER);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		StationsSpecGameTests.rightClick(player, keeper, new ItemStack(Items.SWEET_BERRIES), true);
		helper.assertTrue(StationsSpecGameTests.job(keeper) == ModVillagers.ORCHARD_KEEPER, "setup: not an orchard keeper");
		BlockPos away = helper.absolutePos(new BlockPos(25, 2, 25));
		keeper.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		helper.setBlock(old, Blocks.AIR);
		BlockPos fresh = new BlockPos(26, 2, 25);
		helper.setBlock(fresh, Blocks.COMPOSTER);
		helper.runAfterDelay(5, () -> {
			StationsSpecGameTests.rightClick(player, keeper, new ItemStack(Items.BONE_MEAL), true);
			helper.assertTrue(StationsSpecGameTests.job(keeper) == ModVillagers.COMPOSTER
					&& StationsSpecGameTests.site(keeper).equals(Optional.of(helper.absolutePos(fresh))),
				"the villager is a " + StationsSpecGameTests.name(StationsSpecGameTests.job(keeper)) + " at " + StationsSpecGameTests.site(keeper));
			helper.succeed();
		});
	}

	/**
	 * Bug B8: a worker's block is broken while they're away and put back; someone else takes the new one; then the first
	 * worker is given another job (with an item, or from the Village Hall). Letting go of their old site must not free the
	 * second worker's place, or a third villager could share it.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void reassigningAWorkerKeepsSomeoneElsesBlockTaken(GameTestHelper helper) {
		reassignAfterTheBlockChangedHands(helper, false);
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void assigningFromTheHallKeepsSomeoneElsesBlockTaken(GameTestHelper helper) {
		reassignAfterTheBlockChangedHands(helper, true);
	}

	private static void reassignAfterTheBlockChangedHands(GameTestHelper helper, boolean fromTheHall) {
		ServerLevel level = helper.getLevel();
		BlockPos lectern = helper.absolutePos(STATION);
		BlockPos composter = helper.absolutePos(new BlockPos(9, 2, 9));
		helper.setBlock(STATION, Blocks.LECTERN);
		helper.setBlock(new BlockPos(9, 2, 9), Blocks.COMPOSTER);
		Villager first = helper.spawn(EntityType.VILLAGER, STANDING);
		first.setNoAi(true); // (away: their brain doesn't see the block go)
		Jobs.employ(level, first, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		helper.setBlock(STATION, Blocks.AIR);
		helper.setBlock(STATION, Blocks.LECTERN);
		Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 3));
		Jobs.employ(level, second, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 0, "the second librarian didn't take the new lectern");
		if (fromTheHall) {
			var poi = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE)
				.getHolderOrThrow(PoiTypes.FARMER);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.VillageHalls.assign(level, first,
				new io.github.jcondedata.aliveworkplace.hall.VillageHalls.FreeStation(composter, VillagerProfession.FARMER, poi)),
				"the Hall didn't give the first worker the composter");
		} else {
			io.github.jcondedata.aliveworkplace.work.Stations.assign(level, first, composter, VillagerProfession.FARMER);
		}
		helper.assertTrue(StationsSpecGameTests.job(first) == VillagerProfession.FARMER, "the first worker isn't a farmer");
		helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 0,
			"giving the first worker a new job freed the second worker's lectern (" + (fromTheHall ? "Village Hall" : "item") + ")");
		helper.succeed();
	}
}
