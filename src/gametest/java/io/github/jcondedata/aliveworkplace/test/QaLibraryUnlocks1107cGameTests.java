package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks.State;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * QA (lane-b-1107-0637) of the Blueprint library's unlocks (26.10, docs/wiki/blueprint-library.md), written from the
 * wiki page, not the code, for the corners no test named: a Wonder Plan used on the Table names its Wonder there, view
 * only (listed and previewed, never handed out); a Table far from every hall uses one server-wide set, which a new hall
 * takes in on its first use and an already-seeded hall doesn't; a found blueprint in a style unlocks its base build, and
 * an upgrade tier unlocks only itself; a Table between two villages uses the nearer hall, one block either way. Each test
 * is alone in its batch, with its own halls, the switch on and a small village radius (put back after).
 */
public class QaLibraryUnlocks1107cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final String NEW_LINE = QaBlueprintUnlocksGameTests.NEW_LINE;
	private static final String WONDER_ONLY = "message.aliveworkplace.table.wonder_only";

	private static TablePayloads.Entry entry(List<TablePayloads.Open> opened, ResourceLocation id) {
		return opened.isEmpty() ? null : opened.get(opened.size() - 1).entries().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
	}

	/** The server-wide set (Tables far from any hall) keeps {@code id} out after the test, so later villages aren't seeded with it. */
	private static void forgetWild(MinecraftServer server, ResourceLocation id) {
		try {
			Class<?> wild = Class.forName("io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks$Wild");
			java.lang.reflect.Method get = wild.getDeclaredMethod("get", MinecraftServer.class);
			get.setAccessible(true);
			Object set = get.invoke(null, server);
			java.lang.reflect.Field ids = wild.getDeclaredField("ids");
			ids.setAccessible(true);
			((Set<?>) ids.get(set)).remove(id);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("couldn't reset the server-wide unlocks: " + e);
		}
	}

	/**
	 * "A Wonder Plan used on the Table names its Wonder there (view only)": before, the Wonder is only a "???" (no entry
	 * by its id); the plan used on the Table through the player's own click says "new blueprint" once, keeps the plan in
	 * the hand, and the screen that opens lists the Wonder by its id, view only, with its size (previewed). Get Blueprint
	 * for it is refused with the "raised from its Wonder Plan" line and no Blank Blueprint used.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1107cWonderView"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1107cWonderView")
	public void aWonderPlanUsedOnTheTableNamesItsWonderViewOnly(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		BlockPos hallRel = QaBlueprintUnlocksGameTests.HALL;
		BlockPos tableRel = QaBlueprintUnlocksGameTests.TABLE;
		QaBlueprintUnlocksGameTests.village(helper, hallRel, tableRel);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(hallRel);
			BlockPos table = helper.absolutePos(tableRel);
			Wonder folly = Wonders.loaded().get(FOLLY);
			helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
			ResourceLocation id = WonderBlueprints.idOf(folly.id());
			helper.assertTrue(BlueprintUnlocks.state(level, hall, id) == State.HIDDEN, "the folly isn't a ??? to begin with: " + BlueprintUnlocks.state(level, hall, id));
			helper.assertTrue(TableServer.listing(level, hall).stream().noneMatch(e -> e.id().equals(id)), "the locked folly is listed by its id");
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, tableRel);
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.MAIN_HAND, WonderPlanItem.of(level, hall, folly, Component.literal("Testholm")));
			p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
			c.sent();
			QaBlueprintUnlocksGameTests.clickTable(helper, p, tableRel);
			helper.assertTrue(BlueprintUnlocks.state(level, hall, id) == State.VIEW_ONLY, "the plan used on the Table left the folly " + BlueprintUnlocks.state(level, hall, id));
			long lines = QaBlueprintUnlocksGameTests.linesOf(c.seen(), NEW_LINE);
			helper.assertTrue(lines == 1, "'new blueprint' was said " + lines + " times: " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(WonderPlanItem.plan(p.getMainHandItem()).isPresent(), "the Table took the Wonder Plan: " + p.getMainHandItem());
			List<TablePayloads.Open> opened = c.sent(TablePayloads.Open.class);
			helper.assertTrue(opened.size() == 1, "the Table screen opened " + opened.size() + " times");
			TablePayloads.Entry shown = entry(opened, id);
			helper.assertTrue(shown != null, "the screen that opened doesn't name the folly");
			helper.assertTrue(shown.state() == State.VIEW_ONLY.ordinal() && !shown.unlocked(), "the folly is listed as state " + shown.state() + ", not view only");
			helper.assertTrue(shown.sizeX() > 0 && shown.sizeY() > 0 && shown.sizeZ() > 0, "the view-only folly has no size to preview: " + shown);
			p.containerMenu = p.inventoryMenu;
			helper.assertFalse(TableServer.take(p, table, id), "the Table handed out the view-only folly");
			helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 2 && p.getInventory().countItem(ModItems.BLUEPRINT) == 0,
				"the refusal used a Blank Blueprint or gave a blueprint: " + p.getInventory().countItem(ModItems.BLANK_BLUEPRINT));
			Component refused = QaStageFourGameTests.find(c.seen(), WONDER_ONLY);
			helper.assertTrue(refused != null, "no 'raised from its Wonder Plan' line: " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(refused.getString().contains("raised from its Wonder Plan, not handed out at the Table"), "the line reads: " + refused.getString());
			helper.succeed();
		});
	}

	/**
	 * "With no hall in reach, one server-wide set is used; the first time a hall is used it takes that set in too" and
	 * the known limit "a village that has already been seeded doesn't take it in later": a found Market Stall used on a
	 * Table far from every hall unlocks it there and at a second far Table; the hall seeded before stays without it; a
	 * hall used for the first time after has it.
	 */
	//$ gametest_ticks_batch HUGE '80' '"qa1107cWildSet"'
	@GameTest(template = HUGE, timeoutTicks = 80, batch = "qa1107cWildSet")
	public void aTableFarFromEveryHallSharesOneSetThatANewHallTakesIn(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 8);
		ServerLevel level = helper.getLevel();
		ResourceLocation stall = StarterBlueprints.MARKET_STALL.id();
		boolean wasWild = BlueprintUnlocks.unlocked(level, null).contains(stall);
		Leftovers.after(helper, () -> {
			if (!wasWild) {
				forgetWild(level.getServer(), stall);
			}
		});
		BlockPos hallA = new BlockPos(1, 2, 1);
		BlockPos far1 = new BlockPos(25, 2, 25);
		BlockPos far2 = new BlockPos(25, 2, 18);
		BlockPos hallB = new BlockPos(14, 2, 1);
		helper.setBlock(hallA, ModBlocks.VILLAGE_HALL);
		helper.setBlock(far1, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(far2, ModBlocks.BLUEPRINT_TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			helper.assertTrue(!wasWild, "the server-wide set already had the Market Stall (left by another test)");
			helper.assertTrue(BlueprintUnlocks.village(level, helper.absolutePos(far1)) == null && BlueprintUnlocks.village(level, helper.absolutePos(far2)) == null,
				"a far Table found a hall: " + BlueprintUnlocks.village(level, helper.absolutePos(far1)));
			helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, hallA, StarterBlueprints.MARKET_STALL) == State.LOCKED, "hall A's Market Stall isn't locked to begin with");
			helper.assertTrue(BlueprintUnlocks.state(level, null, stall) == State.LOCKED, "the server-wide Market Stall isn't locked to begin with");
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, far1);
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(stall, StarterBlueprints.MARKET_STALL.size()));
			QaBlueprintUnlocksGameTests.clickTable(helper, p, far1);
			p.containerMenu = p.inventoryMenu;
			helper.assertTrue(BlueprintUnlocks.state(level, null, stall) == State.UNLOCKED, "the found Market Stall didn't unlock it at a Table far from every hall");
			helper.assertTrue(QaBlueprintUnlocksGameTests.linesOf(c.seen(), NEW_LINE) == 1, "'new blueprint' was said " + QaBlueprintUnlocksGameTests.linesOf(c.seen(), NEW_LINE) + " times");
			BlockPos at2 = helper.absolutePos(far2);
			p.moveTo(at2.getX() + 1.5, at2.getY(), at2.getZ() + 0.5);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			c.sent();
			QaBlueprintUnlocksGameTests.clickTable(helper, p, far2);
			p.containerMenu = p.inventoryMenu;
			TablePayloads.Entry shown = entry(c.sent(TablePayloads.Open.class), stall);
			helper.assertTrue(shown != null && shown.unlocked(), "the second far Table shows the Market Stall as " + shown);
			p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
			helper.assertTrue(TableServer.take(p, at2, stall), "the second far Table refused the Market Stall");
			helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, hallA, StarterBlueprints.MARKET_STALL) == State.LOCKED,
				"hall A, seeded before, took in the server-wide Market Stall (the wiki's known limit says it doesn't)");
			helper.setBlock(hallB, ModBlocks.VILLAGE_HALL);
			helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, hallB, StarterBlueprints.MARKET_STALL) == State.UNLOCKED,
				"a hall used for the first time didn't take in the server-wide Market Stall");
			helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, hallB, StarterBlueprints.MARKET_STALL_2) == State.LOCKED, "the new hall has Market Stall II too");
			helper.succeed();
		});
	}

	/**
	 * "Styles share their base's unlock; each upgrade tier is its own build": a found Market Stall in cherry used on the
	 * Table unlocks the plain Market Stall (and its other styles), and the chat line names the build; a found Stone House
	 * III unlocks neither Stone House II nor Stone House IV.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1107cStyleTier"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1107cStyleTier")
	public void aStyledBlueprintUnlocksItsBaseAndATierOnlyItself(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		BlockPos hallRel = QaBlueprintUnlocksGameTests.HALL;
		BlockPos tableRel = QaBlueprintUnlocksGameTests.TABLE;
		QaBlueprintUnlocksGameTests.village(helper, hallRel, tableRel);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(hallRel);
			BlockPos table = helper.absolutePos(tableRel);
			ResourceLocation cherry = BlueprintStyles.styled(StarterBlueprints.MARKET_STALL.id(), "cherry");
			ResourceLocation spruce = BlueprintStyles.styled(StarterBlueprints.MARKET_STALL.id(), "spruce");
			helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.MARKET_STALL.id()) == State.LOCKED, "the Market Stall isn't locked to begin with");
			helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.STONE_HOUSE_3.id()) == State.LOCKED, "Stone House III isn't locked to begin with");
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, tableRel);
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(cherry, StarterBlueprints.MARKET_STALL.size()));
			QaBlueprintUnlocksGameTests.clickTable(helper, p, tableRel);
			p.containerMenu = p.inventoryMenu;
			helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.MARKET_STALL.id()) == State.UNLOCKED, "the cherry Market Stall didn't unlock the plain one");
			helper.assertTrue(BlueprintUnlocks.state(level, hall, spruce) == State.UNLOCKED, "the cherry Market Stall didn't unlock its spruce style");
			Component line = QaStageFourGameTests.find(c.seen(), NEW_LINE);
			helper.assertTrue(line != null && line.getString().startsWith("The village's library has a new blueprint: "), "the unlock line: " + (line == null ? "none" : line.getString()));
			helper.assertTrue(line.getString().contains("Market Stall"), "the unlock line doesn't name the Market Stall: " + line.getString());
			p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
			helper.assertTrue(TableServer.take(p, table, StarterBlueprints.MARKET_STALL.id()), "the plain Market Stall was refused after its cherry style unlocked it");
			p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(StarterBlueprints.STONE_HOUSE_3.id(), StarterBlueprints.STONE_HOUSE_3.size()));
			QaBlueprintUnlocksGameTests.clickTable(helper, p, tableRel);
			p.containerMenu = p.inventoryMenu;
			helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.STONE_HOUSE_3.id()) == State.UNLOCKED, "the found Stone House III didn't unlock");
			helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.STONE_HOUSE_2.id()) == State.LOCKED, "Stone House III unlocked the tier below");
			helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.STONE_HOUSE_4.id()) == State.LOCKED, "Stone House III unlocked the tier above");
			helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.MARKET_STALL_2.id()) == State.LOCKED, "Market Stall II unlocked with its base");
			helper.succeed();
		});
	}

	/**
	 * "A Table between two villages uses the nearer hall", one block either way: a Table 5 blocks from hall A and 6 from
	 * hall B unlocks into A only; moved one block toward B (6 from A, 5 from B), it unlocks into B only.
	 */
	//$ gametest_ticks_batch HUGE '80' '"qa1107cNearerHall"'
	@GameTest(template = HUGE, timeoutTicks = 80, batch = "qa1107cNearerHall")
	public void aTableBetweenTwoVillagesUsesTheNearerHallOneBlockEitherWay(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		BlockPos hallA = new BlockPos(2, 2, 10);
		BlockPos hallB = new BlockPos(13, 2, 10);
		BlockPos nearA = new BlockPos(7, 2, 10);
		BlockPos nearB = new BlockPos(8, 2, 10);
		helper.setBlock(hallA, ModBlocks.VILLAGE_HALL);
		helper.setBlock(hallB, ModBlocks.VILLAGE_HALL);
		helper.setBlock(nearA, ModBlocks.BLUEPRINT_TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			helper.assertTrue(helper.absolutePos(hallA).equals(BlueprintUnlocks.village(level, helper.absolutePos(nearA))),
				"the Table 5 from A and 6 from B uses " + BlueprintUnlocks.village(level, helper.absolutePos(nearA)));
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, nearA);
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(StarterBlueprints.MARKET_STALL.id(), StarterBlueprints.MARKET_STALL.size()));
			QaBlueprintUnlocksGameTests.clickTable(helper, p, nearA);
			p.containerMenu = p.inventoryMenu;
			helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, hallA, StarterBlueprints.MARKET_STALL) == State.UNLOCKED, "the nearer village A didn't unlock the Market Stall");
			helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, hallB, StarterBlueprints.MARKET_STALL) == State.LOCKED, "the farther village B unlocked the Market Stall");
			helper.setBlock(nearA, net.minecraft.world.level.block.Blocks.AIR);
			helper.setBlock(nearB, ModBlocks.BLUEPRINT_TABLE);
			helper.runAfterDelay(3, () -> {
				VillageHalls.forgetNearest();
				helper.assertTrue(helper.absolutePos(hallB).equals(BlueprintUnlocks.village(level, helper.absolutePos(nearB))),
					"the Table 6 from A and 5 from B uses " + BlueprintUnlocks.village(level, helper.absolutePos(nearB)));
				BlockPos at = helper.absolutePos(nearB);
				p.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5);
				p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(StarterBlueprints.MARKET_STALL_2.id(), StarterBlueprints.MARKET_STALL_2.size()));
				QaBlueprintUnlocksGameTests.clickTable(helper, p, nearB);
				p.containerMenu = p.inventoryMenu;
				helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, hallB, StarterBlueprints.MARKET_STALL_2) == State.UNLOCKED, "the nearer village B didn't unlock Market Stall II");
				helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, hallA, StarterBlueprints.MARKET_STALL_2) == State.LOCKED, "the farther village A unlocked Market Stall II");
				helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, hallB, StarterBlueprints.MARKET_STALL) == State.LOCKED, "village B took A's Market Stall");
				helper.succeed();
			});
		});
	}

	/**
	 * With the switch off ("every build is unlocked and the Table lists exactly what it did before"): the Table's listing
	 * doesn't name a Wonder, and a Wonder Plan used on the Table unlocks nothing, says nothing and stays in the hand.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1107cWonderSwitchOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1107cWonderSwitchOff")
	public void withTheSwitchOffAWonderPlanOnTheTableUnlocksNothing(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		BlueprintUnlocks.ENABLED = false;
		BlockPos hallRel = QaBlueprintUnlocksGameTests.HALL;
		BlockPos tableRel = QaBlueprintUnlocksGameTests.TABLE;
		QaBlueprintUnlocksGameTests.village(helper, hallRel, tableRel);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(hallRel);
			Wonder folly = Wonders.loaded().get(FOLLY);
			helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
			ResourceLocation id = WonderBlueprints.idOf(folly.id());
			helper.assertTrue(TableServer.listing(level, hall).stream().noneMatch(e -> e.id().equals(id)), "with the switch off the Table lists the folly");
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, tableRel);
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.MAIN_HAND, WonderPlanItem.of(level, hall, folly, Component.literal("Testholm")));
			c.sent();
			QaBlueprintUnlocksGameTests.clickTable(helper, p, tableRel);
			p.containerMenu = p.inventoryMenu;
			helper.assertTrue(QaBlueprintUnlocksGameTests.linesOf(c.seen(), NEW_LINE) == 0, "with the switch off the plan said 'new blueprint'");
			helper.assertTrue(WonderPlanItem.plan(p.getMainHandItem()).isPresent(), "the Table took the Wonder Plan");
			TablePayloads.Entry shown = entry(c.sent(TablePayloads.Open.class), id);
			helper.assertTrue(shown == null, "with the switch off the screen that opened lists the folly: " + shown);
			helper.succeed();
		});
	}
}
