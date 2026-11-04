package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mail.DeliveryNoteItem;
import io.github.jcondedata.aliveworkplace.mail.Postmen;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1104-1254) of the landed fixes B819, B722, B784, B684 and B852, written from each bug's Expected and the
 * fix's description, covering what their own tests leave out:
 * <ul>
 * <li>B819: a Delivery Note whose filter named only a removed mod's item, through the player's own clicks (sneak-clear,
 * marking both chests, handing it to a Postman, the Postman's route list); the filter's last real item taken off
 * leaving only the gone one (still carries nothing) against a note whose only item is taken off (carries everything);
 * an id that isn't even a valid id; a Postman at work on a route mixing a gone item and oak logs.</li>
 * <li>B722: a loaded hauler pushed into water two below its banks with one climbable step: it gets out ("or gets out")
 * and stores the load.</li>
 * <li>B784: the two-wide lava trench a block below the ground met at its corner, hugged from right beside it, and
 * through a save and reload mid-detour: never in lava, on fire or hurt.</li>
 * <li>B684: a Storehouse's chest 4 blocks above or below it is in the settlers' reach, 5 is not (both sides).</li>
 * <li>B852: an older save's look at unlisted beds lasts until the list is first saved: beds the look found stay wagon
 * beds, a bedroll placed after the save, or put back where one stood, is a home.</li>
 * </ul>
 */
public class QaVillageVerify1104c4GameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final int TOP = QaHaulerWaterVerify1102GameTests.TOP;
	/** An item of a mod taken out of the pack. */
	private static final String GONE = "create:brass_ingot";

	// ------------------------------------------------------------------------------------------ B819

	/** {@code route} as it loads from a save in which its filter's ids were {@code ids} (gone ones included). */
	private static RouteData loadedWith(GameTestHelper helper, RouteData route, String... ids) {
		ItemStack note = new ItemStack(ModItems.DELIVERY_NOTE);
		note.set(ModComponents.ROUTE, route);
		var registries = helper.getLevel().registryAccess();
		CompoundTag saved = (CompoundTag) note.save(registries);
		CompoundTag tag = saved.getCompound("components").getCompound("aliveworkplace:route");
		ListTag list = new ListTag();
		for (String id : ids) {
			list.add(StringTag.valueOf(id));
		}
		tag.put("filter", list);
		RouteData back = ItemStack.parseOptional(registries, saved).get(ModComponents.ROUTE);
		helper.assertTrue(back != null, "the note lost its route on loading");
		return back;
	}

	private static List<String> savedFilter(GameTestHelper helper, ItemStack note) {
		CompoundTag saved = (CompoundTag) note.save(helper.getLevel().registryAccess());
		ListTag list = saved.getCompound("components").getCompound("aliveworkplace:route").getList("filter", Tag.TAG_STRING);
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			ids.add(list.getString(i));
		}
		return ids;
	}

	private static List<String> tooltip(GameTestHelper helper, ItemStack note) {
		return note.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
	}

	private static boolean carriesAnything(RouteData r) {
		for (Item item : List.of(Items.COBBLESTONE, Items.DIAMOND, Items.OAK_LOG, Items.WHEAT, Items.COPPER_INGOT, Items.BREAD)) {
			if (r.carries(new ItemStack(item, 5))) {
				return true;
			}
		}
		return false;
	}

	private static String last(List<Component> seen) {
		return seen.isEmpty() ? "(nothing)" : seen.get(seen.size() - 1).getString();
	}

	private static Villager postman(GameTestHelper helper, BlockPos desk, BlockPos at) {
		helper.setBlock(desk, ModBlocks.POSTAL_DESK);
		Villager postman = helper.spawn(EntityType.VILLAGER, at);
		Jobs.employ(helper.getLevel(), postman, helper.absolutePos(desk), ModVillagers.POSTAL_DESK_POI, ModVillagers.POSTMAN);
		return postman;
	}

	/**
	 * A note that carried only Create's brass, loaded without Create, used the way a player uses it: sneak-clicking a
	 * chest clears the containers ("Delivery Note cleared.") but not the kinds; marking two chests makes a full route
	 * that still carries nothing; handed to a Postman his route carries nothing, his route list says "1 item no longer in
	 * the game" (never "everything"), and the gone id is still on the route after the Postman is saved and loaded.
	 */
	//$ gametest_batch BIG '"qa1104c4NoteClicks"'
	@GameTest(template = BIG, batch = "qa1104c4NoteClicks")
	public void aGoneOnlyNoteThroughEveryClickStillCarriesNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		BlockPos from = new BlockPos(4, 2, 4);
		BlockPos to = new BlockPos(9, 2, 4);
		helper.setBlock(from, Blocks.CHEST);
		helper.setBlock(to, Blocks.BARREL);
		BlockPos desk = new BlockPos(2, 2, 8);
		Villager postman = postman(helper, desk, new BlockPos(4, 2, 8));
		ItemStack note = new ItemStack(ModItems.DELIVERY_NOTE);
		note.set(ModComponents.ROUTE, loadedWith(helper, new RouteData(Optional.of(helper.absolutePos(to)), Optional.of(helper.absolutePos(from)),
			List.of(Items.COPPER_INGOT)), GONE));
		helper.assertTrue(DeliveryNoteItem.data(note).gone().equals(List.of(GONE)) && !carriesAnything(DeliveryNoteItem.data(note)),
			"setup: the note didn't load as a gone-only filter: " + DeliveryNoteItem.data(note));
		player.setItemInHand(InteractionHand.MAIN_HAND, note);

		player.setShiftKeyDown(true);
		QaStageThreeGameTests.click(helper, player, from);
		player.setShiftKeyDown(false);
		RouteData cleared = DeliveryNoteItem.data(player.getMainHandItem());
		helper.assertTrue(cleared.from().isEmpty() && cleared.to().isEmpty(), "sneak-click didn't clear the containers: " + cleared);
		helper.assertTrue(last(seen).equals("Delivery Note cleared."), "sneak-click said \"" + last(seen) + "\"");
		helper.assertTrue(cleared.gone().equals(List.of(GONE)) && !cleared.carriesEverything() && !carriesAnything(cleared),
			"after the sneak-click the note carries something (or lost the gone kind): " + cleared);

		QaStageThreeGameTests.click(helper, player, from);
		QaStageThreeGameTests.click(helper, player, to);
		RouteData marked = DeliveryNoteItem.data(player.getMainHandItem());
		helper.assertTrue(marked.isComplete() && marked.from().equals(Optional.of(helper.absolutePos(from)))
			&& marked.to().equals(Optional.of(helper.absolutePos(to))), "the two clicks didn't mark the route: " + marked);
		helper.assertTrue(!marked.carriesEverything() && !carriesAnything(marked) && marked.gone().equals(List.of(GONE)),
			"a route marked on a gone-only note carries items: " + marked);
		helper.assertTrue(savedFilter(helper, player.getMainHandItem()).equals(List.of(GONE)), "the note saves its filter as "
			+ savedFilter(helper, player.getMainHandItem()));
		List<String> tip = tooltip(helper, player.getMainHandItem());
		helper.assertTrue(tip.contains("Carries only: 1 item no longer in the game") && tip.stream().noneMatch(s -> s.contains("everything")),
			"the marked note's tooltip: " + tip);

		var result = Postmen.assign(player, postman, player.getMainHandItem());
		helper.assertTrue(result.consumesAction(), "the Postman didn't take the note: " + result + ", said \"" + last(seen) + "\"");
		List<RouteData> routes = Postmen.routes(postman);
		helper.assertTrue(routes.size() == 1 && !carriesAnything(routes.get(0)) && !routes.get(0).carriesEverything(),
			"the Postman's route carries items: " + routes);
		seen.clear();
		Postmen.sendStatus(player, postman);
		String status = last(seen);
		helper.assertTrue(status.contains(": 1 item no longer in the game") && !status.contains("everything"), "the Postman's route list: " + status);

		CompoundTag tag = postman.saveWithoutId(new CompoundTag());
		Villager back = EntityType.VILLAGER.create(helper.getLevel());
		back.load(tag);
		List<RouteData> loaded = ModAttachments.COURIER_ROUTES.get(back);
		helper.assertTrue(loaded != null && loaded.equals(routes) && !carriesAnything(loaded.get(0)), "after a save the Postman's route is " + loaded);
		helper.succeed();
	}

	/**
	 * The filter's boundary from both sides: a note carrying wheat and a gone item, wheat taken off with wheat in the
	 * other hand ("Stop carrying"), still carries nothing and says "Carries only: 1 item no longer in the game"; a note
	 * that carried only wheat, wheat taken off, carries everything but tools again (no filter at all).
	 */
	//$ gametest_batch BIG '"qa1104c4NoteToggle"'
	@GameTest(template = BIG, batch = "qa1104c4NoteToggle")
	public void takingTheLastRealKindOffLeavesAGoneOnlyFilterNotEverything(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		RouteData ends = new RouteData(Optional.of(new BlockPos(1, 2, 1)), Optional.of(new BlockPos(5, 2, 1)), List.of(Items.COPPER_INGOT));
		ItemStack mixed = new ItemStack(ModItems.DELIVERY_NOTE);
		mixed.set(ModComponents.ROUTE, loadedWith(helper, ends, "minecraft:wheat", GONE));
		helper.assertTrue(DeliveryNoteItem.data(mixed).carries(new ItemStack(Items.WHEAT)) && !DeliveryNoteItem.data(mixed).carries(new ItemStack(Items.COBBLESTONE)),
			"setup: the mixed note: " + DeliveryNoteItem.data(mixed));
		player.setItemInHand(InteractionHand.MAIN_HAND, mixed);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WHEAT, 3));
		QaStageThreeGameTests.clickAir(helper, player);
		RouteData off = DeliveryNoteItem.data(player.getMainHandItem());
		helper.assertTrue(off.filter().isEmpty() && off.gone().equals(List.of(GONE)), "taking wheat off left " + off + "; said \"" + last(seen) + "\"");
		helper.assertTrue(!off.carriesEverything() && !carriesAnything(off), "with only the gone kind left the note carries items: " + off);
		List<String> tip = tooltip(helper, player.getMainHandItem());
		helper.assertTrue(tip.contains("Carries only: 1 item no longer in the game") && tip.stream().noneMatch(s -> s.contains("everything")),
			"the tooltip with only the gone kind left: " + tip);
		helper.assertTrue(savedFilter(helper, player.getMainHandItem()).equals(List.of(GONE)), "saved as " + savedFilter(helper, player.getMainHandItem()));

		ItemStack wheatOnly = new ItemStack(ModItems.DELIVERY_NOTE);
		wheatOnly.set(ModComponents.ROUTE, new RouteData(ends.from(), ends.to(), List.of(Items.WHEAT)));
		player.setItemInHand(InteractionHand.MAIN_HAND, wheatOnly);
		QaStageThreeGameTests.clickAir(helper, player);
		RouteData none = DeliveryNoteItem.data(player.getMainHandItem());
		helper.assertTrue(none.carriesEverything() && none.carries(new ItemStack(Items.COBBLESTONE)) && !none.carries(new ItemStack(Items.IRON_SWORD)),
			"a note whose only (real) kind was taken off should carry everything but tools again: " + none);
		helper.assertTrue(tooltip(helper, player.getMainHandItem()).contains("Carries everything but tools, weapons and armor"),
			"its tooltip: " + tooltip(helper, player.getMainHandItem()));
		helper.succeed();
	}

	/**
	 * A filter id that isn't even a valid id (a hand-edited or corrupt save) counts like a gone item: the route carries
	 * nothing, keeps the id through two saves, and two such ids read "2 items no longer in the game".
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void anInvalidFilterIdIsAGoneItemNotNoFilter(GameTestHelper helper) {
		String bad = "Create:Brass Ingot!";
		RouteData ends = new RouteData(Optional.of(new BlockPos(1, 2, 1)), Optional.of(new BlockPos(5, 2, 1)), List.of(Items.COPPER_INGOT));
		RouteData once = loadedWith(helper, ends, bad);
		helper.assertTrue(!once.carriesEverything() && !carriesAnything(once), "a route filtered only to an invalid id carries items: " + once);
		ItemStack note = new ItemStack(ModItems.DELIVERY_NOTE);
		note.set(ModComponents.ROUTE, once);
		RouteData twice = QaStageThreeGameTests.saveAndLoad(helper, note, ModComponents.ROUTE);
		helper.assertTrue(twice != null && twice.equals(once) && savedFilter(helper, note).equals(List.of(bad)), "the invalid id didn't survive a save: "
			+ twice + ", saved " + savedFilter(helper, note));
		RouteData two = loadedWith(helper, ends, bad, GONE);
		helper.assertTrue(two.filterNames().getString().equals("2 items no longer in the game") && !carriesAnything(two),
			"two unknown ids: \"" + two.filterNames().getString() + "\", " + two);
		helper.succeed();
	}

	/**
	 * A Postman at work on one route whose filter names a gone item and oak logs: he carries all the logs over and leaves
	 * the cobblestone and diamonds beside them where they are.
	 */
	//$ gametest_ticks_batch BIG '1600' '"qa1104c4PostmanMixed"'
	@GameTest(template = BIG, timeoutTicks = 1600, batch = "qa1104c4PostmanMixed")
	public void aPostmanOnAMixedGoneRouteCarriesOnlyTheRealKind(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos from = new BlockPos(12, 2, 12);
		BlockPos to = new BlockPos(4, 2, 12);
		helper.setBlock(from, Blocks.CHEST);
		helper.setBlock(to, Blocks.CHEST);
		Container source = helper.getBlockEntity(from);
		source.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		source.setItem(1, new ItemStack(Items.OAK_LOG, 20));
		source.setItem(2, new ItemStack(Items.DIAMOND, 3));
		Villager postman = postman(helper, new BlockPos(2, 2, 2), new BlockPos(3, 2, 3));
		RouteData route = loadedWith(helper, new RouteData(Optional.of(helper.absolutePos(from)), Optional.of(helper.absolutePos(to)),
			List.of(Items.COPPER_INGOT)), GONE, "minecraft:oak_log");
		ModAttachments.COURIER_ROUTES.set(postman, List.of(route));
		Container target = helper.getBlockEntity(to);
		helper.succeedWhen(() -> {
			helper.assertTrue(target.countItem(Items.COBBLESTONE) == 0 && target.countItem(Items.DIAMOND) == 0,
				"the mixed route carried items it doesn't name: " + target.countItem(Items.COBBLESTONE) + " cobblestone, " + target.countItem(Items.DIAMOND) + " diamonds");
			helper.assertTrue(target.countItem(Items.OAK_LOG) == 20, "the logs aren't all over yet: " + target.countItem(Items.OAK_LOG) + " of 20");
			helper.assertTrue(source.countItem(Items.COBBLESTONE) == 64 && source.countItem(Items.DIAMOND) == 3, "the cobblestone or diamonds left the source");
		});
	}

	// ------------------------------------------------------------------------------------------ B722 / B784: haulers

	/**
	 * B722 "(or gets out)": a loaded hauler pushed into a pool whose water is two blocks below its banks, which it can't
	 * climb out of anywhere but one three-long step a block below the east bank. It gets out by the step and stores all
	 * 64 logs at the open Storehouse beyond, none carried, none dropped.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1104c4PoolStep"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1104c4PoolStep")
	public void aHaulerPushedIntoALowPoolLeavesByItsOnlyStep(GameTestHelper helper) {
		QaHaulerWaterVerify1102GameTests.setup(helper);
		for (int x = 7; x <= 17; x++) {
			for (int z = 7; z <= 17; z++) {
				QaHaulerWaterVerify1102GameTests.liquid(helper, x, z, 2, 2, Blocks.WATER);
			}
		}
		for (int z = 11; z <= 13; z++) {
			for (int y = 1; y <= TOP - 2; y++) {
				helper.setBlock(new BlockPos(17, y, z), Blocks.STONE); // the step: its top a block below the bank's
			}
		}
		helper.setBlock(new BlockPos(21, TOP, 12), ModBlocks.STOREHOUSE);
		BlockPos chest = new BlockPos(22, TOP, 12);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = QaHaulerWaterVerify1102GameTests.hauler(helper, new BlockPos(3, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		QaHaulerWaterVerify1102GameTests.put(helper, golem, new BlockPos(10, 2, 12));
		helper.runAfterDelay(5, () -> helper.assertTrue(golem.isInWater(), "setup: the hauler isn't in the pool: " + golem.position()));
		helper.succeedWhen(() -> QaHaulerWaterVerify1102GameTests.delivered(helper, golem, chest, Map.of(Items.OAK_LOG, 64),
			"pushed into water two below its banks with one step out"));
	}

	/**
	 * A loaded hauler going round a two-wide lava trench a block below the ground ({@code x} 12..13, z 7..17) from
	 * {@code start} to a Storehouse at {@code store}; reloaded at tick {@code reloadAt} (0: never). On no tick is it in
	 * lava, on fire or hurt, and all 64 logs are stored.
	 */
	static void trench(GameTestHelper helper, BlockPos start, BlockPos store, int reloadAt, String where) {
		QaHaulerWaterVerify1102GameTests.setup(helper);
		for (int x = 12; x <= 13; x++) {
			for (int z = 7; z <= 17; z++) {
				QaHaulerWaterVerify1102GameTests.liquid(helper, x, z, 1, 1, Blocks.LAVA);
			}
		}
		helper.setBlock(store, ModBlocks.STOREHOUSE);
		BlockPos chest = store.east();
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem[] golem = {QaHaulerWaterVerify1102GameTests.hauler(helper, start)};
		ModAttachments.HAULER_LOAD.set(golem[0], Map.of(Items.OAK_LOG, 64));
		float health = golem[0].getHealth();
		helper.onEachTick(() -> {
			if (reloadAt > 0 && helper.getTick() == reloadAt) {
				helper.assertTrue(QaHaulerWaterVerify1102GameTests.carried(golem[0]) == 64, "setup: the hauler stored its load before the reload");
				golem[0] = QaHaulerWaterVerify1102GameTests.reloaded(helper, golem[0]);
			}
			IronGolem g = golem[0];
			if (g.isInLava() || g.isOnFire() || g.getHealth() < health) {
				helper.fail(where + ": the hauler went into the lava: in lava " + g.isInLava() + ", on fire " + g.isOnFire() + ", health " + g.getHealth()
					+ " of " + health + " at " + helper.relativeVec(g.position()) + " on tick " + helper.getTick());
			}
		});
		helper.succeedWhen(() -> QaHaulerWaterVerify1102GameTests.delivered(helper, golem[0], chest, Map.of(Items.OAK_LOG, 64), where));
	}

	/** B784's trench, the Storehouse beyond its south end: the shortest way round turns its corner. */
	//$ gametest_ticks_batch HUGE '1600' '"qa1104c4LavaCorner"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1104c4LavaCorner")
	public void aHaulerTurningASunkenLavaTrenchsCornerNeverFallsIn(GameTestHelper helper) {
		trench(helper, new BlockPos(6, TOP - 1, 15), new BlockPos(19, TOP, 18), 0, "round the corner of a sunken two-wide lava trench");
	}

	//$ gametest_ticks_batch HUGE '1600' '"qa1104c4LavaCornerB"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1104c4LavaCornerB")
	public void aHaulerTurningASunkenLavaTrenchsCornerNeverFallsInCopy1(GameTestHelper helper) {
		trench(helper, new BlockPos(6, TOP - 1, 15), new BlockPos(19, TOP, 18), 0, "round the corner of a sunken two-wide lava trench (copy 1)");
	}

	/**
	 * The hauler starts two blocks of ground from the trench's edge, the nearest start that isn't right beside it (the
	 * start right beside it is QaVillageVerify1104c4BugTests'): it goes round and stores the load.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qa1104c4LavaNear"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1104c4LavaNear")
	public void aHaulerStartingTwoBlocksFromASunkenLavaTrenchGoesRound(GameTestHelper helper) {
		trench(helper, new BlockPos(9, TOP - 1, 12), new BlockPos(20, TOP, 12), 0, "starting two blocks from a sunken two-wide lava trench");
	}

	/** Saved and loaded again 30 ticks into its way round: the loaded golem still keeps out of the lava. */
	//$ gametest_ticks_batch HUGE '1600' '"qa1104c4LavaReload"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1104c4LavaReload")
	public void aHaulerReloadedOnItsWayRoundASunkenLavaTrenchNeverFallsIn(GameTestHelper helper) {
		trench(helper, new BlockPos(5, TOP - 1, 12), new BlockPos(20, TOP, 12), 30, "reloaded on its way round a sunken two-wide lava trench");
	}

	// ------------------------------------------------------------------------------------------ B684: the settlers' reach

	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	private static ItemStack[] allSupplies() {
		return new ItemStack[] {new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.BREAD, 32), new ItemStack(Items.TORCH, 16), new ItemStack(Items.GLASS_PANE, 12), new ItemStack(Items.RED_BED),
			new ItemStack(Items.RED_BED), new ItemStack(Items.RED_BED)};
	}

	/**
	 * A City (hall at {@link #HALL}, colonies on, two jobless and two builders, as QaColonySupplies1102GameTests sets
	 * one up) whose Storehouse stands at {@code storehouse} and whose only chest, with every supply, at {@code chest};
	 * the owner sends settlers with a charter. {@code reach}: whether the chest is in the Storehouse's reach, so every
	 * supply is taken, or none is and the chest keeps all of it.
	 */
	private static void departure(GameTestHelper helper, BlockPos storehouse, BlockPos chest, boolean reach) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		int delay = Settlers.MUSTER_DELAY;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		VillageHalls.RADIUS = 8;
		Settlers.MUSTER_DELAY = -1;
		RealmData.get(level.getServer()).clearColonies();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = enabled;
			Colonies.RANK = needed;
			VillageNeeds.CHECK_EVERY = every;
			VillageHalls.RADIUS = radius;
			Settlers.MUSTER_DELAY = delay;
			RealmData.get(level.getServer()).clearColonies();
			Caravans.Data.get(level).remove(hall);
		});
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.CITY);
		for (BlockPos p : List.of(new BlockPos(9, 2, 9), new BlockPos(9, 2, 13), new BlockPos(10, 2, 11), new BlockPos(13, 2, 13))) {
			Villager v = helper.spawn(EntityType.VILLAGER, p);
			v.setNoAi(true);
		}
		for (int y = 1; y < storehouse.getY(); y++) {
			helper.setBlock(new BlockPos(storehouse.getX(), y, storehouse.getZ()), Blocks.STONE);
		}
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		Container box = helper.getBlockEntity(chest);
		ItemStack[] all = allSupplies();
		for (int i = 0; i < all.length; i++) {
			box.setItem(i, all[i]);
		}
		ItemStack charter = ColonyCharterItem.of(level, hall);
		charter.set(ModComponents.COLONY_CHARTER, charter.get(ModComponents.COLONY_CHARTER).withSpot(hall.offset(400, 0, 0)));
		charter.set(DataComponents.CUSTOM_NAME, Component.literal("Newbrook"));
		player.setItemInHand(InteractionHand.MAIN_HAND, charter);
		player.gameMode.useItemOn(player, level, player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(hall), Direction.UP, hall, false));
		RealmData.Order order = RealmData.get(level.getServer()).orderOf(net.minecraft.core.GlobalPos.of(level.dimension(), hall));
		helper.assertTrue(order != null, "the charter gave no order");
		int dy = chest.getY() - storehouse.getY();
		if (reach) {
			helper.assertTrue(Settlers.missing(order).isEmpty() && box.isEmpty(), "a chest " + dy + " above the Storehouse is in its reach, but the settlers miss "
				+ Settlers.missing(order) + " and the chest still holds " + (box.isEmpty() ? "nothing" : "items"));
		} else {
			helper.assertTrue(Settlers.missing(order).size() == Settlers.Supply.values().length, "a chest " + dy
				+ " above the Storehouse is out of its reach, but the settlers took from it; missing only " + Settlers.missing(order));
			for (int i = 0; i < all.length; i++) {
				helper.assertTrue(ItemStack.matches(box.getItem(i), all[i]), "the out-of-reach chest's slot " + i + " changed: " + box.getItem(i));
			}
		}
		helper.succeed();
	}

	//$ gametest_ticks_batch BIG '100' '"qa1104c4ReachUp4"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa1104c4ReachUp4")
	public void aChestFourAboveTheStorehouseGivesTheSettlersEverySupply(GameTestHelper helper) {
		departure(helper, new BlockPos(13, 2, 9), new BlockPos(14, 6, 9), true);
	}

	//$ gametest_ticks_batch BIG '100' '"qa1104c4ReachUp5"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa1104c4ReachUp5")
	public void aChestFiveAboveTheStorehouseGivesTheSettlersNothing(GameTestHelper helper) {
		departure(helper, new BlockPos(13, 2, 9), new BlockPos(14, 7, 9), false);
	}

	//$ gametest_ticks_batch BIG '100' '"qa1104c4ReachDown4"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa1104c4ReachDown4")
	public void aChestFourBelowTheStorehouseGivesTheSettlersEverySupply(GameTestHelper helper) {
		departure(helper, new BlockPos(13, 7, 9), new BlockPos(14, 3, 9), true);
	}

	//$ gametest_ticks_batch BIG '100' '"qa1104c4ReachDown5"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa1104c4ReachDown5")
	public void aChestFiveBelowTheStorehouseGivesTheSettlersNothing(GameTestHelper helper) {
		departure(helper, new BlockPos(13, 7, 9), new BlockPos(14, 2, 9), false);
	}

	// ------------------------------------------------------------------------------------------ B852: wagon beds

	private static BlockPos bed(GameTestHelper helper, BlockPos foot, Block bed) {
		helper.setBlock(foot, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	/**
	 * An older save's first session (its list from before the list was complete): two brown bedrolls by a campfire, a hay
	 * bale and a Blueprint Table are wagon beds (the look). The list is then saved, as the server's autosave does: the
	 * look ends. Both stay wagon beds (listed, and through a reload of the list); a third brown bed put by the same marks
	 * after the save is a home; and the first bedroll swapped for a yellow bed and put back is a home (B852's "rightly").
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1104c4WagonSave"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1104c4WagonSave")
	public void theLookAtUnlistedBedsEndsWithTheListsFirstSave(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean noSave = level.noSave;
		level.noSave = true; // no autosave of its own in the middle: this test saves the list itself, once
		Leftovers.after(helper, () -> level.noSave = noSave);
		CampBeds.Data live = CampBeds.Data.get(level);
		Leftovers.after(helper, () -> level.getDataStorage().set(CampBeds.Data.NAME, live));
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(-2, 0, -2)), helper.absolutePos(new BlockPos(31, 12, 31)));
		Leftovers.after(helper, () -> CampBeds.Data.get(level).forget(box));
		CampBeds.Data old = CampBeds.Data.load(new CompoundTag(), level.registryAccess());
		helper.assertTrue(old.looks() && !old.complete(), "setup: an older save's list");
		level.getDataStorage().set(CampBeds.Data.NAME, old);
		helper.setBlock(new BlockPos(14, 2, 15), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(10, 2, 15), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(12, 2, 18), ModBlocks.BLUEPRINT_TABLE);
		BlockPos footA = new BlockPos(10, 2, 10);
		BlockPos a = bed(helper, footA, Blocks.BROWN_BED);
		BlockPos b = bed(helper, new BlockPos(13, 2, 10), Blocks.GREEN_BED);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(CampBeds.isWagonBed(level, a) && CampBeds.isWagonBed(level, b), "in the first session the bedrolls by a camp's marks aren't wagon beds: "
				+ CampBeds.isWagonBed(level, a) + ", " + CampBeds.isWagonBed(level, b));
			helper.assertTrue(old.has(a) && old.has(b), "the look didn't list the bedrolls it found");
			old.save(new CompoundTag(), level.registryAccess()); // what the autosave writes
			helper.assertFalse(old.looks(), "the list still looks at unlisted beds after it was saved");
			BlockPos c = bed(helper, new BlockPos(16, 2, 10), Blocks.BROWN_BED);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(CampBeds.isWagonBed(level, a) && CampBeds.isWagonBed(level, b), "after the save the listed bedrolls aren't wagon beds");
				CampBeds.Data reloaded = CampBeds.Data.load(old.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
				helper.assertTrue(reloaded.has(a) && reloaded.has(b) && !reloaded.looks(), "a reload of the saved list lost a bedroll or looks again");
				helper.assertFalse(CampBeds.isWagonBed(level, c), "a brown bed placed by the marks after the list was saved is a wagon bed, not a home");
				helper.assertFalse(old.has(c), "the bed placed after the save was listed");
				bed(helper, footA, Blocks.YELLOW_BED);
				helper.runAfterDelay(2, () -> {
					helper.assertFalse(CampBeds.isWagonBed(level, a), "a yellow bed on a bedroll's spot is a wagon bed");
					bed(helper, footA, Blocks.BROWN_BED);
					helper.runAfterDelay(2, () -> {
						helper.assertFalse(CampBeds.isWagonBed(level, a), "a brown bed put back after the save is a wagon bed (the look had ended)");
						helper.assertTrue(CampBeds.isWagonBed(level, b), "the untouched green bedroll stopped being a wagon bed");
						helper.succeed();
					});
				});
			});
		});
	}

	/**
	 * The other side: in the same first session with no save between, a bedroll swapped for a yellow bed and put back
	 * by the marks is a wagon bed again (the look still runs), and a new brown bed by the marks is one too.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1104c4WagonNoSave"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1104c4WagonNoSave")
	public void withoutASaveTheLookStillFindsABedrollPutBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean noSave = level.noSave;
		level.noSave = true;
		Leftovers.after(helper, () -> level.noSave = noSave);
		CampBeds.Data live = CampBeds.Data.get(level);
		Leftovers.after(helper, () -> level.getDataStorage().set(CampBeds.Data.NAME, live));
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(-2, 0, -2)), helper.absolutePos(new BlockPos(31, 12, 31)));
		Leftovers.after(helper, () -> CampBeds.Data.get(level).forget(box));
		CampBeds.Data old = CampBeds.Data.load(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(CampBeds.Data.NAME, old);
		helper.setBlock(new BlockPos(14, 2, 15), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(10, 2, 15), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(12, 2, 18), ModBlocks.BLUEPRINT_TABLE);
		BlockPos footA = new BlockPos(10, 2, 10);
		BlockPos a = bed(helper, footA, Blocks.BROWN_BED);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(CampBeds.isWagonBed(level, a), "control: the bedroll by the marks isn't a wagon bed");
			bed(helper, footA, Blocks.YELLOW_BED);
			helper.runAfterDelay(2, () -> {
				helper.assertFalse(CampBeds.isWagonBed(level, a), "a yellow bed on a bedroll's spot is a wagon bed");
				bed(helper, footA, Blocks.BROWN_BED);
				BlockPos c = bed(helper, new BlockPos(16, 2, 10), Blocks.BROWN_BED);
				helper.runAfterDelay(2, () -> {
					helper.assertTrue(old.looks(), "the list stopped looking without a save");
					helper.assertTrue(CampBeds.isWagonBed(level, a) && CampBeds.isWagonBed(level, c), "in the first session a bedroll put back, or a new one, by "
						+ "the marks isn't a wagon bed: " + CampBeds.isWagonBed(level, a) + ", " + CampBeds.isWagonBed(level, c));
					helper.succeed();
				});
			});
		});
	}
}
