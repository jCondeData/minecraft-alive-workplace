package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.fossil.FossilScientists;
import io.github.jcondedata.aliveworkplace.fossil.Revival;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Tester, lane d 2026-11-02: the Fossil Scientist from the README's words (lines 1585-1595), through the player's own
 * right-click. Hand over a fossil (a Galar fossil's two halves, one in each hand) and pay 800 CobbleDollars: they revive it
 * while they work, three at a time, and the Pokémon joins its owner's party (or PC) when it's done, or the next time they're
 * online.
 */
public class QaFossilScientist1102dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	static final BlockPos LAB = new BlockPos(2, 2, 2);
	static final BlockPos STAND = new BlockPos(3, 2, 3);
	static final long PRICE = 800;
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("QaFossilScientist1102d");

	// ------------------------------------------------------------------ helpers

	static Item cobblemon(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", path));
		if (item == Items.AIR) {
			throw new GameTestAssertException("no cobblemon:" + path);
		}
		return item;
	}

	/** Shortens revivals for this test (put back when it ends) and a scientist employed at a Fossil Analyzer, by day. */
	static Villager scientist(GameTestHelper helper, int reviveTicks) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		int usual = FossilScientists.REVIVE_TICKS;
		FossilScientists.REVIVE_TICKS = reviveTicks;
		PartnerShowsCompatTests.after(helper, () -> FossilScientists.REVIVE_TICKS = usual);
		helper.setDayTime(2000);
		helper.setBlock(LAB, StationsCompatTests.fossilAnalyzer());
		Villager scientist = helper.spawn(EntityType.VILLAGER, STAND);
		Jobs.employ(helper.getLevel(), scientist, helper.absolutePos(LAB), ModVillagers.FOSSIL_ANALYZER_POI, ModVillagers.FOSSIL_SCIENTIST);
		helper.assertTrue(FossilScientists.isScientist(scientist), "not a Fossil Scientist");
		return scientist;
	}

	/** A logged-in survival player (with {@code uuid}) whose chat lines land in {@code seen}; logged out when the test ends. */
	static ServerPlayer player(GameTestHelper helper, List<String> seen, UUID uuid, String name) {
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

	static void setDollars(ServerPlayer player, long amount) {
		CobbleDollarsBank.add(player, amount - CobbleDollarsBank.balance(player));
	}

	/**
	 * A client's right-click on {@code villager} (sneaking or not) with what the player holds, through the server's packet
	 * handler: the "interact at" packet, then (as the client does when that one did nothing) the plain "interact" one.
	 * {@code seen} must be the player's chat lines.
	 */
	static void click(ServerPlayer player, Villager villager, boolean sneak, List<String> seen) {
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.setShiftKeyDown(sneak);
		int before = seen.size();
		ItemStack main = player.getMainHandItem().copy();
		ItemStack off = player.getOffhandItem().copy();
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, sneak, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (seen.size() == before && player.containerMenu == player.inventoryMenu
				&& ItemStack.matches(main, player.getMainHandItem()) && ItemStack.matches(off, player.getOffhandItem())) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, sneak, InteractionHand.MAIN_HAND));
		}
		// 43.3: an empty-hand click opens the villager's card; its first button does what the click did, pressed as the client sends it
		if (player.containerMenu instanceof io.github.jcondedata.aliveworkplace.card.StatusCardMenu card) {
			player.connection.handleContainerButtonClick(new net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket(
				card.containerId, card.card().buttons().get(0).id()));
		}
		player.setShiftKeyDown(false);
	}

	static void hold(ServerPlayer player, ItemStack main, ItemStack off) {
		player.setItemInHand(InteractionHand.MAIN_HAND, main);
		player.setItemInHand(InteractionHand.OFF_HAND, off);
	}

	static List<String> party(ServerPlayer player) {
		List<String> out = new ArrayList<>();
		for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(player)) {
			if (p != null) {
				out.add(p.getSpecies().getName().toLowerCase(java.util.Locale.ROOT));
			}
		}
		return out;
	}

	static List<String> pc(ServerPlayer player) {
		List<String> out = new ArrayList<>();
		for (Pokemon p : Cobblemon.INSTANCE.getStorage().getPC(player)) {
			if (p != null) {
				out.add(p.getSpecies().getName().toLowerCase(java.util.Locale.ROOT));
			}
		}
		return out;
	}

	static long count(List<String> list, String species) {
		return list.stream().filter(species::equals).count();
	}

	static String last(List<String> seen) {
		return seen.isEmpty() ? "(nothing)" : seen.get(seen.size() - 1);
	}

	// ------------------------------------------------------------------ paying

	/**
	 * 799 CobbleDollars isn't enough: the fossil stays in the hand, the money stays, nothing is queued and the player is told
	 * the price. With 800 the fossil is taken and exactly 800 charged, and the novice's estimate is about three minutes.
	 * An empty hand then tells how it's going; something that isn't a fossil takes nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaFossilPay1102d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFossilPay1102d")
	public void qaFossilCantAffordKeepsEverythingThenChargesExactly800(GameTestHelper helper) {
		Villager scientist = scientist(helper, 3600);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, UUID.randomUUID(), "qa-fossil-pay");
		helper.runAfterDelay(2, () -> {
			// empty hand, nothing handed over yet: the hint with the price
			hold(player, ItemStack.EMPTY, ItemStack.EMPTY);
			click(player, scientist, false, seen);
			helper.assertTrue(last(seen).startsWith("Hand ") && last(seen).endsWith("a fossil to revive it, for 800 CobbleDollars."),
				"the empty-hand hint said: " + last(seen));

			// a diamond isn't a fossil: nothing taken, nothing charged
			setDollars(player, 799);
			hold(player, new ItemStack(Items.DIAMOND), ItemStack.EMPTY);
			click(player, scientist, false, seen);
			helper.assertTrue(player.getMainHandItem().is(Items.DIAMOND) && player.getMainHandItem().getCount() == 1, "the diamond was taken");
			helper.assertTrue(FossilScientists.queue(scientist).isEmpty(), "a diamond was queued");
			helper.assertTrue(CobbleDollarsBank.balance(player) == 799, "a diamond cost " + (799 - CobbleDollarsBank.balance(player)));
			if (player.containerMenu != player.inventoryMenu) {
				player.closeContainer();
			}

			// 799: refused
			hold(player, new ItemStack(cobblemon("dome_fossil")), ItemStack.EMPTY);
			int before = seen.size();
			click(player, scientist, false, seen);
			helper.assertTrue(player.getMainHandItem().is(cobblemon("dome_fossil")) && player.getMainHandItem().getCount() == 1,
				"the fossil left the hand without payment: " + player.getMainHandItem());
			helper.assertTrue(CobbleDollarsBank.balance(player) == 799, "799 became " + CobbleDollarsBank.balance(player));
			helper.assertTrue(FossilScientists.queue(scientist).isEmpty(), "queued without payment");
			helper.assertTrue(seen.size() > before && last(seen).equals("Reviving a fossil costs 800 CobbleDollars."),
				"can't-afford line: " + last(seen));

			// 800: taken, charged once
			setDollars(player, PRICE);
			click(player, scientist, false, seen);
			helper.assertTrue(player.getMainHandItem().isEmpty(), "the fossil is still in the hand: " + player.getMainHandItem());
			helper.assertTrue(CobbleDollarsBank.balance(player) == 0, "charged " + (PRICE - CobbleDollarsBank.balance(player)) + ", not 800");
			helper.assertTrue(FossilScientists.queue(scientist).size() == 1, "queue: " + FossilScientists.queue(scientist).size());
			Revival r = FossilScientists.queue(scientist).get(0);
			helper.assertTrue(r.owner().equals(player.getUUID()), "the revival isn't the player's");
			LOG.info("pay: lines {}", seen);
			helper.assertTrue(last(seen).matches(".* takes the Dome Fossil: it'll be revived in about [234] minutes of their work\\."),
				"taken line (a novice: about three minutes): " + last(seen));

			// empty hand: progress
			hold(player, ItemStack.EMPTY, ItemStack.EMPTY);
			click(player, scientist, false, seen);
			helper.assertTrue(last(seen).matches("Your Dome Fossil: \\d+% revived\\."), "progress line: " + last(seen));
			helper.assertTrue(FossilScientists.queue(scientist).size() == 1, "checking changed the queue");
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ three at a time, each to its owner

	/**
	 * Two players hand over three fossils (A two, B one); B's fourth is refused (fossil and money kept). All three are
	 * revived, and each Pokémon goes only to the player who handed its fossil over.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaFossilQueue1102d"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaFossilQueue1102d")
	public void qaFossilFourthIsRefusedAndEachOwnerGetsOnlyTheirs(GameTestHelper helper) {
		Villager scientist = scientist(helper, 40);
		List<String> seenA = new ArrayList<>();
		List<String> seenB = new ArrayList<>();
		ServerPlayer a = player(helper, seenA, UUID.randomUUID(), "qa-fossil-a");
		ServerPlayer b = player(helper, seenB, UUID.randomUUID(), "qa-fossil-b");
		setDollars(a, 10_000);
		setDollars(b, 10_000);
		helper.runAfterDelay(2, () -> {
			hold(a, new ItemStack(cobblemon("dome_fossil")), ItemStack.EMPTY);
			click(a, scientist, false, seenA);
			hold(a, new ItemStack(cobblemon("helix_fossil")), ItemStack.EMPTY);
			click(a, scientist, false, seenA);
			helper.assertTrue(last(seenA).contains("(1 ahead of it)"), "A's second line: " + last(seenA));
			hold(b, new ItemStack(cobblemon("root_fossil")), ItemStack.EMPTY);
			click(b, scientist, false, seenB);
			helper.assertTrue(last(seenB).contains("(2 ahead of it)"), "B's line: " + last(seenB));
			helper.assertTrue(FossilScientists.queue(scientist).size() == 3, "queue: " + FossilScientists.queue(scientist).size());
			helper.assertTrue(CobbleDollarsBank.balance(b) == 10_000 - PRICE, "B paid " + (10_000 - CobbleDollarsBank.balance(b)));

			hold(b, new ItemStack(cobblemon("claw_fossil")), ItemStack.EMPTY);
			click(b, scientist, false, seenB);
			helper.assertTrue(FossilScientists.queue(scientist).size() == 3, "a fourth was queued");
			helper.assertTrue(b.getMainHandItem().is(cobblemon("claw_fossil")) && b.getMainHandItem().getCount() == 1, "the fourth fossil was taken");
			helper.assertTrue(CobbleDollarsBank.balance(b) == 10_000 - PRICE, "the refused fourth cost " + (10_000 - PRICE - CobbleDollarsBank.balance(b)));
			helper.assertTrue(last(seenB).endsWith("is already working on 3 fossils. Come back when one is done."), "busy line: " + last(seenB));
			hold(b, ItemStack.EMPTY, ItemStack.EMPTY);

			helper.startSequence()
				.thenWaitUntil(() -> {
					helper.assertTrue(FossilScientists.queue(scientist).isEmpty(), "still queued: " + FossilScientists.queue(scientist).size());
				})
				.thenIdle(40)
				.thenExecute(() -> {
					List<String> pa = party(a);
					List<String> pb = party(b);
					helper.assertTrue(count(pa, "kabuto") == 1 && count(pa, "omanyte") == 1 && pa.size() == 2, "A's party: " + pa);
					helper.assertTrue(pb.equals(List.of("lileep")), "B's party: " + pb);
					helper.assertTrue(pc(a).isEmpty() && pc(b).isEmpty(), "PCs: " + pc(a) + " / " + pc(b));
					helper.assertTrue(ModAttachments.FOSSILS_REVIVED.getOrElse(scientist, 0) == 3, "revived count "
						+ ModAttachments.FOSSILS_REVIVED.getOrElse(scientist, 0));
					helper.assertTrue(seenB.stream().filter(s -> s.contains("revived your fossil")).count() == 1, "B's revived lines: " + seenB);
					helper.assertTrue(seenA.stream().filter(s -> s.contains("revived your fossil")).count() == 2, "A's revived lines: " + seenA);
				})
				.thenSucceed();
		});
	}

	// ------------------------------------------------------------------ Galar fossils

	/**
	 * A Galar fossil: one half in each hand is one revival, both halves used and charged once (either half in the main
	 * hand). One half alone, or two of the same half, takes nothing and costs nothing. Both Pokémon arrive.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaFossilGalar1102d"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaFossilGalar1102d")
	public void qaFossilGalarHalvesTakeBothHandsAndChargeOnce(GameTestHelper helper) {
		Villager scientist = scientist(helper, 40);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, UUID.randomUUID(), "qa-fossil-galar");
		setDollars(player, 5_000);
		helper.runAfterDelay(2, () -> {
			// one half alone
			hold(player, new ItemStack(cobblemon("fossilized_fish")), ItemStack.EMPTY);
			click(player, scientist, false, seen);
			helper.assertTrue(player.getMainHandItem().getCount() == 1, "half a fossil was taken");
			helper.assertTrue(FossilScientists.queue(scientist).isEmpty() && CobbleDollarsBank.balance(player) == 5_000, "half a fossil was taken on or charged");
			helper.assertTrue(last(seen).equals("That's half a fossil: hold the other half in your other hand."), "half line: " + last(seen));

			// two of the same half
			hold(player, new ItemStack(cobblemon("fossilized_bird")), new ItemStack(cobblemon("fossilized_bird")));
			click(player, scientist, false, seen);
			helper.assertTrue(player.getMainHandItem().getCount() == 1 && player.getOffhandItem().getCount() == 1, "two birds were taken");
			helper.assertTrue(FossilScientists.queue(scientist).isEmpty() && CobbleDollarsBank.balance(player) == 5_000, "two birds were taken on or charged");

			// half in the main hand, a whole fossil in the off hand: nothing taken (the whole one must be in the main hand)
			hold(player, new ItemStack(cobblemon("fossilized_bird")), new ItemStack(cobblemon("dome_fossil")));
			click(player, scientist, false, seen);
			helper.assertTrue(player.getMainHandItem().getCount() == 1 && player.getOffhandItem().getCount() == 1, "bird + dome: something was taken");
			helper.assertTrue(FossilScientists.queue(scientist).isEmpty() && CobbleDollarsBank.balance(player) == 5_000, "bird + dome was taken on or charged");

			// bird + drake = Dracozolt
			hold(player, new ItemStack(cobblemon("fossilized_bird"), 2), new ItemStack(cobblemon("fossilized_drake"), 2));
			click(player, scientist, false, seen);
			helper.assertTrue(player.getMainHandItem().getCount() == 1 && player.getOffhandItem().getCount() == 1,
				"one of each half should be used: " + player.getMainHandItem() + " / " + player.getOffhandItem());
			helper.assertTrue(FossilScientists.queue(scientist).size() == 1, "queue: " + FossilScientists.queue(scientist).size());
			helper.assertTrue(CobbleDollarsBank.balance(player) == 5_000 - PRICE, "charged " + (5_000 - CobbleDollarsBank.balance(player)));
			helper.assertTrue(last(seen).contains("Fossilized Bird and Fossilized Drake"), "taken line: " + last(seen));

			// the other way round: drake in the main hand, fish in the off hand = Dracovish
			hold(player, new ItemStack(cobblemon("fossilized_drake")), new ItemStack(cobblemon("fossilized_fish")));
			click(player, scientist, false, seen);
			helper.assertTrue(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty(),
				"drake + fish: " + player.getMainHandItem() + " / " + player.getOffhandItem());
			helper.assertTrue(FossilScientists.queue(scientist).size() == 2, "queue: " + FossilScientists.queue(scientist).size());
			helper.assertTrue(CobbleDollarsBank.balance(player) == 5_000 - 2 * PRICE, "charged " + (5_000 - CobbleDollarsBank.balance(player)));

			helper.succeedWhen(() -> {
				List<String> party = party(player);
				helper.assertTrue(count(party, "dracozolt") == 1 && count(party, "dracovish") == 1 && party.size() == 2, "party: " + party);
				helper.assertTrue(FossilScientists.queue(scientist).isEmpty(), "still queued");
			});
		});
	}

	// ------------------------------------------------------------------ a full party

	/** With six Pokémon in the party the revived one goes to the PC, and the party stays six. */
	//$ gametest_ticks_batch AREA '1200' '"qaFossilFullParty1102d"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaFossilFullParty1102d")
	public void qaFossilFullPartySendsItToThePc(GameTestHelper helper) {
		Villager scientist = scientist(helper, 40);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, UUID.randomUUID(), "qa-fossil-full");
		setDollars(player, PRICE);
		var store = Cobblemon.INSTANCE.getStorage().getParty(player);
		for (int i = 0; i < 6; i++) {
			store.add(PokemonProperties.Companion.parse("pikachu level=5", " ", "=").create());
		}
		helper.assertTrue(party(player).size() == 6, "party: " + party(player));
		helper.runAfterDelay(2, () -> {
			hold(player, new ItemStack(cobblemon("dome_fossil")), ItemStack.EMPTY);
			click(player, scientist, false, seen);
			helper.assertTrue(FossilScientists.queue(scientist).size() == 1, "not taken: " + last(seen));
			helper.succeedWhen(() -> {
				helper.assertTrue(FossilScientists.queue(scientist).isEmpty(), "still queued");
				List<String> party = party(player);
				helper.assertTrue(party.size() == 6 && count(party, "pikachu") == 6, "party: " + party);
				helper.assertTrue(pc(player).equals(List.of("kabuto")), "PC: " + pc(player));
			});
		});
	}

	// ------------------------------------------------------------------ offline owners

	/**
	 * The owner logs out right after handing a fossil over. The revival finishes and waits (nobody else gets it); when the
	 * owner logs back in it joins their party, exactly once: an empty-hand right-click afterwards hands over nothing more.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaFossilOffline1102d"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaFossilOffline1102d")
	public void qaFossilOfflineOwnerGetsItOnceWhenBack(GameTestHelper helper) {
		Villager scientist = scientist(helper, 40);
		UUID id = UUID.randomUUID();
		List<String> seen = new ArrayList<>();
		List<String> seenOther = new ArrayList<>();
		ServerPlayer first = player(helper, seen, id, "qa-fossil-away");
		ServerPlayer other = player(helper, seenOther, UUID.randomUUID(), "qa-fossil-other");
		setDollars(first, PRICE);
		ServerLevel level = helper.getLevel();
		ServerPlayer[] back = new ServerPlayer[1];
		helper.runAfterDelay(2, () -> {
			hold(first, new ItemStack(cobblemon("dome_fossil")), ItemStack.EMPTY);
			click(first, scientist, false, seen);
			helper.assertTrue(FossilScientists.queue(scientist).size() == 1, "not taken: " + last(seen));
			level.getServer().getPlayerList().remove(first);
			helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(!FossilScientists.queue(scientist).isEmpty() && FossilScientists.queue(scientist).get(0).ready(),
					"not ready yet"))
				.thenIdle(60)
				.thenExecute(() -> {
					helper.assertTrue(FossilScientists.queue(scientist).size() == 1, "the finished revival left the queue while its owner was away");
					helper.assertTrue(ModAttachments.FOSSILS_REVIVED.getOrElse(scientist, 0) == 0, "counted as revived while the owner was away");
					helper.assertTrue(party(other).isEmpty(), "another player got it: " + party(other));
					// another player's empty hand doesn't collect it
					hold(other, ItemStack.EMPTY, ItemStack.EMPTY);
					click(other, scientist, false, seenOther);
					helper.assertTrue(party(other).isEmpty() && FossilScientists.queue(scientist).size() == 1, "another player's click took it");
					LOG.info("offline: ready and kept for its owner, game time {}", level.getGameTime());
					back[0] = player(helper, seen, id, "qa-fossil-away");
				})
				.thenWaitUntil(() -> helper.assertTrue(count(party(back[0]), "kabuto") == 1, "not delivered after logging back in: " + party(back[0])))
				.thenIdle(60)
				.thenExecute(() -> {
					hold(back[0], ItemStack.EMPTY, ItemStack.EMPTY);
					click(back[0], scientist, false, seen);
					List<String> party = party(back[0]);
					helper.assertTrue(party.equals(List.of("kabuto")), "party after another click: " + party);
					helper.assertTrue(pc(back[0]).isEmpty(), "PC: " + pc(back[0]));
					helper.assertTrue(FossilScientists.queue(scientist).isEmpty(), "still queued");
					helper.assertTrue(ModAttachments.FOSSILS_REVIVED.getOrElse(scientist, 0) == 1, "revived count " + ModAttachments.FOSSILS_REVIVED.getOrElse(scientist, 0));
					helper.assertTrue(seen.stream().filter(s -> s.contains("revived your fossil")).count() == 1, "revived lines: " + seen);
					LOG.info("offline: delivered once on return, game time {}, lines {}", level.getGameTime(), seen);
				})
				.thenSucceed();
		});
	}

	/**
	 * The revival finished while its owner was away, and they log back in at night, when the scientist doesn't work. The
	 * README: it joins the party "the next time you're online" — so it arrives without waiting for morning.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaFossilOfflineNight1102d"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaFossilOfflineNight1102d")
	public void qaFossilOwnerBackAtNightGetsIt(GameTestHelper helper) {
		Villager scientist = scientist(helper, 40);
		UUID id = UUID.randomUUID();
		List<String> seen = new ArrayList<>();
		ServerPlayer first = player(helper, seen, id, "qa-fossil-night");
		setDollars(first, PRICE);
		ServerLevel level = helper.getLevel();
		ServerPlayer[] back = new ServerPlayer[1];
		PartnerShowsCompatTests.after(helper, () -> level.setDayTime(2000));
		helper.runAfterDelay(2, () -> {
			hold(first, new ItemStack(cobblemon("dome_fossil")), ItemStack.EMPTY);
			click(first, scientist, false, seen);
			level.getServer().getPlayerList().remove(first);
			helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(!FossilScientists.queue(scientist).isEmpty() && FossilScientists.queue(scientist).get(0).ready(),
					"not ready yet"))
				.thenIdle(30)
				.thenExecute(() -> {
					LOG.info("night: ready at game time {}", level.getGameTime());
					helper.setDayTime(18000);
					back[0] = player(helper, seen, id, "qa-fossil-night");
				})
				.thenIdle(300)
				.thenExecute(() -> {
					helper.assertTrue(count(party(back[0]), "kabuto") == 1,
						"back online at night (" + level.getDayTime() % 24000 + ") for 300 ticks and the finished Kabuto hasn't arrived: party " + party(back[0])
							+ ", queue " + FossilScientists.queue(scientist).size());
					LOG.info("night: delivered at day time {}, game time {}, sleeping {}, lines {}", level.getDayTime() % 24000, level.getGameTime(), scientist.isSleeping(), seen);
				})
				.thenSucceed();
		});
	}

	// ------------------------------------------------------------------ save and reload

	/**
	 * The scientist is saved and loaded again half way through a revival: the work done so far is kept (not restarted),
	 * the loaded scientist finishes it, and the Pokémon arrives once.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaFossilReload1102d"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaFossilReload1102d")
	public void qaFossilReloadMidRevivalKeepsProgressAndDeliversOnce(GameTestHelper helper) {
		Villager scientist = scientist(helper, 400);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, UUID.randomUUID(), "qa-fossil-reload");
		setDollars(player, PRICE);
		Villager[] loaded = new Villager[1];
		int[] leftAtSave = new int[1];
		helper.runAfterDelay(2, () -> {
			hold(player, new ItemStack(cobblemon("dome_fossil")), ItemStack.EMPTY);
			click(player, scientist, false, seen);
			helper.assertTrue(FossilScientists.queue(scientist).size() == 1, "not taken: " + last(seen));
			int total = FossilScientists.queue(scientist).get(0).total();
			helper.startSequence()
				.thenWaitUntil(() -> {
					Revival r = FossilScientists.queue(scientist).get(0);
					helper.assertTrue(r.left() <= total * 3 / 4, "not a quarter done yet: " + r.left() + "/" + total);
				})
				.thenExecute(() -> {
					Revival r = FossilScientists.queue(scientist).get(0);
					leftAtSave[0] = r.left();
					LOG.info("reload: saved at {}/{} left, game time {}", r.left(), r.total(), helper.getLevel().getGameTime());
					CompoundTag tag = new CompoundTag();
					scientist.saveWithoutId(tag);
					scientist.discard();
					Villager copy = EntityType.VILLAGER.create(helper.getLevel());
					copy.load(tag);
					List<Revival> q = FossilScientists.queue(copy);
					helper.assertTrue(q.size() == 1 && q.get(0).equals(r), "after a reload the queue is " + q + ", was " + r);
					helper.assertTrue(helper.getLevel().addFreshEntity(copy), "couldn't put the loaded scientist back");
					loaded[0] = copy;
				})
				.thenWaitUntil(() -> helper.assertTrue(count(party(player), "kabuto") == 1, "no Kabuto yet; queue "
					+ FossilScientists.queue(loaded[0])))
				.thenIdle(60)
				.thenExecute(() -> {
					helper.assertTrue(party(player).equals(List.of("kabuto")), "party: " + party(player));
					helper.assertTrue(FossilScientists.queue(loaded[0]).isEmpty(), "still queued");
					helper.assertTrue(ModAttachments.FOSSILS_REVIVED.getOrElse(loaded[0], 0) == 1, "revived count");
					LOG.info("reload: delivered once after the reload, game time {}", helper.getLevel().getGameTime());
				})
				.thenSucceed();
		});
	}

	// ------------------------------------------------------------------ more

	/** A whole fossil in the main hand and a Galar half in the off hand: only the whole one is taken (one fee). */
	//$ gametest_ticks_batch AREA '200' '"qaFossilMainFirst1102d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFossilMainFirst1102d")
	public void qaFossilWholeInMainHandLeavesTheOffHandHalf(GameTestHelper helper) {
		Villager scientist = scientist(helper, 3600);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, UUID.randomUUID(), "qa-fossil-main");
		setDollars(player, 5_000);
		helper.runAfterDelay(2, () -> {
			hold(player, new ItemStack(cobblemon("dome_fossil")), new ItemStack(cobblemon("fossilized_bird")));
			click(player, scientist, false, seen);
			helper.assertTrue(player.getMainHandItem().isEmpty(), "the dome wasn't taken");
			helper.assertTrue(player.getOffhandItem().is(cobblemon("fossilized_bird")) && player.getOffhandItem().getCount() == 1, "the off-hand half was taken");
			helper.assertTrue(FossilScientists.queue(scientist).size() == 1 && CobbleDollarsBank.balance(player) == 5_000 - PRICE,
				"queue " + FossilScientists.queue(scientist).size() + ", paid " + (5_000 - CobbleDollarsBank.balance(player)));
			helper.assertTrue(last(seen).contains("takes the Dome Fossil:"), "line: " + last(seen));
			helper.succeed();
		});
	}

	/** "Quicker as they level up": the same scientist takes on a fossil in less time at master level than as a novice. */
	//$ gametest_ticks_batch AREA '200' '"qaFossilLevels1102d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFossilLevels1102d")
	public void qaFossilAMasterRevivesQuickerThanANovice(GameTestHelper helper) {
		Villager scientist = scientist(helper, 3600);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, UUID.randomUUID(), "qa-fossil-levels");
		setDollars(player, 5_000);
		helper.runAfterDelay(2, () -> {
			hold(player, new ItemStack(cobblemon("dome_fossil")), ItemStack.EMPTY);
			click(player, scientist, false, seen);
			scientist.setVillagerData(scientist.getVillagerData().setLevel(5));
			hold(player, new ItemStack(cobblemon("helix_fossil")), ItemStack.EMPTY);
			click(player, scientist, false, seen);
			List<Revival> q = FossilScientists.queue(scientist);
			helper.assertTrue(q.size() == 2, "queue " + q.size());
			LOG.info("levels: novice {} ticks, master {} ticks", q.get(0).total(), q.get(1).total());
			helper.assertTrue(q.get(1).total() < q.get(0).total(), "a master takes " + q.get(1).total() + " ticks, a novice " + q.get(0).total());
			helper.assertTrue(q.get(0).total() >= 2400 && q.get(0).total() <= 4800, "a novice's revival should be about three minutes: " + q.get(0).total() + " ticks");
			helper.succeed();
		});
	}

	/** Sneaking with an empty hand is for trading: no progress report and nothing collected. */
	//$ gametest_ticks_batch AREA '200' '"qaFossilSneak1102d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFossilSneak1102d")
	public void qaFossilSneakWithAnEmptyHandTrades(GameTestHelper helper) {
		Villager scientist = scientist(helper, 3600);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, UUID.randomUUID(), "qa-fossil-sneak");
		setDollars(player, 5_000);
		helper.runAfterDelay(2, () -> {
			hold(player, new ItemStack(cobblemon("dome_fossil")), ItemStack.EMPTY);
			click(player, scientist, false, seen);
			int before = seen.size();
			hold(player, ItemStack.EMPTY, ItemStack.EMPTY);
			click(player, scientist, true, seen);
			helper.assertTrue(seen.size() == before, "a sneak click told the player: " + seen.subList(before, seen.size()));
			boolean offers = !scientist.getOffers().isEmpty();
			LOG.info("sneak: offers {}, menu {}", scientist.getOffers().size(), player.containerMenu.getClass().getSimpleName());
			helper.assertTrue(!offers || player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu,
				"a sneak click with an empty hand didn't open the trades: " + player.containerMenu);
			player.closeContainer();
			helper.assertTrue(FossilScientists.queue(scientist).size() == 1, "queue " + FossilScientists.queue(scientist).size());
			helper.succeed();
		});
	}

	/** The owner went far away (2,000 blocks) while it was revived: it still joins their party ("wherever they are"). */
	//$ gametest_ticks_batch AREA '1200' '"qaFossilFar1102d"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaFossilFar1102d")
	public void qaFossilOwnerFarAwayStillGetsIt(GameTestHelper helper) {
		Villager scientist = scientist(helper, 40);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, UUID.randomUUID(), "qa-fossil-far");
		setDollars(player, PRICE);
		helper.runAfterDelay(2, () -> {
			hold(player, new ItemStack(cobblemon("dome_fossil")), ItemStack.EMPTY);
			click(player, scientist, false, seen);
			helper.assertTrue(FossilScientists.queue(scientist).size() == 1, "not taken: " + last(seen));
			player.teleportTo(player.getX() + 2000, player.getY() + 40, player.getZ());
			helper.succeedWhen(() -> {
				helper.assertTrue(party(player).equals(List.of("kabuto")), "party: " + party(player));
				helper.assertTrue(FossilScientists.queue(scientist).isEmpty(), "still queued");
			});
		});
	}
}
