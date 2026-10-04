package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.netty.buffer.Unpooled;
import java.util.BitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** The City Plan screen (ROADMAP 27.3): painting, erasing and undoing through its packets, and what it is sent to draw. */
public class CityPlanScreenGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(8, 2, 8);

	/** An edit as the screen sends it: written to a packet and read back, as the server receives it. */
	private static CityPlans.Edit sent(ServerLevel level, CityPlans.Edit edit) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
		CityPlans.Edit.CODEC.encode(buf, edit);
		return CityPlans.Edit.CODEC.decode(buf);
	}

	/** The cell {@code dx}, {@code dz} blocks from the hall (near enough that no other test's hall is nearer). */
	private static int cell(BlockPos hall, int dx, int dz) {
		return CityPlan.cellAt(hall, hall.offset(dx, 0, dz));
	}

	private static BitSet cells(int... cells) {
		BitSet out = new BitSet();
		for (int c : cells) {
			out.set(c);
		}
		return out;
	}

	private static BitSet cellsOf(VillageHallBlockEntity entity, int zone) {
		return entity.plan().zones().get(zone).cells();
	}

	/** Paint, paint over, erase, undo twice: the plan on the hall matches the screen's strokes after each packet. */
	//$ gametest_ticks_batch AREA '20' '"cityPlanScreenPaint"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "cityPlanScreenPaint")
	public void paintEraseAndUndoThroughThePackets(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int a = cell(hall, -4, -4), b = cell(hall, 0, -4), c = cell(hall, 4, -4), d = cell(hall, 0, 4);
		helper.assertTrue(a != b && b != c && c != d && a != d, "setup: the cells aren't four different ones");

		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", "cherry"))), "no zone added");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "gardens", "Gardens", ""))), "no second zone");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, cells(a, b, c)))), "the brush stroke was refused");
		helper.assertTrue(cellsOf(entity, 0).equals(cells(a, b, c)), "after painting Homes: " + cellsOf(entity, 0));
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 1, cells(c, d)))), "painting over was refused");
		helper.assertTrue(cellsOf(entity, 0).equals(cells(a, b)) && cellsOf(entity, 1).equals(cells(c, d)),
			"after painting Gardens over one cell: " + cellsOf(entity, 0) + " / " + cellsOf(entity, 1));
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.erase(hall, cells(b, d)))), "the eraser was refused");
		helper.assertTrue(cellsOf(entity, 0).equals(cells(a)) && cellsOf(entity, 1).equals(cells(c)),
			"after erasing: " + cellsOf(entity, 0) + " / " + cellsOf(entity, 1));
		helper.assertTrue(entity.planUndoSteps() == 5, "undo steps after five changes: " + entity.planUndoSteps());

		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.undo(hall))), "undo was refused");
		helper.assertTrue(cellsOf(entity, 0).equals(cells(a, b)) && cellsOf(entity, 1).equals(cells(c, d)),
			"the first undo didn't bring back the erased cells: " + cellsOf(entity, 0) + " / " + cellsOf(entity, 1));
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.undo(hall))), "the second undo was refused");
		helper.assertTrue(cellsOf(entity, 0).equals(cells(a, b, c)) && cellsOf(entity, 1).isEmpty(),
			"the second undo didn't take back the Gardens stroke: " + cellsOf(entity, 0) + " / " + cellsOf(entity, 1));
		// a rename and a style as the side panel sends them, then undone
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.editZone(hall, 0, "workshops", "Smithy Row", "stonework", true))),
			"the side panel's edit was refused");
		CityPlan.Zone edited = entity.plan().zones().get(0);
		helper.assertTrue(edited.kind().equals("workshops") && edited.name().equals("Smithy Row") && edited.style().equals("stonework") && edited.renew()
			&& edited.cells().equals(cells(a, b, c)), "the edit: " + edited);
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.undo(hall))), "undoing the edit was refused");
		helper.assertTrue(entity.plan().zones().get(0).name().equals("Homes") && entity.plan().zones().get(0).kind().equals("homes"),
			"the edit wasn't undone: " + entity.plan().zones().get(0));
		helper.succeed();
	}

	/** Undo goes back ten steps and no further; a stranger can't undo in someone else's protected village. */
	//$ gametest_ticks_batch AREA '20' '"cityPlanScreenUndo"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "cityPlanScreenUndo")
	public void undoKeepsTenStepsAndAStrangerCantUndo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CityPlans.apply(owner, sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", ""))), "no zone added");
		for (int i = 0; i < 12; i++) {
			helper.assertTrue(CityPlans.apply(owner, sent(level, CityPlans.Edit.paint(hall, 0, cells(cell(hall, -6 + (i % 4) * 4, -6 + (i / 4) * 4))))), "stroke " + i + " refused");
		}
		helper.assertTrue(entity.planUndoSteps() == CityPlans.UNDO_STEPS, "undo steps kept: " + entity.planUndoSteps());
		entity.setProtected(true);
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		CityPlan before = entity.plan();
		helper.assertFalse(CityPlans.apply(stranger, sent(level, CityPlans.Edit.undo(hall))), "a stranger undid the owner's stroke");
		helper.assertTrue(entity.plan().equals(before), "a stranger's undo changed the plan");
		for (int i = 0; i < CityPlans.UNDO_STEPS; i++) {
			helper.assertTrue(CityPlans.apply(owner, sent(level, CityPlans.Edit.undo(hall))), "undo " + (i + 1) + " refused");
		}
		helper.assertFalse(CityPlans.apply(owner, sent(level, CityPlans.Edit.undo(hall))), "an eleventh undo did something");
		helper.assertTrue(entity.plan().zones().size() == 1 && cellsOf(entity, 0).cardinality() == 2,
			"ten undos should leave the zone and its first two strokes: " + entity.plan());
		helper.succeed();
	}

	/** What the screen is sent: the map, the hall's banner, the shipped kinds and styles, the plan, and who may edit. */
	//$ gametest_ticks_batch AREA '20' '"cityPlanScreenOpen"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "cityPlanScreenOpen")
	public void theScreenIsSentTheMapThePlanAndTheKinds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		CityPlans.apply(owner, CityPlans.Edit.addZone(hall, "keep_clear", "The old oak", ""));
		CityPlans.apply(owner, CityPlans.Edit.paint(hall, 0, cells(cell(hall, 0, 4))));
		entity.setProtected(true);
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
		CityPlans.Open.CODEC.encode(buf, CityPlans.screen(level, entity, owner));
		CityPlans.Open open = CityPlans.Open.CODEC.decode(buf);
		helper.assertTrue(open.hall().equals(hall) && open.plan().equals(entity.plan()) && open.mayEdit(), "the owner's screen: " + open.plan());
		helper.assertTrue(open.colors().length == CityPlans.MAP * CityPlans.MAP, "map pixels: " + open.colors().length);
		int x = CityPlans.MAP / 2, z = CityPlans.MAP / 2 + 2; // the floor just south of the hall
		helper.assertTrue(open.colors()[x + z * CityPlans.MAP] != 0, "the land by the hall isn't on the map");
		helper.assertTrue(open.marks().stream().anyMatch(m -> m.hall() && m.dx() == 0 && m.dz() == 0), "no hall banner: " + open.marks());
		helper.assertTrue(open.kinds().size() >= 8 && open.kinds().stream().anyMatch(k -> k.id().equals("keep_clear") && !k.buildable()),
			"kinds: " + open.kinds());
		helper.assertTrue(!open.styles().isEmpty(), "no styles sent");
		helper.assertTrue(open.cellSize() == CityPlan.cellSize(), "cell size " + open.cellSize());
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(CityPlans.screen(level, entity, stranger).mayEdit(), "a stranger's screen lets them edit");
		helper.succeed();
	}
}
