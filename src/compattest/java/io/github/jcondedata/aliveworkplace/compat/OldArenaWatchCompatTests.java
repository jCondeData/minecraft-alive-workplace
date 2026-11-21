package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleRegistry;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 50.12 and saves: the Arena was rebuilt from scratch and its notice board moved, but an Arena standing in its old
 * drawing (blueprint_history/arena/v0.145.0.nbt; a finished building records no drawing version) keeps its board where
 * the old drawing put it. With the real Cobblemon: while a player bout is fought there, a click on that board shows the
 * bout and [Watch] (28.20), as on today's Arena.
 */
public class OldArenaWatchCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final String WATCH_COMMAND = "/workplace cup watch";

	private static ServerPlayer player(GameTestHelper helper, String... party) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		for (String spec : party) {
			Cobblemon.INSTANCE.getStorage().getParty(p).add(PokemonProperties.Companion.parse(spec, " ", "=").create());
		}
		return p;
	}

	/** A named player who keeps every chat line they're sent (as QaCupWatchBoard1115d2CompatTests). */
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
		return player;
	}

	private static CupData.Entrant villagerEntrant(String name, BlockPos host) {
		return new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes(("old-arena-" + name).getBytes()), name, 3, 0, host);
	}

	private static void at(ServerPlayer p, BlockPos pos) {
		p.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}

	private static void click(ServerPlayer player, BlockPos pos) {
		player.gameMode.useItemOn(player, player.serverLevel(), player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
	}

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

	private static List<String> board(List<Component> seen) {
		return seen.stream().map(Component::getString).filter(l -> l.startsWith("Being fought now")).toList();
	}

	/** The old drawing of {@code id} as the mod ships it in blueprint_history. */
	private static StructureTemplate oldTemplate(ServerLevel level, ResourceLocation id) throws Exception {
		ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "blueprint_history/" + id.getPath() + "/v0.145.0.nbt");
		try (InputStream in = level.getServer().getResourceManager().getResource(file).orElseThrow().open()) {
			CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
			StructureTemplate template = new StructureTemplate();
			template.load(Lookup.lookup(BuiltInRegistries.BLOCK), tag);
			return template;
		}
	}

	/**
	 * An Arena of the old drawing, standing: the village's Arena is read with its old notice board, and a click on that board
	 * while a player bout is fought shows "Being fought now: Pia against Quinn. [Watch]" ([Watch] runs /workplace cup
	 * watch); a click where today's drawing has its board shows nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"oldArenaWatch"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "oldArenaWatch")
	public void theOldArenasNoticeBoardShowsTheBoutAndWatch(GameTestHelper helper) throws Exception {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 15));
		BlockPos origin = host.offset(-12, -1, -14);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		oldTemplate(level, StarterBlueprints.ARENA.id()).placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(50_12L), 2);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENA.id(), placement, UUID.randomUUID());
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = player(helper, "pikachu level=25");
		ServerPlayer q = player(helper, "rattata level=10");
		ServerPlayer r = listener(helper, seen, "Rin");
		try {
			Arenas.Arena arena = Arenas.find(level, host).orElse(null);
			helper.assertTrue(arena != null, "setup: the village has no Arena");
			BlockPos oldBoard = Arenas.world(placement, Arenas.V0145_NOTICE_BOARD);
			helper.assertTrue(arena.noticeBoard().equals(oldBoard), "the old Arena's notice board is read at " + helper.relativePos(arena.noticeBoard())
				+ ", not at its board " + helper.relativePos(oldBoard));
			helper.assertTrue(arena.ring().equals(Arenas.world(placement, Arenas.V0145_RING_CENTRE)), "the old Arena's ring is read at " + helper.relativePos(arena.ring()));
			BlockPos ring = arena.ring();
			at(p, ring);
			at(q, ring);
			at(r, oldBoard.north());
			CupData.Entrant a = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "Pia", 0, 0, host);
			CupData.Entrant b = new CupData.Entrant(CupData.Kind.PLAYER, q.getUUID(), "Quinn", 0, 0, host);
			CupData.Cup cup = CupData.get(level).cup(host);
			cup.theme = theme.id();
			cup.day = -1;
			cup.closed = true;
			List<CupData.Entrant> four = List.of(a, b, villagerEntrant("Dara", host), villagerEntrant("Kai", host));
			cup.entrants.addAll(four);
			cup.bracket.addAll(four);
			CupMatches.call(level, host, cup, new CupBouts.Pairing(1, a, b), ring, arena.boxes().get(0), arena.boxes().get(1));
			CupMatches.ready(p);
			CupMatches.ready(q);
			PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(p);
			helper.assertTrue(battle != null && battle.getBattleId().equals(CupMatches.running(level, host)), "setup: the players' bout isn't running");
			seen.clear();
			click(r, oldBoard);
			List<Component> shown = seen.stream().filter(c -> c.getString().startsWith("Being fought now")).toList();
			helper.assertTrue(shown.size() == 1, "clicking the old Arena's notice board during the bout showed " + seen.stream().map(Component::getString).toList());
			helper.assertTrue(shown.get(0).getString().equals("Being fought now: Pia against Quinn. [Watch]"), "the notice board reads: " + shown.get(0).getString());
			helper.assertTrue(WATCH_COMMAND.equals(watchClick(shown.get(0))), "the board's [Watch] runs " + watchClick(shown.get(0)));
			// where today's drawing has its board, this Arena has a bare pitch
			seen.clear();
			click(r, Arenas.world(placement, Arenas.NOTICE_BOARD));
			helper.assertTrue(board(seen).isEmpty(), "a click where today's board would be shows the bout: " + board(seen));
		} finally {
			for (ServerPlayer s : List.of(p, q)) {
				PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(s);
				if (left != null) {
					left.end();
				}
			}
			BuildSiteManager.get(level).forgetFinished(placement);
			CupData.get(level).forget(host);
			level.getServer().getPlayerList().remove(r);
		}
		helper.succeed();
	}
}
