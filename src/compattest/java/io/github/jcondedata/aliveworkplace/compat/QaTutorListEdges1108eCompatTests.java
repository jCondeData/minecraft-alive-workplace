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
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
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
import net.minecraft.network.chat.TextColor;
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
 * QA (lane-e-1108-2212) for the Move Tutor's list screen (ROADMAP 42.1), from its spec and wiki page (list-screen.md:
 * "If your party changes while the screen is open ... or your money does, the tabs, rows and prices follow at once,
 * without a click"; "a lesson waiting for Confirm stays chosen unless its Pokémon is gone"; prices "green when you can pay
 * it, red when you can't"), through the player's own right-click and chest clicks: the party reordered between Teach and
 * Confirm, the money spent elsewhere between them, money paid in turning a red price green, and the move learned
 * elsewhere while the lesson waits for Confirm.
 */
public class QaTutorListEdges1108eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos DESK = new BlockPos(2, 2, 2);
	private static final BlockPos STAND = new BlockPos(3, 2, 3);
	private static final int PAGE = 27;

	private record Setup(Villager tutor, ServerPlayer player, PlayerPartyStore party, List<String> seen) {
	}

	private static Setup setup(GameTestHelper helper, long dollars) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		helper.setDayTime(2000);
		helper.setBlock(DESK, ModBlocks.TUTORS_DESK);
		Villager tutor = helper.spawn(EntityType.VILLAGER, STAND);
		Jobs.employ(helper.getLevel(), tutor, helper.absolutePos(DESK), ModVillagers.TUTORS_DESK_POI, ModVillagers.TUTOR);
		tutor.setVillagerData(tutor.getVillagerData().setLevel(5));
		tutor.setNoAi(true);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen);
		CobbleDollarsBank.add(player, dollars - CobbleDollarsBank.balance(player));
		return new Setup(tutor, player, Cobblemon.INSTANCE.getStorage().getParty(player), seen);
	}

	private static ServerPlayer player(GameTestHelper helper, List<String> seen) {
		ServerLevel level = helper.getLevel();
		UUID uuid = UUID.randomUUID();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(uuid, "qaTutorList"), false);
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
		helper.assertTrue(player.containerMenu.getType() == ModBlocks.CHOICE_LIST_MENU, "the tutor didn't open the list screen");
		return (ChoiceMenu) player.containerMenu;
	}

	private static void click(Setup s, ChoiceMenu menu, int slot) {
		ServerPlayer p = s.player();
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, p.containerMenu.getStateId(), slot, 0, ClickType.PICKUP,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	private static int rowOf(ChoiceMenu menu, MoveTemplate move) {
		for (int i = 0; i < PAGE; i++) {
			int slot = CobblemonTutors.FIRST_MOVE_SLOT + i;
			if (menu.icon(slot).getHoverName().getString().equals(move.getDisplayName().getString())) {
				return slot;
			}
		}
		return -1;
	}

	/** The open tab's name, or null. */
	private static String openTab(ChoiceMenu menu) {
		for (int slot = 0; slot < 9; slot++) {
			ChoiceList.Mark mark = ChoiceList.mark(menu.icon(slot));
			if (mark != null && mark.kind() == ChoiceList.Kind.TAB && mark.on()) {
				return menu.icon(slot).getHoverName().getString();
			}
		}
		return null;
	}

	/** The row waiting for Confirm, or -1 (at most one). */
	private static int waiting(GameTestHelper helper, ChoiceMenu menu) {
		int found = -1;
		for (int i = 0; i < PAGE; i++) {
			ChoiceList.Mark mark = ChoiceList.mark(menu.icon(CobblemonTutors.FIRST_MOVE_SLOT + i));
			if (mark != null && mark.on()) {
				helper.assertTrue(found < 0, "two rows wait for Confirm");
				found = CobblemonTutors.FIRST_MOVE_SLOT + i;
			}
		}
		return found;
	}

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
	}

	private static boolean colored(Component c, ChatFormatting color) {
		TextColor got = c.getStyle().getColor();
		if (got != null) {
			return got.getValue() == color.getColor();
		}
		for (Component sibling : c.getSiblings()) {
			if (sibling.getStyle().getColor() != null) {
				return sibling.getStyle().getColor().getValue() == color.getColor();
			}
		}
		return false;
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

	private static String tabName(Pokemon pokemon) {
		return pokemon.getDisplayName(false).getString();
	}

	/**
	 * Teach on Bulbasaur (the first tab), then the player reorders the party so Charmander is first: Bulbasaur is still
	 * there, so its tab stays open and its lesson stays chosen; one more click on that row (Confirm) teaches Bulbasaur and
	 * charges once, and Charmander, now in the first place, is taught nothing.
	 */
	//$ gametest_batch AREA '"qaTutorListSwap"'
	@GameTest(template = AREA, batch = "qaTutorListSwap")
	public void aPartyReorderedBetweenTeachAndConfirmTeachesTheChosenPokemon(GameTestHelper helper) {
		Setup s = setup(helper, 10_000);
		Pokemon a = pokemon(s, "bulbasaur level=10");
		Pokemon b = pokemon(s, "charmander level=10");
		ChoiceMenu menu = open(helper, s);
		var lesson = CobblemonTutors.lessons(a).get(0);
		String move = lesson.move().getName();
		long price = lesson.dollars(s.tutor());
		int slot = rowOf(menu, lesson.move());
		helper.assertTrue(slot >= 0, "Bulbasaur's first lesson isn't a row");
		helper.assertTrue(openTab(menu) != null && openTab(menu).contains(tabName(a)), "Bulbasaur's tab isn't open: " + openTab(menu));
		boolean bCould = CobblemonTutors.lessons(b).stream().anyMatch(l -> l.move().getName().equals(move));
		click(s, menu, slot); // Teach
		helper.assertValueEqual(waiting(helper, menu), slot, "the row waiting for Confirm after Teach");
		s.party().swap(0, 1);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(s.player().containerMenu == menu, "the screen closed when the party was reordered");
			helper.assertTrue(openTab(menu) != null && openTab(menu).contains(tabName(a)),
				"the open tab left Bulbasaur when the party was reordered: " + openTab(menu));
			int now = waiting(helper, menu);
			helper.assertTrue(now >= 0, "the lesson chosen for Bulbasaur was dropped though Bulbasaur is still in the party");
			helper.assertTrue(menu.icon(now).getHoverName().getString().equals(lesson.move().getDisplayName().getString()),
				"the row waiting for Confirm is " + menu.icon(now).getHoverName().getString() + ", not " + lesson.move().getDisplayName().getString());
			helper.assertTrue(key(ChoiceList.mark(menu.icon(now)).button()).equals("message.aliveworkplace.tutor.button_confirm"),
				"the waiting row's button isn't Confirm");
			helper.assertValueEqual(CobbleDollarsBank.balance(s.player()), 10_000L, "charged before Confirm");
			click(s, menu, now); // Confirm
			helper.assertTrue(knows(a, move), "Bulbasaur didn't learn " + move + " after Confirm");
			if (!bCould) {
				helper.assertFalse(knows(b, move), "Charmander learned Bulbasaur's lesson " + move);
			}
			helper.assertValueEqual(CobbleDollarsBank.balance(s.player()), 10_000L - price, "the balance after one lesson");
			helper.assertValueEqual(ModAttachments.TUTOR_LESSONS.getOrElse(s.tutor(), 0), 1, "lessons counted");
			helper.succeed();
		});
	}

	/**
	 * Exactly the price, Teach, then the player spends a CobbleDollar elsewhere: the price turns red while the screen is
	 * open, the lesson stays chosen (its Pokémon is still there), and Confirm refuses (too poor, the price named),
	 * teaches nothing, takes nothing and keeps the screen open.
	 */
	//$ gametest_batch AREA '"qaTutorListSpent"'
	@GameTest(template = AREA, batch = "qaTutorListSpent")
	public void moneySpentBetweenTeachAndConfirmTeachesNothing(GameTestHelper helper) {
		Setup s = setup(helper, 0);
		Pokemon a = pokemon(s, "bulbasaur level=20");
		var lesson = CobblemonTutors.lessons(a).get(0);
		long price = lesson.dollars(s.tutor());
		CobbleDollarsBank.add(s.player(), price);
		ChoiceMenu menu = open(helper, s);
		int slot = rowOf(menu, lesson.move());
		helper.assertTrue(slot >= 0, "the lesson isn't a row");
		helper.assertTrue(colored(ChoiceList.mark(menu.icon(slot)).price(), ChatFormatting.GREEN),
			"a price the player can pay exactly isn't green: " + ChoiceList.mark(menu.icon(slot)).price());
		click(s, menu, slot); // Teach
		CobbleDollarsBank.add(s.player(), -1);
		helper.runAfterDelay(3, () -> {
			int now = rowOf(menu, lesson.move());
			helper.assertTrue(now >= 0, "the lesson's row went when the money dropped");
			ChoiceList.Mark mark = ChoiceList.mark(menu.icon(now));
			helper.assertTrue(colored(mark.price(), ChatFormatting.RED), "the price didn't turn red when the money dropped: " + mark.price());
			helper.assertTrue(info(menu).getString().equals(Money.balance(s.player()).getString()),
				"the money at the top didn't follow: " + info(menu).getString());
			helper.assertTrue(mark.on(), "the lesson chosen was dropped though its Pokémon is still there");
			click(s, menu, now); // Confirm
			helper.assertFalse(knows(a, lesson.move().getName()), "taught one CobbleDollar short");
			helper.assertValueEqual(CobbleDollarsBank.balance(s.player()), price - 1, "the balance after a refused Confirm");
			helper.assertValueEqual(ModAttachments.TUTOR_LESSONS.getOrElse(s.tutor(), 0), 0, "a refused lesson counted");
			String told = Component.translatable("message.aliveworkplace.tutor.too_poor", Money.describe(price, lesson.price(s.tutor()))).getString();
			helper.assertTrue(s.seen().contains(told), "the player wasn't told the price (" + told + "): " + s.seen());
			helper.assertTrue(s.player().containerMenu == menu, "the screen closed on a refusal");
			helper.succeed();
		});
	}

	/** One CobbleDollar short (a red price), then one paid in with the screen open: the price turns green, no click needed. */
	//$ gametest_batch AREA '"qaTutorListPaidIn"'
	@GameTest(template = AREA, batch = "qaTutorListPaidIn")
	public void moneyPaidInWhileOpenTurnsARedPriceGreen(GameTestHelper helper) {
		Setup s = setup(helper, 0);
		Pokemon a = pokemon(s, "squirtle level=20");
		var lesson = CobblemonTutors.lessons(a).get(0);
		long price = lesson.dollars(s.tutor());
		CobbleDollarsBank.add(s.player(), price - 1);
		ChoiceMenu menu = open(helper, s);
		int slot = rowOf(menu, lesson.move());
		helper.assertTrue(slot >= 0, "the lesson isn't a row");
		helper.assertTrue(colored(ChoiceList.mark(menu.icon(slot)).price(), ChatFormatting.RED), "one short isn't red");
		CobbleDollarsBank.add(s.player(), 1);
		helper.runAfterDelay(3, () -> {
			int now = rowOf(menu, lesson.move());
			helper.assertTrue(now >= 0, "the lesson's row went when money was paid in");
			helper.assertTrue(colored(ChoiceList.mark(menu.icon(now)).price(), ChatFormatting.GREEN),
				"the price didn't turn green when the player could pay it: " + ChoiceList.mark(menu.icon(now)).price());
			helper.assertTrue(info(menu).getString().equals(Money.balance(s.player()).getString()),
				"the money at the top didn't follow: " + info(menu).getString());
			click(s, menu, now);
			click(s, menu, now);
			helper.assertTrue(knows(a, lesson.move().getName()), "the lesson wasn't taught once the player could pay");
			helper.assertValueEqual(CobbleDollarsBank.balance(s.player()), 0L, "the balance after the lesson");
			helper.succeed();
		});
	}

	/**
	 * Teach, then the Pokémon learns that move some other way (another tutor's screen, a TM) before Confirm: Confirm takes
	 * nothing and counts no lesson, as the tutor never sells a move the Pokémon already knows.
	 */
	//$ gametest_batch AREA '"qaTutorListLearnedElsewhere"'
	@GameTest(template = AREA, batch = "qaTutorListLearnedElsewhere")
	public void aMoveLearnedElsewhereBeforeConfirmIsNotSold(GameTestHelper helper) {
		Setup s = setup(helper, 10_000);
		Pokemon a = pokemon(s, "bulbasaur level=20");
		var lesson = CobblemonTutors.lessons(a).get(0);
		ChoiceMenu menu = open(helper, s);
		int slot = rowOf(menu, lesson.move());
		helper.assertTrue(slot >= 0, "the lesson isn't a row");
		click(s, menu, slot); // Teach
		CobblemonTutors.teach(a, lesson.move());
		helper.assertTrue(knows(a, lesson.move().getName()), "setup: the move wasn't learned elsewhere");
		helper.runAfterDelay(3, () -> {
			int seen = s.seen().size();
			click(s, menu, slot); // Confirm (or whatever row now sits there: one click is never a sale)
			helper.assertValueEqual(CobbleDollarsBank.balance(s.player()), 10_000L, "charged for a move the Pokémon already knew");
			helper.assertValueEqual(ModAttachments.TUTOR_LESSONS.getOrElse(s.tutor(), 0), 0, "a lesson counted for a move already known");
			helper.assertFalse(s.seen().subList(seen, s.seen().size()).stream().anyMatch(l -> l.contains(lesson.move().getDisplayName().getString())),
				"told the Pokémon learned " + lesson.move().getName() + " again: " + s.seen());
			helper.succeed();
		});
	}

	private static Component info(ChoiceMenu menu) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ChoiceList.Info info = ChoiceList.info(menu.icon(slot));
			if (info != null) {
				return info.money();
			}
		}
		throw new AssertionError("the menu has no line of its own (money, hint, headings)");
	}
}
