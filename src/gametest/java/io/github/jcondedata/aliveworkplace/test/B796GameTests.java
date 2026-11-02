package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.research.Research;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/**
 * B796, the parts of a Village Hall's save QaHallListsOneBadEntry1102BugTests leaves out: one damaged zone in the city
 * plan, or one damaged research level, used to lose the whole plan or the whole village's research. Only it goes now.
 */
public class B796GameTests implements FabricGameTest {

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void oneDamagedZoneOrResearchLevelKeepsTheRest(GameTestHelper helper) {
		var level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		var state = ModBlocks.VILLAGE_HALL.defaultBlockState();
		BitSet cells = new BitSet();
		cells.set(3);
		VillageHallBlockEntity a = new VillageHallBlockEntity(pos, state);
		a.setPlan(new CityPlan(List.of(new CityPlan.Zone("homes", "North Homes", "", false, cells),
			new CityPlan.Zone("market", "Market", "", true, cells)), List.of(), Optional.empty(), CityPlan.Mode.ASK));
		a.setResearch(new Research.State(Map.of("scholars/farming", 2, "scholars/masonry", 1), Optional.empty(), 0, false));
		CompoundTag tag = a.saveWithoutMetadata(level.registryAccess());
		helper.assertTrue(tag.contains("plan") && tag.contains("research"), "set-up: the hall saved no plan or research: " + tag.getAllKeys());

		CompoundTag hurt = tag.copy();
		((ListTag) hurt.getCompound("plan").get("zones")).add(0, new CompoundTag());
		hurt.getCompound("research").getCompound("levels").put("scholars/broken", StringTag.valueOf("not a number"));
		VillageHallBlockEntity b = new VillageHallBlockEntity(pos, state);
		b.loadWithComponents(hurt, level.registryAccess());

		helper.assertTrue(b.plan().zones().equals(a.plan().zones()),
			"one damaged zone first: the plan loaded " + b.plan().zones().size() + " zones, not the 2 good ones (" + b.plan().zones() + ")");
		helper.assertTrue(b.research().levels().equals(a.research().levels()),
			"one damaged research level: the hall loaded " + b.research().levels() + ", not " + a.research().levels());
		helper.succeed();
	}
}
