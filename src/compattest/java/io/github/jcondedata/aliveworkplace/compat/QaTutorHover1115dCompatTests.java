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
 * QA (lane D, 2026-11-15) for the Move Tutor's list screen (ROADMAP 42.1, wiki list-screen.md: the tooltip of a row is
 * "as the chest showed it"): hovering a lesson's row tells what a click does. A row waiting for Confirm says "Click again
 * to teach <move> to <Pokemon>", every other row "Click to choose", and after Confirm no row is left saying click again.
 * Kills two CobblemonTutors:282 survivors of a mutation pass ({@code pending && pokemon != null} to {@code ||}: every row
 * asks to confirm; to {@code pokemon == null}: the waiting row only says "Click to choose").
 */
public class QaTutorHover1115dCompatTests implements FabricGameTest {
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
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(uuid, "qaTutorHover"), false);
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

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
	}

	/** The last line of the row's tooltip (what a click does), as its translation key. */
	private static String clickLine(ChoiceMenu menu, int slot) {
		var lore = menu.icon(slot).get(net.minecraft.core.component.DataComponents.LORE);
		if (lore == null || lore.lines().isEmpty()) {
			return "no tooltip";
		}
		return key(lore.lines().get(lore.lines().size() - 1));
	}

	private static List<Integer> teachableRows(ChoiceMenu menu) {
		List<Integer> rows = new ArrayList<>();
		for (int i = 0; i < PAGE; i++) {
			int slot = CobblemonTutors.FIRST_MOVE_SLOT + i;
			ChoiceList.Mark mark = ChoiceList.mark(menu.icon(slot));
			if (mark != null && mark.button() != null) {
				rows.add(slot);
			}
		}
		return rows;
	}

	//$ gametest_batch AREA '"qaTutorHover1115d"'
	@GameTest(template = AREA, batch = "qaTutorHover1115d")
	public void aRowsTooltipSaysClickAgainOnlyWhileItWaitsForConfirm(GameTestHelper helper) {
		Setup s = setup(helper, 10_000);
		Pokemon bulbasaur = pokemon(s, "bulbasaur level=10");
		ChoiceMenu menu = open(helper, s);
		List<Integer> rows = teachableRows(menu);
		helper.assertTrue(rows.size() >= 2, "setup: fewer than two lessons Bulbasaur can be taught: " + rows.size());
		for (int slot : rows) {
			helper.assertValueEqual(clickLine(menu, slot), "message.aliveworkplace.tutor.click", "row " + menu.icon(slot).getHoverName().getString() + "'s tooltip before any click");
		}
		var lesson = CobblemonTutors.lessons(bulbasaur).get(0);
		int chosen = rowOf(menu, lesson.move());
		helper.assertTrue(rows.contains(chosen), "setup: Bulbasaur's first lesson isn't a teachable row");
		click(s, menu, chosen); // Teach
		for (int slot : teachableRows(menu)) {
			String line = clickLine(menu, slot);
			if (menu.icon(slot).getHoverName().getString().equals(lesson.move().getDisplayName().getString())) {
				helper.assertValueEqual(line, "message.aliveworkplace.tutor.confirm", "the tooltip of the row waiting for Confirm");
				var lore = menu.icon(slot).get(net.minecraft.core.component.DataComponents.LORE);
				Component last = lore.lines().get(lore.lines().size() - 1);
				Object[] args = ((TranslatableContents) last.getContents()).getArgs();
				helper.assertTrue(args.length == 2 && String.valueOf(args[0] instanceof Component c ? c.getString() : args[0]).equals(lesson.move().getDisplayName().getString())
					&& String.valueOf(args[1] instanceof Component c2 ? c2.getString() : args[1]).equals(bulbasaur.getDisplayName(false).getString()),
					"the Confirm tooltip doesn't name the move and Bulbasaur: " + last.getString());
			} else {
				helper.assertValueEqual(line, "message.aliveworkplace.tutor.click", "row " + menu.icon(slot).getHoverName().getString() + "'s tooltip while another waits for Confirm");
			}
		}
		click(s, menu, rowOf(menu, lesson.move()) >= 0 ? rowOf(menu, lesson.move()) : chosen); // Confirm
		helper.assertTrue(s.seen().stream().anyMatch(m -> m.contains(lesson.move().getDisplayName().getString()) || m.contains("tutor.learned")),
			"setup: Confirm taught nothing: " + s.seen());
		for (int slot : teachableRows(menu)) {
			helper.assertValueEqual(clickLine(menu, slot), "message.aliveworkplace.tutor.click", "row " + menu.icon(slot).getHoverName().getString() + "'s tooltip after Confirm");
		}
		helper.succeed();
	}
}
