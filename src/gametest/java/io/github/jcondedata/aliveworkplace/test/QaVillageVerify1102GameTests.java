package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mechanic.MachineJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-lane-c-1102-0655) verifying two fixed bugs from their text:
 * <ul>
 * <li>B625, the Lost Caravan (31.20): "only a standing hall counts as a village". The receiving hall broken, then set
 * up again on the same spot or nearby (after a restart too), takes the carter home there; a third village's hall
 * never counts; the ending's sentence names the village and the standing goes to the hall that stands.</li>
 * <li>B791, a mod taken out of the pack: a saved list or map drops only the entries naming its items, with a warning,
 * and keeps the rest (several gone at the ends and in the middle, every entry gone, a stack with components, map
 * keys), and saves again cleanly.</li>
 * </ul>
 */
public class QaVillageVerify1102GameTests implements FabricGameTest {
	private static final String AREA = QaLostCaravanGameTests.AREA;
	private static final String GONE = QaRemovedModItems1102BugTests.GONE;
	private static final String GONE2 = "gonemod:andesite_alloy";

	// ---- B625 --------------------------------------------------------------------------------------------------------

	/** Tobin freed and following {@code me}, the arc in its third chapter. */
	private static Villager following(GameTestHelper helper, ArcState s, ServerPlayer me) {
		Villager tobin = QaLostCaravanGameTests.freed(helper, s, me);
		Leftovers.after(helper, tobin::discard);
		return tobin;
	}

	/**
	 * Tobin and {@code me} by {@code hall} (west of it, inside the test area), then the story's tick. Tobin must still be
	 * loaded: a carter moved into a chunk nobody loads is invisible to the story, and every "not home" check would pass
	 * for nothing (the receiving village is therefore moved into the test area, as QaLostCaravanBugTests does).
	 */
	private static void walkTo(GameTestHelper helper, Villager tobin, ServerPlayer me, BlockPos hall) {
		tobin.teleportTo(hall.getX() - 1.5, hall.getY(), hall.getZ() + 0.5);
		me.teleportTo(hall.getX() - 2.5, hall.getY(), hall.getZ() + 0.5);
		helper.assertTrue(helper.getLevel().getEntity(tobin.getUUID()) == tobin && tobin.isAlive(), "Tobin isn't loaded in the level (at "
			+ tobin.blockPosition() + ")");
		LostCaravanStory.tick(helper.getLevel());
	}

	/** Where the receiving village's hall goes, in the test area by the cage (more than 24 from the sending hall). */
	private static final BlockPos THERE = new BlockPos(27, 2, 27);

	/** The receiving village moved to {@link #THERE}, its hall standing, the radius 8 so the sending hall is no neighbour. */
	private static BlockPos receivingHere(GameTestHelper helper, ArcState s) {
		VillageHalls.RADIUS = 8; // (begin puts it back)
		helper.setBlock(THERE, ModBlocks.VILLAGE_HALL);
		BlockPos there = helper.absolutePos(THERE);
		LostCaravanStory.hold(s, there, LostCaravanStory.goods(s));
		return there;
	}

	/**
	 * The receiving hall broken, Tobin walked to where it stood: the story goes on. The hall put back on the very same
	 * spot (and named): walking him there now ends it, he joins at Journeyman, the sentence a player reads names that
	 * village in American English with no raw key, and the ending's standing goes to that hall.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaVerify1102HallBack"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVerify1102HallBack")
	public void tobinComesHomeToTheHallPutBackOnTheSameSpot(GameTestHelper helper) {
		BlockPos[] ab = QaLostCaravanGameTests.begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = QaLostCaravanGameTests.lose(helper, a, b);
		BlockPos there = receivingHere(helper, s);
		QaLostCaravanGameTests.toTheCaptive(helper, a, s);
		helper.setBlock(THERE, Blocks.AIR); // the receiving hall broken
		List<QaLostCaravanGameTests.Seen> seen = new ArrayList<>();
		ServerPlayer me = QaLostCaravanGameTests.listener(helper, seen, a.getX() + 0.5, a.getY(), a.getZ() + 6.5, "QaVerifyHallBack");
		Villager tobin = following(helper, s, me);
		walkTo(helper, tobin, me, there);
		helper.assertTrue(Arcs.running(level, a) == s && !s.flags.containsKey(LostCaravanStory.TOBIN_HOME),
			"walked to where the broken hall stood, the story ended");
		helper.runAfterDelay(2, () -> {
			helper.setBlock(THERE, ModBlocks.VILLAGE_HALL); // put back on the same spot
			((VillageHallBlockEntity) level.getBlockEntity(there)).setCustomName(Component.literal("Brookmere"));
			helper.runAfterDelay(2, () -> {
				walkTo(helper, tobin, me, there);
				helper.assertTrue(Arcs.running(level, a) != s, "walked to the hall put back on the same spot, the story runs on");
				helper.assertTrue(tobin.isAlive() && ModAttachments.HEAD_START.getOrElse(tobin, 1) == LostCaravanStory.JOURNEYMAN,
					"he didn't join the village at Journeyman");
				List<String> chat = QaLostCaravanGameTests.texts(seen, false);
				String joins = chat.stream().filter(t -> t.contains("is home safe")).findFirst().orElse(null);
				helper.assertTrue(joins != null && joins.endsWith("is home safe, and stays in Brookmere."), "the ending's sentence: " + joins + " in " + chat);
				QaLostCaravanGameTests.english(helper, chat);
				helper.assertTrue(Reputation.points(level, there, me.getUUID()) == LostCaravanStory.ENDING_REPUTATION,
					"standing at the hall put back: " + Reputation.points(level, there, me.getUUID()) + ", expected " + LostCaravanStory.ENDING_REPUTATION);
				helper.succeed();
			});
		});
	}

	/**
	 * "Walk him home to either village": a third village's standing hall is neither. Walking Tobin there ends nothing,
	 * with the receiving hall standing or broken, and the story doesn't move to it (it's far beyond the receiving
	 * village's radius).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaVerify1102ThirdHall"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVerify1102ThirdHall")
	public void aThirdVillagesHallIsNotHome(GameTestHelper helper) {
		BlockPos[] ab = QaLostCaravanGameTests.begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		VillageHalls.RADIUS = 8; // (begin puts it back)
		ArcState s = QaLostCaravanGameTests.lose(helper, a, b);
		helper.setBlock(THERE, ModBlocks.VILLAGE_HALL); // a third village, 320 from the receiving one
		BlockPos c = helper.absolutePos(THERE);
		Leftovers.after(helper, () -> Arcs.stopAll(level, c));
		QaLostCaravanGameTests.toTheCaptive(helper, a, s);
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		Leftovers.after(helper, me::discard);
		Villager tobin = following(helper, s, me);
		walkTo(helper, tobin, me, c);
		helper.assertTrue(Arcs.running(level, a) == s && !s.flags.containsKey(LostCaravanStory.TOBIN_HOME),
			"walked to a third village's hall (the receiving one standing), the story ended");
		level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
		helper.runAfterDelay(2, () -> {
			walkTo(helper, tobin, me, c);
			helper.assertTrue(Arcs.running(level, a) == s && !s.flags.containsKey(LostCaravanStory.TOBIN_HOME),
				"walked to a third village's hall (the receiving one broken), the story ended");
			helper.assertTrue(b.equals(LostCaravanStory.to(s)), "the story moved to a far village's hall: " + LostCaravanStory.to(s));
			BlockPos by = helper.absolutePos(new BlockPos(3, 2, 5));
			walkTo(helper, tobin, me, by);
			helper.assertTrue(Arcs.running(level, a) != s, "walked into the sending village, the story runs on");
			helper.succeed();
		});
	}

	/**
	 * The receiving hall broken and the server restarted: where it stood still isn't home. The village's hall set up
	 * again 7 blocks on takes him home: the arc ends, and the ending's standing goes to the new hall, none to the spot
	 * where the old one stood.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaVerify1102Reload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVerify1102Reload")
	public void aBrokenHallStillIsNotHomeAfterARestartAndTheNewHallIs(GameTestHelper helper) {
		BlockPos[] ab = QaLostCaravanGameTests.begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = QaLostCaravanGameTests.lose(helper, a, b);
		BlockPos there = receivingHere(helper, s);
		QaLostCaravanGameTests.toTheCaptive(helper, a, s);
		helper.setBlock(THERE, Blocks.AIR); // the receiving hall broken
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		Leftovers.after(helper, me::discard);
		Villager tobin = following(helper, s, me);
		BlockPos newHall = new BlockPos(28, 2, 20); // 7 from where the old one stood
		BlockPos rebuilt = helper.absolutePos(newHall);
		Leftovers.after(helper, () -> Arcs.stopAll(level, rebuilt));
		QaLostCaravanGameTests.reloadStories(level);
		ArcState again = Arcs.running(level, a);
		helper.assertTrue(again != null && again.id.equals(LostCaravanStory.ARC.toString()), "the arc didn't survive the restart: " + again);
		helper.assertTrue(me.getUUID().equals(LostCaravanStory.leader(again)), "after the restart he doesn't follow the player");
		walkTo(helper, tobin, me, there);
		helper.assertTrue(Arcs.running(level, a) == again && !again.flags.containsKey(LostCaravanStory.TOBIN_HOME),
			"after a restart, walked to where the broken hall stood, the story ended");
		helper.runAfterDelay(2, () -> {
			helper.setBlock(newHall, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(2, () -> {
				walkTo(helper, tobin, me, rebuilt);
				helper.assertTrue(Arcs.running(level, a) != again, "walked to the village's new hall, the story runs on (to: " + LostCaravanStory.to(again) + ")");
				helper.assertTrue(Reputation.points(level, rebuilt, me.getUUID()) == LostCaravanStory.ENDING_REPUTATION,
					"standing at the new hall: " + Reputation.points(level, rebuilt, me.getUUID()));
				helper.assertTrue(Reputation.honours(level, rebuilt, me.getUUID()).contains("wayfinder"), "no Wayfinder at the new hall");
				helper.assertTrue(Reputation.points(level, there, me.getUUID()) == 0, "standing where the old hall stood: " + Reputation.points(level, there, me.getUUID()));
				helper.succeed();
			});
		});
	}

	// ---- B791 --------------------------------------------------------------------------------------------------------

	/** Six stacks, the 1st, 3rd and 6th renamed to removed mods' items: the 2nd, 4th and 5th come back, in order, exactly. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aBagLosesOnlyItsGoneStacksAtTheEndsAndInTheMiddleInOrder(GameTestHelper helper) {
		Villager builder = EntityType.VILLAGER.create(helper.getLevel());
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(builder);
		Item[] items = {Items.GLASS, Items.COBBLESTONE, Items.SAND, Items.OAK_PLANKS, Items.DIRT, Items.GRAVEL};
		for (int i = 0; i < items.length; i++) {
			bag.addAll(items[i], 10 + i);
		}
		CompoundTag saved = builder.saveWithoutId(new CompoundTag());
		ListTag stacks = (ListTag) QaRemovedModItems1102BugTests.attachment(saved, "builder_bag");
		helper.assertTrue(stacks.size() == 6, "the bag didn't save 6 stacks: " + stacks);
		int renamed = 0;
		for (int i = 0; i < stacks.size(); i++) {
			String id = stacks.getCompound(i).getString("id");
			if (id.equals("minecraft:glass") || id.equals("minecraft:sand") || id.equals("minecraft:gravel")) {
				stacks.getCompound(i).putString("id", renamed++ % 2 == 0 ? GONE : GONE2);
			}
		}
		Villager reopened = EntityType.VILLAGER.create(helper.getLevel());
		reopened.load(saved);
		BuilderBag back = ModAttachments.BUILDER_BAG.get(reopened);
		helper.assertTrue(back != null, "the whole bag was lost for three removed stacks");
		List<String> got = back.stacks().stream().map(st -> st.getItem() + " x" + st.getCount()).toList();
		List<String> want = List.of(Items.COBBLESTONE + " x11", Items.OAK_PLANKS + " x13", Items.DIRT + " x14");
		helper.assertTrue(got.equals(want), "the bag after the load: " + got + ", expected " + want);
		helper.succeed();
	}

	/** A bag whose stacks are ALL removed items loads as an empty bag, and the builder's other saved things load too. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aBagOfOnlyGoneStacksLoadsEmptyAndTheVillagersOtherDataStays(GameTestHelper helper) {
		Villager builder = EntityType.VILLAGER.create(helper.getLevel());
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(builder);
		bag.addAll(Items.GLASS, 8);
		bag.addAll(Items.SAND, 4);
		ModAttachments.HEAD_START.set(builder, 3);
		CompoundTag saved = builder.saveWithoutId(new CompoundTag());
		ListTag stacks = (ListTag) QaRemovedModItems1102BugTests.attachment(saved, "builder_bag");
		stacks.getCompound(0).putString("id", GONE);
		stacks.getCompound(1).putString("id", GONE2);
		Villager reopened = EntityType.VILLAGER.create(helper.getLevel());
		reopened.load(saved);
		BuilderBag back = ModAttachments.BUILDER_BAG.get(reopened);
		helper.assertTrue(back == null || back.stacks().isEmpty(), "removed stacks came back as something: " + back.stacks());
		helper.assertTrue(ModAttachments.HEAD_START.getOrElse(reopened, 1) == 3, "the villager's other saved data was lost with the bag");
		helper.succeed();
	}

	/** A named, damaged pickaxe next to a removed item: the stack keeps its components and the gone one is dropped. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aStackWithComponentsBesideAGoneOneKeepsItsComponents(GameTestHelper helper) {
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		pick.set(DataComponents.CUSTOM_NAME, Component.literal("Old Reliable"));
		pick.setDamageValue(17);
		Villager builder = EntityType.VILLAGER.create(helper.getLevel());
		ModAttachments.BUILDER_BAG.set(builder, BuilderBag.of(List.of(new ItemStack(Items.GLASS, 5), pick.copy(), new ItemStack(Items.TORCH, 9))));
		CompoundTag saved = builder.saveWithoutId(new CompoundTag());
		ListTag stacks = (ListTag) QaRemovedModItems1102BugTests.attachment(saved, "builder_bag");
		stacks.getCompound(0).putString("id", GONE);
		Villager reopened = EntityType.VILLAGER.create(helper.getLevel());
		reopened.load(saved);
		BuilderBag back = ModAttachments.BUILDER_BAG.get(reopened);
		helper.assertTrue(back != null && back.stacks().size() == 2, "the bag after the load: " + (back == null ? "lost" : back.stacks()));
		ItemStack got = back.stacks().get(0);
		helper.assertTrue(ItemStack.isSameItemSameComponents(got, pick), "the pickaxe lost its components: " + got + " " + got.getComponentsPatch());
		helper.assertTrue(back.stacks().get(1).is(Items.TORCH) && back.stacks().get(1).getCount() == 9, "the torches: " + back.stacks().get(1));
		helper.succeed();
	}

	/**
	 * Saved, loaded with two removed stacks, saved again: the second save is the first minus exactly those two entries
	 * (nothing invented, nothing reordered), and it loads again with no change.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aBagSavedAgainIsTheOldSaveMinusTheGoneStacks(GameTestHelper helper) {
		Villager builder = EntityType.VILLAGER.create(helper.getLevel());
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(builder);
		bag.addAll(Items.COBBLESTONE, 40);
		bag.addAll(Items.GLASS, 8);
		bag.addAll(Items.OAK_LOG, 12);
		bag.addAll(Items.SAND, 3);
		CompoundTag saved = builder.saveWithoutId(new CompoundTag());
		ListTag stacks = (ListTag) QaRemovedModItems1102BugTests.attachment(saved, "builder_bag");
		ListTag expected = new ListTag();
		for (int i = 0; i < stacks.size(); i++) {
			CompoundTag st = stacks.getCompound(i);
			if (st.getString("id").equals("minecraft:glass") || st.getString("id").equals("minecraft:sand")) {
				st.putString("id", GONE);
			} else {
				expected.add(st.copy());
			}
		}
		Villager once = EntityType.VILLAGER.create(helper.getLevel());
		once.load(saved);
		Tag first = QaRemovedModItems1102BugTests.attachment(once.saveWithoutId(new CompoundTag()), "builder_bag");
		helper.assertTrue(expected.equals(first), "saved again: " + first + ", expected " + expected);
		Villager twice = EntityType.VILLAGER.create(helper.getLevel());
		twice.load(once.saveWithoutId(new CompoundTag()));
		Tag second = QaRemovedModItems1102BugTests.attachment(twice.saveWithoutId(new CompoundTag()), "builder_bag");
		helper.assertTrue(first.equals(second), "a second round trip changed the bag: " + second);
		helper.succeed();
	}

	/** A Hauler Golem's load with two removed items' keys (and every key gone): the others load; all gone loads empty. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aGolemsLoadDropsEveryGoneKeyAndKeepsTheRest(GameTestHelper helper) {
		IronGolem golem = EntityType.IRON_GOLEM.create(helper.getLevel());
		Map<Item, Integer> load = new LinkedHashMap<>();
		load.put(Items.COBBLESTONE, 64);
		load.put(Items.OAK_LOG, 20);
		load.put(Items.WHEAT, 12);
		load.put(Items.CARROT, 7);
		ModAttachments.HAULER_LOAD.set(golem, load);
		CompoundTag saved = golem.saveWithoutId(new CompoundTag());
		CompoundTag map = (CompoundTag) QaRemovedModItems1102BugTests.attachment(saved, "hauler_load");
		map.put(GONE, map.get("minecraft:wheat"));
		map.remove("minecraft:wheat");
		map.put(GONE2, map.get("minecraft:carrot"));
		map.remove("minecraft:carrot");
		IronGolem reopened = EntityType.IRON_GOLEM.create(helper.getLevel());
		reopened.load(saved);
		Map<Item, Integer> back = ModAttachments.HAULER_LOAD.get(reopened);
		helper.assertTrue(back != null && back.size() == 2 && Integer.valueOf(64).equals(back.get(Items.COBBLESTONE))
			&& Integer.valueOf(20).equals(back.get(Items.OAK_LOG)), "the load after dropping two gone keys: " + back);

		CompoundTag allGone = golem.saveWithoutId(new CompoundTag());
		CompoundTag map2 = (CompoundTag) QaRemovedModItems1102BugTests.attachment(allGone, "hauler_load");
		for (String key : List.copyOf(map2.getAllKeys())) {
			map2.put("gonemod:" + key.replace(':', '_'), map2.get(key));
			map2.remove(key);
		}
		IronGolem empty = EntityType.IRON_GOLEM.create(helper.getLevel());
		empty.load(allGone);
		Map<Item, Integer> none = ModAttachments.HAULER_LOAD.get(empty);
		helper.assertTrue(none == null || none.isEmpty(), "a load of only gone items came back as: " + none);
		helper.succeed();
	}

	/** A Mechanic's trip carrying only removed items still loads: its machine and stage stay, it carries nothing. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aMachineTripWhoseEveryCarriedStackIsGoneStillLoads(GameTestHelper helper) {
		Villager mechanic = EntityType.VILLAGER.create(helper.getLevel());
		BlockPos machine = new BlockPos(5, 64, 5);
		ModAttachments.MACHINE_JOB.set(mechanic, new MachineJob(machine, AliveWorkplace.id("millstone"), MachineJob.Stage.FEED,
			List.of(new ItemStack(Items.WHEAT, 12), new ItemStack(Items.SUGAR, 4))));
		CompoundTag saved = mechanic.saveWithoutId(new CompoundTag());
		ListTag carried = ((CompoundTag) QaRemovedModItems1102BugTests.attachment(saved, "machine_job")).getList("carried", Tag.TAG_COMPOUND);
		carried.getCompound(0).putString("id", "create:wheat_flour");
		carried.getCompound(1).putString("id", "create:cinder_flour");
		Villager reopened = EntityType.VILLAGER.create(helper.getLevel());
		reopened.load(saved);
		MachineJob back = ModAttachments.MACHINE_JOB.get(reopened);
		helper.assertTrue(back != null, "the trip was lost because everything it carried came from a removed mod");
		helper.assertTrue(back.machine().equals(machine) && back.stage() == MachineJob.Stage.FEED && back.carried().isEmpty(),
			"the trip after the load: " + back);
		helper.succeed();
	}
}
