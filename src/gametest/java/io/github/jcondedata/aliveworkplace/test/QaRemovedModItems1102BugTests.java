package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.mechanic.MachineJob;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.LinkedHashMap;
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
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, saves and optional mods, 2026-11-02): a mod taken out of the pack (Create, or a building mod whose blocks
 * a blueprint used) leaves its items' ids in the save with nothing behind them. Vanilla then loses only those stacks: a
 * chest keeps every other slot. What the mod keeps on a villager or a golem must do the same: a builder's bag with one
 * removed mod's block in it keeps its cobblestone and planks, a Hauler Golem's load keeps its other items, and a
 * courier's routes keep every route (a route's filter just drops the item that no longer exists).
 *
 * <p>Each test saves the entity the way the level does ({@code saveWithoutId}), renames one item in the saved tag to an
 * id no mod registers, and loads it into a fresh entity ({@code load}), as a world reopened without that mod does.
 */
public class QaRemovedModItems1102BugTests implements FabricGameTest {

	/** An item id no loaded mod registers: a block from a mod taken out of the pack. */
	static final String GONE = "gonemod:brass_casing";

	/** The saved tag of attachment {@code name} on {@code tag} (the loader's own key). */
	static Tag attachment(CompoundTag tag, String name) {
		CompoundTag all = tag.getCompound("fabric:attachments");
		return all.get("aliveworkplace:" + name);
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aBuildersBagKeepsItsOtherStacksWhenARemovedModsBlockWasInIt(GameTestHelper helper) {
		Villager builder = EntityType.VILLAGER.create(helper.getLevel());
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(builder);
		bag.addAll(Items.COBBLESTONE, 40);
		bag.addAll(Items.OAK_PLANKS, 16);
		bag.addAll(Items.GLASS, 8);
		bag.addAll(Items.DIRT, 3);
		CompoundTag saved = builder.saveWithoutId(new CompoundTag());
		Tag bagTag = attachment(saved, "builder_bag");
		helper.assertTrue(bagTag instanceof ListTag list && list.size() == 4, "the bag didn't save as a list of 4 stacks: " + bagTag);
		ListTag stacks = (ListTag) bagTag;
		int glass = -1;
		for (int i = 0; i < stacks.size(); i++) {
			if (stacks.getCompound(i).getString("id").equals("minecraft:glass")) {
				glass = i;
			}
		}
		helper.assertTrue(glass >= 0, "no glass stack in the saved bag: " + stacks);
		stacks.getCompound(glass).putString("id", GONE); // the glass stands in for a removed mod's block

		Villager reopened = EntityType.VILLAGER.create(helper.getLevel());
		reopened.load(saved);
		BuilderBag back = ModAttachments.BUILDER_BAG.get(reopened);
		helper.assertTrue(back != null, "the whole bag was lost (cobblestone 40, planks 16, dirt 3) for one stack of a removed mod's block;"
			+ " vanilla keeps a chest's other slots");
		helper.assertTrue(back.count(Items.COBBLESTONE) == 40 && back.count(Items.OAK_PLANKS) == 16 && back.count(Items.DIRT) == 3,
			"the bag's other stacks after the load: cobblestone " + back.count(Items.COBBLESTONE) + " of 40, planks "
				+ back.count(Items.OAK_PLANKS) + " of 16, dirt " + back.count(Items.DIRT) + " of 3");
		helper.assertTrue(back.count(Items.GLASS) == 0, "the renamed stack came back as glass");
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aHaulerGolemsLoadKeepsItsOtherItemsWhenARemovedModsItemWasInIt(GameTestHelper helper) {
		IronGolem golem = EntityType.IRON_GOLEM.create(helper.getLevel());
		Map<Item, Integer> load = new LinkedHashMap<>();
		load.put(Items.COBBLESTONE, 64);
		load.put(Items.OAK_LOG, 20);
		load.put(Items.WHEAT, 12);
		ModAttachments.HAULER_LOAD.set(golem, load);
		CompoundTag saved = golem.saveWithoutId(new CompoundTag());
		Tag loadTag = attachment(saved, "hauler_load");
		helper.assertTrue(loadTag instanceof CompoundTag c && c.contains("minecraft:wheat"), "the load didn't save as a map with wheat: " + loadTag);
		CompoundTag map = (CompoundTag) loadTag;
		map.put(GONE, map.get("minecraft:wheat"));
		map.remove("minecraft:wheat"); // the wheat stands in for a removed mod's item

		IronGolem reopened = EntityType.IRON_GOLEM.create(helper.getLevel());
		reopened.load(saved);
		Map<Item, Integer> back = ModAttachments.HAULER_LOAD.get(reopened);
		helper.assertTrue(back != null, "the golem's whole load was lost (cobblestone 64, oak logs 20) for one removed mod's item");
		helper.assertTrue(Integer.valueOf(64).equals(back.get(Items.COBBLESTONE)) && Integer.valueOf(20).equals(back.get(Items.OAK_LOG)),
			"the load's other items after the load: " + back);
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aCouriersRoutesAllComeBackWhenOneFilterNamedARemovedModsItem(GameTestHelper helper) {
		Villager postman = EntityType.VILLAGER.create(helper.getLevel());
		RouteData wheat = new RouteData(Optional.of(new BlockPos(1, 2, 3)), Optional.of(new BlockPos(10, 2, 3)), List.of(Items.WHEAT, Items.CARROT));
		RouteData logs = new RouteData(Optional.of(new BlockPos(4, 2, 4)), Optional.of(new BlockPos(4, 2, 20)), List.of(Items.OAK_LOG));
		RouteData all = new RouteData(Optional.of(new BlockPos(7, 2, 7)), Optional.of(new BlockPos(30, 2, 7)), List.of());
		ModAttachments.COURIER_ROUTES.set(postman, List.of(wheat, logs, all));
		CompoundTag saved = postman.saveWithoutId(new CompoundTag());
		Tag routesTag = attachment(saved, "courier_routes");
		helper.assertTrue(routesTag instanceof ListTag l && l.size() == 3, "the routes didn't save as a list of 3: " + routesTag);
		ListTag filter = ((ListTag) routesTag).getCompound(0).getList("filter", Tag.TAG_STRING);
		helper.assertTrue(filter.size() == 2, "the first route's filter didn't save as 2 ids: " + filter);
		filter.set(1, StringTag.valueOf(GONE)); // the carrots stand in for a removed mod's crop

		Villager reopened = EntityType.VILLAGER.create(helper.getLevel());
		reopened.load(saved);
		List<RouteData> back = ModAttachments.COURIER_ROUTES.get(reopened);
		helper.assertTrue(back != null, "every courier route was lost (3 routes) because one filter named a removed mod's item");
		helper.assertTrue(back.size() == 3, "routes after the load: " + back.size() + " of 3: " + back);
		helper.assertTrue(back.contains(logs) && back.contains(all), "the two untouched routes changed in the load: " + back);
		helper.assertTrue(back.stream().anyMatch(r -> r.from().equals(wheat.from()) && r.to().equals(wheat.to()) && r.filter().contains(Items.WHEAT)),
			"the route with the removed item didn't keep its ends and its wheat: " + back);
		helper.succeed();
	}
	/**
	 * Create taken out of the pack while a farmer walked back from the village's millstone with flour ({@code
	 * create:wheat_flour}) and the 12 wheat the stone didn't take yet: with Create gone his trip must still load, so the
	 * Create-gone path ({@code QaCreateGoneGameTests#aMachineTripSavedWithCreateGivesItsWheatBack}) gives the wheat back.
	 * Instead the whole trip is dropped with the flour and the 12 wheat are never seen again.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aMachineTripCarryingCreateFlourStillGivesItsWheatBackWithCreateGone(GameTestHelper helper) {
		helper.assertTrue(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("create"), "Create is loaded in the plain suite");
		Villager farmer = EntityType.VILLAGER.create(helper.getLevel());
		ModAttachments.MACHINE_JOB.set(farmer, new MachineJob(new BlockPos(5, 64, 5), AliveWorkplace.id("millstone"), MachineJob.Stage.FEED,
			List.of(new ItemStack(Items.WHEAT, 12), new ItemStack(Items.SUGAR, 4))));
		CompoundTag saved = farmer.saveWithoutId(new CompoundTag());
		Tag jobTag = attachment(saved, "machine_job");
		helper.assertTrue(jobTag instanceof CompoundTag j && j.getList("carried", Tag.TAG_COMPOUND).size() == 2, "the trip didn't save 2 carried stacks: " + jobTag);
		ListTag carried = ((CompoundTag) jobTag).getList("carried", Tag.TAG_COMPOUND);
		for (int i = 0; i < carried.size(); i++) {
			if (carried.getCompound(i).getString("id").equals("minecraft:sugar")) {
				carried.getCompound(i).putString("id", "create:wheat_flour"); // Create's flour, with Create gone
			}
		}

		Villager reopened = EntityType.VILLAGER.create(helper.getLevel());
		reopened.load(saved);
		MachineJob back = ModAttachments.MACHINE_JOB.get(reopened);
		helper.assertTrue(back != null, "the farmer's whole trip was lost with Create's flour, and the 12 wheat he carried with it");
		helper.assertTrue(back.carried().stream().filter(s -> s.is(Items.WHEAT)).mapToInt(ItemStack::getCount).sum() == 12,
			"the wheat carried after the load: " + back.carried());
		helper.succeed();
	}
}
