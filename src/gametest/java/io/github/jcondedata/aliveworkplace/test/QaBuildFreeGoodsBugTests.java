package io.github.jcondedata.aliveworkplace.test;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bug test (lane-b-1025-0337), from the builder's promise (builder.md: "Builders never copy what is inside
 * containers"; BlockEntityData: nothing that would hand out free items is copied), not from the code. A player scans a
 * lava cauldron, a composter full and ready with bone meal (level 8) and a beehive full of honey (honey level 5), and a
 * real builder builds that blueprint from a chest holding exactly what its material list asks. What a player can take
 * out of the finished build must have been paid for: a lava cauldron only if the list asked for a lava bucket, no ready
 * bone meal and no honey from nothing.
 */
public class QaBuildFreeGoodsBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos SRC = new BlockPos(20, 2, 20);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);

	//$ gametest_ticks_batch HUGE '2400' '"qaBuildFreeGoods"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaBuildFreeGoods")
	public void aScannedLavaCauldronFullComposterAndHoneyHiveGiveNothingForFree(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.LAVA_CAULDRON);
		helper.setBlock(SRC.east(1), Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, ComposterBlock.READY));
		helper.setBlock(SRC.east(2), Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.FACING, Direction.SOUTH).setValue(BeehiveBlock.HONEY_LEVEL, BeehiveBlock.MAX_HONEY_LEVELS));
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_free_goods_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(3, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int dx = 0; dx < 3; dx++) {
			helper.setBlock(SRC.east(dx), Blocks.AIR);
		}

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
			BlockPos at = helper.absolutePos(ORIGIN);
			BlockState cauldron = level.getBlockState(at);
			BlockState composter = level.getBlockState(at.east(1));
			BlockState hive = level.getBlockState(at.east(2));
			helper.assertTrue(cauldron.is(Blocks.CAULDRON) || cauldron.is(Blocks.LAVA_CAULDRON), "no cauldron built: " + cauldron);
			helper.assertTrue(composter.is(Blocks.COMPOSTER) && hive.is(Blocks.BEEHIVE), "not built: " + composter + ", " + hive);
			List<String> free = new ArrayList<>();
			if (cauldron.is(Blocks.LAVA_CAULDRON) && !asked.containsKey(Items.LAVA_BUCKET)) {
				free.add("a lava cauldron (a lava bucket for the taking) built from a plain cauldron");
			}
			if (composter.getValue(ComposterBlock.LEVEL) == ComposterBlock.READY && !asked.containsKey(Items.BONE_MEAL)) {
				free.add("a composter ready with bone meal it was never fed");
			}
			if (hive.getValue(BeehiveBlock.HONEY_LEVEL) > 0) {
				free.add("a beehive at honey level " + hive.getValue(BeehiveBlock.HONEY_LEVEL) + " (" + (hive.getValue(BeehiveBlock.HONEY_LEVEL) == BeehiveBlock.MAX_HONEY_LEVELS
					? "honeycomb or a honey bottle for the taking" : "honey from nothing") + ")");
			}
			helper.assertTrue(free.isEmpty(), "the builder gave away for the material list " + list + ": " + free);
		});
	}
}
