package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * QA (lane b, 2026-11-08), from BlueprintEntities' promise (builder.md: the entities a builder puts up are "always put
 * up empty ... and never invulnerable or fixed in place") and the upload rule (workplaceAllowUploads, on by default).
 * Vanilla's ArmorStand.hurt ignores every hit on an invisible stand, so an uploaded file's "Invisible" stand would be
 * one a player can't break. A file with a plain stand and an invisible one is built; a survival player punches each
 * twice, as breaking a stand takes, and both must break.
 */
public class QaUploadedStands1108BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(22, 2, 22);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);

	//$ gametest_ticks_batch HUGE '2400' '"qaUploadedStands1108"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUploadedStands1108")
	public void anInvisibleStandFromAnUploadedFileCanStillBeBroken(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		// The uploaded file: a 3x1x1 stone floor, a plain stand on its west end and an invisible stand on its east end
		for (int dx = 0; dx < 3; dx++) {
			helper.setBlock(SRC.east(dx), Blocks.STONE);
		}
		ArmorStand plain = helper.spawn(EntityType.ARMOR_STAND, SRC.above());
		ArmorStand ghost = helper.spawn(EntityType.ARMOR_STAND, SRC.east(2).above());
		ghost.setInvisible(true);
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(3, 2, 1), true, Blocks.STRUCTURE_VOID);
		helper.assertTrue(template.save(new CompoundTag()).getList("entities", 10).size() == 2, "(setup) the file doesn't hold both stands");
		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(template.save(new CompoundTag()), out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		plain.discard();
		ghost.discard();
		for (int dx = 0; dx < 3; dx++) {
			helper.setBlock(SRC.east(dx), Blocks.AIR);
		}
		ResourceLocation id;
		try {
			// the import a player's upload at the Table goes through (TableServer: folder "uploads/<player>")
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/qa_stander",
				"stands_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
		} catch (Exception e) {
			throw new GameTestAssertException("(setup) the upload didn't import: " + e);
		}

		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		AABB area = new AABB(helper.absolutePos(ORIGIN)).inflate(4);
		StringBuilder unbroken = new StringBuilder();
		boolean[] punched = {false};
		helper.succeedWhen(() -> {
			if (punched[0]) {
				// judged once, on the first tick both stands stood (a broken stand is gone the tick after)
				helper.assertTrue(unbroken.isEmpty(), "two punches from a survival player didn't break " + unbroken
					+ "put up from an uploaded file (BlueprintEntities: never invulnerable); vanilla ignores every hit on an invisible stand");
				player.discard();
				return;
			}
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, area, ArmorStand::isAlive);
			helper.assertTrue(stands.size() == 2, "the builder didn't put up both stands: " + stands.stream().map(st -> (st.isInvisible() ? "invisible at " : "plain at ") + st.blockPosition().subtract(helper.absolutePos(ORIGIN))).toList() + ", skipped " + site.skipped());
			BlockPos at = helper.absolutePos(ORIGIN);
			player.teleportTo(at.getX() + 1.5, at.getY() + 1, at.getZ() + 2.5);
			punched[0] = true;
			for (ArmorStand stand : stands) {
				player.attack(stand);
				player.attack(stand);
				if (stand.isAlive()) {
					unbroken.append(stand.isInvisible() ? "the invisible stand " : "the plain stand ");
				}
			}
			helper.assertTrue(unbroken.isEmpty(), "two punches from a survival player didn't break " + unbroken
				+ "put up from an uploaded file (BlueprintEntities: never invulnerable); vanilla ignores every hit on an invisible stand");
			player.discard();
		});
	}
}
