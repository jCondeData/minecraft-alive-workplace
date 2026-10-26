package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildEntities;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane B), from ROADMAP 23.8 (a placed blueprint is built turned and mirrored as the player chose) and the
 * builder page ("item frames, paintings and armour stands go up last", each paid with its item): in every starter build
 * that has them, put up in all four turns, plain and mirrored, every item frame and painting hangs (none left out for
 * want of a wall) on the wall the blueprint hangs it on, turned and mirrored with the build.
 */
public class QaTurnedFramesGameTests {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");

	//$ gametest_ticks_batch HUGE_AREA '200' '"qaTurnedFrames"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qaTurnedFrames")
	public void everyBuildsFramesAndPaintingsHangTurnedAndMirrored(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer()));
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		BlockPos corner = helper.absolutePos(new BlockPos(1, 2, 1));
		BoundingBox area = new BoundingBox(corner.getX(), corner.getY(), corner.getZ(), corner.getX() + 28, corner.getY() + 26, corner.getZ() + 28);
		List<String> wrong = new ArrayList<>();
		List<String> builds = new ArrayList<>();
		int hung = 0;
		for (StarterBlueprints.Entry entry : StarterBlueprints.ALL) {
			Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow();
			long hanging = blueprint.entities().stream().filter(e -> isHanging(e.nbt())).count();
			if (hanging == 0) {
				continue;
			}
			builds.add(entry.id().getPath() + " (" + hanging + ")");
			for (Mirror mirror : List.of(Mirror.NONE, Mirror.FRONT_BACK)) {
				for (Rotation rotation : Rotation.values()) {
					String where = entry.id().getPath() + " " + rotation + (mirror == Mirror.NONE ? "" : " mirrored");
					BlueprintData.Placement probe = new BlueprintData.Placement(level.dimension().location(), corner, rotation, mirror);
					BoundingBox box = BuildPlan.create(blueprint, probe).bounds();
					BlockPos origin = corner.offset(corner.getX() - box.minX(), corner.getY() - box.minY(), corner.getZ() - box.minZ());
					BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, mirror);
					BuildPlan plan = BuildPlan.create(blueprint, placement);
					if (!area.isInside(new BlockPos(plan.bounds().maxX(), plan.bounds().maxY(), plan.bounds().maxZ()))) {
						wrong.add(where + ": too big for the test area " + plan.bounds());
						continue;
					}
					clear(level, area);
					plan.steps(BuildPlan.Stage.CLEAR).forEach(s -> level.setBlock(s.pos(), s.state(), 2 | 16));
					int left = BuildEntities.placeAll(level, plan, List.of());
					if (left != 0) {
						wrong.add(where + ": " + left + " left out");
					}
					for (BuildPlan.EntityStep step : plan.entities()) {
						if (!isHanging(step.nbt())) {
							continue;
						}
						Direction drawn = step.nbt().getString("id").equals("minecraft:painting")
							? Direction.from2DDataValue(step.nbt().getByte("facing")) : Direction.from3DDataValue(step.nbt().getByte("Facing"));
						Direction expected = rotation.rotate(mirror.mirror(drawn));
						List<Entity> there = level.getEntities((Entity) null, new AABB(step.pos(), step.pos()).inflate(0.6),
							e -> e instanceof HangingEntity && e.isAlive());
						if (there.isEmpty()) {
							wrong.add(where + ": nothing hangs at " + BlockPos.containing(step.pos()).subtract(origin).toShortString());
							continue;
						}
						HangingEntity frame = (HangingEntity) there.get(0);
						if (frame.getDirection() != expected || !frame.survives()) {
							wrong.add(where + ": the " + frame.getType().toShortString() + " at " + frame.blockPosition().subtract(origin).toShortString()
								+ " looks " + frame.getDirection() + " (survives " + frame.survives() + "), drawn looking " + drawn + " so expected " + expected);
						}
						hung++;
					}
					clear(level, area);
				}
			}
		}
		LOGGER.info("qa turned frames: {} hung in 8 placements of {}; wrong {}", hung, builds, wrong);
		helper.assertTrue(!builds.isEmpty(), "no starter build has an item frame or painting to check");
		helper.assertTrue(wrong.isEmpty(), "frames and paintings put up wrong: " + wrong.subList(0, Math.min(10, wrong.size())) + (wrong.size() > 10 ? " and " + (wrong.size() - 10) + " more" : ""));
		helper.succeed();
	}

	/**
	 * The player's view: a builder builds the Guildhall placed turned a quarter and mirrored (the style screen's mirror
	 * button, then a sneak-click to turn it) and finishes it with its item frame hanging where the blueprint hangs it.
	 */
	//$ gametest_ticks_batch HUGE_AREA '24000' '"qaTurnedFrameBuilt"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 24000, batch = "qaTurnedFrameBuilt")
	public void aBuilderHangsTheGuildhallsFrameWhenItIsTurnedAndMirrored(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		Blueprint blueprint = BlueprintLibrary.get(level, StarterBlueprints.GUILDHALL.id()).orElseThrow();
		BlockPos corner = helper.absolutePos(new BlockPos(6, 2, 6));
		BlueprintData.Placement probe = new BlueprintData.Placement(level.dimension().location(), corner, Rotation.CLOCKWISE_90, Mirror.FRONT_BACK);
		BoundingBox box = BuildPlan.create(blueprint, probe).bounds();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(),
			corner.offset(corner.getX() - box.minX(), 0, corner.getZ() - box.minZ()), Rotation.CLOCKWISE_90, Mirror.FRONT_BACK);
		BuildSite site = Builders.start(level, builder, null, StarterBlueprints.GUILDHALL.id(), placement);
		helper.assertTrue(site != null, "the builder didn't take the Guildhall");
		List<BuildPlan.EntityStep> frames = site.plan(level).entities().stream().filter(e -> isHanging(e.nbt())).toList();
		helper.assertTrue(!frames.isEmpty(), "the Guildhall has no item frame to check");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status());
			for (BuildPlan.EntityStep step : frames) {
				helper.assertTrue(BuildEntities.isPresent(level, step), "the turned, mirrored Guildhall was finished without its item frame at "
					+ BlockPos.containing(step.pos()).subtract(placement.origin()).toShortString() + " (" + site.skipped() + " skipped)");
			}
			LOGGER.info("qa turned frame built: the turned, mirrored Guildhall's {} frame(s) hang", frames.size());
		});
	}

	private static boolean isHanging(CompoundTag nbt) {
		String id = nbt.getString("id");
		return id.equals("minecraft:item_frame") || id.equals("minecraft:glow_item_frame") || id.equals("minecraft:painting");
	}

	private static void clear(ServerLevel level, BoundingBox area) {
		level.getEntities((Entity) null, AABB.of(area).inflate(1), e -> e instanceof HangingEntity).forEach(Entity::discard);
		BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())
			.forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16));
	}
}
