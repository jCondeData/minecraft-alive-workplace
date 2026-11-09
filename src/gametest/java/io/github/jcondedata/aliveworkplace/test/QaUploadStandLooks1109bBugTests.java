package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * QA (lane b, 2026-11-09), from the guide's promise for uploads (builder.md: "A build from a file holds no more than your
 * own blocks could") and BlueprintEntities' (put up "empty ... never invulnerable or fixed in place"): an armor stand and
 * an item frame from an uploaded file are put up as a player in survival could put them up. A file made in a creative
 * world carries looks no survival player can give: an armor stand sixteen times its size (the scale attribute), glowing
 * (an outline every player sees through walls), invisible or floating, and an invisible, glowing item frame.
 * BlueprintEntities strips a list of keys (items, Invisible-to-damage, Fixed, ...) but keeps the rest, so these stay.
 */
public class QaUploadStandLooks1109bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(22, 2, 22);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);

	//$ gametest_ticks_batch HUGE '2400' '"qaUploadStandLooks1109b"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUploadStandLooks1109b")
	public void anArmorStandAndAFrameFromAnUploadedFileArePutUpAsASurvivalPlayerCould(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		// a creative world's decorations: a giant glowing invisible floating stand, and an invisible glowing frame
		helper.setBlock(SRC, Blocks.OAK_PLANKS);
		helper.setBlock(SRC.east(), Blocks.STONE);
		BlockPos at = helper.absolutePos(SRC.above());
		ArmorStand stand = new ArmorStand(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		stand.getAttribute(Attributes.SCALE).setBaseValue(16.0);
		stand.setGlowingTag(true);
		stand.setInvisible(true);
		stand.setNoGravity(true);
		level.addFreshEntity(stand);
		ItemFrame frame = new ItemFrame(level, helper.absolutePos(SRC.east().south()), Direction.SOUTH);
		frame.setInvisible(true);
		frame.setGlowingTag(true);
		level.addFreshEntity(frame);

		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(2, 2, 2), true, Blocks.STRUCTURE_VOID);
		CompoundTag saved = template.save(new CompoundTag());
		stand.discard();
		frame.discard();
		for (BlockPos p : List.of(SRC, SRC.east())) {
			helper.setBlock(p, Blocks.AIR);
		}
		ListTag entities = saved.getList("entities", Tag.TAG_COMPOUND);
		helper.assertTrue(entities.size() == 2, "(setup) the file holds " + entities.size() + " entities, not the stand and the frame");
		// control: the stand data the file carries, loaded into a fresh stand, is giant and glowing
		for (int i = 0; i < entities.size(); i++) {
			CompoundTag nbt = entities.getCompound(i).getCompound("nbt");
			if ("minecraft:armor_stand".equals(nbt.getString("id"))) {
				ArmorStand control = EntityType.ARMOR_STAND.create(level);
				control.load(nbt);
				helper.assertTrue(control.getScale() > 15 && control.hasGlowingTag() && control.isInvisible(),
					"(setup) the file's stand isn't giant, glowing and invisible: scale " + control.getScale());
				control.discard();
			}
		}
		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(saved, out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		ResourceLocation id;
		try {
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/qa_standlooks",
				"qa1109b_stand_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
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
		AABB around = new AABB(helper.absolutePos(ORIGIN)).inflate(4);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, around, Entity::isAlive);
			List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, around, Entity::isAlive);
			helper.assertTrue(stands.size() == 1 && frames.size() == 1, "the builder put up " + stands.size() + " armor stands and "
				+ frames.size() + " item frames, not 1 and 1");
			List<String> wrong = new ArrayList<>();
			ArmorStand built = stands.get(0);
			if (built.getScale() != 1.0f) {
				wrong.add("the stand is " + built.getScale() + " times its size");
			}
			if (built.hasGlowingTag()) {
				wrong.add("the stand glows (seen through walls)");
			}
			if (built.isInvisible()) {
				wrong.add("the stand is invisible");
			}
			if (built.isNoGravity()) {
				wrong.add("the stand floats (no gravity)");
			}
			if (frames.get(0).isInvisible()) {
				wrong.add("the frame is invisible");
			}
			if (frames.get(0).hasGlowingTag()) {
				wrong.add("the frame glows (seen through walls)");
			}
			helper.assertTrue(wrong.isEmpty(), "an armor stand and an item frame from an uploaded file, put up by a builder: "
				+ String.join("; ", wrong) + ". A survival player's stand and frame do none of that (guide: a build from a file"
				+ " holds no more than your own blocks could)");
		});
	}
}
