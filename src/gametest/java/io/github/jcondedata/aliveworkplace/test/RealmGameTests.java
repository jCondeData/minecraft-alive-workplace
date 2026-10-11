package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * ROADMAP 33.11, founding a realm: a Town founds one on its Trade page's Realm tab ("the Barony of ..."), the capital's
 * owner adds their own villages and a friend's with one click and can't add a stranger's or one too far; colonies join
 * their mother's realm; a member leaves and the capital lets one go; the ranks are counted (County, Duchy, Kingdom)
 * with fireworks, a chat line and the new title; a broken capital passes the crown to the biggest Town or ends the
 * realm; a Name Tag renames it and a banner sets its arms; the hall's name icon and "What next?" say so; it survives
 * save and reload; and the switch. Realms are off in GameTests (other tests count the Trade page's tabs), so every
 * test here turns them on, alone in its batch.
 */
public class RealmGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** A test's villages: their halls, side by side, the first owned by {@code owner}, who hears every chat line. */
	record Land(ServerLevel level, ServerPlayer owner, List<Component> heard, List<BlockPos> halls) {
		BlockPos hall(int i) {
			return halls.get(i);
		}

		GlobalPos at(int i) {
			return GlobalPos.of(level.dimension(), halls.get(i));
		}

		VillageHallBlockEntity entity(int i) {
			return (VillageHallBlockEntity) level.getBlockEntity(halls.get(i));
		}

		String name(int i) {
			return VillageHalls.name(level, halls.get(i)).getString();
		}

		RealmData data() {
			return RealmData.get(level.getServer());
		}
	}

	/**
	 * {@code ranks.length} halls in a 3 x 3 grid, all owned by one listening player, of the given ranks, each seen by a
	 * round (as the hall's own round does) with {@code 5 + i} villagers; realms on, no hall rounds during the test, the
	 * realm records empty. Everything is put back after.
	 */
	static Land land(GameTestHelper helper, VillageRanks.Rank... ranks) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Realms.ENABLED;
		int max = Realms.MAX_MEMBERS;
		int every = VillageNeeds.CHECK_EVERY;
		Realms.ENABLED = true;
		Realms.MAX_MEMBERS = 12;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		RealmData.get(level.getServer()).clearRealms();
		RealmData.get(level.getServer()).clearColonies();
		Leftovers.after(helper, () -> {
			Realms.ENABLED = enabled;
			Realms.MAX_MEMBERS = max;
			VillageNeeds.CHECK_EVERY = every;
			RealmData.get(level.getServer()).clearRealms();
			RealmData.get(level.getServer()).clearColonies();
		});
		List<Component> heard = new ArrayList<>();
		ServerPlayer owner = listener(helper, heard, "realm-owner");
		List<BlockPos> halls = new ArrayList<>();
		for (int i = 0; i < ranks.length; i++) {
			BlockPos rel = new BlockPos(3 + 9 * (i % 3), 2, 3 + 9 * (i / 3));
			helper.setBlock(rel, ModBlocks.VILLAGE_HALL);
			BlockPos hall = helper.absolutePos(rel);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setOwner(owner.getUUID(), "Owner");
			entity.setRank(ranks[i]);
			halls.add(hall);
			Realms.round(level, hall, entity, 5 + i);
		}
		return new Land(level, owner, heard, halls);
	}

	/** A player who keeps every chat line they're shown. */
	static ServerPlayer listener(GameTestHelper helper, List<Component> seen, String name) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
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

	/** The hall's screen, its minecart pressed and the Realm tab opened: the way a player gets there. */
	static ChoiceMenu realmTab(GameTestHelper helper, Land land, int hall, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, land.hall(hall));
		menu.press(VillageHallScreen.ROUTES, player);
		ItemStack tab = menu.icon(TradePage.Tab.REALM.slot());
		helper.assertTrue(tab.is(Items.GOLDEN_HELMET), "no Realm tab: " + tab);
		menu.press(TradePage.Tab.REALM.slot(), player);
		helper.assertTrue(TradePage.marks(menu.icon(TradePage.Tab.REALM.slot())).contains(TradePage.OPEN), "the Realm tab didn't open");
		return menu;
	}

	static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	static boolean heard(Land land, String line) {
		return land.heard().stream().anyMatch(c -> c.getString().equals(line));
	}

	static boolean chronicled(Land land, int hall, String line) {
		return land.entity(hall).chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.REALM && e.text().getString().equals(line));
	}

	/** Founds the realm at hall 0 through its tab and returns the menu. */
	static ChoiceMenu found(GameTestHelper helper, Land land) {
		ChoiceMenu menu = realmTab(helper, land, 0, land.owner());
		ItemStack button = menu.icon(Realms.FOUND);
		helper.assertTrue(button.is(Items.GOLDEN_HELMET) && button.getHoverName().getString().equals("Found a realm"), "no Found button: " + button);
		menu.press(Realms.FOUND, land.owner());
		helper.assertTrue(land.data().realmOf(land.at(0)) != null, "no realm founded");
		return menu;
	}

	/** Found a realm, add one's own village and a friend's from the tab; the hall's name icon and the chronicle say so. */
	//$ gametest_ticks_batch AREA '100' '"realmFound"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmFound")
	public void foundARealmAndAddOnesOwnVillages(GameTestHelper helper) {
		Land land = land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.HAMLET);
		// a friend's village: its owner made our player a friend
		UUID friend = UUID.randomUUID();
		land.entity(2).setOwner(friend, "Friend");
		Realms.round(land.level(), land.hall(2), land.entity(2), 3);
		Friends.get(land.level().getServer()).add(friend, land.owner().getUUID(), "Owner");
		Leftovers.after(helper, () -> Friends.get(land.level().getServer()).remove(friend, land.owner().getUUID()));

		ChoiceMenu menu = found(helper, land);
		String title = "the Barony of " + land.name(0);
		helper.assertTrue(heard(land, land.name(0) + " has founded " + title + "!"), "no founding line: " + land.heard());
		helper.assertTrue(chronicled(land, 0, "realm-owner founded " + title + ", with this village as its capital"), "no chronicle line: " + land.entity(0).chronicle());
		helper.assertTrue(menu.icon(Realms.TITLE).getHoverName().getString().equals(title), "the tab's title: " + menu.icon(Realms.TITLE).getHoverName().getString());
		helper.assertTrue(menu.icon(Realms.MEMBERS).is(Items.GOLDEN_HELMET) && lore(menu.icon(Realms.MEMBERS)).contains("The capital"), "the capital has no crown");
		// both of the player's other villages are offered, nearest first
		ItemStack add = menu.icon(Realms.MEMBERS + 1);
		helper.assertTrue(add.is(Items.LIME_BANNER) && add.getHoverName().getString().equals("Add " + land.name(1)), "no Add for the own village: " + add);
		helper.assertTrue(menu.icon(Realms.MEMBERS + 2).getHoverName().getString().equals("Add " + land.name(2)), "no Add for the friend's village");
		menu.press(Realms.MEMBERS + 1, land.owner());
		helper.assertTrue(heard(land, land.name(1) + " has joined " + title + "."), "no joined line: " + land.heard());
		helper.assertTrue(land.data().realmOf(land.at(1)) != null, "the own village didn't join");
		helper.assertTrue(chronicled(land, 1, "Joined " + title), "the member's chronicle");
		// the list now shows it as a member, and the friend's village still to add
		helper.assertTrue(menu.icon(Realms.MEMBERS + 1).getHoverName().getString().equals(land.name(1)), "the member isn't listed: " + menu.icon(Realms.MEMBERS + 1));
		List<String> member = lore(menu.icon(Realms.MEMBERS + 1));
		helper.assertTrue(member.contains("A Village") && member.contains("6 villagers, wellbeing 0%") && member.contains("Owner: Owner")
			&& member.contains("Click to let it go"), "the member's lines: " + member);
		menu.press(Realms.MEMBERS + 2, land.owner());
		helper.assertTrue(land.data().realmOf(land.at(2)) != null, "the friend's village didn't join");
		// the hall's name icon
		List<String> capital = lore(VillageHallScreen.forTest(land.owner(), land.hall(0)).icon(VillageHallScreen.NAME));
		String county = "the County of " + land.name(0); // three villages, the capital a Town
		helper.assertTrue(capital.contains("Capital of " + county), "the capital's name icon: " + capital);
		List<String> member1 = lore(VillageHallScreen.forTest(land.owner(), land.hall(1)).icon(VillageHallScreen.NAME));
		helper.assertTrue(member1.contains("In " + county), "a member's name icon: " + member1);
		// a member's tab shows the realm too, without the Found button or anything to add
		ChoiceMenu fromMember = realmTab(helper, land, 1, land.owner());
		helper.assertTrue(fromMember.icon(Realms.TITLE).getHoverName().getString().equals(county), "a member's tab");
		helper.assertTrue(fromMember.icon(Realms.LEAVE).getHoverName().getString().equals("Leave the realm"), "no Leave");
		helper.succeed();
	}

	/** Only a Town or better founds a realm; "What next?" suggests it to a Town in none. */
	//$ gametest_ticks_batch AREA '100' '"realmRank"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmRank")
	public void onlyATownFoundsARealm(GameTestHelper helper) {
		Land land = land(helper, VillageRanks.Rank.VILLAGE);
		helper.assertTrue(VillageAdvice.tips(land.level(), land.hall(0)).stream().noneMatch(t -> t.key().equals("realm")), "a Village told to found a realm");
		ChoiceMenu menu = realmTab(helper, land, 0, land.owner());
		helper.assertTrue(lore(menu.icon(Realms.FOUND)).contains(land.name(0) + " must be a Town to found a realm; it is a Village."), "why not: " + lore(menu.icon(Realms.FOUND)));
		menu.press(Realms.FOUND, land.owner());
		helper.assertTrue(land.data().realmOf(land.at(0)) == null, "a Village founded a realm");
		helper.assertTrue(heard(land, land.name(0) + " must be a Town to found a realm; it is a Village."), "no refusal: " + land.heard());
		land.entity(0).setRank(VillageRanks.Rank.TOWN);
		VillageAdvice.Tip tip = VillageAdvice.tips(land.level(), land.hall(0)).stream().filter(t -> t.key().equals("realm")).findFirst().orElse(null);
		helper.assertTrue(tip != null && tip.title().getString().equals("Found a realm")
			&& tip.how().getString().equals("A Town may found a realm on the hall's Trade page, Realm tab, and add your other villages to it"), "the tip: " + tip);
		// a stranger may read the tab but not found one
		List<Component> theirs = new ArrayList<>();
		ServerPlayer stranger = listener(helper, theirs, "stranger");
		ChoiceMenu strangers = realmTab(helper, land, 0, stranger);
		strangers.press(Realms.FOUND, stranger);
		helper.assertTrue(land.data().realmOf(land.at(0)) == null && theirs.stream().anyMatch(c -> c.getString().equals("Only Owner and their friends can found a realm from " + land.name(0) + ".")),
			"a stranger founded one: " + theirs);
		menu = found(helper, land);
		helper.assertTrue(VillageAdvice.tips(land.level(), land.hall(0)).stream().noneMatch(t -> t.key().equals("realm")), "still suggested in a realm");
		helper.assertTrue(Realms.found(land.level(), land.hall(0), land.owner()).refusal() == Realms.Refusal.IN_REALM, "founded twice");
		helper.succeed();
	}

	/** A stranger's village isn't offered and can't be added; nor one too far, already taken, or past the cap; a stranger can't add to our realm. */
	//$ gametest_ticks_batch AREA '100' '"realmStranger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmStranger")
	public void cantAddAStrangersVillage(GameTestHelper helper) {
		Land land = land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		List<Component> theirs = new ArrayList<>();
		ServerPlayer stranger = listener(helper, theirs, "stranger");
		land.entity(1).setOwner(stranger.getUUID(), "stranger");
		Realms.round(land.level(), land.hall(1), land.entity(1), 4);
		ChoiceMenu menu = found(helper, land);
		helper.assertTrue(menu.icon(Realms.MEMBERS + 1).getHoverName().getString().equals("Add " + land.name(2)), "the stranger's village is offered: "
			+ menu.icon(Realms.MEMBERS + 1).getHoverName().getString());
		Realms.Result result = Realms.add(land.level(), land.hall(0), land.at(1), land.owner());
		helper.assertTrue(result.refusal() == Realms.Refusal.STRANGER && result.message().getString().equals(land.name(1) + " isn't yours or a friend's."),
			"a stranger's village: " + result);
		helper.assertTrue(land.data().realmOf(land.at(1)) == null, "the stranger's village joined");
		// the stranger can't add their own village to our realm either
		result = Realms.add(land.level(), land.hall(0), land.at(1), stranger);
		helper.assertTrue(result.refusal() == Realms.Refusal.NOT_YOURS && result.message().getString().equals("Only Owner and their friends can rule the Barony of " + land.name(0) + "."),
			"the stranger ruled our realm: " + result.message().getString());
		// too far: a village of ours seen 3,000 blocks off
		GlobalPos far = GlobalPos.of(land.level().dimension(), land.hall(0).offset(3000, 0, 0));
		land.data().putKnown(new RealmData.Known(far, Component.literal("Faraway"), Optional.of(land.owner().getUUID()), "Owner", "town", 9, 50, 0));
		result = Realms.add(land.level(), land.hall(0), far, land.owner());
		helper.assertTrue(result.refusal() == Realms.Refusal.TOO_FAR && result.message().getString().equals("Faraway is more than 2048 blocks from the capital."), "too far: " + result);
		// the cap
		Realms.MAX_MEMBERS = 1;
		result = Realms.add(land.level(), land.hall(0), land.at(2), land.owner());
		helper.assertTrue(result.refusal() == Realms.Refusal.FULL, "past the cap: " + result);
		Realms.MAX_MEMBERS = 12;
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(2), land.owner()).ok(), "our own village");
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(2), land.owner()).refusal() == Realms.Refusal.TAKEN, "added twice");
		helper.succeed();
	}

	/** A member's owner leaves from its tab; the capital lets one go by clicking it; a stranger can do neither; the capital can't be let go. */
	//$ gametest_ticks_batch AREA '100' '"realmLeave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmLeave")
	public void leaveAndLetGo(GameTestHelper helper) {
		Land land = land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		found(helper, land);
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).ok() && Realms.add(land.level(), land.hall(0), land.at(2), land.owner()).ok(), "adding");
		String title = "the County of " + land.name(0);
		List<Component> theirs = new ArrayList<>();
		ServerPlayer stranger = listener(helper, theirs, "stranger");
		ChoiceMenu strangers = realmTab(helper, land, 1, stranger);
		strangers.press(Realms.LEAVE, stranger);
		helper.assertTrue(land.data().realmOf(land.at(1)) != null, "a stranger took a member out");
		helper.assertTrue(theirs.stream().anyMatch(c -> c.getString().equals("Only Owner and their friends can take " + land.name(1) + " out of its realm.")), "why not: " + theirs);
		// the member's owner leaves from its tab
		ChoiceMenu member = realmTab(helper, land, 1, land.owner());
		member.press(Realms.LEAVE, land.owner());
		helper.assertTrue(land.data().realmOf(land.at(1)) == null, "the member didn't leave");
		helper.assertTrue(heard(land, land.name(1) + " has left " + title + "."), "no left line: " + land.heard());
		helper.assertTrue(chronicled(land, 1, "Left " + title), "the leaver's chronicle");
		helper.assertTrue(member.icon(Realms.FOUND).getHoverName().getString().equals("Found a realm"), "the tab after leaving: " + member.icon(Realms.FOUND));
		helper.assertTrue("barony".equals(land.data().realmOf(land.at(0)).rank()), "two villages left: a Barony");
		// the capital lets the other go by clicking it
		ChoiceMenu capital = realmTab(helper, land, 0, land.owner());
		helper.assertTrue(capital.icon(Realms.MEMBERS + 1).getHoverName().getString().equals(land.name(2)), "member 2 not listed");
		capital.press(Realms.MEMBERS + 1, land.owner());
		helper.assertTrue(land.data().realmOf(land.at(2)) == null, "not let go");
		helper.assertTrue(heard(land, land.name(2) + " has been let go from the Barony of " + land.name(0) + "."), "no let go line: " + land.heard());
		helper.assertTrue(chronicled(land, 2, "Let go from the Barony of " + land.name(0)), "the let-go's chronicle");
		Realms.Result result = Realms.letGo(land.level(), land.hall(0), land.at(0), land.owner());
		helper.assertTrue(result.refusal() == Realms.Refusal.CAPITAL && result.message().getString().equals("The capital can't be let go; give up the crown instead."), "the capital let go");
		helper.succeed();
	}

	/** The ranks: Barony, County (3, a Town capital), Duchy (5, a City), Kingdom (8, two Cities); a rank up brings fireworks, a chat line and the title. */
	//$ gametest_ticks_batch AREA '100' '"realmRanks"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmRanks")
	public void ranksAreCountedWithTheRankUp(GameTestHelper helper) {
		VillageRanks.Rank h = VillageRanks.Rank.HAMLET;
		VillageRanks.Rank t = VillageRanks.Rank.TOWN;
		VillageRanks.Rank c = VillageRanks.Rank.CITY;
		helper.assertTrue(Realms.count(1, c, 1) == Realms.Rank.BARONY && Realms.count(2, c, 1) == Realms.Rank.BARONY, "a Barony");
		helper.assertTrue(Realms.count(3, t, 0) == Realms.Rank.COUNTY && Realms.count(3, VillageRanks.Rank.VILLAGE, 0) == Realms.Rank.BARONY, "a County needs a Town capital");
		helper.assertTrue(Realms.count(5, c, 1) == Realms.Rank.DUCHY && Realms.count(7, t, 3) == Realms.Rank.COUNTY, "a Duchy needs a City capital");
		helper.assertTrue(Realms.count(8, c, 2) == Realms.Rank.KINGDOM && Realms.count(12, c, 1) == Realms.Rank.DUCHY, "a Kingdom needs two Cities");
		Land land = land(helper, c, h, t, h, h, h, h, c, h);
		found(helper, land);
		String name = land.name(0);
		AABB over = new AABB(land.hall(0)).inflate(8);
		land.level().getEntitiesOfClass(FireworkRocketEntity.class, over).forEach(e -> e.discard());
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "a second");
		helper.assertTrue(land.level().getEntitiesOfClass(FireworkRocketEntity.class, over).isEmpty(), "fireworks without a rank up");
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(2), land.owner()).ok(), "a third");
		helper.assertTrue("county".equals(land.data().realmOf(land.at(0)).rank()), "three with a City capital: a County");
		helper.assertTrue(land.level().getEntitiesOfClass(FireworkRocketEntity.class, over).size() == 3, "no fireworks over the capital");
		helper.assertTrue(heard(land, "Rejoice! the Barony of " + name + " has risen to the County of " + name + "."), "no rank-up line: " + land.heard());
		helper.assertTrue(chronicled(land, 0, "The realm rose to the County of " + name), "no rank-up chronicle");
		helper.assertTrue(VillageHallScreen.forTest(land.owner(), land.hall(1)).icon(VillageHallScreen.NAME) != null
			&& lore(VillageHallScreen.forTest(land.owner(), land.hall(1)).icon(VillageHallScreen.NAME)).contains("In the County of " + name), "the new title on a member");
		for (int i = 3; i <= 4; i++) {
			helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(i), land.owner()).ok(), "member " + i);
		}
		helper.assertTrue("duchy".equals(land.data().realmOf(land.at(0)).rank()), "five with a City capital: a Duchy");
		helper.assertTrue(heard(land, "Rejoice! the County of " + name + " has risen to the Duchy of " + name + "."), "no Duchy line");
		for (int i = 5; i <= 6; i++) {
			helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(i), land.owner()).ok(), "member " + i);
		}
		helper.assertTrue("duchy".equals(land.data().realmOf(land.at(0)).rank()), "seven: still a Duchy");
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(7), land.owner()).ok(), "the second City");
		helper.assertTrue("kingdom".equals(land.data().realmOf(land.at(0)).rank()), "eight with two Cities: a Kingdom");
		helper.assertTrue(heard(land, "Rejoice! the Duchy of " + name + " has risen to the Kingdom of " + name + "."), "no Kingdom line");
		// a rank lower: the title changes, no fireworks
		land.level().getEntitiesOfClass(FireworkRocketEntity.class, over).forEach(e -> e.discard());
		helper.assertTrue(Realms.letGo(land.level(), land.hall(0), land.at(7), land.owner()).ok(), "letting the City go");
		helper.assertTrue("duchy".equals(land.data().realmOf(land.at(0)).rank()), "back to a Duchy");
		helper.assertTrue(land.level().getEntitiesOfClass(FireworkRocketEntity.class, over).isEmpty(), "fireworks for a rank down");
		// the hall's round counts the rank again: the capital falls to a Town
		land.entity(0).setRank(t);
		Realms.round(land.level(), land.hall(0), land.entity(0), 30);
		helper.assertTrue("county".equals(land.data().realmOf(land.at(0)).rank()), "a Town capital's realm is a County at most");
		helper.succeed();
	}

	/** A broken member hall leaves; a broken capital passes the crown to the biggest Town, then, with none left, the realm ends. */
	//$ gametest_ticks_batch AREA '100' '"realmHallBroken"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmHallBroken")
	public void theCapitalsHallBroken(GameTestHelper helper) {
		Land land = land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		found(helper, land);
		for (int i = 1; i <= 4; i++) {
			helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(i), land.owner()).ok(), "member " + i);
		}
		UUID id = land.data().realmOf(land.at(0)).id();
		String capitalName = land.name(0);
		String heir = land.name(3); // the Towns are 2 (7 villagers) and 3 (8): the bigger takes it
		String member4 = land.name(4);
		// a member's hall broken: it leaves
		land.level().destroyBlock(land.hall(4), false);
		helper.assertTrue(!land.data().realm(id).has(land.at(4)), "a broken member is still in: " + land.level().getBlockState(land.hall(4)) + " " + land.data().realm(id));
		helper.assertTrue(heard(land, member4 + "'s hall is gone: it has left the County of " + capitalName + "."), "no member-lost line: " + land.heard());
		// the capital's hall broken: the crown passes
		land.level().destroyBlock(land.hall(0), false);
		RealmData.Realm realm = land.data().realm(id);
		helper.assertTrue(realm != null && realm.capital().equals(land.at(3)), "the crown didn't pass to the biggest Town: " + realm);
		helper.assertTrue(realm.members().get(0).hall().equals(land.at(3)) && realm.members().size() == 3, "the members: " + realm.members());
		helper.assertTrue(heard(land, "The crown of the County of " + capitalName + " passes to " + heir + ": it is now the County of " + heir + "."), "no crown line: " + land.heard());
		helper.assertTrue(chronicled(land, 1, "The crown passed to " + heir + ": the realm is now the County of " + heir), "no crown chronicle");
		// again: Town 2 takes it
		land.level().destroyBlock(land.hall(3), false);
		realm = land.data().realm(id);
		helper.assertTrue(realm != null && realm.capital().equals(land.at(2)) && "barony".equals(realm.rank()), "the crown to the last Town: " + realm);
		// and once more: only a Hamlet is left, the realm ends
		land.level().destroyBlock(land.hall(2), false);
		helper.assertTrue(land.data().realm(id) == null && land.data().realmOf(land.at(1)) == null, "the realm didn't end");
		helper.assertTrue(heard(land, "the Barony of " + land.name(2) + " has ended: its capital is gone and no Town is left to take the crown.")
			|| land.heard().stream().anyMatch(c -> c.getString().endsWith("has ended: its capital is gone and no Town is left to take the crown.")), "no ended line: " + land.heard());
		helper.assertTrue(chronicled(land, 1, "the Barony of " + land.name(2) + " ended") || land.entity(1).chronicle().stream()
			.anyMatch(e -> e.kind() == Chronicle.Kind.REALM && e.text().getString().endsWith(" ended")), "no ended chronicle");
		helper.succeed();
	}

	/** A Name Tag renames the realm and a banner sets its arms, from the tab; the realm survives save and reload; an older file loads empty. */
	//$ gametest_ticks_batch AREA '100' '"realmSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmSave")
	public void nameArmsAndSaveAndReload(GameTestHelper helper) {
		Land land = land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET);
		ChoiceMenu menu = found(helper, land);
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "adding");
		// a plain click on the title does nothing; a named Name Tag renames
		menu.press(Realms.TITLE, land.owner());
		helper.assertTrue(land.data().realmOf(land.at(0)).name().isEmpty(), "renamed with nothing");
		ItemStack tag = new ItemStack(Items.NAME_TAG, 2);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal("Eldmark"));
		menu.setCarried(tag);
		menu.press(Realms.TITLE, land.owner());
		helper.assertTrue(menu.icon(Realms.TITLE).getHoverName().getString().equals("the Barony of Eldmark"), "not renamed: " + menu.icon(Realms.TITLE).getHoverName().getString());
		helper.assertTrue(menu.getCarried().getCount() == 1, "the Name Tag wasn't used up");
		helper.assertTrue(heard(land, "the Barony of " + land.name(0) + " is now the Barony of Eldmark."), "no renamed line: " + land.heard());
		ItemStack plain = new ItemStack(Items.NAME_TAG);
		menu.setCarried(plain);
		menu.press(Realms.TITLE, land.owner());
		helper.assertTrue(heard(land, "Click with a Name Tag named in an anvil to rename the realm."), "an unnamed tag");
		// a banner sets the arms
		ItemStack banner = new ItemStack(Items.RED_BANNER);
		menu.setCarried(banner);
		menu.press(Realms.ARMS, land.owner());
		helper.assertTrue(menu.icon(Realms.ARMS).is(Items.RED_BANNER) && menu.icon(Realms.ARMS).getHoverName().getString().equals("The realm's arms"), "no arms: " + menu.icon(Realms.ARMS));
		helper.assertTrue(menu.getCarried().getCount() == 1, "the banner was used up");
		VillageBanners.Colours arms = Realms.arms(land.level().getServer(), land.at(1));
		helper.assertTrue(arms != null && arms.base() == DyeColor.RED, "a member's arms (what riders' shields will show): " + arms);
		helper.assertTrue(heard(land, "the Barony of Eldmark has new arms."), "no arms line");
		// a stranger can't
		List<Component> theirs = new ArrayList<>();
		ServerPlayer stranger = listener(helper, theirs, "stranger");
		helper.assertTrue(Realms.setArms(land.level(), land.hall(0), stranger, new ItemStack(Items.BLUE_BANNER)).refusal() == Realms.Refusal.NOT_YOURS, "a stranger set the arms");
		// save and reload
		RealmData data = land.data();
		RealmData back = RealmData.load(data.save(new CompoundTag(), land.level().registryAccess()), land.level().registryAccess());
		helper.assertTrue(back.realms().equals(data.realms()), "the realms changed on reload: " + data.realms() + " -> " + back.realms());
		RealmData.Realm realm = back.realmOf(land.at(1));
		helper.assertTrue(realm != null && realm.capital().equals(land.at(0)) && realm.members().size() == 2 && realm.name().map(Component::getString).orElse("").equals("Eldmark")
			&& realm.arms().map(VillageBanners.Colours::base).orElse(null) == DyeColor.RED, "the realm after reload: " + realm);
		helper.assertTrue(back.known(land.at(1)) != null && back.known(land.at(1)).villagers() == 6, "what the round saw, after reload");
		RealmData old = RealmData.load(new CompoundTag(), land.level().registryAccess());
		helper.assertTrue(old.realms().isEmpty() && old.knownVillages().isEmpty(), "an older file has realms");
		helper.succeed();
	}

	/** Colonies join their mother's realm: those founded before it when it's founded, and a new one on arrival. */
	//$ gametest_ticks_batch AREA '100' '"realmColonies"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmColonies")
	public void coloniesJoinTheirMothersRealm(GameTestHelper helper) {
		Land land = land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		RealmData data = land.data();
		data.recordFounded(land.at(0), land.at(1), 1);
		found(helper, land);
		helper.assertTrue(data.realmOf(land.at(1)) != null, "an older colony didn't join at the founding");
		helper.assertTrue(chronicled(land, 1, "Joined the Barony of " + land.name(0)), "the colony's chronicle");
		// a colony of the colony, founded later (what the arrival calls)
		data.recordFounded(land.at(1), land.at(2), 2);
		Realms.colonyFounded(land.level(), land.at(1), land.at(2));
		helper.assertTrue(data.realmOf(land.at(2)) != null && "county".equals(data.realmOf(land.at(0)).rank()), "the new colony didn't join: " + data.realmOf(land.at(0)));
		helper.succeed();
	}

	/** With {@code realms} off: no Realm tab, nothing founded, no line or tip; the realm stays saved and comes back. */
	//$ gametest_ticks_batch AREA '100' '"realmSwitch"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmSwitch")
	public void theSwitchOff(GameTestHelper helper) {
		Land land = land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.TOWN);
		found(helper, land);
		Realms.ENABLED = false;
		helper.assertTrue(!TradePage.tabs().contains(TradePage.Tab.REALM), "a Realm tab while off");
		helper.assertTrue(Realms.hallLine(land.level(), land.hall(0)) == null, "the name icon's line while off");
		helper.assertTrue(VillageAdvice.tips(land.level(), land.hall(1)).stream().noneMatch(t -> t.key().equals("realm")), "a tip while off");
		Realms.Result result = Realms.found(land.level(), land.hall(1), land.owner());
		helper.assertTrue(result.refusal() == Realms.Refusal.OFF && result.message().getString().equals("Realms are turned off on this server."), "founded while off");
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).refusal() == Realms.Refusal.OFF, "added while off");
		helper.assertTrue(Realms.arms(land.level().getServer(), land.at(0)) == null, "arms while off");
		helper.assertTrue(land.data().realmOf(land.at(0)) != null, "the realm wasn't kept");
		Realms.ENABLED = true;
		helper.assertTrue(TradePage.tabs().contains(TradePage.Tab.REALM) && Realms.hallLine(land.level(), land.hall(0)) != null, "the realm didn't come back");
		helper.succeed();
	}
}
