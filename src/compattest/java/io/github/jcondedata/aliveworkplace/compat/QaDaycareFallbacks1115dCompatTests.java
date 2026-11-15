package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import io.github.jcondedata.aliveworkplace.ranch.Daycare;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-15): the Pokémon daycares' fallback paths, from the specs (ROADMAP 28.12: "to the party or the PC",
 * "if she dies, the pairs go to their trainers' PCs", four emeralds an egg; ranch/Daycare: the PC when the rancher dies).
 * Taking a pair back with the party full, one slot free or two (the PC takes what the party can't), after the keeper is
 * saved and loaded, and not while eggs wait; a keeper's or rancher's death with the owner offline or their party full;
 * an egg collected with no room in the inventory (refunded, still waiting) or room for just one of two; the hatchling
 * without Cobbreeding to the party or, when full, the PC.
 */
public class QaDaycareFallbacks1115dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	// ---- shared ----

	static Pokemon make(String spec) {
		return PokemonProperties.Companion.parse(spec, " ", "=").create();
	}

	static ServerPlayer player(GameTestHelper helper, List<String> seen, long dollars, String... party) {
		ServerPlayer player = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(player, dollars - CobbleDollarsBank.balance(player));
		PlayerPartyStore store = Cobblemon.INSTANCE.getStorage().getParty(player);
		for (String spec : party) {
			store.add(make(spec));
		}
		return player;
	}

	static List<Pokemon> party(ServerPlayer player) {
		List<Pokemon> out = new ArrayList<>();
		for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(player)) {
			out.add(p);
		}
		return out;
	}

	static boolean inParty(ServerPlayer player, UUID id) {
		return party(player).stream().anyMatch(p -> p.getUuid().equals(id));
	}

	static boolean inPc(PCStore pc, UUID id) {
		for (Pokemon p : pc) {
			if (p.getUuid().equals(id)) {
				return true;
			}
		}
		return false;
	}

	static void fillParty(ServerPlayer player) {
		PlayerPartyStore store = Cobblemon.INSTANCE.getStorage().getParty(player);
		while (party(player).size() < 6) {
			store.add(make("rattata level=3"));
		}
	}

	/** A right-click with an empty hand on {@code villager}, as a client sends it: the screen it opens. */
	static ChoiceMenu open(GameTestHelper helper, ServerPlayer player, Villager villager) {
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

	static void click(ServerPlayer p, ChoiceMenu menu, int slot) {
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, p.containerMenu.getStateId(), slot, 0, ClickType.PICKUP,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	static Villager keeper(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager keeper = DaycareKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		return keeper;
	}

	static Villager rancher(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setDayTime(2000);
		BlockPos trough = new BlockPos(2, 2, 2);
		helper.setBlock(trough, ModBlocks.FEED_TROUGH);
		Villager rancher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), rancher, helper.absolutePos(trough), ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
		rancher.setNoAi(true);
		helper.assertTrue(Daycare.keepsDaycare(rancher), "setup: not a rancher with a daycare");
		return rancher;
	}

	/** The player leaves {@code party.get(1)} and {@code party.get(2)} (a female and a male Eevee) as their pair. */
	static UUID[] leavePair(GameTestHelper helper, Villager keeper, ServerPlayer player) {
		List<Pokemon> party = party(player);
		Pokemon mother = party.get(1);
		Pokemon father = party.get(2);
		helper.assertTrue(CobblemonDaycareKeeper.leave(player, keeper, mother, father), "setup: the pair wasn't left");
		return new UUID[] {mother.getUuid(), father.getUuid()};
	}

	static long eggItemsAround(GameTestHelper helper, ServerPlayer player) {
		long n = CobblemonDaycareKeeper.countEggs(player);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(16))) {
			if (CobblemonDaycareKeeper.isCobbreedingEgg(e.getItem())) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	static void fillInventory(ServerPlayer player, int free) {
		var items = player.getInventory().items;
		for (int i = 0; i < items.size(); i++) {
			items.set(i, i < items.size() - free ? new ItemStack(Items.DIRT, 64) : ItemStack.EMPTY);
		}
	}

	static final String TWO_EEVEE_AND_ONE = "bulbasaur level=10";

	// ---- takeBack: the party, else the PC ----

	/** The party is full when the pair is taken back (through the screen): both go to the PC, none is lost. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dFullParty"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dFullParty")
	public void qaTakeBackWithAFullPartySendsBothToThePc(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		fillParty(player);
		ChoiceMenu menu = open(helper, player, keeper);
		click(player, menu, CobblemonDaycareKeeper.TAKE_BACK);
		PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
		helper.assertTrue(inPc(pc, pair[0]) && inPc(pc, pair[1]), "not both in the PC (mother " + inPc(pc, pair[0]) + ", father " + inPc(pc, pair[1]) + ")");
		helper.assertTrue(!inParty(player, pair[0]) && !inParty(player, pair[1]) && party(player).size() == 6, "the party changed: " + party(player).size());
		helper.assertTrue(DaycareKeepers.pairs(keeper).isEmpty(), "she still holds the pair");
		helper.assertTrue(seen.contains("You took your pair back."), "the player was told: " + seen);
		helper.succeed();
	}

	/** One slot free: the first of the pair joins the party, the second goes to the PC. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dOneFree"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dOneFree")
	public void qaTakeBackWithOneFreeSlotSplitsThePairBetweenPartyAndPc(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		fillParty(player);
		Cobblemon.INSTANCE.getStorage().getParty(player).remove(party(player).get(5));
		helper.assertValueEqual(party(player).size(), 5, "setup: party size");
		ChoiceMenu menu = open(helper, player, keeper);
		click(player, menu, CobblemonDaycareKeeper.TAKE_BACK);
		PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
		helper.assertTrue(inParty(player, pair[0]) && !inPc(pc, pair[0]), "the mother: party " + inParty(player, pair[0]) + ", PC " + inPc(pc, pair[0]));
		helper.assertTrue(!inParty(player, pair[1]) && inPc(pc, pair[1]), "the father: party " + inParty(player, pair[1]) + ", PC " + inPc(pc, pair[1]));
		helper.assertTrue(DaycareKeepers.pairs(keeper).isEmpty() && seen.contains("You took your pair back."), "pairs " + DaycareKeepers.pairs(keeper) + ", told " + seen);
		helper.succeed();
	}

	/** Two slots free: both join the party, nothing goes to the PC. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dTwoFree"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dTwoFree")
	public void qaTakeBackWithTwoFreeSlotsPutsBothInTheParty(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20",
			"rattata level=3", "rattata level=3", "rattata level=3");
		UUID[] pair = leavePair(helper, keeper, player);
		helper.assertValueEqual(party(player).size(), 4, "setup: party size");
		ChoiceMenu menu = open(helper, player, keeper);
		click(player, menu, CobblemonDaycareKeeper.TAKE_BACK);
		PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
		helper.assertTrue(inParty(player, pair[0]) && inParty(player, pair[1]) && party(player).size() == 6, "not both in the party: " + party(player).size());
		helper.assertTrue(!inPc(pc, pair[0]) && !inPc(pc, pair[1]), "one went to the PC with room in the party");
		helper.succeed();
	}

	/** The keeper is saved and loaded (a restart) before the pair is taken back with a full party: the PC still takes both. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dReload")
	public void qaTakeBackAfterTheKeeperIsReloadedStillUsesThePc(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		fillParty(player);
		CompoundTag saved = new CompoundTag();
		keeper.save(saved);
		keeper.discard();
		Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
		loaded.load(saved);
		helper.getLevel().addFreshEntity(loaded);
		helper.assertValueEqual(DaycareKeepers.pairs(loaded).size(), 1, "pairs after the reload");
		ChoiceMenu menu = open(helper, player, loaded);
		click(player, menu, CobblemonDaycareKeeper.TAKE_BACK);
		PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
		helper.assertTrue(inPc(pc, pair[0]) && inPc(pc, pair[1]), "not both in the PC after a reload (mother " + inPc(pc, pair[0]) + ", father "
			+ inPc(pc, pair[1]) + ")");
		helper.assertTrue(DaycareKeepers.pairs(loaded).isEmpty(), "she still holds the pair");
		loaded.discard();
		helper.succeed();
	}

	/** An egg waits (one, the boundary): Take back does nothing, the pair stays with her and nothing reaches the PC. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dEggWaits"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dEggWaits")
	public void qaTakeBackWithOneEggWaitingKeepsThePair(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		fillParty(player);
		DaycareKeepers.Pair p = DaycareKeepers.pairs(keeper).get(0);
		DaycareKeepers.setPairs(keeper, List.of(p.withEggs(1, p.day())));
		Component said = CobblemonDaycareKeeper.takeBack(player, keeper);
		PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
		helper.assertTrue(said.getString().isEmpty(), "taking back with an egg waiting said: " + said.getString());
		helper.assertTrue(DaycareKeepers.pairs(keeper).size() == 1 && !inPc(pc, pair[0]) && !inPc(pc, pair[1]), "the pair left her with an egg waiting");
		helper.succeed();
	}

	// ---- returnAll: the PC, with the owner away or the party full ----

	/** The keeper dies while the pair's trainer is offline: both go to that trainer's PC (loaded from their save). */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dKeeperOffline"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dKeeperOffline")
	public void qaKeepersDeathSendsAnOfflineTrainersPairToTheirPc(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		var access = helper.getLevel().registryAccess();
		UUID owner = UUID.randomUUID();
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		DaycareKeepers.setPairs(keeper, List.of(new DaycareKeepers.Pair(owner, "away", mother.saveToNBT(access, new CompoundTag()),
			father.saveToNBT(access, new CompoundTag()), 0, DaycareKeepers.day(helper.getLevel()))));
		keeper.kill();
		helper.runAfterDelay(2, () -> {
			PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(owner, access);
			helper.assertTrue(inPc(pc, mother.getUuid()) && inPc(pc, father.getUuid()), "not both in the offline trainer's PC (mother "
				+ inPc(pc, mother.getUuid()) + ", father " + inPc(pc, father.getUuid()) + ")");
			helper.assertTrue(DaycareKeepers.pairs(keeper).isEmpty(), "she still holds them");
			helper.succeed();
		});
	}

	/** The keeper dies while the trainer is online with a full party: the PC (as promised), not the party. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dKeeperFullParty"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dKeeperFullParty")
	public void qaKeepersDeathWithAFullPartySendsThePairToThePc(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		fillParty(player);
		keeper.kill();
		helper.runAfterDelay(2, () -> {
			PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
			helper.assertTrue(inPc(pc, pair[0]) && inPc(pc, pair[1]), "not both in the PC (mother " + inPc(pc, pair[0]) + ", father " + inPc(pc, pair[1]) + ")");
			helper.assertTrue(party(player).size() == 6 && !inParty(player, pair[0]), "the party changed");
			helper.succeed();
		});
	}

	/** Two trainers' pairs, one online and one offline: her death sends each pair to its own trainer's PC, none to the other's. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dKeeperTwoOwners"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dKeeperTwoOwners")
	public void qaKeepersDeathSendsEachPairToItsOwnTrainer(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		var access = helper.getLevel().registryAccess();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] mine = leavePair(helper, keeper, player);
		UUID other = UUID.randomUUID();
		Pokemon a = make("pidgey gender=female level=8");
		Pokemon b = make("pidgey gender=male level=8");
		List<DaycareKeepers.Pair> pairs = new ArrayList<>(DaycareKeepers.pairs(keeper));
		pairs.add(new DaycareKeepers.Pair(other, "away", a.saveToNBT(access, new CompoundTag()), b.saveToNBT(access, new CompoundTag()), 0,
			DaycareKeepers.day(helper.getLevel())));
		DaycareKeepers.setPairs(keeper, pairs);
		keeper.kill();
		helper.runAfterDelay(2, () -> {
			PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
			PCStore otherPc = Cobblemon.INSTANCE.getStorage().getPC(other, access);
			helper.assertTrue(inPc(pc, mine[0]) && inPc(pc, mine[1]) && !inPc(pc, a.getUuid()) && !inPc(pc, b.getUuid()), "the online trainer's PC");
			helper.assertTrue(inPc(otherPc, a.getUuid()) && inPc(otherPc, b.getUuid()) && !inPc(otherPc, mine[0]), "the offline trainer's PC");
			helper.succeed();
		});
	}

	/** The rancher dies while the boarder's trainer is offline: it goes to that trainer's PC. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dRancherOffline"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dRancherOffline")
	public void qaRanchersDeathSendsAnOfflineTrainersBoarderToTheirPc(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		var access = helper.getLevel().registryAccess();
		UUID owner = UUID.randomUUID();
		Pokemon kid = make("charmander level=5");
		Daycare.setBoarders(rancher, List.of(new Daycare.Boarder(owner, "away", kid.saveToNBT(access, new CompoundTag()), helper.getLevel().getGameTime())));
		rancher.kill();
		helper.runAfterDelay(2, () -> {
			PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(owner, access);
			helper.assertTrue(inPc(pc, kid.getUuid()), "not in the offline trainer's PC");
			helper.assertTrue(Daycare.boarders(rancher).isEmpty(), "she still holds it");
			helper.succeed();
		});
	}

	// ---- giveEgg's refund ----

	/**
	 * Collecting with no room in the inventory: the egg can't be given, so the price comes back and the egg still waits
	 * (no egg item appears anywhere, and the money isn't lost).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dNoRoom"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dNoRoom")
	public void qaAnEggWithNoRoomInTheInventoryIsRefundedAndStillWaits(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		long price = (long) DaycareKeepers.EGG_PRICE * Money.DOLLARS_PER_EMERALD;
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 3 * price, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		leavePair(helper, keeper, player);
		DaycareKeepers.Pair p = DaycareKeepers.pairs(keeper).get(0);
		DaycareKeepers.setPairs(keeper, List.of(p.withEggs(1, p.day())));
		ChoiceMenu menu = open(helper, player, keeper);
		fillInventory(player, 0);
		click(player, menu, CobblemonDaycareKeeper.EGGS);
		long eggs = eggItemsAround(helper, player);
		long balance = CobbleDollarsBank.balance(player);
		int waiting = DaycareKeepers.pairs(keeper).get(0).eggs();
		helper.assertTrue(eggs == 0 && balance == 3 * price && waiting == 1, "with no room: " + eggs + " egg items, balance " + balance + " of "
			+ 3 * price + ", " + waiting + " still waiting; told " + seen);
		helper.succeed();
	}

	/**
	 * Two eggs wait and the inventory has one free slot: one egg is given and paid for, the second is refunded and still
	 * waits; the player is told about the one they got.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dOneSlot"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dOneSlot")
	public void qaTwoEggsWithOneFreeSlotGiveOneAndRefundTheOther(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		long price = (long) DaycareKeepers.EGG_PRICE * Money.DOLLARS_PER_EMERALD;
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 3 * price, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		leavePair(helper, keeper, player);
		DaycareKeepers.Pair p = DaycareKeepers.pairs(keeper).get(0);
		DaycareKeepers.setPairs(keeper, List.of(p.withEggs(2, p.day())));
		ChoiceMenu menu = open(helper, player, keeper);
		fillInventory(player, 1);
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.EGGS);
		long eggs = eggItemsAround(helper, player);
		long balance = CobbleDollarsBank.balance(player);
		int waiting = DaycareKeepers.pairs(keeper).get(0).eggs();
		helper.assertTrue(eggs == 1 && balance == 2 * price && waiting == 1, "one slot free: " + eggs + " egg items, balance " + balance + " (expected "
			+ 2 * price + "), " + waiting + " still waiting; told " + seen);
		helper.assertTrue(seen.stream().anyMatch(l -> l.startsWith("You collected ") && l.contains("Eevee Egg") && !l.contains(" and ")),
			"the player was told: " + seen);
		helper.succeed();
	}

	// ---- giveEgg without Cobbreeding: the party, else the PC ----

	/** Without Cobbreeding the hatchling joins the party when there's room ("Eevee, just hatched"), and the PC when it's full. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dHatchling"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dHatchling")
	public void qaAHatchlingGoesToThePartyThenToThePcWhenItIsFull(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, "rattata level=3", "rattata level=3", "rattata level=3", "rattata level=3", "rattata level=3");
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		Component first = CobblemonDaycareKeeper.giveEgg(player, mother, father, RandomSource.create(3), false);
		helper.assertTrue(first != null && first.getString().equals("Eevee, just hatched"), "the first hatchling reads: " + first);
		helper.assertTrue(party(player).size() == 6 && party(player).get(5).getSpecies().getName().equals("Eevee"), "the sixth in the party: "
			+ party(player).get(party(player).size() - 1).getSpecies().getName());
		Component second = CobblemonDaycareKeeper.giveEgg(player, mother, father, RandomSource.create(4), false);
		helper.assertTrue(second != null && second.getString().equals("Eevee, just hatched"), "the second hatchling reads: " + second);
		int inPc = 0;
		for (Pokemon p : Cobblemon.INSTANCE.getStorage().getPC(player)) {
			if (p.getSpecies().getName().equals("Eevee") && p.getLevel() == 1) {
				inPc++;
			}
		}
		helper.assertTrue(inPc == 1 && party(player).size() == 6, "hatchlings in the PC with the party full: " + inPc);
		helper.succeed();
	}
}
