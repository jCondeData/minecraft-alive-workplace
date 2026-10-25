package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks.State;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-b-1025-0038) for ROADMAP 26.10, "The Blueprint Table is a collection", written from its spec, not the code:
 * a found Blueprint used on the Table unlocks its build, says so in chat once and shows in the screen that opens; each village keeps
 * its own library; a styled id of a locked or "???" build asked for over the network is refused like the plain one;
 * {@code /workplace unlock} answers for a build already unlocked and for an unknown id; an unlocked build of a closed
 * expansion still isn't offered ("expansion gates still apply"); a builder's level-up tells the players near and, with
 * nothing left locked, unlocks nothing. Each test is alone in its batch, with its own hall(s) and a small village radius.
 */
public class QaBlueprintUnlocksGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos HALL = new BlockPos(1, 2, 1);
	static final BlockPos TABLE = new BlockPos(5, 2, 5);
	static final String NEW_LINE = "message.aliveworkplace.blueprint_unlocks.new";

	/** The switch on and the village radius {@code radius}, both put back after the test; nothing left from earlier batches. */
	static void setUp(GameTestHelper helper, int radius) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		boolean was = BlueprintUnlocks.ENABLED;
		int old = VillageHalls.RADIUS;
		BlueprintUnlocks.ENABLED = true;
		VillageHalls.RADIUS = radius;
		Leftovers.after(helper, () -> {
			BlueprintUnlocks.ENABLED = was;
			VillageHalls.RADIUS = old;
			VillageHalls.forgetNearest();
		});
	}

	static void village(GameTestHelper helper, BlockPos hall, BlockPos table) {
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
	}

	static State state(GameTestHelper helper, BlockPos hall, StarterBlueprints.Entry entry) {
		return BlueprintUnlocks.state(helper.getLevel(), helper.absolutePos(hall), entry.id());
	}

	/** A survival player with chat and payloads caught, standing by {@code table}. */
	static QaStageFiveGameTests.Client client(GameTestHelper helper, BlockPos table) {
		QaStageFiveGameTests.Client c = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(table);
		c.player().moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 90f, 0f);
		return c;
	}

	/** A right-click on {@code table} as the client sends it: the main hand, then the off hand if nothing took the click. */
	static void clickTable(GameTestHelper helper, ServerPlayer p, BlockPos table) {
		BlockPos at = helper.absolutePos(table);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
		for (InteractionHand hand : InteractionHand.values()) {
			if (p.gameMode.useItemOn(p, helper.getLevel(), p.getItemInHand(hand), hand, hit).consumesAction()) {
				return;
			}
		}
	}

	/**
	 * How many times {@code key} was shown. The caught player sees a chat line twice (its displayClientMessage passes it
	 * on to sendSystemMessage, both caught), the same component both times, so a line is counted once per component.
	 */
	static long linesOf(List<Component> seen, String key) {
		java.util.Set<Component> once = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		return seen.stream().filter(c -> key.equals(QaStageFiveGameTests.key(c)) && once.add(c)).count();
	}

	/**
	 * From the main hand: the first use unlocks the build and says so once, and the Table screen that opens lists it
	 * unlocked; the same blueprint used again opens the Table with no second "new blueprint" line.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaUnlockTwice"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaUnlockTwice")
	public void aFoundBlueprintUsedTwiceSaysSoOnce(GameTestHelper helper) {
		setUp(helper, 16);
		village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			QaStageFiveGameTests.Client c = client(helper, TABLE);
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(StarterBlueprints.FOUNTAIN.id(), StarterBlueprints.FOUNTAIN.size()));
			helper.assertTrue(state(helper, HALL, StarterBlueprints.FOUNTAIN) == State.LOCKED, "the Fountain isn't locked to begin with");
			c.sent();
			clickTable(helper, p, TABLE);
			helper.assertTrue(state(helper, HALL, StarterBlueprints.FOUNTAIN) == State.UNLOCKED, "the found Fountain didn't unlock it");
			helper.assertTrue(linesOf(c.seen(), NEW_LINE) == 1, "the first use said 'new blueprint' " + linesOf(c.seen(), NEW_LINE) + " times");
			List<TablePayloads.Open> opened = c.sent(TablePayloads.Open.class);
			helper.assertTrue(opened.size() == 1, "the Table screen opened " + opened.size() + " times");
			TablePayloads.Entry fountain = opened.get(0).entries().stream().filter(e -> e.id().equals(StarterBlueprints.FOUNTAIN.id())).findFirst().orElse(null);
			helper.assertTrue(fountain != null && fountain.unlocked(), "the Table screen shows the Fountain as " + fountain);
			p.containerMenu = p.inventoryMenu;
			clickTable(helper, p, TABLE);
			helper.assertTrue(linesOf(c.seen(), NEW_LINE) == 1, "the second use said 'new blueprint' again: " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(c.sent(TablePayloads.Open.class).size() == 1, "the second use didn't open the Table");
			helper.assertTrue(p.getMainHandItem().is(ModItems.BLUEPRINT), "the Table took the blueprint");
			helper.succeed();
		});
	}

	/** Each village keeps its own library: a build unlocked at one village's Table stays locked at the other's. */
	//$ gametest_ticks_batch HUGE '60' '"qaUnlockTwoVillages"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaUnlockTwoVillages")
	public void aBuildUnlockedInOneVillageStaysLockedInTheOther(GameTestHelper helper) {
		setUp(helper, 10);
		BlockPos hallA = new BlockPos(1, 2, 1);
		BlockPos tableA = new BlockPos(4, 2, 4);
		BlockPos hallB = new BlockPos(28, 2, 28);
		BlockPos tableB = new BlockPos(25, 2, 25);
		village(helper, hallA, tableA);
		village(helper, hallB, tableB);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			helper.assertTrue(helper.absolutePos(hallA).equals(BlueprintUnlocks.village(level, helper.absolutePos(tableA))), "Table A isn't in village A");
			helper.assertTrue(helper.absolutePos(hallB).equals(BlueprintUnlocks.village(level, helper.absolutePos(tableB))), "Table B isn't in village B");
			QaStageFiveGameTests.Client c = client(helper, tableA);
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(StarterBlueprints.MARKET_STALL.id(), StarterBlueprints.MARKET_STALL.size()));
			clickTable(helper, p, tableA);
			p.containerMenu = p.inventoryMenu;
			helper.assertTrue(state(helper, hallA, StarterBlueprints.MARKET_STALL) == State.UNLOCKED, "village A didn't unlock the Market Stall");
			helper.assertTrue(state(helper, hallB, StarterBlueprints.MARKET_STALL) == State.LOCKED, "village B's Market Stall unlocked too");
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
			BlockPos b = helper.absolutePos(tableB);
			p.moveTo(b.getX() + 1.5, b.getY(), b.getZ() + 0.5);
			helper.assertFalse(TableServer.take(p, b, StarterBlueprints.MARKET_STALL.id()), "village B's Table handed out the Market Stall");
			helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 2, "village B's refusal used a Blank Blueprint");
			BlockPos a = helper.absolutePos(tableA);
			p.moveTo(a.getX() + 1.5, a.getY(), a.getZ() + 0.5);
			helper.assertTrue(TableServer.take(p, a, StarterBlueprints.MARKET_STALL.id()), "village A's Table refused its unlocked Market Stall");
			helper.succeed();
		});
	}

	/**
	 * Get Blueprint over the network with a styled id (26.11's "a styled id gives it set to that style"): a locked build
	 * in a style and a "???" build in a style are refused with no Blank Blueprint used, as their plain ids are; once the
	 * build unlocks, the same styled id is handed out.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaUnlockStyled"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaUnlockStyled")
	public void aStyledIdOfALockedBuildIsRefused(GameTestHelper helper) {
		setUp(helper, 16);
		village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos table = helper.absolutePos(TABLE);
			QaStageFiveGameTests.Client c = client(helper, TABLE);
			ServerPlayer p = c.player();
			p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 4));
			ResourceLocation stall = BlueprintStyles.styled(StarterBlueprints.MARKET_STALL.id(), "cherry");
			ResourceLocation manor = BlueprintStyles.styled(StarterBlueprints.MANOR.id(), "cherry");
			helper.assertTrue(state(helper, HALL, StarterBlueprints.MARKET_STALL) == State.LOCKED, "the Market Stall isn't locked to begin with");
			c.send(new TablePayloads.Take(table, stall));
			c.send(new TablePayloads.Take(table, manor));
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 4 && p.getInventory().countItem(ModItems.BLUEPRINT) == 0,
					"a styled id of a locked build was handed out: " + p.getInventory().countItem(ModItems.BLUEPRINT) + " blueprints");
				helper.assertTrue(BlueprintUnlocks.unlock(level, helper.absolutePos(HALL), StarterBlueprints.MARKET_STALL.id(), List.of()), "the Market Stall wasn't locked");
				c.send(new TablePayloads.Take(table, stall));
				helper.runAfterDelay(3, () -> {
					helper.assertTrue(p.getInventory().countItem(ModItems.BLUEPRINT) == 1 && p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 3,
						"the unlocked Market Stall in cherry wasn't handed out (positive control)");
					helper.succeed();
				});
			});
		});
	}

	/**
	 * {@code /workplace unlock} (the questbook's reward): the first run unlocks the build and tells the player; a second
	 * run unlocks nothing and tells nobody anything new; an unknown id unlocks nothing and fails.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaUnlockCommand"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaUnlockCommand")
	public void theUnlockCommandAnswersForAnAlreadyUnlockedBuildAndAnUnknownId(GameTestHelper helper) {
		setUp(helper, 16);
		village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			QaStageFiveGameTests.Client c = client(helper, TABLE);
			String name = c.player().getGameProfile().getName();
			var dispatcher = level.getServer().getCommands().getDispatcher();
			var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
			int first;
			int again;
			int unknown;
			try {
				first = dispatcher.execute("workplace unlock " + name + " " + StarterBlueprints.TERRACE_2.id(), source);
				again = dispatcher.execute("workplace unlock " + name + " " + StarterBlueprints.TERRACE_2.id(), source);
				unknown = dispatcher.execute("workplace unlock " + name + " aliveworkplace:no_such_build_qa", source);
			} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
				throw new net.minecraft.gametest.framework.GameTestAssertException("the command didn't parse: " + e.getMessage());
			}
			helper.assertTrue(first == 1, "the first unlock returned " + first);
			helper.assertTrue(state(helper, HALL, StarterBlueprints.TERRACE_2) == State.UNLOCKED, "Terrace II didn't unlock");
			helper.assertTrue(again == 0, "a second unlock of Terrace II returned " + again);
			helper.assertTrue(unknown == 0, "an unknown id returned " + unknown);
			helper.assertTrue(linesOf(c.seen(), NEW_LINE) == 1, "the player was told 'new blueprint' " + linesOf(c.seen(), NEW_LINE) + " times");
			QaStageFourGameTests.assertRendered(helper, QaStageFourGameTests.find(c.seen(), NEW_LINE), "Terrace II");
			long unlocked = BlueprintUnlocks.unlocked(level, helper.absolutePos(HALL)).stream().filter(id -> id.getPath().contains("no_such_build")).count();
			helper.assertTrue(unlocked == 0, "the unknown id went into the library");
			helper.succeed();
		});
	}

	/**
	 * "Expansion gates still apply": a closed expansion's build unlocked in the village (here M28's Gem Grotto) is still
	 * neither listed nor handed out while its gate is closed, and is offered, unlocked, once the gate opens.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaUnlockGate"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaUnlockGate")
	public void anUnlockedBuildOfAClosedExpansionStaysOffTheTable(GameTestHelper helper) {
		setUp(helper, 16);
		village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos table = helper.absolutePos(TABLE);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
			player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
			boolean[] listed = new boolean[1];
			boolean[] took = new boolean[1];
			try {
				ExpansionGateGameTests.asPlayer(() -> {
					BlueprintUnlocks.unlock(level, hall, StarterBlueprints.GEM_GROTTO.id(), List.of());
					listed[0] = TableServer.listing(level, hall).stream().anyMatch(e -> e.id().equals(StarterBlueprints.GEM_GROTTO.id()));
					took[0] = TableServer.take(player, table, StarterBlueprints.GEM_GROTTO.id());
				});
				helper.assertTrue(state(helper, HALL, StarterBlueprints.GEM_GROTTO) == State.UNLOCKED, "the Gem Grotto didn't unlock");
				helper.assertFalse(listed[0], "the Table lists the unlocked Gem Grotto with M28 closed");
				helper.assertFalse(took[0], "the Table handed out the unlocked Gem Grotto with M28 closed");
				helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 2, "the refusal used a Blank Blueprint");
				TablePayloads.Entry open = TableServer.listing(level, hall).stream().filter(e -> e.id().equals(StarterBlueprints.GEM_GROTTO.id())).findFirst().orElse(null);
				helper.assertTrue(open != null && open.unlocked(), "with M28 open the Gem Grotto is " + open);
			} finally {
				level.getServer().getPlayerList().remove(player);
			}
			helper.succeed();
		});
	}

	/**
	 * A builder's level-up tells the players in the village about each build it unlocks; with nothing locked left, the
	 * next level-up unlocks nothing and says nothing (and doesn't throw).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaUnlockLevelUp"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaUnlockLevelUp")
	public void aLevelUpTellsThePlayersNearAndRunsOutQuietly(GameTestHelper helper) {
		setUp(helper, 16);
		village(helper, HALL, TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			QaStageFiveGameTests.Client c = client(helper, TABLE);
			List<ResourceLocation> next = BlueprintUnlocks.nextForLevelUp(level, hall);
			helper.assertTrue(next.size() == BlueprintUnlocks.PER_LEVEL, "a level-up would unlock " + next);
			builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
			io.github.jcondedata.aliveworkplace.build.BuilderLevels.addXp(level, builder, 30, null);
			helper.assertTrue(builder.getVillagerData().getLevel() == 2, "the builder didn't level up");
			helper.assertTrue(linesOf(c.seen(), NEW_LINE) == next.size(),
				"the player near was told about " + linesOf(c.seen(), NEW_LINE) + " of " + next.size() + " new builds");
			for (ResourceLocation id : BlueprintLibrary.list(level.getServer(), false)) {
				if (BlueprintUnlocks.state(level, hall, id) == State.LOCKED) {
					BlueprintUnlocks.unlock(level, hall, id, List.of());
				}
			}
			helper.assertTrue(BlueprintUnlocks.nextForLevelUp(level, hall).isEmpty(), "something is still locked");
			int before = c.seen().size();
			builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(2));
			io.github.jcondedata.aliveworkplace.build.BuilderLevels.addXp(level, builder, 70, null);
			helper.assertTrue(builder.getVillagerData().getLevel() == 3, "the builder didn't level up again: " + builder.getVillagerData().getLevel());
			long more = linesOf(c.seen().subList(before, c.seen().size()), NEW_LINE);
			helper.assertTrue(more == 0, "a level-up with nothing locked said 'new blueprint' " + more + " times");
			helper.assertTrue(state(helper, HALL, StarterBlueprints.MANOR) == State.HIDDEN, "a level-up unlocked the rare Manor");
			helper.succeed();
		});
	}
}
