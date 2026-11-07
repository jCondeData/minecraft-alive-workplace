package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.build.Builders;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-10-31) of the B715 fix against B207's spec: "the wagon's beds are only for the journey; once a village is
 * founded the settlers claim real beds in its homes", so a bed nobody's camp placed stays a home. The fix asks the
 * older-save look for the camp's Blueprint Table within 12 blocks; a player's own village has its builder's table too.
 */
public class QaWagonBedsHome1031GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 20);

	private static void setup(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 18;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(-2, 0, -2)), helper.absolutePos(new BlockPos(31, 12, 31)));
		Leftovers.after(helper, () -> CampBeds.Data.get(helper.getLevel()).forget(box));
	}

	private static BlockPos bed(GameTestHelper helper, BlockPos foot, Block bed) {
		helper.setBlock(foot, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	private static BlockPos home(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	/**
	 * A player's rustic cabin (brown and green beds, a campfire and a hay bale by them) in a village whose own builder works
	 * at a Blueprint Table the player placed 8 blocks off. No camp was ever placed. Returns {brown head, green head}.
	 */
	private static List<BlockPos> cabinWithBuilder(GameTestHelper helper, List<Villager> residents) {
		BlockPos brown = bed(helper, new BlockPos(6, 2, 10), Blocks.BROWN_BED);
		BlockPos green = bed(helper, new BlockPos(8, 2, 10), Blocks.GREEN_BED);
		helper.setBlock(new BlockPos(11, 2, 12), Blocks.CAMPFIRE); // the cabin's cooking fire
		helper.setBlock(new BlockPos(3, 2, 12), Blocks.HAY_BLOCK); // a hay bale by the door
		helper.setBlock(new BlockPos(14, 2, 10), ModBlocks.BLUEPRINT_TABLE); // the village's own builder's table
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 8));
		builder.setNoAi(true);
		residents.add(helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 8)));
		residents.add(helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8)));
		residents.forEach(v -> v.setNoAi(true));
		helper.runAfterDelay(1, () -> Builders.employ(helper.getLevel(), builder, helper.absolutePos(new BlockPos(14, 2, 10))));
		return List.of(brown, green);
	}

	/** (1) The likeliest real village: the cabin's brown and green beds stay homes through the hall's round, counted in Beds. */
	//$ gametest_ticks_batch AREA '200' '"qaCabinByBuildersTable1031"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCabinByBuildersTable1031")
	public void qaACabinByTheVillagesBuildersTableStaysAHome(GameTestHelper helper) {
		setup(helper);
		ServerLevel level = helper.getLevel();
		List<Villager> residents = new ArrayList<>();
		List<BlockPos> beds = cabinWithBuilder(helper, residents);
		helper.runAfterDelay(3, () -> {
			for (int i = 0; i < 2; i++) {
				helper.assertTrue(CampBeds.claim(level, residents.get(i), beds.get(i)), "setup: resident " + i + " couldn't take their bed");
			}
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				CampBeds.Data data = CampBeds.Data.get(level);
				helper.assertTrue(!data.has(beds.get(0)) && !data.has(beds.get(1)),
					"a player's cabin beds (campfire, hay, the village's own Blueprint Table 8 blocks off, no camp ever placed) were listed as wagon beds: brown "
						+ data.has(beds.get(0)) + ", green " + data.has(beds.get(1)));
				for (int i = 0; i < 2; i++) {
					helper.assertTrue(beds.get(i).equals(home(residents.get(i))), "resident " + i + " lost his cabin home at the hall's round: home " + home(residents.get(i)));
				}
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
				helper.assertTrue(census.beds() == 2 && census.wagonBeds() == 0,
					"the hall counts beds " + census.beds() + ", wagon beds " + census.wagonBeds() + " (expected the cabin's 2 beds as homes)");
				helper.succeed();
			});
		});
	}

	/** (3) After (1) the list is saved and loaded: the cabin's beds are still homes, not listed (nor unverified). */
	//$ gametest_ticks_batch AREA '200' '"qaCabinByBuildersTableReload1031"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCabinByBuildersTableReload1031")
	public void qaACabinByTheBuildersTableIsStillAHomeAfterAReload(GameTestHelper helper) {
		setup(helper);
		ServerLevel level = helper.getLevel();
		List<Villager> residents = new ArrayList<>();
		List<BlockPos> beds = cabinWithBuilder(helper, residents);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CampBeds.claim(level, residents.get(0), beds.get(0)), "setup: couldn't take the bed");
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				CampBeds.Data data = CampBeds.Data.get(level);
				CampBeds.Data reloaded = CampBeds.Data.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
				for (BlockPos b : beds) {
					helper.assertTrue(!reloaded.has(b) && !reloaded.unverified(b), "after a save and reload the cabin's bed " + b + " is listed: "
						+ reloaded.has(b) + "/" + reloaded.unverified(b));
				}
				helper.assertTrue(beds.get(0).equals(home(residents.get(0))), "the resident lost his home: " + home(residents.get(0)));
				helper.succeed();
			});
		});
	}

	/**
	 * (2) A real settlers' camp placed by a wagon at one side of the village; the player's cabin (brown bed, its own campfire
	 * and hay, no table near it) more than 12 blocks from the camp's table. The camp's bedrolls are wagon beds; the cabin's
	 * bed stays its resident's home and is counted.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCampFarFromCabin1031"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCampFarFromCabin1031")
	public void qaAPlacedCampsBedrollsAreWagonBedsAndAFarCabinIsAHome(GameTestHelper helper) {
		setup(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BoundingBox[] campBox = new BoundingBox[1];
		List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(new BlockPos(6, 2, 2)), b -> campBox[0] = b);
		helper.assertTrue(settlers.size() == 2 && campBox[0] != null, "settlers: " + settlers.size());
		settlers.forEach(v -> v.setNoAi(true));
		List<BlockPos> rolls = new ArrayList<>();
		List<BlockPos> tables = new ArrayList<>();
		BoundingBox cb = campBox[0];
		for (BlockPos p : BlockPos.betweenClosed(cb.minX(), cb.minY(), cb.minZ(), cb.maxX(), cb.maxY(), cb.maxZ())) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.BEDS) && s.getValue(BedBlock.PART) == BedPart.HEAD) {
				rolls.add(p.immutable());
			}
			if (s.is(ModBlocks.BLUEPRINT_TABLE) || s.is(ModBlocks.BUILDERS_BENCH)) {
				tables.add(p.immutable());
			}
		}
		BlockPos cabin = bed(helper, new BlockPos(26, 2, 25), Blocks.BROWN_BED);
		helper.setBlock(new BlockPos(22, 2, 27), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(28, 2, 22), Blocks.HAY_BLOCK);
		for (BlockPos t : tables) {
			helper.assertTrue(Math.max(Math.abs(t.getX() - cabin.getX()), Math.abs(t.getZ() - cabin.getZ())) > 12, "setup: the camp's table is within 12 of the cabin: " + t);
		}
		Villager resident = helper.spawn(EntityType.VILLAGER, new BlockPos(24, 2, 24));
		resident.setNoAi(true);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(rolls.size() == 2 && rolls.stream().allMatch(b -> CampBeds.isWagonBed(level, b)), "the camp's bedrolls aren't wagon beds: " + rolls);
			helper.assertTrue(CampBeds.claim(level, resident, cabin), "setup: the resident couldn't take the cabin's bed");
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(!CampBeds.isWagonBed(level, cabin) && !CampBeds.Data.get(level).has(cabin), "the far cabin's bed is taken for a wagon bed");
				helper.assertTrue(cabin.equals(home(resident)), "the cabin's resident lost his home: " + home(resident));
				helper.assertTrue(rolls.stream().allMatch(b -> CampBeds.isWagonBed(level, b) && level.getPoiManager().getFreeTickets(b) == 0),
					"a camp bedroll is no longer a held wagon bed");
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
				helper.assertTrue(census.beds() == 1 && census.wagonBeds() == 2, "beds " + census.beds() + ", wagon " + census.wagonBeds());
				helper.succeed();
			});
		});
	}

	/**
	 * (4) Boundary of the older-list look: two brown beds a list from before B715 holds (unverified), both by a campfire
	 * and a hay bale, a Blueprint Table exactly 12 blocks from one and 13 from the other. Within 12 the bed is kept (a
	 * wagon bed); at 13 it is let go (a home).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaOldListTableBoundary1031"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaOldListTableBoundary1031")
	public void qaAnOlderListsBedKeepsTheTableAt12NotAt13(GameTestHelper helper) {
		setup(helper);
		ServerLevel level = helper.getLevel();
		CampBeds.Data live = CampBeds.Data.get(level);
		Leftovers.after(helper, () -> level.getDataStorage().set(CampBeds.Data.NAME, live));
		BlockPos near = bed(helper, new BlockPos(15, 2, 10), Blocks.BROWN_BED); // head (15, 2, 11)
		BlockPos far = bed(helper, new BlockPos(14, 2, 10), Blocks.BROWN_BED); // head (14, 2, 11)
		helper.setBlock(new BlockPos(14, 2, 15), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(16, 2, 15), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(27, 2, 11), ModBlocks.BLUEPRINT_TABLE); // 12 from near, 13 from far
		CompoundTag before = new CompoundTag();
		before.putLongArray("beds", new long[] {near.asLong(), far.asLong()});
		CampBeds.Data old = CampBeds.Data.load(before, level.registryAccess());
		level.getDataStorage().set(CampBeds.Data.NAME, old);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(old.unverified(near) && old.unverified(far), "setup: the older list's beds aren't unverified");
			boolean nearWagon = CampBeds.isWagonBed(level, near);
			boolean farWagon = CampBeds.isWagonBed(level, far);
			CampBeds.Data data = CampBeds.Data.get(level);
			helper.assertTrue(nearWagon && data.has(near) && !data.unverified(near), "table at 12: kept " + nearWagon + ", listed " + data.has(near));
			helper.assertTrue(!farWagon && !data.has(far) && !data.unverified(far), "table at 13: wagon " + farWagon + ", listed " + data.has(far) + "/" + data.unverified(far));
			helper.succeed();
		});
	}
}
