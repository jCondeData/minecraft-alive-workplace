package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c, 2026-11-03) for B346, B347, B357 and B358, written from 34.20, 34.21 and 33.9: a retired elder refuses
 * every kind of job item (a vanilla job's at a vanilla block, ours at ours, the Builder's blank blueprint at a
 * Blueprint Table, and after a reload) and leaves each block free; the successor is the elder's own grown child by
 * id whoever was renamed, the mother as well as the father, never a namesake's child or a baby; the Colony Charter
 * at the hall from the off hand with the main hand empty or holding something else, plain and sneaking; the Colonies
 * tab's settler and day counts at 0, 1, 2 and 3 and past the wait.
 */
public class QaEldersColonies1103cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	// --- B346: a retired elder takes no job, from any item ------------------------------------------------------------

	/**
	 * Bram, a retired Farmer, sneak-clicked by four blocks with the item that picks their job: wheat at a composter (his
	 * own old trade, vanilla), planks at a crafting table (our Carpenter), an emerald at a Shop Counter (ours, at our
	 * block), a Blank Blueprint at a Blueprint Table (the Builder). Each time he stays jobless, holds no block, is told
	 * he has retired and never that he took the job; each block keeps its free place, and a young jobless villager takes
	 * that job there with the same item.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaEC1103RetiredKinds"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaEC1103RetiredKinds")
	public void aRetiredElderRefusesEveryKindOfJobItem(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		village(helper);
		record Kind(BlockPos block, Block type, ItemStack item, VillagerProfession job) {
		}
		List<Kind> kinds = List.of(
			new Kind(new BlockPos(2, 2, 2), Blocks.COMPOSTER, new ItemStack(Items.WHEAT), VillagerProfession.FARMER),
			new Kind(new BlockPos(2, 2, 19), Blocks.CRAFTING_TABLE, new ItemStack(Items.OAK_PLANKS), ModVillagers.CARPENTER),
			new Kind(new BlockPos(19, 2, 2), ModBlocks.SHOP_COUNTER, new ItemStack(Items.EMERALD), ModVillagers.SHOPKEEPER),
			new Kind(new BlockPos(19, 2, 19), ModBlocks.BLUEPRINT_TABLE, new ItemStack(ModItems.BLANK_BLUEPRINT), ModVillagers.BUILDER));
		for (Kind k : kinds) {
			helper.setBlock(k.block(), k.type());
		}
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			ready(helper);
			long today = Chronicle.day(level);
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = listener(helper, seen);
			Villager bram = villager(helper, new BlockPos(11, 2, 6), "Bram", VillagerProfession.NONE, 1);
			LifeStages.setAdultSince(bram, today - 1240);
			ModAttachments.RETIRED.set(bram, new Retirement.Retired(today - 5, "minecraft:farmer"));
			List<String> wrong = new ArrayList<>();
			player.setShiftKeyDown(true);
			for (Kind k : kinds) {
				BlockPos abs = helper.absolutePos(k.block());
				int free = level.getPoiManager().getFreeTickets(abs);
				BlockPos by = helper.absolutePos(k.block().getZ() < 11 ? k.block().south() : k.block().north());
				bram.moveTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5, 0, 0);
				for (InteractionHand hand : InteractionHand.values()) {
					seen.clear();
					player.setItemInHand(InteractionHand.MAIN_HAND, hand == InteractionHand.MAIN_HAND ? k.item().copy() : ItemStack.EMPTY);
					player.setItemInHand(InteractionHand.OFF_HAND, hand == InteractionHand.OFF_HAND ? k.item().copy() : ItemStack.EMPTY);
					click(player, bram);
					String what = k.item().getHoverName().getString() + " in the " + hand;
					if (bram.getVillagerData().getProfession() != VillagerProfession.NONE) {
						wrong.add(what + ": he took " + bram.getVillagerData().getProfession());
						bram.setVillagerData(bram.getVillagerData().setProfession(VillagerProfession.NONE));
					}
					if (bram.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent()) {
						wrong.add(what + ": he holds " + bram.getBrain().getMemory(MemoryModuleType.JOB_SITE).get());
						bram.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
					}
					if (seen.stream().anyMatch(c -> key(c).equals("message.aliveworkplace.job.chosen"))) {
						wrong.add(what + ": told he took the job " + texts(seen));
					}
					if (seen.stream().noneMatch(c -> c.getString().equals("Bram has retired and takes no job now: they mentor the young instead."))) {
						wrong.add(what + ": not told he has retired " + texts(seen));
					}
				}
				int after = level.getPoiManager().getFreeTickets(abs);
				if (after != free || free < 1) {
					wrong.add(k.type().getName().getString() + "'s free places " + free + " -> " + after);
				}
				// The block is still free for a young jobless villager, with the same item.
				BlockPos side = k.block().getX() < 11 ? k.block().east() : k.block().west();
				Villager young = villager(helper, side, "Young" + k.job().toString(), VillagerProfession.NONE, 1);
				LifeStages.setAdultSince(young, today - 10);
				player.setItemInHand(InteractionHand.MAIN_HAND, k.item().copy());
				player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
				seen.clear();
				click(player, young);
				if (young.getVillagerData().getProfession() != k.job()
					|| young.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(abs::equals).isEmpty()) {
					wrong.add("a young villager didn't take the " + k.job() + " job at the " + k.type().getName().getString() + ": "
						+ young.getVillagerData().getProfession() + ", told " + texts(seen));
				}
			}
			player.setShiftKeyDown(false);
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
			helper.succeed();
		});
	}

	/** A retired elder saved and loaded again (the server restarted) still refuses a job item, and the lectern stays free. */
	//$ gametest_ticks_batch AREA '60' '"qaEC1103RetiredReload"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEC1103RetiredReload")
	public void aReloadedRetiredElderStillRefusesAJobItem(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		village(helper);
		BlockPos lectern = new BlockPos(4, 2, 4);
		helper.setBlock(lectern, Blocks.LECTERN);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			ready(helper);
			long today = Chronicle.day(level);
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = listener(helper, seen);
			Villager bram = villager(helper, lectern.south(), "Bram", VillagerProfession.NONE, 1);
			LifeStages.setAdultSince(bram, today - 1240);
			ModAttachments.RETIRED.set(bram, new Retirement.Retired(today - 5, "minecraft:librarian", 4));
			Villager again = reload(helper, bram);
			helper.assertTrue(Retirement.isRetired(again), "the reload forgot he retired");
			player.setShiftKeyDown(true);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK));
			click(player, again);
			player.setShiftKeyDown(false);
			helper.assertTrue(again.getVillagerData().getProfession() == VillagerProfession.NONE && again.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
				"the reloaded retired elder took a job: " + again.getVillagerData().getProfession() + ", told " + texts(seen));
			helper.assertTrue(seen.stream().anyMatch(c -> key(c).equals("message.aliveworkplace.job.retired")), "not told he has retired: " + texts(seen));
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(lectern)) == 1, "the lectern's place was taken: "
				+ level.getPoiManager().getFreeTickets(helper.absolutePos(lectern)));
			helper.succeed();
		});
	}

	// --- B347: their own grown child, known by id ---------------------------------------------------------------------

	/** The child renamed with a Name Tag (the elder not): still Bram's son by id, chosen over a stranger who comes first. */
	//$ gametest_ticks_batch AREA '60' '"qaEC1103ChildRenamed"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEC1103ChildRenamed")
	public void theChildRenamedIsStillTheSuccessor(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(3, () -> {
			Family f = family(helper, 0x11030001L, false);
			nameTag(helper, f.player(), f.son(), "Thomas");
			Retirement.round(helper.getLevel(), helper.absolutePos(HALL), List.of(f.bram(), f.stranger(), f.son()));
			assertShadows(helper, f.son(), f.bram(), f.stranger());
			helper.succeed();
		});
	}

	/** Both renamed (Bram "Grandpa", Tom "Thomas"): the son is still known by id and takes over. */
	//$ gametest_ticks_batch AREA '60' '"qaEC1103BothRenamed"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEC1103BothRenamed")
	public void bothRenamedTheSonStillTakesOver(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(3, () -> {
			Family f = family(helper, 0x11030002L, false);
			nameTag(helper, f.player(), f.bram(), "Grandpa");
			nameTag(helper, f.player(), f.son(), "Thomas");
			Retirement.round(helper.getLevel(), helper.absolutePos(HALL), List.of(f.bram(), f.stranger(), f.son()));
			assertShadows(helper, f.son(), f.bram(), f.stranger());
			helper.succeed();
		});
	}

	/** The elder is the child's MOTHER (mother_id), renamed after the birth: the daughter is found by the mother's id too. */
	//$ gametest_ticks_batch AREA '60' '"qaEC1103MotherId"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEC1103MotherId")
	public void theElderAsMotherIsKnownByHerId(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(3, () -> {
			Family f = family(helper, 0x11030003L, true);
			nameTag(helper, f.player(), f.bram(), "Granny");
			Retirement.round(helper.getLevel(), helper.absolutePos(HALL), List.of(f.bram(), f.stranger(), f.son()));
			assertShadows(helper, f.son(), f.bram(), f.stranger());
			helper.succeed();
		});
	}

	/**
	 * A namesake's child: Ned's record names his father "Bram" but by another Bram's id (a dead one), and Ned comes first
	 * by UUID; the real son Tom (by id) takes over, never Ned, though the names match.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaEC1103Namesake"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEC1103Namesake")
	public void aNamesakesChildIsNotTheElders(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(3, () -> {
			Family f = family(helper, 0x11030004L, false);
			Villager ned = withId(helper, new BlockPos(13, 2, 6), "Ned", new UUID(2L, 0x11030004L));
			LifeStages.setAdultSince(ned, Chronicle.day(helper.getLevel()) - 20);
			ModAttachments.PARENTS.set(ned, new Families.Parents(Component.literal("Ida"), Component.literal("Bram"), "", "minecraft:farmer", true,
				false, false, UUID.nameUUIDFromBytes("ida".getBytes()), UUID.nameUUIDFromBytes("the-other-bram".getBytes())));
			helper.assertTrue(f.stranger().getUUID().compareTo(ned.getUUID()) < 0 && ned.getUUID().compareTo(f.son().getUUID()) < 0, "UUID order");
			Retirement.round(helper.getLevel(), helper.absolutePos(HALL), List.of(f.bram(), f.stranger(), ned, f.son()));
			helper.assertTrue(Retirement.shadow(ned) == null, "a namesake's son (other father id) was taken for Bram's: " + Retirement.shadow(ned));
			assertShadows(helper, f.son(), f.bram(), f.stranger());
			helper.succeed();
		});
	}

	/** An old record without ids still finds the child by the parent's name (Bram not renamed), over a stranger first by UUID. */
	//$ gametest_ticks_batch AREA '60' '"qaEC1103OldRecord"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEC1103OldRecord")
	public void anOldRecordWithoutIdsFindsTheChildByName(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(3, () -> {
			Family f = family(helper, 0x11030005L, false);
			ModAttachments.PARENTS.set(f.son(), new Families.Parents(Component.literal("Ann"), Component.literal("Bram"), "", "minecraft:farmer", true,
				false, false, null, null));
			Retirement.round(helper.getLevel(), helper.absolutePos(HALL), List.of(f.bram(), f.stranger(), f.son()));
			assertShadows(helper, f.son(), f.bram(), f.stranger());
			helper.succeed();
		});
	}

	/**
	 * Two grown jobless children (Tom and Sam, both by id) and a stranger first by UUID: one of the children takes over,
	 * never the stranger, and the same one whatever order the village lists them in.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaEC1103TwoChildren"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEC1103TwoChildren")
	public void ofTwoGrownChildrenOneTakesOverNeverTheStranger(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			Family f = family(helper, 0x11030006L, false);
			Villager sam = withId(helper, new BlockPos(13, 2, 6), "Sam", new UUID(2L, 0x11030006L));
			LifeStages.setAdultSince(sam, Chronicle.day(level) - 25);
			ModAttachments.PARENTS.set(sam, new Families.Parents(Component.literal("Ann"), Component.literal("Bram"), "", "minecraft:farmer", true,
				false, false, UUID.nameUUIDFromBytes("ann".getBytes()), f.bram().getUUID()));
			nameTag(helper, f.player(), f.bram(), "Grandpa");
			BlockPos hall = helper.absolutePos(HALL);
			long day = Chronicle.day(level);
			Villager one = Retirement.successor(hall, f.bram(), List.of(f.bram(), f.stranger(), sam, f.son()), day);
			Villager two = Retirement.successor(hall, f.bram(), List.of(f.son(), sam, f.stranger(), f.bram()), day);
			helper.assertTrue(one == sam || one == f.son(), "not one of his children: " + name(one));
			helper.assertTrue(one == two, "a different child by the list's order: " + name(one) + " then " + name(two));
			Retirement.round(level, hall, List.of(f.bram(), f.stranger(), sam, f.son()));
			helper.assertTrue(Retirement.shadow(f.stranger()) == null, "the stranger shadows Bram");
			int shadows = (Retirement.shadow(sam) != null ? 1 : 0) + (Retirement.shadow(f.son()) != null ? 1 : 0);
			helper.assertTrue(shadows == 1, "children shadowing Bram: " + shadows);
			helper.succeed();
		});
	}

	/** Bram's own child is still a baby: never the successor; the grown stranger takes over. With only the baby, nobody, and the hall says he waits. */
	//$ gametest_ticks_batch AREA '60' '"qaEC1103BabyChild"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEC1103BabyChild")
	public void aBabyChildNeverTakesOver(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			Family f = family(helper, 0x11030007L, false);
			f.son().setAge(-24000);
			ModAttachments.PARENTS.set(f.son(), new Families.Parents(Component.literal("Ann"), Component.literal("Bram"), "", "minecraft:farmer", false,
				false, false, UUID.nameUUIDFromBytes("ann".getBytes()), f.bram().getUUID()));
			BlockPos hall = helper.absolutePos(HALL);
			long day = Chronicle.day(level);
			helper.assertTrue(f.son().isBaby(), "Tom isn't a baby");
			helper.assertTrue(Retirement.successor(hall, f.bram(), List.of(f.bram(), f.son()), day) == null, "a baby would take over: "
				+ name(Retirement.successor(hall, f.bram(), List.of(f.bram(), f.son()), day)));
			Retirement.round(level, hall, List.of(f.bram(), f.son()));
			helper.assertTrue(Retirement.shadow(f.son()) == null, "the baby shadows Bram");
			helper.assertTrue(Boolean.TRUE.equals(ModAttachments.RETIRE_WAITING.get(f.bram())), "with only a baby, Bram isn't waiting for someone to take over");
			Retirement.round(level, hall, List.of(f.bram(), f.stranger(), f.son()));
			helper.assertTrue(Retirement.shadow(f.son()) == null, "the baby shadows Bram");
			assertShadows(helper, f.stranger(), f.bram(), null);
			helper.succeed();
		});
	}

	/** Bram and his son saved and loaded again (a restart), then Bram renamed: the son's record keeps his id and he takes over. */
	//$ gametest_ticks_batch AREA '60' '"qaEC1103ChildReload"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEC1103ChildReload")
	public void afterAReloadTheChildIsStillKnownById(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(3, () -> {
			Family f = family(helper, 0x11030008L, false);
			Villager bram = reload(helper, f.bram());
			Villager son = reload(helper, f.son());
			helper.assertTrue(ModAttachments.PARENTS.get(son) != null && bram.getUUID().equals(ModAttachments.PARENTS.get(son).fatherId()),
				"the reload lost the father's id: " + ModAttachments.PARENTS.get(son));
			nameTag(helper, f.player(), bram, "Grandpa");
			Retirement.round(helper.getLevel(), helper.absolutePos(HALL), List.of(bram, f.stranger(), son));
			assertShadows(helper, son, bram, f.stranger());
			helper.succeed();
		});
	}

	// --- B357: the charter at the hall from either hand, plain and sneaking ------------------------------------------

	/** Charter in the off hand, main hand empty, sneaking: the settlers are sent and the charter used. */
	//$ gametest_ticks_batch AREA '100' '"qaEC1103OffSneak"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaEC1103OffSneak")
	public void theOffHandCharterOnASneakClickSendsTheSettlers(GameTestHelper helper) {
		charterClick(helper, ItemStack.EMPTY, true);
	}

	/** Charter in the off hand, a stick in the main hand, plain click: the settlers are sent. */
	//$ gametest_ticks_batch AREA '100' '"qaEC1103OffStick"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaEC1103OffStick")
	public void theOffHandCharterBehindAStickSendsTheSettlers(GameTestHelper helper) {
		charterClick(helper, new ItemStack(Items.STICK), false);
	}

	/** Charter in the off hand, a stick in the main hand, sneaking: the settlers are sent. */
	//$ gametest_ticks_batch AREA '100' '"qaEC1103OffStickSneak"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaEC1103OffStickSneak")
	public void theOffHandCharterBehindAStickOnASneakClickSendsTheSettlers(GameTestHelper helper) {
		charterClick(helper, new ItemStack(Items.STICK), true);
	}

	/** Charter in the main hand and a torch in the off hand, sneaking: sent once, and the torch stays. */
	//$ gametest_ticks_batch AREA '100' '"qaEC1103MainSneakTorch"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaEC1103MainSneakTorch")
	public void theMainHandCharterWithATorchOffHandOnASneakClickSendsTheSettlers(GameTestHelper helper) {
		QaRealmGameTests.Village v = QaRealmGameTests.village(helper, -1);
		QaRealmGameTests.plainVillage(helper);
		QaRealmGameTests.storehouse(helper, QaRealmGameTests.allSupplies());
		v.owner().setItemInHand(InteractionHand.MAIN_HAND, QaRealmGameTests.charter(v));
		v.owner().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH, 3));
		v.owner().setShiftKeyDown(true);
		QaStageFourGameTests.press(helper, v.owner(), QaRealmGameTests.HALL);
		v.owner().setShiftKeyDown(false);
		helper.assertTrue(v.order() != null, "sent nobody (saw " + QaStageFourGameTests.keys(v.seen()) + ")");
		helper.assertTrue(!v.owner().getMainHandItem().is(ModItems.COLONY_CHARTER), "the charter stayed in hand");
		helper.assertTrue(v.owner().getOffhandItem().is(Items.TORCH) && v.owner().getOffhandItem().getCount() == 3, "the torch was used: " + v.owner().getOffhandItem());
		helper.succeed();
	}

	private static void charterClick(GameTestHelper helper, ItemStack main, boolean sneak) {
		QaRealmGameTests.Village v = QaRealmGameTests.village(helper, -1);
		QaRealmGameTests.plainVillage(helper);
		QaRealmGameTests.storehouse(helper, QaRealmGameTests.allSupplies());
		v.owner().setItemInHand(InteractionHand.MAIN_HAND, main.copy());
		v.owner().setItemInHand(InteractionHand.OFF_HAND, QaRealmGameTests.charter(v));
		v.owner().setShiftKeyDown(sneak);
		QaStageFourGameTests.press(helper, v.owner(), QaRealmGameTests.HALL);
		v.owner().setShiftKeyDown(false);
		String how = (main.isEmpty() ? "empty main hand" : main.getHoverName().getString() + " in the main hand") + (sneak ? ", sneaking" : "");
		helper.assertTrue(v.order() != null, how + ": the off hand's charter sent nobody (saw " + QaStageFourGameTests.keys(v.seen()) + ", menu "
			+ v.owner().containerMenu.getClass().getSimpleName() + ")");
		helper.assertTrue(v.owner().getOffhandItem().isEmpty(), how + ": the charter stayed in the off hand");
		helper.assertTrue(ItemStack.matches(v.owner().getMainHandItem(), main), how + ": the main hand changed: " + v.owner().getMainHandItem());
		helper.succeed();
	}

	// --- B358: the Colonies tab's counts --------------------------------------------------------------------------------

	/** On the road with 0, 1 and 2 settlers: "0 settlers", "1 settler", "2 settlers" exactly, and no leave line. */
	//$ gametest_ticks_batch AREA '60' '"qaEC1103TabSettlers"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEC1103TabSettlers")
	public void theTabCountsSettlersOnTheRoad(GameTestHelper helper) {
		QaRealmGameTests.Village v = QaRealmGameTests.village(helper, -1);
		long now = v.level().getGameTime();
		CompoundTag bram = QaRealmGameTests.saved(helper, "Bram", ModVillagers.BUILDER, 1);
		CompoundTag tomas = QaRealmGameTests.saved(helper, "Tomas", VillagerProfession.NONE, 1);
		List<String> wrong = new ArrayList<>();
		String[] expected = {"0 settlers", "1 settler", "2 settlers"};
		List<List<CompoundTag>> groups = List.of(List.of(), List.of(bram), List.of(bram, tomas));
		for (int n = 0; n < 3; n++) {
			RealmData.Order road = new RealmData.Order(v.mother(), v.hall().offset(400, 0, 0), Optional.of(Component.literal("Newbrook")), RealmData.ON_ROAD,
				now - 10, now + 2400, Colonies.COST * 100, now - 100, List.of(), Optional.of(bram.getUUID("UUID")), Map.of(), groups.get(n));
			List<String> lines = Settlers.lines(v.level(), road).stream().map(Component::getString).toList();
			if (lines.isEmpty() || !lines.get(0).equals(expected[n])) {
				wrong.add(n + " on the road: " + lines);
			}
			if (lines.stream().anyMatch(l -> l.startsWith("They leave"))) {
				wrong.add(n + " on the road, a leave line: " + lines);
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * Waiting for supplies (72,000 ticks): ordered now "in 3 days", after a day "in 2 days", one tick short of two days
	 * still "in 2 days", at exactly two days and past the wait "in 1 day", never "in 0 days" or "1 days".
	 */
	//$ gametest_ticks_batch AREA '60' '"qaEC1103TabDays"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEC1103TabDays")
	public void theTabCountsTheDaysLeftToWait(GameTestHelper helper) {
		QaRealmGameTests.Village v = QaRealmGameTests.village(helper, -1);
		Settlers.WAIT_TICKS = 72000; // (restored by village's Leftovers.after)
		long now = v.level().getGameTime();
		long[] ago = {0, 24000, 47999, 48000, 71999, 90000};
		String[] says = {"in 3 days", "in 2 days", "in 2 days", "in 1 day", "in 1 day", "in 1 day"};
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < ago.length; i++) {
			RealmData.Order waiting = new RealmData.Order(v.mother(), v.hall().offset(400, 0, 0), Optional.of(Component.literal("Newbrook")), RealmData.GATHERING,
				0, 0, Colonies.COST * 100, now - ago[i], List.of(), Optional.empty(), Map.of(), List.of());
			List<String> lines = Settlers.lines(v.level(), waiting).stream().map(Component::getString).toList();
			if (!lines.contains("They leave with what there is " + says[i])) {
				wrong.add("ordered " + ago[i] + " ticks ago: " + lines);
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// --- Helpers ----------------------------------------------------------------------------------------------------

	/** Bram, a Farmer elder; Eli, a jobless stranger first by UUID; Tom, Bram's grown son by id (or daughter, Bram the mother). */
	private record Family(Villager bram, Villager stranger, Villager son, ServerPlayer player) {
	}

	private static Family family(GameTestHelper helper, long salt, boolean asMother) {
		ServerLevel level = helper.getLevel();
		ready(helper);
		long today = Chronicle.day(level);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FARMER, 3);
		Villager stranger = withId(helper, new BlockPos(7, 2, 6), "Eli", new UUID(1L, salt));
		Villager son = withId(helper, new BlockPos(9, 2, 6), "Tom", new UUID(3L, salt));
		Component other = Component.literal("Ann");
		UUID otherId = UUID.nameUUIDFromBytes(("ann" + salt).getBytes());
		ModAttachments.PARENTS.set(son, asMother
			? new Families.Parents(Component.literal("Bram"), other, "minecraft:farmer", "", true, false, false, bram.getUUID(), otherId)
			: new Families.Parents(other, Component.literal("Bram"), "", "minecraft:farmer", true, false, false, otherId, bram.getUUID()));
		LifeStages.setAdultSince(bram, today - 1230);
		LifeStages.setAdultSince(stranger, today - 20);
		LifeStages.setAdultSince(son, today - 20);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return new Family(bram, stranger, son, player);
	}

	/** {@code who} renamed the way a player does it: a named Name Tag used on them. */
	private static void nameTag(GameTestHelper helper, ServerPlayer player, Villager who, String name) {
		ItemStack tag = new ItemStack(Items.NAME_TAG);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		player.setItemInHand(InteractionHand.MAIN_HAND, tag);
		tag.interactLivingEntity(player, who, InteractionHand.MAIN_HAND);
		helper.assertTrue(who.getName().getString().equals(name), "the Name Tag didn't rename: " + who.getName().getString());
	}

	private static void assertShadows(GameTestHelper helper, Villager successor, Villager elder, Villager notThem) {
		Retirement.Shadow s = Retirement.shadow(successor);
		helper.assertTrue(s != null && elder.getUUID().equals(s.elder()), name(successor) + " doesn't shadow " + name(elder) + ": " + s
			+ (notThem == null ? "" : ", " + name(notThem) + " " + Retirement.shadow(notThem)));
		if (notThem != null) {
			helper.assertTrue(Retirement.shadow(notThem) == null, name(notThem) + " shadows too: " + Retirement.shadow(notThem));
		}
	}

	private static String name(Villager v) {
		return v == null ? "nobody" : v.getName().getString();
	}

	/** {@code v} saved and loaded again as a new entity (its UUID kept), the old one gone. */
	private static Villager reload(GameTestHelper helper, Villager v) {
		ServerLevel level = helper.getLevel();
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		v.discard();
		Villager again = EntityType.VILLAGER.create(level);
		again.load(tag);
		again.setNoAi(true);
		helper.assertTrue(level.addFreshEntity(again), "the reloaded villager wasn't added");
		return again;
	}

	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Retirement.ENABLED = true;
			VillageNeeds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		if (job != VillagerProfession.NONE) {
			v.setVillagerXp(1);
		}
		return v;
	}

	/** A jobless villager with a chosen UUID, so the order among candidates is known. */
	private static Villager withId(GameTestHelper helper, BlockPos pos, String name, UUID id) {
		ServerLevel level = helper.getLevel();
		Villager v = EntityType.VILLAGER.create(level);
		BlockPos at = helper.absolutePos(pos);
		v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		v.setUUID(id);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		helper.assertTrue(level.addFreshEntity(v), "the villager " + name + " wasn't added");
		return v;
	}

	/** A right-click as the game sends it: the main hand's turn, then the off hand's if the first passed. */
	private static void click(ServerPlayer player, Villager villager) {
		net.minecraft.world.InteractionResult r = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
		if (r == net.minecraft.world.InteractionResult.PASS) {
			UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.OFF_HAND, villager, null);
		}
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		return hall;
	}

	/** A player who keeps every chat and action-bar line they're shown. */
	private static ServerPlayer listener(GameTestHelper helper, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qa-ec-listener"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	private static String key(Component c) {
		if (c.getContents() instanceof TranslatableContents t) {
			return t.getKey();
		}
		return c.getSiblings().isEmpty() ? "" : key(c.getSiblings().get(0));
	}
}
