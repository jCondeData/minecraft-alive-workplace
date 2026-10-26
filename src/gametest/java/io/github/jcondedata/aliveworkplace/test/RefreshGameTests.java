package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
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
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Bringing an old build up to its latest look (ROADMAP 36.21), through the Village Hall's Repairs page: a build whose
 * blueprint was redrawn since shows "Refresh to the latest look", a first click shows what changes, the second hands
 * it to the nearest free builder, who takes away the old drawing's leftovers (their items to the store) and puts in
 * the new pieces. A chest and its items, a bed a villager sleeps in, a job block and a player's own blocks stay; with no
 * builder free it waits; short of materials the builder asks as for a repair; a build of the latest drawing has no
 * button; a refresh survives a save and reload mid-way. The Stone House is refreshed from its real older drawing.
 */
public class RefreshGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	/** The small build: seven blocks in a row (and the row behind it). */
	private static final BlockPos ROW = new BlockPos(4, 2, 7);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);

	private record Stage(ServerPlayer owner, Villager[] builder, BlueprintData.Placement placement, BlockPos at, Container store, ServerLevel level) {
	}

	/** The latest drawing: planks at both ends of the row, nothing between. */
	private static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState();

	/** The row's older drawing: stone bricks, a fence, a chest, a bed, a composter, a fence (and nothing at the end). */
	private static List<Blueprint.Entry> older() {
		List<Blueprint.Entry> blocks = new ArrayList<>();
		blocks.add(new Blueprint.Entry(new BlockPos(0, 0, 0), Blocks.STONE_BRICKS.defaultBlockState(), null));
		blocks.add(new Blueprint.Entry(new BlockPos(1, 0, 0), Blocks.OAK_FENCE.defaultBlockState(), null));
		blocks.add(new Blueprint.Entry(new BlockPos(2, 0, 0), Blocks.CHEST.defaultBlockState(), null));
		blocks.add(new Blueprint.Entry(new BlockPos(3, 0, 0), bed(BedPart.FOOT), null));
		blocks.add(new Blueprint.Entry(new BlockPos(3, 0, 1), bed(BedPart.HEAD), null));
		blocks.add(new Blueprint.Entry(new BlockPos(4, 0, 0), Blocks.COMPOSTER.defaultBlockState(), null));
		blocks.add(new Blueprint.Entry(new BlockPos(5, 0, 0), Blocks.OAK_FENCE.defaultBlockState(), null));
		return blocks;
	}

	private static BlockState bed(BedPart part) {
		return Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, part);
	}

	/**
	 * The hall, a builder hired by the owner at a bench with planks in its chest (unless {@code stocked} is false), and the
	 * owner's row built from the older drawing: a chest with diamonds, a bed {@code sleeper} sleeps in, a composter,
	 * the last fence changed to cobblestone by the owner and a glowstone they put behind the row. {@code current}: built
	 * from the latest drawing instead.
	 */
	private static Stage stage(GameTestHelper helper, boolean withBuilder, boolean stocked, boolean current) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000); // working hours
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		// The latest drawing, saved as a structure as a blueprint is.
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 2; z++) {
				helper.setBlock(ROW.offset(x, 0, z), Blocks.AIR);
			}
		}
		helper.setBlock(ROW, PLANKS);
		helper.setBlock(ROW.offset(6, 0, 0), PLANKS);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "refresh_" + Long.toHexString(level.getGameTime())
			+ "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(7, 1, 2), false, Blocks.STRUCTURE_VOID);
		Refreshes.addHistory(id, "older", new Blueprint(id, new Vec3i(7, 1, 2), older()));
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Refreshes.removeHistory(id);
			level.getServer().getPlayerList().remove(owner);
		});
		if (!current) {
			for (Blueprint.Entry e : older()) {
				helper.getLevel().setBlock(helper.absolutePos(ROW.offset(e.pos())), e.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
			helper.setBlock(ROW.offset(6, 0, 0), Blocks.AIR);
			Container inside = helper.getBlockEntity(ROW.offset(2, 0, 0));
			inside.setItem(0, new ItemStack(Items.DIAMOND, 5));
			helper.setBlock(ROW.offset(5, 0, 0), Blocks.COBBLESTONE); // the owner's own change
			helper.setBlock(ROW.offset(6, 0, 1), Blocks.GLOWSTONE); // and their own block
			// a villager sleeps in the bed (claimed once the game has registered it: claimBed)
			Villager sleeper = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 4));
			sleeper.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(ROW.offset(3, 0, 1))));
		}
		helper.setBlock(CHEST, Blocks.CHEST);
		Container store = helper.getBlockEntity(CHEST);
		if (stocked) {
			store.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager[] builder = {null};
		if (withBuilder) {
			builder[0] = builder(helper, owner);
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, owner.getUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return new Stage(owner, builder, placement, helper.absolutePos(ROW), store, level);
	}

	/** The bed's home ticket taken, as by the villager who sleeps there (the game registers a bed a tick after it's placed). */
	private static void claimBed(GameTestHelper helper) {
		BlockPos head = helper.absolutePos(ROW.offset(3, 0, 1));
		helper.assertTrue(helper.getLevel().getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(head), head, 4).isPresent(),
			"the bed couldn't be claimed");
	}

	private static Villager builder(GameTestHelper helper, ServerPlayer owner) {
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(helper.getLevel(), builder, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		return builder;
	}

	/** The hall's screen on the Repairs page, through its tab. */
	private static ChoiceMenu openPage(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		int tab = HallPages.slot(RepairsPage.PAGE);
		helper.assertTrue(tab >= VillageHallScreen.PAGE_ROW, "the Repairs page has no tab: " + tab);
		menu.press(tab, player);
		helper.assertTrue(menu.icon(4).is(Items.ANVIL), "the page's header: " + menu.icon(4));
		return menu;
	}

	/** The slot of the build's "Refresh to the latest look" entry on the page, or -1. */
	private static int refreshSlot(ChoiceMenu menu) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).is(Items.PAINTING) || menu.icon(slot).is(Items.SCAFFOLDING)) {
				return slot;
			}
		}
		return -1;
	}

	private static String lore(ItemStack stack) {
		StringBuilder out = new StringBuilder();
		for (net.minecraft.network.chat.Component line : stack.getOrDefault(net.minecraft.core.component.DataComponents.LORE,
			net.minecraft.world.item.component.ItemLore.EMPTY).lines()) {
			out.append(line.getString()).append('\n');
		}
		return out.toString();
	}

	/** First click: the build's view with what changes; second: the button. Returns the menu. */
	private static ChoiceMenu clickRefresh(GameTestHelper helper, Stage s) {
		claimBed(helper);
		ChoiceMenu menu = openPage(helper, s.owner());
		int slot = refreshSlot(menu);
		helper.assertTrue(slot >= 0, "no build to refresh on the Repairs page");
		ItemStack entry = menu.icon(slot);
		helper.assertTrue(lore(entry).contains("Its blueprint has changed since it was built"), "the entry's lines: " + lore(entry));
		menu.press(slot, s.owner());
		String shown = lore(menu.icon(4));
		helper.assertTrue(shown.contains("New: 2 blocks") && shown.contains("Taken away: 1 "), "what changes: " + shown);
		helper.assertTrue(menu.icon(RepairsPage.REFRESH).is(Items.BRUSH)
				&& menu.icon(RepairsPage.REFRESH).getHoverName().getString().equals("Refresh to the latest look"),
			"no Refresh button: " + menu.icon(RepairsPage.REFRESH));
		menu.press(RepairsPage.REFRESH, s.owner());
		return menu;
	}

	private static int count(Stage s, GameTestHelper helper, net.minecraft.world.item.Item item) {
		Container inside = helper.getBlockEntity(ROW.offset(2, 0, 0));
		return s.store().countItem(item) + inside.countItem(item);
	}

	/** What stays, after a refresh: the chest and its diamonds, the bed and its sleeper's claim, the composter, the owner's blocks. */
	private static void assertKept(GameTestHelper helper, Stage s) {
		helper.assertBlockPresent(Blocks.CHEST, ROW.offset(2, 0, 0));
		Container inside = helper.getBlockEntity(ROW.offset(2, 0, 0));
		helper.assertTrue(inside.countItem(Items.DIAMOND) == 5, "the chest's diamonds: " + inside.countItem(Items.DIAMOND));
		helper.assertBlockPresent(Blocks.RED_BED, ROW.offset(3, 0, 0));
		helper.assertBlockPresent(Blocks.RED_BED, ROW.offset(3, 0, 1));
		BlockPos head = helper.absolutePos(ROW.offset(3, 0, 1));
		helper.assertTrue(s.level().getPoiManager().getFreeTickets(head) == 0, "the bed's claim was lost");
		helper.assertBlockPresent(Blocks.COMPOSTER, ROW.offset(4, 0, 0));
		helper.assertBlockPresent(Blocks.COBBLESTONE, ROW.offset(5, 0, 0));
		helper.assertBlockPresent(Blocks.GLOWSTONE, ROW.offset(6, 0, 1));
	}

	private static void assertRefreshed(GameTestHelper helper, Stage s) {
		helper.assertBlockPresent(Blocks.OAK_PLANKS, ROW);
		helper.assertBlockPresent(Blocks.AIR, ROW.offset(1, 0, 0));
		helper.assertBlockPresent(Blocks.OAK_PLANKS, ROW.offset(6, 0, 0));
		helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) == null, "still refreshing");
	}

	/**
	 * The whole refresh through the hall: the page offers it with what changes, the button hands it to the free builder,
	 * who takes the stone bricks and the fence away (into the store) and puts the planks in; the chest with its diamonds,
	 * the bed with its sleeper's claim, the composter and the owner's own blocks stay; afterwards the button's gone.
	 */
	//$ gametest_ticks_batch AREA '2400' '"refresh_row"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "refresh_row")
	public void theHallsButtonRefreshesAnOldBuildAndKeepsWhatsNotTheBuilders(GameTestHelper helper) {
		Stage s = stage(helper, true, true, false);
		helper.runAfterDelay(2, () -> {
			clickRefresh(helper, s);
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			helper.assertTrue(site != null && s.builder()[0].getUUID().equals(site.builder()) && site.isRefresh(), "the button didn't set the builder to it: " + site);
			ChoiceMenu menu = openPage(helper, s.owner());
			helper.assertTrue(lore(menu.icon(refreshSlot(menu))).contains("is bringing it up to its latest look"), "the page doesn't show it going on");
		});
		helper.succeedWhen(() -> {
			assertRefreshed(helper, s);
			assertKept(helper, s);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()[0]), "still at it");
			helper.assertTrue(count(s, helper, Items.OAK_FENCE) >= 1 && count(s, helper, Items.STONE_BRICKS) >= 1,
				"the leftovers' items aren't in the store: fence " + count(s, helper, Items.OAK_FENCE) + ", stone bricks " + count(s, helper, Items.STONE_BRICKS));
			ChoiceMenu menu = openPage(helper, s.owner());
			helper.assertTrue(refreshSlot(menu) < 0, "the button's still there once refreshed");
		});
	}

	/** A build of the latest drawing (nothing changed for it): no button on the page. */
	//$ gametest_ticks_batch AREA '100' '"refresh_none"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "refresh_none")
	public void aBuildOfTheLatestDrawingHasNoButton(GameTestHelper helper) {
		Stage s = stage(helper, true, true, true);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPage(helper, s.owner());
			helper.assertTrue(refreshSlot(menu) < 0, "a build with nothing changed shows the button: " + menu.icon(refreshSlot(menu)));
			helper.assertTrue(Refreshes.change(s.level(), RepairAsksAccess.building(s)).isEmpty(), "a change was found");
			helper.succeed();
		});
	}

	/** No builder free: the refresh waits (the page says so), and the first builder free takes it up and does it. */
	//$ gametest_ticks_batch AREA '2400' '"refresh_wait"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "refresh_wait")
	public void withNoFreeBuilderItWaitsUntilOneIs(GameTestHelper helper) {
		Stage s = stage(helper, false, true, false);
		helper.runAfterDelay(2, () -> {
			clickRefresh(helper, s);
			helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) == null, "a refresh started with no builder");
			helper.assertTrue(BuildSiteManager.get(s.level()).refreshAsked(s.placement()), "the refresh isn't waiting");
			ChoiceMenu menu = openPage(helper, s.owner());
			helper.assertTrue(lore(menu.icon(refreshSlot(menu))).contains("waiting for a free Builder"), "the page doesn't say it waits: "
				+ lore(menu.icon(refreshSlot(menu))));
		});
		helper.runAfterDelay(40, () -> {
			helper.assertBlockPresent(Blocks.OAK_FENCE, ROW.offset(1, 0, 0));
			s.builder()[0] = builder(helper, s.owner());
			Upkeep.look(s.level(), s.builder()[0]); // their round, now
			helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) != null, "the free builder didn't take it up");
			helper.assertFalse(BuildSiteManager.get(s.level()).refreshAsked(s.placement()), "still waiting once taken up");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(s.builder()[0] != null, "no builder yet");
			assertRefreshed(helper, s);
			assertKept(helper, s);
		});
	}

	/** Short of planks: the leftovers come away, then the builder waits for the planks and says what's missing, as for a repair. */
	//$ gametest_ticks_batch AREA '1600' '"refresh_short"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "refresh_short")
	public void shortOfMaterialsItAsksLikeARepair(GameTestHelper helper) {
		Stage s = stage(helper, true, false, false);
		helper.runAfterDelay(2, () -> clickRefresh(helper, s));
		helper.succeedWhen(() -> {
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			helper.assertTrue(site != null, "the refresh ended without its planks");
			helper.assertBlockPresent(Blocks.AIR, ROW.offset(1, 0, 0));
			helper.assertTrue(site.status() == BuildSite.Status.WAITING_FOR_MATERIALS && site.missing().containsKey(Items.OAK_PLANKS),
				"not waiting for the planks: " + site.status() + " " + site.missing());
			helper.assertBlockPresent(Blocks.AIR, ROW.offset(6, 0, 0));
			assertKept(helper, s);
		});
	}

	/**
	 * Saved and loaded mid-way, once the fence is gone: the site comes back a refresh from the same drawing, the waiting
	 * flag round-trips with the buildings, and with the saved sites and the builder loaded back the refresh finishes.
	 */
	//$ gametest_ticks_batch AREA '2400' '"refresh_reload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "refresh_reload")
	public void aRefreshSurvivesASaveAndReloadMidWay(GameTestHelper helper) {
		Stage s = stage(helper, true, true, false);
		boolean[] reloaded = {false};
		helper.runAfterDelay(2, () -> clickRefresh(helper, s));
		helper.onEachTick(() -> {
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			if (reloaded[0] || site == null || !s.level().getBlockState(helper.absolutePos(ROW.offset(1, 0, 0))).isAir()) {
				return;
			}
			reloaded[0] = true;
			BuildSite back = BuildSite.load(site.save());
			helper.assertTrue(back != null && back.isRefresh() && "older".equals(back.refreshFrom()) && back.stage() == site.stage(),
				"the refresh didn't survive a save: " + (back == null ? null : back.refreshFrom() + " " + back.stage()));
			BuildSiteManager manager = BuildSiteManager.get(s.level());
			BuildSiteManager loaded = BuildSiteManager.load(manager.save(new CompoundTag(), s.level().registryAccess()), s.level().registryAccess());
			helper.assertTrue(loaded.get(site.id()) != null && loaded.get(site.id()).isRefresh(), "the refresh site wasn't saved with the others");
			// the waiting flag too
			manager.setRefreshAsked(s.placement(), true);
			BuildSiteManager again = BuildSiteManager.load(manager.save(new CompoundTag(), s.level().registryAccess()), s.level().registryAccess());
			manager.setRefreshAsked(s.placement(), false);
			helper.assertTrue(again.refreshAsked(s.placement()), "a refresh waiting for a builder wasn't saved");
			// the sites and the builder as they come back after a restart
			s.level().getDataStorage().set(BuildSiteManager.NAME, loaded);
			Villager old = s.builder()[0];
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(old.save(tag), "the builder wasn't saved");
			old.discard();
			Entity entity = EntityType.loadEntityRecursive(tag, s.level(), e -> e);
			helper.assertTrue(entity instanceof Villager, "the builder didn't load: " + entity);
			s.level().addFreshEntity(entity);
			s.builder()[0] = (Villager) entity;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never reloaded mid-way");
			assertRefreshed(helper, s);
			assertKept(helper, s);
		});
	}

	/** Finds the finished building of a stage (as the page does). */
	private static final class RepairAsksAccess {
		static BuildSiteManager.Finished building(Stage s) {
			return io.github.jcondedata.aliveworkplace.build.RepairAsks.building(s.level(), s.at()).orElseThrow();
		}
	}

	// ---- the Stone House --------------------------------------------------------------------------------------------

	private static final BlockPos HOUSE = new BlockPos(6, 2, 6);
	private static final ResourceLocation STONE_HOUSE = AliveWorkplace.id("stone_house");
	/** The Stone House's first drawing, as the mod ships it (tools/blueprints/history.py). */
	private static final String FIRST = "25c521dcd";

	/**
	 * A Stone House built from its first drawing, refreshed through the hall to the current one: every leftover the
	 * latest drawing no longer has is gone (or turned into the new piece), and every new piece stands.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"refresh_stone_house"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "refresh_stone_house")
	public void aStoneHouseFromAnOlderDrawingIsRefreshedToTheCurrentOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 32;
		helper.setDayTime(2000);
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(2000); // the whole refresh on one shift
			}
		});
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getServer().getPlayerList().remove(owner);
		});
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		Blueprint first = olderStoneHouse(helper, level);
		// built as the first drawing drew it
		for (Map.Entry<BlockPos, BlockState> e : BuildPlan.create(first, placement).targets().entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(1, 2, 3), Blocks.CHEST);
		List<BlockPos> stores = List.of(new BlockPos(1, 2, 26), new BlockPos(1, 2, 28), new BlockPos(5, 2, 26), new BlockPos(5, 2, 28));
		stores.forEach(c -> helper.setBlock(c, Blocks.CHEST));
		helper.setBlock(new BlockPos(3, 2, 26), ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 24));
		Builders.employ(level, builder, helper.absolutePos(new BlockPos(3, 2, 26)));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		BuildSiteManager.get(level).recordFinished(STONE_HOUSE, placement, owner.getUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BuildPlan[] plan = {null};
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = VillageHallScreen.forTest(owner, helper.absolutePos(new BlockPos(1, 2, 1)));
			menu.press(HallPages.slot(RepairsPage.PAGE), owner);
			int slot = refreshSlot(menu);
			helper.assertTrue(slot >= 0, "the old Stone House has no Refresh on the Repairs page");
			menu.press(slot, owner);
			helper.assertTrue(lore(menu.icon(4)).contains("New: "), "what changes: " + lore(menu.icon(4)));
			menu.press(RepairsPage.REFRESH, owner);
			BuildSite site = Refreshes.siteOf(level, placement);
			helper.assertTrue(site != null && FIRST.equals(site.refreshFrom()), "not refreshed from the first drawing: " + (site == null ? null : site.refreshFrom()));
			plan[0] = site.plan(level);
			// the store holds what the new pieces take
			int at = 0;
			for (Map.Entry<net.minecraft.world.item.Item, Integer> need : plan[0].materials().entrySet()) {
				for (int left = need.getValue() + 2; left > 0 && at < 27 * stores.size(); at++) {
					int n = Math.min(left, need.getKey().getDefaultMaxStackSize());
					Container store = helper.getBlockEntity(stores.get(at / 27));
					store.setItem(at % 27, new ItemStack(need.getKey(), n));
					left -= n;
				}
			}
			helper.assertTrue(at < 27 * stores.size(), "the stores are too small for the refresh's materials: " + plan[0].materials());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(plan[0] != null, "not started");
			helper.assertTrue(Refreshes.siteOf(level, placement) == null, "still refreshing");
			Map<BlockPos, BlockState> now = BuildPlan.create(BlueprintLibrary.get(level, STONE_HOUSE).orElseThrow(), placement).targets();
			for (BuildPlan.Step step : plan[0].steps(BuildPlan.Stage.CLEAR)) {
				BlockState world = level.getBlockState(step.pos());
				helper.assertTrue(step.state().isAir() ? world.isAir() || world.canBeReplaced() : MaterialRules.matches(world, step.state()),
					"a leftover of the first drawing stands at " + step.pos().toShortString() + ": " + world);
			}
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan[0].steps(stage)) {
					helper.assertTrue(MaterialRules.matches(level.getBlockState(step.pos()), now.get(step.pos())),
						"a new piece is missing at " + step.pos().toShortString() + ": " + level.getBlockState(step.pos()) + ", wanted " + now.get(step.pos()));
				}
			}
			helper.assertTrue(Refreshes.change(level, BuildSiteManager.get(level).finishedIn(level).stream()
				.filter(f -> f.placement().equals(placement)).findFirst().orElseThrow()).isEmpty(), "the refreshed house still offers a refresh");
		});
	}

	/** The Stone House's first drawing, from the history the mod ships. */
	static Blueprint olderStoneHouse(GameTestHelper helper, ServerLevel level) {
		return Refreshes.olderDrawing(level.getServer(), STONE_HOUSE, FIRST).orElseThrow(() -> new IllegalStateException("no first drawing of the Stone House shipped"));
	}
}
