package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.card.CardToasts;
import io.github.jcondedata.aliveworkplace.card.JobCards;
import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.ui.StatusCardLayout;
import io.github.jcondedata.aliveworkplace.ui.StatusCardLayout.Boxes;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-11-17, lane d), written from ROADMAP 48.3's spec and the VillagerCard board (docs/design/copperline/boards/
 * VillagerCard.png), not from the code: the villager status card's Copperline layout, its one copper button, a real
 * builder's card, and the toast a builder finishing or taking down a build sends.
 * <ul>
 * <li>The layout at every screen from 200x120 to 854x400 (GUI scale 2 and 3 sizes among them), with 0 to 4 status lines,
 * a body from nothing to thirty rows, 0 to 3 reason lines, with and without a hint, and 0 to 5 buttons (one wider than
 * the card): the card always inside the screen and the buttons always inside the card (the spec's "buttons never leave
 * the card on a short screen"), in reading order and apart, above the hint and under the reason box; the reason box only
 * when a button is grayed out, tall enough for its lines; the body never past the divider and cut only when it got less
 * than it needs; the close X inside the card at its top right, apart from the name and the job. On a real screen (240 or
 * more tall, 320 or more wide) the foot never climbs over the face, the name or the status lines, and a cut body keeps
 * room for its heading and one whole row.</li>
 * <li>The copper button: the first usable one, never Close, never a grayed-out one (two thousand fixed-seed cards).</li>
 * <li>A real builder's card (sneak-click, a hut started, no Village Hall near): Close is the X (out of the row), Hall page
 * is the first grayed-out button and its reason is the box's, In chat is the copper one, and at 427x240 (Minecraft's
 * default window at GUI scale 2), 480x270 and 640x360 the buttons and the reason box sit inside the card and every line
 * shows.</li>
 * <li>The toast through {@link Builders#finish}: the owner gets the chat line, and the toast's title and line name the
 * same build and builder (named, and unnamed: "Builder finished it."); a take-down reads "took it down."; a player whose
 * client lacks the mod gets no toast and no error. The words from en_us.json itself, American English.</li>
 * </ul>
 * Not covered here (client-side, the scene {@code copperline_status_card} films it): the drawing, the line tooltip's place
 * beside the card, the "Not yet" tooltip under the mouse, and the toast arriving on a modded client.
 */
public class QaStatusCardCopper1117dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	/** The card's line height (the client's TableStyle.CARD_LINE) and its heading's. */
	private static final int ROW = 18;
	private static final int HEADING = 12;

	private static String box(int[] b) {
		return "[" + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + "]";
	}

	private static boolean within(int[] in, int[] out) {
		return in[0] >= out[0] && in[1] >= out[1] && in[0] + in[2] <= out[0] + out[2] && in[1] + in[3] <= out[1] + out[3];
	}

	private static boolean overlap(int[] a, int[] b) {
		return a[2] > 0 && a[3] > 0 && b[2] > 0 && b[3] > 0 && a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
	}

	/** The width of {@code s} in the game's default font (the vanilla ASCII advances; letters and digits 6, space 4). */
	private static int width(String s) {
		int w = 0;
		for (char ch : s.toCharArray()) {
			w += switch (ch) {
				case ' ' -> 4;
				case 'i', '.', ',', ':', ';', '!', '|', '\'' -> 2;
				case 'l', '`' -> 3;
				case 't', 'I', '[', ']', '"', '*' -> 4;
				case 'f', 'k', '<', '>', '(', ')', '{', '}' -> 5;
				default -> 6;
			};
		}
		return w;
	}

	/** How many lines {@code s} wraps to at {@code w} pixels, broken at spaces as the game's font splits (0 for none). */
	private static int lines(String s, int w) {
		if (s.isEmpty()) {
			return 0;
		}
		int n = 1;
		int at = 0;
		for (String word : s.split(" ")) {
			int ww = width(word);
			int need = at == 0 ? ww : at + 4 + ww;
			if (at > 0 && need > w) {
				n++;
				at = ww;
			} else {
				at = need;
			}
		}
		return n;
	}

	/** Every check the spec makes of a layout; {@code real} adds the ones for a screen a player can have. */
	private static void check(String at, int sw, int sh, Boxes b, int buttons, int refusalLines, boolean hint, int bodyH, boolean real,
							  List<String> problems) {
		int[] card = b.card();
		if (!within(card, new int[]{0, 0, sw, sh})) {
			problems.add(at + "the card " + box(card) + " leaves the screen");
			return;
		}
		int[] inner = {card[0] + StatusCardLayout.PAD, card[1], card[2] - 2 * StatusCardLayout.PAD, card[3]};
		int[][] bs = b.buttons();
		if (bs.length != buttons) {
			problems.add(at + bs.length + " button boxes for " + buttons + " buttons");
		}
		for (int i = 0; i < bs.length; i++) {
			if (!within(bs[i], inner) || bs[i][2] <= 0 || bs[i][3] != StatusCardLayout.BUTTON_H) {
				problems.add(at + "button " + i + " " + box(bs[i]) + " leaves the card " + box(inner));
			}
			if (hint && bs[i][1] + bs[i][3] > b.hint()[1]) {
				problems.add(at + "button " + i + " " + box(bs[i]) + " runs into the hint " + box(b.hint()));
			}
			if (refusalLines > 0 && bs[i][1] < b.refusal()[1] + b.refusal()[3]) {
				problems.add(at + "button " + i + " " + box(bs[i]) + " is over the reason box " + box(b.refusal()));
			}
			if (i > 0) {
				int[] p = bs[i - 1];
				boolean after = bs[i][1] > p[1] || bs[i][1] == p[1] && bs[i][0] > p[0];
				if (!after || overlap(p, bs[i])) {
					problems.add(at + "button " + i + " " + box(bs[i]) + " isn't after or apart from " + box(p));
				}
			}
		}
		if ((b.hint()[3] > 0) != hint || hint && !within(b.hint(), inner)) {
			problems.add(at + "the hint " + box(b.hint()) + (hint ? " leaves the card" : " has room with no hint"));
		}
		int[] r = b.refusal();
		if (refusalLines == 0 && r[3] != 0) {
			problems.add(at + "a reason box " + box(r) + " with no grayed-out button");
		}
		if (refusalLines > 0 && r[3] < refusalLines * 10 + 3) {
			problems.add(at + "the reason box " + box(r) + " is too short for " + refusalLines + " lines");
		}
		if (StatusCardLayout.refusalTextWidth(sw) + 7 > r[2] && refusalLines > 0) {
			problems.add(at + "the reason's text (" + StatusCardLayout.refusalTextWidth(sw) + " wide, 7 in) is wider than its box " + box(r));
		}
		int[] body = b.body();
		if (body[3] < 0 || body[3] > bodyH || b.cut() != (body[3] < bodyH)) {
			problems.add(at + "the body " + box(body) + " for " + bodyH + " pixels, cut " + b.cut());
		}
		if (body[3] > 0 && body[1] + body[3] > b.divider()[1]) {
			problems.add(at + "the body " + box(body) + " runs past the divider " + box(b.divider()));
		}
		int[] x = b.close();
		if (!within(x, card) || x[0] + x[2] != card[0] + card[2] - StatusCardLayout.PAD || x[1] > card[1] + StatusCardLayout.PAD
			|| overlap(x, b.name()) || overlap(x, b.tag()) || overlap(x, b.portrait())) {
			problems.add(at + "the close X " + box(x) + " isn't at the card's top right apart from the face, name and job");
		}
		if (sh >= 180 && sw >= 320) { // (the spec promises only the buttons on a smaller screen than any GUI scale gives)
			if (refusalLines > 0 && !within(r, inner)) {
				problems.add(at + "the reason box " + box(r) + " leaves the card");
			}
			if (!within(b.divider(), inner)) {
				problems.add(at + "the divider " + box(b.divider()) + " leaves the card");
			}
		}
		if (real) {
			int[] s = b.subtitle();
			int top = Math.max(s[1] + s[3], b.portrait()[1] + b.portrait()[3]);
			if (b.divider()[1] < top) {
				problems.add(at + "the foot (divider " + box(b.divider()) + ") climbs over the face or the status lines " + box(s));
			}
			if (overlap(x, s)) {
				problems.add(at + "the close X " + box(x) + " is over the status lines " + box(s));
			}
		}
	}

	//$ gametest_batch EMPTY_STRUCTURE '"qaStatusCardCopper1117d"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaStatusCardCopper1117d")
	public void cardLayoutHoldsAtEveryScreenSize(GameTestHelper helper) {
		RandomSource random = RandomSource.create(1117L);
		List<int[]> buttonSets = new ArrayList<>(List.of(new int[0], new int[]{44}, new int[]{60, 52}, new int[]{90, 90, 90},
			new int[]{44, 44, 44, 44, 44}, new int[]{300}, new int[]{120, 120, 120, 120}));
		for (int i = 0; i < 4; i++) {
			int[] need = new int[1 + random.nextInt(5)];
			for (int j = 0; j < need.length; j++) {
				need[j] = 44 + random.nextInt(140);
			}
			buttonSets.add(need);
		}
		int[] bodies = {0, 10, HEADING + ROW, HEADING + 5 * ROW, HEADING + 30 * ROW};
		List<String> problems = new ArrayList<>();
		int layouts = 0;
		for (int sw : new int[]{200, 256, 320, 400, 427, 480, 569, 640, 854}) {
			for (int sh = 120; sh <= 400; sh += 4) {
				for (int sub = 0; sub <= 4; sub++) {
					for (int bodyH : bodies) {
						for (int refusal = 0; refusal <= 3; refusal++) {
							for (boolean hint : new boolean[]{false, true}) {
								for (int[] need : buttonSets) {
									int widest = 0;
									for (int n : need) {
										widest = Math.max(widest, n);
									}
									boolean real = sh >= 240 && sw >= 320 && sub <= 3 && need.length <= 4 && widest <= 120;
									String at = sw + "x" + sh + " sub " + sub + " body " + bodyH + " reason " + refusal + " hint " + hint
										+ " buttons " + need.length + ": ";
									Boxes b = StatusCardLayout.of(sw, sh, sub, bodyH, refusal, hint, need);
									check(at, sw, sh, b, need.length, refusal, hint, bodyH, real, problems);
									// Cut on a real screen: the heading and at least one whole row still show.
									if (real && b.cut() && bodyH >= HEADING + ROW && sub <= 2 && refusal <= 2 && need.length <= 3
										&& b.body()[3] < HEADING + ROW) {
										problems.add(at + "the cut body " + box(b.body()) + " has no room for its heading and one row");
									}
									// Not cut: the body gets exactly what it needs.
									if (!b.cut() && b.body()[3] != bodyH) {
										problems.add(at + "an uncut body " + box(b.body()) + " isn't " + bodyH + " tall");
									}
									layouts++;
									if (problems.size() > 12) {
										helper.fail(problems.size() + " problems, first: " + String.join("; ", problems.subList(0, 12)));
									}
								}
							}
						}
					}
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.assertTrue(layouts > 100_000, "only " + layouts + " layouts checked");
		helper.succeed();
	}

	//$ gametest_batch EMPTY_STRUCTURE '"qaStatusCardCopper1117d"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaStatusCardCopper1117d")
	public void copperGoesToTheFirstUsableButtonNeverCloseOrGrayedOut(GameTestHelper helper) {
		RandomSource random = RandomSource.create(48_3L);
		for (int n = 0; n < 2000; n++) {
			int size = random.nextInt(7);
			boolean[] usable = new boolean[size];
			boolean[] close = new boolean[size];
			int expect = -1;
			StringBuilder seen = new StringBuilder();
			for (int i = 0; i < size; i++) {
				usable[i] = random.nextBoolean();
				close[i] = random.nextInt(4) == 0;
				seen.append(close[i] ? "X" : usable[i] ? "U" : "g");
			}
			for (int i = 0; i < size && expect < 0; i++) {
				if (usable[i] && !close[i]) {
					expect = i;
				}
			}
			int got = StatusCardLayout.copper(usable, close);
			helper.assertTrue(got == expect, "buttons " + seen + " (U usable, g grayed out, X Close): copper " + got + ", expected " + expect);
			if (got >= 0) {
				helper.assertTrue(usable[got] && !close[got], "the copper button " + got + " of " + seen + " is grayed out or Close");
			}
		}
		// The board's own case: the first button grayed out, its reason in the box, the second the copper one.
		helper.assertTrue(StatusCardLayout.copper(new boolean[]{false, false, true}, new boolean[]{false, false, false}) == 2,
			"two grayed-out buttons first: the third isn't copper");
		helper.assertTrue(StatusCardLayout.copper(new boolean[]{true, false}, new boolean[]{true, false}) == -1,
			"Close usable and the other grayed out: a copper button anyway");
		helper.succeed();
	}

	/**
	 * A builder with a hut started and no Village Hall near, sneak-clicked with an empty hand: Close is the X, not in the
	 * row; Hall page is grayed out with "There's no Village Hall near Bob." as the box's reason; In chat is the copper
	 * button; laid out with the card's real words at 427x240, 480x270 and 640x360, the buttons and the reason box sit in the
 * card and no line is cut.
	 */
	//$ gametest_batch AREA '"qaStatusCardCopper1117dBuilder"'
	@GameTest(template = AREA, batch = "qaStatusCardCopper1117dBuilder")
	public void aRealBuilderCardGraysHallPageAndKeepsItsButtonsIn(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		BlockPos bench = new BlockPos(3, 2, 3);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		v.setNoAi(true);
		Jobs.employ(level, v, helper.absolutePos(bench), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		v.setCustomName(Component.literal("Bob"));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BuildSite site = Builders.start(level, v, player, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(12, 1, 12)), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the hut didn't start: " + QaStageFourGameTests.keys(seen));
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		NurseCardGameTests.click(player, v, true);
		helper.assertTrue(player.containerMenu instanceof StatusCardMenu, "no card opened: " + player.containerMenu.getClass().getSimpleName());
		StatusCard card = ((StatusCardMenu) player.containerMenu).card();

		List<StatusCard.Button> row = new ArrayList<>();
		for (StatusCard.Button b : card.buttons()) {
			if (b.id() != StatusCardMenu.CLOSE) {
				row.add(b);
			}
		}
		helper.assertTrue(card.button(StatusCardMenu.CLOSE) != null && row.size() == card.buttons().size() - 1,
			"the card has no Close for its X: " + card.buttons());
		StatusCard.Button off = row.stream().filter(b -> !b.usable()).findFirst().orElse(null);
		helper.assertTrue(off != null && off.id() == JobCards.HALL, "Hall page isn't the first grayed-out button with no hall near: "
			+ (off == null ? "none grayed out" : off.label().getString()));
		helper.assertValueEqual(off.refusal().getString(), "There's no Village Hall near Bob.", "the reason box's words");
		boolean[] usable = new boolean[row.size()];
		boolean[] close = new boolean[row.size()];
		for (int i = 0; i < usable.length; i++) {
			usable[i] = row.get(i).usable();
		}
		int copper = StatusCardLayout.copper(usable, close);
		helper.assertTrue(copper >= 0 && row.get(copper).id() == JobCards.CHAT, "the copper button isn't In chat: "
			+ (copper < 0 ? "none" : row.get(copper).label().getString()));

		int[] need = new int[row.size()];
		for (int i = 0; i < need.length; i++) {
			need[i] = Math.max(44, width(row.get(i).label().getString()) + 12);
		}
		int bodyH = (card.heading().getString().isEmpty() ? 0 : HEADING) + (card.lines().isEmpty() ? 10 : card.lines().size() * ROW);
		List<String> problems = new ArrayList<>();
		for (int[] s : new int[][]{{427, 240}, {480, 270}, {640, 360}}) {
			int inner = StatusCardLayout.inner(s[0]);
			int sub = lines(card.subtitle().getString(), inner);
			int refusal = lines(off.refusal().getString(), StatusCardLayout.refusalTextWidth(s[0]));
			Boxes b = StatusCardLayout.of(s[0], s[1], sub, bodyH, refusal, !card.hint().getString().isEmpty(), need);
			check(s[0] + "x" + s[1] + " (Bob, " + card.lines().size() + " lines, " + sub + " status lines): ", s[0], s[1], b, row.size(), refusal,
				!card.hint().getString().isEmpty(), bodyH, true, problems);
			if (b.cut()) {
				problems.add(s[0] + "x" + s[1] + ": Bob's card cuts its lines (" + card.lines().size() + " lines, " + sub + " status lines, body "
					+ box(b.body()) + " of " + bodyH + ")");
			}
		}
		player.closeContainer();
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** The chat line in {@code seen} with {@code key}, rendered. */
	private static String said(GameTestHelper helper, List<Component> seen, String key) {
		Component line = QaStageFourGameTests.find(seen, key);
		helper.assertTrue(line != null, "no " + key + " in chat: " + QaStageFourGameTests.keys(seen));
		return line.getString();
	}

	/**
	 * A builder finishing a build through {@link Builders#finish}: the owner's chat line still goes, and the toast's title
	 * and line name the same build and builder, named ("Mira finished it.") or not ("Builder finished it.", the job's name,
	 * never a raw key); a take-down's toast reads "Mira took it down."; a player whose client lacks the mod gets no toast and
	 * no error (the chat line is enough).
	 */
	//$ gametest_batch AREA '"qaStatusCardCopper1117dToast"'
	@GameTest(template = AREA, batch = "qaStatusCardCopper1117dToast")
	public void finishingABuildTellsTheOwnerAndTheToastSaysTheSame(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(owner));
		owner.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 2, 6.5)));
		ResourceLocation cottage = StarterBlueprints.STARTER_COTTAGE.id();
		String build = Blueprints.displayName(cottage).getString();
		helper.assertTrue(!build.isEmpty() && !build.contains("aliveworkplace"), "the cottage's name reads \"" + build + "\"");

		for (String name : new String[]{"Mira", null}) {
			Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
			builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
			builder.setNoAi(true);
			if (name != null) {
				builder.setCustomName(Component.literal(name));
			}
			String who = builder.getDisplayName().getString();
			helper.assertTrue(name != null ? who.equals(name) : who.equals("Builder"), "the builder is called \"" + who + "\"");
			seen.clear();
			BuildSite site = new BuildSite(UUID.randomUUID(), owner.getUUID(), owner.getGameProfile().getName(), cottage,
				new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(name == null ? 9 : 1, 2, 1)), Rotation.NONE, Mirror.NONE));
			Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(site.placement()));
			Builders.finish(level, builder, site);
			String chat = said(helper, seen, "message.aliveworkplace.finished");
			helper.assertTrue(chat.contains(who) && chat.contains(build), "the chat line \"" + chat + "\" doesn't name " + who + " and " + build);
			CardToasts.Show toast = CardToasts.built(builder.getDisplayName(), Blueprints.displayName(site.structure()), false);
			helper.assertValueEqual(toast.title().getString(), build, "the toast's title");
			helper.assertValueEqual(toast.line().getString(), who + " finished it.", "the toast's line");
			helper.assertTrue(!CardToasts.send(owner, toast), "a toast went to a client without the mod");
			builder.discard();
		}

		// A take-down: the chat line and the toast both say so.
		Villager mira = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 8));
		mira.setVillagerData(mira.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
		mira.setNoAi(true);
		mira.setCustomName(Component.literal("Mira"));
		seen.clear();
		BuildSite down = new BuildSite(UUID.randomUUID(), owner.getUUID(), owner.getGameProfile().getName(), cottage,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 9)), Rotation.NONE, Mirror.NONE));
		down.setDeconstruction();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(down.placement()));
		Builders.finish(level, mira, down);
		String chat = said(helper, seen, "message.aliveworkplace.finished_deconstruct");
		helper.assertTrue(chat.contains("Mira") && chat.contains(build), "the take-down's chat line \"" + chat + "\"");
		CardToasts.Show toast = CardToasts.built(mira.getDisplayName(), Blueprints.displayName(down.structure()), true);
		helper.assertValueEqual(toast.line().getString(), "Mira took it down.", "the take-down toast's line");
		helper.assertValueEqual(toast.title().getString(), build, "the take-down toast's title");
		helper.succeed();
	}

	/**
	 * The toast's and the grayed-out button's words, read from en_us.json itself: one {@code %s} each where a name goes,
	 * a full sentence, American English; a styled name and a long build name come through whole (the client trims).
	 */
	//$ gametest_batch EMPTY_STRUCTURE '"qaStatusCardCopper1117d"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaStatusCardCopper1117d")
	public void toastWordsReadThroughTheLangFile(GameTestHelper helper) {
		JsonObject lang;
		try (InputStream in = QaStatusCardCopper1117dGameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			helper.assertTrue(in != null, "no en_us.json");
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("en_us.json: " + e);
		}
		String[][] keys = {{"toast.aliveworkplace.built", "1"}, {"toast.aliveworkplace.taken_down", "1"}, {"screen.aliveworkplace.card.off", "0"}};
		for (String[] k : keys) {
			helper.assertTrue(lang.has(k[0]), "en_us.json has no " + k[0]);
			String text = lang.get(k[0]).getAsString();
			int args = text.split("%s", -1).length - 1;
			helper.assertTrue(args == Integer.parseInt(k[1]) && !text.contains("%1$") && !text.contains("%d"), k[0] + " \"" + text + "\" has "
				+ args + " names, expected " + k[1]);
			helper.assertTrue(text.equals(text.strip()) && !text.isEmpty() && Character.isUpperCase(text.replace("%s", "M").charAt(0)),
				k[0] + " \"" + text + "\" isn't a capitalized phrase");
			helper.assertTrue(!text.matches("(?i).*\\b(colour|grey|cancelled|armour|defence|finalis|realis|organis).*"),
				k[0] + " \"" + text + "\" isn't American English");
		}
		helper.assertTrue(lang.get("toast.aliveworkplace.built").getAsString().endsWith(".")
			&& lang.get("toast.aliveworkplace.taken_down").getAsString().endsWith("."), "a toast's line isn't a sentence");
		String longName = "The Grand Copper Lighthouse of the Northern Harbor, Second Look";
		CardToasts.Show toast = CardToasts.built(Component.literal("Mira").withStyle(ChatFormatting.GOLD), Component.literal(longName), false);
		helper.assertValueEqual(toast.title().getString(), longName, "a long build name");
		helper.assertValueEqual(toast.line().getString(), "Mira finished it.", "a styled builder's line");
		helper.assertValueEqual(toast.icon(), CardToasts.BUILT_ICON, "the toast's icon");
		CardToasts.Show down = CardToasts.built(Component.literal("Mira"), Component.literal(longName), true);
		helper.assertTrue(!down.line().getString().equals(toast.line().getString()) && down.title().getString().equals(longName),
			"a take-down's toast reads the same as a build's");
		helper.succeed();
	}
}
