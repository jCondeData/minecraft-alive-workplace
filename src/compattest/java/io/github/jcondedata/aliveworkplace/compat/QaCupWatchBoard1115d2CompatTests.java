package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-d-1115-1308), a mutation pass over watching a player bout, the Arena's notice board and the battle's start
 * (ROADMAP 28.20: "[Watch] on the Cup page and the Arena's notice board puts a player in Cobblemon's spectator view of a
 * running player bout"), written from the spec: a click on the notice board while a bout is fought shows who's fighting
 * and [Watch] (and nothing before it begins, after it ends, or on a block away from the board); [Watch] picks the
 * nearest running bout; the bout's start is called at the Arena with [Watch], and not to a player far away; a player
 * already in a battle who clicks [I'm ready] is told to click again and isn't pulled out of their own battle (alone, or
 * as the second player of a players' bout); the villager trainer steps into their box for the battle; the Cup page has
 * [Watch] only while a bout is fought.
 */
public class QaCupWatchBoard1115d2CompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final String WATCH_COMMAND = "/workplace cup watch";
	private static final String READY_COMMAND = "/workplace cup ready";

	private static ServerPlayer player(GameTestHelper helper, String... party) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		for (String spec : party) {
			Cobblemon.INSTANCE.getStorage().getParty(p).add(PokemonProperties.Companion.parse(spec, " ", "=").create());
		}
		return p;
	}

	/** A named player who keeps every chat line they're sent (as QaCup1115dCompatTests). */
	private static ServerPlayer listener(GameTestHelper helper, List<Component> seen, String name, String... party) {
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
		for (String spec : party) {
			Cobblemon.INSTANCE.getStorage().getParty(player).add(PokemonProperties.Companion.parse(spec, " ", "=").create());
		}
		return player;
	}

	private static CupData.Entrant villagerEntrant(String name, BlockPos host) {
		return new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes(("qa1115d2-" + name).getBytes()), name, 3, 0, host);
	}

	/** A Cup at {@code host} with {@code theme} whose bracket is {@code entrants} (four: the first bout isn't the final). */
	private static CupData.Cup cup(GameTestHelper helper, BlockPos host, CupThemes.Theme theme, CupData.Entrant... entrants) {
		CupData.Cup cup = CupData.get(helper.getLevel()).cup(host);
		cup.theme = theme.id();
		cup.day = -1;
		cup.closed = true;
		cup.entrants.addAll(List.of(entrants));
		cup.bracket.addAll(List.of(entrants));
		return cup;
	}

	private static void endBattles(ServerPlayer... players) {
		for (ServerPlayer p : players) {
			PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(p);
			if (left != null) {
				left.end();
			}
		}
	}

	private static void at(ServerPlayer p, BlockPos pos) {
		p.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}

	/** The player right-clicks the block at {@code pos} with their main hand, as the game does. */
	private static InteractionResult click(ServerPlayer player, BlockPos pos) {
		return player.gameMode.useItemOn(player, player.serverLevel(), player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
	}

	/** The command a line's clickable "[Watch]" runs, or null if it has none. */
	private static String watchClick(Component line) {
		String[] found = {null};
		line.visit((style, text) -> {
			ClickEvent click = style.getClickEvent();
			if (text.equals("[Watch]") && click != null && click.getAction() == ClickEvent.Action.RUN_COMMAND) {
				found[0] = click.getValue();
			}
			return Optional.empty();
		}, Style.EMPTY);
		return found[0];
	}

	private static List<String> strings(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	@org.jetbrains.annotations.Nullable
	private static BattleActor actorOf(PokemonBattle battle, UUID id) {
		for (BattleActor a : battle.getActors()) {
			if (a.getUuid().equals(id)) {
				return a;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ the Arena's notice board

	/**
	 * The Arena's notice board (28.20): a player who right-clicks it while a player bout is fought there reads "Being fought
	 * now: Pia against Quinn. [Watch]", and [Watch] runs /workplace cup watch. Before the bout begins, after its battle
	 * ended, and on a block three blocks from the board, the click shows nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCupBoard1115d2"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupBoard1115d2")
	public void theNoticeBoardShowsTheBoutAndWatch(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 15));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), host.offset(-12, -1, -14), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENA.id(), placement, UUID.randomUUID());
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = player(helper, "pikachu level=25");
		ServerPlayer q = player(helper, "rattata level=10");
		ServerPlayer r = listener(helper, seen, "Rin", "pidgey level=10");
		try {
			Arenas.Arena arena = Arenas.find(level, host).orElse(null);
			helper.assertTrue(arena != null, "setup: the village has no Arena");
			BlockPos ring = arena.ring();
			BlockPos board = arena.noticeBoard();
			at(p, ring);
			at(q, ring);
			at(r, board.south());
			CupData.Entrant a = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "Pia", 0, 0, host);
			CupData.Entrant b = new CupData.Entrant(CupData.Kind.PLAYER, q.getUUID(), "Quinn", 0, 0, host);
			CupData.Cup cup = cup(helper, host, theme, a, b, villagerEntrant("Dara", host), villagerEntrant("Kai", host));
			CupMatches.call(level, host, cup, new CupBouts.Pairing(1, a, b), ring, ring.west(10), ring.east(10));
			seen.clear();
			click(r, board);
			helper.assertTrue(strings(seen).stream().noneMatch(l -> l.startsWith("Being fought now")), "the board shows a bout nobody is fighting yet: " + strings(seen));
			CupMatches.ready(p);
			CupMatches.ready(q);
			PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(p);
			helper.assertTrue(battle != null && battle.getBattleId().equals(CupMatches.running(level, host)), "setup: the players' bout isn't running");
			seen.clear();
			InteractionResult result = click(r, board);
			List<Component> shown = seen.stream().filter(c -> c.getString().startsWith("Being fought now")).toList();
			helper.assertTrue(shown.size() == 1, "clicking the notice board during the bout showed " + strings(seen));
			helper.assertTrue(shown.get(0).getString().equals("Being fought now: Pia against Quinn. [Watch]"), "the notice board reads: " + shown.get(0).getString());
			helper.assertTrue(WATCH_COMMAND.equals(watchClick(shown.get(0))), "the board's [Watch] runs " + watchClick(shown.get(0)));
			helper.assertTrue(result.consumesAction(), "the click on the board wasn't taken: " + result);
			// a block three away from the board isn't the board
			seen.clear();
			click(r, board.east(3));
			helper.assertTrue(strings(seen).stream().noneMatch(l -> l.startsWith("Being fought now")), "a block 3 from the board shows the bout: " + strings(seen));
			// the board's [Watch]
			String watched = CupMatches.watch(r).getString();
			helper.assertTrue(watched.equals("You're watching the bout.") && battle.getSpectators().contains(r.getUUID()), "the board's [Watch]: " + watched);
			// the battle is over: nothing on the board
			battle.end();
			seen.clear();
			click(r, board);
			helper.assertTrue(strings(seen).stream().noneMatch(l -> l.startsWith("Being fought now")), "the board shows a bout whose battle ended: " + strings(seen));
		} finally {
			endBattles(p, q);
			BuildSiteManager.get(level).forgetFinished(placement);
			CupData.get(level).forget(host);
			level.getServer().getPlayerList().remove(r);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ [Watch] on the Cup page

	/**
	 * [Watch] on the Cup page (28.20): while a player bout is fought at the host, the village hall's Cup page has "Watch the
	 * bout", and pressing it puts the viewer in the battle's spectators; before the bout begins and after its battle ended
	 * the page has no such button.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCupPageWatch1115d2"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupPageWatch1115d2")
	public void theCupPageHasWatchOnlyWhileABoutIsFought(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos hallAt = new BlockPos(15, 2, 15);
		helper.setBlock(hallAt, io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL);
		ServerLevel level = helper.getLevel();
		BlockPos host = helper.absolutePos(hallAt);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), host.offset(-12, -1, 3), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENA.id(), placement, UUID.randomUUID());
		helper.runAfterDelay(2, () -> {
			CupThemes.Theme theme = CupMatchCompatTests.openSingles();
			io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) helper.getBlockEntity(hallAt);
			entity.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.VILLAGE);
			entity.setCustomName(Component.literal("Thornvale"));
			ServerPlayer p = player(helper, "pikachu level=25");
			ServerPlayer q = player(helper, "rattata level=10");
			ServerPlayer r = player(helper, "pidgey level=10");
			BlockPos ring = helper.absolutePos(new BlockPos(15, 2, 8));
			at(p, ring);
			at(q, ring);
			at(r, host.south(2));
			try {
				CupData.Entrant a = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "Pia", 0, 0, host);
				CupData.Entrant b = new CupData.Entrant(CupData.Kind.PLAYER, q.getUUID(), "Quinn", 0, 0, host);
				CupData.Cup cup = cup(helper, host, theme, a, b, villagerEntrant("Dara", host), villagerEntrant("Kai", host));
				cup.day = (int) (level.getDayTime() / 24000L) + 3;
				CupMatches.call(level, host, cup, new CupBouts.Pairing(1, a, b), ring, ring.west(10), ring.east(10));
				io.github.jcondedata.aliveworkplace.work.ChoiceMenu menu = cupPage(r, host);
				helper.assertTrue(menu.icon(io.github.jcondedata.aliveworkplace.cup.CupPage.CARD).getItem() != net.minecraft.world.item.Items.PAPER,
					"setup: the Cup page shows no Cup");
				helper.assertTrue(menu.icon(io.github.jcondedata.aliveworkplace.cup.CupPage.WATCH).isEmpty(),
					"the Cup page offers [Watch] before the bout begins: " + menu.icon(io.github.jcondedata.aliveworkplace.cup.CupPage.WATCH));
				CupMatches.ready(p);
				CupMatches.ready(q);
				PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(p);
				helper.assertTrue(battle != null && battle.getBattleId().equals(CupMatches.running(level, host)), "setup: the players' bout isn't running");
				menu = cupPage(r, host);
				net.minecraft.world.item.ItemStack watch = menu.icon(io.github.jcondedata.aliveworkplace.cup.CupPage.WATCH);
				helper.assertTrue(watch.is(net.minecraft.world.item.Items.ENDER_EYE) && watch.getHoverName().getString().equals("Watch the bout"),
					"the Cup page has no [Watch] during the bout: " + watch + " " + watch.getHoverName().getString());
				menu.press(io.github.jcondedata.aliveworkplace.cup.CupPage.WATCH, r);
				helper.assertTrue(battle.getSpectators().contains(r.getUUID()), "pressing Watch the bout didn't make the viewer a spectator");
				battle.end();
				menu = cupPage(r, host);
				helper.assertTrue(menu.icon(io.github.jcondedata.aliveworkplace.cup.CupPage.WATCH).isEmpty(), "the Cup page offers [Watch] after the battle ended");
			} finally {
				endBattles(p, q);
				BuildSiteManager.get(level).forgetFinished(placement);
				CupData.get(level).forget(host);
			}
			helper.succeed();
		});
	}

	private static io.github.jcondedata.aliveworkplace.work.ChoiceMenu cupPage(ServerPlayer player, BlockPos hall) {
		io.github.jcondedata.aliveworkplace.work.ChoiceMenu menu = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.forTest(player, hall);
		menu.press(io.github.jcondedata.aliveworkplace.hall.HallPages.slot(io.github.jcondedata.aliveworkplace.cup.CupPage.PAGE), player);
		return menu;
	}

	// ------------------------------------------------------------------ [Watch]: the nearest bout

	/**
	 * Two player bouts are fought at once at two villages' rings 30 blocks apart: [Watch] ("the spectator view of the
	 * nearest running player bout") puts each watcher in the bout at the ring next to them, not the other.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCupWatchNear1115d2"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupWatchNear1115d2")
	public void watchPicksTheNearestBout(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host1 = helper.absolutePos(new BlockPos(6, 2, 6));
		BlockPos host2 = helper.absolutePos(new BlockPos(24, 2, 24));
		BlockPos ring1 = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos ring2 = helper.absolutePos(new BlockPos(26, 2, 26));
		ServerPlayer p1 = player(helper, "pikachu level=25");
		ServerPlayer q1 = player(helper, "rattata level=10");
		ServerPlayer p2 = player(helper, "pikachu level=25");
		ServerPlayer q2 = player(helper, "rattata level=10");
		ServerPlayer r1 = player(helper, "pidgey level=10");
		ServerPlayer r2 = player(helper, "pidgey level=10");
		at(p1, ring1);
		at(q1, ring1);
		at(p2, ring2);
		at(q2, ring2);
		at(r1, ring1.east(2));
		at(r2, ring2.west(2));
		try {
			CupData.Entrant a1 = new CupData.Entrant(CupData.Kind.PLAYER, p1.getUUID(), "Pia", 0, 0, host1);
			CupData.Entrant b1 = new CupData.Entrant(CupData.Kind.PLAYER, q1.getUUID(), "Quinn", 0, 0, host1);
			CupData.Entrant a2 = new CupData.Entrant(CupData.Kind.PLAYER, p2.getUUID(), "Paz", 0, 0, host2);
			CupData.Entrant b2 = new CupData.Entrant(CupData.Kind.PLAYER, q2.getUUID(), "Quill", 0, 0, host2);
			CupData.Cup cup1 = cup(helper, host1, theme, a1, b1, villagerEntrant("Dara", host1), villagerEntrant("Kai", host1));
			CupData.Cup cup2 = cup(helper, host2, theme, a2, b2, villagerEntrant("Ren", host2), villagerEntrant("Mo", host2));
			CupMatches.call(level, host1, cup1, new CupBouts.Pairing(1, a1, b1), ring1, ring1.south(5), ring1.east(5));
			CupMatches.call(level, host2, cup2, new CupBouts.Pairing(1, a2, b2), ring2, ring2.north(5), ring2.west(5));
			CupMatches.ready(p1);
			CupMatches.ready(q1);
			CupMatches.ready(p2);
			CupMatches.ready(q2);
			PokemonBattle battle1 = BattleRegistry.getBattleByParticipatingPlayer(p1);
			PokemonBattle battle2 = BattleRegistry.getBattleByParticipatingPlayer(p2);
			helper.assertTrue(battle1 != null && battle2 != null && battle1 != battle2, "setup: two bouts aren't running");
			String one = CupMatches.watch(r1).getString();
			String two = CupMatches.watch(r2).getString();
			helper.assertTrue(one.equals("You're watching the bout.") && two.equals("You're watching the bout."), "watch: " + one + " / " + two);
			helper.assertTrue(battle1.getSpectators().contains(r1.getUUID()) && !battle2.getSpectators().contains(r1.getUUID()),
				"a player by the first ring isn't watching the bout there (watching the far one: " + battle2.getSpectators().contains(r1.getUUID()) + ")");
			helper.assertTrue(battle2.getSpectators().contains(r2.getUUID()) && !battle1.getSpectators().contains(r2.getUUID()),
				"a player by the second ring isn't watching the bout there (watching the far one: " + battle1.getSpectators().contains(r2.getUUID()) + ")");
		} finally {
			endBattles(p1, p2);
			CupData.get(level).forget(host1);
			CupData.get(level).forget(host2);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the battle's start

	/**
	 * Both players are ready and the battle starts (28.20): a player watching from the stands (10 blocks off) reads
	 * "Round 1 of the Grand Cup: Pia of … against Quinn of … [Watch]" with a [Watch] that runs /workplace cup watch; a
	 * player 70 blocks away isn't told. The second player of the bout finds nothing to [Watch] (they're in it).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCupStart1115d2"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupStart1115d2")
	public void theBoutsStartIsCalledAtTheArenaWithWatch(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 22));
		BlockPos ring = helper.absolutePos(new BlockPos(15, 2, 12));
		List<Component> near = new ArrayList<>();
		List<Component> far = new ArrayList<>();
		ServerPlayer p = player(helper, "pikachu level=25");
		ServerPlayer q = player(helper, "rattata level=10");
		ServerPlayer stands = listener(helper, near, "Sol", "pidgey level=10");
		ServerPlayer away = listener(helper, far, "Fen", "pidgey level=10");
		at(p, ring);
		at(q, ring);
		at(stands, ring.south(10));
		at(away, ring.east(70));
		try {
			CupData.Entrant a = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "Pia", 0, 0, host);
			CupData.Entrant b = new CupData.Entrant(CupData.Kind.PLAYER, q.getUUID(), "Quinn", 0, 0, host);
			CupData.Cup cup = cup(helper, host, theme, a, b, villagerEntrant("Dara", host), villagerEntrant("Kai", host));
			CupMatches.call(level, host, cup, new CupBouts.Pairing(1, a, b), ring, ring.west(10), ring.east(10));
			near.clear();
			far.clear();
			CupMatches.ready(p);
			CupMatches.ready(q);
			helper.assertTrue(cup.call != null && cup.call.fighting && BattleRegistry.getBattleByParticipatingPlayer(p) != null, "setup: the battle didn't start");
			List<Component> start = near.stream().filter(c -> c.getString().startsWith("Round 1 of the ")).toList();
			helper.assertTrue(start.size() == 1, "the stands weren't told the bout began: " + strings(near));
			String line = start.get(0).getString();
			helper.assertTrue(line.startsWith("Round 1 of the Grand Cup: Pia of ") && line.contains(" against Quinn of ") && line.endsWith(" [Watch]"),
				"the stands read: " + line);
			helper.assertTrue(WATCH_COMMAND.equals(watchClick(start.get(0))), "the start's [Watch] runs " + watchClick(start.get(0)));
			helper.assertTrue(strings(far).stream().noneMatch(l -> l.startsWith("Round 1 of the ")), "a player 70 blocks away was told the bout began: " + strings(far));
			String second = CupMatches.watch(q).getString();
			helper.assertTrue(second.equals("No player bout is being fought right now."), "the bout's second player was offered their own bout to watch: " + second);
		} finally {
			endBattles(p, q);
			CupData.get(level).forget(host);
			level.getServer().getPlayerList().remove(stands);
			level.getServer().getPlayerList().remove(away);
		}
		helper.succeed();
	}

	/**
	 * A bout between two players whose second player is already in a battle of their own (a wild one) when both click
	 * [I'm ready] (28.20): no Cup battle starts, the busy player stays in their own battle, the first player isn't put in a
	 * battle, and neither counts as ready (both must click again).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCupPvpBusy1115d2"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupPvpBusy1115d2")
	public void aBusySecondPlayerHoldsUpAPlayersBout(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 24));
		BlockPos ring = helper.absolutePos(new BlockPos(15, 2, 12));
		ServerPlayer p = player(helper, "pikachu level=25");
		ServerPlayer q = player(helper, "rattata level=10");
		at(p, ring);
		at(q, ring.east(2));
		PokemonEntity wild = new PokemonEntity(level, PokemonProperties.Companion.parse("caterpie level=3", " ", "=").create(), CobblemonEntities.POKEMON);
		wild.moveTo(q.getX() + 2, q.getY(), q.getZ());
		wild.setNoAi(true);
		level.addFreshEntity(wild);
		try {
			BattleStartResult own = BattleBuilder.INSTANCE.pve(q, wild);
			helper.assertTrue(own instanceof SuccessfulBattleStart, "setup: the wild battle didn't start: " + own);
			UUID ownId = ((SuccessfulBattleStart) own).getBattle().getBattleId();
			CupData.Entrant a = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "Pia", 0, 0, host);
			CupData.Entrant b = new CupData.Entrant(CupData.Kind.PLAYER, q.getUUID(), "Quinn", 0, 0, host);
			CupData.Cup cup = cup(helper, host, theme, a, b, villagerEntrant("Dara", host), villagerEntrant("Kai", host));
			CupMatches.call(level, host, cup, new CupBouts.Pairing(1, a, b), ring, ring.west(10), ring.east(10));
			CupMatches.ready(p);
			CupMatches.ready(q);
			PokemonBattle mine = BattleRegistry.getBattleByParticipatingPlayer(p);
			PokemonBattle theirs = BattleRegistry.getBattleByParticipatingPlayer(q);
			helper.assertTrue(mine == null, "a Cup battle started while the second player was in a battle of their own");
			helper.assertTrue(theirs != null && theirs.getBattleId().equals(ownId), "the second player was taken out of their own battle: " + theirs);
			helper.assertTrue(cup.call != null && !cup.call.fighting && !cup.call.ready[0] && !cup.call.ready[1] && cup.results.isEmpty(),
				"after the bout couldn't start: fighting " + (cup.call != null && cup.call.fighting) + ", results " + cup.results);
		} finally {
			endBattles(p, q);
			wild.discard();
			CupData.get(level).forget(host);
		}
		helper.succeed();
	}

	/**
	 * A player already in a battle of their own (a wild one) clicks [I'm ready] for their bout against a villager (28.20):
	 * they read "The bout couldn't start (are you already in a battle?). Click [I'm ready] again.", stay in their own
	 * battle, aren't marked ready, and get a new two minutes; once their battle is over, [I'm ready] starts the bout.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCupCantStart1115d2"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupCantStart1115d2")
	public void aPlayerInABattleIsToldToClickReadyAgain(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 24));
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = listener(helper, seen, "Ash", "bulbasaur level=20");
		BlockPos ring = helper.absolutePos(new BlockPos(15, 2, 12));
		at(p, ring);
		Villager mira = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 12));
		mira.setNoAi(true);
		mira.setVillagerData(mira.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(3));
		mira.setCustomName(Component.literal("Mira"));
		PokemonEntity wild = new PokemonEntity(level, PokemonProperties.Companion.parse("caterpie level=3", " ", "=").create(), CobblemonEntities.POKEMON);
		wild.moveTo(p.getX() + 2, p.getY(), p.getZ());
		wild.setNoAi(true);
		level.addFreshEntity(wild);
		try {
			BattleStartResult own = BattleBuilder.INSTANCE.pve(p, wild);
			helper.assertTrue(own instanceof SuccessfulBattleStart, "setup: the wild battle didn't start: " + own);
			UUID ownId = ((SuccessfulBattleStart) own).getBattle().getBattleId();
			CupData.Entrant me = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "Ash", 0, 0, host);
			CupData.Entrant her = new CupData.Entrant(CupData.Kind.HOST_TRAINER, mira.getUUID(), "Mira", 2, 0, host);
			CupData.Cup cup = cup(helper, host, theme, me, her, villagerEntrant("Dara", host), villagerEntrant("Kai", host));
			CupMatches.call(level, host, cup, new CupBouts.Pairing(1, me, her), ring, ring.west(10), ring.east(10));
			long now = level.getGameTime();
			cup.call.calledAt = now - 100; // the call came five seconds ago
			seen.clear();
			// B1336: the player clicks [I'm ready], which runs the command; since B1291 the "couldn't start" line is its reply
			level.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), READY_COMMAND);
			List<String> lines = strings(seen);
			String again = "The bout couldn't start (are you already in a battle?). Click [I'm ready] again.";
			helper.assertTrue(lines.stream().filter(again::equals).count() == 1, "a player in a battle wasn't told once to click again: " + lines);
			PokemonBattle current = BattleRegistry.getBattleByParticipatingPlayer(p);
			helper.assertTrue(current != null && current.getBattleId().equals(ownId), "the player was taken out of their own battle: " + current);
			helper.assertTrue(cup.call != null && !cup.call.fighting && cup.call.battle == null, "the bout counts as fought");
			helper.assertTrue(!cup.call.ready[0], "the player still counts as ready, though they were told to click [I'm ready] again");
			helper.assertTrue(cup.call.calledAt == now, "no new two minutes: called at " + cup.call.calledAt + ", now " + now);
			helper.assertTrue(cup.results.isEmpty(), "the bout was decided: " + cup.results);
			current.end();
			level.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), READY_COMMAND);
			PokemonBattle bout = BattleRegistry.getBattleByParticipatingPlayer(p);
			helper.assertTrue(bout != null && !bout.getBattleId().equals(ownId) && actorOf(bout, mira.getUUID()) != null && cup.call.fighting,
				"once free, [I'm ready] didn't start the bout against Mira");
			// Mira (20 blocks off, at the Arena) steps into her box across the ring for the battle
			Vec3 box = Vec3.atBottomCenterOf(ring.east(10));
			helper.assertTrue(mira.position().distanceTo(box) < 1, "the trainer isn't in her box for the battle: at " + mira.blockPosition() + ", box " + ring.east(10));
		} finally {
			endBattles(p);
			wild.discard();
			CupData.get(level).forget(host);
			level.getServer().getPlayerList().remove(p);
		}
		helper.succeed();
	}
}
