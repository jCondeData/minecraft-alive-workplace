package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (independent tester) for ROADMAP 36.21, "Bring an old house up to its latest look", written from the item's Done
 * when through the Village Hall's Repairs page buttons (the same server handler a click reaches). Covers what the
 * builder's RefreshGameTests don't: the exact preview counts by kind and every sentence a player reads, the NEAREST of
 * two free Builders taking it, a player's block on a spot the new drawing fills, exact item accounting of the leftovers
 * (none duplicated or lost, also across a save and reload), two open hall screens both pressing the button, a player
 * who doesn't own the build pressing it, the builderRepairs switch off with a refresh waiting (taken up by the real
 * tick loop), a leftover broken by a player mid-refresh, and the Builder dying mid-refresh.
 */
public class QaRefreshOldHouseGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	/** The build: a row 7 long, 2 tall, 2 deep. */
	private static final BlockPos ROW = new BlockPos(4, 2, 7);
	private static final BlockPos STORE = new BlockPos(2, 2, 4);
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos BENCH_2 = new BlockPos(12, 2, 2);

	private record Stage(ServerPlayer owner, BlueprintData.Placement placement, ResourceLocation id, Container store, ServerLevel level) {
	}

	private static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState();
	private static final BlockState TORCH = Blocks.TORCH.defaultBlockState();

	/**
	 * The older drawing: planks with no torch at the near end, stone bricks with a torch on them, cobblestone, a chest,
	 * and nothing at the far end. The latest drawing: planks with a torch at both ends, nothing between.
	 */
	private static List<Blueprint.Entry> older() {
		List<Blueprint.Entry> blocks = new ArrayList<>();
		blocks.add(new Blueprint.Entry(new BlockPos(0, 0, 0), PLANKS, null));
		blocks.add(new Blueprint.Entry(new BlockPos(1, 0, 0), Blocks.STONE_BRICKS.defaultBlockState(), null));
		blocks.add(new Blueprint.Entry(new BlockPos(1, 1, 0), TORCH, null));
		blocks.add(new Blueprint.Entry(new BlockPos(2, 0, 0), Blocks.COBBLESTONE.defaultBlockState(), null));
		blocks.add(new Blueprint.Entry(new BlockPos(3, 0, 0), Blocks.CHEST.defaultBlockState(), null));
		return blocks;
	}

	private static void put(GameTestHelper helper, BlockPos rel, BlockState state) {
		helper.getLevel().setBlock(helper.absolutePos(rel), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	/**
	 * The hall, the owner's build standing as the older drawing drew it (7 emeralds in its chest), a store with 4 planks
	 * and 4 torches next to the bench ({@code stocked}), and the finished record. Builders are added by the tests.
	 */
	private static Stage stage(GameTestHelper helper, boolean stocked) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (int x = 0; x < 7; x++) {
			for (int y = 1; y >= 0; y--) {
				for (int z = 0; z < 2; z++) {
					put(helper, ROW.offset(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		// the latest drawing, saved as a structure as a blueprint is
		put(helper, ROW, PLANKS);
		put(helper, ROW.offset(0, 1, 0), TORCH);
		put(helper, ROW.offset(6, 0, 0), PLANKS);
		put(helper, ROW.offset(6, 1, 0), TORCH);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_refresh_" + Long.toHexString(level.getGameTime())
			+ "_" + helper.absolutePos(ROW).getX() + "_" + helper.absolutePos(ROW).getZ());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(7, 2, 2), false, Blocks.STRUCTURE_VOID);
		Refreshes.addHistory(id, "older", new Blueprint(id, new Vec3i(7, 2, 2), older()));
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Refreshes.removeHistory(id);
			level.getServer().getPlayerList().remove(owner);
		});
		// now built as the older drawing drew it
		for (int x = 0; x < 7; x++) {
			put(helper, ROW.offset(x, 1, 0), Blocks.AIR.defaultBlockState());
			put(helper, ROW.offset(x, 0, 0), Blocks.AIR.defaultBlockState());
		}
		for (Blueprint.Entry e : older()) {
			put(helper, ROW.offset(e.pos()), e.state());
		}
		Container inside = helper.getBlockEntity(ROW.offset(3, 0, 0));
		inside.setItem(0, new ItemStack(Items.EMERALD, 7));
		helper.setBlock(STORE, Blocks.CHEST);
		Container store = helper.getBlockEntity(STORE);
		if (stocked) {
			store.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
			store.setItem(1, new ItemStack(Items.TORCH, 4));
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, owner.getUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return new Stage(owner, placement, id, store, level);
	}

	private static Villager builder(GameTestHelper helper, ServerPlayer owner, BlockPos at, BlockPos bench) {
		Villager builder = helper.spawn(EntityType.VILLAGER, at);
		Builders.employ(helper.getLevel(), builder, helper.absolutePos(bench));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		return builder;
	}

	private static ChoiceMenu openPage(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		menu.press(HallPages.slot(RepairsPage.PAGE), player);
		helper.assertTrue(menu.icon(4).is(Items.ANVIL), "not on the Repairs page: " + menu.icon(4));
		return menu;
	}

	private static int refreshSlot(ChoiceMenu menu) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).is(Items.PAINTING) || menu.icon(slot).is(Items.SCAFFOLDING)) {
				return slot;
			}
		}
		return -1;
	}

	private static List<String> lore(ItemStack stack) {
		List<String> out = new ArrayList<>();
		for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
			out.add(line.getString());
		}
		return out;
	}

	/** First click on the build's entry, the view that shows what changes. */
	private static ChoiceMenu openView(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = openPage(helper, player);
		int slot = refreshSlot(menu);
		helper.assertTrue(slot >= 0, "no build to refresh on the Repairs page");
		menu.press(slot, player);
		return menu;
	}

	private static void pressRefresh(GameTestHelper helper, ChoiceMenu menu, ServerPlayer player) {
		helper.assertTrue(menu.icon(RepairsPage.REFRESH).is(Items.BRUSH), "no Refresh button: " + menu.icon(RepairsPage.REFRESH));
		menu.press(RepairsPage.REFRESH, player);
	}

	private static long sites(Stage s) {
		return BuildSiteManager.get(s.level()).all().stream().filter(site -> site.placement().equals(s.placement())).count();
	}

	/** Every one of {@code item} the owner has: in the store, in the build's chest and in the builder's bag. */
	private static int count(GameTestHelper helper, Stage s, Item item, Villager builder) {
		Container inside = helper.getBlockEntity(ROW.offset(3, 0, 0));
		BuilderBag bag = builder != null ? ModAttachments.BUILDER_BAG.get(builder) : null;
		return s.store().countItem(item) + inside.countItem(item) + (bag != null ? bag.count(item) : 0);
	}

	/** The refresh is done: the latest drawing stands where it is the builder's, and nothing's left to do. */
	private static void assertRefreshed(GameTestHelper helper, Stage s) {
		helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) == null && sites(s) == 0, "still refreshing");
		helper.assertBlockPresent(Blocks.OAK_PLANKS, ROW);
		helper.assertBlockPresent(Blocks.TORCH, ROW.offset(0, 1, 0));
		helper.assertBlockPresent(Blocks.AIR, ROW.offset(1, 0, 0));
		helper.assertBlockPresent(Blocks.AIR, ROW.offset(1, 1, 0));
		helper.assertBlockPresent(Blocks.AIR, ROW.offset(2, 0, 0));
		helper.assertBlockPresent(Blocks.TORCH, ROW.offset(6, 1, 0));
	}

	private static void assertChestKept(GameTestHelper helper) {
		helper.assertBlockPresent(Blocks.CHEST, ROW.offset(3, 0, 0));
		Container inside = helper.getBlockEntity(ROW.offset(3, 0, 0));
		helper.assertTrue(inside.countItem(Items.EMERALD) == 7, "the build's chest lost its emeralds: " + inside.countItem(Items.EMERALD));
	}

	private static void assertCount(GameTestHelper helper, Stage s, Item item, Villager builder, int wanted) {
		int n = count(helper, s, item, builder);
		helper.assertTrue(n == wanted, item + ": " + n + ", wanted " + wanted);
	}

	// ---- the page and its words -----------------------------------------------------------------------------------

	/**
	 * The page's entry and the build's view say exactly what changes, by kind ("New: 2 decorations, 1 block", "Taken
	 * away: 1 decoration, 2 blocks": the chest the older drawing had isn't counted, it stays), and every sentence of the
	 * feature reads as written with its real arguments, in American English.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_refresh_words"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_refresh_words")
	public void thePreviewCountsEachKindExactlyAndEverySentenceReads(GameTestHelper helper) {
		Stage s = stage(helper, true);
		builder(helper, s.owner(), new BlockPos(3, 2, 3), BENCH);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPage(helper, s.owner());
			int slot = refreshSlot(menu);
			helper.assertTrue(slot >= 0, "the old build has no Refresh entry");
			List<String> entry = lore(menu.icon(slot));
			helper.assertTrue(entry.contains("Its blueprint has changed since it was built") && entry.contains("New: 2 decorations, 1 block")
				&& entry.contains("Taken away: 1 decoration, 2 blocks") && entry.contains("Click to see what changes"), "the entry's lines: " + entry);
			menu.press(slot, s.owner());
			List<String> view = lore(menu.icon(4));
			helper.assertTrue(view.contains("New: 2 decorations, 1 block") && view.contains("Taken away: 1 decoration, 2 blocks")
				&& !view.contains("Click to see what changes"), "the view's lines: " + view);
			ItemStack button = menu.icon(RepairsPage.REFRESH);
			helper.assertTrue(button.getHoverName().getString().equals("Refresh to the latest look"), "the button: " + button.getHoverName().getString());
			helper.assertTrue(lore(button).equals(List.of("The nearest free Builder takes away what the old drawing left and puts in the new pieces",
				"Chests and what's in them, beds villagers sleep in, job blocks and your own blocks stay")), "the button's lines: " + lore(button));
			// counts of every kind, one and many
			helper.assertTrue("12 decorations, 3 furniture pieces".equals(Refreshes.counts(new int[] {12, 3, 0}).getString()),
				"many: " + Refreshes.counts(new int[] {12, 3, 0}).getString());
			helper.assertTrue("1 decoration, 1 furniture piece, 1 block".equals(Refreshes.counts(new int[] {1, 1, 1}).getString()),
				"one each: " + Refreshes.counts(new int[] {1, 1, 1}).getString());
			helper.assertTrue(Refreshes.counts(new int[] {0, 0, 0}) == null, "nothing to count says something");
			// the chat and action-bar lines, with the Stone House and a builder's name
			Component house = Blueprints.displayName(AliveWorkplace.id("stone_house"));
			helper.assertTrue("Stone House".equals(house.getString()), "the Stone House's name: " + house.getString());
			Component ada = Component.literal("Ada");
			String[][] lines = {
				{Component.translatable("message.aliveworkplace.refreshing", ada, house).getString(), "Ada is bringing the Stone House up to its latest look"},
				{Component.translatable("message.aliveworkplace.refresh_answer.started", house).getString(), "A Builder is bringing the Stone House up to its latest look"},
				{Component.translatable("message.aliveworkplace.refresh_answer.waiting", house).getString(), "No Builder is free: the Stone House is refreshed as soon as one is"},
				{Component.translatable("message.aliveworkplace.refresh_answer.nothing", house).getString(), "The Stone House already has its latest look"},
				{Component.translatable("screen.aliveworkplace.refresh.going", ada, 40).getString(), "Ada is bringing it up to its latest look: 40%"},
				{Component.translatable("screen.aliveworkplace.refresh.waiting").getString(), "Refresh asked for: waiting for a free Builder"},
			};
			for (String[] line : lines) {
				helper.assertTrue(line[0].equals(line[1]), "reads \"" + line[0] + "\", wanted \"" + line[1] + "\"");
				helper.assertFalse(line[0].matches("(?i).*(colour|grey|armour|jeweller|cancelled|defence).*"), "British spelling: " + line[0]);
			}
			helper.succeed();
		});
	}

	// ---- who does it ----------------------------------------------------------------------------------------------

	/** Two free Builders who look after the build: the nearer one gets it (spawned second, so it isn't just the first found). */
	//$ gametest_ticks_batch AREA '200' '"qa_refresh_nearest"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_refresh_nearest")
	public void theSecondClickHandsItToTheNearestFreeBuilder(GameTestHelper helper) {
		Stage s = stage(helper, true);
		helper.setBlock(BENCH_2, ModBlocks.BUILDERS_BENCH);
		Villager far = builder(helper, s.owner(), new BlockPos(15, 2, 2), BENCH_2);
		Villager near = builder(helper, s.owner(), new BlockPos(5, 2, 10), BENCH);
		helper.runAfterDelay(2, () -> {
			double dNear = near.distanceToSqr(helper.absolutePos(ROW).getCenter());
			double dFar = far.distanceToSqr(helper.absolutePos(ROW).getCenter());
			helper.assertTrue(dNear < dFar, "the builders moved: near " + dNear + ", far " + dFar);
			pressRefresh(helper, openView(helper, s.owner()), s.owner());
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			helper.assertTrue(site != null, "the button started nothing with two free builders");
			helper.assertTrue(near.getUUID().equals(site.builder()), "the farther builder got it");
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(far), "the farther builder was set to work too");
			helper.succeed();
		});
	}

	/** Two hall screens open on the same build, both press the button: one refresh, one builder, the other stays free. */
	//$ gametest_ticks_batch AREA '200' '"qa_refresh_twice"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_refresh_twice")
	public void pressingTheButtonTwiceStartsOneRefresh(GameTestHelper helper) {
		Stage s = stage(helper, true);
		helper.setBlock(BENCH_2, ModBlocks.BUILDERS_BENCH);
		Villager first = builder(helper, s.owner(), new BlockPos(5, 2, 10), BENCH);
		Villager second = builder(helper, s.owner(), new BlockPos(13, 2, 3), BENCH_2);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu a = openView(helper, s.owner());
			ChoiceMenu b = openView(helper, s.owner());
			pressRefresh(helper, a, s.owner());
			pressRefresh(helper, b, s.owner());
			helper.assertTrue(sites(s) == 1, "two clicks made " + sites(s) + " sites on one build");
			helper.assertTrue(ModAttachments.BUILDER_JOB.has(first) ^ ModAttachments.BUILDER_JOB.has(second),
				"both or neither builder set to it: " + ModAttachments.BUILDER_JOB.has(first) + " " + ModAttachments.BUILDER_JOB.has(second));
			helper.assertFalse(BuildSiteManager.get(s.level()).refreshAsked(s.placement()), "left waiting as well as started");
			// a third press, from the page's view after it started, has no button: it's handed over
			ChoiceMenu c = openPage(helper, s.owner());
			c.press(refreshSlot(c), s.owner());
			helper.assertFalse(c.icon(RepairsPage.REFRESH).is(Items.BRUSH), "the button is offered again while it's being refreshed");
			helper.succeed();
		});
	}

	/** Someone who doesn't own the build presses the button: nothing starts, nothing waits. */
	//$ gametest_ticks_batch AREA '200' '"qa_refresh_stranger"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_refresh_stranger")
	public void aPlayerWhoDoesntOwnTheBuildCantRefreshIt(GameTestHelper helper) {
		Stage s = stage(helper, true);
		Villager builder = builder(helper, s.owner(), new BlockPos(5, 2, 10), BENCH);
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> s.level().getServer().getPlayerList().remove(stranger));
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPage(helper, stranger);
			int slot = refreshSlot(menu);
			if (slot >= 0) {
				menu.press(slot, stranger);
				if (menu.icon(RepairsPage.REFRESH).is(Items.BRUSH)) {
					menu.press(RepairsPage.REFRESH, stranger);
				}
			}
			helper.assertTrue(sites(s) == 0, "a stranger's click started a refresh");
			helper.assertFalse(BuildSiteManager.get(s.level()).refreshAsked(s.placement()), "a stranger's click left a refresh waiting");
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder), "the builder was set to work by a stranger");
			helper.assertBlockPresent(Blocks.STONE_BRICKS, ROW.offset(1, 0, 0));
			helper.succeed();
		});
	}

	// ---- what comes away, what stays --------------------------------------------------------------------------------

	/**
	 * The whole refresh: the leftovers' items reach the store exactly once (1 stone bricks, 1 cobblestone, the torch back),
	 * the new pieces take exactly what they need (1 planks, 2 torches), the chest and its emeralds stay, and the button's
	 * gone afterwards.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_refresh_items"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_refresh_items")
	public void theLeftoversItemsReachTheStoreExactlyOnce(GameTestHelper helper) {
		Stage s = stage(helper, true);
		Villager builder = builder(helper, s.owner(), new BlockPos(3, 2, 3), BENCH);
		helper.runAfterDelay(2, () -> pressRefresh(helper, openView(helper, s.owner()), s.owner()));
		helper.succeedWhen(() -> {
			assertRefreshed(helper, s);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder), "still at it");
			assertChestKept(helper);
			assertCount(helper, s, Items.STONE_BRICKS, builder, 1);
			assertCount(helper, s, Items.COBBLESTONE, builder, 1);
			assertCount(helper, s, Items.TORCH, builder, 4 + 1 - 2);
			assertCount(helper, s, Items.OAK_PLANKS, builder, 4 - 1);
			helper.assertTrue(refreshSlot(openPage(helper, s.owner())) < 0, "the button's still there once refreshed");
		});
	}

	/** A player's own block on a spot the new drawing fills (the far end): the builder leaves it; the rest is refreshed. */
	//$ gametest_ticks_batch AREA '2400' '"qa_refresh_players_spot"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_refresh_players_spot")
	public void aPlayersBlockWhereANewPieceGoesStays(GameTestHelper helper) {
		Stage s = stage(helper, true);
		helper.setBlock(ROW.offset(6, 0, 0), Blocks.GLOWSTONE); // the owner's own block, where the latest drawing has planks
		Villager builder = builder(helper, s.owner(), new BlockPos(3, 2, 3), BENCH);
		boolean[] started = {false};
		helper.runAfterDelay(2, () -> {
			pressRefresh(helper, openView(helper, s.owner()), s.owner());
			started[0] = Refreshes.siteOf(s.level(), s.placement()) != null;
			helper.assertTrue(started[0], "the refresh didn't start");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(started[0], "not started");
			helper.assertTrue(sites(s) == 0, "still refreshing");
			helper.assertBlockPresent(Blocks.GLOWSTONE, ROW.offset(6, 0, 0));
			helper.assertBlockPresent(Blocks.AIR, ROW.offset(1, 0, 0));
			helper.assertBlockPresent(Blocks.AIR, ROW.offset(2, 0, 0));
			helper.assertBlockPresent(Blocks.TORCH, ROW.offset(0, 1, 0));
			assertChestKept(helper);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder), "still at it");
		});
	}

	/**
	 * A player breaks one of the leftovers (the cobblestone, items kept by the player) right after the click: the
	 * refresh still finishes, and no cobblestone appears in the store from a block the builder never took.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_refresh_broken"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_refresh_broken")
	public void aLeftoverBrokenByAPlayerMidRefreshIsNotPaidTwice(GameTestHelper helper) {
		Stage s = stage(helper, true);
		Villager builder = builder(helper, s.owner(), new BlockPos(3, 2, 3), BENCH);
		helper.runAfterDelay(2, () -> {
			pressRefresh(helper, openView(helper, s.owner()), s.owner());
			helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) != null, "the refresh didn't start");
			s.level().destroyBlock(helper.absolutePos(ROW.offset(2, 0, 0)), false, s.owner());
		});
		helper.succeedWhen(() -> {
			assertRefreshed(helper, s);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder), "still at it");
			assertChestKept(helper);
			assertCount(helper, s, Items.COBBLESTONE, builder, 0);
			assertCount(helper, s, Items.STONE_BRICKS, builder, 1);
		});
	}

	// ---- waiting, switches, saving, dying ----------------------------------------------------------------------------

	/**
	 * With builderRepairs off and no builder free, the owner's click still waits; a builder employed later takes it up
	 * on its own rounds (the real tick loop, no direct call) and does it: the owner asked, the switch is about repairs
	 * the builders find themselves.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa_refresh_switch_off"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_refresh_switch_off")
	public void aWaitingRefreshIsTakenUpWithRepairsSwitchedOff(GameTestHelper helper) {
		Stage s = stage(helper, true);
		boolean enabled = Upkeep.ENABLED;
		Upkeep.ENABLED = false;
		Leftovers.after(helper, () -> Upkeep.ENABLED = enabled);
		Villager[] builder = {null};
		helper.runAfterDelay(2, () -> {
			pressRefresh(helper, openView(helper, s.owner()), s.owner());
			helper.assertTrue(sites(s) == 0 && BuildSiteManager.get(s.level()).refreshAsked(s.placement()), "not waiting with no builder");
			builder[0] = builder(helper, s.owner(), new BlockPos(3, 2, 3), BENCH);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(builder[0] != null, "no builder yet");
			helper.assertFalse(BuildSiteManager.get(s.level()).refreshAsked(s.placement()), "never taken up");
			assertRefreshed(helper, s);
			assertChestKept(helper);
		});
	}

	/**
	 * Saved and loaded mid-way (the sites and the builder as after a restart) once the torch on the stone bricks is
	 * gone: the refresh finishes, and every leftover's item is in the store exactly once (none lost, none twice).
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_refresh_reload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_refresh_reload")
	public void aReloadMidRefreshNeitherLosesNorDuplicatesItems(GameTestHelper helper) {
		Stage s = stage(helper, true);
		Villager[] builder = {builder(helper, s.owner(), new BlockPos(3, 2, 3), BENCH)};
		boolean[] reloaded = {false};
		helper.runAfterDelay(2, () -> pressRefresh(helper, openView(helper, s.owner()), s.owner()));
		helper.onEachTick(() -> {
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			if (reloaded[0] || site == null || !s.level().getBlockState(helper.absolutePos(ROW.offset(1, 1, 0))).isAir()) {
				return;
			}
			reloaded[0] = true;
			BuildSiteManager manager = BuildSiteManager.get(s.level());
			BuildSiteManager loaded = BuildSiteManager.load(manager.save(new CompoundTag(), s.level().registryAccess()), s.level().registryAccess());
			helper.assertTrue(loaded.get(site.id()) != null && loaded.get(site.id()).isRefresh(), "the refresh wasn't saved");
			s.level().getDataStorage().set(BuildSiteManager.NAME, loaded);
			Villager old = builder[0];
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(old.save(tag), "the builder wasn't saved");
			old.discard();
			Entity entity = EntityType.loadEntityRecursive(tag, s.level(), e -> e);
			helper.assertTrue(entity instanceof Villager, "the builder didn't load: " + entity);
			s.level().addFreshEntity(entity);
			builder[0] = (Villager) entity;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never reloaded mid-way");
			assertRefreshed(helper, s);
			helper.assertTrue(sites(s) == 0, "a second refresh site after the reload");
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder[0]), "still at it");
			assertChestKept(helper);
			assertCount(helper, s, Items.STONE_BRICKS, builder[0], 1);
			assertCount(helper, s, Items.COBBLESTONE, builder[0], 1);
			assertCount(helper, s, Items.TORCH, builder[0], 3);
			assertCount(helper, s, Items.OAK_PLANKS, builder[0], 3);
		});
	}

	/**
	 * The builder dies mid-refresh: the refresh isn't stuck (no site left with a dead builder), the page offers it
	 * again, and a new builder through the button finishes it with the chest kept.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa_refresh_builder_dies"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_refresh_builder_dies")
	public void theBuilderDyingMidRefreshLeavesItToBeAskedAgain(GameTestHelper helper) {
		Stage s = stage(helper, true);
		Villager first = builder(helper, s.owner(), new BlockPos(3, 2, 3), BENCH);
		Villager[] second = {null};
		boolean[] died = {false};
		helper.runAfterDelay(2, () -> pressRefresh(helper, openView(helper, s.owner()), s.owner()));
		helper.onEachTick(() -> {
			if (died[0] || Refreshes.siteOf(s.level(), s.placement()) == null
				|| !s.level().getBlockState(helper.absolutePos(ROW.offset(1, 1, 0))).isAir()) {
				return;
			}
			died[0] = true;
			first.kill();
			helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) == null || !first.getUUID().equals(Refreshes.siteOf(s.level(), s.placement()).builder()),
				"the refresh is still set to the dead builder");
			ChoiceMenu menu = openPage(helper, s.owner());
			int slot = refreshSlot(menu);
			helper.assertTrue(slot >= 0, "the half-refreshed build isn't on the page any more");
			second[0] = builder(helper, s.owner(), new BlockPos(3, 2, 3), BENCH);
			if (Refreshes.siteOf(s.level(), s.placement()) == null && !BuildSiteManager.get(s.level()).refreshAsked(s.placement())) {
				menu.press(slot, s.owner());
				pressRefresh(helper, menu, s.owner());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(died[0] && second[0] != null, "the builder never died mid-way");
			assertRefreshed(helper, s);
			assertChestKept(helper);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(second[0]), "still at it");
		});
	}

	/**
	 * QA lane E (saves, 2026-10-27): a world saved mid-refresh opens with a jar that no longer ships the older drawing
	 * the refresh started from (the site keeps its key, "refresh_from"). Nothing is lost and nothing gets stuck: the
	 * loaded site carries on as an ordinary repair to the latest look (BuildSite: a key it can't find plans a repair),
	 * the build's chest keeps its emeralds, the latest drawing's new pieces go in, and the builder comes free.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_refresh_history_gone"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_refresh_history_gone")
	public void aSiteSavedMidRefreshLoadsWhenItsOlderDrawingIsNoLongerShipped(GameTestHelper helper) {
		Stage s = stage(helper, true);
		Villager[] builder = {builder(helper, s.owner(), new BlockPos(3, 2, 3), BENCH)};
		boolean[] reloaded = {false};
		helper.runAfterDelay(2, () -> pressRefresh(helper, openView(helper, s.owner()), s.owner()));
		helper.onEachTick(() -> {
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			if (reloaded[0] || site == null || !s.level().getBlockState(helper.absolutePos(ROW.offset(1, 1, 0))).isAir()) {
				return;
			}
			reloaded[0] = true;
			BuildSiteManager manager = BuildSiteManager.get(s.level());
			CompoundTag saved = manager.save(new CompoundTag(), s.level().registryAccess());
			Refreshes.removeHistory(s.id()); // the new jar: the older drawing isn't shipped any more
			BuildSiteManager loaded = BuildSiteManager.load(saved, s.level().registryAccess());
			helper.assertTrue(loaded.get(site.id()) != null, "the site saved mid-refresh didn't load");
			s.level().getDataStorage().set(BuildSiteManager.NAME, loaded);
			Villager old = builder[0];
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(old.save(tag), "the builder wasn't saved");
			old.discard();
			Entity entity = EntityType.loadEntityRecursive(tag, s.level(), e -> e);
			helper.assertTrue(entity instanceof Villager, "the builder didn't load: " + entity);
			s.level().addFreshEntity(entity);
			builder[0] = (Villager) entity;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never reloaded mid-way");
			helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) == null && sites(s) == 0,
				"the site whose older drawing is gone never finished: " + BuildSiteManager.get(s.level()).all().stream()
					.filter(site -> site.placement().equals(s.placement())).map(site -> site.stage() + " " + site.status()).toList());
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder[0]), "the builder is still at it");
			helper.assertBlockPresent(Blocks.OAK_PLANKS, ROW);
			helper.assertBlockPresent(Blocks.OAK_PLANKS, ROW.offset(6, 0, 0));
			helper.assertBlockPresent(Blocks.TORCH, ROW.offset(6, 1, 0));
			assertChestKept(helper);
			// nothing duplicated: a leftover taken down (before the save or after) comes back at most once
			for (Item item : List.of(Items.STONE_BRICKS, Items.COBBLESTONE)) {
				int n = count(helper, s, item, builder[0]);
				helper.assertTrue(n <= 1, item + ": " + n + " counted, more than the one block the older drawing had");
			}
		});
	}
}
