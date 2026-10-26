package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildEntities;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * B439: a blueprint's entities were turned before they were mirrored, while its blocks are mirrored and then turned
 * (B438), so a quarter-turned, mirrored item frame faced away from its wall and was left out. This sweeps every entity
 * kind a builder puts up (item frame on a wall and on a floor, glow item frame, a one- and a two-wide painting, an
 * armour stand) through all four turns and all three mirror settings: each must stand where the blueprint puts it,
 * face the way the turned, mirrored blueprint faces it, hang on its wall, and keep that facing when saved and loaded.
 */
public class B439GameTests {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final Vec3i SIZE = new Vec3i(9, 6, 9);
	private static final float STAND_YAW = 30f;

	//$ gametest_ticks_batch HUGE_AREA '200' '"b439Sweep"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "b439Sweep")
	public void everyEntityKindFacesRightInEveryTurnAndMirror(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer()));
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());

		// The blueprint, drawn in the world and captured like a saved structure: a stone cube with something on each side.
		BlockPos source = helper.absolutePos(new BlockPos(1, 2, 1));
		BoundingBox sourceBox = BoundingBox.fromCorners(source, source.offset(SIZE.getX() - 1, SIZE.getY() - 1, SIZE.getZ() - 1));
		clear(level, sourceBox);
		BlockPos.betweenClosed(source.offset(3, 1, 3), source.offset(5, 3, 5)).forEach(p -> level.setBlock(p, Blocks.STONE.defaultBlockState(), 2 | 16));
		level.setBlock(source.offset(1, 0, 7), Blocks.STONE.defaultBlockState(), 2 | 16);
		var paintings = level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT);
		List<Entity> drawn = List.of(
			new ItemFrame(level, source.offset(4, 2, 2), Direction.NORTH),
			new GlowItemFrame(level, source.offset(6, 2, 4), Direction.EAST),
			new ItemFrame(level, source.offset(4, 4, 4), Direction.UP),
			new Painting(level, source.offset(4, 2, 6), Direction.SOUTH, paintings.getHolderOrThrow(PaintingVariants.KEBAB)),
			new Painting(level, source.offset(2, 2, 4), Direction.WEST, paintings.getHolderOrThrow(PaintingVariants.POOL)),
			stand(level, source));
		for (Entity e : drawn) {
			helper.assertTrue(!(e instanceof HangingEntity h) || h.survives(), "the drawn " + e.getType().toShortString() + " doesn't hang");
			level.addFreshEntity(e);
		}
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, source, SIZE, true, Blocks.STRUCTURE_VOID);
		clear(level, sourceBox);
		Blueprint blueprint = Blueprint.fromTemplate(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "b439_sweep"), template,
			Lookup.lookup(BuiltInRegistries.BLOCK));
		helper.assertTrue(blueprint.entities().size() == drawn.size(), "the blueprint kept " + blueprint.entities().size() + " of " + drawn.size() + " entities");

		BlockPos corner = helper.absolutePos(new BlockPos(14, 2, 14));
		BoundingBox area = BoundingBox.fromCorners(corner, corner.offset(SIZE.getX() - 1, SIZE.getY() - 1, SIZE.getZ() - 1));
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		for (Mirror mirror : Mirror.values()) {
			for (Rotation rotation : Rotation.values()) {
				String where = rotation + " " + mirror;
				BoundingBox box = BuildPlan.create(blueprint, new BlueprintData.Placement(level.dimension().location(), corner, rotation, mirror)).bounds();
				BlockPos origin = corner.offset(corner.getX() - box.minX(), corner.getY() - box.minY(), corner.getZ() - box.minZ());
				BuildPlan plan = BuildPlan.create(blueprint, new BlueprintData.Placement(level.dimension().location(), origin, rotation, mirror));
				clear(level, area);
				for (Blueprint.Entry block : blueprint.blocks()) {
					if (!block.state().isAir() && !block.state().is(Blocks.STRUCTURE_VOID)) {
						BlockPos at = origin.offset(StructureTemplate.transform(block.pos(), mirror, rotation, BlockPos.ZERO));
						level.setBlock(at, MaterialRules.transformed(block.state(), mirror, rotation), 2 | 16);
					}
				}
				int left = BuildEntities.placeAll(level, plan, List.of());
				if (left != 0) {
					wrong.add(where + ": " + left + " left out");
				}
				for (BuildPlan.EntityStep step : plan.entities()) {
					String id = step.nbt().getString("id");
					EntityType<?> type = EntityType.byString(id).orElseThrow();
					List<Entity> there = level.getEntities((Entity) null, new AABB(step.pos(), step.pos()).inflate(0.6), e -> e.getType() == type && e.isAlive());
					if (there.size() != 1) {
						wrong.add(where + ": " + there.size() + " " + id + " at " + BlockPos.containing(step.pos()).subtract(origin).toShortString());
						continue;
					}
					Entity placed = there.get(0);
					String facing = facing(placed, step, rotation, mirror);
					if (facing != null) {
						wrong.add(where + ": " + facing);
					}
					// Saved and loaded (the server restarting): it keeps that facing.
					if (placed.position().distanceTo(step.pos()) > 0.01) {
						wrong.add(where + ": the " + id + " is at " + placed.position() + ", the blueprint puts it at " + step.pos());
					}
					CompoundTag saved = new CompoundTag();
					placed.save(saved);
					placed.discard(); // so the loaded copy doesn't overlap it
					Entity loaded = EntityType.create(saved, level).orElse(null);
					String reloaded = loaded == null ? id + " didn't load" : facing(loaded, step, rotation, mirror);
					if (loaded != null && loaded.position().distanceTo(step.pos()) > 0.01) {
						reloaded = "the " + id + " moved to " + loaded.position();
					}
					if (reloaded != null) {
						wrong.add(where + " after a reload: " + reloaded);
					}
					checked++;
				}
				clear(level, area);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "entities put up wrong: " + wrong.subList(0, Math.min(12, wrong.size()))
			+ (wrong.size() > 12 ? " and " + (wrong.size() - 12) + " more" : ""));
		helper.assertTrue(checked == drawn.size() * 12, "checked " + checked + " entities, not " + drawn.size() * 12);
		helper.succeed();
	}

	/** Null if the entity faces the way the turned, mirrored blueprint faces it (and hangs), else what's wrong. */
	private static String facing(Entity entity, BuildPlan.EntityStep step, Rotation rotation, Mirror mirror) {
		if (entity instanceof HangingEntity hanging) {
			Direction drawn = step.nbt().getString("id").equals("minecraft:painting")
				? Direction.from2DDataValue(step.nbt().getByte("facing")) : Direction.from3DDataValue(step.nbt().getByte("Facing"));
			Direction expected = rotation.rotate(mirror.mirror(drawn));
			if (hanging.getDirection() != expected || !hanging.survives()) {
				return "the " + entity.getType().toShortString() + " looks " + hanging.getDirection() + " (hangs " + hanging.survives()
					+ "), drawn looking " + drawn + " so expected " + expected;
			}
			return null;
		}
		// An armour stand: its yaw mirrored (x flipped for FRONT_BACK, z for LEFT_RIGHT), then turned clockwise.
		float mirrored = switch (mirror) {
			case FRONT_BACK -> -STAND_YAW;
			case LEFT_RIGHT -> 180f - STAND_YAW;
			default -> STAND_YAW;
		};
		float expected = mirrored + switch (rotation) {
			case CLOCKWISE_90 -> 90f;
			case CLOCKWISE_180 -> 180f;
			case COUNTERCLOCKWISE_90 -> 270f;
			default -> 0f;
		};
		if (Math.abs(Mth.wrapDegrees(entity.getYRot() - expected)) > 0.5f) {
			return "the armour stand looks " + Mth.wrapDegrees(entity.getYRot()) + "°, expected " + Mth.wrapDegrees(expected) + "°";
		}
		return null;
	}

	private static ArmorStand stand(ServerLevel level, BlockPos source) {
		ArmorStand stand = new ArmorStand(level, source.getX() + 1.5, source.getY() + 1, source.getZ() + 7.5);
		stand.setYRot(STAND_YAW);
		stand.setYBodyRot(STAND_YAW);
		stand.setYHeadRot(STAND_YAW);
		stand.setNoGravity(true);
		return stand;
	}

	private static void clear(ServerLevel level, BoundingBox box) {
		level.getEntities((Entity) null, AABB.of(box).inflate(1), e -> e instanceof HangingEntity || e instanceof ArmorStand).forEach(Entity::discard);
		BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
			.forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16));
	}
}
