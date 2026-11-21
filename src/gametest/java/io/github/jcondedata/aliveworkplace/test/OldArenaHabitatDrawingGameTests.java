package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.cup.StandSeat;
import io.github.jcondedata.aliveworkplace.habitat.VillageHabitats;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 50.12 and saves: the Arena and the Habitat Garden were rebuilt from scratch and every spot the mod reads in them
 * moved, but a finished building records no drawing version, and the owner's live server has both standing in the old
 * drawing (blueprint_history/&lt;id&gt;/v0.145.0.nbt). Placed from that old drawing, in every rotation and mirrored: the
 * Arena is read by its old spots (its seats real stairs with room above, the ring, the boxes, the fair lane, the pole and
 * the notice board where the old drawing put them), villagers sat on every one of its seats aren't inside a block, and the
 * Habitat Garden's mossy centre stone is found where the old drawing put it. The new drawings are still read by today's
 * spots ({@code ArenaGameTests} checks those spots in full).
 */
public class OldArenaHabitatDrawingGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos ORIGIN = new BlockPos(0, 2, 0);
	/** The old drawing's key in blueprint_history (the drawing every release up to 50.12 shipped). */
	private static final String OLD = "v0.145.0";

	/** The old drawing of {@code id}, as the mod ships it in blueprint_history, as a structure template. */
	static StructureTemplate oldTemplate(ServerLevel level, ResourceLocation id) {
		ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "blueprint_history/" + id.getPath() + "/" + OLD + ".nbt");
		Resource resource = level.getServer().getResourceManager().getResource(file)
			.orElseThrow(() -> new IllegalStateException("no " + file + " shipped"));
		try (InputStream in = resource.open()) {
			CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
			StructureTemplate template = new StructureTemplate();
			template.load(Lookup.lookup(BuiltInRegistries.BLOCK), tag);
			return template;
		} catch (IOException e) {
			throw new IllegalStateException("can't read " + file, e);
		}
	}

	/** {@code template} placed turned and mirrored so it lies in the area from its (0, 0) corner; its placement. */
	private static BlueprintData.Placement place(GameTestHelper helper, StructureTemplate template, Rotation rotation, Mirror mirror) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement first = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), rotation, mirror);
		BoundingBox turned = BlueprintOutline.bounds(first, template.getSize());
		BlockPos corner = helper.absolutePos(ORIGIN);
		BlockPos origin = first.origin().offset(corner.getX() - turned.minX(), 0, corner.getZ() - turned.minZ());
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(rotation).setMirror(mirror), RandomSource.create(50_12L), 2);
		return new BlueprintData.Placement(level.dimension().location(), origin, rotation, mirror);
	}

	private static void clearArea(GameTestHelper helper) {
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 2, 0), new BlockPos(29, 18, 29))) {
			helper.setBlock(p, Blocks.AIR);
		}
	}

	private static void forgetArenas(ServerLevel level, BlockPos hall) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : sites.finishedNear(level, hall, io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS * 2)) {
			if (Arenas.tier(f.structure()) > 0) {
				sites.forgetFinished(f.placement());
			}
		}
	}

	// --- the Arena ----------------------------------------------------------------------------------------------------

	/**
	 * Every tier of the old drawing, in each of the four rotations, plain and mirrored: {@link Arenas#find} reads it by the
	 * old drawing's spots, where its blocks really are. Each tier of today's drawing is still read by today's spots.
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"old_arena_spots"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "old_arena_spots")
	public void anArenaOfTheOldDrawingIsReadByItsOldSpots(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(15, 2, 15));
		forgetArenas(level, hall);
		int checked = 0;
		for (StarterBlueprints.Entry entry : StarterBlueprints.ARENAS) {
			StructureTemplate old = oldTemplate(level, entry.id());
			for (Mirror mirror : List.of(Mirror.NONE, Mirror.FRONT_BACK)) {
				for (Rotation rotation : Rotation.values()) {
					clearArea(helper);
					BlueprintData.Placement placement = place(helper, old, rotation, mirror);
					BuildSiteManager.get(level).recordFinished(entry.id(), placement, UUID.randomUUID());
					try {
						helper.assertTrue(Arenas.oldDrawing(level, entry.id(), placement), entry.id() + " " + rotation + " " + mirror
							+ ": the old drawing standing isn't told as the old drawing");
						Optional<Arenas.Arena> found = Arenas.find(level, hall);
						helper.assertTrue(found.isPresent() && found.get().placement().equals(placement), "no old " + entry.id() + " found at " + placement);
						assertOldSpots(helper, found.get());
					} finally {
						BuildSiteManager.get(level).forgetFinished(placement);
					}
					checked++;
				}
			}
			// today's drawing beside it: still read by today's spots
			clearArea(helper);
			StructureTemplate now = level.getStructureManager().get(entry.id()).orElseThrow();
			BlueprintData.Placement placement = place(helper, now, Rotation.CLOCKWISE_90, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(entry.id(), placement, UUID.randomUUID());
			try {
				helper.assertTrue(!Arenas.oldDrawing(level, entry.id(), placement), entry.id() + ": today's drawing is told as the old one");
				Arenas.Arena arena = Arenas.find(level, hall).orElseThrow();
				helper.assertTrue(arena.ring().equals(Arenas.at(entry.id(), placement).ring()) && arena.seats().equals(Arenas.at(entry.id(), placement).seats()),
					entry.id() + ": today's drawing isn't read by today's spots");
			} finally {
				BuildSiteManager.get(level).forgetFinished(placement);
			}
		}
		helper.assertTrue(checked == 24, "checked " + checked + " placements");
		clearArea(helper);
		helper.succeed();
	}

	/** The spots of {@code arena} hold what the old drawing (tools/blueprints/arena.py) put there. */
	private static void assertOldSpots(GameTestHelper helper, Arenas.Arena arena) {
		ServerLevel level = helper.getLevel();
		String what = arena.structure() + " (old drawing) " + arena.placement().rotation() + " " + arena.placement().mirror();
		helper.assertTrue(level.getBlockState(arena.ring().below()).is(Blocks.WHITE_GLAZED_TERRACOTTA) && level.getBlockState(arena.ring()).isAir(),
			what + ": the ring centre at " + helper.relativePos(arena.ring()) + " stands on " + level.getBlockState(arena.ring().below()));
		helper.assertTrue(arena.boxes().size() == 2, what + ": " + arena.boxes().size() + " boxes");
		for (BlockPos box : arena.boxes()) {
			helper.assertTrue(level.getBlockState(box.below()).is(Blocks.POLISHED_ANDESITE) && level.getBlockState(box).isAir(),
				what + ": no dais under the box at " + helper.relativePos(box) + ": " + level.getBlockState(box.below()));
		}
		int expected = arena.tier() == 1 ? 12 : arena.tier() == 2 ? 30 : 52;
		helper.assertTrue(arena.seats().size() == expected && new HashSet<>(arena.seats()).size() == expected,
			what + ": " + arena.seats().size() + " seats, not " + expected);
		Vec3 ring = Vec3.atCenterOf(arena.ring());
		for (BlockPos seat : arena.seats()) {
			BlockState state = level.getBlockState(seat);
			helper.assertTrue(state.getBlock() instanceof StairBlock, what + ": the seat at " + helper.relativePos(seat) + " is " + state);
			Direction back = state.getValue(StairBlock.FACING);
			Vec3 toRing = ring.subtract(Vec3.atCenterOf(seat));
			helper.assertTrue(back.getStepX() * toRing.x + back.getStepZ() * toRing.z < 0,
				what + ": the seat at " + helper.relativePos(seat) + " has its back to the ring (" + back + ")");
			helper.assertTrue(level.getBlockState(seat.above()).isAir(), what + ": no room to sit at " + helper.relativePos(seat));
		}
		helper.assertTrue(arena.fairLane().isPresent() == (arena.tier() >= 2), what + ": fair lane " + arena.fairLane());
		arena.fairLane().ifPresent(lane -> helper.assertTrue(level.getBlockState(lane).isAir() && !level.getBlockState(lane.below()).isAir(),
			what + ": the fair lane at " + helper.relativePos(lane) + " is " + level.getBlockState(lane) + " on " + level.getBlockState(lane.below())));
		helper.assertTrue(arena.championPole().isPresent() == (arena.tier() >= 3), what + ": champion's pole " + arena.championPole());
		arena.championPole().ifPresent(pole -> helper.assertTrue(level.getBlockState(pole).is(Blocks.STRIPPED_SPRUCE_LOG)
			&& level.getBlockState(pole.above()).is(Blocks.GOLD_BLOCK), what + ": the champion's pole at " + helper.relativePos(pole) + " is "
			+ level.getBlockState(pole)));
		BlockPos board = arena.noticeBoard();
		helper.assertTrue(level.getBlockState(board).is(Blocks.DARK_OAK_PLANKS),
			what + ": the notice board at " + helper.relativePos(board) + " is " + level.getBlockState(board));
		boolean signed = Direction.Plane.HORIZONTAL.stream().anyMatch(d -> level.getBlockState(board.relative(d)).getBlock() instanceof WallSignBlock);
		helper.assertTrue(signed, what + ": no sign on the notice board at " + helper.relativePos(board));
	}

	/**
	 * The Cup's stands in an Arena III of the old drawing (its 52 seats): a villager sat on each seat the Arena gives rides
	 * it on the stair, not inside a block (today's drawing's seats there would be in its walls and air: none is a stair).
	 */
	//$ gametest_ticks_batch HUGE_AREA '200' '"old_arena_seats"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "old_arena_seats")
	public void villagersSitOnTheRealSeatsOfAnOldArena(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(15, 2, 15));
		forgetArenas(level, hall);
		clearArea(helper);
		StarterBlueprints.Entry entry = StarterBlueprints.ARENA_3;
		BlueprintData.Placement placement = place(helper, oldTemplate(level, entry.id()), Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(entry.id(), placement, UUID.randomUUID());
		List<Entity> spawned = new ArrayList<>();
		Leftovers.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(placement);
			spawned.forEach(Entity::discard);
		});
		long todaysOnStairs = Arenas.at(entry.id(), placement).seats().stream().filter(s -> level.getBlockState(s).getBlock() instanceof StairBlock).count();
		helper.assertTrue(todaysOnStairs == 0, "setup: today's drawing's seats find " + todaysOnStairs + " stairs in the old Arena III");
		Arenas.Arena arena = Arenas.find(level, hall).orElseThrow();
		helper.assertTrue(arena.seats().size() == 52, "the old Arena III has " + arena.seats().size() + " seats, not 52");
		List<Villager> sitters = new ArrayList<>();
		for (BlockPos bench : arena.seats()) {
			StandSeat seat = StandSeat.at(level, bench);
			level.addFreshEntity(seat);
			spawned.add(seat);
			Villager v = EntityType.VILLAGER.create(level);
			v.moveTo(bench.getX() + 0.5, bench.getY() + 1, bench.getZ() + 0.5, 0f, 0f);
			v.setNoAi(true);
			level.addFreshEntity(v);
			spawned.add(v);
			helper.assertTrue(v.startRiding(seat, true), "didn't sit on the seat at " + helper.relativePos(bench));
			sitters.add(v);
		}
		helper.runAfterDelay(5, () -> {
			for (int i = 0; i < sitters.size(); i++) {
				Villager v = sitters.get(i);
				BlockPos bench = arena.seats().get(i);
				helper.assertTrue(v.isAlive() && v.isPassenger(), "the villager on the seat at " + helper.relativePos(bench) + " isn't seated");
				helper.assertTrue(!v.isInWall(), "the villager on the seat at " + helper.relativePos(bench) + " is inside a block at " + v.position());
			}
			helper.succeed();
		});
	}

	// --- the Habitat Garden -------------------------------------------------------------------------------------------

	/**
	 * Both tiers of the old Habitat Garden, in each rotation, plain and mirrored: the garden's centre stone (where an Expert
	 * keeper founds the village's habitat) is found at the old drawing's spot, a mossy cobblestone; today's drawing's
	 * stone is still found at today's spot.
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"old_habitat_centre"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "old_habitat_centre")
	public void anOldHabitatGardensCentreStoneIsFound(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int checked = 0;
		for (StarterBlueprints.Entry entry : List.of(StarterBlueprints.HABITAT_GARDEN, StarterBlueprints.HABITAT_GARDEN_2)) {
			StructureTemplate old = oldTemplate(level, entry.id());
			StructureTemplate now = level.getStructureManager().get(entry.id()).orElseThrow();
			for (Mirror mirror : List.of(Mirror.NONE, Mirror.FRONT_BACK)) {
				for (Rotation rotation : Rotation.values()) {
					clearArea(helper);
					BlueprintData.Placement placement = place(helper, old, rotation, mirror);
					String what = entry.id() + " (old drawing) " + rotation + " " + mirror;
					BlockPos centre = VillageHabitats.centre(level, placement);
					helper.assertTrue(centre.equals(VillageHabitats.v0145Centre(placement)), what + ": the centre is " + helper.relativePos(centre)
						+ ", not the old drawing's " + helper.relativePos(VillageHabitats.v0145Centre(placement)));
					helper.assertTrue(VillageHabitats.isCentreStone(level.getBlockState(centre)), what + ": the centre is " + level.getBlockState(centre));
					helper.assertTrue(!VillageHabitats.isCentreStone(level.getBlockState(VillageHabitats.centre(placement))),
						what + ": setup: today's spot holds a centre stone too");

					clearArea(helper);
					placement = place(helper, now, rotation, mirror);
					what = entry.id() + " " + rotation + " " + mirror;
					centre = VillageHabitats.centre(level, placement);
					helper.assertTrue(centre.equals(VillageHabitats.centre(placement)) && VillageHabitats.isCentreStone(level.getBlockState(centre)),
						what + ": today's centre stone is read at " + helper.relativePos(centre) + ": " + level.getBlockState(centre));
					checked++;
				}
			}
		}
		helper.assertTrue(checked == 16, "checked " + checked + " placements");
		clearArea(helper);
		helper.succeed();
	}
}
