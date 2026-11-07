package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * QA (lane-c-1102-0953) of 33.18 ("Either side can end a pact at once ... by letter. The chronicles note each."), from
 * the side its own tests don't reach: a trade pact ended by the side that offered it (PactGameTests ends one from the
 * other side and checks only that it's gone), with the other owner's letter, both chronicles and both directions'
 * routes; and the offer's and the decline's chronicle lines, word for word.
 */
public class QaPactChronicles1102GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static long count(Land land, int hall, String line) {
		return land.entity(hall).chronicle().stream().filter(e -> e.text().getString().equals(line)).count();
	}

	private static List<String> letters(ServerPlayer player) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			WrittenBookContent book = player.getInventory().getItem(i).get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (book != null) {
				out.add(book.pages().get(0).raw().getString());
			}
		}
		return out;
	}

	//$ gametest_ticks_batch AREA '100' '"qaPactChronicles1102"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPactChronicles1102")
	public void qaAnOffererEndsATradePactAtOnceAndBothChroniclesNoteEachStep(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		MinecraftServer server = land.level().getServer();
		boolean enabled = Pacts.ENABLED;
		boolean alliances = Pacts.ALLIANCES;
		Pacts.ENABLED = true;
		Pacts.ALLIANCES = true;
		Pacts.Data.get(server).clear();
		Pacts.Data.get(server).setMigrated(true);
		Caravans.Data data = Caravans.Data.get(land.level());
		Leftovers.after(helper, () -> {
			Pacts.ENABLED = enabled;
			Pacts.ALLIANCES = alliances;
			Pacts.Data.get(server).clear();
			data.remove(land.hall(0));
			data.remove(land.hall(1));
		});
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = RealmGameTests.listener(helper, theirs, "neighbour");
		land.entity(1).setOwner(them.getUUID(), "neighbour");
		io.github.jcondedata.aliveworkplace.realm.Realms.round(land.level(), land.hall(1), land.entity(1), 6);
		data.setWants(land.hall(0), Component.literal(land.name(0)), List.of());
		data.setWants(land.hall(1), Component.literal(land.name(1)), List.of());
		String ours = land.name(0);
		String other = land.name(1);

		// An offer, declined: the offerer's chronicle notes both, word for word.
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok(), "the first offer was refused");
		String offered = "Offered " + other + " a trade pact (a letter to neighbour)";
		helper.assertTrue(count(land, 0, offered) == 1, "the offerer's chronicle doesn't read \"" + offered + "\" once: " + land.entity(0).chronicle());
		helper.assertTrue(Pacts.decline(land.level(), land.hall(1), land.at(0), them).ok(), "couldn't decline");
		String declined = other + " declined the offer";
		helper.assertTrue(count(land, 0, declined) == 1, "the offerer's chronicle doesn't read \"" + declined + "\" once: " + land.entity(0).chronicle());

		// Offered again and accepted: both chronicles; a route each way.
		helper.assertTrue(Pacts.offer(land.level(), land.hall(0), land.hall(1), false, land.owner()).ok(), "the second offer was refused");
		helper.assertTrue(Pacts.accept(land.level(), land.hall(1), land.at(0), them).ok(), "couldn't accept");
		helper.assertTrue(count(land, 0, "Made a trade pact with " + other) == 1 && count(land, 1, "Made a trade pact with " + ours) == 1,
			"the chronicles of the pact: " + land.entity(0).chronicle() + " / " + land.entity(1).chronicle());
		helper.assertTrue(data.toggleRoute(land.hall(0), land.hall(1)) && data.toggleRoute(land.hall(1), land.hall(0)), "set-up: no routes under the pact");
		int lettersBefore = letters(them).size();

		// The offerer ends it: at once, by letter, both chronicles, both routes gone; a second end is refused.
		helper.assertTrue(Pacts.end(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "the offerer couldn't end the pact");
		helper.assertTrue(Pacts.Data.get(server).pact(land.at(0), land.at(1)) == null, "the trade pact didn't end at once");
		helper.assertTrue(!Pacts.mayRoute(land.level(), land.hall(0), land.hall(1)) && !Pacts.mayRoute(land.level(), land.hall(1), land.hall(0)),
			"a route is still allowed after the pact ended");
		helper.assertTrue(!data.routesFrom(land.hall(0)).contains(land.hall(1)) && !data.routesFrom(land.hall(1)).contains(land.hall(0)),
			"a route stayed: " + data.routesFrom(land.hall(0)) + " / " + data.routesFrom(land.hall(1)));
		helper.assertTrue(letters(them).size() == lettersBefore + 1, "the other owner got no letter of the end: " + letters(them));
		helper.assertTrue(count(land, 0, "The trade pact with " + other + " ended") == 1 && count(land, 1, "The trade pact with " + ours + " ended") == 1,
			"the chronicles of the end: " + land.entity(0).chronicle() + " / " + land.entity(1).chronicle());
		helper.assertTrue(!Pacts.end(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "a pact already ended was ended again");
		helper.assertTrue(count(land, 1, "The trade pact with " + ours + " ended") == 1, "the second end wrote the chronicle again");
		helper.succeed();
	}
}
