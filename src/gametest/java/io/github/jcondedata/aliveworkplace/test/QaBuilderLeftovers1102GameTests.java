package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * QA (2026-11-02) of B820's builder side ("a builder walked his bag to the Storehouse while his own chests had room in
 * their stacks") and of the Hauler Golem with items that carry a name, an enchantment or damage, from what a player
 * expects rather than from the code:
 * <ul>
 * <li>a Builder's leftovers go into his own chest when they fit there, part stacks included; only what doesn't fit goes
 * on to the Storehouse; a brim-full chest sends all of it on; nothing is lost, dropped or doubled, across a reload too;</li>
 * <li>a Hauler Golem (29.15: "what the village's workers make, by PorterWork's rules") hauls plain goods; a named log
 * stack and a named, enchanted, worn axe in a lumberjack's chest stay where they are with all of that intact, in a
 * plain run and across a reload of the golem, and a named log already in the Storehouse chest keeps its name.</li>
 * </ul>
 */
public class QaBuilderLeftovers1102GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	// QaBuilderStockGameTests' village: the table's own chest and the Storehouse's two chests 34 blocks off
	private static final BlockPos STOREHOUSE = new BlockPos(26, 2, 26);
	private static final BlockPos[] STORE_CHESTS = {new BlockPos(23, 2, 26), new BlockPos(26, 2, 23)};
	/** A full bag of andesite: 26 stacks of 64 and one of 1, every one of its 27 slots taken. */
	private static final int BAG_FULL = 26 * 64 + 1;
	/** What a chest with 27 slots of 64 holds. */
	private static final int CHEST_FULL = 27 * 64;

	// the hauler's village (HaulerEveryChestGameTests.village)
	private static final BlockPos H_STORE_CHEST = new BlockPos(4, 2, 4);
	private static final BlockPos[] H_CHESTS = {new BlockPos(12, 2, 27), new BlockPos(22, 2, 27), new BlockPos(26, 2, 15)};

	// --- A: a Builder's leftovers ------------------------------------------------------------------------------------

	/**
	 * B820: the Builder's bag is full of andesite (1,665, not part of the hut) and his chest has one andesite in each of
	 * its 27 slots: room for 1,701. Needing the hut's cobblestone, he empties his bag into his own chest, not the
	 * Storehouse: 1,692 andesite by his table, none in the Storehouse, none on the ground, and the hut is built.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaLeftoversPartStacks"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaLeftoversPartStacks")
	public void qaLeftoversFillThePartStacksOfHisOwnChest(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder b = fullBagBuilder(helper, i -> new ItemStack(Items.ANDESITE, 1));
		helper.succeedWhen(() -> {
			assertDone(helper, b);
			assertAndesite(helper, b, 27 + BAG_FULL, 0);
		});
	}

	/**
	 * Every slot of his chest holds one diorite: his chest takes no andesite, so the bag's 1,665 andesite go on to the
	 * Storehouse, every one of them; the 27 diorite stay as they were.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaLeftoversOtherItem"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaLeftoversOtherItem")
	public void qaLeftoversGoToTheStorehouseWhenEverySlotHoldsAnotherItem(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder b = fullBagBuilder(helper, i -> new ItemStack(Items.DIORITE, 1));
		helper.succeedWhen(() -> {
			assertDone(helper, b);
			assertAndesite(helper, b, 0, BAG_FULL);
			int diorite = table(helper).countItem(Items.DIORITE);
			helper.assertTrue(diorite == 27, "the table's chest has " + diorite + " of its 27 diorite");
		});
	}

	/**
	 * Boundary: his chest's 27 slots hold 64 andesite between them (38 in the first, 1 in each other), room for exactly
	 * 1,664, and his bag holds 1,665. The chest ends brim-full (1,728) and the one extra goes to the Storehouse: not
	 * lost, not dropped.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaLeftoversOneOver"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaLeftoversOneOver")
	public void qaTheOneLeftoverHisChestHasNoRoomForGoesToTheStorehouse(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder b = fullBagBuilder(helper, i -> new ItemStack(Items.ANDESITE, i == 0 ? 38 : 1));
		helper.succeedWhen(() -> {
			assertDone(helper, b);
			assertAndesite(helper, b, CHEST_FULL, 1);
		});
	}

	/** His chest is brim-full of andesite (27 stacks of 64): all 1,665 of the bag's go to the Storehouse. */
	//$ gametest_ticks_batch HUGE '3000' '"qaLeftoversBrimFull"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaLeftoversBrimFull")
	public void qaABrimFullChestSendsTheLeftoversToTheStorehouse(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder b = fullBagBuilder(helper, i -> new ItemStack(Items.ANDESITE, 64));
		helper.succeedWhen(() -> {
			assertDone(helper, b);
			assertAndesite(helper, b, CHEST_FULL, BAG_FULL);
		});
	}

	/**
	 * Save and reload while he carries the leftovers: the build sites and the Builder (as a fresh entity) are saved and
	 * loaded before he has put anything away. He still carries all 1,665 andesite, and they still go into the part
	 * stacks of his own chest.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaLeftoversReload"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaLeftoversReload")
	public void qaAReloadedBuilderStillPutsHisLeftoversInHisOwnChest(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder first = fullBagBuilder(helper, i -> new ItemStack(Items.ANDESITE, 1));
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		CompoundTag tag = new CompoundTag();
		first.villager().saveWithoutId(tag);
		first.villager().discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		int carried = ModAttachments.BUILDER_BAG.getOrCreate(copy).count(Items.ANDESITE);
		helper.assertTrue(carried == BAG_FULL, "after the reload the Builder carries " + carried + " of " + BAG_FULL + " andesite");
		var site = BuildSiteManager.get(level).get(first.site().id());
		helper.assertTrue(site != null, "the build was lost in the reload");
		QaBuilderStockGameTests.Builder b = new QaBuilderStockGameTests.Builder(copy, site, first.plan());
		helper.succeedWhen(() -> {
			assertDone(helper, b);
			assertAndesite(helper, b, 27 + BAG_FULL, 0);
		});
	}

	/**
	 * The finish: he builds the hut from what he carries (the hut's materials and 10 andesite besides), and his chest has
	 * one andesite in each slot. When the hut is done the 10 andesite go into those part stacks (37), not to the
	 * Storehouse, and no hut material is left in his bag.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaLeftoversAtTheEnd"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaLeftoversAtTheEnd")
	public void qaLeftoversAtTheEndOfAJobFillHisPartStacks(GameTestHelper helper) {
		ServerLevel level = village(helper, false);
		Container chest = table(helper);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.ANDESITE, 1));
		}
		QaBuilderStockGameTests.Builder b = QaBuilderStockGameTests.builder(helper, QaBuilderStockGameTests.HUT);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(b.villager());
		for (ItemStack s : List.of(new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR),
				new ItemStack(Items.TORCH), new ItemStack(Items.ANDESITE, 10))) {
			helper.assertTrue(bag.add(s).isEmpty(), "the bag didn't take " + s);
		}
		ModAttachments.BUILDER_BAG.set(b.villager(), bag);
		helper.succeedWhen(() -> {
			assertDone(helper, b);
			assertAndesite(helper, b, 37, 0);
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(b.villager()).isEmpty(),
				"the finished Builder still carries " + ModAttachments.BUILDER_BAG.getOrCreate(b.villager()).stacks());
		});
	}

	// --- B: a Hauler Golem and items with a name, an enchantment or damage ---------------------------------------------

	/**
	 * The first lumberjack's chest holds, besides its 64 plain logs, 64 logs named "Grandpa's Oak" and a named iron axe
	 * with Efficiency III and 100 damage; the Storehouse chest already holds one named log. The golem brings the 192
	 * plain logs; the named logs and the axe stay in the lumberjack's chest exactly as they were (name, enchantment,
	 * damage), and the named log in the Storehouse keeps its name and count.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaHaulerNamedPlain"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaHaulerNamedPlain")
	public void qaHaulerLeavesNamedAndEnchantedItemsUntouched(GameTestHelper helper) {
		Special s = haulerVillage(helper);
		IronGolem golem = HaulerEveryChestGameTests.hauler(helper);
		helper.succeedWhen(() -> assertHauled(helper, s, golem));
	}

	/** The same, with the golem saved and loaded again (as a fresh entity) 200 ticks into its work. */
	//$ gametest_ticks_batch HUGE '4800' '"qaHaulerNamedReload"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaHaulerNamedReload")
	public void qaAReloadedHaulerLeavesNamedAndEnchantedItemsUntouched(GameTestHelper helper) {
		Special s = haulerVillage(helper);
		IronGolem golem = HaulerEveryChestGameTests.hauler(helper);
		IronGolem[] now = {golem};
		helper.runAfterDelay(200, () -> now[0] = QaHaulerWaterVerify1102GameTests.reloaded(helper, golem));
		helper.succeedWhen(() -> {
			helper.assertTrue(now[0] != golem, "the golem wasn't reloaded yet");
			assertHauled(helper, s, now[0]);
		});
	}

	/**
	 * A lumberjack's chest holds only named logs (three stacks of 64 "Grandpa's Oak") and the axe: nothing plain to
	 * haul. After 1,500 ticks every one is still there with its name, the golem carries nothing and the Storehouse chest
	 * has no logs.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaHaulerOnlyNamed"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaHaulerOnlyNamed")
	public void qaHaulerTakesNothingFromAChestOfOnlyNamedLogs(GameTestHelper helper) {
		HaulerEveryChestGameTests.village(helper);
		for (BlockPos c : H_CHESTS) {
			((Container) helper.getBlockEntity(c)).clearContent();
		}
		Container jack = helper.getBlockEntity(H_CHESTS[0]);
		ItemStack axe = axe(helper);
		for (int i = 0; i < 3; i++) {
			jack.setItem(i, namedLogs(64));
		}
		jack.setItem(3, axe.copy());
		Container store = helper.getBlockEntity(H_STORE_CHEST);
		IronGolem golem = HaulerEveryChestGameTests.hauler(helper);
		helper.runAfterDelay(1500, () -> {
			int named = count(jack, QaBuilderLeftovers1102GameTests::isNamedLog);
			helper.assertTrue(named == 192, "the lumberjack's chest has " + named + " of 192 named logs; the golem carries " + HaulerGolems.load(golem)
				+ "; the Storehouse chest " + store.countItem(Items.OAK_LOG) + " logs");
			helper.assertTrue(HaulerGolems.load(golem).isEmpty(), "the golem carries " + HaulerGolems.load(golem));
			helper.assertTrue(store.countItem(Items.OAK_LOG) == 0 && store.countItem(Items.IRON_AXE) == 0,
				"the Storehouse chest has " + store.countItem(Items.OAK_LOG) + " logs and " + store.countItem(Items.IRON_AXE) + " axes");
			helper.assertTrue(count(jack, st -> ItemStack.matches(st, axe)) == 1, "the axe isn't in the lumberjack's chest as it was: " + stacks(jack));
			helper.succeed();
		});
	}

	// --- helpers -----------------------------------------------------------------------------------------------------

	/**
	 * QaBuilderStockGameTests' village with every hut material in the Storehouse (cobblestone, planks and torch in its
	 * first chest, the door in its second), the gamerules put back after the test.
	 */
	private static ServerLevel village(GameTestHelper helper, boolean materialsInStore) {
		ServerLevel level = helper.getLevel();
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		QaBuilderStockGameTests.setup(helper, STOREHOUSE, materialsInStore ? 25 : 0, materialsInStore ? 1 : 0);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		if (materialsInStore) {
			Container store = helper.getBlockEntity(STORE_CHESTS[0]);
			store.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
			store.setItem(2, new ItemStack(Items.TORCH));
		} else {
			for (BlockPos c : STORE_CHESTS) {
				((Container) helper.getBlockEntity(c)).clearContent();
			}
		}
		return level;
	}

	/** The village, the table's chest filled slot by slot from {@code slot}, and the hut's Builder with a full bag of andesite. */
	private static QaBuilderStockGameTests.Builder fullBagBuilder(GameTestHelper helper, java.util.function.IntFunction<ItemStack> slot) {
		village(helper, true);
		Container chest = table(helper);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			chest.setItem(i, slot.apply(i));
		}
		QaBuilderStockGameTests.Builder b = QaBuilderStockGameTests.builder(helper, QaBuilderStockGameTests.HUT);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(b.villager());
		helper.assertTrue(bag.add(new ItemStack(Items.ANDESITE, BAG_FULL)).isEmpty() && bag.freeSlots() == 0,
			"the bag didn't take " + BAG_FULL + " andesite into all of its slots: " + bag.freeSlots() + " free");
		ModAttachments.BUILDER_BAG.set(b.villager(), bag);
		return b;
	}

	private static Container table(GameTestHelper helper) {
		return helper.getBlockEntity(QaBuilderStockGameTests.TABLE_CHEST);
	}

	private static void assertDone(GameTestHelper helper, QaBuilderStockGameTests.Builder b) {
		helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(b.site().id()) == null, "still building: stage " + b.site().stage()
			+ ", status " + b.site().status() + ", missing " + b.site().missing() + "; bag " + ModAttachments.BUILDER_BAG.getOrCreate(b.villager()).stacks());
		helper.assertTrue(b.plan().unfinished(helper.getLevel()).isEmpty(), b.plan().unfinished(helper.getLevel()).size() + " block(s) of the hut wrong");
	}

	/** The andesite by the table and in the Storehouse are as expected, and none is in his bag or on the ground. */
	private static void assertAndesite(GameTestHelper helper, QaBuilderStockGameTests.Builder b, int atTable, int inStore) {
		int table = table(helper).countItem(Items.ANDESITE);
		int store = 0;
		for (BlockPos c : STORE_CHESTS) {
			store += ((Container) helper.getBlockEntity(c)).countItem(Items.ANDESITE);
		}
		int bag = ModAttachments.BUILDER_BAG.getOrCreate(b.villager()).count(Items.ANDESITE);
		int ground = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(Items.ANDESITE))) {
			ground += e.getItem().getCount();
		}
		helper.assertTrue(table == atTable && store == inStore && bag == 0 && ground == 0,
			"andesite: " + table + " by the table (expected " + atTable + "), " + store + " in the Storehouse (expected " + inStore + "), " + bag
				+ " in his bag, " + ground + " on the ground");
	}

	private record Special(Container jack, Container store, ItemStack axe) {
	}

	private static Special haulerVillage(GameTestHelper helper) {
		HaulerEveryChestGameTests.village(helper);
		Container jack = helper.getBlockEntity(H_CHESTS[0]);
		ItemStack axe = axe(helper);
		jack.setItem(1, namedLogs(64));
		jack.setItem(2, axe.copy());
		Container store = helper.getBlockEntity(H_STORE_CHEST);
		store.setItem(0, namedLogs(1));
		return new Special(jack, store, axe);
	}

	private static void assertHauled(GameTestHelper helper, Special s, IronGolem golem) {
		int plainInStore = count(s.store(), st -> st.is(Items.OAK_LOG) && st.getComponentsPatch().isEmpty());
		int namedInStore = count(s.store(), QaBuilderLeftovers1102GameTests::isNamedLog);
		int namedAtJack = count(s.jack(), QaBuilderLeftovers1102GameTests::isNamedLog);
		int plainAtJacks = 0;
		for (BlockPos c : H_CHESTS) {
			plainAtJacks += count(helper.getBlockEntity(c), st -> st.is(Items.OAK_LOG) && st.getComponentsPatch().isEmpty());
		}
		helper.assertTrue(plainInStore == 192 && plainAtJacks == 0 && HaulerGolems.load(golem).isEmpty(),
			"plain logs: " + plainInStore + " of 192 in the Storehouse chest, " + plainAtJacks + " with the lumberjacks; the golem carries " + HaulerGolems.load(golem));
		helper.assertTrue(namedInStore == 1, "the Storehouse chest's named log: " + stacks(s.store()));
		helper.assertTrue(namedAtJack == 64, "the lumberjack's chest has " + namedAtJack + " of 64 named logs: " + stacks(s.jack()));
		helper.assertTrue(count(s.jack(), st -> ItemStack.matches(st, s.axe())) == 1 && s.store().countItem(Items.IRON_AXE) == 0,
			"the named, enchanted, worn axe isn't in the lumberjack's chest as it was: " + stacks(s.jack()) + "; Storehouse " + stacks(s.store()));
		int logs = 0;
		for (BlockPos c : H_CHESTS) {
			logs += ((Container) helper.getBlockEntity(c)).countItem(Items.OAK_LOG);
		}
		logs += s.store().countItem(Items.OAK_LOG);
		helper.assertTrue(logs == 192 + 64 + 1, "logs made or lost: " + logs + " of 257 in the chests");
	}

	private static ItemStack namedLogs(int n) {
		ItemStack s = new ItemStack(Items.OAK_LOG, n);
		s.set(DataComponents.CUSTOM_NAME, Component.literal("Grandpa's Oak"));
		return s;
	}

	private static boolean isNamedLog(ItemStack s) {
		return s.is(Items.OAK_LOG) && "Grandpa's Oak".equals(s.getHoverName().getString()) && s.has(DataComponents.CUSTOM_NAME);
	}

	private static ItemStack axe(GameTestHelper helper) {
		ItemStack axe = new ItemStack(Items.IRON_AXE);
		axe.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
		axe.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.EFFICIENCY), 3);
		axe.setDamageValue(100);
		return axe;
	}

	private static int count(Container c, Predicate<ItemStack> test) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty() && test.test(s)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static String stacks(Container c) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty()) {
				out.append(s.getCount()).append(' ').append(s.getHoverName().getString()).append(s.getComponentsPatch().isEmpty() ? "" : "*").append(", ");
			}
		}
		return out.toString();
	}
}
