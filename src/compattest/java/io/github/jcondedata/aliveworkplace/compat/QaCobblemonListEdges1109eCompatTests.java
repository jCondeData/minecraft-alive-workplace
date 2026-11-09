package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycare;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import io.github.jcondedata.aliveworkplace.ranch.Daycare;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-e-1109-0115) for the Cobblemon menus on the list screen (ROADMAP 42.2), from list-screen.md ("If your party
 * changes while the screen is open ... the rows follow at once, without a click"): the party reordered while a choice
 * waits on the screen, through the player's own right-click and chest clicks. The Daycare's Leave waits for Confirm on
 * the Pokémon it was clicked for, wherever it moves to, and the row that moved into its place is only a Leave; the Daycare
 * Keeper's Pick stays on its Pokémon, and Pair pairs the two the player chose, never the one that took the picked one's
 * place.
 */
public class QaCobblemonListEdges1109eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static ChoiceMenu open(GameTestHelper helper, ServerPlayer player, Villager villager) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND));
		}
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu && player.containerMenu.getType() == ModBlocks.CHOICE_LIST_MENU,
			"a right-click didn't open the list screen: " + player.containerMenu);
		return (ChoiceMenu) player.containerMenu;
	}

	private static void click(ServerPlayer p, ChoiceMenu menu, int slot) {
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, p.containerMenu.getStateId(), slot, 0, ClickType.PICKUP,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	private static ChoiceList.Mark row(GameTestHelper helper, ChoiceMenu menu, int slot) {
		ChoiceList.Mark mark = ChoiceList.mark(menu.icon(slot));
		helper.assertTrue(mark != null && mark.kind() == ChoiceList.Kind.ROW, "slot " + slot + " isn't a list row: " + menu.icon(slot));
		return mark;
	}

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
	}

	private static String name(ChoiceMenu menu, int slot) {
		return menu.icon(slot).getHoverName().getString();
	}

	private static Pokemon add(PlayerPartyStore party, String spec) {
		Pokemon pokemon = PokemonProperties.Companion.parse(spec, " ", "=").create();
		party.add(pokemon);
		return pokemon;
	}

	private static boolean inParty(PlayerPartyStore party, Pokemon pokemon) {
		for (Pokemon p : party) {
			if (p.getUuid().equals(pokemon.getUuid())) {
				return true;
			}
		}
		return false;
	}

	/** Which party slot shows {@code pokemon} (its name), or -1. */
	private static int slotOf(ChoiceMenu menu, int first, Pokemon pokemon) {
		for (int i = 0; i < 6; i++) {
			if (name(menu, first + i).startsWith(pokemon.getDisplayName(false).getString())) {
				return first + i;
			}
		}
		return -1;
	}

	/**
	 * Leave on Charmander (the second row), then the player swaps Charmander and Squirtle: Charmander's row, now third, is
	 * still the one waiting for Confirm; a click where Charmander was (Squirtle's row now) only starts Squirtle's Leave and
	 * leaves nobody; Confirm on Charmander's row leaves Charmander, and Squirtle stays with the player.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaListEdges1109eDaycare"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaListEdges1109eDaycare")
	public void aDaycareLeaveWaitsOnItsPokemonWhenThePartyIsReordered(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setDayTime(2000);
		BlockPos trough = new BlockPos(2, 2, 2);
		helper.setBlock(trough, ModBlocks.FEED_TROUGH);
		Villager rancher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), rancher, helper.absolutePos(trough), ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
		rancher.setNoAi(true);
		ServerPlayer player = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		CobbleDollarsBank.add(player, -CobbleDollarsBank.balance(player));
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		add(party, "bulbasaur level=10");
		Pokemon charmander = add(party, "charmander level=5");
		Pokemon squirtle = add(party, "squirtle level=5");
		ChoiceMenu menu = open(helper, player, rancher);
		int second = CobblemonDaycare.FIRST_PARTY_SLOT + 1;
		helper.assertTrue(name(menu, second).startsWith(charmander.getDisplayName(false).getString()), "setup: the second row is " + name(menu, second));
		click(player, menu, second); // Leave
		helper.assertTrue(row(helper, menu, second).on(), "setup: Leave didn't make Charmander's row wait for Confirm");
		party.swap(1, 2);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(player.containerMenu == menu, "the screen closed when the party was reordered");
			int now = slotOf(menu, CobblemonDaycare.FIRST_PARTY_SLOT, charmander);
			helper.assertTrue(now == CobblemonDaycare.FIRST_PARTY_SLOT + 2, "Charmander's row didn't follow it to the third place: " + now);
			ChoiceList.Mark waiting = row(helper, menu, now);
			helper.assertTrue(waiting.on() && key(waiting.button()).equals("message.aliveworkplace.tutor.button_confirm"),
				"Charmander's moved row isn't waiting for Confirm any more: " + waiting);
			ChoiceList.Mark moved = row(helper, menu, second);
			helper.assertTrue(name(menu, second).startsWith(squirtle.getDisplayName(false).getString()) && !moved.on()
				&& key(moved.button()).equals("screen.aliveworkplace.daycare.button_leave"), "the row in Charmander's old place: " + name(menu, second) + " " + moved);
			click(player, menu, second); // Squirtle's Leave: nobody leaves yet
			helper.assertTrue(inParty(party, squirtle) && inParty(party, charmander) && Daycare.boarders(rancher).isEmpty(),
				"a click where Charmander had been left a Pokémon: boarders " + Daycare.boarders(rancher).size());
			int charmanderNow = slotOf(menu, CobblemonDaycare.FIRST_PARTY_SLOT, charmander);
			click(player, menu, charmanderNow); // Charmander: Leave again (Squirtle's click took the waiting place)
			click(player, menu, slotOf(menu, CobblemonDaycare.FIRST_PARTY_SLOT, charmander)); // Confirm
			helper.assertTrue(!inParty(party, charmander) && inParty(party, squirtle) && Daycare.boarders(rancher).size() == 1,
				"Confirm on Charmander's row didn't leave exactly Charmander: Charmander in party " + inParty(party, charmander)
					+ ", Squirtle in party " + inParty(party, squirtle) + ", boarders " + Daycare.boarders(rancher).size());
			helper.succeed();
		});
	}

	/**
	 * Pick the female Eevee, then the player swaps her with Pikachu: she is still the one picked (her row moved), and Pair
	 * on the male Eevee leaves the two Eevees, never Pikachu, which took her place in the list.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaListEdges1109eKeeper"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaListEdges1109eKeeper")
	public void aKeepersPickStaysOnItsPokemonWhenThePartyIsReordered(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager keeper = DaycareKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		ServerPlayer player = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		add(party, "bulbasaur level=10");
		Pokemon mother = add(party, "eevee gender=female level=20");
		Pokemon father = add(party, "eevee gender=male level=20");
		Pokemon pikachu = add(party, "pikachu gender=female level=20");
		ChoiceMenu menu = open(helper, player, keeper);
		int f = CobblemonDaycareKeeper.FIRST_PARTY_SLOT;
		click(player, menu, f + 1); // Pick the female Eevee
		helper.assertTrue(row(helper, menu, f + 1).on(), "setup: Pick didn't mark the female Eevee");
		party.swap(1, 3); // Pikachu second, the female Eevee fourth
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(player.containerMenu == menu, "the screen closed when the party was reordered");
			helper.assertTrue(name(menu, f + 1).startsWith(pikachu.getDisplayName(false).getString()) && !row(helper, menu, f + 1).on(),
				"Pikachu's row in the female Eevee's old place: " + name(menu, f + 1) + " " + row(helper, menu, f + 1));
			helper.assertTrue(row(helper, menu, f + 3).on() && key(row(helper, menu, f + 3).button()).equals("screen.aliveworkplace.daycare_keeper.button_unpick"),
				"the female Eevee's moved row isn't the one picked: " + name(menu, f + 3) + " " + row(helper, menu, f + 3));
			helper.assertTrue(key(row(helper, menu, f + 2).button()).equals("screen.aliveworkplace.daycare_keeper.button_pair"),
				"the male Eevee's row isn't Pair: " + row(helper, menu, f + 2));
			click(player, menu, f + 2); // Pair
			helper.assertValueEqual(DaycareKeepers.pairs(keeper).size(), 1, "pairs after Pair");
			helper.assertTrue(!inParty(party, mother) && !inParty(party, father) && inParty(party, pikachu),
				"Pair didn't leave the two Eevees: female in party " + inParty(party, mother) + ", male " + inParty(party, father)
					+ ", Pikachu " + inParty(party, pikachu));
			helper.succeed();
		});
	}
}
