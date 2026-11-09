package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * QA (lane b, 2026-11-08), from 39.6 (a Create schematic uploaded again under its name replaces the blueprint of that
 * id, BlueprintImporter.importAs) and the builder's invariants (CLAUDE.md: all progress lives in BuildSite, BuilderWork
 * stays restartable at any tick; "done" means the blocks stand as drawn). A player re-uploads a changed file while a
 * builder is half-way through the old one, then the server restarts: the build must still end with the blueprint it now
 * builds standing whole, not with the steps the old cursor had passed left out.
 */
public class QaReplacedMidBuild1108GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(22, 2, 22);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);

	//$ gametest_ticks_batch HUGE '3000' '"qaReplacedMidBuild1108"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaReplacedMidBuild1108")
	public void aBlueprintReplacedHalfWayAndReloadedIsBuiltWhole(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		// The first file: a 5x1x5 stone brick pad. The second, uploaded under the same name half-way: a 3x2x3 oak plank
		// block (a different size and block, so none of its blocks is already standing)
		byte[] first = file(helper, 5, 1, 5, Blocks.STONE_BRICKS);
		byte[] second = file(helper, 3, 2, 3, Blocks.OAK_PLANKS);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "create_schematics/qa/replaced_" + Long.toHexString(level.getGameTime()));
		try {
			BlueprintImporter.importAs(level.getServer(), id, first);
		} catch (Exception e) {
			throw new GameTestAssertException("(setup) the first file didn't import: " + e);
		}

		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager[] builder = {helper.spawn(EntityType.VILLAGER, BENCH.east())};
		Builders.employ(level, builder[0], helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder[0], null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		UUID siteId = site.id();
		boolean[] replaced = {false};
		helper.onEachTick(() -> {
			if (replaced[0]) {
				return;
			}
			BuildSite now = BuildSiteManager.get(level).get(siteId);
			if (now == null || now.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			long placed = BlockPos.betweenClosedStream(helper.absolutePos(ORIGIN), helper.absolutePos(ORIGIN).offset(4, 0, 4))
				.filter(p -> level.getBlockState(p).is(Blocks.STONE_BRICKS)).count();
			if (placed >= 12 && placed < 25) {
				replaced[0] = true;
				try {
					BlueprintImporter.importAs(level.getServer(), id, second);
				} catch (Exception e) {
					throw new GameTestAssertException("(setup) the second file didn't import: " + e);
				}
				builder[0] = reload(helper, level, builder[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(replaced[0], "(setup) the build was never caught half-way through its stone bricks");
			BuildSite now = BuildSiteManager.get(level).get(siteId);
			helper.assertTrue(now == null, "the build hasn't ended: " + (now == null ? "-" : now.stage() + " " + now.status() + " missing " + now.missing()));
			int planks = 0;
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(ORIGIN), helper.absolutePos(ORIGIN).offset(2, 1, 2))) {
				if (level.getBlockState(p).is(Blocks.OAK_PLANKS)) {
					planks++;
				}
			}
			helper.assertTrue(planks == 18, "the build ended with " + planks + " of the re-uploaded blueprint's 18 oak planks standing:"
				+ " the steps the old blueprint's cursor had passed were left out");
		});
	}

	/** A structure file of a w x h x d box of {@code block}, drawn and saved as a player's upload would be. */
	private static byte[] file(GameTestHelper helper, int w, int h, int d, Block block) {
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(SRC, SRC.offset(w - 1, h - 1, d - 1))) {
			helper.setBlock(p, block);
		}
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(w, h, d), false, Blocks.STRUCTURE_VOID);
		for (BlockPos p : BlockPos.betweenClosed(SRC, SRC.offset(w - 1, h - 1, d - 1))) {
			helper.setBlock(p, Blocks.AIR);
		}
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(template.save(new CompoundTag()), out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
	}

	/** A restart: the sites and the builder saved and loaded back (QaBuildsGameTests' way). */
	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}
}
