package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
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
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA for ROADMAP 41.1 to 41.3 (lane d, 2026-11-07), written from the spec and docs/wiki/waystones.md with the real
 * Waystones mod, for what the earlier waystone classes leave out: the other village leaving the network while settlers are on the road to its stone (saved and loaded
 * on the way), where travelers stand to step in at the two-block edge and on a step (from both sides), and the
 * network file read from an older or damaged save.
 */
public class QaWaystones1107dCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);
	/** The stone's spot in the plaza (lower half), as tools/blueprints/waystones.py draws it. */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);

	// ---- two villages with their plazas (as QaWaystoneTravel1102dCompatTests sets them up) ----------------------------

	private static void villages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		cleanUp(helper);
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
		helper.assertTrue(VillageWaystones.linked(level, a(helper), b(helper)), "setup: the two stones don't stand");
	}

	private static BlockPos a(GameTestHelper helper) {
		return helper.absolutePos(HALL_A);
	}

	private static BlockPos b(GameTestHelper helper) {
		return helper.absolutePos(HALL_B);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	private static void plaza(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BlueprintData.Placement placement = placement(helper, origin);
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

	private static boolean standsAt(GameTestHelper helper, BlockPos plaza) {
		return VillageWaystones.isStone(helper.getLevel().getBlockState(stone(helper, plaza)));
	}

	private static void breakStone(GameTestHelper helper, BlockPos plaza) {
		BlockPos s = stone(helper, plaza);
		helper.getLevel().setBlock(s.above(), Blocks.AIR.defaultBlockState(), 3);
		helper.getLevel().setBlock(s, Blocks.AIR.defaultBlockState(), 3);
	}

	private static Optional<Chronicle.Entry> said(GameTestHelper helper, BlockPos hall, String key) {
		VillageHallBlockEntity entity = helper.getBlockEntity(hall);
		return entity.chronicle().stream().filter(e -> e.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key)).findFirst();
	}

	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos at : List.of(HALL_A, HALL_B)) {
			VillageWaystones.Data.get(level).forget(helper.absolutePos(at));
		}
		RealmData.get(level.getServer()).removeOrder(GlobalPos.of(level.dimension(), a(helper)));
		for (BlockPos plaza : List.of(PLAZA_A, PLAZA_B)) {
			BuildSiteManager.get(level).forgetFinished(placement(helper, plaza));
		}
	}

	private static void start(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : manager.finishedNear(level, a(helper), 160)) {
			if (VillageWaystones.isPlaza(f.structure())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (io.github.jcondedata.aliveworkplace.build.BuildSite site : List.copyOf(manager.all())) {
			if (VillageWaystones.isPlaza(site.structure()) && site.placement().origin().closerThan(a(helper), 160)) {
				manager.remove(site.id());
			}
		}
		villages(helper);
	}

	/** An order from Ashford for Newbrook at {@code spot}, in {@code state}, going through {@code via}. */
	private static RealmData.Order order(GameTestHelper helper, BlockPos spot, String state, Optional<BlockPos> via) {
		ServerLevel level = helper.getLevel();
		return new RealmData.Order(GlobalPos.of(level.dimension(), a(helper)), spot, Optional.of(Component.literal("Newbrook")), state,
			level.getGameTime(), level.getGameTime() + 5000, 0, level.getGameTime(), List.of(), Optional.empty(), Map.of(), List.of(), false, via);
	}

	// ---- the tests ---------------------------------------------------------------------------------------------------

	/**
	 * 41.3 "if either stone is missing ... they walk", "save and reload mid-trip": settlers on the road by Brookmere's stone
	 * (their order saved), Brookmere's whole plaza demolished while the world is off (stone broken, the finished build
	 * forgotten) and its hall round taking it off the network (41.2). Loaded again and their time up, they don't step out
	 * of a stone that isn't there: they walk the whole road from Ashford from now, the order no longer going through
	 * Brookmere, and Ashford's chronicle says so in words ("The settlers for Newbrook found a waystone broken and are
	 * walking the road instead").
	 */
	//$ gametest_batch AREA '"qaWaystones1107ViaGone"'
	@GameTest(template = AREA, batch = "qaWaystones1107ViaGone")
	public void settlersWhoseOtherVillageLeftTheNetworkOnTheWayWalkAfterAReload(GameTestHelper helper) {
		start(helper);
		ServerLevel level = helper.getLevel();
		try {
			RealmData.Order onRoad = order(helper, b(helper).offset(300, 0, 300), RealmData.ON_ROAD, Optional.of(b(helper)));
			helper.assertTrue(Settlers.byWaystone(level, onRoad), "setup: the settlers don't go by waystone");
			Tag saved = RealmData.Order.CODEC.encodeStart(NbtOps.INSTANCE, onRoad).getOrThrow();

			breakStone(helper, PLAZA_B);
			BuildSiteManager.get(level).forgetFinished(placement(helper, PLAZA_B));
			VillageWaystones.round(level, b(helper));
			helper.assertTrue(VillageWaystones.stoneOf(level, b(helper)).isEmpty(), "setup: Brookmere is still on the network with its plaza gone: "
				+ VillageWaystones.stoneOf(level, b(helper)));

			RealmData.Order loaded = RealmData.Order.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
			helper.assertTrue(loaded.via().equals(Optional.of(b(helper))), "setup: the order lost its way through after a reload: " + loaded);
			helper.assertFalse(Settlers.byWaystone(level, loaded), "settlers still go by a stone whose village left the network");
			long now = level.getGameTime();
			RealmData.Order after = Settlers.stepOut(level, loaded);
			helper.assertTrue(after.via().isEmpty(), "the settlers still go through Brookmere: " + after.via());
			long walking = Settlers.journey(after);
			helper.assertTrue(after.arrives() == now + walking, "the walk should take the whole road from Ashford (" + walking + " ticks from now): arrives "
				+ after.arrives() + ", now " + now);
			helper.assertTrue(after.state().equals(RealmData.ON_ROAD) && after.spot().equals(loaded.spot()), "the order changed more than its way: " + after);
			Chronicle.Entry entry = said(helper, HALL_A, "chronicle.aliveworkplace.settlers_walked")
				.orElseThrow(() -> new GameTestAssertException("Ashford's chronicle doesn't say the settlers walk"));
			String text = entry.text().getString();
			helper.assertTrue(text.equals("The settlers for Newbrook found a waystone broken and are walking the road instead"), "the chronicle reads: " + text);
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * 41.3 "walk to their stone": the spot travelers step in from is the nearest open one (footing, two blocks of room,
	 * no water) within two blocks, on the side toward where they're going. From both sides of each edge: a step one
	 * block up in the first ring is taken before the second ring, even when the second ring is nearer the way they go;
	 * with the first ring walled, the second ring's spot toward the east (then the west) is taken; a spot in water is
	 * passed over; with both rings walled (an open third ring doesn't count) they stand at the stone itself.
	 */
	//$ gametest_batch AREA '"qaWaystones1107Beside"'
	@GameTest(template = AREA, batch = "qaWaystones1107Beside")
	public void travelersStepInFromTheNearestRingAndNeverFromWater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos c = new BlockPos(15, 2, 15);
		BlockPos s = helper.absolutePos(c);
		BlockPos east = s.offset(8, 0, 0);
		BlockPos west = s.offset(-8, 0, 0);
		// a level floor of stone round the spot, three blocks out, and air above it
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				helper.setBlock(c.offset(dx, -1, dz), Blocks.STONE);
				for (int dy = 0; dy <= 3; dy++) {
					helper.setBlock(c.offset(dx, dy, dz), Blocks.AIR);
				}
			}
		}
		helper.setBlock(c, Blocks.STONE); // where the stone stands (beside never needs the waystone itself)
		helper.setBlock(c.above(), Blocks.STONE);
		// the first ring walled two high
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					helper.setBlock(c.offset(dx, 0, dz), Blocks.STONE);
					helper.setBlock(c.offset(dx, 1, dz), Blocks.STONE);
				}
			}
		}

		// a step: the east wall one block high there, room on top of it
		helper.setBlock(c.offset(1, 1, 0), Blocks.AIR);
		BlockPos at = VillageWaystones.beside(level, s, east);
		helper.assertTrue(at.equals(s.offset(1, 1, 0)), "the step one block up beside the stone isn't taken first: " + at.subtract(s));
		helper.setBlock(c.offset(1, 1, 0), Blocks.STONE);

		at = VillageWaystones.beside(level, s, east);
		helper.assertTrue(at.equals(s.offset(2, 0, 0)), "with the first ring walled, the second ring's spot toward the east isn't taken: " + at.subtract(s));
		at = VillageWaystones.beside(level, s, west);
		helper.assertTrue(at.equals(s.offset(-2, 0, 0)), "with the first ring walled, the second ring's spot toward the west isn't taken: " + at.subtract(s));

		helper.setBlock(c.offset(2, 0, 0), Blocks.WATER);
		at = VillageWaystones.beside(level, s, east);
		BlockPos off = at.subtract(s);
		helper.assertFalse(off.equals(new BlockPos(2, 0, 0)), "travelers are sent to stand in water");
		helper.assertTrue(off.getX() == 2 && Math.abs(off.getZ()) == 1 && off.getY() == 0, "the nearest dry spot toward the east isn't taken: " + off);

		// the second ring walled too: only the third ring is open, and that is too far
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) {
					helper.setBlock(c.offset(dx, 0, dz), Blocks.STONE);
					helper.setBlock(c.offset(dx, 1, dz), Blocks.STONE);
				}
			}
		}
		at = VillageWaystones.beside(level, s, east);
		helper.assertTrue(at.equals(s), "with nothing open within two blocks they should stand at the stone, not " + at.subtract(s));
		helper.succeed();
	}

	/**
	 * 41.2's network file (`aliveworkplace_village_waystones`, "older worlds start empty"): a save from before 41.2 (no
	 * arrays) loads empty, and a damaged one whose two arrays differ in length keeps only the pairs it has, never a
	 * village with a made-up stone; saved again, it reads back the same.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theNetworkFileFromAnOlderOrDamagedSaveLoadsSafely(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageWaystones.Data old = VillageWaystones.Data.load(new CompoundTag(), level.registryAccess());
		helper.assertTrue(old.halls().isEmpty(), "an older save's network isn't empty: " + old.halls());

		BlockPos hall1 = new BlockPos(100, 64, 100);
		BlockPos hall2 = new BlockPos(-4000, 70, 2500);
		BlockPos stone1 = new BlockPos(103, 65, 103);
		CompoundTag tag = new CompoundTag();
		tag.putLongArray("halls", new long[] {hall1.asLong(), hall2.asLong()});
		tag.putLongArray("stones", new long[] {stone1.asLong()});
		VillageWaystones.Data damaged = VillageWaystones.Data.load(tag, level.registryAccess());
		helper.assertTrue(damaged.halls().equals(List.of(hall1)), "a damaged save's villages: " + damaged.halls());
		helper.assertTrue(damaged.stone(hall1).equals(Optional.of(stone1)), "the first village's stone: " + damaged.stone(hall1));
		helper.assertTrue(damaged.stone(hall2).isEmpty(), "a village without a saved stone got one: " + damaged.stone(hall2));

		CompoundTag again = damaged.save(new CompoundTag(), level.registryAccess());
		VillageWaystones.Data reloaded = VillageWaystones.Data.load(again, level.registryAccess());
		helper.assertTrue(reloaded.halls().equals(List.of(hall1)) && reloaded.stone(hall1).equals(Optional.of(stone1)),
			"saved and loaded again: " + reloaded.halls() + " " + reloaded.stone(hall1));
		helper.succeed();
	}
}
