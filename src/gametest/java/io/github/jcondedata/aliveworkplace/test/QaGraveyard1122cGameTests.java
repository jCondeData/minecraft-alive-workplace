package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.grave.Graveyards;
import io.github.jcondedata.aliveworkplace.people.Mourning;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane C (2026-11-22), B1423 from its bug text: every grave slot of a full Graveyard, both levels, has a standing spot
 * reached on foot from the street and from the brewing stand (the Undertaker's job block); graves in the old slots still
 * rest in the Graveyard. {@link QaRebuiltCivic1121cBugTests} walks the full Level I from the street with the pathfinder;
 * {@link QaRebuiltCivic1121cGameTests} checks the drawings as the yard fills, and the slots' order in both levels. These
 * add: the full Level II, the walk from the brewing stand in both levels, the yard built turned (a placement's rotation
 * moves the slots with the drawing), the real burial (a villager dies in the street and the Undertaker lays the grave in
 * the last free slot of a nearly full yard, then walks to it without a stuck-teleport, leaving an old-slot grave where it
 * lies), and mourners walking from the street to a grave in the middle of a full yard.
 */
public class QaGraveyard1122cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static BlockPos toWorld(BlockPos origin, Rotation rotation, BlockPos local) {
		return origin.offset(StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO));
	}

	/** The yard placed at {@code at} turned by {@code rotation} and recorded finished (forgotten when the test ends). */
	private static BlueprintData.Placement placeYard(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos at, Rotation rotation) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(entry.id()).orElseThrow();
		BlockPos origin = helper.absolutePos(at);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(rotation), net.minecraft.util.RandomSource.create(173L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(entry.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return placement;
	}

	private static BoundingBox box(BlockPos origin, Rotation rotation) {
		return BoundingBox.fromCorners(toWorld(origin, rotation, BlockPos.ZERO), toWorld(origin, rotation, new BlockPos(12, 15, 12)));
	}

	/** The Undertaker's job block in the placed yard: its brewing stand (or an Undertaker's table). */
	private static BlockPos jobBlock(GameTestHelper helper, BoundingBox box) {
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = helper.getLevel().getBlockState(p);
			if (s.is(Blocks.BREWING_STAND) || s.is(ModBlocks.UNDERTAKERS_TABLE)) {
				return p.immutable();
			}
		}
		throw new AssertionError("the placed yard has no brewing stand or Undertaker's table");
	}

	private static boolean standable(ServerLevel level, BlockPos feet) {
		BlockState below = level.getBlockState(feet.below());
		return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
			&& below.isFaceSturdy(level, feet.below(), Direction.UP) && !below.is(ModBlocks.GRAVE) && !(below.getBlock() instanceof FenceBlock)
			&& !(below.getBlock() instanceof WallBlock) && !(below.getBlock() instanceof FenceGateBlock);
	}

	/** A spot right by {@code block} a villager can stand on (its own level first). */
	private static BlockPos besideIt(GameTestHelper helper, BlockPos block) {
		for (int dy : new int[] {0, -1, 1}) {
			for (int r = 1; r <= 2; r++) {
				for (BlockPos p : BlockPos.betweenClosed(block.offset(-r, dy, -r), block.offset(r, dy, r))) {
					if (standable(helper.getLevel(), p)) {
						return p.immutable();
					}
				}
			}
		}
		throw new AssertionError("nowhere to stand by the job block " + block.toShortString());
	}

	/** Lays a grave in each free slot, in the game's order (each must land in the placed yard): the slots, as world positions. */
	private static List<BlockPos> fill(GameTestHelper helper, BlockPos origin, BoundingBox box) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> laid = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			BlockPos next = Graveyards.nextSlot(level, Graveyards.resting(level, origin, false));
			if (next == null) {
				break;
			}
			helper.assertTrue(box.isInside(next), "grave " + (i + 1) + " goes to " + next.toShortString() + ", outside the placed yard " + box);
			level.setBlockAndUpdate(next, ModBlocks.GRAVE.defaultBlockState());
			laid.add(next);
		}
		helper.assertTrue(laid.size() == 32 && new HashSet<>(laid).size() == 32, laid.size() + " graves laid in the placed yard, not 32");
		return laid;
	}

	private static Villager walker(GameTestHelper helper, BlockPos absolute) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
		v.setNoAi(true);
		helper.getLevel().addFreshEntity(v);
		return v;
	}

	/**
	 * The yard placed turned by {@code rotation}, its 32 slots filled in order, then from the street in front (turned with
	 * it) and from beside the job block the game's pathfinder finds a way to within reach of every grave. Before filling,
	 * a grave in an old slot (the walk (3, 0, 6)) of the turned yard rests in it and isn't a stray.
	 */
	private void fullYard(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos at, Rotation rotation) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = placeYard(helper, entry, at, rotation);
		BlockPos origin = placement.origin();
		BoundingBox box = box(origin, rotation);
		BlockPos old = toWorld(origin, rotation, new BlockPos(3, 0, 6));
		level.setBlockAndUpdate(old, ModBlocks.GRAVE.defaultBlockState());
		Graveyards.Resting resting = Graveyards.resting(level, origin, false);
		helper.assertTrue(!resting.slots().contains(old), "the old slot (3, 0, 6) of the yard (turned " + rotation + ") is still a slot");
		helper.assertTrue(Graveyards.resting(level, resting).contains(old), "a grave in an old slot of the yard (turned " + rotation + ") isn't resting there");
		helper.assertTrue(Graveyards.strays(level, List.of(old), resting).isEmpty(), "a grave in an old slot of the yard (turned " + rotation + ") is a stray");
		level.setBlockAndUpdate(old, Blocks.AIR.defaultBlockState());
		List<BlockPos> graves = fill(helper, origin, box);
		helper.assertTrue(Graveyards.resting(level, Graveyards.resting(level, origin, false)).containsAll(graves), "the full yard's graves aren't all resting there");
		BlockPos stand = jobBlock(helper, box);
		Villager fromStreet = walker(helper, toWorld(origin, rotation, new BlockPos(6, 0, -3)));
		Villager fromStand = walker(helper, besideIt(helper, stand));
		helper.runAfterDelay(10, () -> {
			List<String> cut = new ArrayList<>();
			for (Villager w : List.of(fromStreet, fromStand)) {
				w.setOnGround(true); // (a villager without AI never lands by itself: the pathfinder wants it on the ground)
				List<String> none = new ArrayList<>();
				for (BlockPos g : graves) {
					Path path = w.getNavigation().createPath(g, 2);
					if (path == null || !path.canReach()) {
						none.add(StructureTemplate.transform(g.subtract(origin), Mirror.NONE, inverse(rotation), BlockPos.ZERO).toShortString());
					}
				}
				if (!none.isEmpty()) {
					cut.add((w == fromStreet ? "from the street" : "from the job block") + ": " + none.size() + " of 32 " + none);
				}
				w.discard();
			}
			helper.assertTrue(cut.isEmpty(), entry.id().getPath() + " turned " + rotation + ", full: graves with no way to within reach (yard's own coordinates): "
				+ String.join("; ", cut));
			helper.succeed();
		});
	}

	private static Rotation inverse(Rotation r) {
		return switch (r) {
			case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
			case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
			default -> r;
		};
	}

	/** Level II full, as drawn: every grave reached from the street and from the brewing stand. */
	//$ gametest_ticks_batch HUGE '100' '"qaGraveyard1122cFullII"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaGraveyard1122cFullII")
	public void qaEveryGraveInAFullGraveyardIIIsReachedFromTheStreetAndTheStand(GameTestHelper helper) {
		fullYard(helper, StarterBlueprints.GRAVEYARD_2, new BlockPos(8, 2, 8), Rotation.NONE);
	}

	/** Level I full, as drawn: every grave reached from the brewing stand as well as the street. */
	//$ gametest_ticks_batch HUGE '100' '"qaGraveyard1122cFullI"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaGraveyard1122cFullI")
	public void qaEveryGraveInAFullGraveyardIsReachedFromTheStand(GameTestHelper helper) {
		fullYard(helper, StarterBlueprints.GRAVEYARD, new BlockPos(8, 2, 8), Rotation.NONE);
	}

	/** Level I built turned a quarter clockwise: the slots turn with the drawing, all 32 land in it and each is reached. */
	//$ gametest_ticks_batch HUGE '100' '"qaGraveyard1122cTurnedI"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaGraveyard1122cTurnedI")
	public void qaAFullGraveyardBuiltTurnedIsReachedEverywhere(GameTestHelper helper) {
		fullYard(helper, StarterBlueprints.GRAVEYARD, new BlockPos(21, 2, 6), Rotation.CLOCKWISE_90);
	}

	/** Level II built turned around (the gate facing the other way): the same. */
	//$ gametest_ticks_batch HUGE '100' '"qaGraveyard1122cTurnedII"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaGraveyard1122cTurnedII")
	public void qaAFullGraveyardIIBuiltTurnedAroundIsReachedEverywhere(GameTestHelper helper) {
		fullYard(helper, StarterBlueprints.GRAVEYARD_2, new BlockPos(21, 2, 22), Rotation.CLOCKWISE_180);
	}

	private static final BlockPos YARD = new BlockPos(8, 2, 8);

	/**
	 * The real burial: Level II with 31 graves, only (10, 0, 4) free (in the middle of the right side: no wall or way in
	 * within reach), and a grave laid in an old slot on an old save (the walk (3, 0, 6)). A Builder dies in the street; the
	 * Undertaker, working at the yard's brewing stand with nothing to revive with, carries the grave to (10, 0, 4), everyone
	 * in it kept, then walks over to it on foot (no stuck-teleport on the way from where he lifted it) and stands in reach.
	 * The old-slot grave stays where it is (it rests in the Graveyard; it is not a stray to move).
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaGraveyard1122cBurial"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaGraveyard1122cBurial")
	public void qaTheUndertakerLaysAStreetDeathInTheLastFreeSlotAndWalksToIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = placeYard(helper, StarterBlueprints.GRAVEYARD_2, YARD, Rotation.NONE);
		BlockPos origin = placement.origin();
		Graveyards.ENABLED = true; // (off in GameTests by default: WorkplaceConfig.apply)
		Leftovers.after(helper, () -> Graveyards.ENABLED = false);
		helper.setDayTime(2000);
		BlockPos target = origin.offset(10, 0, 4);
		BlockPos old = origin.offset(3, 0, 6);
		helper.assertTrue(level.getBlockState(old).isAir() || level.getBlockState(old).canBeReplaced(), "set-up: the old slot (3, 0, 6) is drawn on");
		for (BlockPos slot : Graveyards.resting(level, origin, false).slots()) {
			if (!slot.equals(target)) {
				level.setBlockAndUpdate(slot, ModBlocks.GRAVE.defaultBlockState());
			}
		}
		level.setBlockAndUpdate(old, ModBlocks.GRAVE.defaultBlockState());
		helper.assertTrue(target.equals(Graveyards.nextSlot(level, Graveyards.resting(level, origin, false))), "set-up: (10, 0, 4) isn't the free slot");
		// a Builder (Mira) dies in the street in front
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BUILDERS_BENCH);
		BlockPos street = YARD.offset(6, 0, -4);
		Villager builder = helper.spawn(EntityType.VILLAGER, street);
		Jobs.employ(level, builder, helper.absolutePos(new BlockPos(2, 2, 2)), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		builder.setCustomName(Component.literal("Mira"));
		builder.kill();
		List<BlockPos> fallen = Graves.near(level, helper.absolutePos(street), 3);
		helper.assertTrue(fallen.size() == 1, fallen.size() + " graves in the street after Mira died");
		BlockPos streetGrave = fallen.get(0);
		CompoundTag mira = ((GraveBlockEntity) level.getBlockEntity(streetGrave)).villager().copy();
		// the Undertaker works at the yard's brewing stand
		BlockPos stand = jobBlock(helper, box(origin, Rotation.NONE));
		Villager undertaker = EntityType.VILLAGER.create(level);
		BlockPos by = besideIt(helper, stand);
		undertaker.moveTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5, 0, 0);
		level.addFreshEntity(undertaker);
		Jobs.employ(level, undertaker, stand, level.getBlockState(stand).is(Blocks.BREWING_STAND) ? PoiTypes.CLERIC : ModVillagers.UNDERTAKERS_TABLE_POI,
			ModVillagers.UNDERTAKER);
		Vec3[] last = {undertaker.position()};
		boolean[] laid = {false};
		boolean[] reached = {false};
		List<String> hops = new ArrayList<>();
		helper.onEachTick(() -> {
			Vec3 now = undertaker.position();
			if (!laid[0] && level.getBlockState(target).is(ModBlocks.GRAVE)) {
				laid[0] = true;
			}
			if (laid[0] && !reached[0]) {
				if (now.distanceTo(last[0]) > 1.2) {
					hops.add(BlockPos.containing(last[0]).subtract(origin).toShortString() + " to " + BlockPos.containing(now).subtract(origin).toShortString());
				}
				if (undertaker.getEyePosition().distanceTo(Vec3.atCenterOf(target)) <= 2.6) {
					reached[0] = true;
				}
			}
			last[0] = now;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!level.getBlockState(streetGrave).is(ModBlocks.GRAVE), "Mira's grave is still in the street");
			helper.assertTrue(level.getBlockEntity(target) instanceof GraveBlockEntity g && g.villager().equals(mira),
				"Mira isn't in the last free slot (10, 0, 4), as she was");
			helper.assertTrue(level.getBlockState(old).is(ModBlocks.GRAVE), "the old-slot grave at (3, 0, 6) was moved");
			helper.assertTrue(reached[0], "the Undertaker hasn't walked to within reach of the grave he laid");
			helper.assertTrue(hops.isEmpty(), "the Undertaker hopped (stuck-teleport) on his way to the grave he laid: " + hops);
			undertaker.discard();
		});
	}

	/**
	 * A funeral at a grave in the middle of a full Level I yard ((2, 0, 4): no gap in the wall or the way in within 3): a
	 * mourner called from the street in front ({@link Mourning#gather}, as the Undertaker's funeral does) walks into the
	 * yard and stands by the grave, within the walk target's 3 blocks.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaGraveyard1122cMourner"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaGraveyard1122cMourner")
	public void qaAMournerFromTheStreetReachesAGraveInTheMiddleOfAFullYard(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = placeYard(helper, StarterBlueprints.GRAVEYARD, YARD, Rotation.NONE);
		BlockPos origin = placement.origin();
		helper.setDayTime(2000);
		fill(helper, origin, box(origin, Rotation.NONE));
		BlockPos grave = origin.offset(2, 0, 4);
		helper.assertTrue(level.getBlockState(grave).is(ModBlocks.GRAVE), "set-up: (2, 0, 4) isn't a slot of the full yard");
		Villager undertaker = walker(helper, helper.absolutePos(new BlockPos(1, 2, 27)));
		Villager mourner = helper.spawn(EntityType.VILLAGER, YARD.offset(6, 0, -3));
		// a nitwit: one who'd take the yard's brewing stand as his job walked off to it instead (seen 1 run in 3)
		mourner.setVillagerData(mourner.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NITWIT));
		// B1490: the brain is built for the profession it had (none): without a new one the "nitwit" still takes the
		// brewing stand as a potential job site and walks to it (AcquirePoi, GoToPotentialJobSite), as a born nitwit never does
		mourner.refreshBrain(level);
		Set<BlockPos> inside = new HashSet<>();
		for (BlockPos p : BlockPos.betweenClosed(origin.offset(1, -1, 1), origin.offset(11, 3, 11))) {
			inside.add(p.immutable());
		}
		int[] ticks = {0};
		helper.onEachTick(() -> {
			if (ticks[0]++ % 40 == 0) {
				Mourning.gather(level, grave, undertaker);
			}
		});
		helper.succeedWhen(() -> {
			BlockPos at = mourner.blockPosition();
			helper.assertTrue(inside.contains(at) && at.distManhattan(grave) <= 3,
				"the mourner stands at " + at.subtract(origin).toShortString() + " (the yard's own), not in the yard within 3 of the grave (2, 0, 4)");
			mourner.discard();
			undertaker.discard();
		});
	}
}
