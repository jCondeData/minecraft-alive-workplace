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
import io.github.jcondedata.aliveworkplace.work.Money;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-15): a first mutation pass over the Pokémon daycares (compat/cobblemon/CobblemonDaycare, the
 * Rancher's, and CobblemonDaycareKeeper, ROADMAP 28.12), written from the specs (ranch/Daycare's contract: two of yours,
 * eight a rancher, a point a second at Novice and twice that at Master, one emerald plus one a level gained, the PC when
 * the rancher dies; 28.12: one pair a player, three a keeper, 70/50/20% (+10 at Expert and Master), four emeralds an egg;
 * wiki list-screen.md: a Pokémon that can't be left is a gray row giving the reason). Each test kills a survivor of the
 * pass: the screen's refusals (two of yours, a full daycare, level 100, the last two with you, a full keeper), the pace
 * line, the price of what was gained and the line that tells it, the rancher's death paying out the experience, eggs
 * collected with money for only some of them, the odds line's order and a pair that isn't a mother and a father.
 */
public class QaDaycare1115dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos PASTURE = new BlockPos(2, 2, 2);

	// ---- shared ----

	private static Pokemon make(String spec) {
		return PokemonProperties.Companion.parse(spec, " ", "=").create();
	}

	private static ServerPlayer player(GameTestHelper helper, List<String> seen, long dollars, String... party) {
		ServerPlayer player = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(player, dollars - CobbleDollarsBank.balance(player));
		PlayerPartyStore store = Cobblemon.INSTANCE.getStorage().getParty(player);
		for (String spec : party) {
			store.add(make(spec));
		}
		return player;
	}

	private static List<Pokemon> party(ServerPlayer player) {
		List<Pokemon> out = new ArrayList<>();
		for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(player)) {
			out.add(p);
		}
		return out;
	}

	/** A right-click with an empty hand on {@code villager}, as a client sends it: the screen it opens. */
	private static ChoiceMenu open(GameTestHelper helper, ServerPlayer player, Villager villager) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND));
		}
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "a right-click didn't open the screen: " + player.containerMenu);
		return (ChoiceMenu) player.containerMenu;
	}

	private static void click(ServerPlayer p, ChoiceMenu menu, int slot) {
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, p.containerMenu.getStateId(), slot, 0, ClickType.PICKUP,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	private static ChoiceList.Mark row(GameTestHelper helper, ChoiceMenu menu, int slot) {
		ChoiceList.Mark mark = ChoiceList.mark(menu.icon(slot));
		helper.assertTrue(mark != null && mark.kind() == ChoiceList.Kind.ROW, "slot " + slot + " isn't a list row: " + mark + " " + menu.icon(slot));
		return mark;
	}

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
	}

	private static List<String> lore(ItemStack stack) {
		List<String> out = new ArrayList<>();
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				out.add(line.getString());
			}
		}
		return out;
	}

	/** Every party row is gray, can't be clicked, and gives {@code reason}. */
	private static void allGray(GameTestHelper helper, ChoiceMenu menu, int first, int count, String reason, String what) {
		for (int i = 0; i < count; i++) {
			ChoiceList.Mark mark = row(helper, menu, first + i);
			helper.assertTrue(mark.off() && mark.button() == null && key(mark.second()).equals(reason),
				what + ": party row " + i + " is " + mark + " (" + (mark.second() == null ? "" : mark.second().getString()) + "), expected gray with " + reason);
		}
	}

	// ---- the Rancher's Daycare ----

	private static Villager rancher(GameTestHelper helper, int level) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setDayTime(2000);
		BlockPos trough = new BlockPos(2, 2, 2);
		helper.setBlock(trough, ModBlocks.FEED_TROUGH);
		Villager rancher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), rancher, helper.absolutePos(trough), ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
		rancher.setVillagerData(rancher.getVillagerData().setLevel(level));
		rancher.setNoAi(true);
		helper.assertTrue(Daycare.keepsDaycare(rancher), "setup: not a rancher with a daycare");
		return rancher;
	}

	/**
	 * Two of yours are with the rancher: every party row is gray with "Only 2 of yours at a time" (not a Leave that does
	 * nothing), and a third isn't taken.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycare1115dTwoOfYours"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycare1115dTwoOfYours")
	public void qaTwoOfYoursLeftGraysThePartyWithOnlyTwoAtATime(GameTestHelper helper) {
		Villager rancher = rancher(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, "bulbasaur level=10", "charmander level=5", "squirtle level=5", "pikachu level=5");
		List<Pokemon> party = party(player);
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, party.get(1)) && CobblemonDaycare.leave(player, rancher, party.get(2)),
			"setup: two weren't left");
		ChoiceMenu menu = open(helper, player, rancher);
		allGray(helper, menu, CobblemonDaycare.FIRST_PARTY_SLOT, 2, "screen.aliveworkplace.daycare.full", "two of yours here");
		String full = lore(menu.icon(CobblemonDaycare.FIRST_PARTY_SLOT)).get(0);
		helper.assertTrue(full.equals("Only " + Daycare.MAX_PER_PLAYER + " of yours at a time") && Daycare.MAX_PER_PLAYER == 2, "the refusal reads: " + full);
		click(player, menu, CobblemonDaycare.FIRST_PARTY_SLOT + 1);
		click(player, menu, CobblemonDaycare.FIRST_PARTY_SLOT + 1);
		helper.assertValueEqual(Daycare.boarders(rancher).size(), 2, "boarders after clicking a gray row twice");
		helper.succeed();
	}

	/**
	 * A rancher looks after eight at most: with seven of other players' Pokémon one more of yours is taken; with eight,
	 * your party rows are gray (the daycare is full) and none is taken.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycare1115dEight"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycare1115dEight")
	public void qaARancherTakesEightButNotANinth(GameTestHelper helper) {
		Villager rancher = rancher(helper, 1);
		List<Daycare.Boarder> others = new ArrayList<>();
		for (int i = 0; i < Daycare.MAX_BOARDERS - 1; i++) {
			CompoundTag tag = make("pikachu level=5").saveToNBT(helper.getLevel().registryAccess(), new CompoundTag());
			others.add(new Daycare.Boarder(UUID.randomUUID(), "other" + i, tag, helper.getLevel().getGameTime()));
		}
		Daycare.setBoarders(rancher, others);
		helper.assertTrue(Daycare.MAX_BOARDERS == 8, "the spec says eight: " + Daycare.MAX_BOARDERS);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, "bulbasaur level=10", "charmander level=5", "squirtle level=5");
		List<Pokemon> party = party(player);
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, party.get(1)), "the eighth wasn't taken");
		helper.assertValueEqual(Daycare.boarders(rancher).size(), 8, "boarders");
		helper.assertFalse(CobblemonDaycare.leave(player, rancher, party.get(2)), "a ninth was taken");
		helper.assertValueEqual(Daycare.boarders(rancher).size(), 8, "boarders after a ninth");
		// Someone else's eighth: the player has none here, and still can't leave one
		List<Daycare.Boarder> eight = new ArrayList<>(others);
		eight.add(new Daycare.Boarder(UUID.randomUUID(), "other7", make("pikachu level=5").saveToNBT(helper.getLevel().registryAccess(), new CompoundTag()),
			helper.getLevel().getGameTime()));
		Daycare.setBoarders(rancher, eight);
		ChoiceMenu menu = open(helper, player, rancher);
		allGray(helper, menu, CobblemonDaycare.FIRST_PARTY_SLOT, 2, "screen.aliveworkplace.daycare.full", "a full daycare");
		click(player, menu, CobblemonDaycare.FIRST_PARTY_SLOT + 1);
		click(player, menu, CobblemonDaycare.FIRST_PARTY_SLOT + 1);
		helper.assertValueEqual(Daycare.boarders(rancher).size(), 8, "boarders after clicking in a full daycare");
		helper.succeed();
	}

	/** A Pokémon at the top level can't gain anything here: its row is gray with "Can't grow any more"; the others aren't. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycare1115dLevel100"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycare1115dLevel100")
	public void qaALevel100PokemonsRowSaysItCantGrow(GameTestHelper helper) {
		Villager rancher = rancher(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, "bulbasaur level=10", "mewtwo level=100", "charmander level=5");
		ChoiceMenu menu = open(helper, player, rancher);
		ChoiceList.Mark top = row(helper, menu, CobblemonDaycare.FIRST_PARTY_SLOT + 1);
		helper.assertTrue(top.off() && top.button() == null && key(top.second()).equals("screen.aliveworkplace.daycare.max_level"),
			"the level-100 row: " + top);
		click(player, menu, CobblemonDaycare.FIRST_PARTY_SLOT + 1);
		click(player, menu, CobblemonDaycare.FIRST_PARTY_SLOT + 1);
		helper.assertTrue(Daycare.boarders(rancher).isEmpty(), "a level-100 Pokémon was left");
		ChoiceList.Mark low = row(helper, menu, CobblemonDaycare.FIRST_PARTY_SLOT + 2);
		helper.assertTrue(!low.off() && key(low.button()).equals("screen.aliveworkplace.daycare.button_leave"), "the level-5 row: " + low);
		helper.succeed();
	}

	/**
	 * The pace line: a point a second at Novice (no "faster"), a quarter more each level, twice as fast at Master
	 * ("100% faster").
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycare1115dPace"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycare1115dPace")
	public void qaThePaceLineGrowsWithTheRanchersLevel(GameTestHelper helper) {
		Villager rancher = rancher(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, "bulbasaur level=10", "charmander level=5");
		String[] expected = {"About a point of experience a second", "About a point of experience a second (25% faster with this rancher's experience)",
			"About a point of experience a second (50% faster with this rancher's experience)",
			"About a point of experience a second (75% faster with this rancher's experience)",
			"About a point of experience a second (100% faster with this rancher's experience)"};
		for (int level = 1; level <= 5; level++) {
			rancher.setVillagerData(rancher.getVillagerData().setLevel(level));
			ChoiceMenu menu = open(helper, player, rancher);
			List<String> lines = lore(menu.icon(CobblemonDaycare.INFO));
			helper.assertTrue(lines.contains(expected[level - 1]), "level " + level + ": the info reads " + lines);
			player.closeContainer();
		}
		helper.succeed();
	}

	/**
	 * Collecting costs one emerald plus one for every level gained (in CobbleDollars here): the row's price is what is
	 * charged, the Pokémon really gained those levels, and the line says "level 5 → N" with the level it came back at.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycare1115dPrice"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycare1115dPrice")
	public void qaCollectingCostsOnePlusOneALevelGained(GameTestHelper helper) {
		Villager rancher = rancher(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 100_000, "bulbasaur level=10", "charmander level=5");
		Pokemon kid = party(player).get(1);
		UUID id = kid.getUuid();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		Daycare.Boarder b = Daycare.boarders(rancher).get(0);
		// Three days in her care: 3600 points at Novice
		Daycare.setBoarders(rancher, List.of(new Daycare.Boarder(b.owner(), b.ownerName(), b.pokemon(), b.since() - 3 * 24000)));
		ChoiceMenu menu = open(helper, player, rancher);
		ChoiceList.Mark mark = row(helper, menu, CobblemonDaycare.FIRST_BOARDER_SLOT);
		String shownPrice = mark.price().getString();
		String shownLevels = mark.second().getString();
		long before = CobbleDollarsBank.balance(player);
		click(player, menu, CobblemonDaycare.FIRST_BOARDER_SLOT);
		Pokemon back = party(player).stream().filter(p -> p.getUuid().equals(id)).findFirst().orElse(null);
		helper.assertTrue(back != null, "Collect didn't bring it back");
		int gained = back.getLevel() - 5;
		helper.assertTrue(gained >= 2, "three days and only " + gained + " levels: " + back.getLevel());
		long charged = before - CobbleDollarsBank.balance(player);
		long expected = (long) (1 + gained) * Money.DOLLARS_PER_EMERALD;
		helper.assertTrue(charged == expected, "gained " + gained + " levels and paid " + charged + ", expected " + expected);
		helper.assertTrue(shownPrice.equals(Money.describe(expected, 1 + gained).getString()), "the row said " + shownPrice + ", charged " + charged);
		helper.assertTrue(shownLevels.equals("Level 5 → " + back.getLevel()), "the row said " + shownLevels + ", came back at " + back.getLevel());
		String told = "is back with you (level 5 → " + back.getLevel() + ")";
		helper.assertTrue(seen.stream().anyMatch(l -> l.endsWith(told)), "the player wasn't told '" + told + "': " + seen);
		helper.succeed();
	}

	/** The rancher dies: the Pokémon goes to its trainer's PC with what it gained in her care. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycare1115dDeath"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycare1115dDeath")
	public void qaARanchersDeathSendsTheBoarderToThePcWithItsLevels(GameTestHelper helper) {
		Villager rancher = rancher(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, "bulbasaur level=10", "charmander level=5");
		Pokemon kid = party(player).get(1);
		UUID id = kid.getUuid();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		Daycare.Boarder b = Daycare.boarders(rancher).get(0);
		Daycare.setBoarders(rancher, List.of(new Daycare.Boarder(b.owner(), b.ownerName(), b.pokemon(), b.since() - 3 * 24000)));
		rancher.kill();
		helper.runAfterDelay(2, () -> {
			Pokemon found = null;
			for (Pokemon p : Cobblemon.INSTANCE.getStorage().getPC(player)) {
				if (p.getUuid().equals(id)) {
					found = p;
				}
			}
			helper.assertTrue(found != null, "not in the PC");
			helper.assertTrue(found.getLevel() > 5, "three days in her care and it came back at level " + found.getLevel());
			helper.assertTrue(Daycare.boarders(rancher).isEmpty(), "she still holds it");
			helper.succeed();
		});
	}

	// ---- the Daycare Keeper ----

	private static Villager keeper(GameTestHelper helper, int level) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager keeper = DaycareKeeperCompatTests.keeper(helper);
		keeper.setVillagerData(keeper.getVillagerData().setLevel(level));
		keeper.setNoAi(true);
		return keeper;
	}

	/** With only two Pokémon you can't leave a pair (keep at least one): both rows are gray with the reason. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycare1115dLastTwo"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycare1115dLastTwo")
	public void qaTwoPokemonInThePartyCantBeLeftAsAPair(GameTestHelper helper) {
		Villager keeper = keeper(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, "eevee gender=female level=20", "eevee gender=male level=20");
		ChoiceMenu menu = open(helper, player, keeper);
		allGray(helper, menu, CobblemonDaycareKeeper.FIRST_PARTY_SLOT, 2, "screen.aliveworkplace.daycare_keeper.last", "two in the party");
		click(player, menu, CobblemonDaycareKeeper.FIRST_PARTY_SLOT);
		click(player, menu, CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 1);
		helper.assertTrue(DaycareKeepers.pairs(keeper).isEmpty() && party(player).size() == 2, "the last two were left");
		helper.succeed();
	}

	/** She looks after three pairs: with three other players' pairs, your rows are gray with "She looks after 3 pairs at most". */
	//$ gametest_ticks_batch AREA '100' '"qaDaycare1115dKeeperFull"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycare1115dKeeperFull")
	public void qaAKeeperWithThreePairsGraysTheParty(GameTestHelper helper) {
		Villager keeper = keeper(helper, 1);
		var access = helper.getLevel().registryAccess();
		List<DaycareKeepers.Pair> pairs = new ArrayList<>();
		for (int i = 0; i < DaycareKeepers.MAX_PAIRS; i++) {
			pairs.add(new DaycareKeepers.Pair(UUID.randomUUID(), "other" + i, make("eevee gender=female level=20").saveToNBT(access, new CompoundTag()),
				make("eevee gender=male level=20").saveToNBT(access, new CompoundTag()), 0, DaycareKeepers.day(helper.getLevel())));
		}
		DaycareKeepers.setPairs(keeper, pairs);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, "bulbasaur level=10", "eevee gender=female level=20", "eevee gender=male level=20");
		ChoiceMenu menu = open(helper, player, keeper);
		allGray(helper, menu, CobblemonDaycareKeeper.FIRST_PARTY_SLOT, 3, "screen.aliveworkplace.daycare_keeper.full", "a full keeper");
		String full = lore(menu.icon(CobblemonDaycareKeeper.FIRST_PARTY_SLOT)).get(0);
		helper.assertTrue(full.equals("She looks after 3 pairs at most"), "the refusal reads: " + full);
		click(player, menu, CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 1);
		click(player, menu, CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 2);
		helper.assertValueEqual(DaycareKeepers.pairs(keeper).size(), 3, "pairs after clicking at a full keeper");
		helper.succeed();
	}

	/**
	 * Two eggs wait and the player can pay for one: Collect gives one egg for one price, and the other still waits for
	 * them (not lost).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaDaycare1115dOneOfTwo"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaDaycare1115dOneOfTwo")
	public void qaMoneyForOneOfTwoEggsCollectsOneAndKeepsTheOther(GameTestHelper helper) {
		Villager keeper = keeper(helper, 1);
		long price = (long) DaycareKeepers.EGG_PRICE * Money.DOLLARS_PER_EMERALD;
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, price + price / 2, "bulbasaur level=10", "eevee gender=female level=20", "eevee gender=male level=20");
		List<Pokemon> party = party(player);
		helper.assertTrue(CobblemonDaycareKeeper.leave(player, keeper, party.get(1), party.get(2)), "setup: the pair wasn't left");
		DaycareKeepers.Pair pair = DaycareKeepers.pairs(keeper).get(0);
		DaycareKeepers.setPairs(keeper, List.of(pair.withEggs(2, pair.day())));
		ChoiceMenu menu = open(helper, player, keeper);
		click(player, menu, CobblemonDaycareKeeper.EGGS);
		helper.assertValueEqual(CobblemonDaycareKeeper.countEggs(player), 1, "eggs in the inventory");
		helper.assertValueEqual(CobbleDollarsBank.balance(player), price / 2, "the balance after one egg");
		helper.assertValueEqual(DaycareKeepers.pairs(keeper).get(0).eggs(), 1, "eggs still waiting (the one not paid for)");
		CobbleDollarsBank.add(player, price);
		helper.runAfterDelay(3, () -> {
			click(player, menu, CobblemonDaycareKeeper.EGGS);
			helper.assertValueEqual(CobblemonDaycareKeeper.countEggs(player), 2, "eggs after paying for the second");
			helper.assertValueEqual(DaycareKeepers.pairs(keeper).get(0).eggs(), 0, "eggs waiting after the second");
			helper.succeed();
		});
	}

	/**
	 * The info line gives the odds in its own order, very well, well, so-so: 70%, 50%, 20% at Novice and 80%, 60%, 30% at
	 * Master.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycare1115dOdds"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycare1115dOdds")
	public void qaTheOddsLineReadsVeryWellThenWellThenSoSo(GameTestHelper helper) {
		Villager keeper = keeper(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, "bulbasaur level=10", "eevee gender=female level=20", "eevee gender=male level=20");
		ChoiceMenu menu = open(helper, player, keeper);
		List<String> lines = lore(menu.icon(CobblemonDaycareKeeper.INFO));
		helper.assertTrue(lines.contains("An egg at dawn: 70% very well, 50% well, 20% so-so"), "Novice: " + lines);
		player.closeContainer();
		keeper.setVillagerData(keeper.getVillagerData().setLevel(5));
		menu = open(helper, player, keeper);
		lines = lore(menu.icon(CobblemonDaycareKeeper.INFO));
		helper.assertTrue(lines.contains("An egg at dawn: 80% very well, 60% well, 30% so-so"), "Master: " + lines);
		helper.succeed();
	}

	/**
	 * Two males of one species (or two females) are no mother and father: picking one, the other's row says they'd get
	 * along "not at all" in red, and left anyway they never give an egg.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycare1115dSameGender"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycare1115dSameGender")
	public void qaTwoMalesOrTwoFemalesDontGetAlong(GameTestHelper helper) {
		Villager keeper = keeper(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, "bulbasaur level=10", "eevee gender=male level=20", "eevee gender=male level=20");
		helper.assertTrue(CobblemonDaycareKeeper.getAlong(make("eevee gender=female"), make("eevee gender=female")) == DaycareKeepers.NOT_AT_ALL,
			"two female Eevee get along");
		ChoiceMenu menu = open(helper, player, keeper);
		click(player, menu, CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 1);
		ChoiceList.Mark partner = row(helper, menu, CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 2);
		String expected = Component.translatable("screen.aliveworkplace.daycare_keeper.with_short",
			Component.translatable("screen.aliveworkplace.daycare_keeper.get_along.not_at_all")).getString();
		helper.assertTrue(partner.second().getString().equals(expected) && partner.second().getStyle().getColor() != null
			&& partner.second().getStyle().getColor().getValue() == ChatFormatting.RED.getColor(), "two male Eevee: " + partner.second().getString());
		click(player, menu, CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 2);
		helper.assertValueEqual(DaycareKeepers.pairs(keeper).size(), 1, "the pair wasn't left");
		helper.assertTrue(seen.stream().anyMatch(l -> l.endsWith("They get along not at all.")), "the player wasn't told: " + seen);
		DaycareKeepers.Pair pair = DaycareKeepers.pairs(keeper).get(0);
		DaycareKeepers.setPairs(keeper, List.of(pair.withEggs(0, pair.day() - 10)));
		DaycareKeepers.dawn(helper.getLevel(), keeper, net.minecraft.util.RandomSource.create(7));
		helper.assertValueEqual(DaycareKeepers.pairs(keeper).get(0).eggs(), 0, "eggs from two male Eevee after ten dawns");
		helper.succeed();
	}
}
