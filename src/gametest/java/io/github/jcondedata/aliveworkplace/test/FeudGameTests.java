package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 33.20, feuds and war, with two owners (village 0: the realm-owner's, who declares; village 1: the
 * neighbour's): a feud from the Pacts tab ends the pact and routes and moves both boards, villagers grumble; peace by
 * letter ends it and a feud fades after 7 days; war is refused with PvP off (and the button says why); in a war a guard
 * goes for the enemy owner but not a neutral player, and the enemy owner can hurt a guard but not a villager, a block or
 * a chest of the protected village; the war ends after 3 days with the guards lost in both chronicles and no relief
 * riders; save and reload; the switches.
 */
public class FeudGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static ServerPlayer them;

	/** Two villages of two owners on the caravans' list, pacts and feuds on and empty, PvP on; all put back after. */
	private static Land two(GameTestHelper helper, List<Component> theirs) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		MinecraftServer server = land.level().getServer();
		boolean pacts = Pacts.ENABLED;
		boolean alliances = Pacts.ALLIANCES;
		boolean feuds = Feuds.ENABLED;
		boolean wars = Feuds.WARS;
		boolean pvp = server.isPvpAllowed();
		Pacts.ENABLED = true;
		Pacts.ALLIANCES = true;
		Feuds.ENABLED = true;
		Feuds.WARS = true;
		server.setPvpAllowed(true);
		Pacts.Data.get(server).clear();
		Pacts.Data.get(server).setMigrated(true);
		Feuds.Data.get(server).clear();
		Leftovers.after(helper, () -> {
			Pacts.ENABLED = pacts;
			Pacts.ALLIANCES = alliances;
			Feuds.ENABLED = feuds;
			Feuds.WARS = wars;
			server.setPvpAllowed(pvp);
			Pacts.Data.get(server).clear();
			Feuds.Data.get(server).clear();
			Caravans.Data data = Caravans.Data.get(land.level());
			data.remove(land.hall(0));
			data.remove(land.hall(1));
		});
		them = RealmGameTests.listener(helper, theirs, "neighbour");
		land.entity(1).setOwner(them.getUUID(), "neighbour");
		Realms.round(land.level(), land.hall(1), land.entity(1), 6);
		Caravans.Data data = Caravans.Data.get(land.level());
		data.setWants(land.hall(0), Component.literal(land.name(0)), List.of());
		data.setWants(land.hall(1), Component.literal(land.name(1)), List.of());
		return land;
	}

	private static String letter(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			WrittenBookContent book = player.getInventory().getItem(i).get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (book != null) {
				return book.pages().get(0).raw().getString();
			}
		}
		return null;
	}

	/** Every letter the player holds, one after another. */
	private static String letters(ServerPlayer player) {
		StringBuilder all = new StringBuilder();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			WrittenBookContent book = player.getInventory().getItem(i).get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (book != null) {
				all.append(book.pages().get(0).raw().getString()).append('\n');
			}
		}
		return all.toString();
	}

	private static boolean chronicled(Land land, int hall, String line) {
		return land.entity(hall).chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.FEUD && e.text().getString().equals(line));
	}

	/** The hall's Pacts tab, opened the way a player does. */
	private static ChoiceMenu pactsTab(Land land, int hall, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, land.hall(hall));
		menu.press(VillageHallScreen.ROUTES, player);
		menu.press(TradePage.Tab.PACTS.slot(), player);
		return menu;
	}

	/** The slot whose button is named {@code name}, or -1. */
	private static int find(ChoiceMenu menu, String name) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).getHoverName().getString().equals(name)) {
				return slot;
			}
		}
		return -1;
	}

	private static String lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? "" : String.join(" | ", lore.lines().stream().map(Component::getString).toList());
	}

	/** A feud whose war began a moment ago and runs three days. */
	private static Feuds.Feud atWar(Land land) {
		long now = land.level().getServer().overworld().getGameTime();
		Feuds.Feud war = new Feuds.Feud(land.at(0), land.at(1), now - 2 * 24000L, now - 1, now - 1 + Feuds.WAR_DAYS * 24000L, false, 0, 0, 0);
		Feuds.Data.get(land.level().getServer()).put(war);
		return war;
	}

	/** Declared from the Pacts tab: a letter, the pact and both routes end, both boards move 25% for the rival's people, villagers grumble. */
	//$ gametest_ticks_batch AREA '100' '"feudDeclare"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "feudDeclare")
	public void aFeudStopsRoutesAndMovesBothBoards(GameTestHelper helper) {
		List<Component> theirs = new ArrayList<>();
		Land land = two(helper, theirs);
		MinecraftServer server = land.level().getServer();
		ServerPlayer neutral = RealmGameTests.listener(helper, new ArrayList<>(), "passer-by");
		Pacts.Data.get(server).put(new Pacts.Pact(land.at(0), land.at(1), false, 0, -1, false, false));
		Caravans.Data routes = Caravans.Data.get(land.level());
		helper.assertTrue(routes.toggleRoute(land.hall(0), land.hall(1)) && routes.toggleRoute(land.hall(1), land.hall(0)), "setup: no routes");
		// the Pacts tab's button
		ChoiceMenu menu = pactsTab(land, 0, land.owner());
		int declare = find(menu, "Declare a feud on " + land.name(1));
		helper.assertTrue(declare >= 0, "no Declare a feud button on the Pacts tab");
		menu.press(declare, land.owner());
		helper.assertTrue(Feuds.feuding(server, land.at(0), land.at(1)), "no feud after pressing Declare");
		String letter = letter(them);
		helper.assertTrue(letter != null && letter.contains("declares a feud on " + land.name(1)) && letter.contains("25% less") && letter.contains("fades after 7 days"), "the letter: " + letter);
		helper.assertTrue(theirs.stream().anyMatch(c -> c.getString().contains("has declared a feud on you")), "the rival wasn't told: " + theirs);
		// pact and routes end, both ways; no new pact
		helper.assertTrue(Pacts.Data.get(server).pact(land.at(0), land.at(1)) == null, "the pact stayed");
		helper.assertTrue(!routes.routesFrom(land.hall(0)).contains(land.hall(1)) && !routes.routesFrom(land.hall(1)).contains(land.hall(0)), "a route stayed");
		helper.assertTrue(!Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)) && !Pacts.mayRoute(land.level(), land.hall(1), land.hall(0)), "routes allowed in a feud");
		helper.assertTrue(!Pacts.offer(land.level(), land.hall(1), land.hall(0), false, them).ok(), "a pact offered in a feud");
		// both boards: the rival's people pay 25% more and get 25% less; neutral and own people as before
		helper.assertTrue(Feuds.boardPercent(land.level(), land.hall(0), them, true) == 125 && Feuds.boardPercent(land.level(), land.hall(0), them, false) == 75, "village 0's board for the rival");
		helper.assertTrue(Feuds.boardPercent(land.level(), land.hall(1), land.owner(), true) == 125 && Feuds.boardPercent(land.level(), land.hall(1), land.owner(), false) == 75, "village 1's board for the rival");
		helper.assertTrue(Feuds.boardPercent(land.level(), land.hall(0), neutral, true) == 100 && Feuds.boardPercent(land.level(), land.hall(1), neutral, false) == 100, "a neutral player's prices moved");
		helper.assertTrue(Feuds.boardPercent(land.level(), land.hall(0), land.owner(), true) == 100, "the owner's own board moved");
		var good = io.github.jcondedata.aliveworkplace.trade.TradeGoods.all().get(0);
		String rivalSees = lore(TradePage.goodIcon(land.level(), land.hall(1), good, null, land.owner(), null));
		String neutralSees = lore(TradePage.goodIcon(land.level(), land.hall(1), good, null, neutral, null));
		helper.assertTrue(rivalSees.contains("charged 25% more") && !neutralSees.contains("charged 25% more") && !rivalSees.equals(neutralSees), "the board's prices: " + rivalSees);
		// chronicles, and the villagers grumble
		helper.assertTrue(chronicled(land, 0, "Declared a feud on " + land.name(1)) && chronicled(land, 1, land.name(0) + " declared a feud on us"), "the chronicles");
		Villager baker = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 6));
		helper.assertTrue(Chatter.topics(land.level(), baker, land.hall(1)).contains("feud"), "no feud chatter");
		helper.assertTrue(Feuds.rivalName(land.level(), land.hall(1)).getString().equals(land.name(0)), "the grumble's rival");
		helper.assertTrue(!Feuds.declare(land.level(), land.hall(0), land.hall(1), land.owner()).ok(), "declared twice");
		helper.succeed();
	}

	/** Peace offered by letter and accepted on the other's tab ends the feud; a feud without a war fades after 7 days. */
	//$ gametest_ticks_batch AREA '100' '"feudPeace"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "feudPeace")
	public void peaceEndsAFeudAndOneFadesAfterAWeek(GameTestHelper helper) {
		List<Component> theirs = new ArrayList<>();
		Land land = two(helper, theirs);
		MinecraftServer server = land.level().getServer();
		helper.assertTrue(Feuds.declare(land.level(), land.hall(0), land.hall(1), land.owner()).ok(), "couldn't declare");
		// the rival offers peace from their tab
		ChoiceMenu menu = pactsTab(land, 1, them);
		int offer = find(menu, "Offer " + land.name(0) + " peace");
		helper.assertTrue(offer >= 0, "no Offer peace button");
		menu.press(offer, them);
		helper.assertTrue(Feuds.feuding(server, land.at(0), land.at(1)), "peace without an answer");
		helper.assertTrue(RealmGameTests.heard(land, land.name(1) + " offers peace: a letter is on its way. Accept it on the Pacts tab."), "not told: " + land.heard());
		helper.assertTrue(!Feuds.acceptPeace(land.level(), land.hall(1), land.at(0), them).ok(), "accepted their own offer");
		ChoiceMenu ours = pactsTab(land, 0, land.owner());
		int accept = find(ours, "Accept peace with " + land.name(1));
		helper.assertTrue(accept >= 0, "no Accept peace button");
		ours.press(accept, land.owner());
		helper.assertTrue(!Feuds.feuding(server, land.at(0), land.at(1)) && Feuds.boardPercent(land.level(), land.hall(0), them, true) == 100, "the feud stayed after peace");
		helper.assertTrue(chronicled(land, 0, "Made peace with " + land.name(1)) && chronicled(land, 1, "Made peace with " + land.name(0)), "peace in the chronicles");
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok(), "no pact after peace");
		// a feud 7 days old without a war fades; one 6 days old stays
		long now = server.overworld().getGameTime();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(1), land.at(0), now - Feuds.FADE + 24000));
		Feuds.settle(server);
		helper.assertTrue(Feuds.feuding(server, land.at(0), land.at(1)), "faded before 7 days");
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(1), land.at(0), now - Feuds.FADE));
		Feuds.settle(server);
		helper.assertTrue(!Feuds.feuding(server, land.at(0), land.at(1)) && chronicled(land, 0, "The feud with " + land.name(1) + " faded"), "didn't fade after 7 days");
		helper.succeed();
	}

	/** PvP off: no war, and the button says why; feudWars off: no war; too soon: no war; then PvP on: war from the next dawn. */
	//$ gametest_ticks_batch AREA '100' '"feudWarRefused"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "feudWarRefused")
	public void warIsRefusedWithPvpOff(GameTestHelper helper) {
		List<Component> theirs = new ArrayList<>();
		Land land = two(helper, theirs);
		MinecraftServer server = land.level().getServer();
		helper.assertTrue(Feuds.declare(land.level(), land.hall(0), land.hall(1), land.owner()).ok(), "couldn't declare");
		Pacts.Result soon = Feuds.raiseWar(land.level(), land.hall(0), land.at(1), land.owner());
		helper.assertTrue(!soon.ok() && soon.message().getString().contains("must stand a day"), "war on the first day: " + soon.message().getString());
		long now = server.overworld().getGameTime();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - Feuds.WAR_AFTER));
		server.setPvpAllowed(false);
		Pacts.Result refused = Feuds.raiseWar(land.level(), land.hall(0), land.at(1), land.owner());
		helper.assertTrue(!refused.ok() && refused.message().getString().contains("PvP off"), "war with PvP off: " + refused.message().getString());
		ChoiceMenu menu = pactsTab(land, 0, land.owner());
		int war = find(menu, "Raise the feud with " + land.name(1) + " to war");
		helper.assertTrue(war >= 0 && lore(menu.icon(war)).contains("Can't be pressed: this server has PvP off"), "the war button doesn't say why: " + (war < 0 ? "none" : lore(menu.icon(war))));
		menu.press(war, land.owner());
		helper.assertTrue(!Feuds.feud(server, land.at(0), land.at(1)).warRaised(), "pressing the button with PvP off raised a war");
		server.setPvpAllowed(true);
		Feuds.WARS = false;
		helper.assertTrue(!Feuds.raiseWar(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "war with feudWars off");
		Feuds.WARS = true;
		helper.assertTrue(!Feuds.raiseWar(land.level(), land.hall(1), land.at(0), them).ok(), "the side that didn't declare raised war");
		Pacts.Result raised = Feuds.raiseWar(land.level(), land.hall(0), land.at(1), land.owner());
		helper.assertTrue(raised.ok(), "war refused with PvP on: " + raised.message().getString());
		Feuds.Feud f = Feuds.feud(server, land.at(0), land.at(1));
		long dawn = now + Math.floorMod(-server.overworld().getDayTime(), 24000L);
		helper.assertTrue(f.warStarts() == dawn && f.warEnds() == dawn + 3 * 24000L, "the war's days: " + f);
		String letter = letters(them);
		helper.assertTrue(letter.contains("raises the feud with " + land.name(1) + " to war"), "the war letter: " + letter);
		helper.succeed();
	}

	/**
	 * In a war: a guard of village 1 goes for village 0's owner inside the village, not for a neutral player or its own
	 * owner; that enemy can hurt the guard, but not a villager, a block or a chest of the protected village; no relief
	 * riders come; a fallen guard is counted, and after 3 days the war ends with both chronicles saying who lost what.
	 */
	//$ gametest_ticks_batch AREA '200' '"feudWar"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "feudWar")
	public void aWarsGuardsFightTheEnemyOwnerOnly(GameTestHelper helper) {
		List<Component> theirs = new ArrayList<>();
		Land land = two(helper, theirs);
		MinecraftServer server = land.level().getServer();
		boolean relief = Relief.ENABLED;
		Relief.ENABLED = true;
		Relief.forget();
		land.entity(1).setProtected(true);
		Leftovers.after(helper, () -> {
			land.entity(1).setProtected(false);
			Relief.ENABLED = relief;
			Relief.forget();
		});
		ServerPlayer neutral = RealmGameTests.listener(helper, new ArrayList<>(), "passer-by");
		ServerPlayer enemy = land.owner();
		List<Villager> guards = ReliefGameTests.guards(land, 1, 2);
		Villager guard = guards.get(0);
		guard.setNoAi(true);
		guards.get(1).setNoAi(true);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 5));
		farmer.setNoAi(true);
		farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		BlockPos stone = new BlockPos(14, 2, 1);
		BlockPos chest = new BlockPos(10, 2, 1);
		helper.setBlock(stone, Blocks.STONE);
		helper.setBlock(chest, Blocks.CHEST);
		BlockPos stand = land.hall(1).offset(0, 0, 1);
		for (ServerPlayer p : List.of(enemy, neutral, them)) {
			p.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
		}
		// before the war: no foe, and the guard is kept from the enemy
		helper.assertTrue(!Guards.isFoe(enemy, guard), "a foe before the war");
		helper.assertTrue(!VillageProtection.mayHurt(land.level(), enemy, guard), "the guard could be hurt before the war");
		// a war raised but not yet begun: still nothing
		long now = server.overworld().getGameTime();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - 2 * 24000L, now + 100, now + 100 + 3 * 24000L, false, 0, 0, 0));
		helper.assertTrue(!Guards.isFoe(enemy, guard), "a foe before the war's dawn");
		atWar(land);
		helper.assertTrue(Guards.isFoe(enemy, guard), "the guard doesn't go for the enemy owner in a war");
		helper.assertTrue(!Guards.isFoe(neutral, guard) && !Guards.isFoe(them, guard), "the guard goes for a neutral player or its own owner");
		// out of the village, not a foe
		BlockPos far = land.hall(1).offset(io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS + 20, 0, 0);
		enemy.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0f, 0f);
		helper.assertTrue(!Guards.isFoe(enemy, guard), "a foe outside the village");
		enemy.moveTo(guard.getX() + 1.2, guard.getY(), guard.getZ(), 90f, 0f);
		// the enemy hits the guard (as the client asks): it's hurt; the farmer isn't
		float before = guard.getHealth();
		enemy.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(guard, false));
		helper.assertTrue(guard.getHealth() < before, "the enemy owner couldn't hurt the guard");
		enemy.moveTo(farmer.getX() + 1.2, farmer.getY(), farmer.getZ(), 90f, 0f);
		float farmerBefore = farmer.getHealth();
		enemy.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(farmer, false));
		helper.assertTrue(farmer.getHealth() == farmerBefore, "the enemy owner hurt a villager");
		helper.assertTrue(!VillageProtection.mayHurt(land.level(), neutral, guard), "a neutral player could hurt the guard");
		// no block, no chest
		helper.assertTrue(!enemy.gameMode.destroyBlock(helper.absolutePos(stone)), "the enemy broke a block");
		helper.assertBlockPresent(Blocks.STONE, stone);
		BlockPos at = helper.absolutePos(chest);
		enemy.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 1.5, 180f, 0f);
		enemy.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		enemy.gameMode.useItemOn(enemy, land.level(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.SOUTH, 0.5), Direction.SOUTH, at, false));
		helper.assertTrue(!(enemy.containerMenu instanceof ChestMenu), "the enemy opened a chest");
		// relief riders answer raids only
		helper.assertTrue(Relief.onTheRoad(land.level(), land.hall(1)).isEmpty() && Relief.onTheRoad(land.level(), land.hall(0)).isEmpty(), "riders came to a war");
		// a guard falls to the enemy: counted for village 1
		Villager fallen = guards.get(1);
		fallen.die(land.level().damageSources().playerAttack(enemy));
		fallen.discard();
		Feuds.Feud f = Feuds.feud(server, land.at(0), land.at(1));
		helper.assertTrue(f.lostOn() == 1 && f.lostBy() == 0, "the fallen guard wasn't counted: " + f);
		// PvP turned off mid-war: guards stand down
		server.setPvpAllowed(false);
		enemy.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
		helper.assertTrue(!Guards.isFoe(enemy, guard), "a foe with PvP off");
		server.setPvpAllowed(true);
		// three days on, the war ends; the feud stands; no treasury moves
		land.entity(0).setTreasury(5000);
		land.entity(1).setTreasury(7000);
		Feuds.Data.get(server).put(new Feuds.Feud(f.by(), f.on(), f.since(), f.warStarts(), server.overworld().getGameTime(), false, f.lostBy(), f.lostOn(), 0));
		Feuds.settle(server);
		helper.assertTrue(!Guards.isFoe(enemy, guard), "still a foe after the war's 3 days");
		helper.assertTrue(Feuds.feud(server, land.at(0), land.at(1)).warDone(), "the war didn't end");
		helper.assertTrue(chronicled(land, 0, "The war with " + land.name(1) + " ended. Guards lost: ours 0, theirs 1"), "village 0's chronicle");
		helper.assertTrue(chronicled(land, 1, "The war with " + land.name(0) + " ended. Guards lost: ours 1, theirs 0"), "village 1's chronicle");
		helper.assertTrue(land.entity(0).treasury() == 5000 && land.entity(1).treasury() == 7000 && Feuds.feuding(server, land.at(0), land.at(1)), "the feud ended with the war");
		helper.succeed();
	}

	/** Save and reload keeps a feud and its war; an old save has none; {@code feuds} off ends them quietly and hides the tab with pacts off. */
	//$ gametest_ticks_batch AREA '100' '"feudSaveSwitches"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "feudSaveSwitches")
	public void feudsSaveAndTheSwitches(GameTestHelper helper) {
		Land land = two(helper, new ArrayList<>());
		MinecraftServer server = land.level().getServer();
		Feuds.Data data = Feuds.Data.get(server);
		data.put(new Feuds.Feud(land.at(0), land.at(1), 5, 30000, 102000, false, 2, 3, 1));
		CompoundTag tag = data.save(new CompoundTag(), server.registryAccess());
		Feuds.Data back = Feuds.Data.load(tag, server.registryAccess());
		helper.assertTrue(back.feuds().equals(data.feuds()), "save and reload: " + back.feuds());
		helper.assertTrue(Feuds.Data.load(new CompoundTag(), server.registryAccess()).feuds().isEmpty(), "an old save has feuds");
		Feuds.ENABLED = false;
		helper.assertTrue(!Feuds.feuding(server, land.at(0), land.at(1)) && Feuds.boardPercent(land.level(), land.hall(0), them, true) == 100, "a feud counts with feuds off");
		helper.assertTrue(!Feuds.declare(land.level(), land.hall(1), land.hall(0), them).ok(), "declared with feuds off");
		Feuds.settle(server);
		helper.assertTrue(data.feuds().isEmpty(), "feuds off didn't end the feud");
		Pacts.ENABLED = false;
		helper.assertTrue(!TradePage.tabs().contains(TradePage.Tab.PACTS), "the Pacts tab shows with pacts and feuds off");
		Feuds.ENABLED = true;
		helper.assertTrue(TradePage.tabs().contains(TradePage.Tab.PACTS), "no Pacts tab with feuds on");
		helper.succeed();
	}
}
