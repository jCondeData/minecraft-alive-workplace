package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
 * QA (2026-10-31, second pass) of B715 from the B206/B207 spec: "the wagon's beds are only for the journey; once a village
 * is founded the settlers claim real beds in its homes". A bed nobody's camp placed stays a home: the hall's round doesn't
 * un-home its resident, and the census counts it in Beds, not as a wagon bed. Covers a green bed by a campfire and hay
 * (and the same after the camp-bed list's save and reload), beds of other colours by a campfire, hay and a Blueprint
 * Table. A player's bed beside a real camp's wagon: QaWagonBeds1031bBugTests.
 */
public class QaWagonBeds1031bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 20);
	private static final BlockPos CAMP = new BlockPos(15, 2, 4);

	static void radius(GameTestHelper helper, int r) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = r;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(-2, 0, -2)), helper.absolutePos(new BlockPos(31, 12, 31)));
		Leftovers.after(helper, () -> CampBeds.Data.get(helper.getLevel()).forget(box));
	}

	/** A bed (foot at {@code foot}, head south of it); its head, absolute. */
	static BlockPos bed(GameTestHelper helper, BlockPos foot, Block bed) {
		helper.setBlock(foot, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	static BlockPos home(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	static List<BlockPos> bedHeads(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 10, 29)))) {
			BlockState state = helper.getLevel().getBlockState(p);
			if (state.is(BlockTags.BEDS) && state.getValue(BedBlock.PART) == BedPart.HEAD) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/**
	 * A farmhouse's GREEN bed with a campfire 6 blocks east and a hay bale 6 blocks west, no camp ever placed: after the
	 * hall's round it is no wagon bed, its resident keeps it, the census counts it. The camp-bed list then goes through a
	 * save and reload (put back as the level's list) and another round: still the same.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaGreenBedByCampfireAndHay1031b"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaGreenBedByCampfireAndHay1031b")
	public void qaAGreenBedByACampfireAndHayStaysAHomeThroughAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		radius(helper, 18);
		ServerLevel level = helper.getLevel();
		CampBeds.Data live = CampBeds.Data.get(level);
		Leftovers.after(helper, () -> level.getDataStorage().set(CampBeds.Data.NAME, live));
		BlockPos foot = new BlockPos(6, 2, 10);
		BlockPos head = bed(helper, foot, Blocks.GREEN_BED);
		helper.setBlock(foot.east(6), Blocks.CAMPFIRE);
		helper.setBlock(foot.west(6), Blocks.HAY_BLOCK);
		Villager resident = helper.spawn(EntityType.VILLAGER, foot.north());
		resident.setNoAi(true);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CampBeds.claim(level, resident, head), "setup: the resident couldn't take the green bed");
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(!CampBeds.isWagonBed(level, head), "a farmhouse's green bed by a campfire and hay is a wagon bed");
				helper.assertTrue(head.equals(home(resident)), "the green bed's resident lost his home at the round: " + home(resident));
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
				helper.assertTrue(census.beds() == 1 && census.wagonBeds() == 0, "before reload: beds " + census.beds() + ", wagon " + census.wagonBeds());
				CampBeds.Data data = CampBeds.Data.get(level);
				CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
				CampBeds.Data reloaded = CampBeds.Data.load(saved, level.registryAccess());
				helper.assertTrue(!reloaded.has(head) && !reloaded.unverified(head), "the green bed is on the reloaded list: " + saved);
				level.getDataStorage().set(CampBeds.Data.NAME, reloaded);
				helper.runAfterDelay(60, () -> {
					helper.assertTrue(!CampBeds.isWagonBed(level, head), "after a reload the green bed is a wagon bed");
					helper.assertTrue(head.equals(home(resident)), "after a reload the resident lost his home: " + home(resident));
					VillageHalls.Census again = VillageHalls.census(level, helper.absolutePos(HALL));
					helper.assertTrue(again.beds() == 1 && again.wagonBeds() == 0, "after reload: beds " + again.beds() + ", wagon " + again.wagonBeds());
					helper.succeed();
				});
			});
		});
	}

	/**
	 * Beds of other colours (red, white, blue) with a campfire, a hay bale and even a Blueprint Table close by: none is a
	 * wagon bed (only a camp's brown or green bedrolls ever are); all three are counted homes and the resident keeps his.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaOtherColoursByCampLook1031b"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOtherColoursByCampLook1031b")
	public void qaBedsOfOtherColoursByACampfireHayAndTableStayHomes(GameTestHelper helper) {
		Leftovers.clear(helper);
		radius(helper, 18);
		ServerLevel level = helper.getLevel();
		BlockPos red = bed(helper, new BlockPos(6, 2, 10), Blocks.RED_BED);
		BlockPos white = bed(helper, new BlockPos(8, 2, 10), Blocks.WHITE_BED);
		BlockPos blue = bed(helper, new BlockPos(10, 2, 10), Blocks.BLUE_BED);
		helper.setBlock(new BlockPos(8, 2, 14), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(4, 2, 12), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(12, 2, 8), ModBlocks.BLUEPRINT_TABLE);
		Villager resident = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 8));
		resident.setNoAi(true);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CampBeds.claim(level, resident, red), "setup: the resident couldn't take the red bed");
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				for (BlockPos b : List.of(red, white, blue)) {
					helper.assertTrue(!CampBeds.isWagonBed(level, b), level.getBlockState(b).getBlock() + " by a campfire, hay and table is a wagon bed");
				}
				helper.assertTrue(red.equals(home(resident)), "the red bed's resident lost his home: " + home(resident));
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
				helper.assertTrue(census.beds() == 3 && census.wagonBeds() == 0, "beds " + census.beds() + ", wagon " + census.wagonBeds());
				helper.succeed();
			});
		});
	}

	/** Where a 1x2 bed (foot, head south) fits on the ground within 6 blocks of {@code near} (absolute), or null. */
	static BlockPos freeSpotBeside(ServerLevel level, BlockPos near) {
		for (int r = 2; r <= 6; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy = -1; dy <= 2; dy++) {
						BlockPos foot = near.offset(dx, dy, dz);
						BlockPos head = foot.south();
						if (level.getBlockState(foot).isAir() && level.getBlockState(head).isAir() && level.getBlockState(foot.above()).isAir()
							&& level.getBlockState(head.above()).isAir() && !level.getBlockState(foot.below()).isAir()
							&& !level.getBlockState(head.below()).isAir() && !level.getBlockState(head.south()).is(BlockTags.BEDS)) {
							return foot.immutable();
						}
					}
				}
			}
		}
		return null;
	}
}
