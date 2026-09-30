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
}
