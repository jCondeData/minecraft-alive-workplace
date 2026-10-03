package io.github.jcondedata.aliveworkplace.test;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * Tester's checks for bug B6 (villagers from village house templates stuck in walls): the house turned every way the
 * village jigsaw turns it, with the villager piece attached the way the jigsaw attaches it, and villagers that don't
 * come from a template left where they were put.
 */
public class StructureVillagerGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	//$ gametest_ticks_batch AREA '200' '"b6DesertHouseRotatedNone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b6DesertHouseRotatedNone")
	public void b6DesertHouseRotatedNone(GameTestHelper helper) {
		rotatedHouse(helper, "desert", "desert_small_house_7", Rotation.NONE);
	}

	//$ gametest_ticks_batch AREA '200' '"b6DesertHouseRotated90"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b6DesertHouseRotated90")
	public void b6DesertHouseRotated90(GameTestHelper helper) {
		rotatedHouse(helper, "desert", "desert_small_house_7", Rotation.CLOCKWISE_90);
	}

	//$ gametest_ticks_batch AREA '200' '"b6DesertHouseRotated180"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b6DesertHouseRotated180")
	public void b6DesertHouseRotated180(GameTestHelper helper) {
		rotatedHouse(helper, "desert", "desert_small_house_7", Rotation.CLOCKWISE_180);
	}

	//$ gametest_ticks_batch AREA '200' '"b6DesertHouseRotated270"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b6DesertHouseRotated270")
	public void b6DesertHouseRotated270(GameTestHelper helper) {
		rotatedHouse(helper, "desert", "desert_small_house_7", Rotation.COUNTERCLOCKWISE_90);
	}

	/**
	 * Places the house with the given rotation, then its villager piece once for every rotation the jigsaw could give it.
	 * Each villager must start over its own spot's block column (so it falls onto that floor); the last one is left to
	 * fall and must stand on the floor level, unhurt and not in a wall.
	 */
	private static void rotatedHouse(GameTestHelper helper, String style, String houseName, Rotation rotation) {
		rotatedHouse(helper, style, houseName, rotation, "minecraft:village/" + style + "/villagers/unemployed", Villager.class);
	}

	private static <T extends net.minecraft.world.entity.Mob> void rotatedHouse(GameTestHelper helper, String style, String houseName,
			Rotation rotation, String villagerPiece, Class<T> kind) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		var manager = level.getStructureManager();
		var empty = level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST)
			.getHolderOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, ResourceLocation.withDefaultNamespace("empty")));
		BlockPos origin = helper.absolutePos(new BlockPos(11, 1, 11));
		var house = StructurePoolElement.legacy("minecraft:village/" + style + "/houses/" + houseName, empty)
			.apply(StructureTemplatePool.Projection.RIGID);
		BoundingBox houseBox = house.getBoundingBox(manager, origin, rotation);
		BoundingBox box = houseBox.inflatedBy(2);
		helper.assertTrue(house.place(manager, level, level.structureManager(), level.getChunkSource().getGenerator(), origin, origin,
			rotation, box, RandomSource.create(1), LiquidSettings.APPLY_WATERLOGGING, false), houseName + " did not place");

		StructureTemplate.StructureBlockInfo socket = house.getShuffledJigsawBlocks(manager, origin, rotation, RandomSource.create(1)).stream()
			.filter(j -> j.nbt() != null && j.nbt().getString("pool").contains("/villagers"))
			.findFirst().orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException(houseName + " has no villager jigsaw"));
		BlockPos target = socket.pos().relative(JigsawBlock.getFrontFacing(socket.state()));

		var piece = StructurePoolElement.legacy(villagerPiece, empty)
			.apply(StructureTemplatePool.Projection.RIGID);
		List<String> checked = new ArrayList<>();
		T last = null;
		for (Rotation pieceRotation : Rotation.values()) {
			StructureTemplate.StructureBlockInfo plug = piece.getShuffledJigsawBlocks(manager, BlockPos.ZERO, pieceRotation, RandomSource.create(1)).stream()
				.filter(j -> JigsawBlock.canAttach(socket, j)).findFirst().orElse(null);
			if (plug == null) {
				continue;
			}
			BlockPos pieceOrigin = target.subtract(plug.pos());
			for (var old : level.getEntitiesOfClass(kind, AABB.of(box))) {
				old.discard();
			}
			helper.assertTrue(piece.place(manager, level, level.structureManager(), level.getChunkSource().getGenerator(), pieceOrigin, pieceOrigin,
				pieceRotation, box, RandomSource.create(1), LiquidSettings.APPLY_WATERLOGGING, false), "the villager piece did not place");
			List<T> villagers = level.getEntitiesOfClass(kind, AABB.of(box));
			helper.assertTrue(villagers.size() == 1, villagers.size() + " villagers placed with piece rotation " + pieceRotation);
			T v = villagers.get(0);
			AABB b = v.getBoundingBox();
			String where = String.format("house %s, piece %s: villager box x %.2f..%.2f z %.2f..%.2f",
				rotation, pieceRotation, b.minX - target.getX(), b.maxX - target.getX(), b.minZ - target.getZ(), b.maxZ - target.getZ());
			helper.assertTrue(b.minX >= target.getX() && b.maxX <= target.getX() + 1 && b.minZ >= target.getZ() && b.maxZ <= target.getZ() + 1,
				"the villager does not start over its spot: " + where);
			checked.add(pieceRotation.toString());
			last = v;
		}
		helper.assertTrue(!checked.isEmpty(), "no piece rotation attaches to " + houseName + "'s villager jigsaw");
		T villager = last;
		helper.runAfterDelay(80, () -> {
			String at = String.format("%.2f %.2f %.2f from the spot", villager.getX() - target.getX(), villager.getY() - target.getY(), villager.getZ() - target.getZ());
			helper.assertTrue(villager.isAlive(), "the villager died (" + rotation + ")");
			helper.assertFalse(villager.isInWall(), "the villager is in a wall (" + rotation + "): " + at);
			helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "the villager was hurt (" + rotation + "): " + villager.getHealth());
			// It may wander off its spot once it has landed (villagers walk), but never up onto a step or into the ceiling.
			helper.assertTrue(villager.getBlockY() == target.getY() && villager.onGround(), "the villager is not on the house floor (" + rotation + "): " + at);
			helper.succeed();
		});
	}

	/**
	 * Bug B10: an abandoned (zombie) desert village's house 7 puts its zombie villager on the same off-centre spot in the
	 * same 1-wide corridor; it must land on the corridor floor too, not in the wall (the house's roof keeps the sun off).
	 */
	//$ gametest_ticks_batch AREA '200' '"b10ZombieDesertHouseRotatedNone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b10ZombieDesertHouseRotatedNone")
	public void b10ZombieDesertHouseRotatedNone(GameTestHelper helper) {
		zombieHouse(helper, Rotation.NONE);
	}

	//$ gametest_ticks_batch AREA '200' '"b10ZombieDesertHouseRotated90"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b10ZombieDesertHouseRotated90")
	public void b10ZombieDesertHouseRotated90(GameTestHelper helper) {
		zombieHouse(helper, Rotation.CLOCKWISE_90);
	}

	//$ gametest_ticks_batch AREA '200' '"b10ZombieDesertHouseRotated180"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b10ZombieDesertHouseRotated180")
	public void b10ZombieDesertHouseRotated180(GameTestHelper helper) {
		zombieHouse(helper, Rotation.CLOCKWISE_180);
	}

	//$ gametest_ticks_batch AREA '200' '"b10ZombieDesertHouseRotated270"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b10ZombieDesertHouseRotated270")
	public void b10ZombieDesertHouseRotated270(GameTestHelper helper) {
		zombieHouse(helper, Rotation.COUNTERCLOCKWISE_90);
	}

	private static void zombieHouse(GameTestHelper helper, Rotation rotation) {
		rotatedHouse(helper, "desert/zombie", "desert_small_house_7", rotation, "minecraft:village/desert/zombie/villagers/unemployed",
			net.minecraft.world.entity.monster.ZombieVillager.class);
	}

	/**
	 * Villagers that don't come from a structure template (spawn eggs, breeding, /summon, natural spawns) stay exactly
	 * where they were put, even off the centre of their block.
	 */
	//$ gametest_batch '"aliveworkplace_test:build_area"' '"b6OtherSpawnsAreNotMoved"'
	@GameTest(template = "aliveworkplace_test:build_area", batch = "b6OtherSpawnsAreNotMoved")
	public void b6OtherSpawnsAreNotMoved(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos spot = helper.absolutePos(new BlockPos(3, 2, 3));
		MobSpawnType[] types = {MobSpawnType.SPAWN_EGG, MobSpawnType.BREEDING, MobSpawnType.COMMAND, MobSpawnType.NATURAL,
			MobSpawnType.CHUNK_GENERATION, MobSpawnType.EVENT, MobSpawnType.SPAWNER, MobSpawnType.CONVERSION};
		for (MobSpawnType type : types) {
			Villager v = EntityType.VILLAGER.create(level);
			double x = spot.getX() + 0.72;
			double z = spot.getZ() + 0.63;
			v.moveTo(x, spot.getY(), z, 0, 0);
			v.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), type, null);
			helper.assertTrue(v.getX() == x && v.getZ() == z && v.getY() == spot.getY(),
				String.format("a %s villager was moved from 0.72 0.63 to %.2f %.2f", type, v.getX() - spot.getX(), v.getZ() - spot.getZ()));
			v.discard();
		}
		// And the structure one, in the same open spot, is centred.
		Villager v = EntityType.VILLAGER.create(level);
		v.moveTo(spot.getX() + 0.72, spot.getY(), spot.getZ() + 0.63, 0, 0);
		v.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.STRUCTURE, null);
		helper.assertTrue(v.getX() == spot.getX() + 0.5 && v.getZ() == spot.getZ() + 0.5 && v.getY() == spot.getY(),
			String.format("a structure villager was not centred: %.2f %.2f %.2f", v.getX() - spot.getX(), v.getY() - spot.getY(), v.getZ() - spot.getZ()));
		v.discard();
		helper.succeed();
	}
}
