package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-e-1109-1913, Create and saves): Create blocks a player makes in the world from another block or item, which
 * have no item of their own: a sail dyed in place, a fluid pipe made glass with a wrench or encased in copper casing, a
 * shaft run through a metal girder. A Create schematic of them (39.6) must be built whole: wiki create.md promises a
 * schematic's blueprint "never ends with holes", and MaterialRules already lays the belt and the small bogey with the
 * item a player places them with (39.4, 39.7b). Built by a real builder from a chest holding exactly the list.
 */
public class QaCreateItemlessBlocks1109eBugCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos SRC = new BlockPos(18, 2, 20);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);
	/** Each two apart, so no two join or connect. */
	private static final String[] BLOCKS = {"create:red_sail", "create:glass_fluid_pipe", "create:encased_fluid_pipe",
		"create:metal_girder_encased_shaft"};

	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa1109e_create_itemless"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa1109e_create_itemless")
	public void aSchematicOfCreateBlocksMadeInPlaceIsBuiltWhole(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		List<String> setup = new ArrayList<>();
		for (int i = 0; i < BLOCKS.length; i++) {
			BlockPos at = SRC.east(2 * i);
			helper.setBlock(at, block(BLOCKS[i]));
			if (!helper.getBlockState(at).is(block(BLOCKS[i]))) {
				setup.add(BLOCKS[i] + " is " + helper.getBlockState(at));
			}
		}
		helper.assertTrue(setup.isEmpty(), "setup: " + setup);
		net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate drawn = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
		drawn.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(2 * BLOCKS.length - 1, 1, 1), true, Blocks.STRUCTURE_VOID);
		for (int dx = 0; dx < 2 * BLOCKS.length - 1; dx++) {
			helper.setBlock(SRC.east(dx), Blocks.AIR);
		}
		net.minecraft.server.MinecraftServer server = level.getServer();
		String owner = "gametest_itemless";
		String file = "made_in_place_" + Long.toHexString(level.getGameTime()) + ".nbt";
		ResourceLocation id = io.github.jcondedata.aliveworkplace.table.CreateSchematics.idFor(owner, file);
		java.nio.file.Path path = io.github.jcondedata.aliveworkplace.table.CreateSchematics.uploadedFolder(server).resolve(owner).resolve(file);
		try {
			java.nio.file.Files.createDirectories(path.getParent());
			net.minecraft.nbt.NbtIo.writeCompressed(drawn.save(new net.minecraft.nbt.CompoundTag()), path);
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		io.github.jcondedata.aliveworkplace.table.CreateSchematics.sync(server);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(server, id).isPresent(), "the schematic " + file + " wasn't imported as " + id);

		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		BuildPlan plan = site.plan(level);
		Map<Item, Integer> asked = plan.materials();
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : asked.entrySet()) {
			chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
		}
		String list = asked.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).toList().toString();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<String> holes = new ArrayList<>();
			for (int i = 0; i < BLOCKS.length; i++) {
				BlockPos at = helper.absolutePos(ORIGIN.east(2 * i));
				if (!level.getBlockState(at).is(block(BLOCKS[i]))) {
					holes.add(BLOCKS[i] + " built as " + level.getBlockState(at));
				}
			}
			helper.assertTrue(holes.isEmpty(), "the schematic finished with holes (list " + list + ", " + site.skipped() + " skipped): " + holes);
		});
	}

	private static Block block(String id) {
		ResourceLocation key = ResourceLocation.parse(id);
		if (!BuiltInRegistries.BLOCK.containsKey(key)) {
			throw new GameTestAssertException("no block " + id + " in this Create");
		}
		return BuiltInRegistries.BLOCK.get(key);
	}
}
