package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.realm.RealmData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * QA (lane E, saves and optional mods, 2026-11-02; B791's kind for the colony orders): settlers on the road to a new
 * colony live only in their order (Settlers.depart saves each into the order and takes them out of the world), with the
 * supplies they took from the Storehouses. Supplies are matched by tag (any log, any planks, any bed), so a modded
 * wood's planks or a modded bed rides along. A world reopened without that mod must lose only that supply: the order,
 * its settlers and its other supplies stay, as a chest keeps its other slots.
 *
 * <p>The order is saved the way the level saves {@code aliveworkplace_realms}, the planks' id renamed in the saved tag to
 * one no mod registers, and the file loaded again. (RealmData reads each list with resultOrPartial, so the partial order
 * comes through; these keep it so.)
 */
public class QaColonyOrderRemovedMod1102GameTests implements FabricGameTest {

	static final String GONE = "gonemod:redwood_planks";

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void settlersOnTheRoadStayWhenARemovedModsPlanksWereAmongTheirSupplies(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		Villager settler = EntityType.VILLAGER.create(helper.getLevel());
		CompoundTag settlerTag = new CompoundTag();
		settler.saveWithoutId(settlerTag);
		UUID settlerId = settler.getUUID();

		GlobalPos mother = GlobalPos.of(Level.OVERWORLD, new BlockPos(1000, 64, 1000));
		Map<Item, Integer> supplies = new LinkedHashMap<>();
		supplies.put(Items.OAK_LOG, 64);
		supplies.put(Items.OAK_PLANKS, 64); // stands in for a modded wood's planks (Supply.PLANKS takes any #planks)
		supplies.put(Items.BREAD, 32);
		RealmData.Order order = new RealmData.Order(mother, new BlockPos(1200, 64, 1000), Optional.empty(), RealmData.ON_ROAD,
			0L, 48000L, 10, 0L, List.of(), Optional.empty(), supplies, List.of(settlerTag));
		RealmData data = RealmData.load(new CompoundTag(), registries);
		data.putOrder(order);
		CompoundTag saved = data.save(new CompoundTag(), registries);

		ListTag orders = saved.getCompound("colonies").getList("orders", Tag.TAG_COMPOUND);
		helper.assertTrue(orders.size() == 1, "the order didn't save as one entry: " + saved.getCompound("colonies"));
		CompoundTag savedSupplies = orders.getCompound(0).getCompound("supplies");
		helper.assertTrue(savedSupplies.contains("minecraft:oak_planks"), "no planks in the saved supplies: " + savedSupplies);
		Tag planks = savedSupplies.get("minecraft:oak_planks");
		savedSupplies.remove("minecraft:oak_planks");
		savedSupplies.put(GONE, planks);

		RealmData back = RealmData.load(saved, registries);
		RealmData.Order loaded = back.orderOf(mother);
		helper.assertTrue(loaded != null, "the whole colony order was lost for one supply of a removed mod's planks: its settler (who"
			+ " lives only in the order while on the road) is gone for good, with the 64 logs and 32 bread");
		helper.assertTrue(loaded.settlers().size() == 1 && loaded.settlers().get(0).getUUID("UUID").equals(settlerId),
			"the settler on the road after the load: " + loaded.settlers().size() + " of 1");
		helper.assertTrue(loaded.state().equals(RealmData.ON_ROAD) && loaded.arrives() == 48000L,
			"the order's road after the load: " + loaded.state() + ", arrives " + loaded.arrives());
		helper.assertTrue(Integer.valueOf(64).equals(loaded.supplies().get(Items.OAK_LOG)) && Integer.valueOf(32).equals(loaded.supplies().get(Items.BREAD)),
			"the order's other supplies after the load: " + loaded.supplies());
		helper.assertTrue(!loaded.supplies().containsKey(Items.OAK_PLANKS), "the renamed planks came back as oak planks");
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aVillageStillGatheringKeepsItsOrderWhenARemovedModsBedWasTaken(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		GlobalPos mother = GlobalPos.of(Level.OVERWORLD, new BlockPos(-2000, 64, 500));
		UUID volunteer = UUID.fromString("6f1c7f7e-8a6b-4b55-9a10-1c2d3e4f5a6b");
		Map<Item, Integer> supplies = new LinkedHashMap<>();
		supplies.put(Items.RED_BED, 2); // stands in for a modded bed (Supply.BEDS takes any #beds)
		supplies.put(Items.COBBLESTONE, 64);
		RealmData.Order order = new RealmData.Order(mother, new BlockPos(-2200, 64, 500), Optional.empty(), RealmData.GATHERING,
			0L, 0L, 25, 100L, List.of(volunteer), Optional.of(volunteer), supplies, List.of());
		RealmData data = RealmData.load(new CompoundTag(), registries);
		data.putOrder(order);
		CompoundTag saved = data.save(new CompoundTag(), registries);
		CompoundTag savedSupplies = saved.getCompound("colonies").getList("orders", Tag.TAG_COMPOUND).getCompound(0).getCompound("supplies");
		Tag beds = savedSupplies.get("minecraft:red_bed");
		helper.assertTrue(beds != null, "no beds in the saved supplies: " + savedSupplies);
		savedSupplies.remove("minecraft:red_bed");
		savedSupplies.put("gonemod:canopy_bed", beds);

		RealmData.Order loaded = RealmData.load(saved, registries).orderOf(mother);
		helper.assertTrue(loaded != null, "the village's whole colony order (its 25-emerald charter, volunteer and 64 cobblestone"
			+ " already taken from the Storehouse) was lost for one removed mod's bed");
		helper.assertTrue(loaded.volunteers().equals(List.of(volunteer)) && loaded.builder().equals(Optional.of(volunteer)) && loaded.cost() == 25,
			"the order after the load: volunteers " + loaded.volunteers() + ", builder " + loaded.builder() + ", cost " + loaded.cost());
		helper.assertTrue(Integer.valueOf(64).equals(loaded.supplies().get(Items.COBBLESTONE)), "the cobblestone after the load: " + loaded.supplies());
		helper.succeed();
	}
}
