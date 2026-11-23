package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.HeartEvents;
import io.github.jcondedata.aliveworkplace.story.LifeStory;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1123-0953) for heart events (ROADMAP 31.7, 31.8, B309), written from docs/wiki/heart-events.md: the heart
 * thresholds exactly at and one point below, the 8-block start range exactly at and just past it, one player with two
 * friends who both have a story, the villager carried off halfway, the children argument with one and three children,
 * every unknown fact reading "nobody", and villagers who keep no friendship (no name, no hall) telling nothing. The
 * engine is ticked by hand as in {@link HeartEventGameTests} (it doesn't tick itself in gametests).
 */
public class QaHeartEvents1123cGameTests implements FabricGameTest {
	static final String AREA = HeartEventGameTests.AREA;
	private static final ResourceLocation LOVE_PARENT = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "love_parent");

	private static String tellingOf(Villager villager) {
		HeartEvents.Telling t = HeartEvents.telling(villager);
		return t == null ? "none" : t.event.id() + " to " + t.player + ", " + t.said + " said";
	}

	private static List<String> lore(ItemStack icon) {
		ItemLore lore = icon.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/** The life story page of {@code name}, opened as a player does: the hall's list, then a shift-click on them. */
	private static ChoiceMenu page(GameTestHelper helper, ServerPlayer me, BlockPos hall, String name) {
		ChoiceMenu menu = VillageHallScreen.forTest(me, hall);
		int slot = -1;
		for (int s = VillageHallScreen.FIRST_PERSON; s < ChoiceMenu.SIZE; s++) {
			if (menu.icon(s).getHoverName().getString().equals(name)) {
				slot = s;
				break;
			}
		}
		helper.assertTrue(slot >= 0, name + " isn't on the hall's list");
		menu.clicked(slot, 0, ClickType.QUICK_MOVE, me);
		helper.assertTrue(menu.icon(4).getHoverName().getString().equals("The life story of " + name), "the page didn't open: " + menu.icon(4).getHoverName().getString());
		return menu;
	}

	private static void place(GameTestHelper helper, net.minecraft.world.entity.Entity e, double x, double z) {
		Vec3 pos = helper.absoluteVec(new Vec3(x, 2, z));
		e.moveTo(pos.x, pos.y, pos.z, 0, 0);
	}

	private static String text(Object o) {
		return o instanceof Component c ? c.getString() : String.valueOf(o);
	}

	// --- Thresholds ------------------------------------------------------------------------------------------------

	/**
	 * 199 points is one below 2 hearts: nothing starts, and her page says she'll have something at 2 hearts. One more
	 * point and she tells it. After it (220) the page says 4 hearts; 399 starts nothing, 400 starts the 4-heart story.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHe1123cEdge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHe1123cEdge")
	public void qaNothingAtOnePointBelowTwoHeartsAndItStartsAtExactlyTwo(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			Villager cora = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Cora");
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 6));
			Friendship.add(cora, me, 199);
			helper.assertTrue(Friendship.points(cora, me.getUUID()) == 199, "setup: " + Friendship.points(cora, me.getUUID()));
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(cora) == null && HeartEvents.pending(level, cora, me.getUUID()) == null,
				"something told at 199 points: " + tellingOf(cora));
			List<String> heartsLore = lore(page(helper, me, hall, "Cora").icon(LifeStory.HEARTS));
			helper.assertTrue(heartsLore.equals(List.of("They'll have something to tell you at 2 hearts")), "the page at 199: " + heartsLore);
			Friendship.add(cora, me, 1);
			HeartEvents.Event event = HeartEvents.pending(level, cora, me.getUUID());
			helper.assertTrue(event != null && event.hearts() == 2, "nothing pending at exactly 200: " + event);
			heartsLore = lore(page(helper, me, hall, "Cora").icon(LifeStory.HEARTS));
			helper.assertTrue(heartsLore.equals(List.of("Cora has something to tell you: find them when they're off work")), "the page at 200: " + heartsLore);
			HeartEventGameTests.listen(helper, cora, me, HeartEventGameTests.later(level), event.lines().size());
			helper.assertTrue(Friendship.points(cora, me.getUUID()) == 220, "points after the story: " + Friendship.points(cora, me.getUUID()));
			ChoiceMenu menu = page(helper, me, hall, "Cora");
			heartsLore = lore(menu.icon(LifeStory.HEARTS));
			helper.assertTrue(heartsLore.equals(List.of("They'll have something to tell you at 4 hearts")), "the page after it: " + heartsLore);
			List<String> storyLore = lore(menu.icon(LifeStory.storySlot(0)));
			helper.assertTrue(storyLore.contains("A story for 2 hearts") && storyLore.stream().anyMatch(s -> s.startsWith("Told to ")),
				"the story's book on the page: " + menu.icon(LifeStory.storySlot(0)).getHoverName().getString() + " " + storyLore);
			Friendship.add(cora, me, 179);
			helper.assertTrue(Friendship.points(cora, me.getUUID()) == 399, "setup: " + Friendship.points(cora, me.getUUID()));
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(cora) == null, "something told at 399 points: " + tellingOf(cora));
			Friendship.add(cora, me, 1);
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			HeartEvents.Telling t = HeartEvents.telling(cora);
			helper.assertTrue(t != null && t.event.hearts() == 4 && t.said == 1, "the 4-heart story didn't start at 400: " + tellingOf(cora));
			helper.succeed();
		});
	}

	// --- Range ---------------------------------------------------------------------------------------------------------

	/**
	 * A player 8.5 blocks off isn't started on; at exactly 8 blocks she starts, and, unable to get near (she can't walk),
	 * tells it from where she stands once 5 seconds have passed, not before.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHe1123cRange"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHe1123cRange")
	public void qaSheStartsAtExactlyEightBlocksButNotAtEightAndAHalf(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager cora = HeartEventGameTests.villager(helper, new BlockPos(2, 2, 4), "Cora");
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(2, 2, 6));
			place(helper, me, 2.5 + 8.5, 4.5);
			Friendship.add(cora, me, 200);
			helper.assertTrue(Math.abs(cora.distanceTo(me) - 8.5) < 1e-6, "setup: " + cora.distanceTo(me));
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(cora) == null, "she started on a player 8.5 blocks away: " + tellingOf(cora));
			place(helper, me, 2.5 + 8, 4.5);
			helper.assertTrue(Math.abs(cora.distanceTo(me) - 8) < 1e-6, "setup: " + cora.distanceTo(me));
			long start = HeartEventGameTests.later(level);
			HeartEvents.tick(level, start);
			HeartEvents.Telling t = HeartEvents.telling(cora);
			helper.assertTrue(t != null && t.player.equals(me.getUUID()), "she didn't start at exactly 8 blocks: " + tellingOf(cora));
			// She can't walk (no AI): after 5 seconds of trying to get within 3.5 blocks, she tells it from where she stands.
			HeartEvents.tick(level, start + HeartEvents.WALK_UP - HeartEvents.CHECK_EVERY);
			t = HeartEvents.telling(cora);
			helper.assertTrue(t != null && t.said == 0, "before 5 seconds of walking up: " + tellingOf(cora));
			HeartEvents.tick(level, start + HeartEvents.WALK_UP + HeartEvents.CHECK_EVERY);
			t = HeartEvents.telling(cora);
			helper.assertTrue(t != null && t.said == 1, "after 5 seconds she didn't tell it from where she stands: " + tellingOf(cora));
			helper.succeed();
		});
	}

	// --- One listener, two tellers -------------------------------------------------------------------------------------

	/**
	 * Cora (2 blocks off) and Dara (5 blocks off) both have their 2-heart story for me. A player listens to one villager
	 * at a time: the nearer, Cora, tells first while Dara waits, then Dara tells hers. Each counts once.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHe1123cTwoTellers"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHe1123cTwoTellers")
	public void qaOneListenerIsToldByOneVillagerAtATimeTheNearestFirst(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			Villager cora = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Cora");
			Villager dara = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 11), "Dara");
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 6));
			Friendship.add(cora, me, 200);
			Friendship.add(dara, me, 200);
			long start = HeartEventGameTests.later(level);
			List<String> order = new ArrayList<>();
			for (int i = 0; i < 120; i++) {
				HeartEvents.tick(level, start + (long) i * HeartEvents.CHECK_EVERY);
				HeartEvents.Telling c = HeartEvents.telling(cora);
				HeartEvents.Telling d = HeartEvents.telling(dara);
				helper.assertTrue(c == null || d == null, "look " + i + ": both tell me at once: " + tellingOf(cora) + " / " + tellingOf(dara));
				String who = c != null ? "Cora" : d != null ? "Dara" : null;
				if (who != null && (order.isEmpty() || !order.get(order.size() - 1).equals(who))) {
					order.add(who);
				}
			}
			helper.assertTrue(order.equals(List.of("Cora", "Dara")), "the tellings went " + order + ", not Cora (nearer) then Dara");
			helper.assertTrue(HeartEventGameTests.told(cora, me).size() == 1 && HeartEventGameTests.told(dara, me).size() == 1,
				"told: " + HeartEventGameTests.told(cora, me) + " / " + HeartEventGameTests.told(dara, me));
			helper.assertTrue(Friendship.points(cora, me.getUUID()) == 220 && Friendship.points(dara, me.getUUID()) == 220,
				"points: " + Friendship.points(cora, me.getUUID()) + " / " + Friendship.points(dara, me.getUUID()));
			helper.assertTrue(HeartEventGameTests.friendLines(level, hall).size() == 2, "the chronicle: " + HeartEventGameTests.friendLines(level, hall));
			helper.succeed();
		});
	}

	// --- The villager leaves -------------------------------------------------------------------------------------------

	/**
	 * Two lines in, Cora is carried 12 blocks off (a cart, a boat): she is more than 8 blocks from the listener, so the
	 * telling stops with nothing kept. Back by me, she starts over from line one and it counts once.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHe1123cCarried"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHe1123cCarried")
	public void qaTheVillagerCarriedOffHalfwayStopsAndStartsOverOnReturn(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			Villager cora = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Cora");
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 6));
			Friendship.add(cora, me, 200);
			HeartEvents.Event event = HeartEvents.pending(level, cora, me.getUUID());
			long t = HeartEventGameTests.later(level);
			HeartEvents.tick(level, t);
			HeartEvents.tick(level, t + HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(cora) != null && HeartEvents.telling(cora).said == 2, "setup: not two lines in: " + tellingOf(cora));
			place(helper, cora, 5.5, 6.5 + 12);
			HeartEvents.tick(level, t + 2L * HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(cora) == null, "she went on telling from 12 blocks away: " + tellingOf(cora));
			helper.assertTrue(HeartEventGameTests.told(cora, me).isEmpty() && Friendship.points(cora, me.getUUID()) == 200
				&& HeartEventGameTests.friendLines(level, hall).isEmpty(), "half a story counted: " + HeartEventGameTests.told(cora, me));
			place(helper, cora, 5.5, 4.5);
			List<String> lines = HeartEventGameTests.listen(helper, cora, me, HeartEventGameTests.later(level), event.lines().size());
			List<String> expected = HeartEvents.lines(level, cora, me, event).stream().map(Component::getString).toList();
			helper.assertTrue(lines.equals(expected), "back, she said " + lines + ", not from line one " + expected);
			helper.assertTrue(HeartEventGameTests.told(cora, me).size() == 1 && Friendship.points(cora, me.getUUID()) == 220
				&& HeartEventGameTests.friendLines(level, hall).size() == 1, "told " + HeartEventGameTests.told(cora, me) + ", " + Friendship.points(cora, me.getUUID()));
			helper.succeed();
		});
	}

	// --- Sentences -----------------------------------------------------------------------------------------------------

	/** A parent of one names that child alone ("I mean Ana."); a parent of three lists them ("I mean Ana, Cal and Finn."). */
	//$ gametest_ticks_batch AREA '100' '"qaHe1123cKids"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHe1123cKids")
	public void qaTheChildrenAreNamedOneAloneAndThreeAsAList(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Dara");
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 6));
			String name = me.getGameProfile().getName();
			HeartEvents.Event love = HeartEvents.all().get(LOVE_PARENT);
			helper.assertTrue(love != null, "no love_parent event shipped");
			Villager ana = HeartEventGameTests.villager(helper, new BlockPos(3, 2, 8), "Ana");
			ModAttachments.PARENTS.set(ana, new Families.Parents(Component.literal("Dara"), Component.literal("Odo"), "", "", false));
			String one = HeartEvents.lines(level, dara, me, love).get(0).getString();
			helper.assertTrue(one.equals("You've met my family, haven't you, " + name + "? I mean Ana. That's where my heart lives."), "one child: " + one);
			Villager finn = HeartEventGameTests.villager(helper, new BlockPos(4, 2, 8), "Finn");
			ModAttachments.PARENTS.set(finn, new Families.Parents(Component.literal("Mira"), Component.literal("Dara"), "", "", false));
			Villager cal = HeartEventGameTests.villager(helper, new BlockPos(6, 2, 8), "Cal");
			ModAttachments.PARENTS.set(cal, new Families.Parents(Component.literal("Dara"), Component.literal("Odo"), "", "", false));
			String three = HeartEvents.lines(level, dara, me, love).get(0).getString();
			helper.assertTrue(three.equals("You've met my family, haven't you, " + name + "? I mean Ana, Cal and Finn. That's where my heart lives."), "three children: " + three);
			// A child no longer living leaves the list.
			finn.kill();
			String two = HeartEvents.lines(level, dara, me, love).get(0).getString();
			helper.assertTrue(two.equals("You've met my family, haven't you, " + name + "? I mean Ana and Cal. That's where my heart lives."), "after Finn died: " + two);
			helper.succeed();
		});
	}

	/** Whatever isn't known reads "nobody" (parents, partner, children) or "a day nobody wrote down" (the days). */
	//$ gametest_ticks_batch AREA '100' '"qaHe1123cNobody"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHe1123cNobody")
	public void qaUnknownFactsReadNobody(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager cora = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Cora");
			Object[] args = HeartEvents.args(level, cora, Component.literal("Jesse"));
			helper.assertTrue(args.length == 10, "not ten arguments: " + args.length);
			helper.assertTrue(text(args[0]).equals("Cora") && text(args[1]).equals("Jesse"), "villager, player: " + text(args[0]) + ", " + text(args[1]));
			for (int i : new int[] {3, 4, 5, 6}) {
				helper.assertTrue(text(args[i]).equals("nobody"), "%" + (i + 1) + "$s reads '" + text(args[i]) + "', not 'nobody'");
			}
			for (int i : new int[] {7, 8}) {
				helper.assertTrue(text(args[i]).equals("a day nobody wrote down"), "%" + (i + 1) + "$s reads '" + text(args[i]) + "'");
			}
			helper.assertTrue(!text(args[2]).isBlank() && !text(args[2]).contains("aliveworkplace"), "the village: " + text(args[2]));
			helper.succeed();
		});
	}

	// --- Switches ------------------------------------------------------------------------------------------------------

	/**
	 * With `heartEvents` off what was told stays: the life story page still opens and shows the story's book, and
	 * nobody starts telling. With `friendship` off too, the page shows no hearts.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHe1123cOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHe1123cOff")
	public void qaSwitchedOffThePageStillOpensWithItsStoriesAndNoHearts(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			Villager cora = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Cora");
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 6));
			Friendship.add(cora, me, 200);
			HeartEvents.Event event = HeartEvents.pending(level, cora, me.getUUID());
			HeartEventGameTests.listen(helper, cora, me, HeartEventGameTests.later(level), event.lines().size());
			Friendship.add(cora, me, 200);
			HeartEvents.ENABLED = false;
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(cora) == null, "off, and she starts her 4-heart story: " + tellingOf(cora));
			ChoiceMenu menu = page(helper, me, hall, "Cora");
			ItemStack book = menu.icon(LifeStory.storySlot(0));
			helper.assertTrue(lore(book).contains("A story for 2 hearts"), "off, the told story is gone from the page: " + book.getHoverName().getString() + " " + lore(book));
			helper.assertTrue(!menu.icon(LifeStory.HEARTS).isEmpty(), "heart events off, and the page lost the hearts too");
			Friendship.ENABLED = false;
			menu = page(helper, me, hall, "Cora");
			helper.assertTrue(menu.icon(LifeStory.HEARTS).isEmpty(), "friendship off, and the page shows hearts: " + menu.icon(LifeStory.HEARTS).getHoverName().getString());
			helper.assertTrue(lore(menu.icon(LifeStory.storySlot(0))).contains("A story for 2 hearts"), "friendship off, and the told story is gone");
			helper.succeed();
		});
	}

	// --- Who tells -----------------------------------------------------------------------------------------------------

	/**
	 * Only named villagers in a village with a hall keep friendships and tell: an unnamed villager and a named one 21
	 * blocks from the only hall (outside its 16) start nothing, however near the player.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHe1123cWho"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHe1123cWho")
	public void qaUnnamedOrHalllessVillagersTellNothing(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager nameless = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), null);
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 6));
			Friendship.add(nameless, me, 1000);
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(nameless) == null, "an unnamed villager tells: " + tellingOf(nameless));
			nameless.discard();
			Villager far = HeartEventGameTests.villager(helper, new BlockPos(9, 2, -12), "Wren");
			Leftovers.after(helper, far::discard);
			place(helper, me, 9.5, -10.5);
			Friendship.add(far, me, 1000);
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(far) == null, "a villager outside any hall's village tells: " + tellingOf(far));
			helper.assertTrue(me.getInventory().isEmpty(), "a keepsake from someone who keeps no friendship");
			helper.succeed();
		});
	}
}
