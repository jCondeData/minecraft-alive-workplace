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
 * QA (lane E, config extremes and the village's extent, 36.20). The spec: a spot is in reach when it is inside the
 * village's claimed area ("the City Plan zones and the hall's radius"), <em>wherever the worker's block stands in it</em>.
 * The City Plan's grid is a square round the hall, so its corner cells lie outside the hall's circle, and a player can
 * paint them into a zone (CityPlans.ownCells refuses only cells nearer another hall); {@link Reach#claims} counts them as
 * the village. A farmer whose composter stands in such a painted corner cell works in the village, so a field across the
 * village, beyond the long reach, is still his.
 *
 * <p>Scaled down so it fits one test area: hall radius 12 (the grid is then ±16 blocks, cells of one block), the field
 * reach at its lowest, 16. The control is the same farmer with the composter inside the circle.
 */
public class QaReachZoneCornerGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	//$ gametest_ticks_batch AREA '60' '"qaReachZoneCorner"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaReachZoneCorner")
	public void aComposterInAPaintedCornerOfThePlanReachesAcrossTheVillage(GameTestHelper helper) {
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

		// the field: centred 8 west and 2 north of the hall, every block of it inside the hall's circle
		BlockPos fieldCentre = HALL.offset(-8, 0, -2);

		// The control: the composter inside the circle, 10 east of the hall (18 from the field: past the reach of 16).
		BlockPos inCircle = HALL.offset(10, 0, 0);
		helper.assertTrue(fieldOffered(helper, inCircle, fieldCentre).isEmpty(),
			"the control: a composter inside the village's circle should reach a field across the village");

		// The corner: a Homes zone painted over the plan's south-east corner, the composter in it (14 east, 14 south:
		// 19.8 blocks from the hall, outside its circle of 12, on the grid of ±16).
		BlockPos corner = HALL.offset(14, 0, 14);
		BitSet cells = new BitSet();
		for (int dx = 13; dx <= 15; dx++) {
			for (int dz = 13; dz <= 15; dz++) {
				int cell = CityPlan.cellAt(hall, hall.offset(dx, 0, dz));
				helper.assertTrue(cell >= 0, "the corner cell " + dx + "," + dz + " isn't on the plan's grid");
				cells.set(cell);
			}
		}
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		helper.assertTrue(Reach.claims(level, hall, helper.absolutePos(corner)),
			"the setup: the painted corner cell isn't the village's claimed area");
		double off = Math.sqrt(helper.absolutePos(corner).distSqr(helper.absolutePos(fieldCentre)));
		helper.assertTrue(off > 16, "the setup: the field is only " + off + " blocks from the corner composter");
		List<String> refused = fieldOffered(helper, corner, fieldCentre);
		helper.assertTrue(refused.isEmpty(), "a farmer whose composter stands in the village's painted Homes zone (its corner, "
			+ "outside the hall's circle) was refused a field across the village, " + Math.round(off) + " blocks off: " + refused);
		helper.succeed();
	}

	/**
	 * A farmer employed at a composter at {@code composterAt}, handed a 5 × 5 Field Marker area centred on {@code centre}
	 * with a right-click: empty if he took it, else what the player was told.
	 */
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
