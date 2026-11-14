package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/**
 * Bug B1233 (bug lane, lane-a-1114-1223): a caravan-list entry whose saved village name is missing or broken (a
 * hand-edited or damaged save) used to be dropped whole on load, losing that village's routes, wants, banner color,
 * stations and tracks. Now the name falls back to the village's made-up name and the rest of the entry is kept. The
 * saves are written by a real {@link Caravans.Data}, hand-edited, then loaded with {@link Caravans.Data#load}.
 */
public class CaravanSavedNameB1233GameTests implements FabricGameTest {
	private static final BlockPos HALL = new BlockPos(1200, 64, -3400);
	private static final BlockPos OTHER = new BlockPos(1500, 64, -3400);
	private static final BlockPos STATION = new BlockPos(1210, 64, -3395);

	/** A village with a name, wants, a route, a banner color, a station and track, saved, hand-edited and loaded. */
	private static Caravans.Data reload(GameTestHelper helper, Consumer<CompoundTag> edit) {
		ServerLevel level = helper.getLevel();
		Caravans.Data data = new Caravans.Data();
		data.setWants(HALL, Component.literal("Willowmere"), List.of(new Caravans.Want(Items.BREAD, 3)));
		data.setWants(OTHER, Component.literal("Ashford"), List.of());
		data.toggleRoute(HALL, OTHER);
		data.setColour(HALL, DyeColor.LIME);
		data.setStations(HALL, List.of(STATION));
		data.setTracked(HALL, OTHER, true);
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		ListTag villages = tag.getList("villages", Tag.TAG_COMPOUND);
		for (int i = 0; i < villages.size(); i++) {
			if (BlockPos.of(villages.getCompound(i).getLong("hall")).equals(HALL)) {
				edit.accept(villages.getCompound(i));
			}
		}
		return Caravans.Data.load(tag, level.registryAccess());
	}

	private static void assertKept(GameTestHelper helper, Caravans.Data loaded, String how) {
		Caravans.Village v = loaded.village(HALL);
		helper.assertTrue(v != null, how + ": the village was dropped from the caravan list");
		String made = VillageHalls.madeUpName(HALL).getString();
		helper.assertTrue(!made.isBlank() && v.name().getString().equals(made), how + ": named '" + v.name().getString() + "', expected the made-up " + made);
		helper.assertTrue(v.wants().size() == 1 && v.wants().get(0).item() == Items.BREAD && v.wants().get(0).count() == 3, how + ": wants " + v.wants());
		helper.assertTrue(loaded.routesFrom(HALL).equals(java.util.Set.of(OTHER)), how + ": routes " + loaded.routesFrom(HALL));
		helper.assertTrue(loaded.colour(HALL) == DyeColor.LIME, how + ": banner color " + loaded.colour(HALL));
		helper.assertTrue(loaded.stations(HALL).equals(List.of(STATION)), how + ": stations " + loaded.stations(HALL));
		helper.assertTrue(loaded.tracked(HALL, OTHER), how + ": the track towards the other village was lost");
		helper.assertTrue(loaded.village(OTHER) != null && loaded.village(OTHER).name().getString().equals("Ashford"), how + ": the other village " + loaded.village(OTHER));
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aSavedVillageWithNoNameKeepsItsRoutesAndData(GameTestHelper helper) {
		assertKept(helper, reload(helper, t -> t.remove("name")), "no name");
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aSavedVillageWithABrokenNameKeepsItsRoutesAndData(GameTestHelper helper) {
		assertKept(helper, reload(helper, t -> t.putString("name", "{\"text\":\"Willow")), "a broken name");
		assertKept(helper, reload(helper, t -> t.putString("name", "null")), "a null name");
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aSavedVillageWithAGoodNameStillKeepsIt(GameTestHelper helper) {
		Caravans.Village v = reload(helper, t -> { }).village(HALL);
		helper.assertTrue(v != null && v.name().getString().equals("Willowmere"), "the saved name: " + v);
		helper.succeed();
	}
}
