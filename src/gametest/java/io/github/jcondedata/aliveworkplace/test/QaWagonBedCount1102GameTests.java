package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.JobPlate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA for B787 from its spec: the hall's wagon-bed line reads in full in both numbers, and goes away with the wagon beds.
 * Two bedrolls: "Not counted: 2 wagon beds (for the road, not homes)"; one: "... 1 wagon bed (for the road, not a
 * home)"; none: no line.
 */
public class QaWagonBedCount1102GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos CAMP = new BlockPos(15, 2, 4);
	private static final BlockPos HALL = new BlockPos(15, 2, 20);

	private static void radii(GameTestHelper helper) {
		int hall = VillageHalls.RADIUS;
		int inn = Innkeepers.RADIUS;
		VillageHalls.RADIUS = 18;
		Innkeepers.RADIUS = 18;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = hall;
			Innkeepers.RADIUS = inn;
		});
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(-2, 0, -2)), helper.absolutePos(new BlockPos(31, 12, 31)));
		Leftovers.after(helper, () -> CampBeds.Data.get(helper.getLevel()).forget(box));
	}

	private static BlockPos bed(GameTestHelper helper, BlockPos foot, net.minecraft.world.level.block.Block bed) {
		helper.setBlock(foot, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	private static List<BlockPos> bedHeads(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 10, 29)))) {
			BlockState state = helper.getLevel().getBlockState(p);
			if (state.is(BlockTags.BEDS) && state.getValue(BedBlock.PART) == BedPart.HEAD) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	private static BlockPos home(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static void breakBed(ServerLevel level, BlockPos head) {
		BlockState state = level.getBlockState(head);
		level.setBlockAndUpdate(head.relative(BedBlock.getConnectedDirection(state)), Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(head, Blocks.AIR.defaultBlockState());
	}

	/** The camp, a house bed and a hall; {@code broken} of the wagon's two bedrolls broken first. Waits for the line. */
	private static void wagonLine(GameTestHelper helper, int broken, int wagonBeds, String expected) {
		Leftovers.clear(helper);
		radii(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(CAMP));
		settlers.forEach(v -> v.setNoAi(true));
		List<BlockPos> wagon = bedHeads(helper);
		helper.assertTrue(wagon.size() == 2, "setup: wagon beds: " + wagon);
		for (int i = 0; i < broken; i++) {
			breakBed(level, wagon.get(i));
		}
		bed(helper, new BlockPos(5, 2, 25), Blocks.RED_BED);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		helper.succeedWhen(() -> {
			VillageHalls.Census census = VillageHalls.census(level, hall);
			helper.assertTrue(census.wagonBeds() == wagonBeds && census.beds() == 1, "wagon " + census.wagonBeds() + ", beds " + census.beds());
			List<String> lines = lore(VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.BEDS));
			List<String> wagonLines = lines.stream().filter(l -> l.startsWith("Not counted:")).toList();
			if (expected == null) {
				helper.assertTrue(wagonLines.isEmpty(), "no wagon beds left, but the Beds button says: " + wagonLines);
			} else {
				helper.assertTrue(wagonLines.equals(List.of(expected)), "the wagon line: " + wagonLines + ", expected '" + expected + "'");
			}
		});
	}

	/** Both bedrolls standing: the plural line, word for word. */
	//$ gametest_ticks_batch AREA '200' '"qaTwoWagonBedsReadPlural1102"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaTwoWagonBedsReadPlural1102")
	public void qaTwoWagonBedsReadInThePlural(GameTestHelper helper) {
		wagonLine(helper, 0, 2, "Not counted: 2 wagon beds (for the road, not homes)");
	}

	/** One bedroll broken: the singular line, word for word (B787). */
	//$ gametest_ticks_batch AREA '200' '"qaOneWagonBedReadsSingular1102"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOneWagonBedReadsSingular1102")
	public void qaOneWagonBedReadsInTheSingularWordForWord(GameTestHelper helper) {
		wagonLine(helper, 1, 1, "Not counted: 1 wagon bed (for the road, not a home)");
	}

	/** Both bedrolls broken: the line goes, rather than reading "0 wagon beds". */
	//$ gametest_ticks_batch AREA '200' '"qaNoWagonBedsNoLine1102"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNoWagonBedsNoLine1102")
	public void qaNoWagonBedsLeftMeansNoLine(GameTestHelper helper) {
		wagonLine(helper, 2, 0, null);
	}
}
