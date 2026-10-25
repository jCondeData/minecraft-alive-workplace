package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Bug test (lane-b-1025-0337), from the builder's promise (builder.md: "Builders never copy what is inside
 * containers"): a chiseled bookshelf holding three books and a brewing stand holding three bottles are scanned and built.
 * The built ones are empty (QaBlueprintLootGameTests proves the shelf), and they must look empty too: a shelf showing
 * books it doesn't hold, or a stand showing bottles, tells the player there is something to take.
 */
public class QaBuildGhostContentsBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(20, 2, 20);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);

	//$ gametest_ticks_batch HUGE '2400' '"qaBuildGhostContents"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaBuildGhostContents")
	public void aBuiltBookshelfAndBrewingStandLookAsEmptyAsTheyAre(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.CHISELED_BOOKSHELF);
		Container shelf = helper.getBlockEntity(SRC);
		for (int i = 0; i < 3; i++) {
			shelf.setItem(i, new ItemStack(Items.BOOK));
		}
		helper.setBlock(SRC.east(1), Blocks.BREWING_STAND);
		Container stand = helper.getBlockEntity(SRC.east(1));
		for (int i = 0; i < 3; i++) {
			stand.setItem(i, new ItemStack(Items.GLASS_BOTTLE));
		}
		// (a stand shows its bottles from its next tick on; set them as that tick does)
		helper.getLevel().setBlock(helper.absolutePos(SRC.east(1)), helper.getBlockState(SRC.east(1)).setValue(BlockStateProperties.HAS_BOTTLE_0, true)
			.setValue(BlockStateProperties.HAS_BOTTLE_1, true).setValue(BlockStateProperties.HAS_BOTTLE_2, true), 2);
		helper.assertTrue(occupied(helper.getBlockState(SRC)) == 3, "the scanned shelf shows " + occupied(helper.getBlockState(SRC)) + " books, not 3");
		helper.assertTrue(occupied(helper.getBlockState(SRC.east(1))) == 3, "the scanned stand shows " + occupied(helper.getBlockState(SRC.east(1))) + " bottles, not 3");
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_ghost_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(2, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int dx = 0; dx < 2; dx++) {
			if (helper.getBlockEntity(SRC.east(dx)) instanceof Clearable c) {
				Clearable.tryClear(c);
			}
			helper.setBlock(SRC.east(dx), Blocks.AIR);
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
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			BlockPos at = helper.absolutePos(ORIGIN);
			List<String> ghosts = new ArrayList<>();
			for (int dx = 0; dx < 2; dx++) {
				BlockState built = level.getBlockState(at.east(dx));
				helper.assertTrue(built.is(Blocks.CHISELED_BOOKSHELF) || built.is(Blocks.BREWING_STAND), "not built at +" + dx + ": " + built);
				helper.assertTrue(level.getBlockEntity(at.east(dx)) instanceof Container c && c.isEmpty(), "the built " + built.getBlock() + " holds something");
				if (occupied(built) > 0) {
					ghosts.add(built.getBlock().getName().getString() + " shows " + occupied(built) + " (" + built + ")");
				}
			}
			helper.assertTrue(ghosts.isEmpty(), "built empty but showing what the scanned ones held: " + ghosts);
		});
	}

	/** How many books (or bottles) the block's state shows. */
	private static int occupied(BlockState state) {
		int n = 0;
		for (BooleanProperty p : List.of(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_0_OCCUPIED, BlockStateProperties.CHISELED_BOOKSHELF_SLOT_1_OCCUPIED,
				BlockStateProperties.CHISELED_BOOKSHELF_SLOT_2_OCCUPIED, BlockStateProperties.CHISELED_BOOKSHELF_SLOT_3_OCCUPIED,
				BlockStateProperties.CHISELED_BOOKSHELF_SLOT_4_OCCUPIED, BlockStateProperties.CHISELED_BOOKSHELF_SLOT_5_OCCUPIED,
				BlockStateProperties.HAS_BOTTLE_0, BlockStateProperties.HAS_BOTTLE_1, BlockStateProperties.HAS_BOTTLE_2)) {
			if (state.hasProperty(p) && state.getValue(p)) {
				n++;
			}
		}
		return n;
	}
}
