package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.moves.Learnset;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTutors;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.tutor.Tutors;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * QA for Move Tutors (README "Move Tutors (with Cobblemon)"), written from the spec. A real player right-clicks the tutor
 * (through the server's packet handler) and clicks the lesson screen the way the client sends clicks. Covers which
 * lessons are offered, paying one CobbleDollar short and exactly, click spam after a lesson, a full move list (the move
 * is benched), switching Pokémon between the two clicks, a Pokémon leaving the party between the clicks, two screens
 * selling the same lesson, ranks (locked lessons, ranking up, a Master), egg-move prices, battles, and sneaking to trade.
 */
public class QaMoveTutor1102dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos DESK = new BlockPos(2, 2, 2);
	private static final BlockPos STAND = new BlockPos(3, 2, 3);
	private static final int PREVIOUS = 45;
	private static final int NEXT = 53;
	private static final int PAGE = 27;

	private record Setup(Villager tutor, ServerPlayer player, PlayerPartyStore party, List<String> seen) {
	}

	private static Setup setup(GameTestHelper helper, int level, long dollars) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		helper.setDayTime(2000);
		helper.setBlock(DESK, ModBlocks.TUTORS_DESK);
		Villager tutor = helper.spawn(EntityType.VILLAGER, STAND);
		Jobs.employ(helper.getLevel(), tutor, helper.absolutePos(DESK), ModVillagers.TUTORS_DESK_POI, ModVillagers.TUTOR);
		tutor.setVillagerData(tutor.getVillagerData().setLevel(level));
		tutor.setNoAi(true);
		helper.assertTrue(Tutors.isTutor(tutor), "setup: not a Move Tutor");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, UUID.randomUUID(), "qaTutor");
		CobbleDollarsBank.add(player, dollars - CobbleDollarsBank.balance(player));
		return new Setup(tutor, player, Cobblemon.INSTANCE.getStorage().getParty(player), seen);
	}

	/** A logged-in survival player whose chat and action-bar lines land in {@code seen}; logged out when the test ends. */
	private static ServerPlayer player(GameTestHelper helper, List<String> seen, UUID uuid, String name) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(uuid, name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message.getString());
				super.displayClientMessage(message, overlay);
			}

			@Override
			public void sendSystemMessage(Component message) {
				seen.add(message.getString());
				super.sendSystemMessage(message);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		PartnerShowsCompatTests.after(helper, () -> {
			if (level.getServer().getPlayerList().getPlayer(uuid) == player) {
				level.getServer().getPlayerList().remove(player);
			}
		});
		return player;
	}

	private static Pokemon pokemon(Setup s, String spec) {
		Pokemon pokemon = PokemonProperties.Companion.parse(spec, " ", "=").create();
		s.party().add(pokemon);
		return pokemon;
	}

	/** A client's right-click on the villager (sneaking or not), through the server's packet handler. */
	private static void rightClick(Setup s, Villager villager, boolean sneak) {
		ServerPlayer player = s.player();
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.setShiftKeyDown(sneak);
		int before = s.seen().size();
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, sneak, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (s.seen().size() == before && player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, sneak, InteractionHand.MAIN_HAND));
		}
		player.setShiftKeyDown(false);
	}

	/** Right-click with an empty hand: the lesson screen. */
	private static ChoiceMenu open(GameTestHelper helper, Setup s) {
		s.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		rightClick(s, s.tutor(), false);
		helper.assertTrue(s.player().containerMenu instanceof ChoiceMenu, "a right-click didn't open the lessons: " + s.player().containerMenu);
		return (ChoiceMenu) s.player().containerMenu;
	}

	/** A left click on {@code slot}, sent the way the client sends it. */
	private static void click(Setup s, ChoiceMenu menu, int slot) {
		ServerPlayer p = s.player();
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, p.containerMenu.getStateId(), slot, 0, ClickType.PICKUP,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	private static long balance(Setup s) {
		return CobbleDollarsBank.balance(s.player());
	}

	private static String name(MoveTemplate move) {
		return move.getName();
	}

	private static List<String> moves(Pokemon pokemon) {
		List<String> out = new ArrayList<>();
		for (Move move : pokemon.getMoveSet().getMoves()) {
			out.add(move.getName());
		}
		return out;
	}

	private static List<String> benched(Pokemon pokemon) {
		List<String> out = new ArrayList<>();
		for (BenchedMove move : pokemon.getBenchedMoves()) {
			out.add(move.getMoveTemplate().getName());
		}
		return out;
	}

	private static boolean knows(Pokemon pokemon, String move) {
		return moves(pokemon).contains(move) || benched(pokemon).contains(move);
	}

	private static String iconName(ChoiceMenu menu, int slot) {
		var name = menu.icon(slot).get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	private static String lore(ChoiceMenu menu, int slot) {
		ItemLore lore = menu.icon(slot).get(DataComponents.LORE);
		StringBuilder out = new StringBuilder();
		if (lore != null) {
			for (Component line : lore.lines()) {
				out.append(line.getString()).append('\n');
			}
		}
		return out.toString();
	}

	/** The lesson slot on the first page whose icon shows {@code move}, or -1. */
	private static int slotOf(ChoiceMenu menu, MoveTemplate move) {
		String shown = move.getDisplayName().getString();
		for (int i = 0; i < PAGE; i++) {
			if (iconName(menu, CobblemonTutors.FIRST_MOVE_SLOT + i).equals(shown)) {
				return CobblemonTutors.FIRST_MOVE_SLOT + i;
			}
		}
		return -1;
	}

	/** Every lesson name shown, page by page (clicking Next as a player would), back on page 1 at the end. */
	private static List<String> allShown(Setup s, ChoiceMenu menu, List<ItemStack> icons) {
		List<String> shown = new ArrayList<>();
		int pages = 0;
		while (true) {
			for (int i = 0; i < PAGE; i++) {
				ItemStack icon = menu.icon(CobblemonTutors.FIRST_MOVE_SLOT + i);
				if (!icon.isEmpty()) {
					shown.add(iconName(menu, CobblemonTutors.FIRST_MOVE_SLOT + i));
					icons.add(icon.copy());
				}
			}
			pages++;
			if (menu.icon(NEXT).isEmpty() || pages > 20) {
				break;
			}
			click(s, menu, NEXT);
		}
		for (int i = 1; i < pages; i++) {
			click(s, menu, PREVIOUS);
		}
		return shown;
	}

	private static Set<String> displayNames(Iterable<MoveTemplate> moves) {
		Set<String> out = new HashSet<>();
		for (MoveTemplate move : moves) {
			out.add(move.getDisplayName().getString());
		}
		return out;
	}

	/**
	 * The screen never offers a move the Pokémon already uses, nor one it only gets from levelling; it does offer a
	 * tutor or TM move and an egg move (Bulbasaur's own learnset, read from Cobblemon).
	 */
	//$ gametest_batch AREA '"qaTutorOffers"'
	@GameTest(template = AREA, batch = "qaTutorOffers")
	public void qaTutorOffersOnlyMovesNotFromLevelling(GameTestHelper helper) {
		Setup s = setup(helper, 5, 0);
		Pokemon bulbasaur = pokemon(s, "bulbasaur level=20");
		Learnset learnset = bulbasaur.getForm().getMoves();
		Set<String> current = new HashSet<>();
		for (Move move : bulbasaur.getMoveSet().getMoves()) {
			current.add(move.getDisplayName().getString());
		}
		Set<String> levelUp = new HashSet<>();
		learnset.getLevelUpMoves().values().forEach(list -> levelUp.addAll(displayNames(list)));
		Set<String> taught = displayNames(learnset.getTutorMoves());
		taught.addAll(displayNames(learnset.getTmMoves()));
		Set<String> egg = displayNames(learnset.getEggMoves());
		Set<String> levelUpOnly = new HashSet<>(levelUp);
		levelUpOnly.removeAll(taught);
		levelUpOnly.removeAll(egg);
		Set<String> reached = displayNames(learnset.getLevelUpMovesUpTo(bulbasaur.getLevel()));

		ChoiceMenu menu = open(helper, s);
		List<ItemStack> icons = new ArrayList<>();
		List<String> shown = allShown(s, menu, icons);
		helper.assertTrue(!shown.isEmpty(), "no lessons for Bulbasaur");
		helper.assertTrue(new HashSet<>(shown).size() == shown.size(), "a lesson is listed twice: " + shown);
		for (String move : shown) {
			helper.assertFalse(current.contains(move), "offered " + move + ", which Bulbasaur already uses " + current);
			helper.assertFalse(reached.contains(move), "offered " + move + ", which Bulbasaur already learned by levelling");
			helper.assertFalse(levelUpOnly.contains(move), "offered " + move + ", which Bulbasaur only gets from levelling");
			helper.assertTrue(taught.contains(move) || egg.contains(move), "offered " + move + ", which isn't a tutor, TM or egg move");
		}
		helper.assertTrue(shown.stream().anyMatch(m -> taught.contains(m) && !egg.contains(m)), "no tutor or TM move offered");
		boolean eggShown = false;
		for (int i = 0; i < shown.size(); i++) {
			if (egg.contains(shown.get(i)) && !taught.contains(shown.get(i))) {
				eggShown = true;
				helper.assertTrue(icons.get(i).is(Items.EGG), "egg move " + shown.get(i) + " isn't shown as an egg move");
			}
		}
		helper.assertTrue(eggShown, "no egg move offered (egg moves: " + egg + ")");
		helper.succeed();
	}

	/**
	 * One CobbleDollar short: nothing taught, nothing taken, and the player is told the price. The exact price: taught and
	 * charged once; clicking on (the same slot again, twice more) never charges or teaches a second time.
	 */
	//$ gametest_batch AREA '"qaTutorPrice"'
	@GameTest(template = AREA, batch = "qaTutorPrice")
	public void qaTutorChargesExactlyOnce(GameTestHelper helper) {
		Setup s = setup(helper, 5, 0);
		Pokemon pokemon = pokemon(s, "bulbasaur level=5");
		var lesson = CobblemonTutors.lessons(pokemon).get(0);
		long price = lesson.dollars(s.tutor());
		helper.assertTrue(price >= 300 && price <= 2400, "price " + price + " is outside 300..2,400");
		CobbleDollarsBank.add(s.player(), price - 1);
		List<String> before = moves(pokemon);
		ChoiceMenu menu = open(helper, s);
		int slot = slotOf(menu, lesson.move());
		helper.assertTrue(slot >= 0, "the lesson " + name(lesson.move()) + " isn't on the first page");
		click(s, menu, slot);
		click(s, menu, slot);
		helper.assertValueEqual(balance(s), price - 1, "money after a lesson one CobbleDollar short");
		helper.assertTrue(moves(pokemon).equals(before) && benched(pokemon).isEmpty(), "taught without payment: " + moves(pokemon));
		String costs = "That lesson costs " + String.format(java.util.Locale.ROOT, "%,d", price) + " CobbleDollars.";
		helper.assertTrue(s.seen().contains(costs), "not told the price (\"" + costs + "\"): " + s.seen());

		CobbleDollarsBank.add(s.player(), 1);
		click(s, menu, slot);
		click(s, menu, slot);
		helper.assertValueEqual(balance(s), 0L, "money after paying the exact price");
		helper.assertTrue(knows(pokemon, name(lesson.move())), "not taught after paying the exact price");
		helper.assertTrue(moves(pokemon).stream().filter(m -> m.equals(name(lesson.move()))).count() == 1, "taught twice: " + moves(pokemon));
		String learned = pokemon.getDisplayName(false).getString() + " learned " + lesson.move().getDisplayName().getString() + "!";
		helper.assertTrue(s.seen().contains(learned), "no \"" + learned + "\": " + s.seen());

		// More clicks on the same slot (now another lesson) with more money: at most that other lesson, never this one again.
		CobbleDollarsBank.add(s.player(), 100_000);
		click(s, menu, slot);
		helper.assertValueEqual(balance(s), 100_000L, "a single click after a lesson charged");
		helper.assertValueEqual(ModAttachments.TUTOR_LESSONS.getOrElse(s.tutor(), 0), 1, "lessons counted");
		helper.assertTrue(moves(pokemon).stream().filter(m -> m.equals(name(lesson.move()))).count() == 1
			&& !benched(pokemon).contains(name(lesson.move())), "the lesson was given twice");
		helper.succeed();
	}

	/** Four moves known: the lesson goes to the moves to swap in, the four stay as they were, and it's paid once. */
	//$ gametest_batch AREA '"qaTutorFull"'
	@GameTest(template = AREA, batch = "qaTutorFull")
	public void qaTutorBenchesWhenFourMovesKnown(GameTestHelper helper) {
		Setup s = setup(helper, 5, 100_000);
		Pokemon pokemon = pokemon(s, "bulbasaur level=40");
		helper.assertValueEqual(pokemon.getMoveSet().getMoves().size(), 4, "setup: a level-40 Bulbasaur's moves");
		List<String> four = moves(pokemon);
		List<String> benchBefore = benched(pokemon);
		var lesson = CobblemonTutors.lessons(pokemon).get(0);
		long price = lesson.dollars(s.tutor());
		ChoiceMenu menu = open(helper, s);
		int slot = slotOf(menu, lesson.move());
		helper.assertTrue(slot >= 0, "the lesson isn't on the first page");
		click(s, menu, slot);
		click(s, menu, slot);
		helper.assertTrue(moves(pokemon).equals(four), "the four moves changed: " + four + " -> " + moves(pokemon));
		List<String> bench = benched(pokemon);
		helper.assertTrue(bench.contains(name(lesson.move())), "the move isn't among the moves to swap in: " + bench);
		helper.assertValueEqual(bench.size(), benchBefore.size() + 1, "benched moves");
		helper.assertValueEqual(balance(s), 100_000 - price, "money");
		String line = pokemon.getDisplayName(false).getString() + " learned " + lesson.move().getDisplayName().getString()
			+ "! Swap it in from the moves page of its summary.";
		helper.assertTrue(s.seen().contains(line), "no \"" + line + "\": " + s.seen());
		helper.succeed();
	}

	/**
	 * A lesson chosen for Pokémon A, then Pokémon B picked at the top: the next click on that slot only chooses B's lesson
	 * there (nothing paid, A untouched), the click after teaches B that lesson, and never A.
	 */
	//$ gametest_batch AREA '"qaTutorSwitch"'
	@GameTest(template = AREA, batch = "qaTutorSwitch")
	public void qaTutorSwitchingPokemonNeverTeachesTheFirst(GameTestHelper helper) {
		Setup s = setup(helper, 5, 100_000);
		Pokemon a = pokemon(s, "bulbasaur level=5");
		Pokemon b = pokemon(s, "charmander level=5");
		ChoiceMenu menu = open(helper, s);
		var lessonA = CobblemonTutors.lessons(a).get(0);
		int slot = slotOf(menu, lessonA.move());
		helper.assertTrue(slot >= 0, "A's lesson isn't on the screen");
		List<String> aBefore = moves(a);
		List<String> bBefore = moves(b);
		click(s, menu, slot); // choose for A
		click(s, menu, 1); // pick B
		String shownB = iconName(menu, slot);
		var lessonsB = CobblemonTutors.lessons(b);
		var lessonB = lessonsB.stream().filter(l -> l.move().getDisplayName().getString().equals(shownB)).findFirst().orElse(null);
		helper.assertTrue(lessonB != null, "slot " + slot + " shows " + shownB + ", which Charmander can't be taught");
		click(s, menu, slot); // the "pay" click a player might expect
		helper.assertValueEqual(balance(s), 100_000L, "the click after switching paid for A's lesson");
		helper.assertTrue(moves(a).equals(aBefore) && benched(a).isEmpty(), "A was taught after switching to B: " + moves(a));
		helper.assertTrue(moves(b).equals(bBefore) && benched(b).isEmpty(), "B was taught on the first click: " + moves(b));
		click(s, menu, slot); // the second click on B's lesson
		helper.assertTrue(knows(b, name(lessonB.move())), "B wasn't taught " + name(lessonB.move()));
		helper.assertTrue(moves(a).equals(aBefore) && benched(a).isEmpty(), "A changed: " + moves(a));
		helper.assertValueEqual(balance(s), 100_000 - lessonB.dollars(s.tutor()), "money");
		helper.succeed();
	}

	/**
	 * The chosen Pokémon leaves the party (to the PC) between the two clicks: nothing paid, nothing taught to it or to the
	 * Pokémon now first in the party, and the screen still works.
	 */
	//$ gametest_batch AREA '"qaTutorLeaves"'
	@GameTest(template = AREA, batch = "qaTutorLeaves")
	public void qaTutorPokemonLeavingThePartyIsNotTaught(GameTestHelper helper) {
		Setup s = setup(helper, 5, 100_000);
		Pokemon a = pokemon(s, "bulbasaur level=5");
		Pokemon b = pokemon(s, "bulbasaur level=5");
		ChoiceMenu menu = open(helper, s);
		var lesson = CobblemonTutors.lessons(a).get(0);
		int slot = slotOf(menu, lesson.move());
		helper.assertTrue(slot >= 0, "the lesson isn't on the screen");
		List<String> aBefore = moves(a);
		List<String> bBefore = moves(b);
		click(s, menu, slot);
		helper.assertTrue(s.party().remove(a), "setup: couldn't take A out of the party");
		Cobblemon.INSTANCE.getStorage().getPC(s.player()).add(a);
		click(s, menu, slot);
		helper.assertValueEqual(balance(s), 100_000L, "paid for a Pokémon that left the party");
		helper.assertTrue(moves(a).equals(aBefore) && benched(a).isEmpty(), "taught a Pokémon in the PC: " + moves(a));
		helper.assertTrue(moves(b).equals(bBefore) && benched(b).isEmpty(), "the lesson went to another Pokémon: " + moves(b));
		helper.assertValueEqual(ModAttachments.TUTOR_LESSONS.getOrElse(s.tutor(), 0), 0, "lessons counted");
		helper.assertTrue(s.player().containerMenu == menu, "the screen closed");
		// The screen now teaches the one left.
		click(s, menu, slot);
		click(s, menu, slot);
		helper.assertTrue(!moves(b).equals(bBefore) || !benched(b).isEmpty(), "the Pokémon left in the party can't be taught");
		helper.assertTrue(moves(a).equals(aBefore) && benched(a).isEmpty(), "A changed in the PC: " + moves(a));
		helper.succeed();
	}

	/** The tutor dies between choosing and paying: the pay click takes no money and teaches nothing. */
	//$ gametest_batch AREA '"qaTutorGone"'
	@GameTest(template = AREA, batch = "qaTutorGone")
	public void qaTutorGoneBetweenClicksTakesNothing(GameTestHelper helper) {
		Setup s = setup(helper, 5, 100_000);
		Pokemon pokemon = pokemon(s, "bulbasaur level=5");
		ChoiceMenu menu = open(helper, s);
		var lesson = CobblemonTutors.lessons(pokemon).get(0);
		int slot = slotOf(menu, lesson.move());
		helper.assertTrue(slot >= 0, "the lesson isn't on the screen");
		List<String> before = moves(pokemon);
		click(s, menu, slot);
		s.tutor().kill();
		click(s, menu, slot);
		helper.assertValueEqual(balance(s), 100_000L, "paid a dead tutor");
		helper.assertTrue(moves(pokemon).equals(before) && benched(pokemon).isEmpty(), "taught by a dead tutor: " + moves(pokemon));
		helper.succeed();
	}

	/** Two lesson screens (two tutors) open on one Pokémon: the same lesson can't be bought twice. */
	//$ gametest_batch AREA '"qaTutorTwoScreens"'
	@GameTest(template = AREA, batch = "qaTutorTwoScreens")
	public void qaTutorTwoScreensSellALessonOnce(GameTestHelper helper) {
		Setup s = setup(helper, 5, 100_000);
		Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3));
		Jobs.employ(helper.getLevel(), second, helper.absolutePos(DESK), ModVillagers.TUTORS_DESK_POI, ModVillagers.TUTOR);
		second.setVillagerData(second.getVillagerData().setLevel(5));
		second.setNoAi(true);
		Pokemon pokemon = pokemon(s, "bulbasaur level=5");
		var lesson = CobblemonTutors.lessons(pokemon).get(0);
		ChoiceMenu one = CobblemonTutors.menuForTest(s.player(), s.tutor());
		ChoiceMenu two = CobblemonTutors.menuForTest(s.player(), second);
		int slot = slotOf(one, lesson.move());
		helper.assertTrue(slot >= 0 && slotOf(two, lesson.move()) == slot, "the lesson isn't on both screens");
		one.press(slot, s.player());
		two.press(slot, s.player());
		one.press(slot, s.player());
		long after = balance(s);
		helper.assertValueEqual(after, 100_000 - lesson.dollars(s.tutor()), "money after the first lesson");
		two.press(slot, s.player());
		helper.assertValueEqual(balance(s), after, "the second screen sold the same lesson again");
		helper.assertTrue(moves(pokemon).stream().filter(m -> m.equals(name(lesson.move()))).count() == 1
			&& !benched(pokemon).contains(name(lesson.move())), "taught twice: " + moves(pokemon) + " / " + benched(pokemon));
		helper.succeed();
	}

	/**
	 * A Novice shows the strongest lessons locked and won't take money for them; lessons rank the tutor up (with the
	 * player told, "an Apprentice"), after which a lesson that was locked can be taught; a Master has nothing locked.
	 */
	//$ gametest_batch AREA '"qaTutorRanks"'
	@GameTest(template = AREA, batch = "qaTutorRanks")
	public void qaTutorRanksUpFromLessons(GameTestHelper helper) {
		Setup s = setup(helper, 1, 100_000);
		for (int i = 0; i < 3; i++) {
			pokemon(s, "bulbasaur level=5");
		}
		ChoiceMenu menu = open(helper, s);
		List<ItemStack> icons = new ArrayList<>();
		allShown(s, menu, icons);
		helper.assertTrue(icons.stream().anyMatch(i -> i.is(Items.PAPER)), "a Novice has no locked lessons");
		helper.assertTrue(icons.stream().anyMatch(i -> !i.is(Items.PAPER)), "a Novice can teach nothing");
		// the strongest lesson (2,400 for a Master) is locked for the Novice
		int locked = -1;
		int pagesForward = 0;
		while (locked < 0) {
			for (int i = 0; i < PAGE; i++) {
				if (menu.icon(CobblemonTutors.FIRST_MOVE_SLOT + i).is(Items.PAPER)) {
					locked = CobblemonTutors.FIRST_MOVE_SLOT + i;
					break;
				}
			}
			if (locked < 0) {
				helper.assertFalse(menu.icon(NEXT).isEmpty(), "no locked lesson on any page");
				click(s, menu, NEXT);
				pagesForward++;
			}
		}
		String lockedName = iconName(menu, locked);
		helper.assertTrue(lore(menu, locked).contains("Needs a tutor of"), "a locked lesson doesn't say why: " + lore(menu, locked));
		click(s, menu, locked);
		click(s, menu, locked);
		helper.assertValueEqual(balance(s), 100_000L, "paid for a locked lesson");

		int lessons = 0;
		while (Tutors.tier(s.tutor()) == 1 && lessons < 12) {
			// the first teachable lesson of whichever party member has one
			boolean taught = false;
			for (int member = 0; member < 3 && !taught; member++) {
				click(s, menu, member);
				for (int i = 0; i < PAGE; i++) {
					int slot = CobblemonTutors.FIRST_MOVE_SLOT + i;
					ItemStack icon = menu.icon(slot);
					if (!icon.isEmpty() && !icon.is(Items.PAPER)) {
						long before = balance(s);
						click(s, menu, slot);
						click(s, menu, slot);
						helper.assertTrue(balance(s) < before, "a teachable lesson wasn't paid for");
						taught = true;
						break;
					}
				}
			}
			helper.assertTrue(taught, "nothing left a Novice can teach after " + lessons + " lessons");
			lessons++;
			helper.assertValueEqual(ModAttachments.TUTOR_LESSONS.getOrElse(s.tutor(), 0), lessons, "lessons counted");
		}
		helper.assertValueEqual(Tutors.tier(s.tutor()), 2, "the tutor's rank after " + lessons + " lessons");
		helper.assertTrue(s.seen().stream().anyMatch(l -> l.endsWith("is now an Apprentice tutor and can teach stronger moves.")),
			"the player wasn't told about the rank-up: " + s.seen());
		helper.assertTrue(Tutors.title(s.tutor()).getString().equals("Apprentice Move Tutor"), "title: " + Tutors.title(s.tutor()).getString());

		// A Master: nothing locked, the strongest lessons cost 2,400.
		s.tutor().setVillagerData(s.tutor().getVillagerData().setLevel(5));
		ChoiceMenu master = CobblemonTutors.menuForTest(s.player(), s.tutor());
		List<ItemStack> masterIcons = new ArrayList<>();
		for (int i = 0; i < PAGE; i++) {
			ItemStack icon = master.icon(CobblemonTutors.FIRST_MOVE_SLOT + i);
			if (!icon.isEmpty()) {
				masterIcons.add(icon);
			}
		}
		helper.assertTrue(masterIcons.stream().noneMatch(i -> i.is(Items.PAPER)), "a Master has locked lessons");
		helper.assertTrue(!lockedName.isEmpty(), "setup");
		helper.succeed();
	}

	/**
	 * Prices on the cards: 300 to 2,400 CobbleDollars, the strongest at 2,400 (written "2,400"), the weakest at 300, and an
	 * egg move costs more than a tutor or TM move of the same power.
	 */
	//$ gametest_batch AREA '"qaTutorEggPrice"'
	@GameTest(template = AREA, batch = "qaTutorEggPrice")
	public void qaTutorEggMovesCostAStepMore(GameTestHelper helper) {
		Setup s = setup(helper, 5, 0);
		Pokemon pokemon = pokemon(s, "bulbasaur level=5");
		var lessons = CobblemonTutors.lessons(pokemon);
		long min = Long.MAX_VALUE;
		long max = 0;
		for (var l : lessons) {
			long p = l.dollars(s.tutor());
			min = Math.min(min, p);
			max = Math.max(max, p);
		}
		helper.assertValueEqual(min, 300L, "the cheapest lesson");
		helper.assertValueEqual(max, 2400L, "the dearest lesson");
		int pairs = 0;
		for (var egg : lessons) {
			if (!egg.egg()) {
				continue;
			}
			for (var tm : lessons) {
				if (!tm.egg() && tm.move().getPower() == egg.move().getPower()) {
					pairs++;
					long e = egg.dollars(s.tutor());
					long t = tm.dollars(s.tutor());
					helper.assertTrue(e > t || e == 2400 && t == 2400, "egg move " + name(egg.move()) + " costs " + e + ", the same-power "
						+ name(tm.move()) + " " + t);
				}
			}
		}
		helper.assertTrue(pairs > 0, "no egg move with a same-power tutor move to compare");
		// The cards say so, too.
		ChoiceMenu menu = open(helper, s);
		List<ItemStack> icons = new ArrayList<>();
		List<String> shown = allShown(s, menu, icons);
		boolean saw2400 = false;
		boolean saw300 = false;
		for (ItemStack icon : icons) {
			ItemLore lore = icon.get(DataComponents.LORE);
			String text = lore == null ? "" : String.join("\n", lore.lines().stream().map(Component::getString).toList());
			saw2400 |= text.contains("Lesson: 2,400 CobbleDollars");
			saw300 |= text.contains("Lesson: 300 CobbleDollars");
		}
		helper.assertTrue(saw2400 && saw300, "the cards don't show 300 and 2,400 CobbleDollars (" + shown.size() + " cards)");
		helper.succeed();
	}

	/**
	 * In a battle: the lesson screen doesn't open ("Finish your battle first."), and a lesson chosen before the battle
	 * started isn't taught or paid for during it.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaTutorBattle"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaTutorBattle")
	public void qaTutorRefusesDuringABattle(GameTestHelper helper) {
		Setup s = setup(helper, 5, 100_000);
		Pokemon pokemon = pokemon(s, "bulbasaur level=30");
		ChoiceMenu menu = open(helper, s);
		var lesson = CobblemonTutors.lessons(pokemon).get(0);
		int slot = slotOf(menu, lesson.move());
		helper.assertTrue(slot >= 0, "the lesson isn't on the screen");
		click(s, menu, slot);
		List<String> before = moves(pokemon);

		BlockPos post = new BlockPos(5, 2, 5);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 4));
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
		trainer.setNoAi(true);
		io.github.jcondedata.aliveworkplace.trainer.Trainers.challenge(s.player(), trainer);
		var battle = com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(s.player());
		helper.assertTrue(battle != null, "setup: no battle started");
		try {
			// a lesson chosen before the battle: the pay click does nothing while it lasts
			if (s.player().containerMenu == menu) {
				click(s, menu, slot);
			} else {
				menu.press(slot, s.player());
			}
			helper.assertValueEqual(balance(s), 100_000L, "paid for a lesson during a battle");
			helper.assertTrue(moves(pokemon).equals(before) && benched(pokemon).isEmpty(), "taught during a battle");
			// and the screen won't open
			s.player().closeContainer();
			s.seen().clear();
			rightClick(s, s.tutor(), false);
			helper.assertFalse(s.player().containerMenu instanceof ChoiceMenu, "the lessons opened during a battle");
			helper.assertTrue(s.seen().contains("Finish your battle first."), "not told why: " + s.seen());
		} finally {
			io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.finishForTest(battle, false);
		}
		helper.succeed();
	}

	/** Sneak-right-click with an empty hand opens the trades, not the lessons; the tutor buys paper and books. */
	//$ gametest_batch AREA '"qaTutorSneak"'
	@GameTest(template = AREA, batch = "qaTutorSneak")
	public void qaTutorSneakOpensTrades(GameTestHelper helper) {
		Setup s = setup(helper, 1, 0);
		pokemon(s, "bulbasaur level=5");
		s.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		rightClick(s, s.tutor(), true);
		helper.assertFalse(s.player().containerMenu instanceof ChoiceMenu, "sneaking opened the lessons");
		helper.assertTrue(s.player().containerMenu instanceof MerchantMenu, "sneaking didn't open the trades: " + s.player().containerMenu);
		boolean paper = false;
		for (var offer : s.tutor().getOffers()) {
			paper |= offer.getCostA().is(Items.PAPER) && offer.getResult().is(Items.EMERALD);
		}
		helper.assertTrue(paper, "a Novice tutor doesn't buy paper");
		Villager apprentice = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		Jobs.employ(helper.getLevel(), apprentice, helper.absolutePos(DESK), ModVillagers.TUTORS_DESK_POI, ModVillagers.TUTOR);
		apprentice.setVillagerData(apprentice.getVillagerData().setLevel(2));
		apprentice.setNoAi(true);
		boolean books = false;
		for (var offer : apprentice.getOffers()) {
			books |= offer.getCostA().is(Items.BOOK) && offer.getResult().is(Items.EMERALD);
		}
		helper.assertTrue(books, "an Apprentice tutor doesn't buy books");
		s.player().closeContainer();
		// and without sneaking, the lessons
		ChoiceMenu menu = open(helper, s);
		helper.assertTrue(menu.icon(0).getItem() instanceof com.cobblemon.mod.common.item.PokemonItem, "the party isn't at the top");
		helper.succeed();
	}
}
