package io.github.jcondedata.aliveworkplace.test;

import com.mojang.authlib.GameProfile;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintNames;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.card.CardToasts;
import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.StatusCardLayout;
import io.github.jcondedata.aliveworkplace.ui.StatusCardLayout.Boxes;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-e-1117-0412), ROADMAP 48.3 from its spec (the item, commit b11b1d6b9's message, docs/wiki/copperline.md "The
 * status card, tooltips and toasts", the VillagerCard board): the card "laid out from the foot up so buttons never leave
 * the card on a short screen", "whole rows only when lines are cut", at a dozen screen sizes from GUI scale 1 at 1920x1080
 * to a forced scale's 683x192, with the most lines any card has (a builder's eight, a Nurse's party of six and her
 * effects) and labels three times as long; and the toast through a real build finishing ({@link Builders#finish}) to
 * real connected players (an embedded channel, the mod's channels registered as a modded client does): "Stone House /
 * Mira finished it." goes to the player the build is for and only them, wherever they are (another dimension, far
 * away), not to a bystander at the site, not to anybody when the owner is offline, not to a client without the mod
 * (who still gets the chat line); taken down reads "took it down."; a player-named build with accents and a villager with
 * an accented name come through as typed; the chat line goes once, the toast once.
 */
public class QaCopperCard1117eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	// ---- the layout ------------------------------------------------------------------------------------------------

	/** GUI-pixel screens: scale 2 and 3 of common windows, GUI scale 1 at 1080p, odd and forced-scale sizes. */
	private static final int[][] SCREENS = {{320, 180}, {320, 240}, {360, 200}, {399, 220}, {427, 240}, {480, 270}, {640, 360},
		{683, 192}, {683, 384}, {960, 540}, {1280, 720}, {1920, 1080}, {330, 186}};

	private static String box(int[] b) {
		return "[" + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + "]";
	}

	private static boolean within(int[] in, int[] out) {
		return in[0] >= out[0] && in[1] >= out[1] && in[0] + in[2] <= out[0] + out[2] && in[1] + in[3] <= out[1] + out[3];
	}

	/** One card's worth of contents, as the screen measures it. */
	private record Contents(String name, int subtitleLines, int bodyH, int refusalLines, boolean hint, int[] need) {
	}

	/** A label's button width in the Hall's rule (label + 12, at least 44), at 6 px a character (the font's widest). */
	private static int need(String label) {
		return Math.max(44, label.length() * 6 + 12);
	}

	private static List<Contents> cards() {
		String chat = Component.translatable("screen.aliveworkplace.card.job.chat").getString();
		String hall = Component.translatable("screen.aliveworkplace.card.job.hall").getString();
		String heal = Component.translatable("screen.aliveworkplace.card.nurse.heal").getString();
		String hand = Component.translatable("screen.aliveworkplace.card.fossil.hand_over").getString();
		List<Contents> out = new ArrayList<>();
		// A builder's fullest card: site, skipped, next up, experience, three needs, "and N more" under the heading;
		// a three-line status (doing + detail + a bench move), a two-line reason (no hall), the hint.
		out.add(new Contents("builder", 3, 12 + 8 * 18, 2, true, new int[]{need(chat), need(hall)}));
		// The same with labels three times as long (a translation): the buttons wrap onto rows.
		out.add(new Contents("builder x3", 3, 12 + 8 * 18, 3, true, new int[]{need(chat.repeat(3)), need(hall.repeat(3))}));
		// A Nurse with Cobblemon: You, a party of six, four effects to clear; Heal grayed out (cooldown) with its reason.
		out.add(new Contents("nurse", 2, 12 + 11 * 18, 2, true, new int[]{need(heal)}));
		// A Fossil Scientist with many fossils, Hand over grayed out.
		out.add(new Contents("fossil", 2, 12 + 9 * 18, 2, true, new int[]{need(hand)}));
		// Five buttons, one wider than the card (a modder's or translator's worst case).
		out.add(new Contents("five buttons", 2, 12 + 8 * 18, 1, true, new int[]{need(chat), need(hall), need(heal), need(hand), 400}));
		return out;
	}

	private static void check(String at, int sw, int sh, Boxes b, Contents c, List<String> problems) {
		int[] screen = {0, 0, sw, sh};
		int[] card = b.card();
		if (!within(card, screen)) {
			problems.add(at + "the card " + box(card) + " leaves the screen");
		}
		int[] inner = {card[0] + StatusCardLayout.PAD, card[1], card[2] - 2 * StatusCardLayout.PAD, card[3]};
		int[][] bs = b.buttons();
		if (bs.length != c.need().length) {
			problems.add(at + bs.length + " button boxes for " + c.need().length + " buttons");
		}
		int headBottom = Math.max(b.portrait()[1] + b.portrait()[3], b.subtitle()[1] + b.subtitle()[3]);
		for (int i = 0; i < bs.length; i++) {
			if (!within(bs[i], inner) || !within(bs[i], screen) || bs[i][2] <= 0) {
				problems.add(at + "button " + i + " " + box(bs[i]) + " leaves the card " + box(card) + " or the screen");
			}
			for (int[] top : List.of(b.portrait(), b.name(), b.tag(), b.close(), b.subtitle())) {
				if (top[3] > 0 && CopperLayout.overlap(bs[i], top)) {
					problems.add(at + "button " + i + " " + box(bs[i]) + " covers the card's top " + box(top));
				}
			}
			if (bs[i][1] < headBottom) {
				problems.add(at + "button " + i + " " + box(bs[i]) + " climbs above the status lines");
			}
			if (b.body()[3] > 0 && CopperLayout.overlap(bs[i], b.body())) {
				problems.add(at + "button " + i + " covers the lines " + box(b.body()));
			}
			if (b.refusal()[3] > 0 && CopperLayout.overlap(bs[i], b.refusal())) {
				problems.add(at + "button " + i + " covers the reason box " + box(b.refusal()));
			}
			if (c.hint() && CopperLayout.overlap(bs[i], b.hint())) {
				problems.add(at + "button " + i + " covers the hint " + box(b.hint()));
			}
			for (int j = 0; j < i; j++) {
				if (CopperLayout.overlap(bs[i], bs[j])) {
					problems.add(at + "buttons " + j + " and " + i + " overlap");
				}
			}
		}
		if (c.refusalLines() > 0 && (b.refusal()[3] < c.refusalLines() * 10 || !within(b.refusal(), inner) || b.refusal()[1] < headBottom)) {
			problems.add(at + "the reason box " + box(b.refusal()) + " is cut, leaves the card or climbs into its top");
		}
		if (c.hint() && (b.hint()[3] <= 0 || !within(b.hint(), inner))) {
			problems.add(at + "the hint " + box(b.hint()) + " is missing or leaves the card");
		}
		if (!within(b.divider(), inner) || b.divider()[1] < headBottom) {
			problems.add(at + "the divider " + box(b.divider()) + " leaves the card or climbs into its top");
		}
		int[] body = b.body();
		if (body[3] < 0 || body[3] > c.bodyH() || (body[3] > 0 && (body[1] < headBottom || body[1] + body[3] > b.divider()[1]))) {
			problems.add(at + "the lines " + box(body) + " run into the status line or past the divider");
		}
		if (body[3] < c.bodyH() != b.cut()) {
			problems.add(at + "cut says " + b.cut() + " but the lines got " + body[3] + " of " + c.bodyH());
		}
		if (b.cut() && card[3] < sh - 4) {
			problems.add(at + "lines were cut on a card " + card[3] + " tall with " + (sh - 4) + " of room");
		}
		// A shipped card in English, cut on a 180-tall screen, still shows the heading and at least one whole line (labels
		// three times as long or five buttons may leave none: the buttons come first).
		if (b.cut() && sh >= 180 && !c.name().contains(" ") && body[3] < 12 + 18) {
			problems.add(at + "a cut card shows no whole line (" + body[3] + " px of lines)");
		}
	}

	/** Every card at every screen size: inside, nothing overlapping, the foot never leaving the card. */
	//$ gametest_batch EMPTY_STRUCTURE '"qaCopperCard1117eLayout"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaCopperCard1117eLayout")
	public void fullestCardsFitEveryScreenFromTheFootUp(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (Contents c : cards()) {
			for (int[] s : SCREENS) {
				Boxes b = StatusCardLayout.of(s[0], s[1], c.subtitleLines(), c.bodyH(), c.refusalLines(), c.hint(), c.need());
				check(s[0] + "x" + s[1] + " " + c.name() + ": ", s[0], s[1], b, c, problems);
				checked++;
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
		LOGGER.info("qa copper card 1117e: {} card layouts checked at {} screen sizes", checked, SCREENS.length);
		helper.succeed();
	}

	/** A taller screen never shows fewer lines; a card that fits is never cut, never taller than its contents. */
	//$ gametest_batch EMPTY_STRUCTURE '"qaCopperCard1117eLayout"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaCopperCard1117eLayout")
	public void tallerScreensShowMoreAndFittingCardsAreWhole(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Contents c : cards()) {
			for (int sw : new int[]{320, 427, 683, 1920}) {
				int last = -1;
				int lastH = -1;
				for (int sh = 180; sh <= 1080; sh++) {
					Boxes b = StatusCardLayout.of(sw, sh, c.subtitleLines(), c.bodyH(), c.refusalLines(), c.hint(), c.need());
					String at = sw + "x" + sh + " " + c.name() + ": ";
					if (b.body()[3] < last) {
						problems.add(at + "fewer lines (" + b.body()[3] + " px) than one pixel shorter (" + last + ")");
					}
					if (b.card()[3] < lastH) {
						problems.add(at + "the card got shorter on a taller screen");
					}
					last = b.body()[3];
					lastH = b.card()[3];
					// the card centered on the screen
					int[] card = b.card();
					if (Math.abs(card[0] - (sw - card[2] - card[0])) > 1) {
						problems.add(at + "the card isn't centered across");
					}
				}
				Boxes big = StatusCardLayout.of(sw, 1080, c.subtitleLines(), c.bodyH(), c.refusalLines(), c.hint(), c.need());
				if (big.cut() || big.body()[3] != c.bodyH()) {
					problems.add(sw + "x1080 " + c.name() + ": the lines were cut on a screen 1080 tall");
				}
				// the same card one pixel taller than its contents need: still whole, and the card no taller than that
				int natural = big.card()[3];
				Boxes snug = StatusCardLayout.of(sw, natural + 4, c.subtitleLines(), c.bodyH(), c.refusalLines(), c.hint(), c.need());
				Boxes tight = StatusCardLayout.of(sw, natural + 3, c.subtitleLines(), c.bodyH(), c.refusalLines(), c.hint(), c.need());
				if (snug.cut() || snug.card()[3] != natural) {
					problems.add(sw + "x" + (natural + 4) + " " + c.name() + ": the card that just fits was cut or resized");
				}
				if (!tight.cut()) {
					problems.add(sw + "x" + (natural + 3) + " " + c.name() + ": one pixel short of room, nothing was cut");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
		helper.succeed();
	}

	/** The width rule: 200 wide, or the screen less 8 each side; the card's top row apart at the narrowest card. */
	//$ gametest_batch EMPTY_STRUCTURE '"qaCopperCard1117eLayout"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaCopperCard1117eLayout")
	public void cardWidthAndTopRowAtNarrowScreens(GameTestHelper helper) {
		for (int sw : new int[]{180, 200, 216, 217, 320, 1920}) {
			int w = StatusCardLayout.width(sw);
			helper.assertTrue(w == Math.min(200, sw - 16), sw + " wide: the card is " + w + " wide");
			Boxes b = StatusCardLayout.of(sw, 240, 1, 40, 1, true, new int[]{48, 62});
			helper.assertTrue(b.card()[0] >= 8 && b.card()[0] + b.card()[2] <= sw - 8, sw + " wide: the card " + box(b.card()) + " is within 8 px of the edge");
			helper.assertTrue(!CopperLayout.overlap(b.name(), b.close()) && !CopperLayout.overlap(b.portrait(), b.name())
				&& within(b.close(), b.card()), sw + " wide: the name " + box(b.name()) + ", portrait and close X " + box(b.close()) + " overlap");
		}
		helper.succeed();
	}

	// ---- the toast ---------------------------------------------------------------------------------------------------

	/** A connected player: packets it's sent wait in {@link #channel}. */
	private record Joined(ServerPlayer player, EmbeddedChannel channel) {
		/** Every packet sent so far (bundles opened), and forgets them. */
		List<Packet<?>> take() {
			List<Packet<?>> out = new ArrayList<>();
			Object o;
			while ((o = channel.readOutbound()) != null) {
				if (o instanceof BundlePacket<?> bundle) {
					bundle.subPackets().forEach(out::add);
				} else if (o instanceof Packet<?> p) {
					out.add(p);
				}
			}
			return out;
		}

		List<CardToasts.Show> toasts() {
			return toasts(take());
		}

		static List<CardToasts.Show> toasts(List<Packet<?>> packets) {
			List<CardToasts.Show> out = new ArrayList<>();
			for (Packet<?> p : packets) {
				if (p instanceof ClientboundCustomPayloadPacket c && c.payload() instanceof CardToasts.Show s) {
					out.add(s);
				}
			}
			return out;
		}

		static List<String> chat(List<Packet<?>> packets) {
			List<String> out = new ArrayList<>();
			for (Packet<?> p : packets) {
				if (p instanceof ClientboundSystemChatPacket c && !c.overlay()) {
					out.add(c.content().getString());
				}
			}
			return out;
		}
	}

	/** A real player on an embedded connection at {@code at} (relative); {@code modded}: its client has the mod's channels. */
	private static Joined join(GameTestHelper helper, String name, BlockPos at, boolean modded) {
		ServerLevel level = helper.getLevel();
		GameProfile profile = new GameProfile(UUID.randomUUID(), name);
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.teleportTo(level, abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0f, 0f);
		Leftovers.after(helper, () -> {
			if (level.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
				level.getServer().getPlayerList().remove(player);
			}
		});
		if (modded) {
			try {
				Field f = AbstractChanneledNetworkAddon.class.getDeclaredField("sendableChannels");
				f.setAccessible(true);
				@SuppressWarnings("unchecked")
				Set<ResourceLocation> channels = (Set<ResourceLocation>) f.get(ServerNetworkingImpl.getAddon(player.connection));
				channels.add(CardToasts.Show.TYPE.id());
			} catch (ReflectiveOperationException e) {
				throw new GameTestAssertException("can't register the toast channel for a modded client: " + e);
			}
		}
		Joined joined = new Joined(player, channel);
		joined.take();
		return joined;
	}

	private static Villager builder(GameTestHelper helper, String name) {
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
		builder.setNoAi(true);
		builder.setCustomName(Component.literal(name));
		return builder;
	}

	private static BuildSite site(GameTestHelper helper, UUID owner, ResourceLocation structure, BlockPos rel) {
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(rel),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = new BuildSite(UUID.randomUUID(), owner, "Jesse", structure, placement);
		Leftovers.after(helper, () -> BuildSiteManager.get(helper.getLevel()).forgetFinished(site.placement()));
		return site;
	}

	private static void begin(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.finished(helper);
	}

	private static long count(List<String> lines, String line) {
		return lines.stream().filter(line::equals).count();
	}

	/**
	 * A build finishing: its owner (a modded client) gets one toast, "Starter Cottage / Mira finished it.", and the chat
	 * line once; a bystander at the site (modded too) gets neither; a vanilla client that owns the next build gets the
	 * chat line and no toast.
	 */
	//$ gametest_batch AREA '"qaCopperCard1117eOwner"'
	@GameTest(template = AREA, batch = "qaCopperCard1117eOwner")
	public void finishedToastGoesToTheOwnerOnly(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		Joined owner = join(helper, "QaOwner", new BlockPos(14, 2, 14), true);
		Joined bystander = join(helper, "QaBystander", new BlockPos(2, 2, 2), true);
		Joined vanilla = join(helper, "QaVanilla", new BlockPos(3, 2, 3), false);
		Villager mira = builder(helper, "Mira");
		ResourceLocation cottage = StarterBlueprints.STARTER_COTTAGE.id();
		String build = Blueprints.displayName(cottage).getString();
		String chatLine = Component.translatable("message.aliveworkplace.finished", "Mira", build).getString();

		Builders.finish(level, mira, site(helper, owner.player().getUUID(), cottage, new BlockPos(1, 2, 1)));
		List<Packet<?>> ownerGot = owner.take();
		List<CardToasts.Show> toasts = Joined.toasts(ownerGot);
		helper.assertTrue(toasts.size() == 1, "the owner got " + toasts.size() + " toasts");
		CardToasts.Show toast = toasts.get(0);
		helper.assertTrue(toast.title().getString().equals(build) && toast.line().getString().equals("Mira finished it.")
			&& toast.icon().equals(CardToasts.BUILT_ICON), "the toast reads \"" + toast.title().getString() + " / " + toast.line().getString() + "\" (" + toast.icon() + ")");
		helper.assertTrue(count(Joined.chat(ownerGot), chatLine) == 1, "the chat line \"" + chatLine + "\" went " + count(Joined.chat(ownerGot), chatLine)
			+ " times: " + Joined.chat(ownerGot));
		List<Packet<?>> byGot = bystander.take();
		helper.assertTrue(Joined.toasts(byGot).isEmpty() && count(Joined.chat(byGot), chatLine) == 0, "the bystander at the site got the toast or the line");
		helper.assertTrue(vanilla.toasts().isEmpty(), "a vanilla client got a toast it isn't the owner of");

		// A client without the mod who owns the build: the chat line, no toast, nothing thrown.
		Builders.finish(level, mira, site(helper, vanilla.player().getUUID(), cottage, new BlockPos(1, 2, 9)));
		List<Packet<?>> vanillaGot = vanilla.take();
		helper.assertTrue(Joined.toasts(vanillaGot).isEmpty(), "a client without the mod was sent the toast packet");
		helper.assertTrue(count(Joined.chat(vanillaGot), chatLine) == 1, "a client without the mod didn't get the chat line once: " + Joined.chat(vanillaGot));
		helper.assertTrue(owner.toasts().isEmpty(), "the first owner got a toast for someone else's build");
		LOGGER.info("qa copper card 1117e: toast \"{} / {}\" to the owner once, chat once; bystander and vanilla none", toast.title().getString(),
			toast.line().getString());
		helper.succeed();
	}

	/** Taken down: "<Build> / <Name> took it down.", with the take-down chat line once. */
	//$ gametest_batch AREA '"qaCopperCard1117eDown"'
	@GameTest(template = AREA, batch = "qaCopperCard1117eDown")
	public void takenDownToastSaysSo(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		Joined owner = join(helper, "QaOwnerDown", new BlockPos(14, 2, 14), true);
		Villager dara = builder(helper, "Dara");
		ResourceLocation cottage = StarterBlueprints.STARTER_COTTAGE.id();
		String build = Blueprints.displayName(cottage).getString();
		BuildSite site = site(helper, owner.player().getUUID(), cottage, new BlockPos(1, 2, 1));
		site.setDeconstruction();
		Builders.finish(level, dara, site);
		List<Packet<?>> got = owner.take();
		List<CardToasts.Show> toasts = Joined.toasts(got);
		helper.assertTrue(toasts.size() == 1, "the owner got " + toasts.size() + " toasts for a take-down");
		helper.assertTrue(toasts.get(0).title().getString().equals(build) && toasts.get(0).line().getString().equals("Dara took it down."),
			"the take-down toast reads \"" + toasts.get(0).title().getString() + " / " + toasts.get(0).line().getString() + "\"");
		String chatLine = Component.translatable("message.aliveworkplace.finished_deconstruct", "Dara", build).getString();
		helper.assertTrue(count(Joined.chat(got), chatLine) == 1, "the take-down chat line went " + count(Joined.chat(got), chatLine) + " times: " + Joined.chat(got));
		helper.assertTrue(!Joined.chat(got).contains(Component.translatable("message.aliveworkplace.finished", "Dara", build).getString()),
			"a take-down also said it finished building");
		helper.succeed();
	}

	/** A player-named build with accents and a long name, and a builder with an accented name: as typed in the toast. */
	//$ gametest_batch AREA '"qaCopperCard1117eNames"'
	@GameTest(template = AREA, batch = "qaCopperCard1117eNames")
	public void accentedAndLongNamesComeThroughAsTyped(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = AliveWorkplace.id("imported/qa_copper_card_1117e");
		String name = "Château de l'Été, la très longue maison au bord du lac où Zoë passe l'hiver";
		BlueprintNames.put(level.getServer(), id, name);
		Leftovers.after(helper, () -> BlueprintNames.put(level.getServer(), id, ""));
		Joined owner = join(helper, "QaOwnerNames", new BlockPos(14, 2, 14), true);
		Villager eve = builder(helper, "Ève-Marie Ñúñez");
		Builders.finish(level, eve, site(helper, owner.player().getUUID(), id, new BlockPos(1, 2, 1)));
		List<Packet<?>> got = owner.take();
		List<CardToasts.Show> toasts = Joined.toasts(got);
		helper.assertTrue(toasts.size() == 1, "the owner got " + toasts.size() + " toasts");
		helper.assertTrue(toasts.get(0).title().getString().equals(name), "the toast's title is \"" + toasts.get(0).title().getString() + "\"");
		helper.assertTrue(toasts.get(0).line().getString().equals("Ève-Marie Ñúñez finished it."), "the toast's line is \"" + toasts.get(0).line().getString() + "\"");
		String chatLine = Component.translatable("message.aliveworkplace.finished", "Ève-Marie Ñúñez", name).getString();
		helper.assertTrue(count(Joined.chat(got), chatLine) == 1, "the chat line went " + count(Joined.chat(got), chatLine) + " times: " + Joined.chat(got));
		helper.succeed();
	}

	/** The owner far away, then in the Nether: still their toast. The owner offline: nobody's, and no error. */
	//$ gametest_batch AREA '"qaCopperCard1117eAway"'
	@GameTest(template = AREA, batch = "qaCopperCard1117eAway")
	public void ownerAwayGetsItOfflineOwnerNobodyDoes(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation cottage = StarterBlueprints.STARTER_COTTAGE.id();
		Villager mira = builder(helper, "Mira");
		Joined owner = join(helper, "QaOwnerAway", new BlockPos(14, 2, 14), true);
		Joined near = join(helper, "QaNear", new BlockPos(2, 2, 2), true);
		// far away in the same world (2,000 blocks)
		owner.player().teleportTo(level, owner.player().getX() + 2000, 120, owner.player().getZ(), 0f, 0f);
		owner.take();
		Builders.finish(level, mira, site(helper, owner.player().getUUID(), cottage, new BlockPos(1, 2, 1)));
		helper.assertTrue(owner.toasts().size() == 1, "the owner 2,000 blocks away didn't get one toast");
		helper.assertTrue(near.toasts().isEmpty(), "a player beside the site got the far owner's toast");

		// in the Nether
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "no Nether");
		owner.player().teleportTo(nether, 0.5, 120, 0.5, 0f, 0f);
		owner.take();
		Builders.finish(level, mira, site(helper, owner.player().getUUID(), cottage, new BlockPos(1, 2, 9)));
		helper.assertTrue(owner.toasts().size() == 1, "the owner in the Nether didn't get one toast");
		helper.assertTrue(near.toasts().isEmpty(), "a player beside the site got the Nether owner's toast");

		// offline: the build was started by a player who has left
		UUID gone = UUID.randomUUID();
		Builders.finish(level, mira, site(helper, gone, cottage, new BlockPos(9, 2, 1)));
		helper.assertTrue(near.toasts().isEmpty() && owner.toasts().isEmpty(), "an offline owner's toast went to someone else");
		helper.succeed();
	}

	/** The new sentences, rendered: toast lines, the grayed-out word, in American English, no key left untranslated. */
	//$ gametest_batch EMPTY_STRUCTURE '"qaCopperCard1117eLayout"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaCopperCard1117eLayout")
	public void toastAndTooltipSentences(GameTestHelper helper) {
		String[][] expect = {
			{"toast.aliveworkplace.built", "Mira finished it."},
			{"toast.aliveworkplace.taken_down", "Mira took it down."},
			{"screen.aliveworkplace.card.off", "Not yet"},
			{"screen.aliveworkplace.card.close", "Close"},
		};
		for (String[] e : expect) {
			String got = Component.translatable(e[0], "Mira").getString();
			helper.assertTrue(got.equals(e[1]), e[0] + " reads \"" + got + "\", expected \"" + e[1] + "\"");
		}
		// a builder's whole card through the record and its codec keeps each line's item (what a tooltip names)
		StatusCard card = new StatusCard(7, Component.literal("Mira"), Component.literal("Builder · Novice"), Component.literal("Building."),
			Component.literal("Their work"), List.of(new StatusCard.Line(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG),
				Component.literal("Needs Oak Log"), Component.literal("×16"), StatusCard.ORANGE, StatusCard.NO_BAR)), Component.empty(),
			List.of(StatusCard.Button.of(1, Component.literal("In chat"), null), StatusCard.Button.of(0, Component.literal("Hall page"),
				Component.literal("There's no Village Hall near Mira."))), Component.literal("hint"));
		var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
		StatusCard.STREAM_CODEC.encode(buf, card);
		StatusCard back = StatusCard.STREAM_CODEC.decode(buf);
		buf.release();
		helper.assertTrue(back.lines().get(0).icon().is(net.minecraft.world.item.Items.OAK_LOG)
			&& back.lines().get(0).icon().getHoverName().getString().equals("Oak Log"), "the line's item didn't come through for its tooltip");
		helper.assertTrue(!back.buttons().get(1).usable() && back.buttons().get(1).refusal().getString().equals("There's no Village Hall near Mira.")
			&& back.buttons().get(0).usable(), "the grayed-out button's reason didn't come through");
		helper.succeed();
	}
}
