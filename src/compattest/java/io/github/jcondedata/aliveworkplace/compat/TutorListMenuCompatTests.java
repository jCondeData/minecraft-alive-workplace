package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTutors;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.tutor.Tutors;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The Move Tutor on the list screen (ROADMAP 42.1), through the player's own clicks: a right-click opens the list menu
 * type (its client copy a ChoiceView); the party is the tab row, each lesson a row with its type, price and a Teach
 * button that turns to Confirm, the money at the top; teaching a move, too little money, a move the Pokémon already
 * knows (gray rows for what the tutor can't teach yet), and the party and the money changing while the screen is open.
 */
public class TutorListMenuCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos DESK = new BlockPos(2, 2, 2);
	private static final BlockPos STAND = new BlockPos(3, 2, 3);
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
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen);
		CobbleDollarsBank.add(player, dollars - CobbleDollarsBank.balance(player));
		return new Setup(tutor, player, Cobblemon.INSTANCE.getStorage().getParty(player), seen);
	}

	/** A logged-in survival player whose chat and action-bar lines land in {@code seen}; logged out when the test ends. */
	private static ServerPlayer player(GameTestHelper helper, List<String> seen) {
		ServerLevel level = helper.getLevel();
		UUID uuid = UUID.randomUUID();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(uuid, "listTutor"), false);
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

	/** A right-click with an empty hand on the tutor, as a client sends it: the lesson screen. */
	private static ChoiceMenu open(GameTestHelper helper, Setup s) {
		ServerPlayer player = s.player();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		Villager tutor = s.tutor();
		player.moveTo(tutor.getX() + 1.5, tutor.getY(), tutor.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(tutor, false, InteractionHand.MAIN_HAND,
			new Vec3(0, tutor.getBbHeight() / 2, 0)));
		if (player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(tutor, false, InteractionHand.MAIN_HAND));
		}
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "a right-click didn't open the lessons: " + player.containerMenu);
		return (ChoiceMenu) player.containerMenu;
	}

	/** A left click on {@code slot}, as the list screen sends it (the chest click on that slot). */
	private static void click(Setup s, ChoiceMenu menu, int slot) {
		ServerPlayer p = s.player();
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, p.containerMenu.getStateId(), slot, 0, ClickType.PICKUP,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	/** The list row whose name is {@code move}'s, or -1. */
	private static int rowOf(ChoiceMenu menu, MoveTemplate move) {
		for (int i = 0; i < PAGE; i++) {
			int slot = CobblemonTutors.FIRST_MOVE_SLOT + i;
			if (menu.icon(slot).getHoverName().getString().equals(move.getDisplayName().getString())) {
				return slot;
			}
		}
		return -1;
	}

	/** The rows' names on the page shown, each checked to be a marked row. */
	private static List<String> rows(GameTestHelper helper, ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < PAGE; i++) {
			int slot = CobblemonTutors.FIRST_MOVE_SLOT + i;
			if (!menu.icon(slot).isEmpty()) {
				ChoiceList.Mark mark = ChoiceList.mark(menu.icon(slot));
				helper.assertTrue(mark != null && mark.kind() == ChoiceList.Kind.ROW, "lesson slot " + slot + " isn't a list row: " + mark);
				out.add(menu.icon(slot).getHoverName().getString());
			}
		}
		return out;
	}

	/** The tabs shown (party slots marked as tabs), by name, and which is open. */
	private static List<String> tabs(GameTestHelper helper, ChoiceMenu menu, int[] open) {
		List<String> out = new ArrayList<>();
		open[0] = -1;
		for (int slot = 0; slot < 9; slot++) {
			if (menu.icon(slot).isEmpty()) {
				continue;
			}
			ChoiceList.Mark mark = ChoiceList.mark(menu.icon(slot));
			helper.assertTrue(mark != null && mark.kind() == ChoiceList.Kind.TAB, "party slot " + slot + " isn't a tab: " + mark);
			if (mark.on()) {
				helper.assertTrue(open[0] < 0, "two tabs are open");
				open[0] = slot;
			}
			out.add(menu.icon(slot).getHoverName().getString());
		}
		return out;
	}

	private static ChoiceList.Info info(GameTestHelper helper, ChoiceMenu menu) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ChoiceList.Info info = ChoiceList.info(menu.icon(slot));
			if (info != null) {
				return info;
			}
		}
		throw new AssertionError("the menu has no line of its own (money, hint, headings)");
	}

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
	}

	private static boolean knows(Pokemon pokemon, String move) {
		for (Move m : pokemon.getMoveSet().getMoves()) {
			if (m.getName().equals(move)) {
				return true;
			}
		}
		for (com.cobblemon.mod.common.api.moves.BenchedMove benched : pokemon.getBenchedMoves()) {
			if (benched.getMoveTemplate().getName().equals(move)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A right-click opens the list type with a ChoiceView client copy; Teach, then Confirm teaches the move and charges
	 * its price once; the money at the top follows; the row goes, as the Pokémon now knows it.
	 */
	//$ gametest_batch AREA '"tutorListTeach"'
	@GameTest(template = AREA, batch = "tutorListTeach")
	public void tutorListTeachesAMove(GameTestHelper helper) {
		Setup s = setup(helper, 5, 10_000);
		Pokemon bulbasaur = pokemon(s, "bulbasaur level=20");
		ChoiceMenu menu = open(helper, s);
		helper.assertTrue(menu.getType() == ModBlocks.CHOICE_LIST_MENU, "the tutor opened as " + menu.getType() + ", not the list");
		var view = ModBlocks.CHOICE_LIST_MENU.create(7, s.player().getInventory());
		helper.assertTrue(view instanceof ChoiceView && view.slots.size() == menu.slots.size(), "the client's copy is " + view);
		int[] open = new int[1];
		helper.assertTrue(tabs(helper, menu, open).size() == 1 && open[0] == 0, "the party's tab row: " + tabs(helper, menu, open));
		ChoiceList.Info info = info(helper, menu);
		helper.assertTrue(info.money().getString().equals(Money.balance(s.player()).getString()), "the money at the top: " + info.money().getString());
		helper.assertTrue(info.columns().size() == 3 && key(info.hint()).equals("message.aliveworkplace.tutor.list_hint"), "headings and hint: " + info);

		var lesson = CobblemonTutors.lessons(bulbasaur).get(0);
		int slot = rowOf(menu, lesson.move());
		helper.assertTrue(slot >= 0, "the first lesson isn't a row");
		ChoiceList.Mark mark = ChoiceList.mark(menu.icon(slot));
		helper.assertTrue(!mark.off() && !mark.on() && key(mark.button()).equals("message.aliveworkplace.tutor.button_teach"), "the row's button: " + mark);
		helper.assertTrue(mark.price().getString().equals(Money.describe(lesson.dollars(s.tutor()), lesson.price(s.tutor())).getString()),
			"the row's price: " + mark.price().getString());
		helper.assertTrue(mark.second().getString().contains(lesson.move().getElementalType().getDisplayName().getString()), "the row's type: " + mark.second().getString());

		click(s, menu, slot);
		mark = ChoiceList.mark(menu.icon(slot));
		helper.assertTrue(mark.on() && key(mark.button()).equals("message.aliveworkplace.tutor.button_confirm"), "after Teach the row isn't waiting for Confirm: " + mark);
		helper.assertValueEqual(CobbleDollarsBank.balance(s.player()), 10_000L, "paid on the first click");
		click(s, menu, slot);
		String name = lesson.move().getName();
		helper.assertTrue(knows(bulbasaur, name), "Bulbasaur didn't learn " + name);
		helper.assertValueEqual(CobbleDollarsBank.balance(s.player()), 10_000L - lesson.dollars(s.tutor()), "the balance after the lesson");
		helper.assertValueEqual(ModAttachments.TUTOR_LESSONS.getOrElse(s.tutor(), 0), 1, "lessons counted");
		helper.assertTrue(s.seen().stream().anyMatch(l -> l.contains(lesson.move().getDisplayName().getString())), "no learned message: " + s.seen());
		helper.assertTrue(info(helper, menu).money().getString().equals(Money.balance(s.player()).getString()), "the money at the top didn't follow the payment");
		helper.assertFalse(rows(helper, menu).contains(lesson.move().getDisplayName().getString()), "the move it now knows is still a row");
		helper.assertTrue(s.player().containerMenu == menu, "the screen closed");
		helper.succeed();
	}

	/** One CobbleDollar short: the price is red, Confirm teaches nothing and takes nothing, the player is told the price. */
	//$ gametest_batch AREA '"tutorListPoor"'
	@GameTest(template = AREA, batch = "tutorListPoor")
	public void tutorListTooLittleMoney(GameTestHelper helper) {
		Setup s = setup(helper, 5, 0);
		Pokemon bulbasaur = pokemon(s, "bulbasaur level=20");
		var lesson = CobblemonTutors.lessons(bulbasaur).get(0);
		long price = lesson.dollars(s.tutor());
		CobbleDollarsBank.add(s.player(), price - 1);
		ChoiceMenu menu = open(helper, s);
		int slot = rowOf(menu, lesson.move());
		helper.assertTrue(slot >= 0, "the lesson isn't a row");
		ChoiceList.Mark mark = ChoiceList.mark(menu.icon(slot));
		helper.assertTrue(!mark.off() && mark.price().getStyle().getColor() != null
			&& mark.price().getStyle().getColor().getValue() == ChatFormatting.RED.getColor(), "a price the player can't pay isn't red: " + mark.price());
		click(s, menu, slot);
		click(s, menu, slot);
		helper.assertFalse(knows(bulbasaur, lesson.move().getName()), "taught without payment");
		helper.assertValueEqual(CobbleDollarsBank.balance(s.player()), price - 1, "the balance");
		String told = Component.translatable("message.aliveworkplace.tutor.too_poor", Money.describe(price, lesson.price(s.tutor()))).getString();
		helper.assertTrue(s.seen().contains(told), "the player wasn't told the price (" + told + "): " + s.seen());
		helper.assertTrue(s.player().containerMenu == menu, "the screen closed on a refusal");
		helper.succeed();
	}

	/**
	 * A move the Pokémon already knows is never a row (nor one it gets by levelling); a Novice's lessons beyond its rank
	 * are gray rows without a button, with the rank they need, and clicking one does nothing.
	 */
	//$ gametest_batch AREA '"tutorListKnows"'
	@GameTest(template = AREA, batch = "tutorListKnows")
	public void tutorListAlreadyKnownAndGrayRows(GameTestHelper helper) {
		Setup s = setup(helper, 1, 10_000);
		Pokemon charmander = pokemon(s, "charmander level=20");
		var known = CobblemonTutors.lessons(charmander).get(0);
		CobblemonTutors.teach(charmander, known.move());
		ChoiceMenu menu = open(helper, s);
		List<String> rows = rows(helper, menu);
		helper.assertTrue(!rows.isEmpty(), "no rows for Charmander");
		helper.assertFalse(rows.contains(known.move().getDisplayName().getString()), "offered " + known.move().getName() + ", which it already knows");
		for (Move move : charmander.getMoveSet().getMoves()) {
			helper.assertFalse(rows.contains(move.getDisplayName().getString()), "offered " + move.getName() + ", which it already uses");
		}
		int gray = -1;
		for (int i = 0; i < PAGE; i++) {
			ChoiceList.Mark mark = ChoiceList.mark(menu.icon(CobblemonTutors.FIRST_MOVE_SLOT + i));
			if (mark != null && mark.off()) {
				gray = CobblemonTutors.FIRST_MOVE_SLOT + i;
				helper.assertTrue(mark.button() == null && key(mark.price()).equals("message.aliveworkplace.tutor.needs_tier_short"),
					"a gray row has a button or no rank: " + mark);
				break;
			}
		}
		helper.assertTrue(gray >= 0, "a Novice shows no gray row for Charmander");
		click(s, menu, gray);
		click(s, menu, gray);
		helper.assertValueEqual(CobbleDollarsBank.balance(s.player()), 10_000L, "a gray row charged");
		helper.assertValueEqual(ModAttachments.TUTOR_LESSONS.getOrElse(s.tutor(), 0), 0, "a gray row taught");
		helper.succeed();
	}

	/**
	 * The party changes while the screen is open, with no click: a Pokémon joining gets its tab; the open one leaving
	 * (to the PC) moves the screen to the one left, its lessons, and a lesson chosen for the one that left isn't taught;
	 * money paid in shows at the top.
	 */
	//$ gametest_batch AREA '"tutorListParty"'
	@GameTest(template = AREA, batch = "tutorListParty")
	public void tutorListFollowsThePartyWhileOpen(GameTestHelper helper) {
		Setup s = setup(helper, 5, 10_000);
		Pokemon a = pokemon(s, "bulbasaur level=10");
		ChoiceMenu menu = open(helper, s);
		int[] open = new int[1];
		helper.assertValueEqual(tabs(helper, menu, open).size(), 1, "tabs before");
		var lessonA = CobblemonTutors.lessons(a).get(0);
		int slotA = rowOf(menu, lessonA.move());
		helper.assertTrue(slotA >= 0, "A's lesson isn't a row");
		click(s, menu, slotA); // Teach: waiting for Confirm
		Pokemon b = pokemon(s, "charmander level=10");
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(s.player().containerMenu == menu, "the screen closed");
			helper.assertValueEqual(tabs(helper, menu, open).size(), 2, "tabs after a Pokémon joined");
			helper.assertValueEqual(open[0], 0, "the open tab moved when another joined");
			helper.assertTrue(ChoiceList.mark(menu.icon(slotA)).on(), "the chosen lesson was dropped when another Pokémon joined");
			helper.assertTrue(s.party().remove(a), "setup: couldn't take A out of the party");
			Cobblemon.INSTANCE.getStorage().getPC(s.player()).add(a);
			CobbleDollarsBank.add(s.player(), 500);
			helper.runAfterDelay(3, () -> {
				List<String> tabs = tabs(helper, menu, open);
				helper.assertTrue(tabs.size() == 1 && open[0] == 0 && tabs.get(0).contains(b.getDisplayName(false).getString()),
					"the tabs after A left: " + tabs);
				List<String> expected = new ArrayList<>();
				var lessonsB = CobblemonTutors.lessons(b);
				for (int i = 0; i < Math.min(PAGE, lessonsB.size()); i++) {
					expected.add(lessonsB.get(i).move().getDisplayName().getString());
				}
				helper.assertTrue(rows(helper, menu).equals(expected), "the rows aren't B's lessons: " + rows(helper, menu));
				helper.assertTrue(info(helper, menu).money().getString().equals(Money.balance(s.player()).getString()),
					"the money at the top didn't follow: " + info(helper, menu).money().getString());
				for (int i = 0; i < PAGE; i++) {
					ChoiceList.Mark mark = ChoiceList.mark(menu.icon(CobblemonTutors.FIRST_MOVE_SLOT + i));
					helper.assertTrue(mark == null || !mark.on(), "a lesson is still waiting for Confirm after its Pokémon left");
				}
				click(s, menu, slotA); // the click that would have paid for A's lesson
				helper.assertValueEqual(CobbleDollarsBank.balance(s.player()), 10_500L, "paid after the Pokémon left");
				helper.assertTrue(CobblemonTutors.lessons(a).stream().anyMatch(l -> l.move().getName().equals(lessonA.move().getName())),
					"A was taught in the PC");
				helper.succeed();
			});
		});
	}
}
