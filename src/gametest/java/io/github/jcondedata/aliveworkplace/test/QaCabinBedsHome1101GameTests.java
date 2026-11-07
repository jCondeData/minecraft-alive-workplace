package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-11-01, lane-b-1101-0638) of B730, B732 and B749, written from the specs: a bed nobody's camp placed stays a
 * home (B207/B715); a bed placed after the village was founded is a home (B732); the old camp look (a brown or green
 * bedroll, a campfire and a hay bale within 10 blocks, a Blueprint Table within 12) judges only beds of a save from
 * before the list (B730), and on a server whose list was saved by an older version a new bed is a home too (B749). A
 * real settlers' camp's bedrolls stay wagon beds throughout.
 */
public class QaCabinBedsHome1101GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 20);

	/** The dimension's list swapped for {@code list} for this test (campArea puts the live one back afterwards). */
	private static CampBeds.Data use(GameTestHelper helper, CompoundTag saved) {
		ServerLevel level = helper.getLevel();
		CampBeds.Data list = CampBeds.Data.load(saved, level.registryAccess());
		level.getDataStorage().set(CampBeds.Data.NAME, list);
		return list;
	}

	/** A list as a version from before B715 saved it: no {@code version}, no {@code complete} (its first session looks). */
	private static CompoundTag preB715(long... beds) {
		CompoundTag tag = new CompoundTag();
		if (beds.length > 0) {
			tag.putLongArray("beds", beds);
		}
		return tag;
	}

	/** A list as the owner's server saved it before B730: this format ({@code version} 2), no {@code complete} mark. */
	private static CompoundTag preB730() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("version", 2);
		tag.putLongArray("beds", new long[0]);
		return tag;
	}

	/** The live list saved and loaded again, put in its place (a server restart). */
	private static CampBeds.Data reload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CompoundTag saved = CampBeds.Data.get(level).save(new CompoundTag(), level.registryAccess());
		return use(helper, saved);
	}

	private static List<BlockPos> heads(GameTestHelper helper, BoundingBox box, boolean bedrollsOnly) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = helper.getLevel().getBlockState(p);
			if (s.is(BlockTags.BEDS) && s.getValue(BedBlock.PART) == BedPart.HEAD && (!bedrollsOnly || s.is(Blocks.BROWN_BED) || s.is(Blocks.GREEN_BED))) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	private static boolean near(BlockPos head, List<BlockPos> any, int r) {
		return any.stream().anyMatch(p -> Math.abs(p.getX() - head.getX()) <= r && Math.abs(p.getZ() - head.getZ()) <= r && p.getY() - head.getY() >= -2 && p.getY() - head.getY() <= 3);
	}

	private static BoundingBox area(GameTestHelper helper) {
		return BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 10, 29)));
	}

	/** Takes away every campfire, hay bale, Blueprint Table and Builder's Bench in the area (the camp is gone). */
	private static int clearCampLook(GameTestHelper helper) {
		int n = 0;
		BoundingBox b = area(helper);
		for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
			BlockState s = helper.getLevel().getBlockState(p);
			if (s.is(BlockTags.CAMPFIRES) || s.is(Blocks.HAY_BLOCK) || s.is(ModBlocks.BLUEPRINT_TABLE) || s.is(ModBlocks.BUILDERS_BENCH)) {
				helper.getLevel().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
				n++;
			}
		}
		return n;
	}

	/** Every hall round every {@code every} ticks for this test (the round is what lists and holds wagon beds). */
	private static void roundsEvery(GameTestHelper helper, int every) {
		int was = VillageNeeds.CHECK_EVERY;
		VillageNeeds.CHECK_EVERY = every;
		Leftovers.after(helper, () -> VillageNeeds.CHECK_EVERY = was);
	}

	private static Villager resident(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		return v;
	}

	private static String census(GameTestHelper helper) {
		VillageHalls.Census c = VillageHalls.census(helper.getLevel(), helper.absolutePos(HALL));
		return "beds " + c.beds() + ", wagon beds " + c.wagonBeds();
	}

	// --- the old camp look's edges, in an older save's first session (B730, B749) ---------------------------------------

	/**
	 * Two brown beds side by side in an older save's first session (a list from before B715), neither on its list: one has
	 * {@code edge} (a campfire or a hay bale) exactly 10 blocks off, the other 11; the other camp pieces are close. Only the
	 * one at 10 has the old camp's look (spec: campfire and hay within 10 blocks).
	 *
	 * <p>B978: asked in the tick the camp is set up. Three ticks later, as it was, the server's autosave (every 6,000
	 * ticks) could fall between: saving the list ends the old look (B754, rightly), and the bed at 10 was then a home.
	 */
	private static void lookEdge(GameTestHelper helper, Block edge, Block close) {
		QaVillageGuards1031GameTests.campArea(helper);
		ServerLevel level = helper.getLevel();
		CampBeds.Data list = use(helper, preB715());
		helper.assertTrue(list.looks() && !list.complete(), "set-up: a list from before B715 doesn't look at unlisted beds");
		BlockPos in = QaVillageGuards1031GameTests.bed(helper, new BlockPos(15, 2, 10), Blocks.BROWN_BED); // head (15, 2, 11)
		BlockPos out = QaVillageGuards1031GameTests.bed(helper, new BlockPos(14, 2, 10), Blocks.BROWN_BED); // head (14, 2, 11)
		helper.setBlock(new BlockPos(25, 2, 11), edge); // 10 from in, 11 from out
		helper.setBlock(new BlockPos(15, 2, 13), close);
		helper.setBlock(new BlockPos(15, 2, 15), ModBlocks.BLUEPRINT_TABLE);
		boolean inWagon = CampBeds.isWagonBed(level, in);
		boolean outWagon = CampBeds.isWagonBed(level, out);
		CampBeds.Data now = CampBeds.Data.get(level);
		String state = String.format(" (in: wagon %b, listed %b; out: wagon %b, listed %b; the list %s, looks %b; %s at (25, 2, 11), %s at (15, 2, 13), %s at (15, 2, 15))",
			inWagon, now.has(in), outWagon, now.has(out), now == list ? "the test's" : "replaced", now.looks(),
			helper.getBlockState(new BlockPos(25, 2, 11)).getBlock().getName().getString(),
			helper.getBlockState(new BlockPos(15, 2, 13)).getBlock().getName().getString(),
			helper.getBlockState(new BlockPos(15, 2, 15)).getBlock().getName().getString());
		helper.assertTrue(inWagon && now.has(in), edge.getName().getString() + " at 10 blocks: the old camp's bedroll isn't known by its look" + state);
		helper.assertTrue(!outWagon && !now.has(out), edge.getName().getString() + " at 11 blocks: the bed is taken for a wagon bed" + state);
		helper.succeed();
	}

	//$ gametest_ticks_batch HUGE '100' '"qaCb1101CampfireEdge"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaCb1101CampfireEdge")
	public void qaAnOlderSessionsLookTakesACampfireAt10NotAt11(GameTestHelper helper) {
		lookEdge(helper, Blocks.CAMPFIRE, Blocks.HAY_BLOCK);
	}

	//$ gametest_ticks_batch HUGE '100' '"qaCb1101HayEdge"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaCb1101HayEdge")
	public void qaAnOlderSessionsLookTakesAHayBaleAt10NotAt11(GameTestHelper helper) {
		lookEdge(helper, Blocks.HAY_BLOCK, Blocks.CAMPFIRE);
	}

	/**
	 * The table's edge for unlisted green beds in an older save's first session (the existing test checks an older list's
	 * brown beds): a Blueprint Table 12 blocks off keeps the look, 13 doesn't.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaCb1101TableEdgeGreen"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaCb1101TableEdgeGreen")
	public void qaAnOlderSessionsLookTakesAGreenBedsTableAt12NotAt13(GameTestHelper helper) {
		QaVillageGuards1031GameTests.campArea(helper);
		ServerLevel level = helper.getLevel();
		use(helper, preB715());
		BlockPos in = QaVillageGuards1031GameTests.bed(helper, new BlockPos(15, 2, 10), Blocks.GREEN_BED);
		BlockPos out = QaVillageGuards1031GameTests.bed(helper, new BlockPos(14, 2, 10), Blocks.GREEN_BED);
		helper.setBlock(new BlockPos(14, 2, 14), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(16, 2, 14), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(27, 2, 11), ModBlocks.BLUEPRINT_TABLE); // 12 from in, 13 from out
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CampBeds.isWagonBed(level, in), "a green bedroll with the table at 12 isn't known by the old camp's look");
			helper.assertTrue(!CampBeds.isWagonBed(level, out), "a green bed with the table at 13 is taken for a wagon bed");
			helper.succeed();
		});
	}

	/**
	 * In an older save's first session (the look on), red, white and black beds with a campfire, a hay bale and a Blueprint
	 * Table right beside them are homes: only a brown or green bed is ever a wagon bed. A brown bed in the same spot shows
	 * the look was there.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaCb1101OtherColours"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaCb1101OtherColours")
	public void qaBedsOfOtherColoursWithTheWholeCampLookAreHomesInAnOlderSession(GameTestHelper helper) {
		QaVillageGuards1031GameTests.campArea(helper);
		ServerLevel level = helper.getLevel();
		use(helper, preB715());
		List<BlockPos> others = List.of(QaVillageGuards1031GameTests.bed(helper, new BlockPos(10, 2, 10), Blocks.RED_BED),
			QaVillageGuards1031GameTests.bed(helper, new BlockPos(12, 2, 10), Blocks.WHITE_BED),
			QaVillageGuards1031GameTests.bed(helper, new BlockPos(14, 2, 10), Blocks.BLACK_BED));
		BlockPos brown = QaVillageGuards1031GameTests.bed(helper, new BlockPos(16, 2, 10), Blocks.BROWN_BED);
		helper.setBlock(new BlockPos(12, 2, 13), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(14, 2, 13), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(16, 2, 13), ModBlocks.BLUEPRINT_TABLE);
		helper.runAfterDelay(3, () -> {
			for (BlockPos b : others) {
				helper.assertTrue(!CampBeds.isWagonBed(level, b) && !CampBeds.Data.get(level).has(b),
					level.getBlockState(b).getBlock().getName().getString() + " by a campfire, hay and a table is taken for a wagon bed");
			}
			helper.assertTrue(CampBeds.isWagonBed(level, brown), "set-up: the brown control bed isn't known by the camp's look");
			helper.succeed();
		});
	}

	// --- a new world (its list complete): the look never runs (B730, B732) ---------------------------------------------

	/**
	 * In a world started with the list, a cabin's brown and green beds with a campfire 10 off, a hay bale 10 off and the
	 * village builder's Blueprint Table 12 off (each at the old look's far edge) are homes: their residents keep them at the
	 * hall's round, both count in Beds, and both are on the hall's bed list. Then the same beds, asked about under an older
	 * save's list, have the look (so the layout really is the camp's).
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaCb1101FarEdgesNewWorld"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCb1101FarEdgesNewWorld")
	public void qaACabinWithTheCampLookAtItsFarEdgesIsAHomeInANewWorld(GameTestHelper helper) {
		QaVillageGuards1031GameTests.campArea(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CampBeds.Data.get(level).complete() && !CampBeds.Data.get(level).looks(),
			"set-up: this world's wagon-bed list isn't the complete one a new world starts (a reused local gameTest world?)");
		BlockPos brown = QaVillageGuards1031GameTests.bed(helper, new BlockPos(12, 2, 10), Blocks.BROWN_BED); // head (12, 2, 11)
		BlockPos green = QaVillageGuards1031GameTests.bed(helper, new BlockPos(13, 2, 10), Blocks.GREEN_BED); // head (13, 2, 11)
		helper.setBlock(new BlockPos(23, 2, 11), Blocks.CAMPFIRE); // 11 from brown, 10 from green
		helper.setBlock(new BlockPos(3, 2, 11), Blocks.HAY_BLOCK); // 9 from brown, 10 from green
		BlockPos table = new BlockPos(13, 2, 23); // 12 from both
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(new BlockPos(2, 2, 11), Blocks.CAMPFIRE); // brown's campfire, 10 off
		Villager builder = resident(helper, new BlockPos(14, 2, 24));
		Builders.employ(level, builder, helper.absolutePos(table));
		List<Villager> residents = List.of(resident(helper, new BlockPos(12, 2, 8)), resident(helper, new BlockPos(13, 2, 8)));
		List<BlockPos> beds = List.of(brown, green);
		helper.runAfterDelay(3, () -> {
			for (int i = 0; i < 2; i++) {
				helper.assertTrue(CampBeds.claim(level, residents.get(i), beds.get(i)), "set-up: resident " + i + " couldn't take their bed");
			}
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				List<BlockPos> listed = HallBeds.options(level, helper.absolutePos(HALL)).stream().map(HallBeds.Option::bed).toList();
				for (int i = 0; i < 2; i++) {
					helper.assertTrue(!CampBeds.isWagonBed(level, beds.get(i)) && beds.get(i).equals(QaVillageGuards1031GameTests.home(residents.get(i))),
						"cabin bed " + i + " in a new world: wagon " + CampBeds.isWagonBed(level, beds.get(i)) + ", home " + QaVillageGuards1031GameTests.home(residents.get(i)));
					helper.assertTrue(listed.contains(beds.get(i)), "cabin bed " + i + " isn't on the hall's bed list: " + listed);
				}
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
				helper.assertTrue(census.beds() == 2 && census.wagonBeds() == 0, census(helper));
				use(helper, preB715());
				helper.assertTrue(CampBeds.isWagonBed(level, brown) && CampBeds.isWagonBed(level, green),
					"set-up: the layout doesn't have the old camp's look (brown " + CampBeds.isWagonBed(level, brown) + ", green " + CampBeds.isWagonBed(level, green) + ")");
				helper.succeed();
			});
		});
	}

	/**
	 * Through the real tick loop: in a new world a villager (AI on) moves into a player's brown cabin bed beside a
	 * campfire, a hay bale and the village builder's Blueprint Table by itself, and still has it after the hall's rounds.
	 */
	//$ gametest_ticks_batch HUGE '700' '"qaCb1101ClaimsByItself"'
	@GameTest(template = HUGE, timeoutTicks = 700, batch = "qaCb1101ClaimsByItself")
	public void qaAVillagerMovesIntoACabinBedByItselfAndKeepsIt(GameTestHelper helper) {
		QaVillageGuards1031GameTests.campArea(helper);
		roundsEvery(helper, 20);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CampBeds.Data.get(level).complete(), "set-up: this world's wagon-bed list isn't complete (a reused local gameTest world?)");
		BlockPos bed = QaVillageGuards1031GameTests.bed(helper, new BlockPos(12, 2, 10), Blocks.BROWN_BED);
		helper.setBlock(new BlockPos(14, 2, 13), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(10, 2, 13), Blocks.HAY_BLOCK);
		BlockPos table = new BlockPos(16, 2, 12);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = resident(helper, new BlockPos(17, 2, 13));
		Builders.employ(level, builder, helper.absolutePos(table));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager settler = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 8));
		long[] movedIn = {-1};
		helper.onEachTick(() -> {
			if (movedIn[0] < 0 && bed.equals(QaVillageGuards1031GameTests.home(settler))) {
				movedIn[0] = level.getGameTime();
			}
			if (movedIn[0] >= 0 && level.getGameTime() - movedIn[0] >= 60) {
				helper.assertTrue(bed.equals(QaVillageGuards1031GameTests.home(settler)) && !CampBeds.isWagonBed(level, bed),
					"the villager lost the cabin bed it moved into at the hall's rounds: home " + QaVillageGuards1031GameTests.home(settler)
						+ ", wagon " + CampBeds.isWagonBed(level, bed));
				helper.assertTrue(VillageHalls.census(level, helper.absolutePos(HALL)).beds() == 1, census(helper));
				helper.succeed();
			}
		});
	}

	// --- a server whose list an older version saved (B749) ----------------------------------------------------------

	/**
	 * The owner's server (a list saved before B730: this format, no "complete" mark): a settlers' wagon placed now has its
	 * bedrolls listed as wagon beds, and a player's green bed put right beside the camp (its campfire, hay and Blueprint
	 * Table close) is a home. After a save and reload the same holds, and at the hall's round the resident keeps the
	 * player's bed and the census counts 1 bed and the camp's wagon beds.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaCb1101OwnersServerCamp"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCb1101OwnersServerCamp")
	public void qaOnAServerWithAnOlderListARealCampsBedsAreWagonBedsAndAPlayersBedBesideThemIsAHome(GameTestHelper helper) {
		QaVillageGuards1031GameTests.campArea(helper);
		ServerLevel level = helper.getLevel();
		CampBeds.Data list = use(helper, preB730());
		helper.assertTrue(!list.complete(), "set-up: an older list is taken for complete");
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		BoundingBox[] campBox = new BoundingBox[1];
		List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(new BlockPos(15, 2, 4)), b -> campBox[0] = b);
		helper.assertTrue(!settlers.isEmpty() && campBox[0] != null, "set-up: no camp was placed");
		settlers.forEach(v -> v.setNoAi(true));
		List<BlockPos> rolls = heads(helper, campBox[0], true);
		helper.assertTrue(!rolls.isEmpty(), "set-up: the camp has no bedrolls");
		for (BlockPos r : rolls) {
			helper.assertTrue(CampBeds.Data.get(level).has(r) && CampBeds.isWagonBed(level, r), "a camp placed on the owner's server didn't list its bedroll " + r);
		}
		// the player's bed goes right by the camp: its head within 10 of the camp's campfire and hay bale and 12 of its table
		BoundingBox cb = campBox[0];
		List<BlockPos> fires = new ArrayList<>();
		List<BlockPos> hay = new ArrayList<>();
		List<BlockPos> tables = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(cb.minX(), cb.minY(), cb.minZ(), cb.maxX(), cb.maxY(), cb.maxZ())) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.CAMPFIRES)) {
				fires.add(p.immutable());
			} else if (s.is(Blocks.HAY_BLOCK)) {
				hay.add(p.immutable());
			} else if (s.is(ModBlocks.BLUEPRINT_TABLE) || s.is(ModBlocks.BUILDERS_BENCH)) {
				tables.add(p.immutable());
			}
		}
		helper.assertTrue(!fires.isEmpty() && !hay.isEmpty() && !tables.isEmpty(), "set-up: the camp has fires " + fires + ", hay " + hay + ", tables " + tables);
		BlockPos foot = null; // the free spot nearest the hall (inside the village's reach)
		BlockPos hall = helper.absolutePos(HALL);
		for (int z = 1; z < 27; z++) {
			for (int x = 1; x < 29; x++) {
				BlockPos f = new BlockPos(x, 2, z);
				BlockPos head = helper.absolutePos(f.south());
				if (!helper.getBlockState(f).isAir() || !helper.getBlockState(f.south()).isAir() || cb.inflatedBy(1).isInside(head)
					|| cb.inflatedBy(1).isInside(helper.absolutePos(f)) || head.distManhattan(hall) < 3) {
					continue;
				}
				if (near(head, fires, 10) && near(head, hay, 10) && near(head, tables, 12)
					&& (foot == null || head.distSqr(hall) < helper.absolutePos(foot.south()).distSqr(hall))) {
					foot = f;
				}
			}
		}
		BlockPos mine = foot == null ? null : QaVillageGuards1031GameTests.bed(helper, foot, Blocks.GREEN_BED);
		helper.assertTrue(mine != null, "set-up: no room for the player's bed by the camp");
		BlockPos cabin = mine;
		Villager resident = resident(helper, new BlockPos(1, 2, 28));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(!CampBeds.isWagonBed(level, cabin), "the player's bed beside the camp, placed on the owner's server, is a wagon bed");
			CampBeds.Data reloaded = reload(helper);
			for (BlockPos r : rolls) {
				helper.assertTrue(reloaded.has(r) && CampBeds.isWagonBed(level, r), "after a reload the camp's bedroll " + r + " is no wagon bed");
			}
			helper.assertTrue(!CampBeds.isWagonBed(level, cabin), "after a reload the player's bed is a wagon bed");
			helper.assertTrue(CampBeds.claim(level, resident, cabin), "set-up: the resident couldn't take the player's bed");
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(cabin.equals(QaVillageGuards1031GameTests.home(resident)) && !CampBeds.isWagonBed(level, cabin),
					"at the hall's round the player's bed was taken back as a wagon bed: home " + QaVillageGuards1031GameTests.home(resident));
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
				helper.assertTrue(census.beds() == 1 && census.wagonBeds() == rolls.size(), census(helper) + " (camp bedrolls " + rolls.size() + ")");
				use(helper, preB715());
				helper.assertTrue(CampBeds.isWagonBed(level, cabin), "set-up: the player's bed doesn't have the old camp's look, so the test proves nothing");
				helper.succeed();
			});
		});
	}

	/**
	 * A list from before B715 holding an old camp's bedroll (unverified) that nobody asks about in the first session: after
	 * the save it is still looked at (and kept, the camp's look being there), while a brown bed the player puts beside it
	 * after the save, by the same campfire, hay and table, is a home. After a second save the bedroll is checked (listed),
	 * and the new bed stays a home.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaCb1101UnlookedOldBed"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaCb1101UnlookedOldBed")
	public void qaAnOldListsUnlookedBedrollSurvivesTheFirstSaveAndANewBedBesideItIsAHome(GameTestHelper helper) {
		QaVillageGuards1031GameTests.campArea(helper);
		ServerLevel level = helper.getLevel();
		BlockPos roll = QaVillageGuards1031GameTests.bed(helper, new BlockPos(10, 2, 10), Blocks.BROWN_BED);
		helper.setBlock(new BlockPos(12, 2, 13), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(8, 2, 13), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(14, 2, 12), ModBlocks.BLUEPRINT_TABLE);
		CampBeds.Data old = use(helper, preB715(roll.asLong()));
		helper.assertTrue(old.unverified(roll) && old.looks(), "set-up: the old list's bedroll isn't unverified");
		CampBeds.Data first = reload(helper); // the first session ends before anything asked
		helper.assertTrue(first.unverified(roll) && !first.looks() && !first.complete(),
			"after the first save: unverified " + first.unverified(roll) + ", looks " + first.looks());
		BlockPos cabin = QaVillageGuards1031GameTests.bed(helper, new BlockPos(11, 2, 10), Blocks.BROWN_BED);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CampBeds.isWagonBed(level, roll), "the old camp's bedroll, never looked at, was let go after the first save");
			helper.assertTrue(!CampBeds.isWagonBed(level, cabin), "a brown bed placed after the first save beside the old camp is a wagon bed");
			CampBeds.Data second = reload(helper);
			helper.assertTrue(second.has(roll) && !second.unverified(roll) && !second.has(cabin) && !CampBeds.isWagonBed(level, cabin),
				"after a second save: bedroll listed " + second.has(roll) + ", new bed listed " + second.has(cabin));
			helper.succeed();
		});
	}

	// --- a bed put back where a wagon bed stood, the camp gone ---------------------------------------------------------

	/** The camp's bedroll at {@code head}, broken (both halves); its facing. */
	private static Direction breakBed(ServerLevel level, BlockPos head) {
		Direction facing = level.getBlockState(head).getValue(BedBlock.FACING);
		level.setBlock(head.relative(facing.getOpposite()), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
		level.setBlock(head, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
		return facing;
	}

	private static void placeBed(ServerLevel level, BlockPos head, Direction facing, Block bed) {
		level.setBlockAndUpdate(head.relative(facing.getOpposite()), bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT));
		level.setBlockAndUpdate(head, bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.HEAD));
	}

	/**
	 * A real camp, its village founded (the hall's round holds its bedrolls); then the camp is cleared away (fire, hay,
	 * table) and one bedroll broken. After a hall round the player puts a brown bed back on that spot: it is a home, its
	 * resident keeps it at the next rounds, and it counts in Beds.
	 */
	//$ gametest_ticks_batch HUGE '300' '"qaCb1101RePlacedAfterRound"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "qaCb1101RePlacedAfterRound")
	public void qaABedPutBackWhereAWagonBedStoodIsAHome(GameTestHelper helper) {
		rePlaced(helper, true);
	}

	static void rePlaced(GameTestHelper helper, boolean roundBetween) {
		QaVillageGuards1031GameTests.campArea(helper);
		roundsEvery(helper, 20);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		BoundingBox[] campBox = new BoundingBox[1];
		List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(new BlockPos(15, 2, 4)), b -> campBox[0] = b);
		helper.assertTrue(!settlers.isEmpty() && campBox[0] != null, "set-up: no camp was placed");
		settlers.forEach(v -> v.setNoAi(true));
		List<BlockPos> rolls = heads(helper, campBox[0], true);
		helper.assertTrue(!rolls.isEmpty(), "set-up: the camp has no bedrolls");
		BlockPos spot = rolls.get(0);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(CampBeds.isWagonBed(level, spot) && level.getPoiManager().getFreeTickets(spot) == 0, "set-up: the hall didn't hold the camp's bedroll");
			helper.assertTrue(clearCampLook(helper) >= 3, "set-up: the camp's campfire, hay and table weren't found");
			Direction facing = breakBed(level, spot);
			Runnable putBack = () -> {
				placeBed(level, spot, facing, Blocks.BROWN_BED);
				Villager resident = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 26));
				resident.setNoAi(true);
				helper.runAfterDelay(3, () -> {
					helper.assertTrue(CampBeds.claim(level, resident, spot), "the bed put back where the bedroll stood can't be taken (wagon "
						+ CampBeds.isWagonBed(level, spot) + ", listed " + CampBeds.Data.get(level).has(spot) + ")");
					helper.runAfterDelay(45, () -> {
						helper.assertTrue(!CampBeds.isWagonBed(level, spot) && spot.equals(QaVillageGuards1031GameTests.home(resident)),
							"the bed put back where a wagon bed stood is taken for one: wagon " + CampBeds.isWagonBed(level, spot) + ", listed "
								+ CampBeds.Data.get(level).has(spot) + ", home " + QaVillageGuards1031GameTests.home(resident));
						VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
						helper.assertTrue(census.beds() == 1 && census.wagonBeds() == rolls.size() - 1, census(helper) + " (bedrolls left " + (rolls.size() - 1) + ")");
						helper.succeed();
					});
				});
			};
			if (roundBetween) {
				helper.runAfterDelay(45, () -> {
					helper.assertTrue(!CampBeds.Data.get(level).has(spot), "the hall's round didn't take the broken bedroll off the list");
					putBack.run();
				});
			} else {
				putBack.run();
			}
		});
	}
}
