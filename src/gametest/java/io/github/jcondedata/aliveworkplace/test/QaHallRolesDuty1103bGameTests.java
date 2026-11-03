package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Escorts;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.RallyBannerItem;
import io.github.jcondedata.aliveworkplace.guard.Scouts;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * QA (lane-c-1103-1554, round 2): the Village Hall's People table, Job ▾ and No job (40.3), on guards in the middle of
 * an errand: a scout out scouting the lair (on the way, or looking through the spyglass), a guard enlisted on a raised
 * Rally Banner, and a guard with an open "tools of my trade" request (a diamond sword). Every change through the hall's
 * own menu clicks. From docs/guide/guard.md, docs/wiki/scouts.md and personal-requests.md.
 */
public class QaHallRolesDuty1103bGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos GRINDSTONE = new BlockPos(8, 2, 20);
	private static final BlockPos STONECUTTER = new BlockPos(22, 2, 22);

	// --- helpers ------------------------------------------------------------------------------------------------------

	static Villager guard(GameTestHelper helper, String name, boolean ai) {
		helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE);
		Villager v = QaHallScreenGameTests.villager(helper, name, GRINDSTONE.offset(1, 0, 1));
		v.setNoAi(!ai);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(GRINDSTONE), PoiTypes.WEAPONSMITH, ModVillagers.GUARD);
		v.setVillagerXp(5);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Guards.updateHealth(v);
		return v;
	}

	private static int emeralds(ServerPlayer player) {
		return player.getInventory().countItem(Items.EMERALD);
	}

	private static void twoClicks(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager v, VillagerProfession job) {
		QaHallScreenGameTests.jobDropdown(helper, player, menu, v);
		QaHallScreenGameTests.pickJob(helper, player, menu, job);
		QaHallScreenGameTests.pickJob(helper, player, menu, job);
	}

	/** Day, a bandit lair {@code dx} east of the hall (its captain standing there), cleared afterwards. */
	static Lairs.Lair lair(GameTestHelper helper, int dx) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		long time = level.getDayTime();
		level.setDayTime(1000);
		int x = hall.getX() + dx;
		int z = hall.getZ();
		level.getChunk(x >> 4, z >> 4);
		BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		Vindicator captain = EntityType.VINDICATOR.create(level);
		captain.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		captain.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
		captain.setNoAi(true);
		captain.setPersistenceRequired();
		captain.setInvulnerable(true);
		captain.addTag(Lairs.TAG);
		captain.addTag(Lairs.CAPTAIN_TAG);
		level.addFreshEntity(captain);
		Lairs.Lair lair = new Lairs.Lair(pos, hall, captain.getUUID(), Chronicle.day(level), Threats.BANDITS, 7, Chronicle.day(level), 0, 0, 3);
		Lairs.putForTest(level, lair);
		Leftovers.after(helper, () -> {
			captain.discard();
			Lairs.forget(level);
			Scouts.forget(level);
			level.setDayTime(time);
		});
		return lair;
	}

	/** Where {@code v} is told to walk, if anywhere. */
	private static Optional<BlockPos> walkingTo(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(t -> t.getTarget().currentBlockPosition());
	}

	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static int spyglasses(Villager v) {
		int n = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (v.getItemBySlot(slot).is(Items.SPYGLASS)) {
				n++;
			}
		}
		return n;
	}

	/** Requests and friendship on for this test, the hall's stories forgotten before and after. */
	static void requestsOn(GameTestHelper helper) {
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

	static Quest accepted(GameTestHelper helper, String name, Villager villager, ServerPlayer player) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		QuestFiles.QuestFile file = QuestFiles.get(PersonalRequestGameTests.file(name)).orElse(null);
		helper.assertTrue(file != null, "no quest file personal/" + name);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		Quest quest = PersonalRequests.resolve(level, hall, entity, VillageHalls.census(level, hall), file, villager, player, RandomSource.create(7));
		helper.assertTrue(quest != null, "setup: " + villager.getName().getString() + " has no " + name + " to ask");
		PersonalRequests.propose(level, hall, villager, player, quest, Chronicle.day(level));
		PersonalRequestGameTests.command(player, "workplace quest accept " + quest.id);
		helper.assertTrue(PersonalRequests.of(villager) == quest, "setup: " + name + " wasn't accepted");
		return quest;
	}

	private static int count(Container chest, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(item)) {
				n += chest.getItem(i).getCount();
			}
		}
		return n;
	}

	// --- a scout on a mission -----------------------------------------------------------------------------------------

	/**
	 * A scout sent from the Defense page ("Scout the lair") is given No job while still on the way: the mission is
	 * called off within a second (he isn't "out" any more, and with no scout left "Scout the lair" sends nobody), and he
	 * keeps the spyglass.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdbScoutOut"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdbScoutOut")
	public void aScoutGivenNoJobOnTheWayIsCalledBack(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager ash = guard(helper, "Ash", false);
		ash.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			Lairs.Lair lair = lair(helper, 60);
			helper.assertTrue(Scouts.isScout(ash) && Scouts.send(level, hall) == Scouts.Sent.SENT, "setup: not sent");
			helper.runAfterDelay(3, () -> {
				Optional<BlockPos> leg = walkingTo(ash);
				helper.assertTrue(Scouts.out(ash) && leg.isPresent(), "setup: not on his way: " + Scouts.missionOf(level, ash.getUUID()) + ", walking to " + leg);
				ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
				twoClicks(helper, player, menu, ash, VillagerProfession.NONE);
				helper.assertTrue(ash.getVillagerData().getProfession() == VillagerProfession.NONE, "No job didn't take: " + ash.getVillagerData() + "; " + seen);
				helper.runAfterDelay(20, () -> {
					helper.assertTrue(!Scouts.out(ash) && Scouts.mission(level, hall).isEmpty(), "the mission goes on: " + Scouts.missionOf(level, ash.getUUID()));
					helper.assertTrue(Scouts.send(level, hall) == Scouts.Sent.NO_SCOUT, "the jobless villager could be sent again");
					helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qaHrdbScoutOut] scout leg " + leg
						+ ", walk target after the mission ended " + walkingTo(ash) + ", lair " + lair.pos()));
					helper.assertTrue(spyglasses(ash) == 1, "the spyglass: " + spyglasses(ash));
					helper.succeed();
				});
			});
		});
	}

	// --- a guard on a Rally Banner ----------------------------------------------------------------------------------

	/**
	 * A guard enlisted on the player's raised Rally Banner (guard.md: "they follow you and fight whatever goes for you")
	 * retrained as a Mason: he no longer follows the banner; enlisted again as a Mason, he isn't taken back as a guard.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdbRally"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdbRally")
	public void anEnlistedGuardRetrainedStopsFollowingTheBanner(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		Villager bram = guard(helper, "Bram", false);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 5);
		ItemStack banner = new ItemStack(ModItems.RALLY_BANNER);
		player.getInventory().setItem(20, banner);
		Leftovers.after(helper, () -> {
			player.getInventory().setItem(20, ItemStack.EMPTY);
			Escorts.scan(level.getServer());
		});
		helper.runAfterDelay(3, () -> {
			RallyBannerItem.enlist(player, bram, banner);
			banner.set(ModComponents.RALLY, new RallyBannerItem.Rally(RallyBannerItem.rally(banner).guards(), true));
			Escorts.scan(level.getServer());
			helper.assertTrue(Escorts.isEscorting(bram) && Escorts.leader(bram).orElse(null) == player, "setup: Bram doesn't follow the banner");
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			twoClicks(helper, player, menu, bram, VillagerProfession.MASON);
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.MASON, "not retrained: " + bram.getVillagerData() + "; " + seen);
			Escorts.scan(level.getServer());
			helper.assertTrue(!Escorts.isEscorting(bram) && Escorts.leader(bram).isEmpty(), "the Mason still follows the banner");
			helper.runAfterDelay(20, () -> {
				Escorts.scan(level.getServer());
				helper.assertTrue(!Escorts.isEscorting(bram), "the Mason follows the banner a second later");
				helper.succeed();
			});
		});
	}

	// --- "the tools of my trade" --------------------------------------------------------------------------------------

	/**
	 * A guard friend asks for the tools of his trade (a diamond sword) and is then retrained as a Mason from the hall.
	 * The request can still be finished from the journal's Personal tab: the sword is taken, the 5 emeralds paid, the
	 * request closed, and the sword lands in a chest (by his old grindstone or his new stonecutter), never lost.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdbTools"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdbTools")
	public void theToolsOfMyTradeCanBeBroughtAfterARetraining(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		requestsOn(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		BlockPos oldChest = GRINDSTONE.east();
		BlockPos newChest = STONECUTTER.west();
		helper.setBlock(oldChest, Blocks.CHEST);
		helper.setBlock(newChest, Blocks.CHEST);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager bram = guard(helper, "Bram", false);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 5);
		helper.runAfterDelay(3, () -> {
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			Friendship.add(bram, player, 450);
			Quest quest = accepted(helper, "tools_of_my_trade", bram, player);
			helper.assertTrue(quest.objectives.get(0) instanceof Objectives.Bring b && b.item().contains("diamond_sword"), "setup: asks " + quest.objectives);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			twoClicks(helper, player, menu, bram, VillagerProfession.MASON);
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.MASON && emeralds(player) == 0, "not retrained: " + bram.getVillagerData());
			helper.assertTrue(PersonalRequests.of(bram) == quest, "the request vanished with the retraining");
			player.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
			ChoiceMenu journal = ChoiceMenu.detached(player, m -> QuestJournal.render(m, level, hall, player, QuestJournal.Tab.PERSONAL));
			journal.press(QuestJournal.PERSONAL_ROW, player);
			helper.assertTrue(PersonalRequests.of(bram) == null, "the sword didn't finish the request: " + quest.objectives + "; told " + seen);
			helper.assertTrue(player.getInventory().countItem(Items.DIAMOND_SWORD) == 0 && emeralds(player) == 5,
				"the player: " + player.getInventory().countItem(Items.DIAMOND_SWORD) + " swords, " + emeralds(player) + " emeralds");
			int byOld = count(helper.getBlockEntity(oldChest), Items.DIAMOND_SWORD);
			int byNew = count(helper.getBlockEntity(newChest), Items.DIAMOND_SWORD);
			helper.assertTrue(byOld + byNew == 1, "the sword: " + byOld + " by the grindstone, " + byNew + " by the stonecutter");
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"[qaHrdbTools] the sword went to the chest by the " + (byNew == 1 ? "stonecutter (his job now)" : "grindstone (his old job)")));
			helper.succeed();
		});
	}
}
