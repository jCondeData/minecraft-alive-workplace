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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane B), from the Scan Tool's guide page ("Built something you love? The Scan Tool turns it into a blueprint, so
 * the village's builders can build it again") and 23.8 (a blueprint is built turned as the player placed it): a small
 * room a player built by hand (a floor, a back wall, two double chests lying either way, a door, a bed, a wall torch, a
 * hanging lantern, an upside-down stair and a ladder) is scanned with real clicks, and a builder builds the blueprint a
 * quarter turned elsewhere: every block is there, turned with the room, the double chests still double, the door
 * whole with its hinge, the bed whole.
 */
public class QaScanRoundTripGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final BlockPos SRC = new BlockPos(18, 2, 18);
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 5);
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);

	//$ gametest_ticks_batch HUGE '3000' '"qaScanRoundTrip"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaScanRoundTrip")
	public void aScannedRoomIsBuiltAgainTurnedWithEveryPieceWhole(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Map<BlockPos, BlockState> room = room();
		room.forEach((p, s) -> helper.setBlock(SRC.offset(p), s));

		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, SRC);
		QaStageThreeGameTests.click(helper, player, SRC.offset(5, 3, 4));
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the scan said " + QaStageThreeGameTests.last(seen));
		ItemStack blueprint = ItemStack.EMPTY;
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT)) {
				blueprint = s;
			}
		}
		helper.assertTrue(!blueprint.isEmpty(), "the scan gave no blueprint");
		ResourceLocation id = BlueprintItem.data(blueprint).orElseThrow().structure();

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
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		Rotation turn = Rotation.CLOCKWISE_90;
		BlockPos origin = helper.absolutePos(ORIGIN);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, turn, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException("the builder didn't take the scanned blueprint " + id);
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status());
			List<String> wrong = new ArrayList<>();
			room.forEach((p, s) -> {
				BlockPos at = origin.offset(StructureTemplate.transform(p, Mirror.NONE, turn, BlockPos.ZERO));
				BlockState expected = s.rotate(turn);
				BlockState there = level.getBlockState(at);
				if (!MaterialRules.matches(there, expected)) {
					wrong.add(p.toShortString() + ": " + there + ", expected " + expected);
				} else if (s.getBlock() instanceof ChestBlock && there.getValue(ChestBlock.TYPE) != expected.getValue(ChestBlock.TYPE)) {
					wrong.add(p.toShortString() + ": the chest is " + there.getValue(ChestBlock.TYPE) + ", expected " + expected.getValue(ChestBlock.TYPE));
				}
			});
			helper.assertTrue(wrong.isEmpty(), "the scanned room built again turned is wrong (" + site.skipped() + " skipped): " + wrong);
			LOGGER.info("qa scan round trip: {} blocks of the scanned room built again turned {}, {} skipped", room.size(), turn, site.skipped());
		});
	}

	/** The hand-built room, 6 wide (x), 4 tall, 5 deep (z), from its corner; air left out. */
	private static Map<BlockPos, BlockState> room() {
		Map<BlockPos, BlockState> out = new LinkedHashMap<>();
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 5; z++) {
				out.put(new BlockPos(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState());
			}
			for (int y = 1; y < 4; y++) {
				out.put(new BlockPos(x, y, 4), Blocks.COBBLESTONE.defaultBlockState());
			}
		}
		// A double chest against the back wall (halves along x) and one along the east side (halves along z)
		BlockState north = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
		out.put(new BlockPos(1, 1, 3), north.setValue(ChestBlock.TYPE, ChestType.LEFT));
		out.put(new BlockPos(2, 1, 3), north.setValue(ChestBlock.TYPE, ChestType.RIGHT));
		BlockState west = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST);
		out.put(new BlockPos(5, 1, 2), west.setValue(ChestBlock.TYPE, ChestType.LEFT));
		out.put(new BlockPos(5, 1, 1), west.setValue(ChestBlock.TYPE, ChestType.RIGHT));
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT);
		out.put(new BlockPos(0, 1, 1), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		out.put(new BlockPos(0, 2, 1), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
		out.put(new BlockPos(2, 1, 1), bed.setValue(BedBlock.PART, BedPart.FOOT));
		out.put(new BlockPos(3, 1, 1), bed.setValue(BedBlock.PART, BedPart.HEAD));
		out.put(new BlockPos(4, 2, 3), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
		out.put(new BlockPos(3, 3, 2), Blocks.STONE_BRICKS.defaultBlockState());
		out.put(new BlockPos(3, 2, 2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		out.put(new BlockPos(4, 1, 1), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.TOP));
		out.put(new BlockPos(5, 2, 3), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		return out;
	}
}
