package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks.State;
import io.github.jcondedata.aliveworkplace.table.TableLibrary;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-b-1103-1238) of docs/wiki/blueprint-library.md section 1, the Blueprint Table's unlocks, from the spec
 * sentences no test pinned down yet:
 * <ul>
 * <li>"A builder of the village leveling up: the next 3 locked builds (lowest tier first, then by id; never a rare
 * one)": the existing tests only compare the level-up with {@code nextForLevelUp}'s own answer; these check the order
 * against every build still locked, and the boundary with fewer than 3 locked left.</li>
 * <li>"A builder of the village leveling up": another job's level-up unlocks nothing (through the same vanilla level-up
 * call, with a builder as the control that the call works).</li>
 * <li>"Store All (every Blueprint and Wonder Plan carried; Blank Blueprints stay)": the Store All test that exists
 * carries no Wonder Plan.</li>
 * </ul>
 * Each test is alone in its batch, with its own hall and a small village radius.
 */
public class QaBlueprintLibrary1103bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = QaBlueprintUnlocksGameTests.AREA;
	private static final BlockPos HALL = QaBlueprintUnlocksGameTests.HALL;
	private static final BlockPos TABLE = QaBlueprintUnlocksGameTests.TABLE;
	private static final String NEW_LINE = QaBlueprintUnlocksGameTests.NEW_LINE;

	/** Every build the Table lists, with where it stands in the village round {@code hall}. */
	private static Map<ResourceLocation, State> states(ServerLevel level, BlockPos hall) {
		Map<ResourceLocation, State> out = new LinkedHashMap<>();
		for (ResourceLocation id : BlueprintLibrary.list(level.getServer(), false)) {
			out.put(id, BlueprintUnlocks.state(level, hall, id));
		}
		return out;
	}

	private static List<ResourceLocation> with(Map<ResourceLocation, State> states, State state) {
		List<ResourceLocation> out = new ArrayList<>();
		states.forEach((id, s) -> {
			if (s == state) {
				out.add(id);
			}
		});
		return out;
	}

	/** Whether {@code a} comes before {@code b} in the spec's order: lowest tier first, then by id. */
	private static boolean before(ResourceLocation a, ResourceLocation b) {
		int ta = BlueprintUpgrades.tier(a);
		int tb = BlueprintUpgrades.tier(b);
		return ta != tb ? ta < tb : a.toString().compareTo(b.toString()) < 0;
	}

	private static Villager builder(GameTestHelper helper) {
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
		return builder;
	}

	/** A builder's level-up as the builder earns it (a Novice with no bench loses the job in a few ticks: hired again). */
	private static void levelUp(GameTestHelper helper, Villager builder) {
		int was = builder.getVillagerData().getLevel();
		builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
		io.github.jcondedata.aliveworkplace.build.BuilderLevels.addXp(helper.getLevel(), builder, 30, null);
		helper.assertTrue(builder.getVillagerData().getLevel() == 2, "the builder didn't level up: " + was + " -> " + builder.getVillagerData().getLevel());
	}

	/**
	 * The three a level-up unlocks are the first three of everything locked, lowest tier first and then by id: each
	 * comes before every build still locked, nothing else changes state, and no "???" opens.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLib1103bOrder"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLib1103bOrder")
	public void aLevelUpUnlocksTheLowestTierThenTheFirstIds(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		Villager builder = builder(helper);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Map<ResourceLocation, State> before = states(level, hall);
			List<ResourceLocation> lockedBefore = with(before, State.LOCKED);
			helper.assertTrue(lockedBefore.size() > BlueprintUnlocks.PER_LEVEL, "fewer than 4 builds locked to begin with: " + lockedBefore);
			helper.assertTrue(lockedBefore.stream().anyMatch(id -> BlueprintUpgrades.tier(id) >= 2), "no tier-2 build is locked: the order isn't tried");
			levelUp(helper, builder);
			Map<ResourceLocation, State> after = states(level, hall);
			List<ResourceLocation> opened = new ArrayList<>();
			for (Map.Entry<ResourceLocation, State> e : before.entrySet()) {
				State now = after.get(e.getKey());
				if (now != e.getValue()) {
					helper.assertTrue(e.getValue() == State.LOCKED && now == State.UNLOCKED,
						"a level-up turned " + e.getKey() + " from " + e.getValue() + " to " + now);
					opened.add(e.getKey());
				}
			}
			helper.assertTrue(opened.size() == BlueprintUnlocks.PER_LEVEL, "a level-up unlocked " + opened.size() + " builds: " + opened);
			List<ResourceLocation> stillLocked = with(after, State.LOCKED);
			for (ResourceLocation o : opened) {
				for (ResourceLocation l : stillLocked) {
					helper.assertTrue(before(o, l), "the level-up unlocked " + o + " (tier " + BlueprintUpgrades.tier(o) + ") before "
						+ l + " (tier " + BlueprintUpgrades.tier(l) + ")");
				}
			}
			helper.assertTrue(with(after, State.HIDDEN).equals(with(before, State.HIDDEN)), "a level-up changed the ??? builds");
			helper.succeed();
		});
	}

	/**
	 * With two builds locked, a level-up unlocks those two (not a "???" in place of the third) and tells the player in
	 * the village of exactly two.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLib1103bTwoLeft"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLib1103bTwoLeft")
	public void aLevelUpWithTwoLockedLeftUnlocksJustThoseTwo(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		Villager builder = builder(helper);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			List<ResourceLocation> locked = with(states(level, hall), State.LOCKED);
			helper.assertTrue(locked.size() > 2, "only " + locked.size() + " locked to begin with");
			// Unlock all but the last two in the spec's order (the highest tiers), so the two left are not the first ones.
			locked.sort((a, b) -> before(a, b) ? -1 : before(b, a) ? 1 : 0);
			List<ResourceLocation> left = locked.subList(locked.size() - 2, locked.size());
			for (ResourceLocation id : locked.subList(0, locked.size() - 2)) {
				BlueprintUnlocks.unlock(level, hall, id, List.of());
			}
			List<ResourceLocation> hiddenBefore = with(states(level, hall), State.HIDDEN);
			helper.assertFalse(hiddenBefore.isEmpty(), "no ??? build to keep hidden");
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, TABLE);
			levelUp(helper, builder);
			Map<ResourceLocation, State> after = states(level, hall);
			for (ResourceLocation id : left) {
				helper.assertTrue(after.get(id) == State.UNLOCKED, "the level-up left " + id + " " + after.get(id));
			}
			helper.assertTrue(with(after, State.LOCKED).isEmpty(), "still locked: " + with(after, State.LOCKED));
			helper.assertTrue(with(after, State.HIDDEN).equals(hiddenBefore), "a level-up with two left opened a ??? build");
			long lines = QaBlueprintUnlocksGameTests.linesOf(c.seen(), NEW_LINE);
			helper.assertTrue(lines == 2, "the player in the village was told of " + lines + " new builds, expected 2");
			helper.succeed();
		});
	}

	/**
	 * "A builder of the village leveling up" unlocks builds; a Farmer of the same village leveling up (the same vanilla
	 * call, {@code increaseMerchantCareer}) unlocks nothing and says nothing. The builder through that call is the control.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLib1103bOtherJob"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLib1103bOtherJob")
	public void anotherJobsLevelUpUnlocksNothing(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 6));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 3));
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, TABLE);
			java.lang.reflect.Method career;
			try {
				career = Villager.class.getDeclaredMethod("increaseMerchantCareer");
				career.setAccessible(true);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("no increaseMerchantCareer on Villager", e);
			}
			Map<ResourceLocation, State> before = states(level, hall);
			farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(1));
			try {
				career.invoke(farmer);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
			helper.assertTrue(farmer.getVillagerData().getLevel() == 2, "the farmer didn't level up");
			helper.assertTrue(states(level, hall).equals(before), "a Farmer's level-up changed the village's library");
			helper.assertTrue(QaBlueprintUnlocksGameTests.linesOf(c.seen(), NEW_LINE) == 0, "a Farmer's level-up said 'new blueprint'");
			// The control: the same call on a builder unlocks three.
			builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
			try {
				career.invoke(builder);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
			long opened = with(states(level, hall), State.UNLOCKED).size() - with(before, State.UNLOCKED).size();
			helper.assertTrue(opened == BlueprintUnlocks.PER_LEVEL, "the control builder's level-up unlocked " + opened);
			helper.succeed();
		});
	}

	/**
	 * Store All takes every Blueprint and Wonder Plan carried and leaves the Blank Blueprints: the plan is listed on the
	 * Stored tab and comes back out whole.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaLib1103bStorePlan"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaLib1103bStorePlan")
	public void storeAllTakesTheWonderPlanAndLeavesTheBlanks(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos table = helper.absolutePos(TABLE);
			helper.assertFalse(Wonders.loaded().isEmpty(), "no Wonder loaded: " + Wonders.problems());
			Wonder wonder = Wonders.loaded().values().iterator().next();
			ItemStack plan = WonderPlanItem.of(level, hall, wonder, Component.literal("Testholm"));
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, TABLE);
			var inv = c.player().getInventory();
			inv.clearContent();
			inv.setItem(3, plan.copy());
			inv.setItem(12, BlueprintItem.create(StarterBlueprints.STONE_HOUSE.id(), null));
			inv.setItem(7, new ItemStack(ModItems.BLANK_BLUEPRINT, 5));
			inv.setItem(25, new ItemStack(Items.PAPER, 3));
			int stored = TableLibrary.store(c.player(), table, true);
			helper.assertTrue(stored == 2, "Store All stored " + stored + ", expected the plan and the blueprint");
			helper.assertTrue(inv.countItem(ModItems.WONDER_PLAN) == 0, "the Wonder Plan stayed in the inventory");
			helper.assertTrue(inv.countItem(ModItems.BLANK_BLUEPRINT) == 5, "Store All took Blank Blueprints: " + inv.countItem(ModItems.BLANK_BLUEPRINT) + " left");
			helper.assertTrue(inv.countItem(Items.PAPER) == 3, "Store All took paper");
			List<TablePayloads.Stored> list = TableServer.openPayload(c.player(), table).stored();
			TablePayloads.Stored listed = list.stream().filter(s -> s.stack().is(ModItems.WONDER_PLAN)).findFirst().orElse(null);
			helper.assertTrue(listed != null && listed.count() == 1, "the Stored tab doesn't list the Wonder Plan: " + list);
			helper.assertTrue(TableLibrary.takeOut(c.player(), table, listed.stack()), "Take One Out refused the Wonder Plan");
			ItemStack back = ItemStack.EMPTY;
			for (int i = 0; i < inv.getContainerSize(); i++) {
				if (inv.getItem(i).is(ModItems.WONDER_PLAN)) {
					back = inv.getItem(i);
				}
			}
			helper.assertTrue(ItemStack.isSameItemSameComponents(back, plan), "the Wonder Plan came back changed: " + back.getComponents());
			helper.succeed();
		});
	}
}
