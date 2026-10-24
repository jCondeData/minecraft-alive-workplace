package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.UpgradesPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.PickaxeCrafting;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Mourning;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.RandomJobs;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA tests (lane-c-1024-0953) for ROADMAP 36.11, 36.14, 36.16 and 36.17, written from each item's "Done when" for the
 * cases the builders' own tests (MinerPickaxe, UpgradesPage, Mourning, LifeStage, AgelessElder, RandomJobs, StationsSpec)
 * leave out:
 * <ul>
 * <li>36.11: a pickaxe already in the chest is taken, not made; one cobblestone short is a wait with a request for the
 * one missing; the village store's sticks count but its cobblestone (not "what they mined themselves") doesn't; one
 * stick and one plank are not enough, and two planks make four sticks; materials taken away mid-craft make no pickaxe.</li>
 * <li>36.14: the page's clicks as a player makes them, with the action bar's words: no Builder, a busy Builder (queued),
 * a stranger to an owned village, a fence raised between opening the page and the click, one block to clear (said in
 * the singular), and the tab counting one or two builds (a build outside the village not counted).</li>
 * <li>36.16: the players told are those in the village (its radius from both sides); years turn over after exactly
 * four seasons; parents, half-siblings and children grieve, strangers don't; grief and a widow's mourning end on the
 * right day through the hall's own round.</li>
 * <li>36.17: the draw in play leans towards the job the village lacks; the weights at the edges of the random (exactly
 * 1 / (1 + n)); dead villagers and the villager drawing aren't counted; the wiki lists every block with several jobs and
 * each of its jobs.</li>
 * </ul>
 */
public class QaVillagerLifeJobsGameTests implements FabricGameTest {
	private static final String BUILD = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	// =================================================================================================================
	// 36.11 Miners craft their own pickaxe
	// =================================================================================================================

	private static final BlockPos M_BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos M_CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos M_VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos M_MIN = new BlockPos(6, 2, 6);
	private static final BlockPos M_MAX = new BlockPos(7, 2, 7);

	private record Mine(ServerLevel level, Villager miner, QuarrySite site) {
	}

	/** A Miner at their bench with a 2x1x2 stone quarry to dig and {@code chest} in their own chest. */
	private static Mine mine(GameTestHelper helper, ItemStack... chest) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		for (BlockPos p : BlockPos.betweenClosed(M_MIN, M_MAX)) {
			helper.setBlock(p, Blocks.STONE);
		}
		helper.setBlock(M_BENCH, ModBlocks.MINERS_BENCH);
		helper.setBlock(M_CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(M_CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, M_VILLAGER);
		Miners.employ(level, villager, helper.absolutePos(M_BENCH));
		QuarrySite site = Miners.start(level, villager, null, BoundingBox.fromCorners(helper.absolutePos(M_MIN), helper.absolutePos(M_MAX)), 1);
		site.setStairs(false);
		return new Mine(level, villager, site);
	}

	private static QuarrySite live(Mine m) {
		return QuarrySiteManager.get(m.level()).get(m.site().id());
	}

	private static String state(GameTestHelper helper, Mine m) {
		QuarrySite site = live(m);
		return site == null ? "finished" : site.status() + ", " + site.mined() + " dug, holding " + m.miner().getMainHandItem()
			+ ", at " + helper.relativePos(m.miner().blockPosition());
	}

	private static int asks(Mine m, Item item) {
		for (Requests.Request r : Requests.of(m.level(), m.miner())) {
			if (r.item() == item) {
				return r.count();
			}
		}
		return 0;
	}

	/**
	 * A good pickaxe already in the chest: the Miner takes it and digs; nothing is made, the cobblestone and sticks set
	 * by for a pickaxe stay where they are (the four dug blocks are added to the cobblestone).
	 */
	//$ gametest_ticks_batch BUILD '2400' '"qaMinerTakesChestPickaxe"'
	@GameTest(template = BUILD, timeoutTicks = 2400, batch = "qaMinerTakesChestPickaxe")
	public void qaAPickaxeInTheChestIsTakenNotMade(GameTestHelper helper) {
		Mine m = mine(helper, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.COBBLESTONE, 3), new ItemStack(Items.STICK, 2));
		boolean[] crafted = {false};
		helper.onEachTick(() -> {
			QuarrySite site = live(m);
			if (site != null && site.status() == QuarrySite.Status.CRAFTING_PICKAXE) {
				crafted[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!crafted[0], "the miner went to make a pickaxe with an iron one in the chest");
			helper.assertTrue(live(m) == null, "still going: " + state(helper, m));
			Container chest = helper.getBlockEntity(M_CHEST);
			helper.assertTrue(chest.countItem(Items.IRON_PICKAXE) == 1 && chest.countItem(Items.STONE_PICKAXE) == 0,
				"pickaxes in the chest: " + chest.countItem(Items.IRON_PICKAXE) + " iron, " + chest.countItem(Items.STONE_PICKAXE) + " stone");
			helper.assertTrue(chest.countItem(Items.STICK) == 2, "sticks used: " + (2 - chest.countItem(Items.STICK)));
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 7, "3 kept and 4 dug, the chest has " + chest.countItem(Items.COBBLESTONE));
		});
	}

	/**
	 * Two cobblestone and two sticks: one short. The Miner waits (nothing taken, nothing dug) and the board asks for the
	 * one cobblestone missing, not three; with a third in the chest they make the pickaxe and dig the quarry out.
	 */
	//$ gametest_ticks_batch BUILD '2400' '"qaMinerOneShort"'
	@GameTest(template = BUILD, timeoutTicks = 2400, batch = "qaMinerOneShort")
	public void qaOneCobblestoneShortIsAWaitForOne(GameTestHelper helper) {
		Mine m = mine(helper, new ItemStack(Items.COBBLESTONE, 2), new ItemStack(Items.STICK, 2));
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(m.site().status() == QuarrySite.Status.NEEDS_PICKAXE && m.site().mined() == 0, "not waiting: " + state(helper, m));
			helper.assertTrue(m.miner().getMainHandItem().isEmpty(), "a pickaxe from two cobblestone: " + m.miner().getMainHandItem());
			helper.assertTrue(asks(m, Items.COBBLESTONE) == 1, "the board should ask for the 1 cobblestone missing, asks " + asks(m, Items.COBBLESTONE));
			helper.assertTrue(asks(m, Items.STICK) == 0, "the sticks are there, yet asked for: " + asks(m, Items.STICK));
			Container chest = helper.getBlockEntity(M_CHEST);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 2 && chest.countItem(Items.STICK) == 2,
				"taken while short: " + chest.countItem(Items.COBBLESTONE) + " cobblestone, " + chest.countItem(Items.STICK) + " sticks left");
			chest.setItem(5, new ItemStack(Items.COBBLESTONE, 1));
		});
		helper.runAfterDelay(121, () -> helper.succeedWhen(() -> {
			helper.assertTrue(live(m) == null, "still going: " + state(helper, m));
			Container chest = helper.getBlockEntity(M_CHEST);
			helper.assertTrue(chest.countItem(Items.STONE_PICKAXE) == 1, "no pickaxe made once the third cobblestone came");
			helper.assertTrue(chest.countItem(Items.STICK) == 0 && chest.countItem(Items.COBBLESTONE) == 4,
				"materials: " + chest.countItem(Items.STICK) + " sticks, " + chest.countItem(Items.COBBLESTONE) + " cobblestone");
		}));
	}

	/**
	 * The spec: the head from "what they mined themselves", the sticks "from the village store". Cobblestone and sticks in
	 * the village store (a Storehouse's chest, not the Miner's own): the sticks are used, the store's cobblestone isn't.
	 */
	//$ gametest_batch BUILD '"qaMinerVillageStore"'
	@GameTest(template = BUILD, batch = "qaMinerVillageStore")
	public void qaTheStoresSticksCountButNotItsCobblestone(GameTestHelper helper) {
		Mine m = mine(helper);
		Leftovers.storehouses(helper);
		ServerLevel level = m.level();
		BlockPos storehouse = new BlockPos(2, 2, 14);
		BlockPos storeChest = new BlockPos(3, 2, 14);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storeChest, Blocks.CHEST);
		Container store = helper.getBlockEntity(storeChest);
		store.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
		store.setItem(1, new ItemStack(Items.STICK, 2));
		BlockPos bench = helper.absolutePos(M_BENCH);
		List<BlockPos> own = SupplyContainers.find(level, bench, m.site().box());
		helper.assertTrue(own.contains(helper.absolutePos(M_CHEST)) && !own.contains(helper.absolutePos(storeChest)), "setup: own chests " + own);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(m.miner());
		// only the store's cobblestone: not theirs to make a head from
		helper.assertTrue(PickaxeCrafting.plan(level, m.miner(), bench, own, bag) == null, "a pickaxe planned from the store's cobblestone");
		PickaxeCrafting.Missing missing = PickaxeCrafting.missing(level, m.miner(), bench, own, bag);
		helper.assertTrue(missing != null && missing.item() == Items.COBBLESTONE && missing.count() == 3, "missing: " + missing);
		helper.assertTrue(PickaxeCrafting.craft(level, m.miner(), bench, own, bag).isEmpty(), "made from the store's cobblestone");
		helper.assertTrue(store.countItem(Items.COBBLESTONE) == 3 && store.countItem(Items.STICK) == 2, "the store was taken from for nothing");
		// their own cobblestone: the pickaxe, with the store's sticks
		Container chest = helper.getBlockEntity(M_CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
		ItemStack made = PickaxeCrafting.craft(level, m.miner(), bench, own, bag);
		helper.assertTrue(made.is(Items.STONE_PICKAXE), "made " + made);
		helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0, "their own cobblestone wasn't used");
		helper.assertTrue(store.countItem(Items.STICK) == 0, "the store's sticks weren't used: " + store.countItem(Items.STICK));
		helper.assertTrue(store.countItem(Items.COBBLESTONE) == 3, "the store's cobblestone was used: " + store.countItem(Items.COBBLESTONE));
		helper.succeed();
	}

	/**
	 * Sticks at the boundary: one stick and one plank make no pickaxe (one stick short, nothing taken); a second plank
	 * makes it, the two planks cut into four sticks, and the three spare sticks are kept (none lost, none made up).
	 */
	//$ gametest_batch BUILD '"qaMinerSticksBoundary"'
	@GameTest(template = BUILD, batch = "qaMinerSticksBoundary")
	public void qaOneStickAndOnePlankAreNotEnough(GameTestHelper helper) {
		Mine m = mine(helper, new ItemStack(Items.COBBLESTONE, 3), new ItemStack(Items.STICK, 1), new ItemStack(Items.OAK_PLANKS, 1));
		ServerLevel level = m.level();
		BlockPos bench = helper.absolutePos(M_BENCH);
		List<BlockPos> own = SupplyContainers.find(level, bench, m.site().box());
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(m.miner());
		Container chest = helper.getBlockEntity(M_CHEST);
		helper.assertTrue(PickaxeCrafting.plan(level, m.miner(), bench, own, bag) == null, "planned with one stick and one plank");
		PickaxeCrafting.Missing missing = PickaxeCrafting.missing(level, m.miner(), bench, own, bag);
		helper.assertTrue(missing != null && missing.item() == Items.STICK && missing.count() == 1, "missing: " + missing);
		helper.assertTrue(PickaxeCrafting.craft(level, m.miner(), bench, own, bag).isEmpty(), "made with one stick and one plank");
		helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 3 && chest.countItem(Items.STICK) == 1 && chest.countItem(Items.OAK_PLANKS) == 1,
			"taken while short");
		chest.setItem(3, new ItemStack(Items.SPRUCE_PLANKS, 1)); // any planks, mixed as the vanilla recipe allows
		ItemStack made = PickaxeCrafting.craft(level, m.miner(), bench, own, bag);
		helper.assertTrue(made.is(Items.STONE_PICKAXE), "made " + made);
		helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0 && chest.countItem(Items.OAK_PLANKS) == 0 && chest.countItem(Items.SPRUCE_PLANKS) == 0,
			"left: " + chest.countItem(Items.COBBLESTONE) + " cobblestone, " + chest.countItem(Items.OAK_PLANKS) + "+" + chest.countItem(Items.SPRUCE_PLANKS) + " planks");
		long spare = chest.countItem(Items.STICK) + bag.count(Items.STICK);
		helper.assertTrue(spare == 3, "1 stick and 4 cut, 2 used: 3 spare expected, " + spare + " kept");
		helper.succeed();
	}

	/**
	 * The materials taken from the chest while the Miner is at the bench making the pickaxe: no pickaxe from nothing; the
	 * Miner is back to waiting for one, nothing dug, and asks for the cobblestone again.
	 */
	//$ gametest_ticks_batch BUILD '1200' '"qaMinerMaterialsGone"'
	@GameTest(template = BUILD, timeoutTicks = 1200, batch = "qaMinerMaterialsGone")
	public void qaMaterialsTakenMidCraftMakeNoPickaxe(GameTestHelper helper) {
		Mine m = mine(helper, new ItemStack(Items.COBBLESTONE, 3), new ItemStack(Items.STICK, 2));
		boolean[] seen = {false};
		helper.onEachTick(() -> {
			QuarrySite site = live(m);
			if (!seen[0] && site != null && site.status() == QuarrySite.Status.CRAFTING_PICKAXE) {
				seen[0] = true;
				Container chest = helper.getBlockEntity(M_CHEST);
				for (int i = 0; i < chest.getContainerSize(); i++) {
					chest.setItem(i, ItemStack.EMPTY);
				}
				helper.runAfterDelay(PickaxeCrafting.CRAFT_TICKS * 4, () -> {
					helper.assertTrue(m.miner().getMainHandItem().isEmpty(), "a pickaxe made from nothing: " + m.miner().getMainHandItem());
					BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(m.miner());
					helper.assertTrue(bag.count(Items.STONE_PICKAXE) == 0, "a pickaxe in the bag");
					helper.assertTrue(m.site().status() == QuarrySite.Status.NEEDS_PICKAXE && m.site().mined() == 0, "not waiting again: " + state(helper, m));
					helper.assertTrue(asks(m, Items.COBBLESTONE) == 3, "the board should ask for 3 cobblestone again, asks " + asks(m, Items.COBBLESTONE));
					helper.succeed();
				});
			}
		});
		helper.runAfterDelay(600, () -> helper.assertTrue(seen[0], "never started making the pickaxe: " + state(helper, m)));
	}

	// =================================================================================================================
	// 36.14 Upgrade a built home from the Village Hall
	// =================================================================================================================

	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ResourceLocation HUT_2 = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut_2");
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	private static final BlockPos U_HALL = new BlockPos(15, 2, 11);
	private static final BlockPos U_HUT = new BlockPos(3, 2, 3);
	private static final int ADDED_Y = 6;
	private static final BlockPos U_BENCH = new BlockPos(13, 2, 3);
	private static final BlockPos U_CHEST = new BlockPos(13, 2, 5);

	private record Village(ServerLevel level, BlockPos hall, BlueprintData.Placement hut, Villager builder) {
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos rel) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE);
	}

	/** The hall (radius 16), a finished Test Hut standing (its level II adds the layer at y = 6) and Bob the Builder at his bench. */
	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement hut = placement(helper, U_HUT);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			for (BuildSite site : List.copyOf(sites.all())) {
				if (site.placement().origin().closerThan(helper.absolutePos(U_HUT), 40)) {
					sites.remove(site.id());
				}
			}
			Leftovers.finished(helper);
		});
		helper.setBlock(U_HALL, ModBlocks.VILLAGE_HALL);
		BlockPos origin = helper.absolutePos(U_HUT);
		level.getStructureManager().get(HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		sites.recordFinished(HUT, hut, UUID.randomUUID());
		helper.setBlock(U_BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(U_CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(U_CHEST);
		chest.setItem(0, new ItemStack(Items.LANTERN, 4));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 4));
		builder.setCustomName(Component.literal("Bob"));
		Builders.employ(level, builder, helper.absolutePos(U_BENCH));
		return new Village(level, helper.absolutePos(U_HALL), hut, builder);
	}

	private static ChoiceMenu upgradesPage(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(U_HALL));
		menu.press(HallPages.slot(UpgradesPage.PAGE), player);
		helper.assertTrue(menu.icon(4).is(Items.BRICKS), "not on the Upgrades page: " + menu.icon(4));
		return menu;
	}

	private static Optional<BuildSite> upgradeSite(Village v) {
		return BuildSiteManager.get(v.level()).all().stream().filter(s -> s.placement().equals(v.hut()) && !s.isDone()).findFirst();
	}

	/** The last action-bar or chat line {@code player} saw, as text. */
	private static String last(List<Component> seen) {
		return seen.isEmpty() ? "(nothing)" : seen.get(seen.size() - 1).getString();
	}

	/** No Builder in the village: the click says so (naming the level II) and orders nothing; the row stays clickable. */
	//$ gametest_ticks_batch BIG '100' '"qaUpgradeNoBuilder"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaUpgradeNoBuilder")
	public void qaNoBuilderSaysSoAndOrdersNothing(GameTestHelper helper) {
		Village v = village(helper);
		v.builder().discard();
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = WonderFinishedGameTests.listener(helper, seen, "QaNoBuilder");
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = upgradesPage(helper, player);
			seen.clear();
			menu.press(VillageHallScreen.FIRST_ROW, player);
			String says = Component.translatable("message.aliveworkplace.upgrades.no_builder", Blueprints.displayName(HUT_2)).getString();
			helper.assertTrue(last(seen).equals(says), "the click said '" + last(seen) + "', expected '" + says + "'");
			helper.assertTrue(says.startsWith("No Builder can take the " + Blueprints.displayName(HUT_2).getString()), "reads: " + says);
			helper.assertTrue(upgradeSite(v).isEmpty(), "ordered with no Builder");
			ItemStack row = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(row.is(Items.BRICKS) && lore(row).contains(Component.translatable("screen.aliveworkplace.upgrades.click").getString()),
				"the row after: " + row + " " + lore(row));
			helper.succeed();
		});
	}

	/** Bob busy with another build: the upgrade joins his queue, the click says "after the current build", the row shows it under way. */
	//$ gametest_ticks_batch BIG '100' '"qaUpgradeQueued"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaUpgradeQueued")
	public void qaABusyBuilderQueuesTheUpgrade(GameTestHelper helper) {
		Village v = village(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = WonderFinishedGameTests.listener(helper, seen, "QaQueue");
		BuildSite pillar = Builders.start(v.level(), v.builder(), (net.minecraft.world.entity.player.Player) null, PILLAR, placement(helper, new BlockPos(3, 2, 15)));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Builders.activeSite(v.level(), v.builder()) != null, "setup: Bob isn't busy");
			ChoiceMenu menu = upgradesPage(helper, player);
			seen.clear();
			menu.press(VillageHallScreen.FIRST_ROW, player);
			String says = Component.translatable("message.aliveworkplace.upgrades.queued", "Bob", Blueprints.displayName(HUT_2), Blueprints.displayName(HUT)).getString();
			helper.assertTrue(last(seen).equals(says), "the click said '" + last(seen) + "', expected '" + says + "'");
			List<BuildSite> queue = Builders.queue(v.level(), v.builder());
			helper.assertTrue(queue.stream().anyMatch(s -> s.placement().equals(v.hut()) && s.structure().equals(HUT_2)), "not in Bob's queue: " + queue);
			helper.assertTrue(Builders.activeSite(v.level(), v.builder()).id().equals(pillar.id()), "the current build was dropped for the upgrade");
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(Items.SCAFFOLDING), "the row: " + menu.icon(VillageHallScreen.FIRST_ROW));
			seen.clear();
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(BuildSiteManager.get(v.level()).all().stream().filter(s -> s.placement().equals(v.hut()) && !s.isDone()).count() == 1, "queued twice");
			helper.succeed();
		});
	}

	/** A village with an owner: a stranger's click is refused in words, nothing ordered; the owner's own click goes ahead. */
	//$ gametest_ticks_batch BIG '100' '"qaUpgradeStranger"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaUpgradeStranger")
	public void qaAStrangerMayNotOrderAnUpgrade(GameTestHelper helper) {
		Village v = village(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer stranger = WonderFinishedGameTests.listener(helper, seen, "QaStranger");
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, new ArrayList<>(), "QaOwner");
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity hall = (VillageHallBlockEntity) v.level().getBlockEntity(v.hall());
			hall.setOwner(owner.getUUID(), "QaOwner");
			helper.assertTrue(!stranger.hasPermissions(2), "setup: the stranger is an operator");
			ChoiceMenu menu = upgradesPage(helper, stranger);
			seen.clear();
			menu.press(VillageHallScreen.FIRST_ROW, stranger);
			String says = Component.translatable("message.aliveworkplace.upgrades.not_allowed").getString();
			helper.assertTrue(last(seen).equals(says), "the click said '" + last(seen) + "', expected '" + says + "'");
			helper.assertTrue(upgradeSite(v).isEmpty(), "a stranger ordered the upgrade");
			helper.assertTrue(UpgradesPage.order(v.level(), v.hall(), owner, v.hut()) == UpgradesPage.Outcome.STARTED, "the owner's order");
			helper.assertTrue(upgradeSite(v).isPresent(), "the owner's order opened no site");
			helper.succeed();
		});
	}

	/**
	 * The page opened with the space free, then a fence put up where the level II goes before the click: the click is
	 * refused with what is in the way and where, and nothing is ordered (the click checks again).
	 */
	//$ gametest_ticks_batch BIG '100' '"qaUpgradeLateFence"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaUpgradeLateFence")
	public void qaAFenceRaisedAfterThePageOpenedStopsTheClick(GameTestHelper helper) {
		Village v = village(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = WonderFinishedGameTests.listener(helper, seen, "QaLateFence");
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = upgradesPage(helper, player);
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(Items.BRICKS), "setup: not free when opened");
			BlockPos corner = new BlockPos(3, ADDED_Y, 3);
			helper.setBlock(corner, Blocks.OAK_FENCE);
			seen.clear();
			menu.press(VillageHallScreen.FIRST_ROW, player);
			BlockPos at = helper.absolutePos(corner);
			String in = Component.translatable("screen.aliveworkplace.upgrades.blocked", Blocks.OAK_FENCE.getName(), at.getX(), at.getY(), at.getZ()).getString();
			String says = Component.translatable("message.aliveworkplace.upgrades.blocked", Blueprints.displayName(HUT_2), Component.literal(in)).getString();
			helper.assertTrue(last(seen).equals(says), "the click said '" + last(seen) + "', expected '" + says + "'");
			helper.assertTrue(upgradeSite(v).isEmpty() && !ModAttachments.BUILDER_JOB.has(v.builder()), "ordered over the fence");
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(Items.BARRIER), "the row after: " + menu.icon(VillageHallScreen.FIRST_ROW));
			helper.assertBlockPresent(Blocks.OAK_FENCE, corner);
			helper.succeed();
		});
	}

	/**
	 * One block of ground where the level II goes: the row says the Builder clears 1 block, in the singular; the click
	 * names Bob, the new level and the old ("Bob will build the Test Hut II over the Test Hut"), and the chronicle notes it.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaUpgradeOneToClear"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaUpgradeOneToClear")
	public void qaOneBlockToClearIsSaidInTheSingular(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(new BlockPos(4, ADDED_Y, 4), Blocks.DIRT);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = WonderFinishedGameTests.listener(helper, seen, "QaOneClear");
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = upgradesPage(helper, player);
			ItemStack row = menu.icon(VillageHallScreen.FIRST_ROW);
			String one = Component.translatable("screen.aliveworkplace.upgrades.free_clear.one", 1).getString();
			helper.assertTrue(one.equals("The space is free: the Builder clears 1 block of ground or plants"), "the singular reads: " + one);
			helper.assertTrue(row.is(Items.BRICKS) && lore(row).contains(one), "the row (" + one + "): " + lore(row));
			seen.clear();
			menu.press(VillageHallScreen.FIRST_ROW, player);
			String says = "Bob will build the " + Blueprints.displayName(HUT_2).getString() + " over the " + Blueprints.displayName(HUT).getString();
			helper.assertTrue(last(seen).equals(says), "the click said '" + last(seen) + "', expected '" + says + "'");
			helper.assertTrue(upgradeSite(v).map(s -> s.structure().equals(HUT_2) && v.builder().getUUID().equals(s.builder())).orElse(false), "not ordered for Bob");
			VillageHallBlockEntity hall = (VillageHallBlockEntity) v.level().getBlockEntity(v.hall());
			helper.assertTrue(hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.PLANS && e.text().getString().contains("Bob")
				&& e.text().getString().contains(Blueprints.displayName(HUT_2).getString())), "the chronicle doesn't note the order: "
				+ hall.chronicle().stream().map(e -> e.text().getString()).toList());
			helper.succeed();
		});
	}

	/** The tab counts: "1 build can go up a level", then with a second hut "2 builds"; a hut outside the village isn't counted or listed. */
	//$ gametest_ticks_batch BIG '100' '"qaUpgradeTabCount"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaUpgradeTabCount")
	public void qaTheTabCountsTheVillagesBuilds(GameTestHelper helper) {
		Village v = village(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		helper.runAfterDelay(2, () -> {
			int tab = HallPages.slot(UpgradesPage.PAGE);
			ChoiceMenu menu = VillageHallScreen.forTest(player, v.hall());
			String one = Component.translatable("screen.aliveworkplace.upgrades.count.one", 1).getString();
			helper.assertTrue(one.equals("1 build can go up a level"), "the singular reads: " + one);
			helper.assertTrue(lore(menu.icon(tab)).contains(one), "the tab with one: " + lore(menu.icon(tab)));
			// a second hut in the village, and one far outside it (40 blocks from the hall, the village's radius 16)
			sites.recordFinished(HUT, placement(helper, new BlockPos(3, 2, 14)), UUID.randomUUID());
			BlueprintData.Placement far = new BlueprintData.Placement(v.level().dimension().location(), v.hall().offset(40, 0, 0), Rotation.NONE, Mirror.NONE);
			sites.recordFinished(HUT, far, UUID.randomUUID());
			Leftovers.after(helper, () -> sites.forgetFinished(far));
			ChoiceMenu again = VillageHallScreen.forTest(player, v.hall());
			String two = Component.translatable("screen.aliveworkplace.upgrades.count", 2).getString();
			helper.assertTrue(two.equals("2 builds can go up a level"), "the plural reads: " + two);
			helper.assertTrue(lore(again.icon(tab)).contains(two), "the tab with two in the village and one outside: " + lore(again.icon(tab)));
			List<UpgradesPage.Upgrade> listed = UpgradesPage.upgrades(v.level(), v.hall());
			helper.assertTrue(listed.size() == 2 && listed.stream().noneMatch(u -> u.built().placement().equals(far)), "listed: "
				+ listed.stream().map(u -> u.built().placement().origin()).toList());
			helper.succeed();
		});
	}

	// =================================================================================================================
	// 36.16 Villagers live much longer, and a death matters
	// =================================================================================================================

	private static final BlockPos L_HALL = new BlockPos(11, 2, 11);

	/** A Village Hall (radius 16) whose needs are met; the switches as they were after. */
	private static void lifeVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Mourning.ENABLED = true;
			VillageNeeds.forget();
			Moods.forget();
		});
		helper.setBlock(L_HALL, ModBlocks.VILLAGE_HALL);
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(L_HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		return hall;
	}

	private static Villager person(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		return v;
	}

	private static Families.Parents parents(String mother, String father) {
		return new Families.Parents(Component.literal(mother), Component.literal(father), "", "", true);
	}

	/**
	 * "A message to players near the village": a player at the village's edge (16 blocks from the hall, its radius) is
	 * told; one a block further out is not.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaMourningWhoIsTold"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaMourningWhoIsTold")
	public void qaOnlyPlayersInTheVillageAreTold(GameTestHelper helper) {
		lifeVillage(helper);
		Villager mira = person(helper, new BlockPos(5, 2, 6), "Mira", VillagerProfession.FARMER);
		List<Component> near = new ArrayList<>();
		List<Component> far = new ArrayList<>();
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			hall(helper);
			ServerPlayer inside = WonderFinishedGameTests.listener(helper, near, "QaEdge");
			ServerPlayer outside = WonderFinishedGameTests.listener(helper, far, "QaBeyond");
			BlockPos h = helper.absolutePos(L_HALL);
			inside.teleportTo(h.getX() + 16 + 0.5, h.getY(), h.getZ() + 0.5);
			outside.teleportTo(h.getX() + 17 + 0.5, h.getY(), h.getZ() + 0.5);
			LifeStages.setAdultSince(mira, Chronicle.day(level) - 10);
			near.clear();
			far.clear();
			mira.kill();
			helper.assertTrue(near.stream().anyMatch(c -> c.getString().startsWith("Mira, the village's Farmer, has died aged ")),
				"the player at the village's edge wasn't told: " + near.stream().map(Component::getString).toList());
			helper.assertTrue(far.stream().noneMatch(c -> c.getString().contains("The village mourns")),
				"a player outside the village was told: " + far.stream().map(Component::getString).toList());
			helper.succeed();
		});
	}

	/** Years: 18 when grown, still 18 a day short of four seasons, 19 on the day; the message and the chronicle say the same. */
	//$ gametest_ticks_batch BIG '100' '"qaMourningYears"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaMourningYears")
	public void qaYearsTurnOverAfterExactlyFourSeasons(GameTestHelper helper) {
		lifeVillage(helper);
		Villager ida = person(helper, new BlockPos(5, 2, 6), "Ida", VillagerProfession.LIBRARIAN);
		Villager tam = person(helper, new BlockPos(8, 2, 6), "Tam", VillagerProfession.MASON);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = hall(helper);
			long today = Chronicle.day(level);
			int year = Mourning.yearDays();
			helper.assertTrue(year > 1, "setup: a year of " + year + " days");
			LifeStages.setAdultSince(ida, today - (year - 1));
			LifeStages.setAdultSince(tam, today - year);
			helper.assertTrue(Mourning.told(ida, today).getString().equals("Ida, the village's Librarian, has died aged 18. The village mourns."),
				"a day short of a year: " + Mourning.told(ida, today).getString());
			helper.assertTrue(Mourning.told(tam, today).getString().equals("Tam, the village's Mason, has died aged 19. The village mourns."),
				"a year on: " + Mourning.told(tam, today).getString());
			// the elder age: 1200 grown days
			LifeStages.setAdultSince(ida, today - LifeStages.ELDER_DAYS);
			helper.assertTrue(Mourning.years(ida, today) == Mourning.GROWN_UP_AGE + LifeStages.ELDER_DAYS / year, "an elder's years: " + Mourning.years(ida, today));
			tam.kill();
			helper.assertTrue(hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.DEATH && e.text().getString().startsWith("Tam")
				&& e.text().getString().endsWith(": the village's Mason, aged 19")), "chronicle: " + hall.chronicle().stream().map(e -> e.text().getString()).toList());
			helper.succeed();
		});
	}

	/**
	 * "Lower mood/work for their partner and family": Mira dies; her mother Ann, her half-brother Pip (Ann's son by
	 * another father) and her daughter Kit grieve and work slower; Odo, of another family, does not.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaMourningFamily"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaMourningFamily")
	public void qaTheWholeFamilyGrievesAndStrangersDoNot(GameTestHelper helper) {
		lifeVillage(helper);
		Villager mira = person(helper, new BlockPos(5, 2, 6), "Mira", VillagerProfession.FARMER);
		Villager ann = person(helper, new BlockPos(7, 2, 6), "Ann", VillagerProfession.CLERIC);
		Villager pip = person(helper, new BlockPos(9, 2, 6), "Pip", VillagerProfession.FLETCHER);
		Villager kit = person(helper, new BlockPos(5, 2, 8), "Kit", VillagerProfession.SHEPHERD);
		Villager odo = person(helper, new BlockPos(7, 2, 8), "Odo", VillagerProfession.MASON);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			hall(helper);
			long today = Chronicle.day(level);
			ModAttachments.PARENTS.set(mira, parents("Ann", "Bob"));
			ModAttachments.PARENTS.set(pip, parents("Ann", "Carl"));
			ModAttachments.PARENTS.set(kit, parents("Mira", "Tom"));
			ModAttachments.PARENTS.set(odo, parents("Dot", "Ed"));
			for (Villager v : List.of(mira, ann, pip, kit, odo)) {
				LifeStages.setAdultSince(v, today - 30);
			}
			mira.kill();
			for (Villager v : List.of(ann, pip, kit)) {
				String name = v.getName().getString();
				helper.assertTrue(Mourning.grieving(v, today), name + " doesn't grieve Mira");
				helper.assertTrue(Mourning.pace(v) == Mourning.PACE, name + " works at " + Mourning.pace(v));
				Mourning.Grief grief = ModAttachments.GRIEF.get(v);
				helper.assertTrue(grief != null && grief.name().getString().equals("Mira"), name + " grieves " + grief);
			}
			helper.assertTrue(!Mourning.grieving(odo, today) && Mourning.pace(odo) == 1f, "Odo, of another family, grieves");
			helper.succeed();
		});
	}

	/**
	 * "A few days", ended by the hall's own round: grief a day short of its days stays, on its last day it comes off; a
	 * widow mourns on the day before {@link Couples#MOURNING_DAYS} and not on it.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaMourningEnds"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaMourningEnds")
	public void qaGriefEndsOnTheRightDayInTheHallsRound(GameTestHelper helper) {
		lifeVillage(helper);
		Villager pip = person(helper, new BlockPos(5, 2, 6), "Pip", VillagerProfession.FLETCHER);
		Villager tom = person(helper, new BlockPos(8, 2, 6), "Tom", VillagerProfession.FARMER);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			hall(helper);
			long today = Chronicle.day(level);
			helper.assertTrue(Mourning.GRIEF_DAYS >= 2 && Mourning.GRIEF_DAYS <= 7 && Couples.MOURNING_DAYS >= 2 && Couples.MOURNING_DAYS <= 7,
				"not 'a few days': grief " + Mourning.GRIEF_DAYS + ", mourning " + Couples.MOURNING_DAYS);
			LifeStages.setAdultSince(pip, today - 30);
			LifeStages.setAdultSince(tom, today - 30);
			BlockPos hallPos = helper.absolutePos(L_HALL);
			ModAttachments.GRIEF.set(pip, new Mourning.Grief(today - (Mourning.GRIEF_DAYS - 1), Component.literal("Mira")));
			LifeStages.round(level, hallPos, List.of(pip));
			helper.assertTrue(ModAttachments.GRIEF.has(pip) && Mourning.pace(pip) == Mourning.PACE, "grief ended a day early");
			ModAttachments.GRIEF.set(pip, new Mourning.Grief(today - Mourning.GRIEF_DAYS, Component.literal("Mira")));
			LifeStages.round(level, hallPos, List.of(pip));
			helper.assertTrue(!ModAttachments.GRIEF.has(pip) && Mourning.pace(pip) == 1f, "grief still on after its days");
			ModAttachments.WIDOWED_DAY.set(tom, today - (Couples.MOURNING_DAYS - 1));
			helper.assertTrue(Mourning.mourning(level, tom) && Mourning.pace(tom) == Mourning.PACE, "Tom stopped mourning a day early");
			ModAttachments.WIDOWED_DAY.set(tom, today - Couples.MOURNING_DAYS);
			helper.assertTrue(!Mourning.mourning(level, tom) && Mourning.pace(tom) == 1f, "Tom still mourns after " + Couples.MOURNING_DAYS + " days");
			helper.succeed();
		});
	}

	// =================================================================================================================
	// 36.17 A job block with several jobs gives one at random
	// =================================================================================================================

	private static String job(VillagerProfession p) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(p).getPath();
	}

	/** A random whose every nextDouble is {@code value}. */
	private static RandomSource fixed(double value) {
		return new LegacyRandomSource(1L) {
			@Override
			public double nextDouble() {
				return value;
			}
		};
	}

	/**
	 * The draw in play leans to what the village lacks: by a composter stand four farmers, four florists and four
	 * composters and no orchard keeper. Twenty jobless villagers take the composter by themselves: each gets one of its
	 * four jobs, and the Orchard Keeper comes up more often than any other.
	 */
	//$ gametest_batch BIG '"qaRandomJobsLean"'
	@GameTest(template = BIG, batch = "qaRandomJobsLean")
	public void qaTheVillagesWorkersTiltTheDraw(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		BlockPos station = new BlockPos(3, 2, 3);
		helper.setBlock(station, Blocks.COMPOSTER);
		Stations.Station composter = Stations.at(Blocks.COMPOSTER).orElseThrow();
		List<VillagerProfession> have = List.of(VillagerProfession.FARMER, ModVillagers.FLORIST, ModVillagers.COMPOSTER);
		for (int i = 0; i < 12; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(14 + i % 4, 2, 14 + i / 4));
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().setProfession(have.get(i % 3)).setLevel(2));
		}
		Map<VillagerProfession, Integer> took = new HashMap<>();
		for (int i = 0; i < 20; i++) {
			Villager v = RandomJobsGameTests.takesByThemselves(helper, station, new BlockPos(5 + i % 6, 2, 5 + i / 6), 3617_000L + i);
			VillagerProfession p = v.getVillagerData().getProfession();
			helper.assertTrue(composter.has(p), "a villager at the composter became a " + job(p));
			took.merge(p, 1, Integer::sum);
		}
		int keepers = took.getOrDefault(ModVillagers.ORCHARD_KEEPER, 0);
		for (VillagerProfession p : have) {
			helper.assertTrue(keepers > took.getOrDefault(p, 0), "the missing Orchard Keeper came up " + keepers + " times, the " + job(p) + " "
				+ took.getOrDefault(p, 0) + ": " + took);
		}
		helper.succeed();
	}

	/**
	 * The weights at the edges of the random: 1 / (1 + n). Farmer with none in the village (weight 1) and Florist with one
	 * (1/2): a draw below 2/3 of the way is the Farmer, above it the Florist; 0 is the first job, just under 1 the last;
	 * a list of one is always that one.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheWeightsAtTheEdgesOfTheRandom(GameTestHelper helper) {
		List<VillagerProfession> two = List.of(VillagerProfession.FARMER, ModVillagers.FLORIST);
		Map<VillagerProfession, Integer> counts = Map.of(ModVillagers.FLORIST, 1);
		helper.assertTrue(RandomJobs.pick(two, counts, fixed(0.66)) == VillagerProfession.FARMER, "0.66 of weights 1 and 1/2 should be the Farmer");
		helper.assertTrue(RandomJobs.pick(two, counts, fixed(0.67)) == ModVillagers.FLORIST, "0.67 of weights 1 and 1/2 should be the Florist");
		List<VillagerProfession> four = List.of(VillagerProfession.FARMER, ModVillagers.ORCHARD_KEEPER, ModVillagers.FLORIST, ModVillagers.COMPOSTER);
		helper.assertTrue(RandomJobs.pick(four, Map.of(), fixed(0.0)) == VillagerProfession.FARMER, "a draw of 0");
		helper.assertTrue(RandomJobs.pick(four, Map.of(), fixed(Math.nextDown(1.0))) == ModVillagers.COMPOSTER, "a draw just under 1");
		// evenly: each quarter of the draw one job
		helper.assertTrue(RandomJobs.pick(four, Map.of(), fixed(0.26)) == ModVillagers.ORCHARD_KEEPER
			&& RandomJobs.pick(four, Map.of(), fixed(0.51)) == ModVillagers.FLORIST, "an even split");
		// a job of 3 (weight 1/4) against one of none: a quarter over a whole, so 0.79 is still the first and 0.81 the second
		Map<VillagerProfession, Integer> three = Map.of(ModVillagers.FLORIST, 3);
		helper.assertTrue(RandomJobs.pick(two, three, fixed(0.79)) == VillagerProfession.FARMER && RandomJobs.pick(two, three, fixed(0.81)) == ModVillagers.FLORIST,
			"weights 1 and 1/4");
		helper.assertTrue(RandomJobs.pick(List.of(VillagerProfession.FARMER), Map.of(VillagerProfession.FARMER, 50), fixed(0.99)) == VillagerProfession.FARMER,
			"a list of one");
		helper.succeed();
	}

	/** The village's count leaves out the villager drawing, the dead and those doing other jobs. */
	//$ gametest_batch BIG '"qaRandomJobsCounts"'
	@GameTest(template = BIG, batch = "qaRandomJobsCounts")
	public void qaTheCountLeavesOutTheDeadAndTheDrawer(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<VillagerProfession> jobs = List.of(VillagerProfession.FARMER, ModVillagers.FLORIST);
		Villager self = person(helper, new BlockPos(4, 2, 4), "Self", VillagerProfession.FARMER);
		Villager alive = person(helper, new BlockPos(6, 2, 4), "Alive", VillagerProfession.FARMER);
		Villager dead = person(helper, new BlockPos(8, 2, 4), "Dead", VillagerProfession.FARMER);
		person(helper, new BlockPos(10, 2, 4), "Mason", VillagerProfession.MASON);
		person(helper, new BlockPos(12, 2, 4), "Flo", ModVillagers.FLORIST);
		dead.kill();
		Map<VillagerProfession, Integer> counts = RandomJobs.counts(level, helper.absolutePos(new BlockPos(3, 2, 3)), self, jobs);
		helper.assertTrue(counts.getOrDefault(VillagerProfession.FARMER, 0) == 1 && counts.getOrDefault(ModVillagers.FLORIST, 0) == 1 && counts.size() == 2,
			"counted: " + counts + " (alive " + alive.isAlive() + ")");
		helper.succeed();
	}

	/**
	 * "A list of every block that offers several jobs in the wiki page": docs/wiki/random-jobs.md has a row for every
	 * block in the game with two or more jobs (each job named as the game names it: QaVillagerLifeJobsBugTests).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheWikiHasARowForEverySharedBlock(GameTestHelper helper) {
		Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		Path page = null;
		for (Path p = dir; p != null && page == null; p = p.getParent()) {
			if (Files.isRegularFile(p.resolve("docs/wiki/random-jobs.md"))) {
				page = p.resolve("docs/wiki/random-jobs.md");
			}
		}
		helper.assertTrue(page != null, "no docs/wiki/random-jobs.md above " + dir);
		List<String> rows;
		try {
			rows = Files.readAllLines(page).stream().filter(l -> l.startsWith("| ") && !l.startsWith("| Block") && !l.startsWith("|---")).toList();
		} catch (java.io.IOException e) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("can't read " + page + ": " + e);
		}
		List<String> missing = new ArrayList<>();
		int shared = 0;
		for (Stations.Station station : Stations.ALL) {
			if (station.jobs().size() < 2 || station.block() == Blocks.AIR) {
				continue;
			}
			String block = station.block().getName().getString();
			Optional<String> row = rows.stream().filter(r -> r.split("\\|")[1].trim().startsWith(block)).findFirst();
			if (row.isEmpty()) {
				missing.add(block + " (no row)");
				continue;
			}
			shared++;
		}
		helper.assertTrue(missing.isEmpty(), "the wiki's list of shared blocks misses " + missing);
		helper.assertTrue(shared >= 13, "only " + shared + " shared blocks checked");
		helper.succeed();
	}
}
