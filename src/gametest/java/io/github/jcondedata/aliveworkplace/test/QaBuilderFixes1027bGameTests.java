package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (qa-lane-b-1027-0638): adversarial checks of three landed fixes, written from the bugs' specs.
 * <ul>
 * <li>B462 (35.4 / 35.7): {@code /workplace cancel} on a Wonder waiting for its goods hands back exactly what the stone
 * took, once (a second cancel gives nothing more); an operator who isn't the owner can end it, and the goods and plan go
 * to the owner, not the operator; a wrong id leaves the Wonder going.</li>
 * <li>B470 (27.7): "never the same blueprint with the same mirroring within 24 blocks", centre to centre, on every side,
 * on diagonals, for odd and even footprints and a turned finished build; a mirrored twin doesn't count.</li>
 * <li>B487: the fixed doors, walked the other way (out) and with the build turned: the Healing Center IV garden door
 * (no walk covered IV), the lodge's front door outwards turned, Royal Hall II's east door outwards, and the Royal Hall's
 * vault door inwards turned half round.</li>
 * </ul>
 */
public class QaBuilderFixes1027bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");

	// ---------------------------------------------------------------- B462

	/** A Town hall owned by {@code owner}, a stone, and the folly at stage III, waiting for its goods. */
	private static WonderSite waitingWonder(GameTestHelper helper, ServerPlayer owner) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos hallRel = new BlockPos(14, 2, 14);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(hallRel);
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.FOUNDATION_STONE);
		BlockPos stone = helper.absolutePos(new BlockPos(3, 2, 3));
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, helper.absolutePos(hallRel), owner.getUUID(), "Owner", Chronicle.day(level));
		Leftovers.after(helper, () -> WonderManager.get(level).remove(site.id()));
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), stone.offset(1, 0, 1), Rotation.NONE, Mirror.NONE), stone);
		manager.setStage(site.id(), 3);
		return manager.site(site.id()).orElseThrow();
	}

	/** Uses whatever the player holds on the stone, as a player does. */
	private static void useOnStone(GameTestHelper helper, ServerPlayer player) {
		ServerLevel level = helper.getLevel();
		BlockPos stone = helper.absolutePos(new BlockPos(3, 2, 3));
		level.getBlockState(stone).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(stone), Direction.UP, stone, false));
	}

	private static int candles(ServerPlayer player) {
		return player.getInventory().countItem(Items.WHITE_CANDLE);
	}

	private static int plans(ServerPlayer player) {
		return player.getInventory().countItem(ModItems.WONDER_PLAN);
	}

	/**
	 * A full stack of 64 candles offered to the stone in two goes: the stone keeps what the stage needs; after
	 * /workplace cancel the player holds exactly 64 again and one plan (no candle lost or made), and a second cancel
	 * gives nothing more and doesn't say "Canceled the Wonder" again.
	 */
	//$ gametest_batch AREA '"qaB462Conserve"'
	@GameTest(template = AREA, batch = "qaB462Conserve")
	public void cancelHandsBackExactlyWhatTheStoneTookAndOnlyOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, seen, "QaB462Conserve");
		WonderSite site = waitingWonder(helper, owner);
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_CANDLE, 64));
		useOnStone(helper, owner);
		useOnStone(helper, owner); // what's left in hand, offered again
		int kept = 64 - candles(owner);
		helper.assertTrue(kept > 0, "setup: the stone took no candles");
		WonderSite now = WonderManager.get(level).site(site.id()).orElseThrow();
		helper.assertTrue(now.parts().isEmpty() && !now.started(), "setup: the stage isn't waiting for its goods (took " + kept + ")");
		seen.clear();
		String cmd = "workplace cancel " + site.id();
		owner.getServer().getCommands().performPrefixedCommand(owner.createCommandSourceStack(), cmd);
		List<String> first = seen.stream().map(Component::getString).toList();
		helper.assertTrue(WonderManager.get(level).site(site.id()).isEmpty(), "the Wonder goes on: " + first);
		helper.assertTrue(candles(owner) == 64, "the stone took " + kept + " candles but the player holds " + candles(owner) + " of 64 after the cancel: " + first);
		helper.assertTrue(plans(owner) == 1, "plans after the cancel: " + plans(owner));
		seen.clear();
		owner.getServer().getCommands().performPrefixedCommand(owner.createCommandSourceStack(), cmd);
		List<String> second = seen.stream().map(Component::getString).toList();
		helper.assertTrue(candles(owner) == 64 && plans(owner) == 1, "a second cancel gave more: " + candles(owner) + " candles, " + plans(owner) + " plans");
		helper.assertTrue(second.stream().noneMatch(s -> s.startsWith("Canceled the Wonder")), "a second cancel says it canceled the Wonder again: " + second);
		helper.assertTrue(!second.isEmpty(), "a second cancel says nothing at all");
		helper.succeed();
	}

	/**
	 * 35.4: the owner "or an operator" may end it. An operator who isn't the owner runs it: the Wonder ends, and the
	 * handed-in goods and the plan go to the owner (online), not into the operator's pockets.
	 */
	//$ gametest_batch AREA '"qaB462Operator"'
	@GameTest(template = AREA, batch = "qaB462Operator")
	public void anOperatorEndsSomeoneElsesWonderAndTheGoodsGoToTheOwner(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Component> ownerSaw = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, ownerSaw, "QaB462OpOwner");
		WonderSite site = waitingWonder(helper, owner);
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_CANDLE, 3));
		useOnStone(helper, owner);
		int kept = 3 - candles(owner);
		helper.assertTrue(kept > 0, "setup: no candles handed in");
		List<Component> opSaw = new ArrayList<>();
		ServerPlayer op = WonderFinishedGameTests.listener(helper, opSaw, "QaB462Op");
		owner.getServer().getCommands().performPrefixedCommand(op.createCommandSourceStack().withPermission(2), "workplace cancel " + site.id());
		List<String> told = opSaw.stream().map(Component::getString).toList();
		helper.assertTrue(WonderManager.get(level).site(site.id()).isEmpty(), "an operator's cancel left the Wonder going: " + told);
		helper.assertTrue(told.stream().noneMatch(s -> s.contains("Only the player who started")), "the operator was refused: " + told);
		helper.assertTrue(candles(op) == 0 && plans(op) == 0, "the operator got the owner's goods or plan: " + candles(op) + " candles, " + plans(op) + " plans");
		helper.assertTrue(candles(owner) == 3 && plans(owner) == 1, "the owner got back " + candles(owner) + " of 3 candles and " + plans(owner) + " plans");
		helper.succeed();
	}

	/** A wrong id (a fresh UUID) changes nothing: the Wonder goes on, its goods stay handed in, no plan appears. */
	//$ gametest_batch AREA '"qaB462WrongId"'
	@GameTest(template = AREA, batch = "qaB462WrongId")
	public void aWrongIdLeavesTheWaitingWonderAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, seen, "QaB462Wrong");
		WonderSite site = waitingWonder(helper, owner);
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_CANDLE, 3));
		useOnStone(helper, owner);
		int left = candles(owner);
		owner.getServer().getCommands().performPrefixedCommand(owner.createCommandSourceStack(), "workplace cancel " + UUID.randomUUID());
		List<String> told = seen.stream().map(Component::getString).toList();
		WonderSite now = WonderManager.get(level).site(site.id()).orElse(null);
		helper.assertTrue(now != null, "a wrong id ended the Wonder: " + told);
		helper.assertTrue(!now.delivered().isEmpty(), "a wrong id handed the goods back");
		helper.assertTrue(candles(owner) == left && plans(owner) == 0, "a wrong id gave candles or a plan");
		helper.assertTrue(told.stream().noneMatch(s -> s.startsWith("Canceled the Wonder")), "a wrong id says it canceled: " + told);
		helper.succeed();
	}

	// ---------------------------------------------------------------- B470

	private record Steward(BlockPos hall, CityPlan plan) {
	}

	/** The QA steward ground (as QaStewardSameRangeGameTests): flat grass, hall at (2,2,14), homes painted x 6..29, z 2..25. */
	private static Steward steward(GameTestHelper helper) {
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		BlockPos hallRel = new BlockPos(2, 2, 14);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(2, 2, 10), ModBlocks.BLUEPRINT_TABLE);
		BlockPos hall = helper.absolutePos(hallRel);
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		return new Steward(hall, CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
	}

	private static double centreX(BoundingBox b) {
		return (b.minX() + b.maxX() + 1) / 2.0;
	}

	private static double centreZ(BoundingBox b) {
		return (b.minZ() + b.maxZ() + 1) / 2.0;
	}

	/**
	 * The candidate {@code id} at (11, 13), turned counterclockwise (its front to the hall), against one finished
	 * {@code id} (turned {@code turn}, mirrored {@code mirror}) whose footprint's centre is ({@code dx}, {@code dz}) from
	 * the candidate's. Returns the verdict's reason, or "ok".
	 */
	private static String against(GameTestHelper helper, Steward s, ResourceLocation id, Rotation turn, Mirror mirror, double dx, double dz) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(11, 2, 13));
		Plots.Verdict alone = Plots.check(level, s.hall(), s.plan(), "homes", id, at, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
		helper.assertTrue(alone.plot().isPresent(), "setup: " + id + " alone isn't a plot: " + alone.reason());
		BoundingBox box = alone.plot().get().box();
		Vec3i size = BlueprintLibrary.get(level, id).orElseThrow().size();
		BoundingBox at0 = BlueprintOutline.bounds(new BlueprintData.Placement(Ids.of(level.dimension()), BlockPos.ZERO, turn, mirror), size);
		double ox = centreX(box) + dx - centreX(at0);
		double oz = centreZ(box) + dz - centreZ(at0);
		helper.assertTrue(ox == Math.rint(ox) && oz == Math.rint(oz), "setup: offset " + dx + ", " + dz + " can't be reached by " + id + " turned " + turn);
		BlueprintData.Placement p = new BlueprintData.Placement(Ids.of(level.dimension()), new BlockPos((int) ox, box.minY(), (int) oz), turn, mirror);
		BoundingBox fin = BlueprintOutline.bounds(p, size);
		helper.assertTrue(centreX(fin) - centreX(box) == dx && centreZ(fin) - centreZ(box) == dz, "setup: centres " + (centreX(fin) - centreX(box)) + ", " + (centreZ(fin) - centreZ(box)));
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(id, p, UUID.randomUUID());
		try {
			Plots.Verdict v = Plots.check(level, s.hall(), s.plan(), "homes", id, at, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
			return v.plot().isPresent() ? "ok" : v.reason().map(Enum::name).orElse("?");
		} finally {
			manager.forgetFinished(p);
		}
	}

	private static void expect(GameTestHelper helper, Steward s, ResourceLocation id, Rotation turn, Mirror mirror, double dx, double dz, boolean refused, List<String> wrong) {
		String got = against(helper, s, id, turn, mirror, dx, dz);
		boolean ok = refused ? got.equals("SAME_NEARBY") : got.equals("ok");
		if (!ok) {
			wrong.add(id.getPath() + " " + turn + " " + mirror + " at (" + dx + ", " + dz + ") = " + String.format("%.2f", Math.sqrt(dx * dx + dz * dz))
				+ ": " + got + " (expected " + (refused ? "SAME_NEARBY" : "a plot") + ")");
		}
	}

	/**
	 * Wells (7×7, odd): exactly 24 off is within 24 and refused on all four sides; 25 is not; diagonals 23.35 and 23.77
	 * refused, 24.04 allowed; a finished Well turned a quarter round still counts; a mirrored Well 14 off doesn't (same
	 * mirroring only) while an unmirrored one there does.
	 */
	//$ gametest_ticks_batch HUGE '20' '"qaB470Wells"'
	@GameTest(template = HUGE, timeoutTicks = 20, batch = "qaB470Wells")
	public void sameRangeIsTwentyFourCentreToCentreOnEverySide(GameTestHelper helper) {
		Leftovers.clear(helper);
		Steward s = steward(helper);
		ResourceLocation well = AliveWorkplace.id("well");
		List<String> wrong = new ArrayList<>();
		for (double[] d : new double[][] {{24, 0}, {-24, 0}, {0, 24}, {0, -24}, {16, 17}, {-23, 6}, {6, -23}, {-17, -16}}) {
			expect(helper, s, well, Rotation.NONE, Mirror.NONE, d[0], d[1], true, wrong);
		}
		for (double[] d : new double[][] {{25, 0}, {-25, 0}, {0, 25}, {0, -25}, {23, 7}, {-7, -23}, {-17, 17}}) {
			expect(helper, s, well, Rotation.NONE, Mirror.NONE, d[0], d[1], false, wrong);
		}
		expect(helper, s, well, Rotation.CLOCKWISE_90, Mirror.NONE, 24, 0, true, wrong);
		expect(helper, s, well, Rotation.CLOCKWISE_90, Mirror.NONE, 0, -25, false, wrong);
		expect(helper, s, well, Rotation.NONE, Mirror.NONE, 14, 0, true, wrong);
		expect(helper, s, well, Rotation.NONE, Mirror.FRONT_BACK, 14, 0, false, wrong);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * Even footprints (the Market Stall, 8×6) land on whole-block centres: 24 refused, 24.02 allowed; the Hay Cart (6×5)
	 * turned against an unturned one has centres half a block apart on both axes: 23.51 refused, 24.51 allowed.
	 */
	//$ gametest_ticks_batch HUGE '20' '"qaB470EvenOdd"'
	@GameTest(template = HUGE, timeoutTicks = 20, batch = "qaB470EvenOdd")
	public void evenAndOddFootprintsAreMeasuredTheSameWay(GameTestHelper helper) {
		Leftovers.clear(helper);
		Steward s = steward(helper);
		ResourceLocation stall = AliveWorkplace.id("market_stall");
		ResourceLocation cart = AliveWorkplace.id("hay_cart");
		List<String> wrong = new ArrayList<>();
		expect(helper, s, stall, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE, 24, 0, true, wrong);
		expect(helper, s, stall, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE, 0, -24, true, wrong);
		expect(helper, s, stall, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE, 24, 1, false, wrong);
		expect(helper, s, stall, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE, -1, -24, false, wrong);
		expect(helper, s, stall, Rotation.NONE, Mirror.NONE, -24, 0, true, wrong);
		expect(helper, s, stall, Rotation.NONE, Mirror.NONE, -25, 0, false, wrong);
		expect(helper, s, cart, Rotation.NONE, Mirror.NONE, 23.5, 0.5, true, wrong);
		expect(helper, s, cart, Rotation.NONE, Mirror.NONE, -23.5, -0.5, true, wrong);
		expect(helper, s, cart, Rotation.NONE, Mirror.NONE, 0.5, 23.5, true, wrong);
		expect(helper, s, cart, Rotation.NONE, Mirror.NONE, 24.5, 0.5, false, wrong);
		expect(helper, s, cart, Rotation.NONE, Mirror.NONE, -0.5, -24.5, false, wrong);
		expect(helper, s, cart, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE, 24, 0, true, wrong);
		expect(helper, s, cart, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE, 25, 0, false, wrong);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + String.join("; ", wrong));
		helper.succeed();
	}

	// ---------------------------------------------------------------- B487

	/**
	 * Healing Center IV (no walk covered it): from its back garden in by the garden door (5,1,14 above ground; the
	 * template's y + 4 for its 4-deep herb store, so the origin sits 4 below the floor), turned a quarter round.
	 */
	//$ gametest_ticks_batch HUGE '500' '"qaB487Healing4Turned"'
	@GameTest(template = HUGE, timeoutTicks = 500, batch = "qaB487Healing4Turned")
	public void aVillagerWalksInByTheTurnedHealingCenterIVsGardenDoor(GameTestHelper helper) {
		QaDoorwayClearanceGameTests.walkIn(helper, "healing_center_4", Rotation.CLOCKWISE_90, new BlockPos(25, -2, 4), new int[] {5, 5, 18}, new int[] {5, 5, 12}, new int[] {5, 5, 14});
	}

	/** The Pokemon Center lodge look II, turned counterclockwise: from inside (6,1,8) out by the front door (6,1,10) to the path (6,1,13). */
	//$ gametest_ticks_batch HUGE '500' '"qaB487LodgeOutTurned"'
	@GameTest(template = HUGE, timeoutTicks = 500, batch = "qaB487LodgeOutTurned")
	public void aVillagerWalksOutByTheTurnedLodgesFrontDoor(GameTestHelper helper) {
		QaDoorwayClearanceGameTests.walkIn(helper, "looks/lodge/pokemon_center_2", Rotation.COUNTERCLOCKWISE_90, new BlockPos(4, 2, 25), new int[] {6, 1, 8}, new int[] {6, 1, 13}, new int[] {6, 1, 10});
	}

	/** Royal Hall II: from the hall floor (15,2,17) out by the east door (16,1,17) to (19,1,17). */
	//$ gametest_ticks_batch HUGE '500' '"qaB487RoyalHallEastOut"'
	@GameTest(template = HUGE, timeoutTicks = 500, batch = "qaB487RoyalHallEastOut")
	public void aVillagerWalksOutByRoyalHallIIsEastDoor(GameTestHelper helper) {
		QaDoorwayClearanceGameTests.walkIn(helper, "realm/royal_hall_2", Rotation.NONE, new BlockPos(1, 2, 2), new int[] {15, 2, 17}, new int[] {19, 1, 17}, new int[] {16, 1, 17});
	}

	/** The Royal Hall turned half round: from the dais (13,2,17) into the vault (9,1,21) by its door (11,2,19). */
	//$ gametest_ticks_batch HUGE '500' '"qaB487VaultInTurned"'
	@GameTest(template = HUGE, timeoutTicks = 500, batch = "qaB487VaultInTurned")
	public void aVillagerWalksIntoTheTurnedRoyalHallsVaultFromTheDais(GameTestHelper helper) {
		QaDoorwayClearanceGameTests.walkIn(helper, "realm/royal_hall", Rotation.CLOCKWISE_180, new BlockPos(23, 2, 27), new int[] {13, 2, 17}, new int[] {9, 1, 21}, new int[] {11, 2, 19});
	}
}
