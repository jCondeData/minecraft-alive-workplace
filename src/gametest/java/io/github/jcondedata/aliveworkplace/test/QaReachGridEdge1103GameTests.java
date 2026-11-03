package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1103-0338, 2026-11-03) for 36.20 and B550/B557, both sides of the City Plan grid's outer edge. The spec:
 * a spot is in reach when it is inside the village's claimed area (the hall's radius and the City Plan's zones),
 * wherever the worker's block stands in it. The grid runs from -half to half - 1 round the hall, so its west and
 * north edges lie exactly half blocks out; a worker's block in a zone painted on that very edge is in the village (it
 * reaches a field across it), and one a block past the edge (off the grid, outside the circle) is not. Written to
 * kill the nightly-style mutant that shrank the hall lookup's square by two blocks (Reach.village, "+ 1" to "- 1"),
 * which every other reach test let through.
 *
 * <p>Scaled like {@link QaReachZoneCornerGameTests}: hall radius 12 (grid ±16, cells of one block), field reach 16.
 */
public class QaReachGridEdge1103GameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(18, 2, 15);

	//$ gametest_ticks_batch AREA '60' '"qa1103ReachGridEdge"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1103ReachGridEdge")
	public void aComposterOnTheGridsWestEdgeIsInTheVillageAndOneBlockPastItIsNot(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean protection = VillageProtection.ENABLED;
		int radius = VillageHalls.RADIUS;
		int reach = Fields.MAX_DISTANCE;
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.RADIUS = radius;
			Fields.MAX_DISTANCE = reach;
			VillageHalls.forgetNearest();
		});
		VillageProtection.ENABLED = false;
		VillageHalls.RADIUS = 12;
		Fields.MAX_DISTANCE = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos hall = helper.absolutePos(HALL);
		helper.assertTrue(CityPlan.half() == 16, "the plan's grid reaches " + CityPlan.half() + " blocks from the hall, not 16");

		// a Homes zone painted on the grid's west edge: columns 16 to 14 west of the hall, rows 9 to 7 north
		BitSet cells = new BitSet();
		for (int dx = -16; dx <= -14; dx++) {
			for (int dz = -9; dz <= -7; dz++) {
				int cell = CityPlan.cellAt(hall, hall.offset(dx, 0, dz));
				helper.assertTrue(cell >= 0, "the edge cell " + dx + "," + dz + " isn't on the plan's grid");
				cells.set(cell);
			}
		}
		helper.assertTrue(CityPlan.cellAt(hall, hall.offset(-17, 0, -8)) < 0, "the setup: 17 west of the hall is still on the grid");
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));

		// the field: centred 8 east and 2 south of the hall, all of it inside the hall's circle of 12
		BlockPos fieldCentre = HALL.offset(8, 0, 2);

		// on the edge: 16 west, 8 north (17.9 from the hall, outside its circle; 26 from the field, past the reach of 16)
		BlockPos edge = HALL.offset(-16, 0, -8);
		helper.assertTrue(Reach.claims(level, hall, helper.absolutePos(edge)), "the setup: the painted edge cell isn't the village's claimed area");
		helper.assertTrue(Reach.village(level, helper.absolutePos(edge)).filter(hall::equals).isPresent(),
			"a block in the zone painted on the grid's west edge, 16 west of the hall, isn't found to stand in its village");
		List<String> refused = fieldOffered(helper, edge, fieldCentre);
		helper.assertTrue(refused.isEmpty(), "a farmer whose composter stands in the zone painted on the plan's west edge (16 west of the hall) "
			+ "was refused a field across the village: " + refused);

		// one block past the edge: off the grid and outside the circle, so only the long reach counts and the field is refused
		BlockPos past = HALL.offset(-17, 0, -8);
		helper.assertTrue(Reach.village(level, helper.absolutePos(past)).isEmpty(),
			"a block one past the grid's west edge (17 west, off the plan, outside the circle) was found to stand in the village");
		List<String> refusedPast = fieldOffered(helper, past, fieldCentre);
		helper.assertFalse(refusedPast.isEmpty(), "a farmer whose composter stands one block past the plan's edge, outside the village, "
			+ "was given a field 27 blocks off with a reach of 16");
		helper.succeed();
	}

	/** As {@link QaReachZoneCornerGameTests}: a farmer at {@code composterAt} handed a 5 × 5 field round {@code centre}; empty if taken. */
	private static List<String> fieldOffered(GameTestHelper helper, BlockPos composterAt, BlockPos centre) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(composterAt, Blocks.COMPOSTER);
		Villager farmer = helper.spawn(EntityType.VILLAGER, composterAt.above());
		farmer.setNoAi(true);
		Jobs.employ(level, farmer, helper.absolutePos(composterAt), PoiTypes.FARMER, VillagerProfession.FARMER);
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(centre);
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(at.offset(-2, 0, -2)), Optional.of(at.offset(2, 0, 2))));
		p.setItemInHand(InteractionHand.MAIN_HAND, marker);
		QaStageSixGameTests.clickVillager(p, farmer, seen);
		boolean taken = Fields.hasField(farmer);
		ModAttachments.FARM_FIELD.remove(farmer);
		farmer.discard();
		helper.setBlock(composterAt, Blocks.AIR);
		return taken ? List.of() : seen.stream().map(Component::getString).toList();
	}
}
