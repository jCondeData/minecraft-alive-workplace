package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-c-1101-0654, second pass) of B721, B730/B732 and 36.20, written from each item's Expected / Done when:
 * <ul>
 * <li>B730/B732: "a bed nobody's camp placed stays a home; the look should only judge beds from lists saved before the
 * fix" — a world started with the list, a real camp's bedrolls counted once each, a bed near only some of an old camp's
 * markers, beds of other colors, and save and reload.</li>
 * <li>B721: "what the Carpenter makes reaches the builder who asked, or he doesn't craft for a builder he can't deliver
 * to" — the side the fix's test leaves out: a chestless builder in a village with no Storehouse.</li>
 * <li>36.20: "the refusal sentences name the new rule (outside Mapleridge and 140 blocks from the Blueprint Table — keep
 * it within 128)" for every job that takes a far spot.</li>
 * </ul>
 */
public class QaLaneC1101bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 20);

	// ------------------------------------------------------------------------------------------ B730 / B732: beds

	private static void bedSetup(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 18;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		// B852: an older save's list looks at unlisted beds only until it is first saved (B749, B754), and the server's
		// autosave (every 6000 ticks) lands inside a test now and then, ending that first session halfway through it (a
		// player's server does the same, on purpose). These tests are each about one session, so the level isn't saved
		// while they run, as with /save-off.
		boolean noSave = level.noSave;
		level.noSave = true;
		Leftovers.after(helper, () -> level.noSave = noSave);
		CampBeds.Data live = CampBeds.Data.get(level);
		Leftovers.after(helper, () -> level.getDataStorage().set(CampBeds.Data.NAME, live));
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(-2, 0, -2)), helper.absolutePos(new BlockPos(31, 12, 31)));
		Leftovers.after(helper, () -> CampBeds.Data.get(level).forget(box));
	}

	/** A world started with the wagon-bed list (this version's list, marked complete), as a fresh world saves it. */
	private static CampBeds.Data newWorld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CompoundTag tag = new CompoundTag();
		tag.putInt("version", 2);
		tag.putBoolean("complete", true);
		CampBeds.Data data = CampBeds.Data.load(tag, level.registryAccess());
		helper.assertTrue(data.complete() && !data.looks(), "setup: a new world's list isn't complete");
		level.getDataStorage().set(CampBeds.Data.NAME, data);
		return data;
	}

	/** A save from before the list (no list at all, or one without a version): its unlisted beds are judged by their look. */
	private static CampBeds.Data olderSave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CampBeds.Data data = CampBeds.Data.load(new CompoundTag(), level.registryAccess());
		helper.assertTrue(!data.complete() && data.looks(), "setup: an older save's list doesn't look at unlisted beds");
		level.getDataStorage().set(CampBeds.Data.NAME, data);
		return data;
	}

	private static CampBeds.Data reload(ServerLevel level) {
		return CampBeds.Data.load(CampBeds.Data.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
	}

	private static BlockPos bed(GameTestHelper helper, BlockPos foot, Block bed) {
		helper.setBlock(foot, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	private static BlockPos home(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	private static Villager resident(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		return v;
	}

	/**
	 * B730/B732 in a world started fresh: a brown cabin bed with every mark an old camp had right beside it (campfire 2
	 * off, hay bale 2 off, the village builder's Blueprint Table 3 off), no camp ever placed. It is a home at the hall's
	 * round and in the census, and still after a save and reload (the list stays complete, the bed off it).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaC1101bNewWorldCabin"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaC1101bNewWorldCabin")
	public void qaANewWorldsCabinBedBesideEveryCampMarkIsAHome(GameTestHelper helper) {
		bedSetup(helper);
		ServerLevel level = helper.getLevel();
		newWorld(helper);
		BlockPos cabin = bed(helper, new BlockPos(12, 2, 10), Blocks.BROWN_BED); // head (12, 2, 11)
		helper.setBlock(new BlockPos(14, 2, 11), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(10, 2, 11), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(12, 2, 14), ModBlocks.BLUEPRINT_TABLE);
		Villager resident = resident(helper, new BlockPos(12, 2, 8));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(!CampBeds.isWagonBed(level, cabin), "a new world's cabin bed beside a campfire, hay bale and Blueprint Table is a wagon bed");
			helper.assertTrue(CampBeds.claim(level, resident, cabin), "the resident couldn't take the cabin's bed");
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(!CampBeds.isWagonBed(level, cabin) && !CampBeds.Data.get(level).has(cabin), "after the hall's round the cabin bed is a wagon bed");
				helper.assertTrue(cabin.equals(home(resident)), "the resident lost his cabin home: " + home(resident));
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
				helper.assertTrue(census.beds() == 1 && census.wagonBeds() == 0, "census: beds " + census.beds() + ", wagon beds " + census.wagonBeds());
				CampBeds.Data reloaded = reload(level);
				helper.assertTrue(reloaded.complete() && !reloaded.looks(), "after a save the new world's list is complete " + reloaded.complete()
					+ ", looks " + reloaded.looks());
				helper.assertTrue(!reloaded.has(cabin) && !reloaded.unverified(cabin), "after a save the cabin's bed is listed");
				level.getDataStorage().set(CampBeds.Data.NAME, reloaded);
				helper.runAfterDelay(2, () -> {
					helper.assertTrue(!CampBeds.isWagonBed(level, cabin), "after a reload the cabin's bed is a wagon bed");
					helper.succeed();
				});
			});
		});
	}

	/**
	 * B730/B732 from the other side, in a world started fresh: a real settlers' camp placed by a wagon lists its bedrolls,
	 * each counted once as a wagon bed in the census (not as a home, not twice), and a green cabin bed put a few blocks
	 * from the camp's own table and campfire is a home; both hold after a save and reload.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaC1101bNewWorldCamp"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaC1101bNewWorldCamp")
	public void qaANewWorldsRealCampCountsEachBedrollOnceAndACabinBesideItIsAHome(GameTestHelper helper) {
		bedSetup(helper);
		ServerLevel level = helper.getLevel();
		newWorld(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BoundingBox[] campBox = new BoundingBox[1];
		List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(new BlockPos(6, 2, 2)), b -> campBox[0] = b);
		helper.assertTrue(campBox[0] != null, "setup: no camp was placed");
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
		helper.assertTrue(!rolls.isEmpty() && !tables.isEmpty(), "setup: the camp has bedrolls " + rolls + " and tables " + tables);
		// the cabin: 2 blocks past the camp's south edge, in line with its table
		BlockPos table = tables.get(0);
		BlockPos footAbs = new BlockPos(table.getX(), helper.absolutePos(new BlockPos(0, 2, 0)).getY(), cb.maxZ() + 2);
		BlockState green = Blocks.GREEN_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		level.setBlockAndUpdate(footAbs, green.setValue(BedBlock.PART, BedPart.FOOT));
		level.setBlockAndUpdate(footAbs.south(), green.setValue(BedBlock.PART, BedPart.HEAD));
		BlockPos cabin = footAbs.south();
		helper.assertTrue(Math.max(Math.abs(table.getX() - cabin.getX()), Math.abs(table.getZ() - cabin.getZ())) <= 12,
			"setup: the cabin is more than 12 from the camp's table: " + cabin + " / " + table);
		Villager resident = EntityType.VILLAGER.create(level);
		resident.moveTo(footAbs.getX() + 2.5, footAbs.getY(), footAbs.getZ() + 0.5);
		resident.setNoAi(true);
		level.addFreshEntity(resident);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(rolls.stream().allMatch(b -> CampBeds.isWagonBed(level, b) && CampBeds.Data.get(level).has(b)),
				"the new world's camp bedrolls aren't listed wagon beds: " + rolls);
			helper.assertTrue(!CampBeds.isWagonBed(level, cabin), "a cabin bed by the camp (no camp placed it) is a wagon bed");
			helper.assertTrue(CampBeds.claim(level, resident, cabin), "the resident couldn't take the cabin's bed");
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
				helper.assertTrue(census.wagonBeds() == rolls.size() && census.beds() == 1,
					"census: wagon beds " + census.wagonBeds() + " (the camp has " + rolls.size() + " bedrolls), beds " + census.beds() + " (expected the cabin's 1)");
				helper.assertTrue(cabin.equals(home(resident)), "the cabin's resident lost his home: " + home(resident));
				CampBeds.Data reloaded = reload(level);
				helper.assertTrue(rolls.stream().allMatch(reloaded::has) && !reloaded.has(cabin) && reloaded.complete(),
					"after a save: bedrolls listed " + rolls.stream().allMatch(reloaded::has) + ", cabin listed " + reloaded.has(cabin) + ", complete " + reloaded.complete());
				helper.succeed();
			});
		});
	}

	/**
	 * An older save (no list yet): a camp built before the list, its brown bedroll by a campfire, a hay bale and the camp's
	 * Blueprint Table, is a wagon bed counted once in the census (asked about twice per round), listed from then on and
	 * still after a save and reload.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaC1101bOldCampOnce"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaC1101bOldCampOnce")
	public void qaAnOlderSavesCampBedrollIsCountedOnceAndKeptAfterAReload(GameTestHelper helper) {
		bedSetup(helper);
		ServerLevel level = helper.getLevel();
		olderSave(helper);
		BlockPos roll = bed(helper, new BlockPos(12, 2, 10), Blocks.BROWN_BED);
		helper.setBlock(new BlockPos(15, 2, 11), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(9, 2, 12), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(12, 2, 15), ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(60, () -> {
			VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
			helper.assertTrue(census.wagonBeds() == 1 && census.beds() == 0, "census: wagon beds " + census.wagonBeds() + ", beds " + census.beds()
				+ " (expected the old camp's one bedroll)");
			CampBeds.Data reloaded = reload(level);
			helper.assertTrue(reloaded.has(roll) && !reloaded.looks(), "after a save: bedroll listed " + reloaded.has(roll) + ", still looking " + reloaded.looks());
			level.getDataStorage().set(CampBeds.Data.NAME, reloaded);
			helper.setBlock(new BlockPos(15, 2, 11), Blocks.AIR); // the camp's fire goes out
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(CampBeds.isWagonBed(level, roll), "after a reload, with its campfire gone, the old camp's bedroll is no longer a wagon bed");
				helper.succeed();
			});
		});
	}

	/**
	 * An older save's look needs all three of an old camp's marks: a brown bed with only two of them nearby is a home —
	 * campfire and hay but no table; campfire and table but no hay; hay and table but no campfire; and all three but the
	 * campfire 11 blocks off. The same bed with all three within reach is then a wagon bed (the test can tell).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaC1101bSomeMarks"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaC1101bSomeMarks")
	public void qaAnOlderSavesBedNearOnlySomeCampMarksIsAHome(GameTestHelper helper) {
		bedSetup(helper);
		ServerLevel level = helper.getLevel();
		olderSave(helper);
		BlockPos b = bed(helper, new BlockPos(12, 2, 10), Blocks.BROWN_BED); // head (12, 2, 11)
		BlockPos fire = new BlockPos(16, 2, 11);
		BlockPos farFire = new BlockPos(23, 2, 11); // 11 across from the head
		BlockPos hay = new BlockPos(8, 2, 12);
		BlockPos table = new BlockPos(12, 2, 16);
		String[] steps = {"campfire and hay, no table", "campfire and table, no hay", "hay and table, no campfire", "hay, table and a campfire 11 off"};
		BlockPos[][] marks = {{fire, hay}, {fire, table}, {hay, table}, {farFire, hay, table}};
		Block[][] kinds = {{Blocks.CAMPFIRE, Blocks.HAY_BLOCK}, {Blocks.CAMPFIRE, ModBlocks.BLUEPRINT_TABLE}, {Blocks.HAY_BLOCK, ModBlocks.BLUEPRINT_TABLE},
			{Blocks.CAMPFIRE, Blocks.HAY_BLOCK, ModBlocks.BLUEPRINT_TABLE}};
		step(helper, level, b, steps, marks, kinds, 0, fire, hay, table);
	}

	private static void step(GameTestHelper helper, ServerLevel level, BlockPos bed, String[] steps, BlockPos[][] marks, Block[][] kinds, int i,
							 BlockPos fire, BlockPos hay, BlockPos table) {
		for (BlockPos[] set : marks) {
			for (BlockPos p : set) {
				helper.setBlock(p, Blocks.AIR);
			}
		}
		if (i == steps.length) {
			helper.setBlock(fire, Blocks.CAMPFIRE);
			helper.setBlock(hay, Blocks.HAY_BLOCK);
			helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(CampBeds.isWagonBed(level, bed), "control: with campfire, hay and table all near, the older save's bed isn't a wagon bed");
				helper.succeed();
			});
			return;
		}
		for (int k = 0; k < marks[i].length; k++) {
			helper.setBlock(marks[i][k], kinds[i][k]);
		}
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!CampBeds.isWagonBed(level, bed) && !CampBeds.Data.get(level).has(bed),
				"an older save's brown bed with only " + steps[i] + " is taken for a wagon bed");
			step(helper, level, bed, steps, marks, kinds, i + 1, fire, hay, table);
		});
	}

	/**
	 * Camps' bedrolls are brown or green: a red and a white bed beside a campfire, a hay bale and a Blueprint Table in an
	 * older save are homes; an older list's unverified spot holding a blue bed is a home; and a camp's listed bedroll
	 * replaced by a yellow bed is a home again (the brown one put back is a wagon bed again). The census counts them as
	 * beds.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaC1101bOtherColors"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaC1101bOtherColors")
	public void qaABedOfAnotherColorIsNeverAWagonBed(GameTestHelper helper) {
		bedSetup(helper);
		ServerLevel level = helper.getLevel();
		BlockPos listed = helper.absolutePos(new BlockPos(20, 2, 11));
		BlockPos unverified = helper.absolutePos(new BlockPos(17, 2, 11));
		CompoundTag before = new CompoundTag();
		before.putLongArray("beds", new long[] {unverified.asLong()}); // a list from before B715
		CampBeds.Data old = CampBeds.Data.load(before, level.registryAccess());
		helper.assertTrue(old.looks() && old.unverified(unverified), "setup: the older list");
		level.getDataStorage().set(CampBeds.Data.NAME, old);
		BlockPos red = bed(helper, new BlockPos(11, 2, 10), Blocks.RED_BED);
		BlockPos white = bed(helper, new BlockPos(13, 2, 10), Blocks.WHITE_BED);
		BlockPos blue = bed(helper, new BlockPos(17, 2, 10), Blocks.BLUE_BED);
		helper.assertTrue(blue.equals(unverified), "setup: blue head " + blue);
		helper.setBlock(new BlockPos(15, 2, 13), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(10, 2, 13), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(14, 2, 15), ModBlocks.BLUEPRINT_TABLE);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(!CampBeds.isWagonBed(level, red) && !CampBeds.isWagonBed(level, white),
				"a red or white bed by a campfire, hay and table is a wagon bed: red " + CampBeds.isWagonBed(level, red) + ", white " + CampBeds.isWagonBed(level, white));
			helper.assertTrue(!CampBeds.isWagonBed(level, blue), "a blue bed on an older list's spot is a wagon bed");
			// a listed camp bedroll swapped for a yellow bed
			BlockPos foot = new BlockPos(20, 2, 10); // the listed head is (20, 2, 11)
			bed(helper, foot, Blocks.BROWN_BED);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(CampBeds.isWagonBed(level, listed) && CampBeds.Data.get(level).has(listed), "control: the brown bedroll by the camp's marks isn't a wagon bed");
				bed(helper, foot, Blocks.YELLOW_BED);
				helper.runAfterDelay(2, () -> {
					helper.assertTrue(!CampBeds.isWagonBed(level, listed), "a yellow bed put where a camp's bedroll stood is a wagon bed");
					// B786: the census reaches 18 blocks across (and 32 up and down) from the hall, past this area's edges, where an
					// earlier batch's tests left their beds (never cleared, as only this area is); those beds aren't this test's
					List<BlockPos> own = List.of(red, white, blue, listed);
					List<BlockPos> stray = VillageHalls.beds(level, helper.absolutePos(HALL)).filter(p -> !own.contains(p)).map(BlockPos::immutable).toList();
					stray.forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
					helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);					helper.runAfterDelay(60, () -> {
						VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
						helper.assertTrue(census.wagonBeds() == 0 && census.beds() == 4, "census: wagon beds " + census.wagonBeds() + ", beds " + census.beds()
							+ " (expected the red, white, blue and yellow beds; counted " + VillageHalls.beds(level, helper.absolutePos(HALL)).map(BlockPos::toShortString).toList()
							+ ", wagon beds " + CampBeds.inVillage(level, helper.absolutePos(HALL)) + ", strays removed before the hall " + stray + ")");
						bed(helper, foot, Blocks.BROWN_BED);
						helper.runAfterDelay(2, () -> {
							CampBeds.Data list = CampBeds.Data.get(level);
							helper.assertTrue(CampBeds.isWagonBed(level, listed), "the brown bedroll put back on its listed spot isn't a wagon bed (list: listed "
								+ list.has(listed) + ", unverified " + list.unverified(listed) + ", looks " + list.looks() + ", complete " + list.complete()
								+ ", the level's list is the test's own " + (list == old) + ")");
							helper.succeed();
						});
					});
				});
			});
		});
	}

	// ------------------------------------------------------------------------------------------ B721: Carpenter

	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);
	private static final BlockPos CARPENTER = new BlockPos(13, 2, 2);
	private static final BlockPos CARPENTER_CHEST = new BlockPos(14, 2, 2);

	/**
	 * B721, the side the fix's test leaves out: a builder with no chest by his Blueprint Table, in a village with no
	 * Storehouse, waits for 3 spruce planks; the only spruce is 4 logs in the chest by the Carpenter's bench. Expected:
	 * either what the Carpenter makes reaches the builder (his row gets built) or he doesn't craft for him (the logs stay)
	 * — never planks left lying by a bench while the builder waits.
	 */
	//$ gametest_ticks_batch BIG '3000' '"qaC1101bChestlessNoStore"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaC1101bChestlessNoStore")
	public void qaACarpenterNeverLeavesPlanksLyingForAChestlessBuilderWithNoStorehouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		ResourceLocation row = blueprint(helper, "planks", ROW, new Vec3i(3, 1, 1), Blocks.SPRUCE_PLANKS.defaultBlockState());
		helper.setBlock(BENCH, ModBlocks.BLUEPRINT_TABLE);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(BENCH));
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, row, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site.plan(level) != null, "setup: no blueprint " + row);
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		helper.setBlock(CARPENTER, ModBlocks.CARPENTERS_BENCH);
		helper.setBlock(CARPENTER_CHEST, Blocks.CHEST);
		((Container) helper.getBlockEntity(CARPENTER_CHEST)).setItem(0, new ItemStack(Items.SPRUCE_LOG, 4));
		Villager carpenter = helper.spawn(EntityType.VILLAGER, CARPENTER.south());
		Jobs.employ(level, carpenter, helper.absolutePos(CARPENTER), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		long start = level.getGameTime();
		helper.onEachTick(() -> {
			long t = level.getGameTime() - start;
			int lying = onGround(helper, Items.SPRUCE_PLANKS);
			boolean open = BuildSiteManager.get(level).get(site.id()) != null;
			if (lying > 0 && open && t > 600) {
				helper.fail("at tick " + t + " " + lying + " spruce planks lie on the ground while the chestless builder (no Storehouse) still waits for "
					+ site.missing() + "; Carpenter's chest: " + count(helper, Items.SPRUCE_LOG) + " logs, " + count(helper, Items.SPRUCE_PLANKS) + " planks");
			}
			int wood = 4 * (count(helper, Items.SPRUCE_LOG) + bags(helper, Items.SPRUCE_LOG) + onGround(helper, Items.SPRUCE_LOG)) + count(helper, Items.SPRUCE_PLANKS)
				+ bags(helper, Items.SPRUCE_PLANKS) + lying + QaCarpenterReserve1031GameTests.placed(helper, ROW, Blocks.SPRUCE_PLANKS)
				+ planksInVillagers(helper);
			if (wood < 3 && open) {
				helper.fail("at tick " + t + " the spruce is gone (" + wood + " planks' worth left) and the builder still waits for " + site.missing());
			}
			if (!open) {
				helper.assertTrue(QaCarpenterReserve1031GameTests.placed(helper, ROW, Blocks.SPRUCE_PLANKS) == 3, "the build closed but its row isn't built");
				helper.succeed();
			} else if (t >= 2600) {
				helper.assertTrue(count(helper, Items.SPRUCE_LOG) == 4 && lying == 0,
					"by tick " + t + " the builder never got his planks and the Carpenter's logs are " + count(helper, Items.SPRUCE_LOG) + " of 4 (planks: chest "
						+ count(helper, Items.SPRUCE_PLANKS) + ", ground " + lying + ")");
				helper.succeed();
			}
		});
	}

	private static ResourceLocation blueprint(GameTestHelper helper, String name, BlockPos origin, Vec3i size, BlockState block) {
		ServerLevel level = helper.getLevel();
		for (BlockPos at : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			helper.setBlock(at, block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qac1101b_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), size, false, Blocks.STRUCTURE_VOID);
		for (BlockPos at : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			helper.setBlock(at, Blocks.AIR);
		}
		return id;
	}

	private static int count(GameTestHelper helper, Item item) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(CARPENTER_CHEST)) instanceof Container c ? c.countItem(item) : 0;
	}

	private static int bags(GameTestHelper helper, Item item) {
		int n = 0;
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8))) {
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
		}
		return n;
	}

	/** Planks (or logs, as 4 planks) a villager carries in its own inventory (the Carpenter on his way). */
	private static int planksInVillagers(GameTestHelper helper) {
		int n = 0;
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8))) {
			n += v.getInventory().countItem(Items.SPRUCE_PLANKS) + 4 * v.getInventory().countItem(Items.SPRUCE_LOG);
		}
		return n;
	}

	private static int onGround(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	// ------------------------------------------------------------------------------------------ 36.20: the sentences

	/**
	 * 36.20: "the refusal sentences name the new rule (outside Mapleridge and 140 blocks from the Blueprint Table — keep it
	 * within 128)". Every job that takes a far spot (builder, miner, farmer, lumberjack, orchard keeper, berry breeder) has
	 * a sentence that, filled in, names the village, the distance, the work block and the reach, and its in-village twin.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void qaEveryFarSpotRefusalNamesTheVillageAndTheReach(GameTestHelper helper) {
		String[] jobs = {"quarry", "field", "tree_farm", "orchard_area", "berry_plot"};
		for (String job : jobs) {
			String key = "message.aliveworkplace." + job + ".too_far_village";
			String text = Component.translatable(key, "Mapleridge", 140, "Blueprint Table", 128).getString();
			helper.assertTrue(!text.equals(key), "no sentence for " + key);
			helper.assertTrue(text.contains("outside Mapleridge and 140 blocks from the Blueprint Table") && text.endsWith("keep it within 128."),
				job + "'s refusal doesn't name the village, the distance, the work block and the reach: " + text);
			String plain = "message.aliveworkplace." + job + ".too_far";
			String near = Component.translatable(plain, 140, "Blueprint Table", 128).getString();
			helper.assertTrue(!near.equals(plain) && near.contains("140 blocks from the Blueprint Table") && near.endsWith("keep it within 128."),
				job + "'s no-village refusal: " + near);
		}
		String builder = Component.translatable("message.aliveworkplace.assign.too_far_village", "Mapleridge", 140, 128).getString();
		helper.assertTrue(builder.equals("That spot is outside Mapleridge and 140 blocks from the builder's Blueprint Table — keep it within 128."),
			"the builder's refusal: " + builder);
		helper.succeed();
	}
}
