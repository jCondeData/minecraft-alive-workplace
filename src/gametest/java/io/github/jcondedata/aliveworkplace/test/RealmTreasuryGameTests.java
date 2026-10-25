package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * ROADMAP 33.13, the realm treasury: ten workers in a well-kept hamlet pay 0.3 of their 3 emeralds a day at the
 * default 10% levy; the capital's owner steps the levy on the Realm tab and at 0 nobody pays; the cap by rank holds
 * (a levy that doesn't fit stays with the village, a gift that doesn't fit is refused); a stranger gives with a click
 * and a shift-click but can't take, while the capital's owner takes with a right-click; the icon lists the last 20
 * movements, newest first, with the day and the reason; {@link RealmTreasury#spend}; and it survives save and reload,
 * an older file's realm loading with the default levy and an empty treasury. Each test is alone in its batch (realms
 * are on only inside {@link RealmGameTests#land}).
 */
public class RealmTreasuryGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** A Town capital and a Hamlet member in one realm, founded and joined through the tab; the member well kept. */
	static RealmGameTests.Land realm(GameTestHelper helper) {
		RealmGameTests.Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		RealmGameTests.found(helper, land);
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "the hamlet didn't join");
		land.entity(1).setNeeds(new VillageNeeds.Needs(10, 10, 10, 10, 10, 10, 10, 1f));
		return land;
	}

	/** The hamlet's next day's takings from ten workers. */
	static void aDay(RealmGameTests.Land land) {
		land.entity(1).setLastTaxDay(Chronicle.day(land.level()) - 1);
		Treasury.round(land.level(), land.hall(1), land.entity(1), 10);
	}

	static RealmData.Realm theRealm(RealmGameTests.Land land) {
		return land.data().realmOf(land.at(0));
	}

	/** Ten workers in a well-kept hamlet pay 0.3 of their 3 emeralds a day at 10%; the tab shows it. */
	//$ gametest_ticks_batch AREA '100' '"realmTreasuryLevy"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmTreasuryLevy")
	public void tenWorkersPayATenth(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper);
		helper.assertTrue(theRealm(land).levy() == 10 && theRealm(land).treasury() == 0, "a new realm's levy and treasury: " + theRealm(land));
		aDay(land);
		helper.assertTrue(land.entity(1).treasury() == 270, "the hamlet keeps 2.7 emeralds: " + land.entity(1).treasury());
		helper.assertTrue(theRealm(land).treasury() == 30, "the realm gets 0.3: " + theRealm(land).treasury());
		aDay(land);
		helper.assertTrue(land.entity(1).treasury() == 540 && theRealm(land).treasury() == 60, "a second day: " + land.entity(1).treasury() + " / " + theRealm(land).treasury());
		// a village in no realm pays nothing
		land.entity(0).setNeeds(new VillageNeeds.Needs(10, 10, 10, 10, 10, 10, 10, 1f));
		Realms.leave(land.level(), land.hall(1), land.owner());
		helper.assertTrue(land.data().realmOf(land.at(1)) == null, "the hamlet didn't leave");
		aDay(land);
		helper.assertTrue(land.entity(1).treasury() == 840 && theRealm(land).treasury() == 60, "out of the realm, it paid: " + land.entity(1).treasury());
		// the tab, from the capital
		ChoiceMenu menu = RealmGameTests.realmTab(helper, land, 0, land.owner());
		ItemStack icon = menu.icon(RealmTreasury.TREASURY);
		List<String> lore = RealmGameTests.lore(icon);
		helper.assertTrue(icon.is(Items.EMERALD_BLOCK) && icon.getHoverName().getString().equals("The realm treasury"), "no treasury icon: " + icon);
		helper.assertTrue(lore.contains("Holds 0.60 of 256 emeralds") && lore.contains("Levy: 10% of each member's daily takings"), "the icon's lines: " + lore);
		ItemStack levy = menu.icon(RealmTreasury.LEVY);
		helper.assertTrue(levy.is(Items.HOPPER) && levy.getHoverName().getString().equals("Levy: 10%")
			&& RealmGameTests.lore(levy).contains("Click to change it: 0, 5, 10 or 20%"), "the levy icon: " + levy + " " + RealmGameTests.lore(levy));
		helper.succeed();
	}

	/** The capital's owner steps the levy (10, 20, 0); at 0 the hamlet keeps all 3 emeralds; a stranger and a member's tab can't change it; with realms off, nothing is levied. */
	//$ gametest_ticks_batch AREA '100' '"realmTreasuryLevyOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmTreasuryLevyOff")
	public void theLevyAtZero(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper);
		ChoiceMenu menu = RealmGameTests.realmTab(helper, land, 0, land.owner());
		menu.press(RealmTreasury.LEVY, land.owner());
		helper.assertTrue(theRealm(land).levy() == 20 && menu.icon(RealmTreasury.LEVY).getHoverName().getString().equals("Levy: 20%"), "10 -> 20: " + theRealm(land).levy());
		String title = "the Barony of " + land.name(0);
		helper.assertTrue(RealmGameTests.heard(land, "The levy of " + title + " is now 20%."), "no levy line: " + land.heard());
		menu.press(RealmTreasury.LEVY, land.owner());
		helper.assertTrue(theRealm(land).levy() == 0, "20 -> 0: " + theRealm(land).levy());
		aDay(land);
		helper.assertTrue(land.entity(1).treasury() == 300 && theRealm(land).treasury() == 0, "at 0% the hamlet paid: " + land.entity(1).treasury() + " / " + theRealm(land).treasury());
		helper.assertTrue(theRealm(land).ledger().isEmpty(), "a movement for nothing: " + theRealm(land).ledger());
		// a stranger can't set it; nor can it be set from a member's tab
		ServerPlayer stranger = RealmGameTests.listener(helper, new ArrayList<>(), "stranger");
		helper.assertTrue(RealmTreasury.setLevy(land.level(), land.hall(0), stranger, 20).refusal() == Realms.Refusal.NOT_YOURS, "a stranger set the levy");
		helper.assertTrue(RealmTreasury.setLevy(land.level(), land.hall(1), land.owner(), 20).refusal() == Realms.Refusal.NOT_CAPITAL, "set from a member");
		ChoiceMenu fromMember = RealmGameTests.realmTab(helper, land, 1, land.owner());
		fromMember.press(RealmTreasury.LEVY, land.owner());
		helper.assertTrue(theRealm(land).levy() == 0 && RealmGameTests.lore(fromMember.icon(RealmTreasury.LEVY)).contains("The capital's owner sets it"), "a member's tab changed the levy");
		// with realms off nothing is levied, and it comes back
		helper.assertTrue(RealmTreasury.setLevy(land.level(), land.hall(0), land.owner(), 10).ok(), "back to 10");
		Realms.ENABLED = false;
		aDay(land);
		helper.assertTrue(land.entity(1).treasury() == 600 && theRealm(land).treasury() == 0, "levied with realms off");
		Realms.ENABLED = true;
		aDay(land);
		helper.assertTrue(land.entity(1).treasury() == 870 && theRealm(land).treasury() == 30, "the levy didn't come back");
		helper.succeed();
	}

	/** The cap by rank: 256, 512, 1,024, 2,048; a levy that doesn't fit stays with the village; a gift that doesn't fit is refused. */
	//$ gametest_ticks_batch AREA '100' '"realmTreasuryCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmTreasuryCap")
	public void theCapHolds(GameTestHelper helper) {
		helper.assertTrue(RealmTreasury.cap(Realms.Rank.BARONY) == 256 && RealmTreasury.cap(Realms.Rank.COUNTY) == 512
			&& RealmTreasury.cap(Realms.Rank.DUCHY) == 1024 && RealmTreasury.cap(Realms.Rank.KINGDOM) == 2048, "the caps");
		RealmGameTests.Land land = realm(helper);
		RealmData.Realm realm = theRealm(land);
		land.data().putRealm(realm.withTreasury(256 * 100 - 10, realm.ledger()));
		aDay(land);
		helper.assertTrue(theRealm(land).treasury() == 25600, "past the cap: " + theRealm(land).treasury());
		helper.assertTrue(land.entity(1).treasury() == 290, "what didn't fit stays with the village: " + land.entity(1).treasury());
		aDay(land);
		helper.assertTrue(theRealm(land).treasury() == 25600 && land.entity(1).treasury() == 590, "a full treasury took a levy");
		// a gift to a full treasury is refused and nothing is taken
		ChoiceMenu menu = RealmGameTests.realmTab(helper, land, 0, land.owner());
		menu.setCarried(new ItemStack(Items.EMERALD, 5));
		menu.press(RealmTreasury.TREASURY, land.owner());
		helper.assertTrue(menu.getCarried().getCount() == 5 && theRealm(land).treasury() == 25600, "a full treasury took a gift");
		helper.assertTrue(RealmGameTests.heard(land, "The treasury of the Barony of " + land.name(0) + " is full: it holds 256 emeralds."), "no full line: " + land.heard());
		// with room for two, a stack gives two
		realm = theRealm(land);
		land.data().putRealm(realm.withTreasury(254 * 100, realm.ledger()));
		menu.clicked(RealmTreasury.TREASURY, 0, ClickType.QUICK_MOVE, land.owner());
		helper.assertTrue(menu.getCarried().getCount() == 3 && theRealm(land).treasury() == 25600, "a stack past the cap: " + menu.getCarried() + " " + theRealm(land).treasury());
		helper.succeed();
	}

	/** A stranger gives one emerald with a click and the stack with a shift-click, but can't take; the capital's owner takes with a right-click. */
	//$ gametest_ticks_batch AREA '100' '"realmTreasuryStranger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmTreasuryStranger")
	public void aStrangerGivesButCantTake(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper);
		String title = "the Barony of " + land.name(0);
		List<Component> theirs = new ArrayList<>();
		ServerPlayer stranger = RealmGameTests.listener(helper, theirs, "stranger");
		ChoiceMenu menu = RealmGameTests.realmTab(helper, land, 1, stranger);
		helper.assertTrue(RealmGameTests.lore(menu.icon(RealmTreasury.TREASURY)).contains("Only the capital's owner and their friends take from it"), "the stranger's tooltip");
		menu.setCarried(new ItemStack(Items.EMERALD, 5));
		menu.press(RealmTreasury.TREASURY, stranger);
		helper.assertTrue(menu.getCarried().getCount() == 4 && theRealm(land).treasury() == 100, "a click gives one: " + theRealm(land).treasury());
		helper.assertTrue(theirs.stream().anyMatch(c -> c.getString().equals("You gave 1 emerald to the treasury of " + title + "; it holds 1.00 emeralds.")), "no given line: " + theirs);
		menu.clicked(RealmTreasury.TREASURY, 0, ClickType.QUICK_MOVE, stranger);
		helper.assertTrue(menu.getCarried().isEmpty() && theRealm(land).treasury() == 500, "a shift-click gives the stack: " + theRealm(land).treasury());
		// something else carried: nothing given, and told what to give
		menu.setCarried(new ItemStack(Items.DIAMOND, 3));
		menu.press(RealmTreasury.TREASURY, stranger);
		helper.assertTrue(menu.getCarried().getCount() == 3 && theRealm(land).treasury() == 500, "a diamond went in");
		helper.assertTrue(theirs.stream().anyMatch(c -> c.getString().equals("Click with emeralds to give to the realm treasury.")), "no give-how line: " + theirs);
		menu.setCarried(ItemStack.EMPTY);
		// the stranger can't take
		menu.clicked(RealmTreasury.TREASURY, 1, ClickType.PICKUP, stranger);
		helper.assertTrue(theRealm(land).treasury() == 500 && stranger.getInventory().countItem(Items.EMERALD) == 0, "a stranger took");
		helper.assertTrue(theirs.stream().anyMatch(c -> c.getString().equals("Only Owner and their friends can take from the treasury of " + title + ".")), "no refusal: " + theirs);
		// the capital's owner takes, from any member's tab
		ChoiceMenu owners = RealmGameTests.realmTab(helper, land, 1, land.owner());
		helper.assertTrue(RealmGameTests.lore(owners.icon(RealmTreasury.TREASURY)).contains("Right-click to take an emerald; shift-right-click to take a stack"), "the owner's tooltip");
		owners.clicked(RealmTreasury.TREASURY, 1, ClickType.PICKUP, land.owner());
		helper.assertTrue(theRealm(land).treasury() == 400 && land.owner().getInventory().countItem(Items.EMERALD) == 1, "a right-click takes one");
		owners.clicked(RealmTreasury.TREASURY, 1, ClickType.QUICK_MOVE, land.owner());
		helper.assertTrue(theRealm(land).treasury() == 0 && land.owner().getInventory().countItem(Items.EMERALD) == 5, "a shift-right-click takes the rest");
		helper.assertTrue(RealmTreasury.take(land.level(), land.hall(0), land.owner(), false).refusal() == Realms.Refusal.NO_MONEY, "took from an empty treasury");
		helper.succeed();
	}

	/** The icon lists the last 20 movements, newest first, with the day and the reason; spending more than it holds pays nothing. */
	//$ gametest_ticks_batch AREA '100' '"realmTreasuryMovements"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmTreasuryMovements")
	public void theMovementsAreListed(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper);
		long day = Chronicle.day(land.level());
		aDay(land);
		helper.assertTrue(RealmTreasury.give(land.level(), land.hall(0), land.owner(), new ItemStack(Items.EMERALD, 3), true).ok(), "giving");
		helper.assertTrue(RealmTreasury.spend(land.level().getServer(), theRealm(land), 125, Component.literal("Realm research")), "spending");
		helper.assertTrue(!RealmTreasury.spend(land.level().getServer(), theRealm(land), 10_000, Component.literal("A Wonder")), "spent more than it held");
		helper.assertTrue(theRealm(land).treasury() == 30 + 300 - 125, "after the movements: " + theRealm(land).treasury());
		List<String> lore = RealmGameTests.lore(RealmGameTests.realmTab(helper, land, 0, land.owner()).icon(RealmTreasury.TREASURY));
		int at = lore.indexOf("Last movements:");
		helper.assertTrue(at >= 0 && lore.size() > at + 3, "no movements: " + lore);
		helper.assertTrue(lore.get(at + 1).equals("Day " + day + ": -1.25 Realm research")
			&& lore.get(at + 2).equals("Day " + day + ": +3.00 Given by realm-owner")
			&& lore.get(at + 3).equals("Day " + day + ": +0.30 Levy from " + land.name(1)), "the movements: " + lore.subList(at, lore.size()));
		// only the last 20 are kept
		for (int i = 0; i < 25; i++) {
			RealmTreasury.spend(land.level().getServer(), theRealm(land), 1, Component.literal("Spent " + i));
		}
		List<RealmData.Movement> ledger = theRealm(land).ledger();
		helper.assertTrue(ledger.size() == 20 && ledger.get(19).reason().getString().equals("Spent 24") && ledger.get(0).reason().getString().equals("Spent 5"),
			"the last 20: " + ledger.size() + " " + ledger.get(0).reason().getString());
		lore = RealmGameTests.lore(RealmGameTests.realmTab(helper, land, 0, land.owner()).icon(RealmTreasury.TREASURY));
		at = lore.indexOf("Last movements:");
		helper.assertTrue(lore.get(at + 1).equals("Day " + day + ": -0.01 Spent 24") && lore.get(at + 20).equals("Day " + day + ": -0.01 Spent 5"), "the tooltip's 20: " + lore);
		helper.succeed();
	}

	/** The levy, treasury and movements survive save and reload; an older file's realm has the default levy and an empty treasury. */
	//$ gametest_ticks_batch AREA '100' '"realmTreasurySave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmTreasurySave")
	public void itSurvivesSaveAndReload(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper);
		helper.assertTrue(RealmTreasury.setLevy(land.level(), land.hall(0), land.owner(), 5).ok(), "levy 5");
		aDay(land);
		RealmTreasury.give(land.level(), land.hall(0), land.owner(), new ItemStack(Items.EMERALD, 12), true);
		RealmData.Realm was = theRealm(land);
		helper.assertTrue(was.treasury() == 15 + 1200 && was.ledger().size() == 2, "before saving: " + was);
		CompoundTag saved = land.data().save(new CompoundTag(), land.level().registryAccess());
		RealmData back = RealmData.load(saved, land.level().registryAccess());
		RealmData.Realm realm = back.realmOf(land.at(1));
		helper.assertTrue(realm != null && realm.levy() == 5 && realm.treasury() == 1215 && realm.ledger().size() == 2, "after reload: " + realm);
		for (int i = 0; i < 2; i++) {
			RealmData.Movement a = was.ledger().get(i);
			RealmData.Movement b = realm.ledger().get(i);
			helper.assertTrue(a.day() == b.day() && a.cents() == b.cents() && a.reason().getString().equals(b.reason().getString()), "movement " + i + ": " + a + " -> " + b);
		}
		// an older file: the realm without the treasury's keys
		ListTag realms = saved.getList("realms", Tag.TAG_COMPOUND);
		for (int i = 0; i < realms.size(); i++) {
			realms.getCompound(i).remove("levy");
			realms.getCompound(i).remove("treasury");
			realms.getCompound(i).remove("ledger");
		}
		RealmData.Realm old = RealmData.load(saved, land.level().registryAccess()).realmOf(land.at(1));
		helper.assertTrue(old != null && old.levy() == 10 && old.treasury() == 0 && old.ledger().isEmpty() && old.members().size() == 2, "an older file's realm: " + old);
		helper.succeed();
	}
}
