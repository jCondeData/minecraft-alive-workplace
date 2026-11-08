package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.bounty.Rogues;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonRogues;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Bank;
import io.github.jcondedata.aliveworkplace.work.Money;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA, lane D (2026-11-08): bounties in the Cobbleverse pack, where CobbleDollars keeps the money. 31.14 and the wiki
 * (bounties.md: "Paid in emeralds, or in CobbleDollars (100 a emerald) where that mod keeps the money") pay a bounty
 * in CobbleDollars; every other price and prize in the mod then names CobbleDollars too ({@code Money.describe}:
 * a quest's "1,500 CobbleDollars"). These tests check what the player reads about a bounty's reward in that world:
 * the hand-in and the win messages, the chronicle lines, the posting and the Wanted Poster. Each must name the money
 * the player actually gets, not "15 emeralds" while no emerald arrives.
 */
public class QaBountyDollars1108dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos SPOT = new BlockPos(18, 2, 18);

	private record Client(ServerPlayer player, List<Component> seen) {
	}

	private static BlockPos village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(48)).forEach(Entity::discard);
		int radius = VillageHalls.RADIUS;
		boolean enabled = Bounties.ENABLED;
		boolean rogues = Rogues.ENABLED;
		VillageHalls.RADIUS = 10;
		Bounties.ENABLED = true;
		Rogues.ENABLED = true;
		Bounties.forget(level);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		hall.setLastQuestDay(Long.MAX_VALUE / 2);
		hall.setRank(VillageRanks.Rank.VILLAGE);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Bounties.ENABLED = enabled;
			Rogues.ENABLED = rogues;
			Bounties.forget(level);
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(48)).forEach(Entity::discard);
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
		});
		return helper.absolutePos(HALL);
	}

	/** A real player in the level (so a posting reaches them) whose chat lines are kept. */
	private static Client client(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qab1108d" + (int) (Math.random() * 100000)), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}

			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))));
		return new Client(player, seen);
	}

	private static void run(GameTestHelper helper, Client c, Runnable body) {
		try {
			helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
			body.run();
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(c.player());
		}
	}

	private static long dollars(ServerPlayer player) {
		return Bank.EXTENSION.call(bank -> bank.balance(player), 0L);
	}

	/** "1,500 CobbleDollars": what the mod's own money line says for {@code emeralds} emeralds' worth. */
	private static String priced(int emeralds) {
		return String.format(Locale.ROOT, "%,d", (long) emeralds * Money.DOLLARS_PER_EMERALD);
	}

	/** A line about a reward of {@code emeralds} names the CobbleDollars paid and no emeralds. */
	private static void namesTheDollars(GameTestHelper helper, String line, int emeralds, String what) {
		String lower = line.toLowerCase(Locale.ROOT);
		helper.assertTrue(!lower.contains("emerald") && lower.contains("cobbledollars") && line.contains(priced(emeralds)),
			what + " should name the " + priced(emeralds) + " CobbleDollars paid (CobbleDollars keeps the money), but reads: '" + line + "'");
	}

	private static String chronicle(GameTestHelper helper, String contains) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.BOUNTY && e.text().getString().contains(contains))
			.map(e -> e.text().getString()).reduce((a, b) -> b).orElse("");
	}

	/**
	 * A bandit captain's insignia handed in at the hall: the player gets the reward in CobbleDollars (no emerald), and
	 * the green "Paid ..." line and the chronicle's "... was paid ..." both say CobbleDollars, with the sum paid.
	 */
	//$ gametest_ticks_batch AREA '200' '"qab1108dInsignia"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qab1108dInsignia")
	public void anInsigniaHandedInSaysTheCobbleDollarsItPaid(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Client c = client(helper);
		ServerPlayer p = c.player();
		run(helper, c, () -> {
			ResourceLocation quirk = Bounties.quirks().keySet().iterator().next();
			Bounties.Bounty b = Bounties.post(level, hall, quirk, 0, 0, helper.absolutePos(SPOT));
			helper.assertTrue(b.reward > 0, "setup: the bounty has no reward");
			long before = dollars(p);
			c.seen().clear();
			ItemStack insignia = Bounties.insignia(level, b);
			helper.assertTrue(Bounties.claim(p, level, hall, insignia) == Bounties.Claim.PAID, "the insignia wasn't paid");
			helper.assertTrue(dollars(p) - before == (long) b.reward * Money.DOLLARS_PER_EMERALD && p.getInventory().countItem(Items.EMERALD) == 0,
				"paid " + (dollars(p) - before) + " CobbleDollars and " + p.getInventory().countItem(Items.EMERALD) + " emeralds for a " + b.reward + "-emerald bounty");
			String paid = c.seen().stream().map(Component::getString).filter(s -> s.startsWith("Paid")).findFirst().orElse("");
			helper.assertTrue(!paid.isEmpty(), "no 'Paid ...' line; the player saw: " + c.seen().stream().map(Component::getString).collect(Collectors.toList()));
			namesTheDollars(helper, paid, b.reward, "the hand-in line");
			namesTheDollars(helper, chronicle(helper, "handed in the insignia"), b.reward, "the chronicle's hand-in line");
			helper.succeed();
		});
	}

	/** A rogue Pokémon beaten: the reward arrives as CobbleDollars, and "The bounty is yours: ..." and its chronicle line say so. */
	//$ gametest_ticks_batch AREA '200' '"qab1108dRogue"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qab1108dRogue")
	public void aRogueBeatenSaysTheCobbleDollarsItPaid(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Client c = client(helper);
		ServerPlayer p = c.player();
		run(helper, c, () -> {
			Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:tauros"), helper.absolutePos(SPOT));
			Entity e = Rogues.spawn(level, b, null);
			helper.assertTrue(e instanceof PokemonEntity, "no rogue: " + e);
			PokemonEntity rogue = (PokemonEntity) e;
			rogue.setNoAi(true);
			long before = dollars(p);
			c.seen().clear();
			helper.assertTrue(CobblemonRogues.beaten(level.getServer(), rogue.getPokemon(), p, false), "the win didn't pay");
			helper.assertTrue(dollars(p) - before == (long) b.reward * Money.DOLLARS_PER_EMERALD && p.getInventory().countItem(Items.EMERALD) == 0,
				"paid " + (dollars(p) - before) + " CobbleDollars and " + p.getInventory().countItem(Items.EMERALD) + " emeralds for a " + b.reward + "-emerald bounty");
			String won = c.seen().stream().map(Component::getString).filter(s -> s.contains("The bounty is yours")).findFirst().orElse("");
			helper.assertTrue(!won.isEmpty(), "no 'The bounty is yours' line; the player saw: " + c.seen().stream().map(Component::getString).collect(Collectors.toList()));
			namesTheDollars(helper, won, b.reward, "the win line");
			namesTheDollars(helper, chronicle(helper, "in battle and was paid"), b.reward, "the chronicle's win line");
			helper.succeed();
		});
	}

	/** A bounty posted while the player is in the village: the posting's "Reward: ..." and the Wanted Poster's reward line name CobbleDollars. */
	//$ gametest_ticks_batch AREA '200' '"qab1108dPoster"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qab1108dPoster")
	public void thePostingAndTheWantedPosterPriceTheRewardInCobbleDollars(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Client c = client(helper);
		run(helper, c, () -> {
			c.seen().clear();
			ResourceLocation quirk = Bounties.quirks().keySet().iterator().next();
			Bounties.Bounty b = Bounties.post(level, hall, quirk, 0, 0, helper.absolutePos(SPOT));
			String posted = c.seen().stream().map(Component::getString).filter(s -> s.contains("posts a bounty")).findFirst().orElse("");
			helper.assertTrue(!posted.isEmpty(), "the player in the village wasn't told of the bounty; saw: "
				+ c.seen().stream().map(Component::getString).collect(Collectors.toList()));
			namesTheDollars(helper, posted.substring(Math.max(0, posted.indexOf("Reward"))), b.reward, "the posting's reward");
			ItemLore lore = Bounties.poster(level, b).get(DataComponents.LORE);
			String reward = lore == null ? "" : lore.lines().stream().map(Component::getString).filter(s -> s.startsWith("Reward")).findFirst().orElse("");
			helper.assertTrue(!reward.isEmpty(), "the Wanted Poster has no reward line");
			namesTheDollars(helper, reward, b.reward, "the Wanted Poster's reward line");
			helper.succeed();
		});
	}
}
