package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 41.3 with the real Waystones mod: between two villages whose waystones both stand, a trade caravan goes by
 * waystone (a short trip, out of the other village's stone) and settlers for a colony go through the stone of the village
 * nearest their spot; a stone broken by the time they step out, they walk the road as before; a trip by waystone
 * survives a save and reload, and older saves (no flag) walk.
 */
public class WaystoneTravelCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);
	/** The stone's spot in the plaza (lower half), as tools/blueprints/waystones.py draws it. */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);
	private static final String CARAVANS = "aliveworkplace_caravans";

	/** Two villages, Ashford and Brookmere, each with its finished plaza and a standing waystone. */
	private static void villages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		cleanUp(helper);
		// Each test here runs in a batch of its own: no other test's village with a standing stone (left by an earlier
		// batch, e.g. WaystonesCompatTests') may be the one settlers pick on the way to their spot.
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		UUID owner = UUID.randomUUID();
		for (Object[] v : new Object[][] {{HALL_A, "Ashford", PLAZA_A}, {HALL_B, "Brookmere", PLAZA_B}}) {
			helper.setBlock((BlockPos) v[0], ModBlocks.VILLAGE_HALL);
			VillageHallBlockEntity hall = helper.getBlockEntity((BlockPos) v[0]);
			hall.setCustomName(Component.literal((String) v[1]));
			hall.setOwner(owner, "Jesse");
			plaza(helper, (BlockPos) v[2]);
		}
		VillageWaystones.round(level, a(helper));
		VillageWaystones.round(level, b(helper));
		helper.assertTrue(VillageWaystones.linked(level, a(helper), b(helper)), "setup: the two stones don't stand (" + VillageWaystones.stoneOf(level, a(helper))
			+ ", " + VillageWaystones.stoneOf(level, b(helper)) + ")");
	}

	private static BlockPos a(GameTestHelper helper) {
		return helper.absolutePos(HALL_A);
	}

	private static BlockPos b(GameTestHelper helper) {
		return helper.absolutePos(HALL_B);
	}

	/** Sets every block of the plaza at {@code origin} as a finished one stands, and records it finished. */
	private static void plaza(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildPlan plan = BuildPlan.create(blueprint, placement);
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		BuildSiteManager.get(level).recordFinished(VillageWaystones.PLAZA, placement, UUID.randomUUID());
	}

	private static BlockPos stone(GameTestHelper helper, BlockPos plaza) {
		return helper.absolutePos(plaza).offset(STONE);
	}

	private static void breakStone(GameTestHelper helper, BlockPos plaza) {
		BlockPos s = stone(helper, plaza);
		helper.getLevel().setBlock(s.above(), Blocks.AIR.defaultBlockState(), 3);
		helper.getLevel().setBlock(s, Blocks.AIR.defaultBlockState(), 3);
	}

	/** A Storehouse with a chest by each hall, Ashford's with 64 oak logs. */
	private static Caravans.Data trade(GameTestHelper helper) {
		helper.setBlock(new BlockPos(14, 2, 1), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(14, 2, 3), Blocks.CHEST);
		helper.setBlock(new BlockPos(24, 2, 14), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(24, 2, 16), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(14, 2, 3));
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		return Caravans.Data.get(helper.getLevel());
	}

	/** Brookmere waits for 32 oak logs, and Ashford has a route to it: set after the halls' first rounds, which write their own wants. */
	private static void route(GameTestHelper helper, Caravans.Data data) {
		data.setWants(a(helper), Component.literal("Ashford"), List.of());
		data.setWants(b(helper), Component.literal("Brookmere"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		helper.assertTrue(data.toggleRoute(a(helper), b(helper)), "setup: no route");
	}

	/** Today's caravan from Ashford: sent by Ashford's hall round. */
	private static Caravans.Shipment send(GameTestHelper helper, Caravans.Data data) {
		data.sent(a(helper), -1);
		Caravans.round(helper.getLevel(), a(helper), null);
		return data.onTheRoad().stream().filter(s -> s.from().equals(a(helper)) && s.to().equals(b(helper))).reduce((x, y) -> y)
			.orElseThrow(() -> new GameTestAssertException("no caravan left Ashford: " + data.onTheRoad()));
	}

	private static List<Caravans.Shipment> toBrookmere(GameTestHelper helper, Caravans.Data data) {
		return data.onTheRoad().stream().filter(s -> s.from().equals(a(helper)) && s.to().equals(b(helper))).toList();
	}

	/** Whether the chronicle of the hall at {@code hall} (in the test area) has a line {@code key}. */
	private static boolean said(GameTestHelper helper, BlockPos hall, String key) {
		VillageHallBlockEntity entity = helper.getBlockEntity(hall);
		return entity.chronicle().stream().anyMatch(e -> e.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}

	private static Component line(GameTestHelper helper, BlockPos hall, String key) {
		VillageHallBlockEntity entity = helper.getBlockEntity(hall);
		return entity.chronicle().stream().map(Chronicle.Entry::text).filter(t -> t.getContents() instanceof TranslatableContents c && c.getKey().equals(key))
			.findFirst().orElseThrow(() -> new GameTestAssertException("no '" + key + "' in the chronicle"));
	}

	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Caravans.Data data = Caravans.Data.get(level);
		for (BlockPos at : List.of(HALL_A, HALL_B)) {
			BlockPos hall = helper.absolutePos(at);
			data.remove(hall);
			VillageWaystones.Data.get(level).forget(hall);
		}
		RealmData realms = RealmData.get(level.getServer());
		realms.removeOrder(GlobalPos.of(level.dimension(), a(helper)));
		for (BlockPos plaza : List.of(PLAZA_A, PLAZA_B)) { // (else a later test by the same spot finds a plaza built)
			BuildSiteManager.get(level).forgetFinished(new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(plaza), Rotation.NONE, Mirror.NONE));
		}
	}

	/**
	 * A caravan through the waystones: Ashford's hall round sends it by waystone (the chronicle says so), arriving in half
	 * the shortest trip; when it's due, Brookmere's round unloads it, out of the stone ("stepped out of the waystone").
	 */
	//$ gametest_ticks_batch AREA '100' '"waystoneCaravanThrough"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "waystoneCaravanThrough")
	public void aCaravanGoesThroughTheWaystones(GameTestHelper helper) {
		villages(helper);
		Caravans.Data data = trade(helper);
		helper.runAfterDelay(2, () -> { // (the Storehouses' points of interest are filed a moment after they're placed)
			ServerLevel level = helper.getLevel();
			try {
				route(helper, data);
				long now = level.getGameTime();
				Caravans.Shipment s = send(helper, data);
				helper.assertTrue(s.byWaystone() && !s.byTrain(), "the caravan doesn't go by waystone: " + s);
				long trip = Caravans.trip(level, a(helper), b(helper), data);
				helper.assertTrue(s.arrives() - now == Caravans.MIN_TRAVEL / 2 && Caravans.MIN_TRAVEL / 2 < trip,
					"by waystone it takes " + (s.arrives() - now) + " ticks, not " + Caravans.MIN_TRAVEL / 2 + " (the road: " + trip + ")");
				helper.assertTrue(line(helper, HALL_A, "chronicle.aliveworkplace.waystone_left").getString().equals("A caravan left for Brookmere by waystone with 32 × Oak Log"),
					"Ashford's chronicle: " + line(helper, HALL_A, "chronicle.aliveworkplace.waystone_left").getString());

				data.hurry(b(helper)); // its time is up
				helper.assertTrue(toBrookmere(helper, data).stream().allMatch(Caravans.Shipment::byWaystone), "hurried, it stopped going by waystone");
				Caravans.round(level, b(helper), null);
				helper.assertTrue(toBrookmere(helper, data).isEmpty(), "the caravan is still on the road: " + toBrookmere(helper, data));
				helper.assertTrue(line(helper, HALL_B, "chronicle.aliveworkplace.waystone_came").getString().equals("A caravan from Ashford stepped out of the waystone with 32 × Oak Log"),
					"Brookmere's chronicle: " + line(helper, HALL_B, "chronicle.aliveworkplace.waystone_came").getString());
				helper.assertFalse(said(helper, HALL_B, "chronicle.aliveworkplace.waystone_walked"), "it walked with both stones standing");
			} finally {
				cleanUp(helper);
			}
			helper.succeed();
		});
	}

	/**
	 * Brookmere's stone broken mid-trip: when the caravan is due it can't step out there, so it walks the whole road from
	 * then (the chronicle says so) and arrives as a walking caravan; the next caravan, with the stone still broken, walks.
	 */
	//$ gametest_ticks_batch AREA '100' '"waystoneCaravanBroken"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "waystoneCaravanBroken")
	public void aStoneBrokenMidTripTheCaravanWalks(GameTestHelper helper) {
		villages(helper);
		Caravans.Data data = trade(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			try {
				route(helper, data);
				Caravans.Shipment s = send(helper, data);
				helper.assertTrue(s.byWaystone(), "setup: the caravan doesn't go by waystone: " + s);
				breakStone(helper, PLAZA_B);
				helper.assertFalse(VillageWaystones.linked(level, a(helper), b(helper)), "a broken stone still links the villages");
				data.hurry(b(helper));
				long now = level.getGameTime();
				Caravans.round(level, b(helper), null);
				List<Caravans.Shipment> road = toBrookmere(helper, data);
				long trip = Caravans.trip(level, a(helper), b(helper), data);
				helper.assertTrue(road.size() == 1 && !road.get(0).byWaystone() && road.get(0).arrives() == now + trip,
					"the caravan should walk the road (" + trip + " ticks from now): " + road);
				helper.assertTrue(road.get(0).goods().stream().mapToInt(ItemStack::getCount).sum() == 32, "it lost goods on the way: " + road.get(0).goods());
				helper.assertTrue(line(helper, HALL_B, "chronicle.aliveworkplace.waystone_walked").getString()
						.equals("The caravan from Ashford found a waystone broken and is walking the road instead"),
					"Brookmere's chronicle: " + line(helper, HALL_B, "chronicle.aliveworkplace.waystone_walked").getString());
				helper.assertFalse(said(helper, HALL_B, "chronicle.aliveworkplace.waystone_came"), "it stepped out of a broken stone");

				data.hurry(b(helper)); // walked all the way
				Caravans.round(level, b(helper), null);
				helper.assertTrue(toBrookmere(helper, data).isEmpty(), "the walking caravan never came: " + toBrookmere(helper, data));
				helper.assertTrue(said(helper, HALL_B, "chronicle.aliveworkplace.caravan_came"), "no walking caravan came to Brookmere");

				// The next caravan, the stone still broken, walks from the start.
				data.setWants(b(helper), Component.literal("Brookmere"), List.of(new Caravans.Want(Items.OAK_LOG, 8)));
				long then = level.getGameTime();
				Caravans.Shipment next = send(helper, data);
				helper.assertTrue(!next.byWaystone() && next.arrives() - then == Caravans.trip(level, a(helper), b(helper), data),
					"with a stone broken the caravan should walk: " + next);
			} finally {
				cleanUp(helper);
			}
			helper.succeed();
		});
	}

	/**
	 * A caravan by waystone saved and loaded mid-trip still goes by waystone and steps out at Brookmere; a caravan saved by
	 * an older version (no flag) walks. Settlers' orders keep the village they go through, and an older order has none.
	 */
	//$ gametest_ticks_batch AREA '100' '"waystoneCaravanReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "waystoneCaravanReload")
	public void aTripByWaystoneSurvivesASaveAndReload(GameTestHelper helper) {
		villages(helper);
		trade(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			try {
				route(helper, Caravans.Data.get(level));
				Caravans.Shipment s = send(helper, Caravans.Data.get(level));
				CompoundTag saved = Caravans.Data.get(level).save(new CompoundTag(), level.registryAccess());
				Caravans.Data loaded = Caravans.Data.load(saved, level.registryAccess());
				level.getDataStorage().set(CARAVANS, loaded); // the server restarted mid-trip
				Caravans.Data data = Caravans.Data.get(level);
				helper.assertTrue(data == loaded, "setup: the reloaded data isn't the level's");
				Caravans.Shipment back = toBrookmere(helper, data).stream().findFirst().orElseThrow(() -> new GameTestAssertException("the caravan is gone after a reload"));
				helper.assertTrue(back.byWaystone() && back.arrives() == s.arrives(), "after a reload: " + back + ", before: " + s);

				// An older save: the same caravan without the flag walks.
				CompoundTag old = saved.copy();
				ListTag road = old.getList("road", Tag.TAG_COMPOUND);
				for (int i = 0; i < road.size(); i++) {
					road.getCompound(i).remove("byWaystone");
				}
				helper.assertTrue(Caravans.Data.load(old, level.registryAccess()).onTheRoad().stream().noneMatch(Caravans.Shipment::byWaystone),
					"an older save's caravan goes by waystone");

				data.hurry(b(helper));
				Caravans.round(level, b(helper), null);
				helper.assertTrue(toBrookmere(helper, data).isEmpty() && said(helper, HALL_B, "chronicle.aliveworkplace.waystone_came"),
					"the reloaded caravan didn't step out at Brookmere: " + toBrookmere(helper, data));

				// The settlers' order keeps where it goes through; an older order has nowhere.
				RealmData.Order order = new RealmData.Order(GlobalPos.of(level.dimension(), a(helper)), b(helper).offset(300, 0, 300), Optional.of(Component.literal("Newbrook")),
					RealmData.ON_ROAD, 10, 5000, 0, 1, List.of(), Optional.empty(), Map.of(), List.of(), false, Optional.of(b(helper)));
				Tag tag = RealmData.Order.CODEC.encodeStart(NbtOps.INSTANCE, order).getOrThrow();
				RealmData.Order reloaded = RealmData.Order.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
				helper.assertTrue(reloaded.equals(order) && reloaded.via().equals(Optional.of(b(helper))), "the order after a reload: " + reloaded);
				((CompoundTag) tag).remove("via");
				helper.assertTrue(RealmData.Order.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().via().isEmpty(), "an older order goes by waystone");
				helper.assertTrue(Settlers.byWaystone(level, reloaded) && Settlers.stepOut(level, reloaded) == reloaded,
					"the reloaded settlers don't step out of Brookmere's stone");
			} finally {
				cleanUp(helper);
			}
			helper.succeed();
		});
	}

	/**
	 * Settlers through the waystones: two settlers called to Ashford's hall for a colony beyond Brookmere set out by
	 * waystone (the bell, then gone through Ashford's stone), their journey counted only from Brookmere's stone; with both
	 * stones standing they step out there when it's up. Brookmere's stone broken mid-trip, they walk the whole road, and
	 * Ashford's chronicle says so.
	 */
	//$ gametest_ticks_batch AREA '200' '"waystoneSettlers"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "waystoneSettlers")
	public void settlersGoThroughTheWaystonesAndWalkWhenOneIsBroken(GameTestHelper helper) {
		villages(helper);
		ServerLevel level = helper.getLevel();
		Villager dara = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 3));
		Villager tomas = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 3));
		GlobalPos mother = GlobalPos.of(level.dimension(), a(helper));
		BlockPos spot = b(helper).offset(300, 0, 300);
		RealmData realms = RealmData.get(level.getServer());
		realms.putOrder(new RealmData.Order(mother, spot, Optional.of(Component.literal("Newbrook")), RealmData.MUSTER, level.getGameTime(), 0, 0,
			level.getGameTime(), List.of(dara.getUUID(), tomas.getUUID()), Optional.of(dara.getUUID()), Map.of(), List.of()));
		helper.onEachTick(() -> {
			RealmData.Order order = realms.orders().stream().filter(o -> o.mother().equals(mother)).findFirst().orElse(null);
			if (order == null || !order.state().equals(RealmData.ON_ROAD)) {
				return; // the bell, then the walk to the stone
			}
			try {
				helper.assertTrue(order.via().equals(Optional.of(b(helper))), "the settlers don't go through Brookmere's stone: " + order.via());
				helper.assertTrue(order.settlers().size() == 2 && !dara.isAlive() && !tomas.isAlive(), "the settlers didn't go: " + order.settlers().size());
				long byStone = Settlers.JOURNEY_TICKS + Math.round(Math.hypot(stone(helper, PLAZA_B).getX() - spot.getX(), stone(helper, PLAZA_B).getZ() - spot.getZ()));
				long left = order.arrives() - level.getGameTime();
				helper.assertTrue(left <= byStone && left >= byStone - 2 && byStone < Settlers.journey(order),
					"the journey by waystone: " + left + " ticks left, expected " + byStone + " (walking: " + Settlers.journey(order) + ")");
				helper.assertTrue(Settlers.stepOut(level, order) == order, "with both stones standing the settlers didn't step out at Brookmere");

				breakStone(helper, PLAZA_B);
				long now = level.getGameTime();
				RealmData.Order walking = Settlers.stepOut(level, order);
				helper.assertTrue(walking.via().isEmpty() && walking.state().equals(RealmData.ON_ROAD) && walking.arrives() == now + Settlers.journey(order)
					&& walking.settlers().size() == 2, "with Brookmere's stone broken they should walk the whole road: " + walking.via() + ", arrives in " + (walking.arrives() - now));
				helper.assertTrue(line(helper, HALL_A, "chronicle.aliveworkplace.settlers_walked").getString()
						.equals("The settlers for Newbrook found a waystone broken and are walking the road instead"),
					"Ashford's chronicle: " + line(helper, HALL_A, "chronicle.aliveworkplace.settlers_walked").getString());
				helper.assertTrue(Settlers.via(level, order).isEmpty(), "settlers would go through a broken stone");
			} finally {
				cleanUp(helper);
			}
			helper.succeed();
		});
	}
}
