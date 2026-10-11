package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.HeartEvents;
import io.github.jcondedata.aliveworkplace.story.LifeStory;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Heart events and life stories (ROADMAP 31.7): each of the four "Where I come from" variants told for its facts and
 * nothing under 2 hearts, once per player, walking off and coming back, the chronicle's FRIEND line, the life-story
 * page from a shift-click on the hall's list, every condition of the data format, broken files skipped, the switch
 * off, a villager who goes to work or dies halfway, two players kept apart, a save and reload, and every sentence a
 * player reads. The engine doesn't tick itself in gametests ({@link HeartEvents#AUTO}); the tests tick it, with a
 * clock of their own for the three seconds between lines.
 */
public class HeartEventGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:big_area";
	static final BlockPos HALL = new BlockPos(9, 2, 9);
	static final String ME = "test-mock-player";

	private static final ResourceLocation BORN = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "born_here");
	private static final ResourceLocation TRAVELLER = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "traveller");
	private static final ResourceLocation BEFORE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "before_hall");
	private static final ResourceLocation GRAVE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "back_from_grave");

	// --- Setting up -----------------------------------------------------------------------------------------------

	/** A hall, alone in the batch, with the events as shipped; {@code then} runs once its POI is in. Everything is put back afterwards. */
	static void village(GameTestHelper helper, Runnable then) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean friendship = Friendship.ENABLED;
		boolean events = HeartEvents.ENABLED;
		boolean graves = Graves.ENABLED;
		Map<ResourceLocation, JsonElement> files = HeartEvents.files(level.getServer().getResourceManager());
		HeartEvents.load(files);
		VillageHalls.RADIUS = 16;
		Friendship.ENABLED = true;
		HeartEvents.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Friendship.ENABLED = friendship;
			HeartEvents.ENABLED = events;
			Graves.ENABLED = graves;
			HeartEvents.load(files);
			CivicEffects.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			CivicEffects.forget(); // the hall's POI goes in after the tick it was placed
			then.run();
		});
	}

	static Villager villager(GameTestHelper helper, BlockPos at, String name) {
		ServerLevel level = helper.getLevel();
		Villager v = EntityType.VILLAGER.create(level);
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
		v.moveTo(pos.x, pos.y, pos.z, 0, 0);
		v.setNoAi(true);
		if (name != null) {
			v.setCustomName(Component.literal(name));
		}
		level.addFreshEntity(v);
		return v;
	}

	static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
		player.moveTo(pos.x, pos.y, pos.z, 0, 0);
		return player;
	}

	private static void moveTo(GameTestHelper helper, ServerPlayer player, BlockPos at) {
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
		player.moveTo(pos.x, pos.y, pos.z, 0, 0);
	}

	/** The line over {@code villager}'s head (lines are stamped with the level's time, whatever clock the engine was ticked with). */
	private static String said(Villager villager, long now) {
		WorkerStatus.Entry entry = WorkerStatus.get(villager, villager.level().getGameTime());
		return entry == null || entry.line() == null ? "" : entry.line().getString();
	}

	static List<String> friendLines(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.FRIEND).map(e -> e.text().getString()).toList();
	}

	static List<String> told(Villager villager, ServerPlayer player) {
		return Friendship.of(villager).bond(player.getUUID()).told();
	}

	/** The tests' clock: far enough on from the last use that no line set before is still fresh. */
	private static long clock;

	static long later(ServerLevel level) {
		clock = Math.max(clock, level.getGameTime()) + 1000;
		return clock;
	}

	/**
	 * Ticks the engine through a whole telling from {@code start}: a line every three seconds, each over the villager's
	 * head, and nothing before its time. Returns the lines said.
	 */
	static List<String> listen(GameTestHelper helper, Villager villager, ServerPlayer player, long start, int lines) {
		ServerLevel level = helper.getLevel();
		List<String> out = new ArrayList<>();
		for (int i = 0; i < lines; i++) {
			long at = start + (long) i * HeartEvents.LINE_EVERY;
			if (i > 0) {
				HeartEvents.tick(level, at - 10);
				HeartEvents.Telling early = HeartEvents.telling(villager);
				helper.assertTrue(early != null && early.said == i, "line " + (i + 1) + " came before its three seconds: " + (early == null ? "stopped" : early.said));
			}
			HeartEvents.tick(level, at);
			HeartEvents.Telling telling = HeartEvents.telling(villager);
			helper.assertTrue(i == lines - 1 ? telling == null : telling != null && telling.said == i + 1 && telling.player.equals(player.getUUID()),
				"after line " + (i + 1) + " of " + lines + ": " + (telling == null ? "no telling" : "said " + telling.said));
			out.add(said(villager, at));
		}
		return out;
	}

	private static List<String> lore(ItemStack icon) {
		ItemLore lore = icon.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static int slotOf(ChoiceMenu menu, String name) {
		for (int slot = VillageHallScreen.FIRST_PERSON; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).getHoverName().getString().equals(name)) {
				return slot;
			}
		}
		return -1;
	}

	private static JsonElement json(String text) {
		return JsonParser.parseString(text);
	}

	// --- The four variants ----------------------------------------------------------------------------------------

	/** At 2 hearts each "Where I come from" variant is told for its facts, line by line, and nothing under 2 hearts. */
	//$ gametest_ticks_batch AREA '80' '"heartVariants"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "heartVariants")
	public void eachVariantIsToldForItsFacts(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			String village = VillageHalls.name(level, hall).getString();
			ServerPlayer me = player(helper, new BlockPos(5, 2, 6));
			// Born here: parents on record.
			Villager dara = villager(helper, new BlockPos(5, 2, 4), "Dara");
			ModAttachments.PARENTS.set(dara, new Families.Parents(Component.literal("Mira"), Component.literal("Tomas"), "", "", true));
			// A traveller hired at an inn, through the inn's own hiring.
			Villager bram = villager(helper, new BlockPos(4, 2, 5), null);
			bram.setVillagerData(bram.getVillagerData().setProfession(VillagerProfession.NITWIT));
			ModAttachments.TRAVELLER.set(bram, new Traveller(level.getGameTime(), 3));
			me.getInventory().add(new ItemStack(Items.EMERALD, 64));
			helper.assertTrue(Innkeepers.hire(me, bram), "the traveller wasn't hired");
			bram.setCustomName(Component.literal("Bram"));
			long day = Chronicle.day(level);
			helper.assertTrue(ModAttachments.HIRED_DAY.getOrElse(bram, -1L) == day, "hired day: " + ModAttachments.HIRED_DAY.get(bram) + " on day " + day);
			// Here before the hall: no facts at all.
			Villager cora = villager(helper, new BlockPos(6, 2, 5), "Cora");
			// Back from the grave, through the grave's own revival (born here too: the grave is what she tells).
			Villager edda = villager(helper, new BlockPos(6, 2, 7), "Edda");
			edda.setVillagerData(edda.getVillagerData().setProfession(VillagerProfession.FARMER));
			ModAttachments.PARENTS.set(edda, new Families.Parents(Component.literal("Mira"), Component.literal("Tomas"), "", "", true));
			Friendship.add(edda, me, 150);
			Graves.ENABLED = true;
			BlockPos grave = Graves.onDeath(level, edda);
			helper.assertTrue(grave != null, "no grave for Edda");
			edda.discard();
			Villager back = Graves.revive(level, grave);
			helper.assertTrue(back != null && ModAttachments.REVIVED.getOrElse(back, false), "revived, without the flag");
			back.setNoAi(true);
			Vec3 spot = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(4, 2, 7)));
			back.moveTo(spot.x, spot.y, spot.z, 0, 0);
			helper.assertTrue(Friendship.points(back, me.getUUID()) == 150, "her friendship didn't come back with her: " + Friendship.points(back, me.getUUID()));
			Friendship.add(back, me, -150);

			record Case(Villager villager, ResourceLocation event, List<String> lines, String chronicle, String story) {
			}
			List<Case> cases = List.of(
				new Case(dara, BORN, List.of(
					"You've been good to me, " + ME + ", so I'll tell you where I come from. It isn't far. I was born right here in " + village + ".",
					"Mira is my mother and Tomas is my father. They say I cried loud enough to set the hall bell humming.",
					"I learned to walk on these paths. I know which roofs leak, and which hens bite.",
					"People ask if I'll ever leave. Why would I? Everyone I'd miss is within shouting distance."),
					"Dara told " + ME + " about growing up in " + village, "Born in " + village + " to Mira and Tomas"),
				new Case(bram, TRAVELLER, List.of(
					"Did you know I wasn't born here, " + ME + "? I walked in off the road with one bag and sore feet.",
					"I only meant to sleep a night at the inn. The stew was hot, and nobody asked where I'd been.",
					"Then somebody put down good coin for me to stay. That was day " + day + ". I've been unpacking ever since.",
					"I've slept under a lot of roofs. " + village + " is the first one I'd call mine.",
					"So if I fuss over the travellers at the inn, now you know why. I was one of them."),
					"Bram told " + ME + " how they came to " + village, "Came to " + village + " as a traveller and was hired at the inn on day " + day),
				new Case(cora, BEFORE, List.of(
					"You want to know where I come from, " + ME + "? Here. I was here before here had a name.",
					"There was no hall then, no bell, no list with us on it. Just a few doors and a well.",
					"Then the hall went up and somebody wrote " + village + " over the door. I had to practise saying it.",
					"It's a good name. But between us, I still think of this place as just home."),
					"Cora told " + ME + " about " + village + " before it had a name", "Lived here before the hall, when " + village + " had no name"),
				new Case(back, GRAVE, List.of(
					"I'll tell you something I don't tell many, " + ME + ". I died once. There's a stone out there that had my name on it.",
					"I remember it going quiet, the way the square does after the last lantern is put out.",
					"It wasn't cold, and it wasn't frightening. It was a long sleep with the door left open a little.",
					"Then someone called me by name. I smelled rain on turned soil, and I was standing in " + village + " again.",
					"So I don't hurry much now. Every morning since has been one I wasn't owed."),
					"Edda told " + ME + " what they remember of the grave", "Died, and was brought back from the grave"));
			List<String> chronicle = new ArrayList<>();
			for (Case c : cases) {
				String who = c.villager().getDisplayName().getString();
				// Under 2 hearts: nothing to tell, and nothing starts.
				Friendship.add(c.villager(), me, 199);
				helper.assertTrue(HeartEvents.pending(level, c.villager(), me.getUUID()) == null, who + " has something to tell at 1 heart");
				HeartEvents.tick(level, later(level));
				helper.assertTrue(HeartEvents.telling(c.villager()) == null, who + " started telling at 1 heart");
				Friendship.add(c.villager(), me, 1);
				HeartEvents.Event event = HeartEvents.pending(level, c.villager(), me.getUUID());
				helper.assertTrue(event != null && event.id().equals(c.event()), who + " would tell " + (event == null ? "nothing" : event.id()) + ", not " + c.event());
				List<String> lines = listen(helper, c.villager(), me, later(level), c.lines().size());
				helper.assertTrue(lines.equals(c.lines()), who + " said " + lines);
				for (int i = 0; i < lines.size(); i++) {
					String chat = HeartEvents.said(c.villager().getDisplayName(), HeartEvents.lines(level, c.villager(), me, event).get(i)).getString();
					helper.assertTrue(chat.equals(who + ": " + c.lines().get(i)), "in chat: " + chat);
				}
				helper.assertTrue(HeartEvents.said(Component.literal("x"), Component.literal("y")).getStyle().getColor().getValue()
					== net.minecraft.ChatFormatting.GRAY.getColor(), "the chat line isn't grey");
				helper.assertTrue(told(c.villager(), me).equals(List.of(c.event().toString())), who + " told: " + told(c.villager(), me));
				helper.assertTrue(Friendship.points(c.villager(), me.getUUID()) == 220, who + ": " + Friendship.points(c.villager(), me.getUUID()) + " points after the event");
				chronicle.add(c.chronicle());
				helper.assertTrue(friendLines(level, hall).equals(chronicle), "the chronicle: " + friendLines(level, hall));
				String story = Component.translatable(event.story(), HeartEvents.args(level, c.villager(), Component.literal(ME))).getString();
				helper.assertTrue(story.equals(c.story()), "life story line: " + story);
				// Once: nothing more to tell at 2 hearts.
				helper.assertTrue(HeartEvents.pending(level, c.villager(), me.getUUID()) == null, who + " would tell it again");
				HeartEvents.tick(level, later(level));
				helper.assertTrue(HeartEvents.telling(c.villager()) == null, who + " started again");
			}
			// A traveller hired before the day was kept says so without a number.
			ModAttachments.HIRED_DAY.remove(bram);
			String undated = HeartEvents.lines(level, bram, me, HeartEvents.all().get(TRAVELLER)).get(2).getString();
			helper.assertTrue(undated.equals("Then somebody put down good coin for me to stay. That was a day nobody wrote down. I've been unpacking ever since."), undated);
			helper.succeed();
		});
	}

	// --- Walking off, once per player ---------------------------------------------------------------------------------

	/** They walk up first; walk away halfway and nothing is kept: next time they start again from the first line. */
	//$ gametest_ticks_batch AREA '80' '"heartWalkOff"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "heartWalkOff")
	public void walkingOffAndComingBackStartsItAgain(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager cora = villager(helper, new BlockPos(5, 2, 4), "Cora");
			ServerPlayer me = player(helper, new BlockPos(5, 2, 14));
			Friendship.add(cora, me, 200);
			// Ten blocks off: too far to start.
			HeartEvents.tick(level, later(level));
			helper.assertTrue(HeartEvents.telling(cora) == null, "started from 10 blocks away");
			// Six blocks off: she sets out towards the player, looking at them, and says nothing yet.
			moveTo(helper, me, new BlockPos(5, 2, 10));
			long start = later(level);
			HeartEvents.tick(level, start);
			HeartEvents.Telling walking = HeartEvents.telling(cora);
			helper.assertTrue(walking != null && walking.said == 0 && said(cora, start).isEmpty(), "six blocks off: " + (walking == null ? "no telling" : "said " + walking.said));
			helper.assertTrue(cora.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent() && cora.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).isPresent(),
				"she isn't walking up, looking at the player");
			// The player comes close: the first line, then the second three seconds on.
			moveTo(helper, me, new BlockPos(5, 2, 6));
			HeartEvents.tick(level, start + 10);
			HeartEvents.tick(level, start + 10 + HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(cora) != null && HeartEvents.telling(cora).said == 2, "two lines in: " + HeartEvents.telling(cora));
			// Walking away halfway: it stops, and nothing was told.
			moveTo(helper, me, new BlockPos(5, 2, 15));
			HeartEvents.tick(level, start + 20 + HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(cora) == null && told(cora, me).isEmpty() && Friendship.points(cora, me.getUUID()) == 200,
				"after walking off: told " + told(cora, me) + ", " + Friendship.points(cora, me.getUUID()) + " points");
			helper.assertTrue(friendLines(level, helper.absolutePos(HALL)).isEmpty(), "a half-told story is in the chronicle");
			// Coming back: from the first line again, to the end.
			moveTo(helper, me, new BlockPos(5, 2, 6));
			List<String> lines = listen(helper, cora, me, later(level), 4);
			helper.assertTrue(lines.get(0).startsWith("You want to know where I come from") && lines.get(3).startsWith("It's a good name"), "the second time: " + lines);
			helper.assertTrue(told(cora, me).size() == 1 && Friendship.points(cora, me.getUUID()) == 220, "told " + told(cora, me));
			// Someone who can't get near tells it from where they stand once they've tried for five seconds.
			Villager dara = villager(helper, new BlockPos(5, 2, 12), "Dara");
			Friendship.add(dara, me, 200);
			long far = later(level);
			HeartEvents.tick(level, far);
			helper.assertTrue(HeartEvents.telling(dara) != null && HeartEvents.telling(dara).said == 0, "Dara didn't set out");
			HeartEvents.tick(level, far + HeartEvents.WALK_UP);
			helper.assertTrue(HeartEvents.telling(dara) != null && HeartEvents.telling(dara).said == 1, "Dara never spoke up");
			helper.succeed();
		});
	}

	/**
	 * The whole thing in the level's own time, with a villager who walks: from six blocks off she comes up to the player,
	 * then tells her four lines three seconds apart, and it's told.
	 */
	//$ gametest_ticks_batch AREA '500' '"heartWalkUp"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "heartWalkUp")
	public void sheWalksUpAndTellsItInRealTime(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager[] who = new Villager[1];
		ServerPlayer[] listener = new ServerPlayer[1];
		double[] nearest = {Double.MAX_VALUE};
		double[] before = new double[1];
		long[] start = new long[1];
		List<Long> lineTimes = new ArrayList<>();
		int[] said = {0};
		boolean[] over = {false};
		village(helper, () -> {
			for (int x = 2; x <= 8; x++) { // a pen, so the walk is hers to the player and nowhere else
				for (int z = 2; z <= 12; z++) {
					if (x == 2 || x == 8 || z == 2 || z == 12) {
						helper.setBlock(new BlockPos(x, 2, z), net.minecraft.world.level.block.Blocks.GLASS);
						helper.setBlock(new BlockPos(x, 3, z), net.minecraft.world.level.block.Blocks.GLASS);
					}
				}
			}
			Villager cora = villager(helper, new BlockPos(5, 2, 4), "Cora");
			cora.setNoAi(false);
			ServerPlayer me = player(helper, new BlockPos(5, 2, 10));
			Friendship.add(cora, me, 200);
			before[0] = Math.sqrt(cora.distanceToSqr(me));
			start[0] = level.getGameTime();
			listener[0] = me;
			who[0] = cora;
		});
		// (Registered here, not from the delayed set-up: the framework can't take new per-tick work while it runs one.)
		helper.onEachTick(() -> {
			Villager cora = who[0];
			ServerPlayer me = listener[0];
			if (cora == null || over[0]) {
				return;
			}
			long now = level.getGameTime();
			if (now % HeartEvents.CHECK_EVERY == 0) {
				HeartEvents.tick(level, now);
			}
			nearest[0] = Math.min(nearest[0], Math.sqrt(cora.distanceToSqr(me)));
			HeartEvents.Telling telling = HeartEvents.telling(cora);
			int count = telling != null ? telling.said : told(cora, me).isEmpty() ? said[0] : 4;
			while (said[0] < count) {
				said[0]++;
				lineTimes.add(now);
			}
			if (told(cora, me).isEmpty()) {
				helper.assertTrue(now - start[0] < 440, "not told after 22 seconds: " + said[0] + " lines, " + Math.sqrt(cora.distanceToSqr(me)) + " blocks off");
				return;
			}
			over[0] = true;
			helper.assertTrue(told(cora, me).equals(List.of(BEFORE.toString())), "told: " + told(cora, me));
			helper.assertTrue(nearest[0] <= HeartEvents.TALK_RANGE + 0.5 && nearest[0] < before[0] - 2, "she never walked up: from " + before[0] + " to " + nearest[0] + " blocks");
			helper.assertTrue(lineTimes.size() == 4, "lines at " + lineTimes);
			for (int i = 1; i < lineTimes.size(); i++) {
				long gap = lineTimes.get(i) - lineTimes.get(i - 1);
				helper.assertTrue(gap == HeartEvents.LINE_EVERY, "line " + (i + 1) + " came " + gap + " ticks after the one before");
			}
			helper.assertTrue(lineTimes.get(0) > start[0] && Friendship.points(cora, me.getUUID()) == 220, "points: " + Friendship.points(cora, me.getUUID()));
			helper.succeed();
		});
	}

	/** Each event is told once per player, and two players are kept apart: one's telling is not the other's. */
	//$ gametest_ticks_batch AREA '80' '"heartPlayers"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "heartPlayers")
	public void oncePerPlayerAndTwoPlayersKeptApart(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager cora = villager(helper, new BlockPos(5, 2, 5), "Cora");
			ServerPlayer alex = player(helper, new BlockPos(5, 2, 7));
			ServerPlayer jesse = player(helper, new BlockPos(7, 2, 5));
			Friendship.add(cora, alex, 200);
			Friendship.add(cora, jesse, 150);
			listen(helper, cora, alex, later(level), 4);
			helper.assertTrue(told(cora, alex).size() == 1 && told(cora, jesse).isEmpty(), "told: " + told(cora, alex) + " / " + told(cora, jesse));
			helper.assertTrue(Friendship.points(cora, alex.getUUID()) == 220 && Friendship.points(cora, jesse.getUUID()) == 150,
				"points: " + Friendship.points(cora, alex.getUUID()) + " / " + Friendship.points(cora, jesse.getUUID()));
			// Jesse, at 1 heart, is told nothing; Alex isn't told twice.
			HeartEvents.tick(level, later(level));
			helper.assertTrue(HeartEvents.telling(cora) == null, "a second telling started");
			// At 2 hearts Jesse hears it too, once.
			Friendship.add(cora, jesse, 50);
			long start = later(level);
			HeartEvents.tick(level, start);
			helper.assertTrue(HeartEvents.telling(cora) != null && HeartEvents.telling(cora).player.equals(jesse.getUUID()), "Jesse isn't the one being told");
			// While she tells Jesse, Alex (told already) changes nothing; told to the end it's Jesse's.
			for (int i = 1; i < 4; i++) {
				HeartEvents.tick(level, start + (long) i * HeartEvents.LINE_EVERY);
			}
			helper.assertTrue(told(cora, jesse).size() == 1 && Friendship.points(cora, jesse.getUUID()) == 220 && Friendship.points(cora, alex.getUUID()) == 220,
				"after Jesse: " + told(cora, jesse) + ", " + Friendship.points(cora, jesse.getUUID()) + " / " + Friendship.points(cora, alex.getUUID()));
			helper.assertTrue(friendLines(level, hall).size() == 2, "the chronicle: " + friendLines(level, hall));
			HeartEvents.tick(level, later(level));
			helper.assertTrue(HeartEvents.telling(cora) == null && friendLines(level, hall).size() == 2, "told a third time");
			// One story line on her page, however many heard it.
			helper.assertTrue(HeartEvents.toldBy(cora).size() == 1, "her story: " + HeartEvents.toldBy(cora));
			helper.succeed();
		});
	}

	// --- The life story --------------------------------------------------------------------------------------------

	/** Shift-click someone on the hall's list: their page, with your hearts, name day, family, partner and what they've told. */
	//$ gametest_ticks_batch AREA '80' '"heartPage"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "heartPage")
	public void shiftClickOnTheHallsListOpensTheLifeStory(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			String village = VillageHalls.name(level, hall).getString();
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara");
			// With a trade and a workplace, a plain click finds her (someone jobless is offered a job instead).
			dara.setVillagerData(dara.getVillagerData().setProfession(VillagerProfession.FARMER));
			dara.getBrain().setMemory(MemoryModuleType.JOB_SITE, net.minecraft.core.GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(4, 2, 4))));
			ModAttachments.PARENTS.set(dara, new Families.Parents(Component.literal("Mira"), Component.literal("Tomas"), "", "", true));
			Villager finn = villager(helper, new BlockPos(7, 2, 7), "Finn");
			ModAttachments.PARENTS.set(finn, new Families.Parents(Component.literal("Dara"), Component.literal("Odo"), "", "", true));
			Villager ana = villager(helper, new BlockPos(7, 2, 8), "Ana");
			ModAttachments.PARENTS.set(ana, new Families.Parents(Component.literal("Dara"), Component.literal("Odo"), "", "", true));
			ServerPlayer me = player(helper, new BlockPos(5, 2, 7));
			Friendship.add(dara, me, 150);
			ChoiceMenu menu = VillageHallScreen.forTest(me, hall);
			int slot = slotOf(menu, "Dara");
			helper.assertTrue(slot >= 0, "Dara isn't on the hall's list");
			helper.assertTrue(lore(menu.icon(slot)).contains("Shift-click: their life story"), "no hint on the list: " + lore(menu.icon(slot)));
			// A plain click still finds them, and opens nothing.
			menu.clicked(slot, 0, ClickType.PICKUP, me);
			helper.assertTrue(dara.hasEffect(MobEffects.GLOWING) && slotOf(menu, "Dara") == slot, "a plain click didn't make her glow");
			// The shift-click, as the client sends it.
			menu.clicked(slot, 0, ClickType.QUICK_MOVE, me);
			helper.assertTrue(menu.icon(4).getHoverName().getString().equals("The life story of Dara")
				&& lore(menu.icon(4)).equals(List.of("Their family, and what they have told their friends")), "the page's header: " + menu.icon(4).getHoverName().getString());
			helper.assertTrue(menu.icon(LifeStory.HEARTS).getHoverName().getString().equals("Your hearts: ♥♡♡♡♡♡♡♡♡♡")
				&& lore(menu.icon(LifeStory.HEARTS)).equals(List.of("They'll have something to tell you at 2 hearts")),
				"hearts: " + menu.icon(LifeStory.HEARTS).getHoverName().getString() + " " + lore(menu.icon(LifeStory.HEARTS)));
			String nameDay = menu.icon(LifeStory.NAME_DAY).getHoverName().getString();
			helper.assertTrue(nameDay.startsWith("Name day") && lore(menu.icon(LifeStory.NAME_DAY)).equals(List.of("A gift on their name day counts three times")),
				"name day: " + nameDay);
			helper.assertTrue(menu.icon(LifeStory.FAMILY).getHoverName().getString().equals("Family")
				&& lore(menu.icon(LifeStory.FAMILY)).equals(List.of("Child of Mira and Tomas", "Children: Ana and Finn")), "family: " + lore(menu.icon(LifeStory.FAMILY)));
			helper.assertTrue(menu.icon(LifeStory.PARTNER).getHoverName().getString().equals("Partner")
				&& lore(menu.icon(LifeStory.PARTNER)).equals(List.of("Not with anyone")), "partner: " + lore(menu.icon(LifeStory.PARTNER)));
			int story = LifeStory.storySlot(0);
			helper.assertTrue(menu.icon(story + 4).getHoverName().getString().equals("No stories told yet")
				&& lore(menu.icon(story + 4)).equals(List.of("Make friends with Dara: at 2 hearts they'll have something to tell")),
				"an empty story: " + menu.icon(story + 4).getHoverName().getString() + " " + lore(menu.icon(story + 4)));
			// The personal request (31.9) has nothing to show yet: no slot is kept for it.
			helper.assertTrue(menu.icon(LifeStory.SECTIONS + 9).isEmpty(), "something under the story: " + menu.icon(LifeStory.SECTIONS + 9));
			// Back to the list, where she still is.
			menu.clicked(0, 0, ClickType.PICKUP, me);
			helper.assertTrue(slotOf(menu, "Dara") == slot, "Back didn't return to the list");
			// At 2 hearts the page says she has something to tell; told, it's her story's first line.
			Friendship.add(dara, me, 50);
			LifeStory.render(menu, level, hall, dara, 0, me);
			helper.assertTrue(lore(menu.icon(LifeStory.HEARTS)).equals(List.of("Dara has something to tell you: find them when they're off work")),
				"hearts at 2: " + lore(menu.icon(LifeStory.HEARTS)));
			listen(helper, dara, me, later(level), 4);
			menu.clicked(0, 0, ClickType.PICKUP, me);
			menu.clicked(slot, 0, ClickType.QUICK_MOVE, me);
			helper.assertTrue(menu.icon(story).is(Items.BOOK) && menu.icon(story).getHoverName().getString().equals("Born in " + village + " to Mira and Tomas")
				&& lore(menu.icon(story)).equals(List.of("A story for 2 hearts", "Told to " + ME)),
				"her story: " + menu.icon(story).getHoverName().getString() + " " + lore(menu.icon(story)));
			helper.assertTrue(menu.icon(story + 1).isEmpty() && menu.icon(story + 4).isEmpty(), "more than one line of story");
			// Nothing more to tell yet: the next is the 4-heart event (31.8).
			helper.assertTrue(lore(menu.icon(LifeStory.HEARTS)).equals(List.of("They'll have something to tell you at 4 hearts")),
				"nothing more to tell, yet: " + lore(menu.icon(LifeStory.HEARTS)));
			// A widow's page names who they lost; a couple's who they're with.
			ModAttachments.LATE_PARTNER.set(dara, new Couples.LatePartner(UUID.randomUUID(), Component.literal("Odo")));
			LifeStory.render(menu, level, hall, dara, 0, me);
			helper.assertTrue(lore(menu.icon(LifeStory.PARTNER)).equals(List.of("Widowed: was with Odo")), "a widow: " + lore(menu.icon(LifeStory.PARTNER)));
			ModAttachments.PARTNER.set(dara, new Couples.Partner(finn.getUUID(), Component.literal("Bram"), 1, true));
			LifeStory.render(menu, level, hall, dara, 0, me);
			helper.assertTrue(lore(menu.icon(LifeStory.PARTNER)).equals(List.of("Married to Bram", "Widowed: was with Odo")), "married again: " + lore(menu.icon(LifeStory.PARTNER)));
			// Someone with no family on record.
			LifeStory.render(menu, level, hall, ana, 0, me);
			helper.assertTrue(lore(menu.icon(LifeStory.FAMILY)).equals(List.of("Child of Dara and Odo")), "Ana's family: " + lore(menu.icon(LifeStory.FAMILY)));
			Villager cora = villager(helper, new BlockPos(8, 2, 5), "Cora");
			LifeStory.render(menu, level, hall, cora, 0, me);
			helper.assertTrue(lore(menu.icon(LifeStory.FAMILY)).equals(List.of("No family on record")), "Cora's family: " + lore(menu.icon(LifeStory.FAMILY)));
			// Someone without a trade: a plain click still offers them a job, a shift-click opens their page.
			menu.clicked(0, 0, ClickType.PICKUP, me);
			int finnSlot = slotOf(menu, "Finn");
			helper.assertTrue(finnSlot >= 0, "Finn isn't on the hall's list");
			menu.clicked(finnSlot, 0, ClickType.PICKUP, me);
			helper.assertTrue(!menu.icon(4).getHoverName().getString().startsWith("The life story"), "a plain click on someone jobless opened their life story");
			VillageHallScreen.showList(menu, level, hall, 0);
			menu.clicked(slotOf(menu, "Finn"), 0, ClickType.QUICK_MOVE, me);
			helper.assertTrue(menu.icon(4).getHoverName().getString().equals("The life story of Finn"), "a shift-click on Finn: " + menu.icon(4).getHoverName().getString());
			// With friendship off the page keeps its family and story, without hearts.
			Friendship.ENABLED = false;
			LifeStory.render(menu, level, hall, dara, 0, me);
			helper.assertTrue(menu.icon(LifeStory.HEARTS).isEmpty() && menu.icon(story).is(Items.BOOK), "friendship off: " + menu.icon(LifeStory.HEARTS));
			helper.succeed();
		});
	}

	// --- Conditions -------------------------------------------------------------------------------------------------

	/** Every condition of the data format holds for its fact and not without it; the lines get partner and children. */
	//$ gametest_ticks_batch AREA '80' '"heartConditions"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "heartConditions")
	public void everyConditionReadsItsFact(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mypack", "test");
			java.util.function.Function<String, HeartEvents.When> when = w -> HeartEvents.read(id, json(
				"{\"hearts\": 4, \"when\": " + w + ", \"lines\": [\"a\", \"b\", \"c\"], \"chronicle\": \"c\", \"story\": \"s\"}")).when();
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara");
			Villager odo = villager(helper, new BlockPos(6, 2, 5), "Odo");
			List<String> wrong = new ArrayList<>();
			java.util.function.BiConsumer<String, Boolean> check = (w, want) -> {
				if (when.apply(w).holds(level, dara) != want) {
					wrong.add(w + " should be " + want);
				}
			};
			// Nothing on record.
			for (String fact : List.of("born", "hired", "revived", "married", "courting", "widowed", "parent")) {
				check.accept("{\"" + fact + "\": true}", false);
				check.accept("{\"" + fact + "\": false}", true);
			}
			check.accept("{}", true);
			// Job families.
			check.accept("{\"jobs\": [\"minecraft:farmer\", \"minecraft:fisherman\"]}", false);
			dara.setVillagerData(dara.getVillagerData().setProfession(VillagerProfession.FARMER));
			check.accept("{\"jobs\": [\"minecraft:farmer\", \"minecraft:fisherman\"]}", true);
			check.accept("{\"jobs\": [\"minecraft:mason\"]}", false);
			// Born, hired, revived.
			ModAttachments.PARENTS.set(dara, new Families.Parents(Component.literal("Mira"), Component.literal("Tomas"), "", "", true));
			check.accept("{\"born\": true}", true);
			ModAttachments.HEAD_START.set(dara, 2);
			check.accept("{\"hired\": true, \"born\": true}", true);
			check.accept("{\"hired\": true, \"born\": false}", false);
			ModAttachments.REVIVED.set(dara, true);
			check.accept("{\"revived\": true}", true);
			// Courting, married, then widowed through the death of the partner.
			ModAttachments.PARTNER.set(dara, new Couples.Partner(odo.getUUID(), Component.literal("Odo"), 1, false));
			ModAttachments.PARTNER.set(odo, new Couples.Partner(dara.getUUID(), Component.literal("Dara"), 1, false));
			check.accept("{\"courting\": true, \"married\": false}", true);
			ModAttachments.PARTNER.set(dara, new Couples.Partner(odo.getUUID(), Component.literal("Odo"), 1, true));
			ModAttachments.PARTNER.set(odo, new Couples.Partner(dara.getUUID(), Component.literal("Dara"), 1, true));
			check.accept("{\"married\": true, \"courting\": false, \"widowed\": false}", true);
			ServerPlayer me = player(helper, new BlockPos(5, 2, 7));
			helper.assertTrue(HeartEvents.args(level, dara, Component.literal(ME))[5].toString().contains("Odo"), "the partner isn't an argument");
			Couples.onDeath(level, odo);
			Couples.LatePartner late = ModAttachments.LATE_PARTNER.get(dara);
			helper.assertTrue(late != null && late.id().equals(odo.getUUID()) && late.name().getString().equals("Odo"), "late partner: " + late);
			check.accept("{\"widowed\": true, \"married\": false}", true);
			helper.assertTrue(((Component) HeartEvents.args(level, dara, Component.literal(ME))[5]).getString().equals("Odo"), "the late partner isn't an argument");
			// A parent: a child of hers lives in the village.
			helper.assertTrue(((Component) HeartEvents.args(level, dara, Component.literal(ME))[6]).getString().equals("nobody"), "children of nobody");
			Villager finn = villager(helper, new BlockPos(7, 2, 7), "Finn");
			ModAttachments.PARENTS.set(finn, new Families.Parents(Component.literal("Dara"), Component.literal("Odo"), "", "", false));
			check.accept("{\"parent\": true}", true);
			helper.assertTrue(((Component) HeartEvents.args(level, dara, Component.literal(ME))[6]).getString().equals("Finn"), "one child");
			for (String name : List.of("Ana", "Cal")) {
				ModAttachments.PARENTS.set(villager(helper, new BlockPos(8, 2, 7), name),
					new Families.Parents(Component.literal("Dara"), Component.literal("Odo"), "", "", false));
			}
			helper.assertTrue(((Component) HeartEvents.args(level, dara, Component.literal(ME))[6]).getString().equals("Ana, Cal and Finn"),
				"three children: " + ((Component) HeartEvents.args(level, dara, Component.literal(ME))[6]).getString());
			// A trait (they come from the UUID): whichever she has, and one she hasn't.
			for (Traits.Trait trait : Traits.Trait.values()) {
				check.accept("{\"trait\": \"" + trait.key() + "\"}", Traits.has(dara, trait));
			}
			// The village's rank, at least: a new hall is a hamlet.
			check.accept("{\"rank\": \"hamlet\"}", true);
			check.accept("{\"rank\": \"town\"}", false);
			// Mood reasons: any one of those she feels now.
			List<String> reasons = HeartEvents.moodReasons(dara);
			check.accept("{\"mood\": [\"no_such_reason\"]}", false);
			if (!reasons.isEmpty()) {
				check.accept("{\"mood\": [\"no_such_reason\", \"" + reasons.get(0) + "\"]}", true);
			}
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
			// A pack's event at 2 hearts for widows comes before ours by id only when its conditions hold.
			Map<ResourceLocation, JsonElement> files = new TreeMap<>(HeartEvents.files(level.getServer().getResourceManager()));
			files.put(ResourceLocation.fromNamespaceAndPath("aaa", "widow"), json("{\"hearts\": 2, \"when\": {\"widowed\": true, \"revived\": false},"
				+ " \"lines\": [\"a\", \"b\", \"c\"], \"chronicle\": \"c\", \"story\": \"s\"}"));
			HeartEvents.load(files);
			Friendship.add(dara, me, 200);
			helper.assertTrue(HeartEvents.pending(level, dara, me.getUUID()).id().equals(GRAVE), "revived, she'd tell " + HeartEvents.pending(level, dara, me.getUUID()).id());
			ModAttachments.REVIVED.remove(dara);
			helper.assertTrue(HeartEvents.pending(level, dara, me.getUUID()).id().getNamespace().equals("aaa"), "a widow would tell " + HeartEvents.pending(level, dara, me.getUUID()).id());
			// One event per heart level: told the pack's, ours at 2 hearts isn't told as well.
			listen(helper, dara, me, later(level), 3);
			helper.assertTrue(told(dara, me).equals(List.of("aaa:widow")) && HeartEvents.pending(level, dara, me.getUUID()) == null,
				"after the pack's event: " + told(dara, me) + ", pending " + HeartEvents.pending(level, dara, me.getUUID()));
			helper.succeed();
		});
	}

	// --- Data files -------------------------------------------------------------------------------------------------

	/** A broken event file is skipped with a log line naming it; the rest load. The four shipped events are as the roadmap says. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"heartData"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "heartData")
	public void aBrokenEventFileIsSkipped(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<ResourceLocation, JsonElement> shipped = HeartEvents.files(level.getServer().getResourceManager());
		Leftovers.after(helper, () -> HeartEvents.load(shipped));
		// The files as the server read them: ours load, and the test pack's broken one (a single line) is skipped and named.
		ResourceLocation packs = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "broken");
		helper.assertTrue(shipped.containsKey(packs), "the test pack's broken file wasn't found: " + shipped.keySet());
		List<ResourceLocation> first = HeartEvents.load(shipped);
		helper.assertTrue(first.equals(List.of(packs)), "skipped of the files on disk: " + first);
		for (String key : List.of("aliveworkplace.config.heartEvents", "aliveworkplace.config.heartEvents.tooltip")) {
			helper.assertTrue(!Component.translatable(key).getString().equals(key), "no text for " + key);
		}
		helper.assertTrue(Component.translatable("aliveworkplace.config.heartEvents").getString().equals("Heart events"), "the switch's name");
		// The four 2-heart events (the events of 4 to 10 hearts, 31.8, are HeartEventSetGameTests').
		Map<ResourceLocation, Integer> lines = Map.of(BORN, 4, TRAVELLER, 5, BEFORE, 4, GRAVE, 5);
		List<HeartEvents.Event> atTwo = HeartEvents.all().values().stream().filter(e -> e.hearts() == 2).toList();
		helper.assertTrue(atTwo.stream().map(HeartEvents.Event::id).collect(java.util.stream.Collectors.toSet()).equals(lines.keySet()),
			"shipped at 2 hearts: " + atTwo.stream().map(HeartEvents.Event::id).toList());
		int ours = HeartEvents.all().size();
		for (HeartEvents.Event event : atTwo) {
			helper.assertTrue(event.lines().size() == lines.get(event.id()), event.id() + ": " + event.lines().size() + " lines");
			List<String> keys = new ArrayList<>(event.lines());
			keys.add(event.chronicle());
			keys.add(event.story());
			for (String key : keys) {
				helper.assertTrue(!Component.translatable(key).getString().equals(key), event.id() + ": no text for " + key);
			}
		}
		String good = "\"lines\": [\"a\", \"b\", \"c\"], \"chronicle\": \"c\", \"story\": \"s\"";
		Map<String, String> broken = new TreeMap<>(Map.of(
			"no_hearts", "{" + good + "}",
			"hearts_zero", "{\"hearts\": 0, " + good + "}",
			"hearts_eleven", "{\"hearts\": 11, " + good + "}",
			"two_lines", "{\"hearts\": 2, \"lines\": [\"a\", \"b\"], \"chronicle\": \"c\", \"story\": \"s\"}",
			"six_lines", "{\"hearts\": 2, \"lines\": [\"a\", \"b\", \"c\", \"d\", \"e\", \"f\"], \"chronicle\": \"c\", \"story\": \"s\"}",
			"no_chronicle", "{\"hearts\": 2, \"lines\": [\"a\", \"b\", \"c\"], \"story\": \"s\"}",
			"bad_condition", "{\"hearts\": 2, \"when\": {\"rich\": true}, " + good + "}",
			"bad_trait", "{\"hearts\": 2, \"when\": {\"trait\": \"sleepy\"}, " + good + "}",
			"bad_rank", "{\"hearts\": 2, \"when\": {\"rank\": \"empire\"}, " + good + "}",
			"not_an_object", "[]"));
		Map<ResourceLocation, JsonElement> files = new TreeMap<>(shipped);
		broken.forEach((name, text) -> files.put(ResourceLocation.fromNamespaceAndPath("mypack", name), json(text)));
		files.put(ResourceLocation.fromNamespaceAndPath("mypack", "fine"), json("{\"hearts\": 4, \"when\": {\"born\": true, \"hired\": false, \"revived\": false,"
			+ " \"jobs\": [\"minecraft:farmer\"], \"married\": true, \"courting\": false, \"widowed\": false, \"parent\": true, \"trait\": \"cheerful\","
			+ " \"mood\": [\"hungry\"], \"rank\": \"town\"}, " + good + "}"));
		files.put(ResourceLocation.fromNamespaceAndPath("mypack", "off"), json("{\"enabled\": false, \"hearts\": 2, " + good + "}"));
		List<String> skipped = HeartEvents.load(files).stream().map(ResourceLocation::getPath).sorted().toList();
		List<String> expected = new ArrayList<>(broken.keySet());
		expected.add("broken"); // the test pack's
		expected.sort(java.util.Comparator.naturalOrder());
		helper.assertTrue(skipped.equals(expected), "skipped as broken: " + skipped);
		helper.assertTrue(HeartEvents.all().size() == ours + 1 && HeartEvents.all().keySet().containsAll(lines.keySet()), "loaded: " + HeartEvents.all().keySet());
		HeartEvents.Event fine = HeartEvents.all().get(ResourceLocation.fromNamespaceAndPath("mypack", "fine"));
		HeartEvents.When w = fine.when();
		helper.assertTrue(fine.hearts() == 4 && Boolean.TRUE.equals(w.born()) && Boolean.FALSE.equals(w.hired()) && Boolean.FALSE.equals(w.revived())
			&& w.jobs().equals(java.util.Set.of(ResourceLocation.withDefaultNamespace("farmer"))) && Boolean.TRUE.equals(w.married())
			&& Boolean.FALSE.equals(w.courting()) && Boolean.FALSE.equals(w.widowed()) && Boolean.TRUE.equals(w.parent()) && w.trait() == Traits.Trait.CHEERFUL
			&& w.moods().equals(java.util.Set.of("hungry")) && w.rank() == io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.TOWN, "a pack's event: " + fine);
		helper.succeed();
	}

	// --- Off, interrupted, saved -------------------------------------------------------------------------------------

	/** {@code heartEvents} off: nobody starts and a telling stops, nothing kept. Going to work or dying halfway stops it too. */
	//$ gametest_ticks_batch AREA '80' '"heartOff"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "heartOff")
	public void theSwitchOffAndInterruptionsStopATelling(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager cora = villager(helper, new BlockPos(5, 2, 5), "Cora");
			ServerPlayer me = player(helper, new BlockPos(5, 2, 7));
			Friendship.add(cora, me, 200);
			// Off: nothing to tell, nothing starts.
			HeartEvents.ENABLED = false;
			HeartEvents.tick(level, later(level));
			helper.assertTrue(HeartEvents.pending(level, cora, me.getUUID()) == null && HeartEvents.telling(cora) == null, "off, and she tells it");
			// On, started; switched off halfway: it stops and nothing is kept.
			HeartEvents.ENABLED = true;
			long start = later(level);
			HeartEvents.tick(level, start);
			helper.assertTrue(HeartEvents.telling(cora) != null && HeartEvents.telling(cora).said == 1, "on again, she doesn't start");
			HeartEvents.ENABLED = false;
			HeartEvents.tick(level, start + HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(cora) == null && told(cora, me).isEmpty() && Friendship.points(cora, me.getUUID()) == 200, "switched off halfway: " + told(cora, me));
			HeartEvents.ENABLED = true;
			// With friendship off there are no hearts to tell it at.
			Friendship.ENABLED = false;
			HeartEvents.tick(level, later(level));
			helper.assertTrue(HeartEvents.telling(cora) == null, "friendship off, and she tells it");
			Friendship.ENABLED = true;
			// She's given work halfway (a work line over her head): the telling stops; off work again, it starts over.
			start = later(level);
			HeartEvents.tick(level, start);
			HeartEvents.tick(level, start + HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(cora) != null && HeartEvents.telling(cora).said == 2, "two lines in");
			WorkerStatus.set(cora, Component.literal("Farmer"), 0.5f, Component.literal("Harvesting wheat"));
			HeartEvents.tick(level, level.getGameTime() + 10);
			helper.assertTrue(HeartEvents.telling(cora) == null && told(cora, me).isEmpty(), "at work, and still telling");
			HeartEvents.tick(level, level.getGameTime() + 20);
			helper.assertTrue(HeartEvents.telling(cora) == null, "started while at work");
			helper.assertTrue(said(cora, level.getGameTime()).equals("Harvesting wheat"), "her work line was written over: " + said(cora, level.getGameTime()));
			// Asleep, she starts nothing.
			long quiet = later(level);
			HeartEvents.tick(level, quiet);
			helper.assertTrue(HeartEvents.telling(cora) != null && HeartEvents.telling(cora).said == 1, "off work again, she doesn't start over");
			// She dies halfway: nothing is told, nothing breaks, and the chronicle has no line.
			cora.discard();
			HeartEvents.tick(level, quiet + HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(cora) == null && friendLines(level, hall).isEmpty(), "a telling outlived her");
			// Someone unnamed, or outside any hall's village, tells nothing (they keep no friendship).
			Villager nameless = villager(helper, new BlockPos(5, 2, 5), null);
			helper.assertTrue(HeartEvents.pending(level, nameless, me.getUUID()) == null, "an unnamed villager has something to tell");
			helper.succeed();
		});
	}

	/** Saved halfway, nothing of the telling is kept; told, it's saved with the villager, as are the new facts. Old saves read as before. */
	//$ gametest_ticks_batch AREA '80' '"heartSaved"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "heartSaved")
	public void aSaveAndReloadKeepsWhatWasTold(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager cora = villager(helper, new BlockPos(5, 2, 5), "Cora");
			ServerPlayer me = player(helper, new BlockPos(5, 2, 7));
			Friendship.add(cora, me, 200);
			long start = later(level);
			HeartEvents.tick(level, start);
			HeartEvents.tick(level, start + HeartEvents.LINE_EVERY);
			// Saved two lines in: the copy that loads has been told nothing, and would start from the first line.
			Villager halfway = EntityType.VILLAGER.create(level);
			halfway.load(cora.saveWithoutId(new CompoundTag()));
			helper.assertTrue(Friendship.of(halfway).bond(me.getUUID()).told().isEmpty() && Friendship.of(halfway).bond(me.getUUID()).points() == 200,
				"saved halfway: " + Friendship.of(halfway));
			halfway.discard();
			// A reload forgets the telling (as a restart does): she starts again and tells it to the end.
			HeartEvents.forget();
			helper.assertTrue(HeartEvents.telling(cora) == null, "a telling survived the restart");
			List<String> lines = listen(helper, cora, me, later(level), 4);
			helper.assertTrue(lines.get(0).startsWith("You want to know where I come from"), "after the reload she began with: " + lines.get(0));
			ModAttachments.REVIVED.set(cora, true);
			ModAttachments.HIRED_DAY.set(cora, 12L);
			UUID odo = UUID.randomUUID();
			ModAttachments.LATE_PARTNER.set(cora, new Couples.LatePartner(odo, Component.literal("Odo")));
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(cora.saveWithoutId(new CompoundTag()));
			helper.assertTrue(Friendship.of(copy).equals(Friendship.of(cora)) && Friendship.of(copy).bond(me.getUUID()).told().equals(List.of(BEFORE.toString())),
				"after a reload: " + Friendship.of(copy));
			Couples.LatePartner late = ModAttachments.LATE_PARTNER.get(copy);
			helper.assertTrue(ModAttachments.REVIVED.getOrElse(copy, false) && ModAttachments.HIRED_DAY.getOrElse(copy, -1L) == 12L
				&& late != null && late.id().equals(odo) && late.name().getString().equals("Odo"), "the new facts after a reload: " + late);
			copy.discard();
			// A villager from before this update has none of them: not revived, no late partner, no hired day.
			Villager old = EntityType.VILLAGER.create(level);
			helper.assertTrue(!ModAttachments.REVIVED.has(old) && !ModAttachments.LATE_PARTNER.has(old) && !ModAttachments.HIRED_DAY.has(old)
				&& !ModAttachments.REVIVED.getOrElse(old, false), "an old villager has the new facts");
			old.discard();
			// A friendship saved before heart events (no "told") reads as nothing told.
			Friendship.Data sparse = Friendship.Data.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString(
				"{\"players\": {\"" + me.getUUID() + "\": {\"points\": 300}}}")).getOrThrow();
			helper.assertTrue(sparse.bond(me.getUUID()).told().isEmpty(), "sparse: " + sparse);
			// A chronicle saved with the new kind reads back as it; a kind this version doesn't know stays readable.
			helper.assertTrue(Chronicle.Kind.valueOf("FRIEND").icon == Items.PINK_TULIP, "the FRIEND kind's icon");
			helper.succeed();
		});
	}
}
