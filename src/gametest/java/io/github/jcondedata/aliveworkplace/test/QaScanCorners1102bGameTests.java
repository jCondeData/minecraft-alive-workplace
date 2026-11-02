package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b-1102-1626) of the Scan Tool from its guide page and lang text ("right-click the first corner", "now
 * right-click the opposite corner", "Marked %s × %s × %s"): the guide never says which corner comes first, so a player
 * who picks the far corner first, or two corners crossed on different axes, must get the same build; and a box wider
 * than a chunk (any 20-wide box crosses a chunk border) must be scanned whole, not just the part in the first corner's
 * chunk.
 */
public class QaScanCorners1102bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");

	private static ItemStack blueprint(ServerPlayer player) {
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT)) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	private static ResourceLocation scan(GameTestHelper helper, List<Component> seen, ServerPlayer player, BlockPos first, BlockPos second) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, first);
		QaStageThreeGameTests.click(helper, player, second);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.marked"), "the second corner said " + QaStageThreeGameTests.last(seen));
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the save said " + QaStageThreeGameTests.last(seen));
		ItemStack bp = blueprint(player);
		helper.assertTrue(!bp.isEmpty(), "the scan gave no blueprint");
		return BlueprintItem.data(bp).orElseThrow().structure();
	}

	/**
	 * A 20 × 3 × 18 build (across a chunk border on x and z) scanned far corner first, near corner second: the
	 * blueprint is 20 × 3 × 18 and, laid out again above, every block is where it was, the marker blocks at the far
	 * ends and corners included.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaScanCornersBig"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaScanCornersBig")
	public void aChunkWideScanPickedFarCornerFirstKeepsEveryBlockInPlace(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos min = new BlockPos(2, 2, 2);
		int sx = 20;
		int sy = 3;
		int sz = 18;
		BlockPos max = min.offset(sx - 1, sy - 1, sz - 1);
		Map<BlockPos, BlockState> build = new LinkedHashMap<>();
		for (int x = 0; x < sx; x++) {
			for (int z = 0; z < sz; z++) {
				build.put(new BlockPos(x, 0, z), ((x + z) % 3 == 0 ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS).defaultBlockState());
			}
		}
		build.put(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
		build.put(new BlockPos(sx - 1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState());
		build.put(new BlockPos(0, 0, sz - 1), Blocks.EMERALD_BLOCK.defaultBlockState());
		build.put(new BlockPos(0, 2, 0), Blocks.DIAMOND_BLOCK.defaultBlockState());
		build.put(new BlockPos(sx - 1, 1, sz - 1), Blocks.IRON_BLOCK.defaultBlockState());
		build.put(new BlockPos(sx - 1, 2, sz - 1), Blocks.LAPIS_BLOCK.defaultBlockState());
		build.put(new BlockPos(10, 1, 9), Blocks.OAK_LOG.defaultBlockState());
		build.forEach((p, s) -> helper.setBlock(min.offset(p), s));

		BlockPos absMin = helper.absolutePos(min);
		BlockPos absMax = helper.absolutePos(max);
		helper.assertTrue((absMin.getX() >> 4) != (absMax.getX() >> 4) && (absMin.getZ() >> 4) != (absMax.getZ() >> 4),
			"the box should cross a chunk border: " + absMin + " to " + absMax);

		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		ResourceLocation id = scan(helper, seen, player, max, min);
		StructureTemplate template = level.getStructureManager().get(id).orElseThrow();
		Vec3i size = template.getSize();
		helper.assertTrue(size.equals(new Vec3i(sx, sy, sz)), "the far-corner-first scan is " + size + ", the box was " + sx + "x" + sy + "x" + sz);

		BlockPos copy = min.above(10);
		BlockPos absCopy = helper.absolutePos(copy);
		template.placeInWorld(level, absCopy, absCopy, new StructurePlaceSettings(), RandomSource.create(1102L), 2);
		List<String> wrong = new ArrayList<>();
		for (int x = 0; x < sx; x++) {
			for (int y = 0; y < sy; y++) {
				for (int z = 0; z < sz; z++) {
					BlockPos p = new BlockPos(x, y, z);
					BlockState expected = build.getOrDefault(p, Blocks.AIR.defaultBlockState());
					BlockState there = level.getBlockState(absCopy.offset(p));
					if (!there.is(expected.getBlock())) {
						wrong.add(p.toShortString() + ": " + there.getBlock() + ", expected " + expected.getBlock());
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the far-corner-first chunk-crossing scan laid out again is wrong at " + wrong.size() + " spots: "
			+ wrong.subList(0, Math.min(8, wrong.size())));
		LOGGER.info("qa scan corners: {} x {} x {} scanned far corner first across chunks {} to {}, all {} blocks in place", sx, sy, sz,
			absMin.getX() >> 4, absMax.getX() >> 4, build.size());
		helper.succeed();
	}

	/**
	 * A small room with slabs of all three kinds, a door, a bed and a lilac on grass, scanned with corners crossed
	 * (the first at the high-x low-z bottom, the second at the low-x high-z top): a builder builds the blueprint
	 * elsewhere and every block matches, the slabs keep their kind, the door, bed and lilac both halves.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaScanCornersBuilt"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaScanCornersBuilt")
	public void aScanWithCrossedCornersIsBuiltByABuilderBlockForBlock(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos src = new BlockPos(18, 2, 18);
		Map<BlockPos, BlockState> room = room();
		room.forEach((p, s) -> helper.setBlock(src.offset(p), s));

		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		ResourceLocation id = scan(helper, seen, player, src.offset(4, 0, 0), src.offset(0, 2, 3));
		Vec3i size = level.getStructureManager().get(id).orElseThrow().getSize();
		helper.assertTrue(size.equals(new Vec3i(5, 3, 4)), "the crossed-corner scan is " + size + ", the box was 5x3x4");

		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, bench.east());
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 6));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException("the builder didn't take the crossed-corner scan " + id);
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status());
			List<String> wrong = new ArrayList<>();
			room.forEach((p, s) -> {
				BlockState there = level.getBlockState(origin.offset(p));
				if (!MaterialRules.matches(there, s)) {
					wrong.add(p.toShortString() + ": " + there + ", expected " + s);
				} else if (s.getBlock() instanceof SlabBlock && there.getValue(SlabBlock.TYPE) != s.getValue(SlabBlock.TYPE)) {
					wrong.add(p.toShortString() + ": slab " + there.getValue(SlabBlock.TYPE) + ", expected " + s.getValue(SlabBlock.TYPE));
				}
			});
			helper.assertTrue(wrong.isEmpty(), "the crossed-corner scan built again is wrong (" + site.skipped() + " skipped): " + wrong);
			LOGGER.info("qa scan corners: crossed-corner scan of {} blocks built again, {} skipped", room.size(), site.skipped());
		});
	}

	/**
	 * The 48-on-a-side limit holds the other way too: from a first corner, a second 49 across toward -x is refused
	 * with "That's 49 × 1 × 1: a scan can be at most 48 on a side" and keeps the first corner; then one 48 across
	 * toward -x and -z at once is "Marked 48 × 1 × 48".
	 */
	//$ gametest_batch AREA '"qaScanCornersLimit"'
	@GameTest(template = AREA, batch = "qaScanCornersLimit")
	public void theSizeLimitHoldsWhenTheSecondCornerIsBehindTheFirst(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.STONE);
		BlockPos a = helper.absolutePos(new BlockPos(1, 2, 1));
		BlockPos far = a.offset(-48, 0, 0);
		BlockPos edge = a.offset(-47, 0, -47);
		BlockState farWas = level.getBlockState(far);
		BlockState edgeWas = level.getBlockState(edge);
		level.setBlockAndUpdate(far, Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(edge, Blocks.STONE.defaultBlockState());
		try {
			QaStageThreeGameTests.click(helper, player, new BlockPos(1, 2, 1));
			clickAbs(level, player, far);
			String said = seen.get(seen.size() - 1).getString();
			helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.too_big"), "49 across toward -x said " + said);
			helper.assertTrue(said.equals("That's 49 × 1 × 1: a scan can be at most 48 on a side"), "the refusal reads \"" + said + "\"");
			clickAbs(level, player, edge);
			said = seen.get(seen.size() - 1).getString();
			helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.marked"), "48 x 48 toward -x -z said " + said);
			helper.assertTrue(said.startsWith("Marked 48 × 1 × 48."), "the mark reads \"" + said + "\"");
		} finally {
			level.setBlockAndUpdate(far, farWas);
			level.setBlockAndUpdate(edge, edgeWas);
		}
		helper.succeed();
	}

	private static void clickAbs(ServerLevel level, ServerPlayer player, BlockPos abs) {
		player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
			new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
	}

	/** 5 wide (x), 3 tall, 4 deep (z), from its low corner; air left out. */
	private static Map<BlockPos, BlockState> room() {
		Map<BlockPos, BlockState> out = new LinkedHashMap<>();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 4; z++) {
				out.put(new BlockPos(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState());
			}
		}
		out.put(new BlockPos(4, 0, 0), Blocks.GRASS_BLOCK.defaultBlockState());
		BlockState lilac = Blocks.LILAC.defaultBlockState();
		out.put(new BlockPos(4, 1, 0), lilac.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
		out.put(new BlockPos(4, 2, 0), lilac.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
		BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
		out.put(new BlockPos(1, 1, 0), slab.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
		out.put(new BlockPos(2, 1, 0), slab.setValue(SlabBlock.TYPE, SlabType.TOP));
		out.put(new BlockPos(3, 1, 0), slab.setValue(SlabBlock.TYPE, SlabType.DOUBLE));
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
		out.put(new BlockPos(0, 1, 2), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		out.put(new BlockPos(0, 2, 2), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		BlockState bed = Blocks.BLUE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
		out.put(new BlockPos(2, 1, 2), bed.setValue(BedBlock.PART, BedPart.FOOT));
		out.put(new BlockPos(3, 1, 2), bed.setValue(BedBlock.PART, BedPart.HEAD));
		out.put(new BlockPos(0, 2, 3), Blocks.COBBLESTONE.defaultBlockState());
		return out;
	}
}
