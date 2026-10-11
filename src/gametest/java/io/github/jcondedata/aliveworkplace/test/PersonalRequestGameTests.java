package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.LifeStory;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Personal requests (ROADMAP 31.9): a 3-heart villager asks and a 2-heart one doesn't; the walk up and the ask; accept
 * and decline through {@code /workplace quest}; each request done by its objective, paying every helper; a missed
 * deadline; one open request per villager; the journal's Personal tab, the life-story page and the hall's tooltip; the
 * new conditions; a save and reload; the switch off; and the sentences a player reads. The two Cobblemon requests are
 * in {@code PersonalRequestCompatTests}; here they are never offered. The engine doesn't tick itself in gametests
 * ({@link PersonalRequests#AUTO}); the tests tick it.
 */
public class PersonalRequestGameTests implements FabricGameTest {
	static final String AREA = HeartEventGameTests.AREA;
	static final BlockPos HALL = HeartEventGameTests.HALL;
	static final String ME = HeartEventGameTests.ME;

	static ResourceLocation file(String name) {
		return AliveWorkplace.id("personal/" + name);
	}

	// --- Setting up -----------------------------------------------------------------------------------------------

	/** A hall alone in its batch with requests on and nothing open or asked; everything is put back afterwards. */
	static void village(GameTestHelper helper, Runnable then) {
		boolean requests = PersonalRequests.ENABLED;
		boolean moods = Moods.ENABLED;
		boolean festivals = io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED;
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		HeartEventGameTests.village(helper, () -> {
			PersonalRequests.ENABLED = true;
			io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED = true; // off in the GameTest server; "before the next festival" needs them
			PersonalRequests.forget();
			Stories.Data.get(level).remove(hall);
			Leftovers.after(helper, () -> {
				PersonalRequests.ENABLED = requests;
				io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED = festivals;
				Moods.ENABLED = moods;
				Moods.forget();
				PersonalRequests.forget();
				Stories.Data.get(level).remove(hall);
			});
			then.run();
		});
	}

	static Villager worker(GameTestHelper helper, BlockPos at, String name, String job, int jobLevel) {
		Villager v = HeartEventGameTests.villager(helper, at, name);
		v.setVillagerData(v.getVillagerData().setProfession(BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(job))).setLevel(jobLevel));
		return v;
	}

	static ServerPlayer friend(GameTestHelper helper, BlockPos at, Villager villager, int points) {
		ServerPlayer player = HeartEventGameTests.player(helper, at);
		Friendship.add(villager, player, points);
		return player;
	}

	static VillageHallBlockEntity hallEntity(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
	}

	/** {@code villager}'s request from the file {@code name} for {@code player}, as it would be asked now; null when it isn't theirs to ask. */
	static Quest resolve(GameTestHelper helper, String name, Villager villager, ServerPlayer player) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		QuestFiles.QuestFile file = QuestFiles.get(file(name)).orElse(null);
		helper.assertTrue(file != null, "no quest file personal/" + name);
		return PersonalRequests.resolve(level, hall, hallEntity(helper), VillageHalls.census(level, hall), file, villager, player, RandomSource.create(7));
	}

	/** {@code villager} asks {@code player} for the request {@code name}, and the player takes it through the command. */
	static Quest accepted(GameTestHelper helper, String name, Villager villager, ServerPlayer player) {
		ServerLevel level = helper.getLevel();
		Quest quest = resolve(helper, name, villager, player);
		helper.assertTrue(quest != null, villager.getDisplayName().getString() + " has no " + name + " to ask");
		PersonalRequests.propose(level, helper.absolutePos(HALL), villager, player, quest, Chronicle.day(level));
		command(player, "workplace quest accept " + quest.id);
		helper.assertTrue(PersonalRequests.of(villager) == quest && quest.helpers.containsKey(player.getUUID()), name + " wasn't accepted");
		return quest;
	}

	static void command(ServerPlayer player, String command) {
		player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	static List<String> lore(ItemStack icon) {
		ItemLore lore = icon.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	static String said(Villager villager) {
		WorkerStatus.Entry entry = WorkerStatus.get(villager, villager.level().getGameTime());
		return entry == null || entry.line() == null ? "" : entry.line().getString();
	}

	static List<String> friendLines(GameTestHelper helper) {
		return HeartEventGameTests.friendLines(helper.getLevel(), helper.absolutePos(HALL));
	}

	/** The commands behind the clickable parts of {@code text}, in order. */
	static List<String> commands(Component text) {
		List<String> out = new ArrayList<>();
		collect(text, out);
		return out;
	}

	private static void collect(Component text, List<String> out) {
		ClickEvent click = text.getStyle().getClickEvent();
		if (click != null && click.getAction() == ClickEvent.Action.RUN_COMMAND) {
			out.add(click.getValue());
		}
		if (text.getContents() instanceof TranslatableContents t) {
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component c) {
					collect(c, out);
				}
			}
		}
		text.getSiblings().forEach(s -> collect(s, out));
	}

	// --- The asking -----------------------------------------------------------------------------------------------

	/** A villager with 3 hearts and a friend near asks; one with 2 hearts doesn't, nor one whose friend is far away. The roll is one in four. */
	//$ gametest_ticks_batch AREA '80' '"requestAsks"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestAsks")
	public void aThreeHeartVillagerAsksAndATwoHeartOneDoesnt(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hallEntity(helper);
			long today = Chronicle.day(level);
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			Villager bram = worker(helper, new BlockPos(7, 2, 4), "Bram", "minecraft:farmer", 1);
			ServerPlayer me = friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Friendship.add(bram, me, 299);
			helper.assertTrue(Friendship.hearts(Friendship.points(dara, me.getUUID())) == 3 && Friendship.hearts(Friendship.points(bram, me.getUUID())) == 2,
				"hearts: " + Friendship.points(dara, me.getUUID()) + " and " + Friendship.points(bram, me.getUUID()));
			helper.assertTrue(PersonalRequests.mayAsk(level, hall, dara, today) && PersonalRequests.mayAsk(level, hall, bram, today), "both could ask someone");

			// Whatever the random, it's Dara who asks, and only for what is hers to ask: never a Cobblemon request here.
			Set<String> asked = new TreeSet<>();
			for (int seed = 0; seed < 40; seed++) {
				PersonalRequests.forget();
				PersonalRequests.Offer offer = PersonalRequests.pick(level, hall, entity, RandomSource.create(seed), today);
				helper.assertTrue(offer != null && offer.villager.equals(dara.getUUID()) && offer.player.equals(me.getUUID()),
					"seed " + seed + ": " + (offer == null ? "nobody asked" : "the 2-heart villager asked"));
				helper.assertTrue(offer.quest.personal() && offer.quest.poster.equals("Dara") && PersonalRequests.offer(dara) == offer, "the offer: " + offer.quest);
				asked.add(offer.quest.file.getPath());
			}
			helper.assertTrue(asked.equals(Set.of("personal/home", "personal/level_up", "personal/taste_of_home", "personal/tools_of_my_trade")),
				"a plains farmer without a home asked for: " + asked);
			// While her offer stands she asks nothing else; Bram at 2 hearts never does.
			helper.assertTrue(PersonalRequests.pick(level, hall, entity, RandomSource.create(1), today) == null, "a second villager asked at 2 hearts");
			PersonalRequests.forget();
			Friendship.add(dara, me, -1);
			helper.assertTrue(PersonalRequests.pick(level, hall, entity, RandomSource.create(1), today) == null, "asked at 299 points");
			Friendship.add(bram, me, 1);
			PersonalRequests.Offer brams = PersonalRequests.pick(level, hall, entity, RandomSource.create(1), today);
			helper.assertTrue(brams != null && brams.villager.equals(bram.getUUID()), "Bram didn't ask at 3 hearts");
			// A friend too far away isn't asked.
			PersonalRequests.forget();
			HeartEventGameTests.player(helper, new BlockPos(5, 2, 6)); // (a stranger near changes nothing)
			me.moveTo(me.getX() + PersonalRequests.NEAR + 6, me.getY(), me.getZ(), 0, 0);
			helper.assertTrue(PersonalRequests.pick(level, hall, entity, RandomSource.create(1), today) == null, "asked a friend " + Math.sqrt(me.distanceToSqr(bram)) + " blocks away");

			// The morning's roll: one in four, with a fixed random.
			RandomSource random = RandomSource.create(31_9);
			int hits = 0;
			for (int i = 0; i < 2000; i++) {
				hits += PersonalRequests.rolls(random) ? 1 : 0;
			}
			helper.assertTrue(hits > 440 && hits < 560, "2000 mornings, " + hits + " asks: not one in four");
			helper.succeed();
		});
	}

	/** They walk up first: nothing is said while they're on the way; near (or after ten seconds) they ask over their head, and the offer has both buttons. */
	//$ gametest_ticks_batch AREA '80' '"requestWalkUp"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestWalkUp")
	public void theyWalkUpAndAskWithBothButtons(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			long today = Chronicle.day(level);
			Villager dara = worker(helper, new BlockPos(3, 2, 3), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = friend(helper, new BlockPos(3, 2, 11), dara, 300);
			Quest quest = resolve(helper, "level_up", dara, me);
			PersonalRequests.Offer offer = PersonalRequests.propose(level, hall, dara, me, quest, today);
			long now = level.getGameTime();
			PersonalRequests.tick(level, now);
			helper.assertTrue(!offer.asked && said(dara).isEmpty(), "asked from 8 blocks away: " + said(dara));
			helper.assertTrue(dara.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent(), "she isn't walking up");
			PersonalRequests.tick(level, now + PersonalRequests.WALK_UP - 10);
			helper.assertTrue(!offer.asked, "asked before the ten seconds were up");
			// Near enough: she asks.
			me.moveTo(dara.getX() + 2, dara.getY(), dara.getZ(), 0, 0);
			PersonalRequests.tick(level, now + 20);
			String ask = ME + ", the festival's nearly on us, and I'd dearly love to stand there having made Journeyman. Trade with me a while, or spare me a Bottle o' Enchanting."
				+ " Would you?";
			helper.assertTrue(offer.asked && said(dara).equals(ask), "she said: " + said(dara));
			helper.assertTrue(dara.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty(), "she kept walking after asking");
			Component line = PersonalRequests.offerLine(level, quest);
			helper.assertTrue(line.getString().equals("Dara wants to make Journeyman before the next festival. For 6 emeralds and their friendship. [I'll help] [Not now]"),
				"the offer reads: " + line.getString());
			helper.assertTrue(commands(line).equals(List.of("/workplace quest accept " + quest.id, "/workplace quest decline " + quest.id)), "the buttons run: " + commands(line));
			// From where she stands, once the ten seconds are up, if the player never lets her near.
			Villager odo = worker(helper, new BlockPos(13, 2, 3), "Odo", "minecraft:farmer", 1);
			Friendship.add(odo, me, 300);
			me.moveTo(odo.getX(), odo.getY(), odo.getZ() + 8, 0, 0);
			PersonalRequests.Offer second = PersonalRequests.propose(level, hall, odo, me, resolve(helper, "level_up", odo, me), today);
			PersonalRequests.tick(level, level.getGameTime() + PersonalRequests.WALK_UP);
			helper.assertTrue(second.asked && said(odo).contains("having made Apprentice"), "from afar he said: " + said(odo));
			// The offers lapse when the player leaves the game; nothing was lost.
			level.getServer().getPlayerList().remove(me);
			PersonalRequests.tick(level, level.getGameTime() + 400);
			helper.assertTrue(PersonalRequests.offer(dara) == null && PersonalRequests.offer(odo) == null && Friendship.points(dara, me.getUUID()) == 300,
				"offers to a player who left stood");
			helper.succeed();
		});
	}

	/** [I'll help] opens the request and shows it in the journal, on the life story and on the hall's tooltip; [Not now] costs nothing; one open request per villager. */
	//$ gametest_ticks_batch AREA '80' '"requestAnswer"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestAnswer")
	public void acceptAndDecline(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hallEntity(helper);
			long today = Chronicle.day(level);
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			dara.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(4, 2, 4))));
			Villager odo = worker(helper, new BlockPos(7, 2, 4), "Odo", "minecraft:farmer", 1);
			ServerPlayer me = friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Friendship.add(odo, me, 300);
			ServerPlayer other = friend(helper, new BlockPos(6, 2, 6), dara, 300);

			// Not now: the offer is gone, nothing is open, nothing is lost, and he may ask again.
			Quest odos = resolve(helper, "level_up", odo, me);
			PersonalRequests.propose(level, hall, odo, me, odos, today);
			command(other, "workplace quest decline " + odos.id);
			helper.assertTrue(PersonalRequests.offer(odo) != null, "someone else declined his request");
			command(me, "workplace quest decline " + odos.id);
			helper.assertTrue(PersonalRequests.offer(odo) == null && PersonalRequests.of(odo) == null && Friendship.points(odo, me.getUUID()) == 300,
				"after Not now: " + PersonalRequests.of(odo) + ", " + Friendship.points(odo, me.getUUID()));
			helper.assertTrue(said(odo).equals("Another time, then, " + ME + "."), "he said: " + said(odo));
			helper.assertTrue(PersonalRequests.mayAsk(level, hall, odo, today), "he won't ask again after a Not now");
			command(me, "workplace quest accept " + odos.id);
			helper.assertTrue(PersonalRequests.of(odo) == null, "a declined request was accepted afterwards");

			// I'll help: only the one who was asked can take it.
			Quest quest = resolve(helper, "level_up", dara, me);
			PersonalRequests.propose(level, hall, dara, me, quest, today);
			command(other, "workplace quest accept " + quest.id);
			helper.assertTrue(PersonalRequests.of(dara) == null && PersonalRequests.offer(dara) != null, "someone else took her request");
			command(me, "workplace quest accept " + quest.id);
			helper.assertTrue(PersonalRequests.of(dara) == quest && PersonalRequests.offer(dara) == null && quest.helpers.keySet().equals(Set.of(me.getUUID())),
				"after I'll help: " + PersonalRequests.of(dara) + " helpers " + quest.helpers);
			helper.assertTrue(said(dara).equals("Thank you, " + ME + ". I knew I could ask you."), "she said: " + said(dara));
			helper.assertTrue(quest.villager.equals(dara.getUUID()) && quest.festival && quest.due > level.getGameTime() + 24000, "the request: due " + quest.due);

			// One open request per villager: she asks for nothing else, whatever the random.
			helper.assertTrue(!PersonalRequests.mayAsk(level, hall, dara, today), "she may ask with a request open");
			Friendship.add(odo, me, -300);
			for (int seed = 0; seed < 20; seed++) {
				helper.assertTrue(PersonalRequests.pick(level, hall, entity, RandomSource.create(seed), today) == null, "seed " + seed + ": a second request of hers");
			}
			helper.assertTrue(PersonalRequests.open(level, hall).equals(List.of(quest)), "open requests: " + PersonalRequests.open(level, hall));

			// The journal's Personal tab.
			ChoiceMenu menu = ChoiceMenu.detached(me, m -> VillageHallScreen.renderQuests(m, level, hall, me));
			helper.assertTrue(lore(menu.icon(QuestJournal.Tab.PERSONAL.slot)).equals(List.of("Requests from villagers who are your friends")),
				"the Personal tab: " + lore(menu.icon(QuestJournal.Tab.PERSONAL.slot)));
			menu.press(QuestJournal.Tab.PERSONAL.slot, me);
			ItemStack icon = menu.icon(QuestJournal.PERSONAL_ROW);
			helper.assertTrue(icon.is(Items.EXPERIENCE_BOTTLE) && icon.getHoverName().getString().equals("Next level before the festival"), "the request's icon: " + icon);
			List<String> lines = lore(icon);
			helper.assertTrue(lines.get(0).equals("Asked by Dara") && lines.get(1).equals("Help them reach Journeyman (0/1)") && lines.get(2).startsWith("Reward: "),
				"the request's lines: " + lines);
			helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("Before the next festival (")) && lines.contains("And a heart and a half of friendship for everyone who helps")
				&& lines.contains("Helping: " + ME), "the request's lines: " + lines);
			// Someone with 3 hearts sees how to join; someone without sees why not.
			ChoiceMenu others = ChoiceMenu.detached(other, m -> QuestJournal.render(m, level, hall, other, QuestJournal.Tab.PERSONAL));
			helper.assertTrue(lore(others.icon(QuestJournal.PERSONAL_ROW)).contains("Click: help with this"), "a friend's view: " + lore(others.icon(QuestJournal.PERSONAL_ROW)));
			ServerPlayer stranger = friend(helper, new BlockPos(7, 2, 6), dara, 299);
			ChoiceMenu strangers = ChoiceMenu.detached(stranger, m -> QuestJournal.render(m, level, hall, stranger, QuestJournal.Tab.PERSONAL));
			helper.assertTrue(lore(strangers.icon(QuestJournal.PERSONAL_ROW)).contains("Only their friends can help (3 hearts)"),
				"a stranger's view: " + lore(strangers.icon(QuestJournal.PERSONAL_ROW)));
			strangers.press(QuestJournal.PERSONAL_ROW, stranger);
			helper.assertTrue(!quest.helpers.containsKey(stranger.getUUID()), "joined at 2 hearts");
			// The Village tab doesn't list it, and an empty Personal tab says so.
			menu.press(QuestJournal.Tab.VILLAGE.slot, me);
			for (int slot : VillageHallScreen.QUEST_SLOTS) {
				helper.assertTrue(!menu.icon(slot).getHoverName().getString().equals("Next level before the festival"), "it's on the Village tab too");
			}

			// The hall's tooltip for her, and her life story.
			String wants = "Dara wants to make Journeyman before the next festival";
			helper.assertTrue(lore(VillageHallScreen.person(level, hall, dara, me)).contains(wants), "the hall's tooltip: " + lore(VillageHallScreen.person(level, hall, dara, me)));
			helper.assertTrue(!lore(VillageHallScreen.person(level, hall, odo, me)).toString().contains("wants"), "Odo's tooltip has a request");
			ChoiceMenu story = ChoiceMenu.detached(me, m -> LifeStory.render(m, level, hall, dara, 0, me));
			ItemStack section = story.icon(LifeStory.SECTIONS);
			helper.assertTrue(section.getHoverName().getString().equals(wants)
				&& lore(section).equals(List.of("Help them reach Journeyman (0/1)", "Helping: " + ME, "You're helping with this")),
				"her life story: " + section.getHoverName().getString() + " " + lore(section));
			ChoiceMenu odosStory = ChoiceMenu.detached(me, m -> LifeStory.render(m, level, hall, odo, 0, me));
			helper.assertTrue(!odosStory.icon(LifeStory.SECTIONS).getHoverName().getString().contains("wants"), "Odo's life story has a request");
			helper.succeed();
		});
	}

	// --- The requests ---------------------------------------------------------------------------------------------

	/** Next level before the festival: done when she reaches the level, in the hall's round; both helpers get the emeralds and the friendship, and the chronicle a line. */
	//$ gametest_ticks_batch AREA '80' '"requestLevelUp"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestLevelUp")
	public void theNextLevelPaysEveryHelper(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hallEntity(helper);
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			ServerPlayer me = friend(helper, new BlockPos(5, 2, 6), dara, 300);
			ServerPlayer other = friend(helper, new BlockPos(6, 2, 6), dara, 420);
			ServerPlayer stranger = friend(helper, new BlockPos(7, 2, 6), dara, 250);
			// A Master has no next level to ask for; nor has someone without a trade.
			helper.assertTrue(resolve(helper, "level_up", worker(helper, new BlockPos(9, 2, 4), "Mast", "minecraft:farmer", 5), me) == null, "a Master asked for a level");
			helper.assertTrue(resolve(helper, "level_up", worker(helper, new BlockPos(10, 2, 4), "Nit", "minecraft:nitwit", 1), me) == null, "a nitwit asked for a level");
			Quest quest = accepted(helper, "level_up", dara, me);
			helper.assertTrue(quest.objectives.get(0) instanceof Objectives.LevelUp up && up.level() == 2, "the objective: " + quest.objectives);
			// Another friend joins from the journal; a 2-heart player can't, by the button either.
			ChoiceMenu menu = ChoiceMenu.detached(other, m -> QuestJournal.render(m, level, hall, other, QuestJournal.Tab.PERSONAL));
			menu.press(QuestJournal.PERSONAL_ROW, other);
			command(stranger, "workplace quest accept " + quest.id);
			helper.assertTrue(quest.helpers.keySet().equals(Set.of(me.getUUID(), other.getUUID())), "helpers: " + quest.helpers.keySet());
			helper.assertTrue(lore(menu.icon(QuestJournal.PERSONAL_ROW)).contains("Click to track it"), "a helper's view: " + lore(menu.icon(QuestJournal.PERSONAL_ROW)));

			// Not yet: the round looks and nothing happens.
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(PersonalRequests.of(dara) == quest && quest.progress[0] == 0, "done at level 1");
			// A Bottle o' Enchanting's worth of experience takes a Novice to Apprentice.
			BuilderLevels.addXp(level, dara, io.github.jcondedata.aliveworkplace.story.Gifts.BOTTLE_XP, null);
			helper.assertTrue(dara.getVillagerData().getLevel() == 2, "her level after the experience: " + dara.getVillagerData().getLevel());
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(PersonalRequests.of(dara) == null && PersonalRequests.open(level, hall).isEmpty(), "still open at level 2");
			helper.assertTrue(Friendship.points(dara, me.getUUID()) == 450 && Friendship.points(dara, other.getUUID()) == 570 && Friendship.points(dara, stranger.getUUID()) == 250,
				"friendship: " + Friendship.points(dara, me.getUUID()) + ", " + Friendship.points(dara, other.getUUID()) + ", " + Friendship.points(dara, stranger.getUUID()));
			helper.assertTrue(me.getInventory().countItem(Items.EMERALD) == 6 && other.getInventory().countItem(Items.EMERALD) == 6
				&& stranger.getInventory().countItem(Items.EMERALD) == 0, "emeralds: " + me.getInventory().countItem(Items.EMERALD) + ", "
				+ other.getInventory().countItem(Items.EMERALD) + ", " + stranger.getInventory().countItem(Items.EMERALD));
			helper.assertTrue(friendLines(helper).contains(ME + " helped Dara: Next level before the festival"), "the chronicle: " + friendLines(helper));
			helper.assertTrue(said(dara).equals("Apprentice! I made it, " + ME + ", and I wouldn't have without you. Here, for your trouble."), "she said: " + said(dara));
			// Done, she may ask again another day (the round's own roll may have had her ask already: that offer is put aside).
			PersonalRequests.forget();
			helper.assertTrue(PersonalRequests.mayAsk(level, hall, dara, Chronicle.day(level)), "she can't ask again");
			helper.succeed();
		});
	}

	/** A home of my own: asked by someone with no home or a tier I one; done when they sleep in a bed of their own in a finished tier II home. */
	//$ gametest_ticks_batch AREA '80' '"requestHome"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestHome")
	public void aHomeOfTheirOwn(GameTestHelper helper) {
		village(helper, () -> {
			Leftovers.finished(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hallEntity(helper);
			// Two finished houses (only their size matters to a home): tier I and tier II, a bed in each.
			BuildSiteManager sites = BuildSiteManager.get(level);
			List<BlueprintData.Placement> placed = new ArrayList<>();
			BlockPos[] beds = new BlockPos[3];
			for (int tier = 1; tier <= 2; tier++) {
				ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", tier == 1 ? "request_house" : "request_house_2");
				BlockPos corner = new BlockPos(2 + (tier - 1) * 8, 8, 2);
				BlockPos origin = helper.absolutePos(corner);
				level.getStructureManager().getOrCreate(id).fillFromWorld(level, origin.above(60), new Vec3i(5, 4, 5), false, Blocks.AIR);
				BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
				sites.recordFinished(id, placement, UUID.randomUUID());
				placed.add(placement);
				helper.setBlock(corner.offset(2, 0, 2), Blocks.OAK_PLANKS);
				helper.setBlock(corner.offset(2, 1, 2), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD));
				beds[tier] = origin.offset(2, 1, 2);
			}
			Leftovers.after(helper, () -> placed.forEach(sites::forgetFinished));
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 5);
			ServerPlayer me = friend(helper, new BlockPos(5, 2, 6), dara, 300);
			// Someone already in a tier II home has nothing to ask.
			Villager odo = worker(helper, new BlockPos(7, 2, 4), "Odo", "minecraft:farmer", 5);
			Friendship.add(odo, me, 300);
			odo.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), beds[2]));
			helper.assertTrue(resolve(helper, "home", odo, me) == null, "asked for a home from a tier II home");
			odo.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), beds[1]));
			helper.assertTrue(resolve(helper, "home", odo, me) != null, "a tier I home has nothing to ask for");

			Quest quest = accepted(helper, "home", dara, me);
			helper.assertTrue(!quest.festival && PersonalRequests.daysLeft(level, quest) == 12, "days: " + PersonalRequests.daysLeft(level, quest));
			helper.assertTrue(PersonalRequests.wants(level, quest).getString()
				.equals("Dara wants a home of their own (a bed in a finished house of tier II or better) within 12 days"), PersonalRequests.wants(level, quest).getString());
			// A bed in a tier I home, asleep: not what she asked for.
			dara.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), beds[1]));
			dara.startSleeping(beds[1]);
			PersonalRequests.look(level, hall, entity, false);
			helper.assertTrue(dara.isSleeping() && PersonalRequests.of(dara) == quest, "done in a tier I home");
			// A bed in the tier II home, but awake: not yet.
			dara.stopSleeping();
			dara.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), beds[2]));
			PersonalRequests.look(level, hall, entity, false);
			helper.assertTrue(PersonalRequests.of(dara) == quest, "done before she slept there");
			// Asleep in it: done.
			dara.startSleeping(beds[2]);
			PersonalRequests.look(level, hall, entity, false);
			helper.assertTrue(PersonalRequests.of(dara) == null && Friendship.points(dara, me.getUUID()) == 450 && me.getInventory().countItem(Items.EMERALD) == 8,
				"after her first night: " + PersonalRequests.of(dara) + ", " + Friendship.points(dara, me.getUUID()) + " points, " + me.getInventory().countItem(Items.EMERALD) + " emeralds");
			helper.assertTrue(friendLines(helper).contains(ME + " helped Dara: A home of my own"), "the chronicle: " + friendLines(helper));
			helper.succeed();
		});
	}

	/** A taste of home: what they miss goes by their villager type; handed in from the journal, it goes into their own hands. */
	//$ gametest_ticks_batch AREA '80' '"requestTaste"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestTaste")
	public void aTasteOfHomeByVillagerType(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Map<VillagerType, ItemStack> miss = new LinkedHashMap<>();
			miss.put(VillagerType.PLAINS, new ItemStack(Items.PUMPKIN_PIE, 1));
			miss.put(VillagerType.DESERT, new ItemStack(Items.RABBIT_STEW, 1));
			miss.put(VillagerType.SAVANNA, new ItemStack(Items.COOKED_MUTTON, 4));
			miss.put(VillagerType.TAIGA, new ItemStack(Items.SWEET_BERRIES, 16));
			miss.put(VillagerType.SNOW, new ItemStack(Items.BAKED_POTATO, 4));
			miss.put(VillagerType.SWAMP, new ItemStack(Items.MUSHROOM_STEW, 1));
			miss.put(VillagerType.JUNGLE, new ItemStack(Items.COOKIE, 8));
			Villager odo = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Odo");
			ServerPlayer me = friend(helper, new BlockPos(5, 2, 6), odo, 300);
			for (Map.Entry<VillagerType, ItemStack> e : miss.entrySet()) {
				odo.setVillagerData(odo.getVillagerData().setType(e.getKey()));
				Quest quest = resolve(helper, "taste_of_home", odo, me);
				helper.assertTrue(quest != null && quest.objectives.get(0) instanceof Objectives.Bring bring && bring.count() == e.getValue().getCount()
					&& Objectives.matches(bring.item(), e.getValue()) && bring.to().equals("giver"), e.getKey() + " misses " + (quest == null ? null : quest.objectives));
			}
			// A taiga villager, from the ask to the thanks.
			odo.setVillagerData(odo.getVillagerData().setType(VillagerType.TAIGA));
			Quest quest = accepted(helper, "taste_of_home", odo, me);
			helper.assertTrue(PersonalRequests.wants(level, quest).getString().equals("Odo wants a taste of home: 16 Sweet Berries within 3 days"),
				PersonalRequests.wants(level, quest).getString());
			ChoiceMenu menu = ChoiceMenu.detached(me, m -> QuestJournal.render(m, level, hall, me, QuestJournal.Tab.PERSONAL));
			helper.assertTrue(menu.icon(QuestJournal.PERSONAL_ROW).is(Items.SWEET_BERRIES) && lore(menu.icon(QuestJournal.PERSONAL_ROW)).contains("You have none on you"),
				"with nothing to give: " + lore(menu.icon(QuestJournal.PERSONAL_ROW)));
			menu.press(QuestJournal.PERSONAL_ROW, me);
			helper.assertTrue(quest.progress[0] == 0, "handed in nothing");
			// Ten now, the rest later; a pie isn't what a taiga villager misses.
			me.getInventory().add(new ItemStack(Items.SWEET_BERRIES, 10));
			me.getInventory().add(new ItemStack(Items.PUMPKIN_PIE, 1));
			menu.press(QuestJournal.PERSONAL_ROW, me);
			helper.assertTrue(quest.progress[0] == 10 && me.getInventory().countItem(Items.SWEET_BERRIES) == 0 && me.getInventory().countItem(Items.PUMPKIN_PIE) == 1,
				"after ten: " + quest.progress[0]);
			helper.assertTrue(odo.getInventory().countItem(Items.SWEET_BERRIES) == 10, "he holds " + odo.getInventory().countItem(Items.SWEET_BERRIES) + " of the ten");
			me.getInventory().add(new ItemStack(Items.SWEET_BERRIES, 20));
			menu.press(QuestJournal.PERSONAL_ROW, me);
			helper.assertTrue(PersonalRequests.of(odo) == null && me.getInventory().countItem(Items.SWEET_BERRIES) == 14 && me.getInventory().countItem(Items.EMERALD) == 4,
				"after the rest: " + PersonalRequests.of(odo) + ", berries " + me.getInventory().countItem(Items.SWEET_BERRIES) + ", emeralds " + me.getInventory().countItem(Items.EMERALD));
			// The friendship of the request, and of the hand-in (a favour of its own, once a day).
			helper.assertTrue(Friendship.points(odo, me.getUUID()) == 300 + Friendship.Favour.REQUEST.points + Friendship.Favour.HAND_IN.points,
				"friendship: " + Friendship.points(odo, me.getUUID()));
			helper.assertTrue(said(odo).equals("Oh, that's it. That's exactly it. Thank you, " + ME + ". It tastes like being small again."), "he said: " + said(odo));
			helper.assertTrue(friendLines(helper).contains(ME + " helped Odo: A taste of home"), "the chronicle: " + friendLines(helper));
			helper.succeed();
		});
	}

	/** The tools of my trade: what they need goes by their job, and it goes into the chest by their workstation. */
	//$ gametest_ticks_batch AREA '80' '"requestTools"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestTools")
	public void theToolsOfTheirTradeByJob(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			ItemStack luckyRod = new ItemStack(Items.FISHING_ROD);
			luckyRod.enchant(enchantments.getOrThrow(Enchantments.LUCK_OF_THE_SEA), 1);
			ItemStack fireResistance = PotionContents.createItemStack(Items.POTION, Potions.LONG_FIRE_RESISTANCE);
			Map<String, ItemStack> tools = new LinkedHashMap<>();
			tools.put("aliveworkplace:miner", new ItemStack(Items.DIAMOND_PICKAXE));
			tools.put("aliveworkplace:lumberjack", new ItemStack(Items.DIAMOND_AXE));
			tools.put("minecraft:farmer", new ItemStack(Items.DIAMOND_HOE));
			tools.put("minecraft:fisherman", luckyRod);
			tools.put("aliveworkplace:guard", new ItemStack(Items.DIAMOND_SWORD));
			tools.put("minecraft:cartographer", new ItemStack(Items.SPYGLASS));
			tools.put("aliveworkplace:netherworker", fireResistance);
			tools.put("aliveworkplace:sifter", new ItemStack(Items.DIAMOND_SHOVEL));
			Villager finn = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Finn");
			ServerPlayer me = friend(helper, new BlockPos(5, 2, 6), finn, 300);
			for (Map.Entry<String, ItemStack> e : tools.entrySet()) {
				finn.setVillagerData(finn.getVillagerData().setProfession(BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(e.getKey()))));
				Quest quest = resolve(helper, "tools_of_my_trade", finn, me);
				helper.assertTrue(quest != null && quest.objectives.get(0) instanceof Objectives.Bring bring && bring.count() == 1 && Objectives.matches(bring.item(), e.getValue()),
					e.getKey() + " needs " + (quest == null ? null : quest.objectives));
			}
			// A job with no tool on the list has nothing to ask.
			finn.setVillagerData(finn.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
			helper.assertTrue(resolve(helper, "tools_of_my_trade", finn, me) == null, "a librarian asked for tools");
			// What "with Luck of the Sea" and "of Fire Resistance" take.
			String rod = "minecraft:fishing_rod[enchantment=minecraft:luck_of_the_sea]";
			String potion = "minecraft:potion[effect=minecraft:fire_resistance]";
			helper.assertTrue(Objectives.matches(rod, luckyRod) && !Objectives.matches(rod, new ItemStack(Items.FISHING_ROD)), "the rod");
			helper.assertTrue(Objectives.matches(potion, fireResistance) && Objectives.matches(potion, PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE))
				&& !Objectives.matches(potion, PotionContents.createItemStack(Items.POTION, Potions.WATER))
				&& !Objectives.matches(potion, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.FIRE_RESISTANCE)), "the potion");
			helper.assertTrue(Objectives.name(rod).getString().equals("Fishing Rod with Luck of the Sea") && Objectives.name(potion).getString().equals("Potion of Fire Resistance"),
				Objectives.name(rod).getString() + " / " + Objectives.name(potion).getString());

			// A fisherman with a workstation and a chest beside it, from the ask to the thanks.
			finn.setVillagerData(finn.getVillagerData().setProfession(VillagerProfession.FISHERMAN));
			BlockPos station = new BlockPos(3, 2, 3);
			helper.setBlock(station, Blocks.FLETCHING_TABLE); // (stands for his workstation: a barrel would take the rod itself)
			helper.setBlock(station.east(), Blocks.CHEST);
			finn.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(station)));
			Quest quest = accepted(helper, "tools_of_my_trade", finn, me);
			helper.assertTrue(quest.objectives.get(0) instanceof Objectives.Bring bring && bring.station().equals(Optional.of(helper.absolutePos(station))), "where it goes: " + quest.objectives);
			helper.assertTrue(PersonalRequests.wants(level, quest).getString().equals("Finn wants the tools of the trade: 1 Fishing Rod with Luck of the Sea within 5 days"),
				PersonalRequests.wants(level, quest).getString());
			ChoiceMenu menu = ChoiceMenu.detached(me, m -> QuestJournal.render(m, level, hall, me, QuestJournal.Tab.PERSONAL));
			helper.assertTrue(lore(menu.icon(QuestJournal.PERSONAL_ROW)).contains("Bring 1 Fishing Rod with Luck of the Sea (0/1)"), "the journal: " + lore(menu.icon(QuestJournal.PERSONAL_ROW)));
			me.getInventory().add(new ItemStack(Items.FISHING_ROD));
			menu.press(QuestJournal.PERSONAL_ROW, me);
			helper.assertTrue(PersonalRequests.of(finn) == quest && me.getInventory().countItem(Items.FISHING_ROD) == 1, "a plain rod was taken");
			me.getInventory().add(luckyRod.copy());
			menu.press(QuestJournal.PERSONAL_ROW, me);
			helper.assertTrue(PersonalRequests.of(finn) == null && me.getInventory().countItem(Items.FISHING_ROD) == 1 && me.getInventory().countItem(Items.EMERALD) == 5,
				"after the rod: " + PersonalRequests.of(finn) + ", emeralds " + me.getInventory().countItem(Items.EMERALD));
			ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(station.east()));
			boolean inChest = false;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				inChest |= Objectives.matches(rod, chest.getItem(i));
			}
			helper.assertTrue(inChest, "the rod isn't in the chest by his workstation");
			helper.assertTrue(Friendship.points(finn, me.getUUID()) == 300 + Friendship.Favour.REQUEST.points + Friendship.Favour.HAND_IN.points,
				"friendship: " + Friendship.points(finn, me.getUUID()));
			helper.assertTrue(friendLines(helper).contains(ME + " helped Finn: The tools of my trade"), "the chronicle: " + friendLines(helper));
			helper.succeed();
		});
	}

	/** Without Cobblemon the two Pokémon requests are never offered, to a Trainer or anyone; their sentences still read right. */
	//$ gametest_ticks_batch AREA '80' '"requestNoPokemon"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestNoPokemon")
	public void thePokemonRequestsNeedCobblemon(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager tara = worker(helper, new BlockPos(5, 2, 4), "Tara", "aliveworkplace:trainer", 2);
			tara.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(4, 2, 4))));
			Villager dara = worker(helper, new BlockPos(6, 2, 4), "Dara", "minecraft:farmer", 2);
			dara.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(7, 2, 4))));
			ServerPlayer me = friend(helper, new BlockPos(5, 2, 6), tara, 300);
			Friendship.add(dara, me, 300);
			helper.assertTrue(QuestFiles.get(file("help_me_train")).isPresent() && QuestFiles.get(file("pokemon_friend")).isPresent(), "the two files aren't loaded");
			helper.assertTrue(resolve(helper, "help_me_train", tara, me) == null && resolve(helper, "pokemon_friend", dara, me) == null
				&& resolve(helper, "pokemon_friend", tara, me) == null, "a Pokémon request without Cobblemon");
			// The sentences, with the objectives as Cobblemon would resolve them.
			Quest train = new Quest(UUID.randomUUID(), file("help_me_train"), "villager", Optional.of(Component.translatable("request.aliveworkplace.help_me_train.title")), "Tara",
				level.getGameTime(), level.getGameTime() + 7 * 24000, List.of(new Objectives.BeatGiver(3)), new int[1], List.of());
			train.villager = tara.getUUID();
			train.text = "request.aliveworkplace.help_me_train";
			helper.assertTrue(PersonalRequests.wants(level, train).getString().equals("Tara wants a real rival: beat them in battle on 3 different days within 7 days"),
				PersonalRequests.wants(level, train).getString());
			helper.assertTrue(train.objectives.get(0).line().getString().equals("Beat them in battle on 3 different days") && train.title().getString().equals("Help me train"),
				train.objectives.get(0).line().getString());
			helper.assertTrue(PersonalRequests.offerLine(level, train).getString()
				.equals("Tara wants a real rival: beat them in battle on 3 different days within 7 days. For their friendship. [I'll help] [Not now]"),
				PersonalRequests.offerLine(level, train).getString());
			Quest partner = new Quest(UUID.randomUUID(), file("pokemon_friend"), "villager", Optional.of(Component.translatable("request.aliveworkplace.pokemon_friend.title")), "Dara",
				level.getGameTime(), level.getGameTime() + 24000, List.of(new Objectives.PartnerPokemon(16, List.of("grass", "ground", "water"))), new int[1], List.of());
			partner.villager = dara.getUUID();
			partner.text = "request.aliveworkplace.pokemon_friend";
			helper.assertTrue(PersonalRequests.wants(level, partner).getString()
				.equals("Dara wants a Pokémon friend at work (Grass, Ground or Water type, pastured within 16 blocks of their workstation at dawn) by tomorrow"),
				PersonalRequests.wants(level, partner).getString());
			helper.assertTrue(partner.objectives.get(0).line().getString().equals("A Pokémon of the Grass, Ground or Water type pastured by their workstation at dawn"),
				partner.objectives.get(0).line().getString());
			// Both objectives survive a save as they were asked.
			for (Quest q : List.of(train, partner)) {
				Objectives.Objective again = Objectives.parse(q.objectives.get(0).json());
				helper.assertTrue(again.equals(q.objectives.get(0)), "saved and read: " + again);
			}
			helper.succeed();
		});
	}

	// --- Deadlines ------------------------------------------------------------------------------------------------

	/** A missed deadline: 30 friendship off every helper, glum for a day, and no asking for three days. */
	//$ gametest_ticks_batch AREA '80' '"requestMissed"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestMissed")
	public void aMissedDeadlineCostsThirtyAndThreeQuietDays(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hallEntity(helper);
			long today = Chronicle.day(level);
			Moods.ENABLED = true;
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			ServerPlayer me = friend(helper, new BlockPos(5, 2, 6), dara, 300);
			ServerPlayer other = friend(helper, new BlockPos(6, 2, 6), dara, 500);
			ServerPlayer bystander = friend(helper, new BlockPos(7, 2, 6), dara, 400);
			Quest quest = accepted(helper, "taste_of_home", dara, me);
			helper.assertTrue(PersonalRequests.join(other, level, quest), "a 5-heart friend couldn't join");
			helper.assertTrue(quest.due == quest.posted + 3 * 24000L, "three days: " + (quest.due - quest.posted));
			helper.assertTrue(Moods.work(level, dara).bad().stream().noneMatch(r -> r.getString().startsWith("Let down")), "glum before anything was missed");

			helper.assertTrue(PersonalRequests.overdue(level, hall, quest.due - 1) == 0 && PersonalRequests.of(dara) == quest, "missed a tick early");
			helper.assertTrue(PersonalRequests.overdue(level, hall, quest.due) == 1 && PersonalRequests.of(dara) == null, "not missed when due");
			helper.assertTrue(Friendship.points(dara, me.getUUID()) == 270 && Friendship.points(dara, other.getUUID()) == 470 && Friendship.points(dara, bystander.getUUID()) == 400,
				"friendship after the miss: " + Friendship.points(dara, me.getUUID()) + ", " + Friendship.points(dara, other.getUUID()) + ", " + Friendship.points(dara, bystander.getUUID()));
			helper.assertTrue(me.getInventory().countItem(Items.EMERALD) == 0 && friendLines(helper).isEmpty(), "a missed request paid or was written down");
			// Glum for a day: a mood reason, worth ten points.
			Moods.Mood mood = Moods.work(level, dara);
			helper.assertTrue(mood.bad().stream().anyMatch(r -> r.getString().equals("Let down: nobody helped in time")), "her mood: " + mood.bad());
			PersonalRequests.LetDown state = ModAttachments.REQUEST_LET_DOWN.get(dara);
			helper.assertTrue(state != null && state.glumUntil() == level.getGameTime() + 24000 && state.quietDay() == today + 3, "what the miss left: " + state);
			ModAttachments.REQUEST_LET_DOWN.set(dara, new PersonalRequests.LetDown(state.quietDay(), level.getGameTime()));
			Moods.Mood after = Moods.work(level, dara);
			helper.assertTrue(after.bad().stream().noneMatch(r -> r.getString().startsWith("Let down")) && after.score() == mood.score() + PersonalRequests.GLUM,
				"a day on: " + after.bad() + ", " + mood.score() + " -> " + after.score());
			// She asks nobody for three days (the bystander still has her hearts), then again.
			for (long day = today; day <= today + 3; day++) {
				helper.assertTrue(!PersonalRequests.mayAsk(level, hall, dara, day) && PersonalRequests.pick(level, hall, entity, RandomSource.create(day), day) == null,
					"she asked on day " + (day - today) + " after the miss");
			}
			boolean mayAgain = PersonalRequests.mayAsk(level, hall, dara, today + 4);
			PersonalRequests.Offer again = PersonalRequests.pick(level, hall, entity, RandomSource.create(4), today + 4);
			helper.assertTrue(mayAgain && again != null && again.villager.equals(dara.getUUID())
				&& again.player.equals(other.getUUID()), "on the fourth day: " + (again == null ? "nobody asked" : "asked " + again.player) + ", may ask " + mayAgain);
			// The hall's round is what misses it in the game: a request due now comes down there.
			PersonalRequests.forget();
			Villager odo = worker(helper, new BlockPos(8, 2, 4), "Odo", "minecraft:farmer", 1);
			Friendship.add(odo, me, 300 - Friendship.points(odo, me.getUUID()));
			Quest odos = resolve(helper, "taste_of_home", odo, me);
			Quest due = new Quest(odos.id, odos.file, odos.giver, odos.name, odos.poster, level.getGameTime() - 100, level.getGameTime(), odos.objectives, new int[1], odos.rewards);
			due.villager = odo.getUUID();
			due.text = odos.text;
			due.helpers.put(me.getUUID(), 0);
			Stories.post(level, hall, due);
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(PersonalRequests.of(odo) == null && Friendship.points(odo, me.getUUID()) == 270 && PersonalRequests.quiet(odo, today + 3)
				&& !PersonalRequests.quiet(odo, today + 4), "the round's miss: " + Friendship.points(odo, me.getUUID()));
			helper.succeed();
		});
	}

	/** "Before the next festival" is the morning of the village's next festival, at least two days off. */
	//$ gametest_ticks_batch AREA '80' '"requestFestival"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestFestival")
	public void beforeTheNextFestival(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hallEntity(helper);
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 3);
			ServerPlayer me = friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Quest quest = resolve(helper, "level_up", dara, me);
			long today = Chronicle.day(level);
			long festival = io.github.jcondedata.aliveworkplace.hall.Festivals.nextDay(level, hall, entity);
			if (festival - today < PersonalRequests.MIN_FESTIVAL_DAYS) {
				festival += io.github.jcondedata.aliveworkplace.hall.Festivals.every(entity);
			}
			long dueDay = (level.getDayTime() + (quest.due - level.getGameTime())) / 24000 + 1;
			helper.assertTrue(quest.festival && dueDay == festival && (level.getDayTime() + (quest.due - level.getGameTime())) % 24000 == 0,
				"due on day " + dueDay + ", the festival is on day " + festival);
			helper.assertTrue(festival - today >= 2 && PersonalRequests.daysLeft(level, quest) >= 1, "a festival " + (festival - today) + " days off");
			helper.assertTrue(PersonalRequests.wants(level, quest).getString().equals("Dara wants to make Expert before the next festival"), PersonalRequests.wants(level, quest).getString());
			// With festivals off it's a number of days.
			boolean festivals = io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED;
			io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED = false;
			try {
				Quest plain = resolve(helper, "level_up", dara, me);
				helper.assertTrue(!plain.festival && plain.due == plain.posted + 6 * 24000L
					&& PersonalRequests.wants(level, plain).getString().equals("Dara wants to make Expert within 6 days"), PersonalRequests.wants(level, plain).getString());
			} finally {
				io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED = festivals;
			}
			helper.succeed();
		});
	}

	// --- Conditions, saving, the switch ---------------------------------------------------------------------------

	private static Condition condition(String json) {
		return Conditions.parse(JsonParser.parseString(json).getAsJsonObject());
	}

	/** The seven conditions on the giver: each reads its fact, none holds for the hall's board, and bad ones are refused. */
	//$ gametest_ticks_batch AREA '80' '"requestConditions"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestConditions")
	public void everyConditionReadsTheGiver(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "aliveworkplace:miner", 3);
			dara.setVillagerData(dara.getVillagerData().setType(VillagerType.DESERT));
			ServerPlayer me = friend(helper, new BlockPos(5, 2, 6), dara, 400);
			Map<String, Boolean> cases = new LinkedHashMap<>();
			cases.put("{\"type\": \"hearts_at_least\", \"hearts\": 4}", true);
			cases.put("{\"type\": \"hearts_at_least\", \"hearts\": 5}", false);
			cases.put("{\"type\": \"job\", \"jobs\": [\"minecraft:farmer\", \"aliveworkplace:miner\"]}", true);
			cases.put("{\"type\": \"job\", \"jobs\": [\"minecraft:farmer\"]}", false);
			cases.put("{\"type\": \"job_family\", \"family\": \"mining\"}", true);
			cases.put("{\"type\": \"job_family\", \"family\": \"land\"}", false);
			cases.put("{\"type\": \"level_below\", \"level\": 4}", true);
			cases.put("{\"type\": \"level_below\", \"level\": 3}", false);
			cases.put("{\"type\": \"villager_type\", \"types\": [\"minecraft:desert\"]}", true);
			cases.put("{\"type\": \"villager_type\", \"types\": [\"minecraft:plains\", \"minecraft:snow\"]}", false);
			cases.put("{\"type\": \"home_tier_below\", \"tier\": 1}", true);
			cases.put("{\"type\": \"no_bed\"}", true);
			cases.put("{\"type\": \"not\", \"condition\": {\"type\": \"no_bed\"}}", false);
			cases.put("{\"type\": \"not\", \"condition\": {\"type\": \"job_family\", \"family\": \"land\"}}", true);
			for (Map.Entry<String, Boolean> e : cases.entrySet()) {
				Condition c = condition(e.getKey());
				helper.assertTrue(c.met(level, hall, dara, me) == e.getValue(), e.getKey() + " for a level 3 desert miner with 4 hearts and no bed: " + !e.getValue());
				// Without a giver (the hall's board, a Legend's file) none of them holds, nor its opposite.
				helper.assertTrue(!c.met(level, hall) && !c.met(level, hall, null, null), e.getKey() + " holds with nobody asking");
				helper.assertTrue(!c.progress(level, hall).line().getString().contains("rule.aliveworkplace"), "no text for " + e.getKey() + ": " + c.progress(level, hall).line().getString());
			}
			// With a bed, no_bed stops holding; a nitwit's level isn't "below".
			dara.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(3, 2, 3))));
			helper.assertTrue(!condition("{\"type\": \"no_bed\"}").met(level, hall, dara, me), "no_bed with a bed");
			Villager nit = worker(helper, new BlockPos(7, 2, 4), "Nit", "minecraft:nitwit", 1);
			helper.assertTrue(!condition("{\"type\": \"level_below\", \"level\": 5}").met(level, hall, nit, me), "a nitwit's level is below Master");
			helper.assertTrue(!condition("{\"type\": \"hearts_at_least\", \"hearts\": 1}").met(level, hall, dara, null), "hearts with no player");
			for (String bad : List.of("{\"type\": \"hearts_at_least\"}", "{\"type\": \"hearts_at_least\", \"hearts\": 11}", "{\"type\": \"job\"}", "{\"type\": \"job\", \"jobs\": []}",
				"{\"type\": \"job_family\", \"family\": \"wizards\"}", "{\"type\": \"level_below\", \"level\": 1}", "{\"type\": \"villager_type\", \"types\": [\"not an id\"]}",
				"{\"type\": \"home_tier_below\"}")) {
				try {
					condition(bad);
					helper.fail("read " + bad);
				} catch (IllegalArgumentException expected) {
					// refused, naming the field
				}
			}
			// The files as shipped: six requests, each with its text; one without text or with a bad deadline is refused.
			List<String> personal = QuestFiles.all().stream().filter(f -> f.giver().equals("villager")).map(f -> f.id().getPath()).sorted().toList();
			helper.assertTrue(personal.equals(List.of("personal/help_me_train", "personal/home", "personal/level_up", "personal/pokemon_friend", "personal/taste_of_home",
				"personal/tools_of_my_trade")), "the request files: " + personal);
			for (QuestFiles.QuestFile f : QuestFiles.all()) {
				if (f.giver().equals("villager")) {
					for (String part : List.of("title", "ask", "wants", "thanks")) {
						String key = f.text() + "." + part;
						helper.assertTrue(!Component.translatable(key, "Dara", "Jesse", "1 Pumpkin Pie").getString().equals(key), "no text " + key);
					}
					helper.assertTrue(f.conditions().stream().anyMatch(c -> c.type().equals("hearts_at_least")), f.id() + " asks for no hearts");
				}
			}
			JsonObject noText = JsonParser.parseString("{\"giver\": \"villager\", \"objectives\": [{\"type\": \"level_up\"}]}").getAsJsonObject();
			JsonObject badDeadline = JsonParser.parseString("{\"giver\": \"villager\", \"text\": \"x\", \"deadline\": \"soon\", \"objectives\": [{\"type\": \"level_up\"}]}").getAsJsonObject();
			JsonObject badBy = JsonParser.parseString("{\"giver\": \"villager\", \"text\": \"x\", \"objectives\": [{\"type\": \"bring\", \"by\": \"mood\", \"options\": {}}]}").getAsJsonObject();
			JsonObject badItem = JsonParser.parseString("{\"giver\": \"hall\", \"objectives\": [{\"type\": \"bring\", \"item\": \"minecraft:potion[colour=red]\", \"count\": 1}]}").getAsJsonObject();
			for (JsonObject bad : List.of(noText, badDeadline, badBy, badItem)) {
				try {
					QuestFiles.read(file("broken"), bad);
					helper.fail("read " + bad);
				} catch (IllegalArgumentException expected) {
					// refused
				}
			}
			helper.succeed();
		});
	}

	/** A request, its helpers and what a miss left survive a save and reload; saves from before requests read as before. */
	//$ gametest_ticks_batch AREA '80' '"requestSaved"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestSaved")
	public void aSaveAndReloadKeepsARequest(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hallEntity(helper);
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Quest quest = accepted(helper, "level_up", dara, me);
			quest.mark = 17;
			VillageQuests.tick(level, hall, entity); // the round's roll and dawn look are saved too
			CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
			Stories.Data loaded = Stories.Data.load(saved, level.registryAccess());
			CompoundTag again = loaded.save(new CompoundTag(), level.registryAccess());
			helper.assertTrue(saved.equals(again), "the stories changed on reload:\n" + saved + "\n" + again);
			String text = saved.toString();
			helper.assertTrue(text.contains("request_day") && text.contains("request_dawn") && text.contains("request.aliveworkplace.level_up") && text.contains("festival"),
				"the save holds no request: " + text);
			// Read back from the level's own data after a reload of the file's content: still hers, still mine to help with.
			Quest read = null;
			for (net.minecraft.nbt.Tag h : saved.getList("halls", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
				for (net.minecraft.nbt.Tag q : ((CompoundTag) h).getList("quests", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
					CompoundTag tag = (CompoundTag) q;
					if (io.github.jcondedata.aliveworkplace.mc.Nbt.getUuid(tag, "id").equals(quest.id)) {
						helper.assertTrue(io.github.jcondedata.aliveworkplace.mc.Nbt.getUuid(tag, "villager").equals(dara.getUUID()) && tag.getBoolean("festival") && tag.getLong("mark") == 17
							&& tag.getCompound("helpers").contains(me.getUUID().toString()), "the saved request: " + tag);
						// A quest saved before 1.5's requests has none of the new fields: it reads as an ordinary quest.
						CompoundTag old = tag.copy();
						for (String key : List.of("villager", "text", "festival", "mark")) {
							old.remove(key);
						}
						CompoundTag wrap = new CompoundTag();
						net.minecraft.nbt.ListTag quests = new net.minecraft.nbt.ListTag();
						quests.add(old);
						CompoundTag oldHall = new CompoundTag();
						oldHall.putLong("hall", hall.asLong());
						oldHall.put("quests", quests);
						net.minecraft.nbt.ListTag halls = new net.minecraft.nbt.ListTag();
						halls.add(oldHall);
						wrap.put("halls", halls);
						CompoundTag oldAgain = Stories.Data.load(wrap, level.registryAccess()).save(new CompoundTag(), level.registryAccess());
						helper.assertTrue(!oldAgain.toString().contains("request_day") && !oldAgain.toString().contains("villager:") && !oldAgain.toString().contains("festival"),
							"an old save grew request fields: " + oldAgain);
						read = quest;
					}
				}
			}
			helper.assertTrue(read != null, "the request isn't in the save");
			// What a miss left on her is saved with her; a villager saved before has none.
			ModAttachments.REQUEST_LET_DOWN.set(dara, new PersonalRequests.LetDown(41, 9000));
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(dara.saveWithoutId(new CompoundTag()));
			helper.assertTrue(new PersonalRequests.LetDown(41, 9000).equals(ModAttachments.REQUEST_LET_DOWN.get(copy)) && PersonalRequests.quiet(copy, 41)
				&& !PersonalRequests.quiet(copy, 42), "reloaded: " + ModAttachments.REQUEST_LET_DOWN.get(copy));
			copy.discard();
			Villager fresh = EntityType.VILLAGER.create(level);
			helper.assertTrue(ModAttachments.REQUEST_LET_DOWN.get(fresh) == null && !PersonalRequests.quiet(fresh, 0) && !PersonalRequests.glum(level, fresh), "a new villager is let down");
			fresh.discard();
			// A restart forgets an unanswered offer (they may ask again); the accepted request is still there.
			Villager odo = worker(helper, new BlockPos(7, 2, 4), "Odo", "minecraft:farmer", 1);
			Friendship.add(odo, me, 300);
			PersonalRequests.propose(level, hall, odo, me, resolve(helper, "level_up", odo, me), Chronicle.day(level));
			PersonalRequests.forget();
			helper.assertTrue(PersonalRequests.offer(odo) == null && PersonalRequests.mayAsk(level, hall, odo, Chronicle.day(level)) && PersonalRequests.of(dara) == quest,
				"after a restart");
			helper.succeed();
		});
	}

	/** Config {@code personalRequests} off: nobody asks, an offer lapses and can't be taken; a request already accepted can still be finished. */
	//$ gametest_ticks_batch AREA '80' '"requestOff"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "requestOff")
	public void theSwitchOff(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hallEntity(helper);
			long today = Chronicle.day(level);
			Villager dara = worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			Villager odo = worker(helper, new BlockPos(7, 2, 4), "Odo", "minecraft:farmer", 1);
			Villager finn = worker(helper, new BlockPos(9, 2, 4), "Finn", "minecraft:farmer", 1);
			ServerPlayer me = friend(helper, new BlockPos(5, 2, 6), dara, 300);
			Friendship.add(odo, me, 300);
			Friendship.add(finn, me, 300);
			Quest open = accepted(helper, "taste_of_home", dara, me);
			Quest offered = resolve(helper, "level_up", odo, me);
			PersonalRequests.Offer offer = PersonalRequests.propose(level, hall, odo, me, offered, today);

			PersonalRequests.ENABLED = false;
			helper.assertTrue(!PersonalRequests.active(), "active with the switch off");
			for (int seed = 0; seed < 10; seed++) {
				helper.assertTrue(PersonalRequests.pick(level, hall, entity, RandomSource.create(seed), today) == null, "someone asked with the switch off");
			}
			PersonalRequests.tick(level, level.getGameTime() + PersonalRequests.WALK_UP);
			helper.assertTrue(!offer.asked && PersonalRequests.offer(odo) == null && said(odo).isEmpty(), "an offer stood with the switch off: " + said(odo));
			command(me, "workplace quest accept " + offered.id);
			helper.assertTrue(PersonalRequests.of(odo) == null, "a lapsed offer was accepted");
			// The request already accepted is finished as ever.
			me.getInventory().add(new ItemStack(Items.PUMPKIN_PIE));
			ChoiceMenu menu = ChoiceMenu.detached(me, m -> QuestJournal.render(m, level, hall, me, QuestJournal.Tab.PERSONAL));
			menu.press(QuestJournal.PERSONAL_ROW, me);
			helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) == 4 && Friendship.points(dara, me.getUUID()) > 300,
				"finishing with the switch off: " + open + ", " + Friendship.points(dara, me.getUUID()));
			helper.assertTrue(lore(menu.icon(22)).equals(List.of("A villager with 3 hearts or more may walk up and ask you for help when you're near"))
				&& menu.icon(22).getHoverName().getString().equals("Nobody is waiting on a favour"), "the empty tab: " + menu.icon(22).getHoverName().getString());
			// With friendship off nobody asks either (requests go by hearts); on again, they do.
			PersonalRequests.ENABLED = true;
			Friendship.ENABLED = false;
			helper.assertTrue(PersonalRequests.pick(level, hall, entity, RandomSource.create(3), today) == null, "someone asked with friendship off");
			Friendship.ENABLED = true;
			helper.assertTrue(PersonalRequests.pick(level, hall, entity, RandomSource.create(3), today) != null, "nobody asks with everything on again");
			helper.succeed();
		});
	}
}
