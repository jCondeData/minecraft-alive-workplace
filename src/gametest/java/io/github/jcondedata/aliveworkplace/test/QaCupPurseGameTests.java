package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane d, 2026-10-27) of ROADMAP 28.1a, the owner's change to the Cup: "100,000 PokéDollars per bout won and 500,000
 * for the final; a Cup at every festival, not every third". Not covered before: the shipped setting (a config file without
 * the key, as on a server upgraded from before) puts a Cup at every festival; a player who wins the final (a two-entrant
 * bracket, by walkover) is really paid 100,000 + 500,000 (in emeralds at dollarsPerEmerald without CobbleDollars, what
 * doesn't fit dropped at their feet) and reads the purse sentence with that sum. With CobbleDollars:
 * {@code QaCupPurseCompatTests}.
 */
public class QaCupPurseGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A Cup at every festival out of the box: the default, and a config file from before that lacks the key. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheShippedSettingPutsACupAtEveryFestival(GameTestHelper helper) {
		helper.assertTrue(new WorkplaceConfig().cupEveryFestivals == 1, "default cupEveryFestivals " + new WorkplaceConfig().cupEveryFestivals);
		helper.assertTrue(WorkplaceConfig.parse("{}").cupEveryFestivals == 1, "a file without the key: " + WorkplaceConfig.parse("{}").cupEveryFestivals);
		for (long n = -3; n <= 9; n++) {
			helper.assertTrue(Cups.isCup(n, 1), "festival " + n + " isn't a Cup with a Cup every festival");
		}
		// with festivals every 8 days from day 3: each of them is the Cup's day, not every third one
		for (long festival = 3; festival <= 3 + 8 * 6; festival += 8) {
			helper.assertTrue(Cups.cupDay(festival - 7, 3, 8, 1) == festival, "the Cup after day " + (festival - 7) + " is on "
				+ Cups.cupDay(festival - 7, 3, 8, 1) + ", not the festival on " + festival);
		}
		helper.succeed();
	}

	/** A player who keeps every system and chat line they're sent. */
	private static ServerPlayer listener(GameTestHelper helper, List<Component> seen, String name) {
		ServerLevel level = helper.getLevel();
		var cookie = CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	/**
	 * Two players, the only two entrants, so their bout is the final: one is ready, the other never comes; at two minutes
	 * the ready one wins by walkover and is paid 600,000 (6,000 emeralds at 100 a emerald), reading "The Cup's purse for
	 * your win: 6000 emeralds"; the other gets nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCupPurseFinal"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupPurseFinal")
	public void qaTheFinalsWinnerIsPaidTheFinalPurse(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Component> pSeen = new ArrayList<>();
		List<Component> qSeen = new ArrayList<>();
		ServerPlayer p = listener(helper, pSeen, "qa-purse-p");
		ServerPlayer q = listener(helper, qSeen, "qa-purse-q");
		BlockPos ring = helper.absolutePos(new BlockPos(8, 2, 8));
		p.teleportTo(ring.getX() + 0.5, ring.getY(), ring.getZ() + 0.5);
		q.teleportTo(ring.getX() + 0.5, ring.getY(), ring.getZ() + 3.5);
		BlockPos host = helper.absolutePos(new BlockPos(1, 1, 1));
		CupData.Cup cup = CupData.get(level).cup(host);
		Leftovers.after(helper, () -> CupData.get(level).forget(host));
		cup.theme = AliveWorkplace.id("grand_cup");
		cup.day = -1;
		cup.closed = true;
		CupData.Entrant a = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "qa-purse-p", 0, 0, host);
		CupData.Entrant b = new CupData.Entrant(CupData.Kind.PLAYER, q.getUUID(), "qa-purse-q", 0, 0, host);
		cup.entrants.addAll(List.of(a, b));
		cup.bracket.addAll(List.of(a, b));
		CupMatches.Call call = CupMatches.call(level, host, cup, new CupBouts.Pairing(1, a, b), ring, ring.west(8), ring.east(8));
		CupMatches.ready(p);
		pSeen.clear();
		call.calledAt = level.getGameTime() - CupMatches.WINDOW;
		CupMatches.tick(level, host, cup);
		helper.assertTrue(cup.call == null && cup.results.size() == 1 && cup.results.get(0).winner().equals(p.getUUID()),
			"the ready player didn't win the final: " + cup.results);
		helper.assertTrue(CupBouts.champion(cup) != null && CupBouts.champion(cup).id().equals(p.getUUID()), "no champion after the final");
		long dollars = CupMatches.PURSE + CupMatches.FINAL_PURSE;
		helper.assertTrue(dollars == 600_000, "the final's purse is " + dollars);
		int want = (int) (dollars / Math.max(1, Money.DOLLARS_PER_EMERALD));
		helper.runAfterDelay(2, () -> {
			int got = p.getInventory().countItem(Items.EMERALD) + level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(12),
				e -> e.getItem().is(Items.EMERALD)).stream().mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(got == want, "the champion got " + got + " emeralds, not " + want);
			helper.assertTrue(q.getInventory().countItem(Items.EMERALD) == 0, "the loser was paid");
			String expected = "The Cup's purse for your win: " + Money.describe(dollars, want).getString();
			List<String> lines = pSeen.stream().map(Component::getString).toList();
			helper.assertTrue(lines.contains(expected), "the champion didn't read \"" + expected + "\"; read " + lines);
			helper.assertTrue(Money.describe(dollars, want).getString().equals(want + " emeralds"), "the sum reads " + Money.describe(dollars, want).getString());
			helper.assertTrue(qSeen.stream().map(Component::getString).noneMatch(s -> s.startsWith("The Cup's purse")), "the loser read the purse line");
			level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16), e -> e.getItem().is(Items.EMERALD)).forEach(ItemEntity::discard);
			helper.succeed();
		});
	}
}
