package io.github.jcondedata.aliveworkplace.test;

import com.mojang.serialization.Codec;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.mc.Lenient;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * B791: saved lists and maps naming an item of a mod taken out of the pack drop only that entry ({@link Lenient}). These
 * cover what the QA tests don't: saves are written exactly as before, a list whose every entry is gone loads empty
 * rather than lost, a load entry with a bad count is dropped too, a Delivery Note's own route keeps its other filter
 * items, and a bag that lost a stack saves and loads again cleanly.
 */
public class RemovedModItemsGameTests implements FabricGameTest {
	static final String GONE = QaRemovedModItems1102BugTests.GONE;

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theLenientCodecsWriteExactlyWhatTheStrictOnesDid(GameTestHelper helper) {
		List<ItemStack> stacks = List.of(new ItemStack(Items.COBBLESTONE, 40), new ItemStack(Items.OAK_PLANKS, 16));
		Tag lenientList = BuilderBag.CODEC.encodeStart(NbtOps.INSTANCE, BuilderBag.of(stacks)).getOrThrow();
		Tag strictList = ItemStack.CODEC.listOf().encodeStart(NbtOps.INSTANCE, stacks).getOrThrow();
		helper.assertTrue(lenientList.equals(strictList), "the bag saves differently: " + lenientList + " vs " + strictList);

		Map<Item, Integer> load = new LinkedHashMap<>();
		load.put(Items.WHEAT, 12);
		load.put(Items.OAK_LOG, 20);
		Codec<Map<Item, Integer>> strictMap = Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.INT);
		Tag lenientMap = HaulerGolems.LOAD_CODEC.encodeStart(NbtOps.INSTANCE, load).getOrThrow();
		helper.assertTrue(lenientMap.equals(strictMap.encodeStart(NbtOps.INSTANCE, load).getOrThrow()), "the golem load saves differently: " + lenientMap);
		Map<Item, Integer> old = strictMap.parse(NbtOps.INSTANCE, lenientMap).getOrThrow();
		helper.assertTrue(old.equals(load), "the strict codec (an older jar) can't read the new save: " + old);
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aBagWhoseEveryStackIsGoneLoadsEmptyNotLost(GameTestHelper helper) {
		ListTag list = new ListTag();
		CompoundTag gone = new CompoundTag();
		gone.putString("id", GONE);
		gone.putInt("count", 5);
		list.add(gone);
		var result = BuilderBag.CODEC.parse(NbtOps.INSTANCE, list);
		helper.assertTrue(result.result().isPresent(), "a bag of only a removed mod's stack failed to load: " + result);
		helper.assertTrue(result.result().get().isEmpty(), "the removed stack came back as something");
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aLoadEntryWithABadCountOrAGoneItemIsDroppedAndTheRestKept(GameTestHelper helper) {
		CompoundTag map = new CompoundTag();
		map.putInt("minecraft:cobblestone", 64);
		map.putInt(GONE, 3);
		map.putString("minecraft:wheat", "lots"); // a count that isn't one
		map.putInt("minecraft:oak_log", 20);
		var result = HaulerGolems.LOAD_CODEC.parse(NbtOps.INSTANCE, map);
		helper.assertTrue(result.result().isPresent(), "the load failed as a whole: " + result);
		Map<Item, Integer> back = result.result().get();
		helper.assertTrue(back.size() == 2 && Integer.valueOf(64).equals(back.get(Items.COBBLESTONE)) && Integer.valueOf(20).equals(back.get(Items.OAK_LOG)),
			"the load after dropping the bad entries: " + back);
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aDeliveryNotesRouteKeepsItsOtherFilterItems(GameTestHelper helper) {
		RouteData route = new RouteData(Optional.of(new BlockPos(1, 2, 3)), Optional.of(new BlockPos(9, 2, 3)), List.of(Items.WHEAT, Items.CARROT, Items.POTATO));
		CompoundTag tag = (CompoundTag) RouteData.CODEC.encodeStart(NbtOps.INSTANCE, route).getOrThrow();
		ListTag filter = tag.getList("filter", Tag.TAG_STRING);
		filter.set(1, StringTag.valueOf(GONE));
		var result = RouteData.CODEC.parse(NbtOps.INSTANCE, tag);
		helper.assertTrue(result.result().isPresent(), "the note's route failed to load: " + result);
		RouteData back = result.result().get();
		helper.assertTrue(back.from().equals(route.from()) && back.to().equals(route.to()), "the route's ends changed: " + back);
		helper.assertTrue(back.filter().equals(List.of(Items.WHEAT, Items.POTATO)), "the filter after the load: " + back.filter());
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aBagThatLostAStackSavesAndLoadsAgain(GameTestHelper helper) {
		Villager builder = EntityType.VILLAGER.create(helper.getLevel());
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(builder);
		bag.addAll(Items.COBBLESTONE, 40);
		bag.addAll(Items.GLASS, 8);
		CompoundTag saved = builder.saveWithoutId(new CompoundTag());
		ListTag stacks = (ListTag) QaRemovedModItems1102BugTests.attachment(saved, "builder_bag");
		for (int i = 0; i < stacks.size(); i++) {
			if (stacks.getCompound(i).getString("id").equals("minecraft:glass")) {
				stacks.getCompound(i).putString("id", GONE);
			}
		}
		Villager once = EntityType.VILLAGER.create(helper.getLevel());
		once.load(saved);
		ModAttachments.BUILDER_BAG.getOrCreate(once).addAll(Items.OAK_PLANKS, 16); // he goes on working
		Villager twice = EntityType.VILLAGER.create(helper.getLevel());
		twice.load(once.saveWithoutId(new CompoundTag()));
		BuilderBag back = ModAttachments.BUILDER_BAG.get(twice);
		helper.assertTrue(back != null && back.count(Items.COBBLESTONE) == 40 && back.count(Items.OAK_PLANKS) == 16 && back.count(Items.GLASS) == 0,
			"the bag after a second save and load: " + (back == null ? "lost" : back.stacks()));
		helper.succeed();
	}
}
