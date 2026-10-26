package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (Milestone 41, Waystones) with the real Waystones mod, written from the ROADMAP text of 41.1 to 41.3: the switch
 * turned off after the stones stand (caravans and settlers walk, the stones stay), the village renamed through the
 * player's own name tag with a long, odd name and back to its made-up name, the Map's distance and direction rendered
 * for a village due north and one south-east, two villages where only one has a stone (they walk), the network file
 * through a real save and reload, and a village whose hall is gone (nobody travels through its stone).
 */
public class WaystoneQaCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";
	/** The stone's spot in the plaza (lower half), as tools/blueprints/waystones.py draws it. */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);
	private static final String NETWORK = "aliveworkplace_village_waystones";

	// The two-village layout the travel tests use: Ashford in the north-west corner, Brookmere in the south-east.
	static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);

	static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at, String name, UUID owner) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(at);
		if (name != null) {
			hall.setCustomName(Component.literal(name));
		}
		hall.setOwner(owner, "Jesse");
		ServerLevel level = helper.getLevel();
		RealmData.get(level.getServer()).putKnown(new RealmData.Known(GlobalPos.of(level.dimension(), helper.absolutePos(at)),
			name == null ? VillageHalls.name(level, helper.absolutePos(at)) : Component.literal(name), Optional.of(owner), "Jesse", "hamlet", 0, 0, level.getGameTime()));
		return hall;
	}

	/** Sets every block of the plaza at {@code origin} as a finished one stands, and records it finished. */
	static void plaza(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BuildPlan plan = BuildPlan.create(blueprint, placement(helper, origin));
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		BuildSiteManager.get(level).recordFinished(VillageWaystones.PLAZA, placement(helper, origin), UUID.randomUUID());
	}

	static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	static BlockPos stone(GameTestHelper helper, BlockPos plaza) {
		return helper.absolutePos(plaza).offset(STONE);
	}

	static Optional<String> nameAt(GameTestHelper helper, BlockPos plaza) {
		return VillageWaystones.EXTENSION.call(s -> s.nameAt(helper.getLevel(), stone(helper, plaza)), Optional.<String>empty());
	}

	static boolean standsAt(GameTestHelper helper, BlockPos plaza) {
		return VillageWaystones.EXTENSION.call(s -> s.standing(helper.getLevel(), stone(helper, plaza)), false);
	}

	static void breakStone(GameTestHelper helper, BlockPos plaza) {
		BlockPos s = stone(helper, plaza);
		helper.getLevel().setBlock(s.above(), Blocks.AIR.defaultBlockState(), 3);
		helper.getLevel().setBlock(s, Blocks.AIR.defaultBlockState(), 3);
	}

	/** Ashford and Brookmere, each with its finished plaza and standing stone (when {@code stoneAtB}). */
	static void villages(GameTestHelper helper, boolean stoneAtB) {
		ServerLevel level = helper.getLevel();
		// (each test runs in a batch of its own: villages other batches left with a standing stone mustn't be picked)
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		UUID owner = UUID.randomUUID();
		hall(helper, HALL_A, "Ashford", owner);
		hall(helper, HALL_B, "Brookmere", owner);
		plaza(helper, PLAZA_A);
		if (stoneAtB) {
			plaza(helper, PLAZA_B);
		}
		VillageWaystones.round(level, helper.absolutePos(HALL_A));
		VillageWaystones.round(level, helper.absolutePos(HALL_B));
		helper.assertTrue(VillageWaystones.standing(level, helper.absolutePos(HALL_A)), "setup: Ashford's stone doesn't stand");
		helper.assertTrue(VillageWaystones.standing(level, helper.absolutePos(HALL_B)) == stoneAtB, "setup: Brookmere's stone standing: " + !stoneAtB);
	}

	/** A Storehouse with a chest by each hall, Ashford's with 64 oak logs (as the travel tests lay them out). */
	private static Caravans.Data trade(GameTestHelper helper) {
		helper.setBlock(new BlockPos(14, 2, 1), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(14, 2, 3), Blocks.CHEST);
		helper.setBlock(new BlockPos(24, 2, 14), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(24, 2, 16), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(14, 2, 3));
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		return Caravans.Data.get(helper.getLevel());
	}

	private static void route(GameTestHelper helper, Caravans.Data data, int logs) {
		data.setWants(helper.absolutePos(HALL_A), Component.literal("Ashford"), List.of());
		data.setWants(helper.absolutePos(HALL_B), Component.literal("Brookmere"), List.of(new Caravans.Want(Items.OAK_LOG, logs)));
		if (!data.routesFrom(helper.absolutePos(HALL_A)).contains(helper.absolutePos(HALL_B))) {
			helper.assertTrue(data.toggleRoute(helper.absolutePos(HALL_A), helper.absolutePos(HALL_B)), "setup: no route");
		}
	}

	private static Caravans.Shipment send(GameTestHelper helper, Caravans.Data data) {
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		data.sent(a, -1);
		Caravans.round(helper.getLevel(), a, null);
		return data.onTheRoad().stream().filter(s -> s.from().equals(a) && s.to().equals(b)).reduce((x, y) -> y)
			.orElseThrow(() -> new GameTestAssertException("no caravan left Ashford: " + data.onTheRoad()));
	}

	private static List<Caravans.Shipment> toBrookmere(GameTestHelper helper, Caravans.Data data) {
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		return data.onTheRoad().stream().filter(s -> s.from().equals(a) && s.to().equals(b)).toList();
	}

	/** A colony order of Ashford's for a spot well beyond Brookmere, on the road. */
	private static RealmData.Order order(GameTestHelper helper, Optional<BlockPos> via) {
		ServerLevel level = helper.getLevel();
		return new RealmData.Order(GlobalPos.of(level.dimension(), helper.absolutePos(HALL_A)), helper.absolutePos(HALL_B).offset(300, 0, 300),
			Optional.of(Component.literal("Newbrook")), RealmData.ON_ROAD, 10, 5000, 0, 1, List.of(), Optional.empty(), Map.of(), List.of(), false, via);
	}

	static List<Component> mapLore(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		return menu.icon(VillageHallScreen.MAP).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
	}

	static List<String> texts(List<Component> lore, String keyPrefix) {
		return lore.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().startsWith(keyPrefix)).map(Component::getString).toList();
	}

	static void cleanUp(GameTestHelper helper, List<BlockPos> halls, List<BlockPos> plazas) {
		ServerLevel level = helper.getLevel();
		Caravans.Data data = Caravans.Data.get(level);
		RealmData realms = RealmData.get(level.getServer());
		for (BlockPos at : halls) {
			BlockPos hall = helper.absolutePos(at);
			data.remove(hall);
			VillageWaystones.Data.get(level).forget(hall);
			realms.forgetKnown(GlobalPos.of(level.dimension(), hall));
			realms.removeOrder(GlobalPos.of(level.dimension(), hall));
		}
		for (BlockPos plaza : plazas) {
			BuildSiteManager.get(level).forgetFinished(placement(helper, plaza));
		}
	}

	static void cleanUp(GameTestHelper helper) {
		cleanUp(helper, List.of(HALL_A, HALL_B), List.of(PLAZA_A, PLAZA_B));
	}

	/**
	 * {@code villageWaystones} turned off after both stones stand (41.1's switch, 41.3's "they walk as today"): the stones
	 * stay working waystones with the village's name, a new caravan walks the road, settlers don't go through, and a caravan
	 * that left by waystone before the switch went off walks the whole road when it's due, its goods all there.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWaystoneSwitchOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWaystoneSwitchOff")
	public void theSwitchTurnedOffAfterTheStonesStandTheyWalk(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		cleanUp(helper);
		villages(helper, true);
		Caravans.Data data = trade(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos a = helper.absolutePos(HALL_A);
			BlockPos b = helper.absolutePos(HALL_B);
			boolean was = VillageWaystones.ENABLED;
			try {
				route(helper, data, 32);
				Caravans.Shipment first = send(helper, data);
				helper.assertTrue(first.byWaystone(), "setup: with the switch on the caravan doesn't go by waystone: " + first);

				VillageWaystones.ENABLED = false;
				helper.assertTrue(standsAt(helper, PLAZA_A) && standsAt(helper, PLAZA_B), "the switch off took the standing stones away");
				VillageWaystones.round(level, a);
				helper.assertTrue(nameAt(helper, PLAZA_A).equals(Optional.of("Ashford")), "Ashford's stone after a round with the switch off: " + nameAt(helper, PLAZA_A));
				helper.assertFalse(VillageWaystones.linked(level, a, b), "the switch off, the villages are still linked by waystone");

				// The caravan that left by waystone is due: it walks the whole road now.
				data.hurry(b);
				long now = level.getGameTime();
				Caravans.round(level, b, null);
				List<Caravans.Shipment> road = toBrookmere(helper, data);
				long trip = Caravans.trip(level, a, b, data);
				helper.assertTrue(road.size() == 1 && !road.get(0).byWaystone() && road.get(0).arrives() == now + trip,
					"the caravan under way should walk the road (" + trip + " ticks) with the switch off: " + road);
				helper.assertTrue(road.get(0).goods().stream().mapToInt(ItemStack::getCount).sum() == 32, "goods lost: " + road.get(0).goods());
				data.hurry(b);
				Caravans.round(level, b, null);
				helper.assertTrue(toBrookmere(helper, data).isEmpty(), "the walking caravan never came: " + toBrookmere(helper, data));

				// A new caravan walks from the start.
				route(helper, data, 8);
				long then = level.getGameTime();
				Caravans.Shipment next = send(helper, data);
				helper.assertTrue(!next.byWaystone() && next.arrives() - then == Caravans.trip(level, a, b, data), "a new caravan with the switch off: " + next);

				// Settlers for a spot beyond Brookmere walk.
				helper.assertTrue(Settlers.via(level, order(helper, Optional.empty())).isEmpty(), "settlers go by waystone with the switch off");
				helper.assertFalse(Settlers.byWaystone(level, order(helper, Optional.of(b))), "an order kept from before the switch went off still goes by waystone");
				helper.assertTrue(standsAt(helper, PLAZA_A) && standsAt(helper, PLAZA_B), "the stones are gone at the end");
			} finally {
				VillageWaystones.ENABLED = was;
				cleanUp(helper);
			}
			helper.succeed();
		});
	}

	/**
	 * Two villages, only Ashford with a waystone (41.3: "if either stone is missing ... they walk as today"): its caravan to
	 * Brookmere walks the road, and settlers for a spot beyond Brookmere don't go by waystone.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWaystoneOnlyOne"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWaystoneOnlyOne")
	public void twoVillagesOnlyOneWithAWaystoneWalk(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		cleanUp(helper);
		villages(helper, false);
		Caravans.Data data = trade(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos a = helper.absolutePos(HALL_A);
			BlockPos b = helper.absolutePos(HALL_B);
			try {
				helper.assertFalse(VillageWaystones.linked(level, a, b), "linked with only one stone");
				route(helper, data, 32);
				long now = level.getGameTime();
				Caravans.Shipment s = send(helper, data);
				helper.assertTrue(!s.byWaystone() && s.arrives() - now == Caravans.trip(level, a, b, data), "with one stone the caravan should walk: " + s);
				helper.assertTrue(Settlers.via(level, order(helper, Optional.empty())).isEmpty(), "settlers go by waystone to a village without one");
				helper.assertFalse(Settlers.byWaystone(level, order(helper, Optional.of(b))), "settlers go through a village without a stone");
			} finally {
				cleanUp(helper);
			}
			helper.succeed();
		});
	}

	/**
	 * A village whose hall is gone (its plaza and stone still standing) leaves Ashford's Map (41.2: "the player's
	 * villages"), which then lists Ashford alone, "Waystone network (1):".
	 */
	//$ gametest_batch AREA '"qaWaystoneHallGone"'
	@GameTest(template = AREA, batch = "qaWaystoneHallGone")
	public void aVillageWhoseHallIsGoneLeavesTheMap(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		cleanUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			villages(helper, true);
			BlockPos a = helper.absolutePos(HALL_A);
			BlockPos b = helper.absolutePos(HALL_B);
			helper.assertTrue(Settlers.via(level, order(helper, Optional.empty())).equals(Optional.of(b)), "setup: the settlers don't go through Brookmere: "
				+ Settlers.via(level, order(helper, Optional.empty())));
			helper.setBlock(HALL_B, Blocks.AIR); // Brookmere's hall broken; its plaza and stone stand
			helper.assertTrue(standsAt(helper, PLAZA_B), "setup: Brookmere's stone went with the hall");
			helper.assertTrue(VillageWaystones.network(level, a, player).stream().noneMatch(v -> v.hall().equals(b)), "Ashford's Map lists a village with no hall: "
				+ VillageWaystones.network(level, a, player));
			List<String> lines = texts(mapLore(player, a), "screen.aliveworkplace.hall.waystone");
			helper.assertTrue(lines.equals(List.of("Waystone network (1):", "Ashford, this village: not discovered yet")), "Ashford's Map: " + lines);
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * The village renamed through the player's own entry point, a name tag used on the hall (41.1: "renamed when the
	 * village is"): a long name with an accent, an apostrophe and an ampersand is carried exactly; with
	 * {@code villageWaystones} off a rename still reaches the standing stone; a hall with no name of its own gives the
	 * stone its made-up name, read as words (never a translation key).
	 */
	//$ gametest_batch AREA '"qaWaystoneRename"'
	@GameTest(template = AREA, batch = "qaWaystoneRename")
	public void aNameTagOnTheHallRenamesTheStoneEvenWithOddLongNames(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		cleanUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		boolean was = VillageWaystones.ENABLED;
		try {
			hall(helper, HALL_A, null, player.getUUID());
			plaza(helper, PLAZA_A);
			BlockPos a = helper.absolutePos(HALL_A);
			VillageWaystones.round(level, a);
			String madeUp = VillageHalls.madeUpName(a).getString();
			helper.assertTrue(!madeUp.isBlank() && !madeUp.contains("village_name") && nameAt(helper, PLAZA_A).equals(Optional.of(madeUp)),
				"an unnamed village's stone: " + nameAt(helper, PLAZA_A) + " (made-up name " + madeUp + ")");

			String odd = "Saint-Émile's Ford & the Old Bridge on Long Water"; // 49 characters: within the anvil's 50
			nameTag(helper, player, odd);
			helper.assertTrue(VillageHalls.name(level, a).getString().equals(odd), "setup: the name tag didn't rename the hall: " + VillageHalls.name(level, a).getString());
			helper.assertTrue(nameAt(helper, PLAZA_A).equals(Optional.of(odd)), "the stone after a name tag: " + nameAt(helper, PLAZA_A));
			helper.assertTrue(VillageWaystones.network(level, a, player).get(0).name().getString().equals(odd), "the Map's name: " + VillageWaystones.network(level, a, player));

			VillageWaystones.ENABLED = false;
			nameTag(helper, player, "Quietford");
			helper.assertTrue(nameAt(helper, PLAZA_A).equals(Optional.of("Quietford")), "with the switch off the standing stone kept its old name: " + nameAt(helper, PLAZA_A));
			VillageWaystones.ENABLED = was;

			VillageHallBlockEntity entity = helper.getBlockEntity(HALL_A);
			entity.setCustomName(null); // its own name taken away: the made-up one again
			helper.assertTrue(nameAt(helper, PLAZA_A).equals(Optional.of(madeUp)), "the stone after the hall's name was cleared: " + nameAt(helper, PLAZA_A));
		} finally {
			VillageWaystones.ENABLED = was;
			cleanUp(helper, List.of(HALL_A), List.of(PLAZA_A));
		}
		helper.succeed();
	}

	private static void nameTag(GameTestHelper helper, ServerPlayer player, String name) {
		ItemStack tag = new ItemStack(Items.NAME_TAG);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		player.setItemInHand(InteractionHand.MAIN_HAND, tag);
		BlockPos at = helper.absolutePos(HALL_A);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
		helper.getLevel().getBlockState(at).useItemOn(tag, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
	}

	/**
	 * The Map's distance and direction (41.2), rendered: Ashford's hall at the south edge, Brookmere's stone due north of it
	 * ("24 blocks north") and, from Brookmere, Ashford's stone 17 blocks south-east. Both lines in full, with the heading.
	 */
	//$ gametest_batch AREA '"qaWaystoneMapWords"'
	@GameTest(template = AREA, batch = "qaWaystoneMapWords")
	public void theMapSaysHowFarAndWhichWayInWords(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		BlockPos hallA = new BlockPos(3, 2, 27);
		BlockPos hallB = new BlockPos(3, 2, 8);
		BlockPos plazaA = new BlockPos(8, 2, 20);
		BlockPos plazaB = new BlockPos(0, 2, 0);
		List<BlockPos> halls = List.of(hallA, hallB);
		List<BlockPos> plazas = List.of(plazaA, plazaB);
		cleanUp(helper, halls, plazas);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			hall(helper, hallA, "Ashford", player.getUUID());
			hall(helper, hallB, "Brookmere", player.getUUID());
			plaza(helper, plazaA);
			plaza(helper, plazaB);
			BlockPos a = helper.absolutePos(hallA);
			BlockPos b = helper.absolutePos(hallB);
			VillageWaystones.round(level, a);
			VillageWaystones.round(level, b);
			helper.assertTrue(VillageWaystones.stoneOf(level, a).equals(Optional.of(stone(helper, plazaA))) && VillageWaystones.stoneOf(level, b).equals(Optional.of(stone(helper, plazaB))),
				"setup: the stones kept: " + VillageWaystones.stoneOf(level, a) + ", " + VillageWaystones.stoneOf(level, b));

			List<String> fromA = texts(mapLore(player, a), "screen.aliveworkplace.hall.waystone");
			helper.assertTrue(fromA.equals(List.of("Waystone network (2):", "Ashford, this village: not discovered yet", "Brookmere, 24 blocks north: not discovered yet")),
				"Ashford's Map: " + fromA);
			List<String> fromB = texts(mapLore(player, b), "screen.aliveworkplace.hall.waystone");
			helper.assertTrue(fromB.equals(List.of("Waystone network (2):", "Brookmere, this village: not discovered yet", "Ashford, 17 blocks south-east: not discovered yet")),
				"Brookmere's Map: " + fromB);
		} finally {
			cleanUp(helper, halls, plazas);
		}
		helper.succeed();
	}

	/**
	 * The network file through a real save and reload (the level's own saved data replaced by what was written): both
	 * villages are still linked and on the Map; a broken stone keeps its village listed as broken. (A village whose plaza is
	 * gone should leave at its next round; B528, its test is on tests/waystones-1026.)
	 */
	//$ gametest_batch AREA '"qaWaystoneNetworkReload"'
	@GameTest(template = AREA, batch = "qaWaystoneNetworkReload")
	public void theNetworkFileSurvivesASaveAndReloadAndKeepsABrokenStone(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		cleanUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			villages(helper, true);
			BlockPos a = helper.absolutePos(HALL_A);
			BlockPos b = helper.absolutePos(HALL_B);
			CompoundTag saved = VillageWaystones.Data.get(level).save(new CompoundTag(), level.registryAccess());
			level.getDataStorage().set(NETWORK, VillageWaystones.Data.load(saved, level.registryAccess()));
			helper.assertTrue(VillageWaystones.stoneOf(level, b).equals(Optional.of(stone(helper, PLAZA_B))), "Brookmere's stone after a reload: " + VillageWaystones.stoneOf(level, b));
			helper.assertTrue(VillageWaystones.linked(level, a, b), "the villages aren't linked after a reload");
			List<VillageWaystones.Village> network = VillageWaystones.network(level, a, player);
			helper.assertTrue(network.size() == 2 && network.get(1).hall().equals(b), "the network after a reload: " + network);

			breakStone(helper, PLAZA_B);
			VillageWaystones.round(level, b);
			network = VillageWaystones.network(level, a, player);
			helper.assertTrue(network.size() == 2 && network.get(1).state() == VillageWaystones.State.BROKEN, "a broken stone after a round: " + network);

		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/** Every sentence Milestone 41 added that the other tests don't render, with real arguments, in American English. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void milestone41SentencesReadRight(GameTestHelper helper) {
		Map<String, String> want = Map.of(
			"steward.aliveworkplace.why.waystone_plaza", "Travelers could find the village by a waystone",
			"steward.aliveworkplace.condition.waystones_offered", "Village waystones are on, and Waystones is installed",
			"blueprint.aliveworkplace.waystone_plaza", "Waystone Plaza",
			"blueprint.aliveworkplace.looks.snowy.waystone_plaza", "Waystone Plaza: Snowy",
			"aliveworkplace.config.villageWaystones", "Village Waystones");
		for (Map.Entry<String, String> e : want.entrySet()) {
			String got = Component.translatable(e.getKey()).getString();
			helper.assertTrue(got.equals(e.getValue()), e.getKey() + " reads '" + got + "'");
		}
		String more = Component.translatable("screen.aliveworkplace.hall.waystones_more", 2).getString();
		helper.assertTrue(more.equals("and 2 more"), "the count line: " + more);
		String broken = Component.translatable("screen.aliveworkplace.hall.waystone.broken", "Brookmere",
			Component.translatable("screen.aliveworkplace.hall.away", 120, Component.translatable("screen.aliveworkplace.hall.dir.nw"))).getString();
		helper.assertTrue(broken.equals("Brookmere, 120 blocks north-west: its waystone is broken"), "the broken line: " + broken);
		String tooltip = Component.translatable("aliveworkplace.config.villageWaystones.tooltip").getString();
		helper.assertTrue(tooltip.contains("stones already standing stay") && !tooltip.contains("villageWaystones."), "the switch's tooltip: " + tooltip);
		helper.succeed();
	}

	/**
	 * Where a caravan or settlers stand to step into the stone (41.3: "walk to their stone"): an open spot beside the
	 * plaza's stone, within two blocks of it, with footing and two blocks of room, never the stone's own spot, on the
	 * side toward the other village.
	 */
	//$ gametest_batch AREA '"qaWaystoneBeside"'
	@GameTest(template = AREA, batch = "qaWaystoneBeside")
	public void travelersStepInFromAnOpenSpotBesideTheStone(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		cleanUp(helper);
		ServerLevel level = helper.getLevel();
		try {
			villages(helper, true);
			BlockPos s = stone(helper, PLAZA_A);
			BlockPos toward = helper.absolutePos(HALL_B);
			BlockPos at = VillageWaystones.beside(level, s, toward);
			helper.assertFalse(at.equals(s), "the travelers stand on the stone itself: " + at);
			helper.assertTrue(Math.max(Math.abs(at.getX() - s.getX()), Math.abs(at.getZ() - s.getZ())) <= 2 && Math.abs(at.getY() - s.getY()) <= 1,
				"the spot " + at + " isn't beside the stone " + s);
			helper.assertTrue(level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty(),
				"no room to stand at " + at + ": " + level.getBlockState(at) + ", " + level.getBlockState(at.above()));
			helper.assertFalse(level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty(), "no footing under " + at + ": " + level.getBlockState(at.below()));
			helper.assertTrue(at.distSqr(toward) < s.distSqr(toward), "the spot " + at + " isn't on Brookmere's side of the stone " + s);
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}
}
