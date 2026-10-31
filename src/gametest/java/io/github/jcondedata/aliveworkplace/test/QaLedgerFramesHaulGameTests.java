package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildEntities;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b-1027-0938) on three landed, unverified fixes in the builders' and Storehouse area, written from their
 * ROADMAP text rather than the code:
 * <ul>
 * <li>B575 (with 27.19): "a block a player places or breaks within a hall's area marks its 16x16x16 section; no plan,
 * road or wall goes through a marked section". The fix's tests mark the ledger by hand; these mark it the player's way
 * (a block broken, a block placed through its item) near a real Village Hall, which is also the path's goal.</li>
 * <li>B439: "the frame hangs where the blueprint hangs it in every turn and mirror". QaTurnedFramesGameTests sweeps the
 * starter builds; this sweeps every blueprint of the mod that hangs a frame or painting, starter or not (workplaces,
 * Wonders, Legends' builds, sold builds).</li>
 * <li>B463: "with no reachable Storehouse the load goes back into the Drop Box (or the nearest open chest it came from)
 * and the golem is free". The fix's test walls the only Storehouse in; this one has the player break it while the
 * golem carries a real load picked up from the Drop Box.</li>
 * </ul>
 */
public class QaLedgerFramesHaulGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");

	// ---- B575: the path to the hall keeps out of a section a player built in, marked the player's way ----

	/**
	 * Grass all over, a Village Hall in the far corner (the path's goal: no bell), the hut near the other. A player
	 * breaks a cobblestone block lying in the section between them (27.19: placing or breaking marks it): the finished
	 * hut's path has no block in that section, and still runs in the others.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaPathLedgerBroken"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaPathLedgerBroken")
	public void aBlockAPlayerBrokeNearTheHallKeepsTheBuildersPathOutOfItsSection(GameTestHelper helper) {
		pathToHallAvoidsAPlayersSection(helper, true);
	}

	/** As above, with the player placing a block (through its item, as a click does) instead of breaking one. */
	//$ gametest_ticks_batch HUGE '3000' '"qaPathLedgerPlaced"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaPathLedgerPlaced")
	public void aBlockAPlayerPlacedNearTheHallKeepsTheBuildersPathOutOfItsSection(GameTestHelper helper) {
		pathToHallAvoidsAPlayersSection(helper, false);
	}

	private static void pathToHallAvoidsAPlayersSection(GameTestHelper helper, boolean breakIt) {
		ServerLevel level = QaBenchMovePathsPaceGameTests.prepare(helper, 2);
		boolean was = Paths.ENABLED;
		Paths.ENABLED = true;
		Leftovers.after(helper, () -> Paths.ENABLED = was);
		clearBells(helper);
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox area = new BoundingBox(a.getX() - 20, level.getMinBuildHeight(), a.getZ() - 20, a.getX() + 50, level.getMaxBuildHeight(), a.getZ() + 50);
		PlayerBuilt.get(level).forget(area);
		Leftovers.after(helper, () -> PlayerBuilt.get(level).forget(area));
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}
		helper.setBlock(new BlockPos(27, 2, 27), ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		Leftovers.after(helper, VillageHalls::forgetNearest);
		helper.assertTrue(VillageHalls.nearest(level, helper.absolutePos(new BlockPos(19, 2, 19))).isPresent(), "setup: the hall's area doesn't take in the middle");
		// the player's block, in the section halfway between the hut and the hall
		BlockPos spot = helper.absolutePos(new BlockPos(19, 2, 19));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, player::discard);
		if (breakIt) {
			level.setBlockAndUpdate(spot, Blocks.COBBLESTONE.defaultBlockState());
			helper.assertTrue(player.gameMode.destroyBlock(spot), "setup: the player couldn't break the block");
		} else {
			helper.assertTrue(StewardSafetyGameTests.place(helper, player, spot, Blocks.OAK_PLANKS), "setup: the player couldn't place the block");
		}
		helper.assertTrue(PlayerBuilt.get(level).marked(spot), "the ledger didn't mark the section of a block a player " + (breakIt ? "broke" : "placed")
			+ " beside a Village Hall (27.19)");
		long marked = SectionPos.asLong(spot);
		helper.assertTrue(SectionPos.asLong(spot.below()) == marked, "setup: the ground under the spot is in another section");
		Villager builder = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 2), QaBenchMovePathsPaceGameTests.hutMaterials());
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, new BlockPos(8, 2, 8)));
		helper.assertTrue(site != null, "setup: the builder didn't take the hut");
		int[] planned = {0};
		helper.succeedWhen(() -> {
			List<BlockPos> plan = ModAttachments.PATH.getOrElse(builder, List.of());
			planned[0] = Math.max(planned[0], plan.size());
			for (BlockPos p : plan) {
				helper.assertTrue(SectionPos.asLong(p) != marked, "a path block planned in the player's section at " + helper.relativePos(p));
			}
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building");
			helper.assertTrue(planned[0] > 0, "no path planned to the hall");
			helper.assertTrue(!ModAttachments.PATH.has(builder), "still laying the path: " + plan.size() + " to go");
			int inside = 0;
			int outside = 0;
			for (int x = 0; x < 30; x++) {
				for (int z = 0; z < 30; z++) {
					BlockPos rel = new BlockPos(x, 1, z);
					if (helper.getBlockState(rel).is(Blocks.DIRT_PATH)) {
						if (SectionPos.asLong(helper.absolutePos(rel)) == marked) {
							inside++;
						} else {
							outside++;
						}
					}
				}
			}
			helper.assertTrue(inside == 0, inside + " blocks of path in the section a player " + (breakIt ? "broke" : "placed") + " a block in");
			helper.assertTrue(outside >= 2, "only " + outside + " blocks of path outside the player's section");
			LOGGER.info("qa path ledger ({}): {} path blocks outside the player's section, none in it", breakIt ? "broken" : "placed", outside);
		});
	}

	/** Bells another batch left beside this area (never cleared) would be nearer than this test's hall. */
	private static void clearBells(GameTestHelper helper) {
		AABB box = helper.getBounds();
		for (BlockPos bell : helper.getLevel().getPoiManager().findAll(h -> h.is(PoiTypes.MEETING), p -> !box.contains(Vec3.atCenterOf(p)),
			BlockPos.containing(box.getCenter()), 96, PoiManager.Occupancy.ANY).toList()) {
			helper.getLevel().removeBlock(bell, false);
		}
	}

	// ---- B439: every blueprint's frames and paintings, turned and mirrored ----

	/**
	 * For every blueprint of the mod that hangs an item frame or painting, in the four turns plain and mirrored (the
	 * mirrors a player can choose), each hanging piece is made the way the builder makes it (BuildEntities) beside its
	 * own part of the build, moved into this area: it faces the way the blueprint hangs it, turned and mirrored, and
	 * has its wall behind it. A piece that can't hang even unturned (a modded wall block missing in this run) is listed
	 * and left out: only a turn or mirror that loses a piece that hangs unturned fails the test.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaEveryBlueprintFrames"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaEveryBlueprintFrames")
	public void everyBlueprintsFramesAndPaintingsHangTurnedAndMirrored(GameTestHelper helper) throws ReflectiveOperationException {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Method create = BuildEntities.class.getDeclaredMethod("create", ServerLevel.class, BuildPlan.EntityStep.class);
		create.setAccessible(true);
		BlockPos centre = helper.absolutePos(new BlockPos(15, 12, 15));
		BlockPos corner = helper.absolutePos(new BlockPos(1, 2, 1));
		List<String> wrong = new ArrayList<>();
		List<String> unhangable = new ArrayList<>();
		int builds = 0;
		int checked = 0;
		for (ResourceLocation id : BlueprintLibrary.list(level.getServer(), true)) {
			if (!id.getNamespace().equals(AliveWorkplace.MOD_ID)) {
				continue;
			}
			Optional<Blueprint> found = BlueprintLibrary.get(level, id);
			if (found.isEmpty() || found.get().entities().stream().noneMatch(e -> isHanging(e.nbt()))) {
				continue;
			}
			builds++;
			Blueprint blueprint = found.get();
			List<Integer> hangsUnturned = new ArrayList<>();
			for (Mirror mirror : List.of(Mirror.NONE, Mirror.FRONT_BACK)) {
				for (Rotation rotation : Rotation.values()) {
					BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), corner, rotation, mirror);
					BuildPlan plan = BuildPlan.create(blueprint, placement);
					List<BuildPlan.EntityStep> steps = plan.entities();
					for (int i = 0; i < steps.size(); i++) {
						BuildPlan.EntityStep step = steps.get(i);
						if (!isHanging(step.nbt())) {
							continue;
						}
						boolean unturned = rotation == Rotation.NONE && mirror == Mirror.NONE;
						if (!unturned && !hangsUnturned.contains(i)) {
							continue; // can't hang even unturned: listed once below
						}
						String where = id.getPath() + " #" + i + " " + rotation + (mirror == Mirror.NONE ? "" : " mirrored");
						BlockPos at = BlockPos.containing(step.pos());
						BlockPos delta = centre.subtract(at);
						BuildPlan.EntityStep moved = new BuildPlan.EntityStep(step.pos().add(delta.getX(), delta.getY(), delta.getZ()), step.nbt(),
							step.cost(), step.rotation(), step.mirror());
						BoundingBox near = new BoundingBox(at.getX() - 3, at.getY() - 3, at.getZ() - 3, at.getX() + 3, at.getY() + 3, at.getZ() + 3);
						BoundingBox here = near.moved(delta.getX(), delta.getY(), delta.getZ());
						clear(level, here);
						for (Map.Entry<BlockPos, BlockState> t : plan.targets().entrySet()) {
							if (near.isInside(t.getKey())) {
								level.setBlock(t.getKey().offset(delta), t.getValue(), 2 | 16);
							}
						}
						Optional<?> made = (Optional<?>) create.invoke(null, level, moved);
						Direction drawn = step.nbt().getString("id").equals("minecraft:painting")
							? Direction.from2DDataValue(step.nbt().getByte("facing")) : Direction.from3DDataValue(step.nbt().getByte("Facing"));
						Direction expected = rotation.rotate(mirror.mirror(drawn));
						String problem = null;
						if (made.isEmpty() || !(made.get() instanceof HangingEntity hanging)) {
							problem = "not made at all";
						} else if (!hanging.survives()) {
							problem = "has no wall: faces " + hanging.getDirection() + ", expected " + expected;
						} else if (hanging.getDirection() != expected) {
							problem = "faces " + hanging.getDirection() + ", drawn " + drawn + " so expected " + expected;
						} else if (hanging.position().distanceTo(moved.pos()) > 0.6) {
							problem = "hangs " + String.format("%.2f", hanging.position().distanceTo(moved.pos())) + " blocks off its spot";
						}
						if (unturned) {
							if (problem == null) {
								hangsUnturned.add(i);
							} else {
								unhangable.add(id.getPath() + " #" + i + " (" + problem + ")");
							}
						} else if (problem != null) {
							wrong.add(where + ": " + problem);
						}
						if (problem == null) {
							checked++;
						}
						clear(level, here);
					}
				}
			}
		}
		LOGGER.info("qa every blueprint's frames: {} builds with frames or paintings, {} pieces hung right; can't hang even unturned in this run: {}; wrong: {}",
			builds, checked, unhangable, wrong);
		helper.assertTrue(builds > 0 && checked > 0, "no blueprint hangs a frame or painting to check");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " frames and paintings hang wrong once turned or mirrored: "
			+ wrong.subList(0, Math.min(10, wrong.size())));
		helper.succeed();
	}

	private static boolean isHanging(CompoundTag nbt) {
		String id = nbt.getString("id");
		return id.equals("minecraft:item_frame") || id.equals("minecraft:glow_item_frame") || id.equals("minecraft:painting");
	}

	private static void clear(ServerLevel level, BoundingBox box) {
		level.getEntities((Entity) null, AABB.of(box).inflate(2), e -> e instanceof HangingEntity).forEach(Entity::discard);
		BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
			.forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16));
	}

	// ---- B463: the only Storehouse broken while a hauler carries a load ----

	/**
	 * A Hauler Golem picks up a Drop Box's 3 stacks for the only Storehouse, and the player breaks that Storehouse while
	 * the golem carries them: the load goes back into the Drop Box (B463: no Storehouse to reach), nothing is lost, and
	 * the golem is free (carries nothing).
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaHaulerStorehouseBroken"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaHaulerStorehouseBroken")
	public void aHaulerWhoseStorehouseIsBrokenMidTripPutsItsLoadBackInTheDropBox(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		BlockPos storehouse = new BlockPos(24, 2, 24);
		BlockPos storeChest = new BlockPos(25, 2, 24);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storeChest, Blocks.CHEST);
		BlockPos boxPos = new BlockPos(4, 2, 4);
		helper.setBlock(boxPos, ModBlocks.DROP_BOX);
		Container box = helper.getBlockEntity(boxPos);
		for (int i = 0; i < 3; i++) {
			box.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(10, 1, 10)), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, player::discard);
		boolean[] broken = {false};
		helper.onEachTick(() -> {
			if (!broken[0] && carried(golem) == 3 * 64) {
				broken[0] = player.gameMode.destroyBlock(helper.absolutePos(storehouse));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "the golem never picked up the 3 stacks (carries " + carried(golem) + ", the Drop Box has " + box.countItem(Items.COBBLESTONE) + ")");
			Container store = helper.getBlockEntity(storeChest);
			int stored = store.countItem(Items.COBBLESTONE);
			helper.assertTrue(stored == 0, "cobblestone got to the broken Storehouse's chest (" + stored + "): the test proves nothing");
			helper.assertTrue(box.countItem(Items.COBBLESTONE) == 3 * 64, "the Storehouse was broken mid-trip and the Drop Box has " + box.countItem(Items.COBBLESTONE)
				+ " of 192 cobblestone back; the golem carries " + carried(golem) + " at " + golem.blockPosition() + ", walking: " + golem.getNavigation().isInProgress());
			helper.assertTrue(carried(golem) == 0, "the golem still carries " + carried(golem));
			LOGGER.info("qa hauler storehouse broken: 192 cobblestone back in the Drop Box, the golem free");
		});
	}

	private static int carried(IronGolem golem) {
		return HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum();
	}
}
