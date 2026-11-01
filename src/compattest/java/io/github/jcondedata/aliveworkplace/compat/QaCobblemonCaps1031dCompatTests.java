package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.gem.GemGrowers;
import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.PokemonFeatures;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (ROADMAP 28.8, 28.10, 28.11), written from the spec after the fixes of B464, B465 and B490: the Habitat Keeper's
 * 32-block lure-spot range from both sides on diagonals and up and down (a spot taken is a spot used; a spot refused
 * says how far it is), the Camp Cook's 16 Poké Snacks from 14, 17 and split across two chests, and the Gem Grower's 8
 * Blank TMs (Cobblemon 1.8's recipe makes 2) from 6, 7 and 8, split across chests and with a renamed one.
 */
public class QaCobblemonCaps1031dCompatTests implements FabricGameTest {
	private static final String AREA = QaCampHabitatCompatTests.AREA;
	private static final BlockPos STATION = QaCampHabitatCompatTests.STATION;
	private static final BlockPos CHEST = QaCampHabitatCompatTests.CHEST;
	private static final BlockPos CHEST_TWO = new BlockPos(0, 2, 2);

	// ------------------------------------------------------------------ Habitat Keeper: lure spots (B464)

	/** Marks the spot {@code dx dy dz} from her pasture (the marked block is one below it) and returns the lines heard. */
	private static List<String> mark(GameTestHelper helper, ServerPlayer player, List<Component> seen, Villager keeper, int dx, int dy, int dz) {
		seen.clear();
		HabitatKeepers.markSpot(player, keeper, HabitatKeeperCompatTests.marker(helper, STATION.offset(dx, dy - 1, dz)));
		return QaCampHabitatCompatTests.lines(seen);
	}

	private static String keeps(Villager keeper, BlockPos spot, int of) {
		return keeper.getDisplayName().getString() + " keeps a snack set out at " + spot.getX() + " " + spot.getY() + " " + spot.getZ()
			+ " (" + of + " of 3 lure spots)";
	}

	/** Asserts the spot {@code dx dy dz} out is taken (with her line), and used. */
	private static void taken(GameTestHelper helper, ServerPlayer player, List<Component> seen, Villager keeper, int dx, int dy, int dz, int of) {
		List<String> heard = mark(helper, player, seen, keeper, dx, dy, dz);
		BlockPos pasture = helper.absolutePos(STATION);
		BlockPos spot = pasture.offset(dx, dy, dz);
		String where = dx + " " + dy + " " + dz + " (" + String.format("%.2f", Math.sqrt(spot.distSqr(pasture))) + " blocks)";
		helper.assertTrue(HabitatKeepers.spots(keeper).contains(spot), "the spot " + where + " wasn't taken; heard " + heard);
		helper.assertTrue(heard.contains(keeps(keeper, spot, of)), "the spot " + where + " was taken, but the line reads " + heard);
		helper.assertTrue(HabitatKeepers.lureSpots(helper.getLevel(), keeper, pasture).contains(spot),
			"the spot " + where + " was taken but she never uses it: " + HabitatKeepers.lureSpots(helper.getLevel(), keeper, pasture));
	}

	/** Asserts the spot {@code dx dy dz} out is refused with exactly {@code line}, and not added. */
	private static void refused(GameTestHelper helper, ServerPlayer player, List<Component> seen, Villager keeper, int dx, int dy, int dz, String line) {
		List<BlockPos> before = HabitatKeepers.spots(keeper);
		List<String> heard = mark(helper, player, seen, keeper, dx, dy, dz);
		BlockPos pasture = helper.absolutePos(STATION);
		BlockPos spot = pasture.offset(dx, dy, dz);
		String where = dx + " " + dy + " " + dz + " (" + String.format("%.2f", Math.sqrt(spot.distSqr(pasture))) + " blocks)";
		helper.assertTrue(HabitatKeepers.spots(keeper).equals(before), "the spot " + where + " changed her spots: " + before + " -> "
			+ HabitatKeepers.spots(keeper) + "; heard " + heard);
		helper.assertTrue(heard.equals(List.of(line)), "the spot " + where + " should be refused with '" + line + "', heard " + heard);
	}

	private static Villager stillKeeper(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		return keeper;
	}

	/** Just inside the 32 on diagonals (31 east 7 south: 31.78; 7 west 31 north), both are taken and used. */
	//$ gametest_ticks_batch AREA '100' '"qa_caps_lure_inside"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_caps_lure_inside")
	public void qaDiagonalsJustInsideThirtyTwoAreTakenAndUsed(GameTestHelper helper) {
		Villager keeper = stillKeeper(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaCampHabitatCompatTests.listener(helper, seen, "qa-caps-in");
		taken(helper, player, seen, keeper, 31, 0, 7, 1);
		taken(helper, player, seen, keeper, -7, 0, -31, 2);
		taken(helper, player, seen, keeper, -32, 0, 0, 3);
		helper.succeed();
	}

	/**
	 * Just beyond the 32 on diagonals: 31 east 8 south (32.02) and 32 east 1 south (32.02) are refused, and the line never
	 * says "32 blocks ... within 32" (it reads 33).
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_caps_lure_outside"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_caps_lure_outside")
	public void qaDiagonalsJustBeyondThirtyTwoAreRefusedAsThirtyThree(GameTestHelper helper) {
		Villager keeper = stillKeeper(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaCampHabitatCompatTests.listener(helper, seen, "qa-caps-out");
		String line = "That spot is 33 blocks from her pasture: lure spots go within 32";
		refused(helper, player, seen, keeper, 31, 0, 8, line);
		refused(helper, player, seen, keeper, 32, 0, 1, line);
		refused(helper, player, seen, keeper, -1, 0, -32, line);
		refused(helper, player, seen, keeper, -8, 0, 31, line);
		helper.succeed();
	}

	/**
	 * Height counts too: a spot 32 straight up and one 31 east 7 up are taken and used; 32 east 1 up and 32 east 1 down
	 * (32.02) are refused.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_caps_lure_height"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_caps_lure_height")
	public void qaHeightCountsInTheThirtyTwo(GameTestHelper helper) {
		Villager keeper = stillKeeper(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaCampHabitatCompatTests.listener(helper, seen, "qa-caps-up");
		String line = "That spot is 33 blocks from her pasture: lure spots go within 32";
		refused(helper, player, seen, keeper, 32, 1, 0, line);
		refused(helper, player, seen, keeper, 32, -1, 0, line);
		taken(helper, player, seen, keeper, 0, 32, 0, 1);
		taken(helper, player, seen, keeper, 31, 7, 0, 2);
		helper.succeed();
	}

	/** With three spots taken, a fourth just beyond the 32 is refused and does not push out her oldest. */
	//$ gametest_ticks_batch AREA '100' '"qa_caps_lure_full"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_caps_lure_full")
	public void qaARefusedFourthSpotKeepsAllThree(GameTestHelper helper) {
		Villager keeper = stillKeeper(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaCampHabitatCompatTests.listener(helper, seen, "qa-caps-full");
		taken(helper, player, seen, keeper, 20, 0, 0, 1);
		taken(helper, player, seen, keeper, 0, 0, 20, 2);
		taken(helper, player, seen, keeper, 31, 0, 7, 3);
		refused(helper, player, seen, keeper, 31, 0, 8, "That spot is 33 blocks from her pasture: lure spots go within 32");
		helper.assertTrue(HabitatKeepers.spots(keeper).size() == 3, "after a refused fourth: " + HabitatKeepers.spots(keeper));
		helper.succeed();
	}

	/** A spot taken just inside the 32 on a diagonal is still hers and still used after a save and reload. */
	//$ gametest_ticks_batch AREA '100' '"qa_caps_lure_saved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_caps_lure_saved")
	public void qaADiagonalSpotIsUsedAfterAReload(GameTestHelper helper) {
		Villager keeper = stillKeeper(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaCampHabitatCompatTests.listener(helper, seen, "qa-caps-save");
		taken(helper, player, seen, keeper, 31, 0, 7, 1);
		BlockPos pasture = helper.absolutePos(STATION);
		BlockPos spot = pasture.offset(31, 0, 7);
		CompoundTag tag = new CompoundTag();
		keeper.saveWithoutId(tag);
		Villager reloaded = EntityType.VILLAGER.create(helper.getLevel());
		reloaded.load(tag);
		helper.assertTrue(HabitatKeepers.spots(reloaded).equals(List.of(spot)), "after a reload her spots are " + HabitatKeepers.spots(reloaded));
		helper.assertTrue(HabitatKeepers.lureSpots(helper.getLevel(), reloaded, pasture).equals(List.of(spot)),
			"after a reload she sets snacks out at " + HabitatKeepers.lureSpots(helper.getLevel(), reloaded, pasture));
		helper.succeed();
	}

	// ------------------------------------------------------------------ Camp Cook: up to 16 Poké Snacks (B465)

	private static Item snack() {
		return QaCampHabitatCompatTests.item("poke_snack");
	}

	/** The Poké Snacks in both chests (when there) and her bag. */
	private static int snacks(GameTestHelper helper, Villager cook) {
		int n = ((Container) helper.getBlockEntity(CHEST)).countItem(snack());
		if (helper.getBlockState(CHEST_TWO).is(Blocks.CHEST)) {
			n += ((Container) helper.getBlockEntity(CHEST_TWO)).countItem(snack());
		}
		return n + ModAttachments.BUILDER_BAG.getOrCreate(cook).stacks().stream().filter(s -> s.is(snack())).mapToInt(ItemStack::getCount).sum();
	}

	/**
	 * Watches the snacks from {@code start}: they reach 16, never go past 16, and stay there 800 ticks (the makings for
	 * three more are there all along).
	 */
	private static void toppedUpToSixteen(GameTestHelper helper, Villager cook, int start) {
		List<Integer> history = new ArrayList<>(List.of(start));
		long[] reached = {-1};
		helper.onEachTick(() -> {
			int n = snacks(helper, cook);
			if (n != history.get(history.size() - 1)) {
				history.add(n);
			}
			if (n >= 16 && reached[0] < 0) {
				reached[0] = helper.getTick();
			}
			helper.assertTrue(n <= 16, "she cooked past 16 Poké Snacks: " + history + "; she says " + QaCampHabitatCompatTests.said(helper, cook));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reached[0] >= 0, start + " snacks never became 16: " + history + "; she says " + QaCampHabitatCompatTests.said(helper, cook));
			helper.assertTrue(helper.getTick() - reached[0] >= 800, "waiting to see she stops: " + history);
		});
	}

	/** From 14 she cooks two (the first carries what it was cooked with) and stops at 16, never 17. */
	//$ gametest_ticks_batch AREA '4800' '"qa_caps_cook_14"'
	@GameTest(template = AREA, timeoutTicks = 4800, batch = "qa_caps_cook_14")
	public void qaFourteenSnacksBecomeSixteenNotSeventeen(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager cook = QaCampHabitatCompatTests.cook(helper, QaCampHabitatCompatTests.snackMakings(14));
		toppedUpToSixteen(helper, cook, 14);
	}

	/** With 17 already (more than she keeps), she cooks none. */
	//$ gametest_ticks_batch AREA '1200' '"qa_caps_cook_17"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_caps_cook_17")
	public void qaSeventeenSnacksGetNoMore(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager cook = QaCampHabitatCompatTests.cook(helper, QaCampHabitatCompatTests.snackMakings(17));
		int[] most = {17};
		helper.onEachTick(() -> most[0] = Math.max(most[0], snacks(helper, cook)));
		helper.runAfterDelay(1000, () -> {
			helper.assertTrue(most[0] == 17, "with 17 kept she cooked more: " + most[0] + "; she says " + QaCampHabitatCompatTests.said(helper, cook));
			helper.succeed();
		});
	}

	/** 8 snacks in one chest and 7 in another count together: she cooks one, to 16, and no more. */
	//$ gametest_ticks_batch AREA '4000' '"qa_caps_cook_split"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa_caps_cook_split")
	public void qaSnacksInTwoChestsCountTogether(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ItemStack[] makings = QaCampHabitatCompatTests.snackMakings(8);
		Villager cook = QaCampHabitatCompatTests.cook(helper, makings);
		helper.setBlock(CHEST_TWO, Blocks.CHEST);
		((Container) helper.getBlockEntity(CHEST_TWO)).setItem(0, new ItemStack(snack(), 7));
		toppedUpToSixteen(helper, cook, 15);
	}

	/** 8 and 8 in two chests are 16: she cooks none. */
	//$ gametest_ticks_batch AREA '1200' '"qa_caps_cook_split16"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_caps_cook_split16")
	public void qaEightAndEightInTwoChestsAreEnough(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager cook = QaCampHabitatCompatTests.cook(helper, QaCampHabitatCompatTests.snackMakings(8));
		helper.setBlock(CHEST_TWO, Blocks.CHEST);
		((Container) helper.getBlockEntity(CHEST_TWO)).setItem(0, new ItemStack(snack(), 8));
		int[] most = {16};
		helper.onEachTick(() -> most[0] = Math.max(most[0], snacks(helper, cook)));
		helper.runAfterDelay(1000, () -> {
			helper.assertTrue(most[0] == 16, "with 8 + 8 kept she cooked more: " + most[0] + "; she says " + QaCampHabitatCompatTests.said(helper, cook));
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ Gem Grower: up to 8 Blank TMs (B490, Cobblemon 1.8)

	private static Item tm() {
		return QaCampHabitatCompatTests.item("blank_tm");
	}

	private static int tms(GameTestHelper helper, BlockPos... chests) {
		int n = 0;
		for (BlockPos c : chests) {
			n += ((Container) helper.getBlockEntity(c)).countItem(tm());
		}
		return n;
	}

	/** Fresh chests at {@code at}, each with {@code tmsEach[i]} Blank TMs, glass and shards in the first. */
	private static List<BlockPos> chests(GameTestHelper helper, int[] tmsEach, BlockPos... at) {
		List<BlockPos> abs = new ArrayList<>();
		for (int i = 0; i < at.length; i++) {
			helper.setBlock(at[i], Blocks.AIR);
			helper.setBlock(at[i], Blocks.CHEST);
			Container c = helper.getBlockEntity(at[i]);
			if (tmsEach[i] > 0) {
				c.setItem(0, new ItemStack(tm(), tmsEach[i]));
			}
			if (i == 0) {
				c.setItem(1, new ItemStack(Items.GLASS, 16));
				c.setItem(2, new ItemStack(Items.AMETHYST_SHARD, 16));
			}
			abs.add(helper.absolutePos(at[i]));
		}
		return abs;
	}

	/**
	 * Straight through her Blank TM step, crafting until she stops: from 6 she makes one craft (2) to 8; from 7 and from 8
	 * none; split 3 + 3 over two chests she reaches 8; split 4 + 3 she stays at 7.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_caps_gem_step"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_caps_gem_step")
	public void qaBlankTmStepStopsAtEightFromBothSides(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		if (!PokemonFeatures.TYPE_GEMS.available()) {
			helper.succeed();
			return;
		}
		int[][] cases = {{6}, {7}, {8}, {3, 3}, {4, 3}, {0}};
		int[] expected = {8, 7, 8, 8, 7, 8};
		for (int k = 0; k < cases.length; k++) {
			BlockPos[] at = cases[k].length == 1 ? new BlockPos[] {CHEST} : new BlockPos[] {CHEST, CHEST_TWO};
			helper.setBlock(CHEST_TWO, Blocks.AIR);
			List<BlockPos> list = chests(helper, cases[k], at);
			for (int craft = 0; craft < 10 && GemGrowers.makeBlankTms(helper.getLevel(), list); craft++) {
				helper.assertTrue(tms(helper, at) <= GemGrowers.BLANK_TMS_KEPT, "from " + java.util.Arrays.toString(cases[k])
					+ " she made " + tms(helper, at) + " Blank TMs");
			}
			int got = tms(helper, at);
			helper.assertTrue(got == expected[k], "from " + java.util.Arrays.toString(cases[k]) + " Blank TMs she ended on " + got
				+ ", expected " + expected[k]);
		}
		helper.succeed();
	}

	/** Eight Blank TMs, one of them renamed by the player, are still eight: she makes none. */
	//$ gametest_ticks_batch AREA '100' '"qa_caps_gem_named"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_caps_gem_named")
	public void qaARenamedBlankTmStillCounts(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		if (!PokemonFeatures.TYPE_GEMS.available()) {
			helper.succeed();
			return;
		}
		List<BlockPos> list = chests(helper, new int[] {7}, CHEST);
		ItemStack named = new ItemStack(tm());
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Spare"));
		((Container) helper.getBlockEntity(CHEST)).setItem(3, named);
		boolean made = GemGrowers.makeBlankTms(helper.getLevel(), list);
		helper.assertTrue(!made && tms(helper, CHEST) == 8, "with 8 Blank TMs (one renamed) she made more: " + tms(helper, CHEST));
		helper.succeed();
	}

	/** At work from 6 Blank TMs (glass and shards there): she reaches 8 and never 9 or 10. */
	//$ gametest_ticks_batch AREA '2000' '"qa_caps_gem_six"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qa_caps_gem_six")
	public void qaSixBlankTmsBecomeEightAtWork(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		if (!PokemonFeatures.TYPE_GEMS.available()) {
			helper.succeed();
			return;
		}
		Villager grower = GemGrowerCompatTests.grower(helper, new ItemStack(tm(), 6), new ItemStack(Items.GLASS, 16), new ItemStack(Items.AMETHYST_SHARD, 16));
		long[] reached = {-1};
		helper.onEachTick(() -> {
			int n = tms(helper, CHEST) + ModAttachments.BUILDER_BAG.getOrCreate(grower).stacks().stream().filter(s -> s.is(tm()))
				.mapToInt(ItemStack::getCount).sum();
			helper.assertTrue(n <= GemGrowers.BLANK_TMS_KEPT, "more than 8 Blank TMs: " + n);
			if (n == 8 && reached[0] < 0) {
				reached[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reached[0] >= 0, "6 Blank TMs never became 8: " + tms(helper, CHEST));
			helper.assertTrue(helper.getTick() - reached[0] >= 400, "waiting to see she stops at 8");
		});
	}
}
