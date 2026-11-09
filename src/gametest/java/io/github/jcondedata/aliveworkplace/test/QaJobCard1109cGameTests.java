package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.card.JobCards;
import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane c, 2026-11-09) for ROADMAP 42.5, the job-status card, written from the spec: "The job-status chat lines (sneak
 * + empty hand on a Builder, Miner, Fisher, Farmer, Lumberjack, Orchard keeper, Postman) open a small card in the Hall's
 * look instead: the villager's name, job and level, what they're doing, what they need, and a button to their Hall
 * page." These tests try the entry point's edges (not sneaking, an item in hand, the off hand, another job, a jobless
 * villager, a worker with no field or not hired), the level names and the experience bar, what a miner needs, an idle
 * postman, the Hall page in a protected village (a stranger and the owner), a player who walks away and a villager who
 * dies with the card open. Every click goes through the server's packet handler, as a player's does.
 */
public class QaJobCard1109cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos STATION = new BlockPos(3, 2, 3);
	private static final BlockPos AT = new BlockPos(4, 2, 4);
	private static final BlockPos HALL = new BlockPos(9, 2, 3);

	record Setup(Villager villager, ServerPlayer player, List<Component> seen) {
	}

	/** A villager with {@code job} at {@code block}, named {@code name}, a listening player beside them, and maybe a hall. */
	private static Setup worker(GameTestHelper helper, Block block, ResourceKey<PoiType> poi, VillagerProfession job, String name, boolean hall) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		if (hall) {
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		}
		VillageHalls.forgetNearest();
		helper.setBlock(STATION, block);
		Villager v = helper.spawn(EntityType.VILLAGER, AT);
		v.setNoAi(true);
		if (job != VillagerProfession.NONE) {
			Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), poi, job);
		}
		v.setCustomName(Component.literal(name));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		return new Setup(v, player, seen);
	}

	/**
	 * A right-click on the villager holding {@code main} and {@code off}, sneaking or not, as the client sends it: the
	 * main hand's turn (at a point, then plain), then the off hand's, stopping at the first that opened something.
	 */
	private static void click(ServerPlayer player, Villager villager, ItemStack main, ItemStack off, boolean sneak) {
		player.setItemInHand(InteractionHand.MAIN_HAND, main);
		player.setItemInHand(InteractionHand.OFF_HAND, off);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.setShiftKeyDown(sneak);
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.containerMenu != player.inventoryMenu) {
				break;
			}
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, sneak, hand,
				new Vec3(0, villager.getBbHeight() / 2, 0)));
			if (player.containerMenu == player.inventoryMenu) {
				player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, sneak, hand));
			}
		}
		player.setShiftKeyDown(false);
	}

	private static void noCard(GameTestHelper helper, Setup s, String what) {
		helper.assertFalse(s.player().containerMenu instanceof StatusCardMenu, what + " opened the status card");
		s.player().closeContainer();
	}

	private static StatusCardMenu card(GameTestHelper helper, Setup s, String what) {
		helper.assertTrue(s.player().containerMenu instanceof StatusCardMenu, what + " didn't open the status card: "
			+ s.player().containerMenu.getClass().getSimpleName() + ", chat " + QaStageFourGameTests.keys(s.seen()));
		return (StatusCardMenu) s.player().containerMenu;
	}

	private static List<String> lines(StatusCard card) {
		return JobCardGameTests.lines(card);
	}

	private static void field(GameTestHelper helper, Villager v) {
		BlockPos o = helper.absolutePos(new BlockPos(12, 1, 12));
		ModAttachments.FARM_FIELD.set(v, new FieldJob(new BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX() + 4, o.getY() + 1, o.getZ() + 3)));
	}

	/**
	 * A Farmer with a field: a plain right-click (not sneaking) with an empty hand, a sneak-click holding a stick, and a
	 * sneak-click holding wheat open no card and send no field status; a sneak-click with only a torch in the off hand is
	 * still an empty-handed one (B342) and opens it.
	 */
	//$ gametest_batch AREA '"qaJobCard1109cHands"'
	@GameTest(template = AREA, batch = "qaJobCard1109cHands")
	public void qaOnlyASneakWithAnEmptyHandOpensTheCard(GameTestHelper helper) {
		Setup s = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER, "Bram", true);
		field(helper, s.villager());
		click(s.player(), s.villager(), ItemStack.EMPTY, ItemStack.EMPTY, false);
		noCard(helper, s, "a plain right-click");
		click(s.player(), s.villager(), new ItemStack(Items.STICK), ItemStack.EMPTY, true);
		noCard(helper, s, "a sneak-click holding a stick");
		click(s.player(), s.villager(), new ItemStack(Items.WHEAT, 3), ItemStack.EMPTY, true);
		noCard(helper, s, "a sneak-click holding wheat");
		helper.assertTrue(QaStageFourGameTests.keys(s.seen()).stream().noneMatch(k -> k.startsWith("message.aliveworkplace.field.header")),
			"the field status reached chat without a sneak and an empty hand: " + QaStageFourGameTests.keys(s.seen()));
		click(s.player(), s.villager(), ItemStack.EMPTY, new ItemStack(Items.TORCH), true);
		StatusCard c = card(helper, s, "a sneak-click with a torch in the off hand").card();
		helper.assertValueEqual(c.title().getString(), "Bram", "the title");
		s.player().closeContainer();
		helper.assertTrue(s.player().getOffhandItem().is(Items.TORCH), "the off-hand torch was used up");
		helper.succeed();
	}

	/**
	 * Villagers the card isn't for: a Librarian, a jobless villager, a Farmer with no field, a Fisherman not hired, a
	 * Lumberjack with no tree farm and an Orchard keeper with no orchard (none of whom had status lines) open no card.
	 */
	//$ gametest_batch AREA '"qaJobCard1109cNotForThem"'
	@GameTest(template = AREA, batch = "qaJobCard1109cNotForThem")
	public void qaNoCardForOtherJobsOrWorkersWithNothingToShow(GameTestHelper helper) {
		record Case(String what, Block block, ResourceKey<PoiType> poi, VillagerProfession job) {
		}
		List<Case> cases = List.of(
			new Case("a Librarian", Blocks.LECTERN, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN),
			new Case("a jobless villager", Blocks.AIR, PoiTypes.FARMER, VillagerProfession.NONE),
			new Case("a Farmer with no field", Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER),
			new Case("a Fisherman not hired", Blocks.BARREL, PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN),
			new Case("a Lumberjack with no tree farm", ModBlocks.CHOPPING_BLOCK, ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK),
			new Case("an Orchard keeper with no orchard", ModBlocks.FRUIT_BASKET, ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER));
		for (Case c : cases) {
			Setup s = worker(helper, c.block(), c.poi(), c.job(), "Nemo", true);
			helper.assertValueEqual(s.villager().getVillagerData().getProfession(), c.job(), "setup: " + c.what() + "'s job");
			click(s.player(), s.villager(), ItemStack.EMPTY, ItemStack.EMPTY, true);
			noCard(helper, s, "a sneak-click on " + c.what());
			s.villager().discard();
		}
		helper.succeed();
	}

	/**
	 * The tag names each level ("Farmer · Apprentice", "· Journeyman", "· Expert", "· Master"); the experience line counts
	 * up to the next level's threshold (Apprentice 35 → "35 / 70 XP", bar half full; Expert 200 → "200 / 250 XP"), and a
	 * Master's says "Top level" with no bar. An unnamed Farmer's title is his job.
	 */
	//$ gametest_batch AREA '"qaJobCard1109cLevels"'
	@GameTest(template = AREA, batch = "qaJobCard1109cLevels")
	public void qaEveryLevelAndItsExperienceLine(GameTestHelper helper) {
		Setup s = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER, "Bram", true);
		field(helper, s.villager());
		record Lvl(int level, int xp, String name, String experience) {
		}
		for (Lvl l : List.of(new Lvl(2, 35, "Apprentice", "Experience: 35 / 70 XP"), new Lvl(3, 100, "Journeyman", "Experience: 100 / 150 XP"),
			new Lvl(4, 200, "Expert", "Experience: 200 / 250 XP"), new Lvl(5, 300, "Master", "Experience: Top level"))) {
			s.villager().setVillagerData(s.villager().getVillagerData().setLevel(l.level()));
			s.villager().setVillagerXp(l.xp());
			click(s.player(), s.villager(), ItemStack.EMPTY, ItemStack.EMPTY, true);
			StatusCard c = card(helper, s, "a sneak-click on a level " + l.level() + " farmer").card();
			helper.assertValueEqual(c.tag().getString(), "Farmer · " + l.name(), "the tag at level " + l.level());
			StatusCard.Line xp = c.lines().stream().filter(x -> x.label().getString().equals("Experience")).findFirst().orElse(null);
			helper.assertTrue(xp != null, "no experience line: " + lines(c));
			helper.assertValueEqual(xp.label().getString() + ": " + xp.value().getString(), l.experience(), "the experience line at level " + l.level());
			if (l.level() == 5) {
				helper.assertFalse(xp.hasBar(), "a Master's experience line has a bar");
			} else {
				helper.assertTrue(xp.hasBar(), "no experience bar at level " + l.level());
			}
			s.player().closeContainer();
		}
		s.villager().setCustomName(null);
		click(s.player(), s.villager(), ItemStack.EMPTY, ItemStack.EMPTY, true);
		helper.assertValueEqual(card(helper, s, "a sneak-click on an unnamed farmer").card().title().getString(), "Farmer", "an unnamed farmer's title");
		s.player().closeContainer();
		helper.succeed();
	}

	/**
	 * A Miner whose quarry waits for a pickaxe: the card's needs list it in orange, with a pickaxe icon and "×1", not
	 * "Needs: Nothing"; when it waits for ladders, "Needs Ladder: ×64".
	 */
	//$ gametest_batch AREA '"qaJobCard1109cMinerNeeds"'
	@GameTest(template = AREA, batch = "qaJobCard1109cMinerNeeds")
	public void qaAMinerWaitingForAPickaxeOrLaddersSaysSo(GameTestHelper helper) {
		Setup s = worker(helper, ModBlocks.MINERS_BENCH, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER, "Gimli", true);
		ServerLevel level = helper.getLevel();
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()),
			Optional.of(helper.absolutePos(new BlockPos(14, 1, 14))), Optional.of(helper.absolutePos(new BlockPos(16, 1, 16))), 2));
		Miners.assign(s.player(), s.villager(), marker);
		QuarrySite site = Miners.activeSite(level, s.villager());
		helper.assertTrue(site != null, "setup: the miner didn't take the marker: " + QaStageFourGameTests.keys(s.seen()));
		Leftovers.after(helper, () -> QuarrySiteManager.get(level).remove(site.id()));
		site.setStatus(QuarrySite.Status.NEEDS_PICKAXE);
		click(s.player(), s.villager(), ItemStack.EMPTY, ItemStack.EMPTY, true);
		StatusCard c = card(helper, s, "a sneak-click on a miner").card();
		List<StatusCard.Line> needs = c.lines().stream().filter(l -> l.label().getString().toLowerCase().startsWith("needs ")).toList();
		helper.assertTrue(needs.size() == 1 && needs.get(0).label().getString().toLowerCase().equals("needs a pickaxe")
			&& needs.get(0).value().getString().equals("×1") && needs.get(0).color() == StatusCard.ORANGE && needs.get(0).icon().is(Items.IRON_PICKAXE),
			"the pickaxe isn't asked for: " + lines(c));
		helper.assertFalse(lines(c).contains("Needs: Nothing"), "a miner waiting for a pickaxe needs nothing: " + lines(c));
		s.player().closeContainer();
		site.setStatus(QuarrySite.Status.NEEDS_LADDERS);
		click(s.player(), s.villager(), ItemStack.EMPTY, ItemStack.EMPTY, true);
		c = card(helper, s, "a sneak-click on a miner short of ladders").card();
		helper.assertTrue(lines(c).contains("Needs Ladder: ×64"), "the ladders aren't asked for: " + lines(c));
		s.player().closeContainer();
		helper.succeed();
	}

	/**
	 * A Postman with no routes yet still gets a card (he had status lines): "Parcels delivered: 0", "Courier routes: 0",
	 * his experience and "Needs: Nothing" in green, and with no hall about Hall page is grayed with "There's no Village
	 * Hall near Pat."; In chat sends the routes' status.
	 */
	//$ gametest_batch AREA '"qaJobCard1109cIdlePostman"'
	@GameTest(template = AREA, batch = "qaJobCard1109cIdlePostman")
	public void qaAnIdlePostmanStillGetsACard(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < 20; i++) { // no hall left near this spot by an earlier batch
			VillageHalls.forgetNearest();
			Optional<BlockPos> left = VillageHalls.nearest(level, helper.absolutePos(AT));
			if (left.isEmpty()) {
				break;
			}
			level.setBlockAndUpdate(left.get(), Blocks.AIR.defaultBlockState());
		}
		Setup s = worker(helper, ModBlocks.MAILBOX, ModVillagers.MAILBOX_POI, ModVillagers.POSTMAN, "Pat", false);
		click(s.player(), s.villager(), ItemStack.EMPTY, ItemStack.EMPTY, true);
		StatusCardMenu menu = card(helper, s, "a sneak-click on an idle postman");
		StatusCard c = menu.card();
		helper.assertValueEqual(c.tag().getString(), "Postman · Novice", "the tag");
		helper.assertValueEqual(lines(c), List.of("Parcels delivered: 0", "Courier routes: 0", "Experience: " + s.villager().getVillagerXp() + " / 10 XP",
			"Needs: Nothing"), "the lines");
		helper.assertValueEqual(c.lines().get(3).color(), StatusCard.GREEN, "Needs: Nothing's color");
		helper.assertValueEqual(c.button(JobCards.HALL).refusal().getString(), "There's no Village Hall near Pat.", "Hall page's reason");
		NurseCardGameTests.press(s.player(), menu, JobCards.CHAT);
		helper.assertTrue(QaStageFourGameTests.keys(s.seen()).stream().anyMatch(k -> k.startsWith("message.aliveworkplace.route.")),
			"In chat sent no route status: " + QaStageFourGameTests.keys(s.seen()));
		helper.succeed();
	}

	/**
	 * In a protected village, a stranger's card has Hall page grayed with the village's "only they and their friends"
	 * line, and pressing it anyway opens no hall and says why; the owner's card has it usable, and it opens the hall.
	 */
	//$ gametest_batch AREA '"qaJobCard1109cProtected"'
	@GameTest(template = AREA, batch = "qaJobCard1109cProtected")
	public void qaHallPageInAProtectedVillage(GameTestHelper helper) {
		Setup s = worker(helper, Blocks.BARREL, PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN, "Finn", true);
		ModAttachments.FISHER_JOB.set(s.villager(), true);
		int radius = VillageHalls.RADIUS;
		boolean enabled = VillageProtection.ENABLED;
		VillageHalls.RADIUS = 16;
		VillageProtection.ENABLED = true;
		List<Component> ownerSeen = new ArrayList<>();
		ServerPlayer owner = QaStageFourGameTests.player(helper, ownerSeen, GameType.SURVIVAL);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setProtected(true);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageProtection.ENABLED = enabled;
			hall.setProtected(false);
		});
		ServerLevel level = helper.getLevel();
		helper.assertFalse(s.player().hasPermissions(2), "setup: the stranger is an operator");
		helper.assertTrue(VillageProtection.keeper(level, s.player(), helper.absolutePos(HALL)).isPresent(), "setup: the village doesn't keep the stranger out");

		click(s.player(), s.villager(), ItemStack.EMPTY, ItemStack.EMPTY, true);
		StatusCardMenu menu = card(helper, s, "a stranger's sneak-click");
		StatusCard.Button button = menu.card().button(JobCards.HALL);
		String denied = VillageProtection.denied(level, helper.absolutePos(HALL)).getString();
		helper.assertFalse(button.usable(), "Hall page isn't grayed for a stranger");
		helper.assertValueEqual(button.refusal().getString(), denied, "Hall page's reason for a stranger");
		helper.assertTrue(denied.contains("Owner") && denied.contains("only they and their friends"), "the reason doesn't name the owner: " + denied);
		NurseCardGameTests.press(s.player(), menu, JobCards.HALL);
		helper.assertFalse(s.player().containerMenu instanceof ChoiceMenu, "Hall page opened the hall for a stranger");
		helper.assertTrue(s.seen().stream().anyMatch(c -> c.getString().equals(denied)), "pressed anyway, no reason: " + QaStageFourGameTests.keys(s.seen()));

		Setup o = new Setup(s.villager(), owner, ownerSeen);
		click(owner, s.villager(), ItemStack.EMPTY, ItemStack.EMPTY, true);
		StatusCardMenu ownerMenu = card(helper, o, "the owner's sneak-click");
		helper.assertTrue(ownerMenu.card().button(JobCards.HALL).usable(), "Hall page grayed for the owner: "
			+ ownerMenu.card().button(JobCards.HALL).refusal().getString());
		NurseCardGameTests.press(owner, ownerMenu, JobCards.HALL);
		helper.assertTrue(owner.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU,
			"Hall page didn't open the hall for the owner: " + owner.containerMenu.getClass().getSimpleName());
		owner.closeContainer();
		helper.succeed();
	}

	/**
	 * A player who walks 28 blocks away with the card open (still inside the test area, whose chunks tick): pressing In
	 * chat from there sends nothing, and the card closes on the next ticks.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaJobCard1109cWalkAway"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaJobCard1109cWalkAway")
	public void qaTheCardClosesWhenThePlayerWalksAway(GameTestHelper helper) {
		Setup s = worker(helper, Blocks.BARREL, PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN, "Finn", true);
		ModAttachments.FISHER_JOB.set(s.villager(), true);
		click(s.player(), s.villager(), ItemStack.EMPTY, ItemStack.EMPTY, true);
		StatusCardMenu menu = card(helper, s, "a sneak-click on a hired fisher");
		BlockPos far = helper.absolutePos(new BlockPos(24, 2, 24));
		s.player().teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
		helper.assertFalse(menu.stillValid(s.player()), "the card is still valid 28 blocks away");
		NurseCardGameTests.press(s.player(), menu, JobCards.CHAT);
		helper.assertTrue(QaStageFourGameTests.keys(s.seen()).stream().noneMatch(k -> k.startsWith("message.aliveworkplace.fisher.")),
			"In chat answered 28 blocks away: " + QaStageFourGameTests.keys(s.seen()));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(s.player().containerMenu == s.player().inventoryMenu, "the card stayed open 28 blocks away: " + s.player().containerMenu + " at " + s.player().blockPosition());
			helper.succeed();
		});
	}

	/** The villager is killed while the card is open: In chat sends no status for the dead villager. */
	//$ gametest_ticks_batch AREA '60' '"qaJobCard1109cDiedChat"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaJobCard1109cDiedChat")
	public void qaInChatDoesNothingForADeadVillager(GameTestHelper helper) {
		Setup s = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER, "Bram", true);
		field(helper, s.villager());
		click(s.player(), s.villager(), ItemStack.EMPTY, ItemStack.EMPTY, true);
		StatusCardMenu menu = card(helper, s, "a sneak-click on a farmer");
		s.villager().kill();
		int before = s.seen().size();
		NurseCardGameTests.press(s.player(), menu, JobCards.CHAT);
		List<String> after = QaStageFourGameTests.keys(s.seen().subList(before, s.seen().size()));
		helper.assertTrue(after.stream().noneMatch(k -> k.startsWith("message.aliveworkplace.field.")), "In chat answered for a dead farmer: " + after);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(s.player().containerMenu == s.player().inventoryMenu, "the card stayed open: " + s.player().containerMenu);
			helper.succeed();
		});
	}

	/**
	 * The card shows the state when it opens: a Fisher's catch read, then 3 more fish, then reopened, reads 4 and 7; and
	 * Close shuts it without sending anything to chat.
	 */
	//$ gametest_batch AREA '"qaJobCard1109cFresh"'
	@GameTest(template = AREA, batch = "qaJobCard1109cFresh")
	public void qaReopeningShowsFreshNumbersAndCloseSendsNothing(GameTestHelper helper) {
		Setup s = worker(helper, Blocks.BARREL, PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN, "Finn", true);
		ModAttachments.FISHER_JOB.set(s.villager(), true);
		ModAttachments.FISH_CAUGHT.set(s.villager(), 4);
		click(s.player(), s.villager(), ItemStack.EMPTY, ItemStack.EMPTY, true);
		StatusCardMenu menu = card(helper, s, "the first sneak-click");
		helper.assertValueEqual(lines(menu.card()).get(0), "Fish caught: 4", "the first catch");
		int before = s.seen().size();
		NurseCardGameTests.press(s.player(), menu, StatusCardMenu.CLOSE);
		helper.assertTrue(s.player().containerMenu == s.player().inventoryMenu, "Close didn't close the card");
		helper.assertValueEqual(s.seen().size(), before, "Close sent something to chat");
		ModAttachments.FISH_CAUGHT.set(s.villager(), 7);
		click(s.player(), s.villager(), ItemStack.EMPTY, ItemStack.EMPTY, true);
		helper.assertValueEqual(lines(card(helper, s, "the second sneak-click").card()).get(0), "Fish caught: 7", "the catch after 3 more fish");
		s.player().closeContainer();
		helper.succeed();
	}
}
