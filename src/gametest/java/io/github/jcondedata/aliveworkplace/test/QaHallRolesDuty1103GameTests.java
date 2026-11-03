package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.guard.GuardCombat;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Gifts;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1103-1554): the Village Hall's People table, Job ▾ (40.3) and No job, used on villagers who hold a role
 * of their own, every change through the hall's real menu clicks: a guard on duty (his patrol route, his doubled health,
 * his count on the Defense side of the hall, a fight under way, a save and reload), a shopkeeper (the counter, its price
 * list and owner, the stock and the takings in its chest, the shop run again by the next villager), and a friend with an
 * open personal request (the hearts kept, the "next level" request still finishable after a retraining). Written from
 * the items' Done when, docs/wiki/village-hall.md, docs/guide/guard.md and shopkeeper.md, and the lang lines.
 */
public class QaHallRolesDuty1103GameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos GRINDSTONE = new BlockPos(8, 2, 20);
	private static final BlockPos STONECUTTER = new BlockPos(22, 2, 22);
	private static final BlockPos LECTERN = new BlockPos(6, 2, 6);
	private static final BlockPos COUNTER = new BlockPos(10, 2, 20);
	private static final BlockPos CHEST = new BlockPos(10, 2, 22);

	// --- helpers ------------------------------------------------------------------------------------------------------

	private static int emeralds(ServerPlayer player) {
		return player.getInventory().countItem(Items.EMERALD);
	}

	private static int free(GameTestHelper helper, BlockPos relative) {
		return helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(relative));
	}

	private static CompoundTag row(GameTestHelper helper, ChoiceMenu menu, Villager v) {
		return HallPeople.row(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, v)));
	}

	/** A guard of {@code level} at the grindstone, with an iron sword and chestplate, a patrol route and his guard's health. */
	private static Villager guard(GameTestHelper helper, String name, int level, boolean ai) {
		helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE);
		Villager v = QaHallScreenGameTests.villager(helper, name, GRINDSTONE.offset(1, 0, 1));
		v.setNoAi(!ai);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(GRINDSTONE), PoiTypes.WEAPONSMITH, ModVillagers.GUARD);
		v.setVillagerData(v.getVillagerData().setLevel(level));
		v.setVillagerXp(level == 1 ? 5 : 70 * level);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		ModAttachments.PATROL_ROUTE.set(v, List.of(helper.absolutePos(new BlockPos(5, 2, 5)), helper.absolutePos(new BlockPos(25, 2, 25))));
		Guards.updateHealth(v);
		return v;
	}

	/** How many iron swords are still about: in {@code v}'s hands or dropped in the test area. */
	private static int swords(GameTestHelper helper, Villager v) {
		int n = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (v.getItemBySlot(slot).is(Items.IRON_SWORD)) {
				n++;
			}
		}
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(40);
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (item.getItem().is(Items.IRON_SWORD)) {
				n++;
			}
		}
		return n;
	}

	private static Villager reloaded(GameTestHelper helper, Villager v) {
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		v.discard();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(tag);
		helper.getLevel().addFreshEntity(copy);
		return copy;
	}

	private static int guardsCounted(GameTestHelper helper) {
		return VillageNeeds.count(helper.getLevel(), helper.absolutePos(HALL)).guards();
	}

	/** A shop owned by "Frank": 16 cobblestone for an emerald, a chest of 64 cobblestone and 7 emeralds of takings. */
	private static ShopCounterBlockEntity shop(GameTestHelper helper, UUID owner) {
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(COUNTER);
		counter.setOwner(owner, "Frank");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 1));
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		chest.setItem(1, new ItemStack(Items.EMERALD, 7));
		return counter;
	}

	private static Villager keeper(GameTestHelper helper, String name, int level) {
		Villager v = QaHallScreenGameTests.villager(helper, name, COUNTER.offset(1, 0, 0));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(COUNTER), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		v.setVillagerData(v.getVillagerData().setLevel(level));
		v.setVillagerXp(level == 1 ? 5 : 70 * level);
		return v;
	}

	private static int chestCount(GameTestHelper helper, net.minecraft.world.item.Item item) {
		Container chest = helper.getBlockEntity(CHEST);
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(item)) {
				n += chest.getItem(i).getCount();
			}
		}
		return n;
	}

	private static boolean sellsCobble(Villager v) {
		for (MerchantOffer offer : v.getOffers()) {
			if (offer.getResult().is(Items.COBBLESTONE)) {
				return true;
			}
		}
		return false;
	}

	/** Two clicks of a job on the open list: the offer, then the pay. */
	private static void retrain(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager v, VillagerProfession job) {
		QaHallScreenGameTests.jobDropdown(helper, player, menu, v);
		QaHallScreenGameTests.pickJob(helper, player, menu, job);
		QaHallScreenGameTests.pickJob(helper, player, menu, job);
	}

	/** Requests and friendship on for this test, the hall's stories forgotten before and after. */
	private static void requestsOn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		boolean requests = PersonalRequests.ENABLED;
		boolean friendship = Friendship.ENABLED;
		PersonalRequests.ENABLED = true;
		Friendship.ENABLED = true;
		PersonalRequests.forget();
		Stories.Data.get(level).remove(hall);
		Leftovers.after(helper, () -> {
			PersonalRequests.ENABLED = requests;
			Friendship.ENABLED = friendship;
			PersonalRequests.forget();
			Stories.Data.get(level).remove(hall);
		});
	}

	/** {@code villager} asks {@code player} for the next level (personal/level_up) and the player takes it on. */
	static Quest levelUpAccepted(GameTestHelper helper, Villager villager, ServerPlayer player) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		QuestFiles.QuestFile file = QuestFiles.get(PersonalRequestGameTests.file("level_up")).orElse(null);
		helper.assertTrue(file != null, "no quest file personal/level_up");
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		Quest quest = PersonalRequests.resolve(level, hall, entity, VillageHalls.census(level, hall), file, villager, player, RandomSource.create(7));
		helper.assertTrue(quest != null, "setup: " + villager.getName().getString() + " has no level_up to ask (" + Friendship.points(villager, player.getUUID())
			+ " points, " + villager.getVillagerData() + ")");
		PersonalRequests.propose(level, hall, villager, player, quest, io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level));
		PersonalRequestGameTests.command(player, "workplace quest accept " + quest.id);
		helper.assertTrue(PersonalRequests.of(villager) == quest, "setup: level_up wasn't accepted");
		return quest;
	}

	// --- a guard on duty ----------------------------------------------------------------------------------------------

	/**
	 * A guard on duty (sword, chestplate, a patrol route, 40 health) retrained as a Mason from his row: his own list
	 * says "Guard · their job now", the Apprentice price (10) is paid once, and the old role is over: no longer a guard,
	 * a villager's 20 health again, the grindstone free for the next guard, the stonecutter his, the hall counting no
	 * guard, and the sword he carried not destroyed (still held or dropped).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdGuardMason"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdGuardMason")
	public void aGuardRetrainedAsAMasonIsOffDuty(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager bram = guard(helper, "Bram", 2, false);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 10);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(Guards.isGuard(bram) && bram.getMaxHealth() == 40f && guardsCounted(helper) == 1,
				"setup: guard " + Guards.isGuard(bram) + ", health " + bram.getMaxHealth() + ", counted " + guardsCounted(helper));
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			helper.assertTrue(row(helper, menu, bram).getString("kind").equals("guard") && row(helper, menu, bram).getString("job_action").equals("retrain"),
				"his row: " + row(helper, menu, bram));
			QaHallScreenGameTests.jobDropdown(helper, player, menu, bram);
			List<ItemStack> lines = HallJobs.lines(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, bram)));
			ItemStack own = QaHallScreenGameTests.jobLine(helper, lines, ModVillagers.GUARD);
			helper.assertTrue(own.getHoverName().getString().equals("Guard · their job now"), "his own line: " + own.getHoverName().getString());
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(Guards.isGuard(bram) && emeralds(player) == 10, "the first click changed something");
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.MASON && bram.getVillagerData().getLevel() == 1,
				"not retrained: " + bram.getVillagerData() + "; told " + seen);
			helper.assertTrue(emeralds(player) == 0, "paid " + (10 - emeralds(player)) + ", not 10");
			helper.assertTrue(!Guards.isGuard(bram), "still a guard");
			helper.assertTrue(bram.getMaxHealth() == 20f && bram.getHealth() <= 20f, "a Mason with a guard's health: " + bram.getHealth() + "/" + bram.getMaxHealth());
			helper.assertTrue(free(helper, GRINDSTONE) == 1 && free(helper, STONECUTTER) == 0,
				"blocks: grindstone free " + free(helper, GRINDSTONE) + ", stonecutter free " + free(helper, STONECUTTER));
			helper.assertTrue(guardsCounted(helper) == 0, "the hall still counts " + guardsCounted(helper) + " guard");
			helper.assertTrue(swords(helper, bram) >= 1, "the guard's sword vanished");
			helper.assertTrue(seen.contains("Bram gave up the Guard job and was retrained as a Mason at the Stonecutter, for 10 emeralds."), "told: " + seen);
			helper.succeed();
		});
	}

	/**
	 * A guard given No job, then saved and loaded: free, jobless, a villager's health, the grindstone let go, and after
	 * the reload still jobless and no guard (nothing in the save brings the guard back); a jobless neighbour's Job ▾
	 * then offers the grindstone's Guard job as free.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdGuardNoJob"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdGuardNoJob")
	public void aGuardGivenNoJobStaysOffDutyAfterAReload(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		Villager bram = guard(helper, "Bram", 3, false);
		Villager ivy = QaHallScreenGameTests.villager(helper, "Ivy", new BlockPos(12, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 20);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			retrain(helper, player, menu, bram, VillagerProfession.NONE);
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE && QaHallScreenGameTests.site(bram).isEmpty(),
				"still working: " + bram.getVillagerData());
			helper.assertTrue(emeralds(player) == 20, "No job cost " + (20 - emeralds(player)));
			helper.assertTrue(seen.contains("Bram gave up the Guard job and has no job now."), "told: " + seen);
			helper.assertTrue(!Guards.isGuard(bram) && bram.getMaxHealth() == 20f, "jobless with a guard's health: " + bram.getMaxHealth());
			helper.assertTrue(free(helper, GRINDSTONE) == 1, "the grindstone is still held");
			Villager back = reloaded(helper, bram);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(back.getVillagerData().getProfession() == VillagerProfession.NONE && QaHallScreenGameTests.site(back).isEmpty(),
					"after the reload: " + back.getVillagerData() + " at " + QaHallScreenGameTests.site(back));
				helper.assertTrue(!Guards.isGuard(back) && back.getMaxHealth() == 20f, "after the reload a guard again: " + back.getMaxHealth());
				helper.assertTrue(guardsCounted(helper) == 0, "the hall counts " + guardsCounted(helper) + " guard");
				ChoiceMenu again = QaHallScreenGameTests.openPeople(helper, player);
				helper.assertTrue(row(helper, again, back).getString("kind").equals("jobless"), "his row after the reload: " + row(helper, again, back));
				QaHallScreenGameTests.jobDropdown(helper, player, again, ivy);
				ItemStack line = QaHallScreenGameTests.jobLine(helper, HallJobs.lines(again.icon(QaHallScreenGameTests.slotOf(helper, again, ivy))), ModVillagers.GUARD);
				helper.assertTrue(HallJobs.mark(line).getString("state").equals("free"), "the grindstone isn't free for Ivy: " + line.getHoverName().getString());
				helper.succeed();
			});
		});
	}

	/**
	 * A guard in the middle of a fight retrained as a Mason: within two seconds he is no longer fighting (no Mason
	 * fights on with a guard's brain), he is no guard and his health is a villager's again.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaHrdGuardFight"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaHrdGuardFight")
	public void aGuardRetrainedMidFightStopsFighting(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setDayTime(2000);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager bram = guard(helper, "Bram", 1, true);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 5);
		player.setInvulnerable(true);
		helper.runAfterDelay(20, () -> {
			Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(12, 2, 22));
			husk.setInvulnerable(true); // the fight lasts until the retraining
			Leftovers.after(helper, husk::discard);
		});
		long[] retrainedAt = {-1};
		helper.onEachTick(() -> {
			if (retrainedAt[0] < 0) {
				if (!GuardCombat.isFighting(bram)) {
					return;
				}
				ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
				retrain(helper, player, menu, bram, VillagerProfession.MASON);
				helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.MASON, "not retrained mid-fight: " + bram.getVillagerData() + "; " + seen);
				retrainedAt[0] = helper.getTick();
				return;
			}
			if (helper.getTick() - retrainedAt[0] < 40) {
				return;
			}
			helper.assertTrue(!GuardCombat.isFighting(bram), "the Mason still fights, 40 ticks after the retraining");
			helper.assertTrue(!Guards.isGuard(bram) && bram.getMaxHealth() == 20f, "guard " + Guards.isGuard(bram) + ", health " + bram.getMaxHealth());
			helper.succeed();
		});
	}

	/**
	 * A guard given No job, then the Guard job again from his own row: jobless, the grindstone is free on his own list,
	 * one click gives it back for nothing (a jobless villager's first job), and he is a guard again with a guard's 40
	 * health and counted by the hall; nothing was paid on the way.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdGuardBack"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdGuardBack")
	public void aGuardGivenNoJobCanBeMadeAGuardAgain(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		Villager bram = guard(helper, "Bram", 4, false);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 40);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			retrain(helper, player, menu, bram, VillagerProfession.NONE);
			helper.assertTrue(!Guards.isGuard(bram) && guardsCounted(helper) == 0, "No job didn't end his duty: " + bram.getVillagerData());
			QaHallScreenGameTests.jobDropdown(helper, player, menu, bram);
			ItemStack line = QaHallScreenGameTests.jobLine(helper, HallJobs.lines(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, bram))), ModVillagers.GUARD);
			helper.assertTrue(HallJobs.mark(line).getString("state").equals("free"), "his old grindstone isn't free on his list: " + line.getHoverName().getString());
			QaHallScreenGameTests.pickJob(helper, player, menu, ModVillagers.GUARD);
			helper.assertTrue(Guards.isGuard(bram) && QaHallScreenGameTests.site(bram).equals(Optional.of(helper.absolutePos(GRINDSTONE))),
				"not a guard again: " + bram.getVillagerData() + " at " + QaHallScreenGameTests.site(bram) + "; " + seen);
			helper.assertTrue(bram.getMaxHealth() == 40f, "a guard with " + bram.getMaxHealth() + " health");
			helper.assertTrue(guardsCounted(helper) == 1 && free(helper, GRINDSTONE) == 0, "counted " + guardsCounted(helper) + ", grindstone free " + free(helper, GRINDSTONE));
			helper.assertTrue(emeralds(player) == 40, "paid " + (40 - emeralds(player)));
			helper.succeed();
		});
	}

	/**
	 * Game rule {@code workplaceRetraining} off: a guard's and a shopkeeper's rows hide Job ▾, and forged clicks (Job ▾,
	 * a job, No job) leave both at their posts, the grindstone and counter held and the money untouched.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdRuleOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdRuleOff")
	public void withRetrainingOffRolesCantBeChanged(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager bram = guard(helper, "Bram", 2, false);
		shop(helper, UUID.randomUUID());
		Villager sal = keeper(helper, "Sal", 2);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 64);
		ServerLevel level = helper.getLevel();
		var rule = level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.RETRAINING);
		rule.set(false, level.getServer());
		Leftovers.after(helper, () -> rule.set(true, level.getServer()));
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			for (Villager v : List.of(bram, sal)) {
				helper.assertTrue(row(helper, menu, v).getBoolean("job_hidden"), "Job ▾ shown with the rule off: " + row(helper, menu, v));
				menu.clickMenuButton(player, HallJobs.OPEN + QaHallScreenGameTests.slotOf(helper, menu, v));
				helper.assertTrue(!HallJobs.isOpen(menu, v), "a list opened with the rule off");
				for (int i = 0; i < 2; i++) {
					QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
					QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.NONE);
				}
			}
			helper.assertTrue(Guards.isGuard(bram) && Shops.isShopkeeper(sal), "changed with the rule off: " + bram.getVillagerData() + ", " + sal.getVillagerData());
			helper.assertTrue(free(helper, GRINDSTONE) == 0 && free(helper, COUNTER) == 0 && free(helper, STONECUTTER) == 1, "a block changed hands");
			helper.assertTrue(emeralds(player) == 64, "paid " + (64 - emeralds(player)));
			helper.succeed();
		});
	}

	// --- a shopkeeper -------------------------------------------------------------------------------------------------

	/**
	 * A shopkeeper retrained as a Mason: the counter is free, the shop itself is untouched (owner, goods and price kept),
	 * the stock and the 7 emeralds of takings are still in its chest, and he no longer sells the shop's goods; across a
	 * save and reload he stays a Mason and sells nothing of the shop's.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdShopMason"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdShopMason")
	public void aShopkeeperRetrainedLeavesTheShopWhole(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		UUID frank = UUID.randomUUID();
		ShopCounterBlockEntity counter = shop(helper, frank);
		Villager sal = keeper(helper, "Sal", 2);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 10);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(3, () -> {
			Shops.refreshOffers(level, sal);
			helper.assertTrue(Shops.isShopkeeper(sal) && sellsCobble(sal), "setup: Sal doesn't run the shop: " + sal.getOffers().size() + " offers");
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			retrain(helper, player, menu, sal, VillagerProfession.MASON);
			helper.assertTrue(sal.getVillagerData().getProfession() == VillagerProfession.MASON && emeralds(player) == 0,
				"not retrained: " + sal.getVillagerData() + ", " + emeralds(player) + " left; " + seen);
			helper.assertTrue(!Shops.isShopkeeper(sal) && Shops.counter(level, sal).isEmpty(), "still at the counter");
			helper.assertTrue(free(helper, COUNTER) == 1, "the counter is still held");
			helper.assertTrue(frank.equals(counter.owner()) && counter.goods(0).is(Items.COBBLESTONE) && counter.goods(0).getCount() == 16
				&& counter.price(0).is(Items.EMERALD), "the shop changed: owner " + counter.owner() + ", " + counter.goods(0) + " for " + counter.price(0));
			helper.assertTrue(chestCount(helper, Items.COBBLESTONE) == 64 && chestCount(helper, Items.EMERALD) == 7,
				"the chest: " + chestCount(helper, Items.COBBLESTONE) + " cobblestone, " + chestCount(helper, Items.EMERALD) + " emeralds");
			helper.assertTrue(!sellsCobble(sal), "the Mason still sells the shop's cobblestone");
			Villager back = reloaded(helper, sal);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(back.getVillagerData().getProfession() == VillagerProfession.MASON && !Shops.isShopkeeper(back) && !sellsCobble(back),
					"after the reload: " + back.getVillagerData());
				helper.assertTrue(chestCount(helper, Items.EMERALD) == 7, "the takings after the reload: " + chestCount(helper, Items.EMERALD));
				helper.succeed();
			});
		});
	}

	/**
	 * A shopkeeper given No job, and the shop runs on with the next: the counter is free on a jobless neighbour's Job ▾
	 * (Shopkeeper), one click makes her its Shopkeeper for nothing, she sells the counter's goods from the chest, and the
	 * sale's emerald goes into the shop's chest, beside the old takings. The one who left sells nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdShopNext"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdShopNext")
	public void aShopLeftByItsKeeperRunsOnWithTheNext(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		ShopCounterBlockEntity counter = shop(helper, UUID.randomUUID());
		Villager sal = keeper(helper, "Sal", 3);
		Villager ivy = QaHallScreenGameTests.villager(helper, "Ivy", new BlockPos(12, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 3);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			retrain(helper, player, menu, sal, VillagerProfession.NONE);
			helper.assertTrue(sal.getVillagerData().getProfession() == VillagerProfession.NONE && emeralds(player) == 3, "No job: " + sal.getVillagerData());
			helper.assertTrue(free(helper, COUNTER) == 1, "the counter is still held");
			QaHallScreenGameTests.jobDropdown(helper, player, menu, ivy);
			ItemStack line = QaHallScreenGameTests.jobLine(helper, HallJobs.lines(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, ivy))), ModVillagers.SHOPKEEPER);
			helper.assertTrue(HallJobs.mark(line).getString("state").equals("free"), "the counter isn't free on Ivy's list: " + line.getHoverName().getString());
			QaHallScreenGameTests.pickJob(helper, player, menu, ModVillagers.SHOPKEEPER);
			helper.assertTrue(Shops.isShopkeeper(ivy) && QaHallScreenGameTests.site(ivy).equals(Optional.of(helper.absolutePos(COUNTER))),
				"Ivy: " + ivy.getVillagerData() + " at " + QaHallScreenGameTests.site(ivy) + "; " + seen);
			helper.assertTrue(emeralds(player) == 3, "Ivy's first job cost " + (3 - emeralds(player)));
			Shops.refreshOffers(level, ivy);
			helper.assertTrue(sellsCobble(ivy), "Ivy doesn't sell the shop's goods: " + ivy.getOffers().size() + " offers");
			helper.assertTrue(Shops.buy(player, ivy, 0), "the sale was refused; told " + seen);
			helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 16 && emeralds(player) == 2, "the buyer: "
				+ player.getInventory().countItem(Items.COBBLESTONE) + " cobblestone, " + emeralds(player) + " emeralds");
			helper.assertTrue(chestCount(helper, Items.COBBLESTONE) == 48 && chestCount(helper, Items.EMERALD) == 8,
				"the chest: " + chestCount(helper, Items.COBBLESTONE) + " cobblestone, " + chestCount(helper, Items.EMERALD) + " emeralds");
			helper.assertTrue(!Shops.isShopkeeper(sal) && !sellsCobble(sal), "Sal, jobless, still sells the shop's goods");
			helper.assertTrue(counter.goods(0).is(Items.COBBLESTONE), "the counter's goods changed");
			helper.succeed();
		});
	}

	// --- a friend with an open request ------------------------------------------------------------------------------------

	/**
	 * A friend (3 hearts) with an open "next level" request is retrained from Librarian to Mason: the friendship is kept,
	 * through a save and reload too, and the request can still be finished: the Mason's next level (Apprentice) ends it
	 * at the hall's next round and the reward is paid.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdRequestRetrain"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdRequestRetrain")
	public void aRetrainedFriendsRequestCanStillBeFinished(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		requestsOn(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		helper.setBlock(LECTERN, Blocks.LECTERN);
		Villager dara = QaHallScreenGameTests.villager(helper, "Dara", LECTERN.offset(1, 0, 1));
		Jobs.employ(helper.getLevel(), dara, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		dara.setVillagerXp(5);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 5);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(3, () -> {
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			Friendship.add(dara, player, 450);
			Quest quest = levelUpAccepted(helper, dara, player);
			int before = Friendship.points(dara, player.getUUID());
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			retrain(helper, player, menu, dara, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.MASON && emeralds(player) == 0, "not retrained: " + dara.getVillagerData());
			helper.assertTrue(Friendship.points(dara, player.getUUID()) == before, "friendship " + before + " -> " + Friendship.points(dara, player.getUUID()));
			helper.assertTrue(PersonalRequests.of(dara) == quest, "the request vanished with the retraining");
			Villager back = reloaded(helper, dara);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(Friendship.points(back, player.getUUID()) == before, "friendship after the reload: " + Friendship.points(back, player.getUUID()));
				helper.assertTrue(back.getVillagerData().getProfession() == VillagerProfession.MASON, "after the reload: " + back.getVillagerData());
				VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
				VillageQuests.tick(level, hall, entity);
				helper.assertTrue(PersonalRequests.of(back) == quest, "done before the Mason levelled up");
				BuilderLevels.addXp(level, back, Gifts.BOTTLE_XP, null);
				helper.assertTrue(back.getVillagerData().getLevel() == 2, "the Mason's level after the experience: " + back.getVillagerData().getLevel());
				VillageQuests.tick(level, hall, entity);
				helper.assertTrue(PersonalRequests.of(back) == null, "still open with the Mason at Apprentice: " + quest);
				helper.assertTrue(emeralds(player) > 0, "the reward wasn't paid: " + emeralds(player) + "; told " + seen);
				helper.succeed();
			});
		});
	}
}
