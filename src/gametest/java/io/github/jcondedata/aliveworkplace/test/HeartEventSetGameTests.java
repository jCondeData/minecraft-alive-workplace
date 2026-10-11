package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.HeartEventGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.HeartEventGameTests.HALL;
import static io.github.jcondedata.aliveworkplace.test.HeartEventGameTests.ME;
import static io.github.jcondedata.aliveworkplace.test.HeartEventGameTests.friendLines;
import static io.github.jcondedata.aliveworkplace.test.HeartEventGameTests.later;
import static io.github.jcondedata.aliveworkplace.test.HeartEventGameTests.listen;
import static io.github.jcondedata.aliveworkplace.test.HeartEventGameTests.player;
import static io.github.jcondedata.aliveworkplace.test.HeartEventGameTests.told;
import static io.github.jcondedata.aliveworkplace.test.HeartEventGameTests.village;
import static io.github.jcondedata.aliveworkplace.test.HeartEventGameTests.villager;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.HeartEvents;
import io.github.jcondedata.aliveworkplace.story.JobFamilies;
import io.github.jcondedata.aliveworkplace.story.Keepsakes;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * The full set of heart events (ROADMAP 31.8): every variant of <b>My work</b> (4 hearts, one per job family),
 * <b>What keeps me up at night</b> (6), <b>The people I love</b> (8) and <b>What I dream of</b> (10) is picked for
 * its facts and told, with the sentences a player reads; and the 10-heart keepsake of each family comes once per
 * player, through a save, a full inventory and the switch off. Set-up and listening are {@link HeartEventGameTests}'.
 */
public class HeartEventSetGameTests implements FabricGameTest {
	private static final String NS = "aliveworkplace:";
	/** One event of each heart level, to mark the levels below the one a test is about as told. */
	private static final List<String> BELOW = List.of(NS + "before_hall", NS + "work_none", NS + "night_you", NS + "love_village");

	// --- Setting up -----------------------------------------------------------------------------------------------

	private static VillagerProfession job(GameTestHelper helper, String id) {
		ResourceLocation key = ResourceLocation.parse(id);
		helper.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.containsKey(key), "no profession " + id);
		return BuiltInRegistries.VILLAGER_PROFESSION.get(key);
	}

	/** A named villager with a job and its level. */
	private static Villager worker(GameTestHelper helper, BlockPos at, String name, String job, int jobLevel) {
		Villager v = villager(helper, at, name);
		v.setVillagerData(v.getVillagerData().setProfession(job(helper, job)).setLevel(jobLevel));
		return v;
	}

	/** {@code hearts} hearts with {@code me}, the events of every level below already told. */
	private static void friends(Villager villager, ServerPlayer me, int hearts) {
		for (int i = 0; i < hearts / 2 - 1; i++) {
			Friendship.told(villager, me, BELOW.get(i));
		}
		Friendship.add(villager, me, hearts * Friendship.PER_HEART - Friendship.points(villager, me.getUUID()));
	}

	/** What {@code villager} would tell {@code me} now. */
	private static String pending(GameTestHelper helper, Villager villager, ServerPlayer me) {
		HeartEvents.Event event = HeartEvents.pending(helper.getLevel(), villager, me.getUUID());
		return event == null ? "nothing" : event.id().toString();
	}

	/** One variant: who, the facts set up, the event it must pick, and (when told to the end) what is read. */
	private record Case(String what, Villager villager, String event, String first, String chronicle, String story) {
		Case(String what, Villager villager, String event) {
			this(what, villager, event, null, null, null);
		}
	}

	/**
	 * At {@code hearts} hearts the case's villager has its event to tell and nothing at one heart less; with a first
	 * line given it is told to the end: every line reads as a sentence, the chronicle and the life story get theirs,
	 * and it's told once. The villager is taken away afterwards.
	 */
	private static List<String> tell(GameTestHelper helper, ServerPlayer me, int hearts, Case c) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager v = c.villager();
		friends(v, me, hearts);
		Friendship.add(v, me, -1);
		helper.assertTrue(pending(helper, v, me).equals("nothing"), c.what() + ": at " + (hearts - 1) + " hearts they'd tell " + pending(helper, v, me));
		Friendship.add(v, me, 1);
		helper.assertTrue(pending(helper, v, me).equals(NS + c.event()), c.what() + ": would tell " + pending(helper, v, me) + ", not " + c.event());
		List<String> lines = List.of();
		if (c.first() != null) {
			HeartEvents.Event event = HeartEvents.pending(level, v, me.getUUID());
			lines = listen(helper, v, me, later(level), event.lines().size());
			helper.assertTrue(lines.get(0).equals(c.first()), c.what() + " began: " + lines.get(0));
			for (String line : lines) {
				helper.assertTrue(line.length() > 20 && !line.contains("%") && !line.contains("heart_event.") && !line.contains("nobody wrote down")
					&& ".?!".indexOf(line.charAt(line.length() - 1)) >= 0, c.what() + " said: " + line);
			}
			helper.assertTrue(told(v, me).contains(NS + c.event()), c.what() + ": told " + told(v, me));
			List<String> chronicle = friendLines(level, hall);
			helper.assertTrue(!chronicle.isEmpty() && chronicle.get(chronicle.size() - 1).equals(c.chronicle()), c.what() + ", the chronicle: " + chronicle);
			String story = Component.translatable(event.story(), HeartEvents.args(level, v, Component.literal(ME))).getString();
			helper.assertTrue(story.equals(c.story()), c.what() + ", life story line: " + story);
			HeartEvents.tick(level, later(level));
			helper.assertTrue(HeartEvents.telling(v) == null, c.what() + ": started again");
		}
		v.discard();
		return lines;
	}

	private static HeartEvents.When when(String json) {
		return HeartEvents.read(ResourceLocation.fromNamespaceAndPath("mypack", "test"), JsonParser.parseString(
			"{\"hearts\": 6, \"when\": " + json + ", \"lines\": [\"a\", \"b\", \"c\"], \"chronicle\": \"c\", \"story\": \"s\"}")).when();
	}

	/** Both ways: {@code {"<condition>": true}} holds exactly when the fact does, {@code false} when it doesn't. */
	private static void reads(GameTestHelper helper, Villager villager, String condition, boolean fact, String what) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(when("{\"" + condition + "\": true}").holds(level, villager) == fact && when("{\"" + condition + "\": false}").holds(level, villager) != fact,
			what + ": \"" + condition + "\" should read " + fact);
	}

	private static void sleepsAt(ServerLevel level, Villager villager, BlockPos bed) {
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
	}

	// --- 4 hearts: My work ----------------------------------------------------------------------------------------

	/** At 4 hearts every job of every family tells its family's event; the jobless ask about the nearest free workstation. */
	//$ gametest_ticks_batch AREA '100' '"heartWork"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "heartWork")
	public void eachJobFamilyTellsItsWork(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(5, 2, 4);
		ServerPlayer[] listener = new ServerPlayer[1];
		// (The later steps are registered here, not from the delayed set-up: the framework can't take new timed work while it runs one.)
		// A loom goes up near the hall once everyone has been asked (its POI is in a tick later): the jobless then ask about that.
		helper.runAfterDelay(6, () -> {
			helper.setBlock(new BlockPos(11, 2, 9), Blocks.LOOM);
			helper.setBlock(new BlockPos(3, 2, 14), Blocks.COMPOSTER); // further from the hall
		});
		helper.runAfterDelay(10, () -> {
			ServerPlayer me = listener[0];
			helper.assertTrue(me != null, "the first part didn't run");
			Villager finn = villager(helper, at, "Finn");
			friends(finn, me, 4);
			List<String> asks = listen(helper, finn, me, later(level), 4);
			helper.assertTrue(asks.get(2).equals("Nobody works at the Loom near the hall. Could you see me as the village's next Shepherd?"),
				"the nearest free workstation: " + asks.get(2));
			helper.assertTrue(asks.get(3).equals("You've seen more of the world than I have. Tell me what you think, next time. I'd listen to you."), asks.get(3));
			helper.succeed();
		});
		village(helper, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			String village = VillageHalls.name(level, hall).getString();
			ServerPlayer me = player(helper, new BlockPos(5, 2, 6));
			record Work(String event, List<String> jobs, String first, String chronicle, String story) {
			}
			List<Work> works = List.of(
				new Work("work_building", JobFamilies.Family.BUILDING.jobs.stream().map(ResourceLocation::toString).toList(),
					"You've watched me work, " + ME + ". Let me tell you about the first wall I ever raised.",
					"Dara told " + ME + " about the first wall they raised, and the one that fell", "Raised a first wall that fell, and a second that still stands"),
				new Work("work_mining", JobFamilies.Family.MINING.jobs.stream().map(ResourceLocation::toString).toList(),
					"Do you know what dark is, " + ME + "? I thought I did, until the day my lamp went out underground.",
					"Dara told " + ME + " about the day their lamp went out underground", "Walked out of the dark the day their lamp went out underground"),
				new Work("work_land", JobFamilies.Family.LAND.jobs.stream().map(ResourceLocation::toString).toList(),
					"People think my work is all sunshine, " + ME + ". Let me tell you about the year the harvest failed.",
					"Dara told " + ME + " about the year the harvest failed", "Came through the year the harvest failed"),
				new Work("work_animals", List.of("minecraft:shepherd", "minecraft:butcher", "aliveworkplace:rancher"),
					"I'll tell you why I do this work, " + ME + ". There was a foal once, born too early, on a cold night.",
					"Dara told " + ME + " about the foal they raised", "Raised a foal by hand that its mother wouldn't have"),
				new Work("work_water", List.of("minecraft:fisherman"),
					"Everyone who fishes has one story, " + ME + ", and this is mine. It's about the fish that got away.",
					"Dara told " + ME + " about the fish that got away", "Still fishes the bend where the big one got away"),
				new Work("work_kitchen", JobFamilies.Family.KITCHEN.jobs.stream().map(ResourceLocation::toString).toList(),
					"Do you want to know what made me a cook, " + ME + "? One dish. I didn't even mean to make it.",
					"Dara told " + ME + " about the dish that made them a cook", "Became a cook over one pumpkin pie"),
				new Work("work_learning", JobFamilies.Family.LEARNING.jobs.stream().map(ResourceLocation::toString).toList(),
					"I used to be certain about everything, " + ME + ". Then a book changed my mind.",
					"Dara told " + ME + " about the book that changed their mind", "Had their mind changed by a book with half a cover"),
				new Work("work_healing", JobFamilies.Family.HEALING.jobs.stream().map(ResourceLocation::toString).toList(),
					"There's something you should know about my work, " + ME + ". It doesn't always end well.",
					"Dara told " + ME + " about the patient they couldn't save", "Lost a patient once, and has been careful ever since"),
				new Work("work_arms", JobFamilies.Family.ARMS.jobs.stream().map(ResourceLocation::toString).toList(),
					"You think I'm steady, " + ME + ". I wasn't always. Let me tell you about my first raid.",
					"Dara told " + ME + " about their first raid", "Stood their ground in their first raid"),
				new Work("work_trade", JobFamilies.Family.TRADE.jobs.stream().map(ResourceLocation::toString).toList(),
					"Since we're friends, " + ME + ", I'll tell you about the worst bargain of my life. Don't repeat it.",
					"Dara told " + ME + " about the worst bargain of their life", "Keeps a compass from the worst bargain of their life"),
				new Work("work_music", JobFamilies.Family.MUSIC.jobs.stream().map(ResourceLocation::toString).toList(),
					"There's a song I know that nobody sings any more, " + ME + ". I only have half of it.",
					"Dara told " + ME + " about a song nobody sings any more", "Knows half of a song nobody sings any more"),
				new Work("work_pokemon", JobFamilies.Family.POKEMON.jobs.stream().map(ResourceLocation::toString).toList(),
					"Do you remember your first partner Pokémon, " + ME + "? I remember mine. I think of it every day.",
					"Dara told " + ME + " about their first partner Pokémon", "Won over a stubborn first partner Pokémon"),
				new Work("work_none", JobFamilies.Family.NONE.jobs.stream().map(ResourceLocation::toString).toList(),
					"Everyone here has their work, " + ME + ". I watch them go off to it every morning.",
					"Dara asked " + ME + " where their place in " + village + " might be", "Hasn't found their place yet"));
			Set<String> seen = new TreeSet<>();
			for (Work work : works) {
				for (int i = 0; i < work.jobs().size(); i++) {
					String job = work.jobs().get(i);
					seen.add(job);
					Villager dara = worker(helper, at, "Dara", job, 2);
					// The first job of each family tells it to the end; the others are only asked what they'd tell.
					List<String> lines = tell(helper, me, 4, i == 0 ? new Case(job, dara, work.event(), work.first(), work.chronicle(), work.story())
						: new Case(job, dara, work.event()));
					if (i == 0 && work.event().equals("work_land")) {
						helper.assertTrue(lines.get(2).equals("We counted what was left and shared it by the handful. Nobody in " + village
							+ " got fat that winter, but nobody was left out."), "the village in a line: " + lines.get(2));
					}
					if (i == 0 && work.event().equals("work_none")) {
						// No workstation stands free yet.
						helper.assertTrue(lines.get(2).equals("Every bench in the village already has someone at it. Maybe mine hasn't been built yet."),
							"no free workstation: " + lines.get(2));
					}
				}
			}
			// Every job of the twelve families was asked, and each is a real profession.
			Set<String> all = new TreeSet<>();
			for (JobFamilies.Family family : JobFamilies.Family.values()) {
				family.jobs.forEach(j -> all.add(j.toString()));
			}
			helper.assertTrue(seen.equals(all), "jobs not asked: " + all.stream().filter(j -> !seen.contains(j)).toList());
			// A job no family lists (another mod's) has the event of those without a trade.
			helper.assertTrue(JobFamilies.of(ResourceLocation.fromNamespaceAndPath("othermod", "brewer")) == JobFamilies.Family.NONE, "an unknown job's family");
			// A nitwit is told no bench would have them, whatever stands free.
			Villager nitwit = worker(helper, at, "Nell", "minecraft:nitwit", 1);
			helper.assertTrue(HeartEvents.station(level, nitwit).getString()
				.equals("They say no bench would have me, and maybe they're right. I'd still like to be good for something."), HeartEvents.station(level, nitwit).getString());
			nitwit.discard();
			listener[0] = me;
		});
	}

	// --- 6 hearts: What keeps me up at night -----------------------------------------------------------------------

	/** At 6 hearts they tell the worry that fits their life now: hunger, no bed, raids, illness, loneliness, or you. */
	//$ gametest_ticks_batch AREA '100' '"heartNight"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "heartNight")
	public void eachWorryIsToldForItsFacts(GameTestHelper helper) {
		// The village's store: a chest by a smoker, with 16 loaves in it (placed first: its POI is in a tick later).
		BlockPos chestAt = new BlockPos(13, 2, 12);
		helper.setBlock(new BlockPos(12, 2, 12), Blocks.SMOKER);
		helper.setBlock(chestAt, Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.BREAD, 16));
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity hallEntity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			String village = VillageHalls.name(level, hall).getString();
			long day = Chronicle.day(level);
			long now = level.getGameTime();
			ServerPlayer me = player(helper, new BlockPos(5, 2, 6));
			BlockPos at = new BlockPos(5, 2, 4);
			helper.assertTrue(VillageNeeds.store(level, hall).contains(helper.absolutePos(chestAt)), "the chest isn't the village's store");
			// Someone unnamed stands by: company. Everyone below has a bed unless the case takes it away.
			Villager[] company = {villager(helper, new BlockPos(6, 2, 4), null)};
			Consumer<Villager> housed = v -> sleepsAt(level, v, helper.absolutePos(new BlockPos(4, 2, 4)));

			// Fed, housed, well, in company, no trouble about: they worry about you.
			Villager dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			housed.accept(dara);
			for (String fact : List.of("hungry", "no_bed", "raided", "ill", "lonely")) {
				reads(helper, dara, fact, false, "nothing wrong");
			}
			tell(helper, me, 6, new Case("nothing wrong", dara, "night_you",
				"Do you want to know what keeps me up at night, " + ME + "? I'm fed, I'm housed and I'm well. So it's you.",
				"Dara told " + ME + " that they worry when " + ME + " is away", "Has lain awake worrying about a friend on the road"));

			// Hunger: they haven't eaten for a day...
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			housed.accept(dara);
			ModAttachments.LAST_MEAL.set(dara, now - 3 * VillageNeeds.DAY);
			reads(helper, dara, "hungry", true, "unfed for three days");
			tell(helper, me, 6, new Case("hungry", dara, "night_hunger",
				"Can I tell you what keeps me up at night, " + ME + "? It's a small thing. It's the larder.",
				"Dara told " + ME + " that the empty larder keeps them up at night", "Has lain awake counting the meals in the store"));
			// ...or the store holds under 16 meals (15 loaves: hunger; the 16th back: not).
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			housed.accept(dara);
			ModAttachments.LAST_MEAL.set(dara, now);
			chest.setItem(0, new ItemStack(Items.BREAD, 15));
			reads(helper, dara, "hungry", true, "15 meals in the store");
			tell(helper, me, 6, new Case("15 meals in the store", dara, "night_hunger"));
			chest.setItem(0, new ItemStack(Items.BREAD, 16));

			// No bed of their own.
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			reads(helper, dara, "no_bed", true, "no bed");
			tell(helper, me, 6, new Case("no bed", dara, "night_no_bed",
				"Do you want to know what keeps me up at night, " + ME + "? Mostly it's having nowhere to lie down.",
				"Dara told " + ME + " that they have no bed of their own", "Has lain awake wishing for a bed of their own"));

			// A raid five days ago still counts; six days ago doesn't.
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			housed.accept(dara);
			hallEntity.setLastRaidDay(day - 6);
			reads(helper, dara, "raided", false, "a raid six days ago");
			hallEntity.setLastRaidDay(day - 5);
			reads(helper, dara, "raided", true, "a raid five days ago");
			List<String> raid = tell(helper, me, 6, new Case("a raid five days ago", dara, "night_raids",
				"I'll tell you what keeps me up at night, " + ME + ". I listen.",
				"Dara told " + ME + " that they lie awake listening for raiders", "Has lain awake listening for raiders"));
			helper.assertTrue(raid.get(2).equals("There's been trouble too close to " + village + " lately. You've seen it. We all pretend we haven't."), raid.get(2));
			hallEntity.setLastRaidDay(-100);

			// Illness: their own...
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			housed.accept(dara);
			ModAttachments.ILL_SINCE.set(dara, now);
			reads(helper, dara, "ill", true, "ill");
			tell(helper, me, 6, new Case("ill", dara, "night_illness",
				"Something has been keeping me up at night, " + ME + ", and I'd rather say it to you than to the ceiling.",
				"Dara told " + ME + " about the sickness in their house", "Has lain awake over the sickness in their house"));
			// ...a child's...
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			housed.accept(dara);
			Villager finn = villager(helper, new BlockPos(7, 2, 8), "Finn");
			ModAttachments.PARENTS.set(finn, new Families.Parents(Component.literal("Dara"), Component.literal("Odo"), "", "", false));
			reads(helper, dara, "ill", false, "a healthy child");
			ModAttachments.ILL_SINCE.set(finn, now);
			reads(helper, dara, "ill", true, "an ill child");
			tell(helper, me, 6, new Case("an ill child", dara, "night_illness"));
			// ...a parent's (and the child worries for the parent)...
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			reads(helper, finn, "ill", true, "ill himself");
			ModAttachments.ILL_SINCE.remove(finn);
			reads(helper, finn, "ill", false, "well, with a well mother");
			ModAttachments.ILL_SINCE.set(dara, now);
			reads(helper, finn, "ill", true, "an ill mother");
			dara.discard();
			finn.discard();
			// ...or their partner's.
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			housed.accept(dara);
			Villager odo = villager(helper, new BlockPos(7, 2, 8), "Odo");
			ModAttachments.PARTNER.set(dara, new Couples.Partner(odo.getUUID(), Component.literal("Odo"), 1, true));
			reads(helper, dara, "ill", false, "a healthy partner");
			ModAttachments.ILL_SINCE.set(odo, now);
			reads(helper, dara, "ill", true, "an ill partner");
			tell(helper, me, 6, new Case("an ill partner", dara, "night_illness"));
			odo.discard();

			// Which worry comes first when several fit: hunger, then no bed, then raids, then illness.
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			ModAttachments.ILL_SINCE.set(dara, now);
			hallEntity.setLastRaidDay(day);
			housed.accept(dara);
			tell(helper, me, 6, new Case("raided and ill", dara, "night_raids"));
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			ModAttachments.ILL_SINCE.set(dara, now);
			tell(helper, me, 6, new Case("no bed, raided and ill", dara, "night_no_bed"));
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			ModAttachments.ILL_SINCE.set(dara, now);
			ModAttachments.LAST_MEAL.set(dara, now - 3 * VillageNeeds.DAY);
			tell(helper, me, 6, new Case("hungry, no bed, raided and ill", dara, "night_hunger"));
			hallEntity.setLastRaidDay(-100);

			// Loneliness: single, and nobody within a few blocks. With company, or with a partner, they aren't lonely.
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			housed.accept(dara);
			reads(helper, dara, "lonely", false, "in company");
			company[0].discard();
			reads(helper, dara, "lonely", true, "single and alone");
			ModAttachments.PARTNER.set(dara, new Couples.Partner(UUID.randomUUID(), Component.literal("Odo"), 1, false));
			reads(helper, dara, "lonely", false, "courting, though alone just now");
			ModAttachments.PARTNER.remove(dara);
			ModAttachments.ILL_SINCE.set(dara, now);
			helper.assertTrue(pending(helper, friendsOf(dara, me, 6), me).equals(NS + "night_illness"), "ill and lonely: " + pending(helper, dara, me));
			ModAttachments.ILL_SINCE.remove(dara);
			tell(helper, me, 6, new Case("single and alone", dara, "night_lonely",
				"Do you know what keeps me up at night, " + ME + "? The quiet. There's so much of it.",
				"Dara told " + ME + " how quiet their evenings are", "Has lain awake in a house that is too quiet"));
			helper.succeed();
		});
	}

	private static Villager friendsOf(Villager villager, ServerPlayer me, int hearts) {
		friends(villager, me, hearts);
		return villager;
	}

	// --- 8 hearts: The people I love -------------------------------------------------------------------------------

	/** At 8 hearts they tell of who they love: their partner and the wedding day, who they court, their children, who they lost, their parents, or the village. */
	//$ gametest_ticks_batch AREA '100' '"heartLove"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "heartLove")
	public void eachLoveIsToldForItsFacts(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			String village = VillageHalls.name(level, hall).getString();
			ServerPlayer me = player(helper, new BlockPos(5, 2, 6));
			BlockPos at = new BlockPos(5, 2, 4);
			Families.Parents born = new Families.Parents(Component.literal("Mira"), Component.literal("Tomas"), "", "", true);
			UUID odo = UUID.randomUUID();

			// Nobody of their own, nothing on record: the village is their family.
			Villager dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			List<String> lines = tell(helper, me, 8, new Case("alone", dara, "love_village",
				"Who do I love, " + ME + "? I've nobody of my own to name. So I'll say it plainly: the village is my family.",
				"Dara told " + ME + " that the village is their family", "Calls the whole of " + village + " their family"));
			helper.assertTrue(lines.get(1).equals("The one who saves me the heel of the loaf. The one who complains about my singing. All of " + village + "."), lines.get(1));

			// Alone, born here: their parents, by name.
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			ModAttachments.PARENTS.set(dara, born);
			tell(helper, me, 8, new Case("alone, born here", dara, "love_parents",
				"When I think about the people I love, " + ME + ", I start with Mira and Tomas. They made me, after all.",
				"Dara told " + ME + " about their parents, Mira and Tomas", "Grateful to Mira and Tomas for a good table to grow up at"));

			// Mourning: their late partner, by name (born here or not).
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			ModAttachments.PARENTS.set(dara, born);
			ModAttachments.LATE_PARTNER.set(dara, new Couples.LatePartner(odo, Component.literal("Odo")));
			tell(helper, me, 8, new Case("widowed", dara, "love_mourning",
				"I'd like to tell you about Odo, " + ME + ". I don't get to say the name out loud very often.",
				"Dara told " + ME + " about Odo, whom they lost", "Still sets out a cup for Odo"));

			// A parent: their children by name; a widowed parent tells of the children.
			Villager finn = villager(helper, new BlockPos(7, 2, 8), "Finn");
			ModAttachments.PARENTS.set(finn, new Families.Parents(Component.literal("Dara"), Component.literal("Odo"), "", "", false));
			Villager ana = villager(helper, new BlockPos(8, 2, 8), "Ana");
			ModAttachments.PARENTS.set(ana, new Families.Parents(Component.literal("Dara"), Component.literal("Odo"), "", "", false));
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			ModAttachments.LATE_PARTNER.set(dara, new Couples.LatePartner(odo, Component.literal("Odo")));
			tell(helper, me, 8, new Case("a widowed parent", dara, "love_parent",
				"You've met my family, haven't you, " + ME + "? I mean Ana and Finn. That's where my heart lives.",
				"Dara told " + ME + " about their children", "Lives for their children"));

			// Courting: who they have their eye on. A parent who courts tells of that.
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			ModAttachments.PARTNER.set(dara, new Couples.Partner(odo, Component.literal("Odo"), 3, false));
			lines = tell(helper, me, 8, new Case("courting, with children", dara, "love_courting",
				"Can you keep a secret, " + ME + "? It isn't much of one. Half of " + village + " has guessed already.",
				"Dara told " + ME + " a secret about Odo", "Has their heart set on Odo"));
			helper.assertTrue(lines.get(1).equals("It's Odo. I find reasons to walk past, and I forget every one of them when I get there."), lines.get(1));

			// Married: their partner, and the wedding day. A married parent tells of the partner.
			dara = worker(helper, at, "Dara", "minecraft:farmer", 2);
			ModAttachments.PARENTS.set(dara, born);
			ModAttachments.PARTNER.set(dara, new Couples.Partner(odo, Component.literal("Odo"), 12, true));
			lines = tell(helper, me, 8, new Case("married, with children", dara, "love_married",
				"I want to tell you about the people I love, " + ME + ". It's a short list, and Odo is at the top of it.",
				"Dara told " + ME + " about Odo and their wedding day", "Loves Odo, married on day 12"));
			helper.assertTrue(lines.size() == 5 && lines.get(1).equals("We were married on day 12. I remember it better than I remember this morning.")
				&& lines.get(2).equals("I said my words wrong, and Odo laughed, and then the whole of " + village + " laughed with us."), "the wedding: " + lines);
			finn.discard();
			ana.discard();
			helper.succeed();
		});
	}

	// --- 10 hearts: What I dream of --------------------------------------------------------------------------------

	/** At 10 hearts they tell their dream: to be a Master, the Nether or the sea, a finer house, a City, a festival, or nothing more. */
	//$ gametest_ticks_batch AREA '100' '"heartDream"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "heartDream")
	public void eachDreamIsToldForItsFacts(GameTestHelper helper) {
		village(helper, () -> {
			Leftovers.finished(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity hallEntity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			String village = VillageHalls.name(level, hall).getString();
			ServerPlayer me = player(helper, new BlockPos(5, 2, 6));
			BlockPos at = new BlockPos(5, 2, 4);
			boolean moods = Moods.ENABLED;
			Moods.ENABLED = true;
			// A finished tier III house (only its size matters to a home), and one of tier II.
			BuildSiteManager sites = BuildSiteManager.get(level);
			List<BlueprintData.Placement> placed = new ArrayList<>();
			BlockPos[] beds = new BlockPos[4];
			for (int tier = 2; tier <= 3; tier++) {
				ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "dream_house_" + tier);
				BlockPos origin = helper.absolutePos(new BlockPos(2 + (tier - 2) * 8, 10, 2));
				level.getStructureManager().getOrCreate(id).fillFromWorld(level, origin.above(60), new Vec3i(5, 4, 5), false, Blocks.AIR);
				BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
				sites.recordFinished(id, placement, UUID.randomUUID());
				placed.add(placement);
				beds[tier] = origin.offset(2, 1, 2);
			}
			Leftovers.after(helper, () -> {
				Moods.ENABLED = moods;
				Moods.forget();
				placed.forEach(sites::forgetFinished);
			});

			// Below Master, whatever else: to be a Master.
			Villager dara = worker(helper, at, "Dara", "aliveworkplace:miner", 4);
			helper.assertTrue(when("{\"level_below\": 5}").holds(level, dara) && !when("{\"level\": 5}").holds(level, dara) && when("{\"trade\": true}").holds(level, dara),
				"a level 4 miner's level and trade");
			tell(helper, me, 10, new Case("a level 4 miner", dara, "dream_master",
				"I've never told anyone what I dream of, " + ME + ". Don't laugh. I want to be a Master of my trade.",
				"Dara told " + ME + " they dream of becoming a Master", "Dreams of being a Master of their trade"));

			// A Master whose trade comes out of the deep: the Nether. One of the water and the roads: the sea.
			dara = worker(helper, at, "Dara", "aliveworkplace:miner", 5);
			helper.assertTrue(!when("{\"level_below\": 5}").holds(level, dara) && when("{\"level\": 5}").holds(level, dara), "a Master's level");
			tell(helper, me, 10, new Case("a Master miner", dara, "dream_nether",
				"I'll tell you what I dream of, " + ME + ", and you'll think me mad. I want to see the Nether.",
				"Dara told " + ME + " they dream of seeing the Nether", "Dreams of seeing the Nether"));
			for (String job : List.of("minecraft:armorer", "minecraft:toolsmith", "minecraft:weaponsmith", "aliveworkplace:sifter")) {
				tell(helper, me, 10, new Case("a Master " + job, worker(helper, at, "Dara", job, 5), "dream_nether"));
			}
			dara = worker(helper, at, "Dara", "minecraft:fisherman", 5);
			tell(helper, me, 10, new Case("a Master fisherman", dara, "dream_sea",
				"Here is what I dream of, " + ME + ". The sea. I've never laid eyes on it.",
				"Dara told " + ME + " they dream of seeing the sea", "Dreams of seeing the sea"));
			for (String job : List.of("aliveworkplace:ferryman", "minecraft:cartographer", "aliveworkplace:postman", "aliveworkplace:porter")) {
				tell(helper, me, 10, new Case("a Master " + job, worker(helper, at, "Dara", job, 5), "dream_sea"));
			}

			// Any other Master with a home below tier III (none, or tier II): a finer house. So do those without a trade.
			dara = worker(helper, at, "Dara", "minecraft:farmer", 5);
			helper.assertTrue(HeartEvents.homeTier(level, dara) == 0 && when("{\"home_tier_below\": 3}").holds(level, dara), "no home: tier " + HeartEvents.homeTier(level, dara));
			List<String> lines = tell(helper, me, 10, new Case("a Master farmer without a home", dara, "dream_house",
				"Shall I tell you what I dream of, " + ME + "? It isn't grand. It's a house.",
				"Dara told " + ME + " they dream of a finer house", "Dreams of a finer house"));
			helper.assertTrue(lines.get(3).equals("Maybe it will stand in " + village + " one day. Until then, here. I want you to have something of mine."), lines.get(3));
			dara = worker(helper, at, "Dara", "minecraft:farmer", 5);
			sleepsAt(level, dara, beds[2]);
			helper.assertTrue(HeartEvents.homeTier(level, dara) == 2, "a tier II home reads " + HeartEvents.homeTier(level, dara));
			tell(helper, me, 10, new Case("a Master farmer in a tier II home", dara, "dream_house"));
			for (String job : List.of("minecraft:none", "minecraft:nitwit")) {
				dara = worker(helper, at, "Dara", job, 1);
				helper.assertTrue(!when("{\"trade\": true}").holds(level, dara) && when("{\"trade\": false}").holds(level, dara), job + " has a trade");
				tell(helper, me, 10, new Case(job, dara, "dream_house"));
			}

			// In a tier III home, with the village below a City: the village a City.
			dara = worker(helper, at, "Dara", "minecraft:farmer", 5);
			sleepsAt(level, dara, beds[3]);
			helper.assertTrue(HeartEvents.homeTier(level, dara) == 3 && !when("{\"home_tier_below\": 3}").holds(level, dara), "a tier III home reads " + HeartEvents.homeTier(level, dara));
			helper.assertTrue(when("{\"rank_below\": \"city\"}").holds(level, dara) && !when("{\"rank_below\": \"hamlet\"}").holds(level, dara), "a hamlet's rank");
			tell(helper, me, 10, new Case("a Master farmer in a tier III home", dara, "dream_city",
				"Do you know what I dream of, " + ME + "? I dream of " + village + " as a City.",
				"Dara told " + ME + " they dream of " + village + " as a City", "Dreams of seeing " + village + " a City"));

			// In a City, with nothing left to want of house or rank: a festival in their honour.
			hallEntity.setRank(VillageRanks.Rank.CITY);
			dara = worker(helper, at, "Dara", "minecraft:farmer", 5);
			sleepsAt(level, dara, beds[3]);
			helper.assertTrue(!when("{\"rank_below\": \"city\"}").holds(level, dara), "a City is below a City");
			tell(helper, me, 10, new Case("a Master farmer in a tier III home in a City", dara, "dream_festival",
				"You'll laugh at what I dream of, " + ME + ". I dream of a festival, and it's in my honour.",
				"Dara told " + ME + " they dream of a festival in their honour", "Dreams of a festival in their honour"));
			hallEntity.setRank(VillageRanks.Rank.HAMLET);

			// A happy, married Master has everything they wanted (fed and at home in a grand house: happy)...
			dara = worker(helper, at, "Dara", "aliveworkplace:miner", 5);
			sleepsAt(level, dara, beds[3]);
			ModAttachments.LAST_MEAL.set(dara, level.getGameTime());
			ModAttachments.PARTNER.set(dara, new Couples.Partner(UUID.randomUUID(), Component.literal("Odo"), 12, true));
			Moods.Mood mood = Moods.of(dara);
			helper.assertTrue(mood != null && mood.score() >= Moods.HAPPY && when("{\"happy\": true}").holds(level, dara), "fed, in a grand home, with a job: " + mood);
			lines = tell(helper, me, 10, new Case("a happy, married Master", dara, "dream_content",
				"People ask me what I dream of, " + ME + ". The truth is, I have everything I wanted.",
				"Dara told " + ME + " they have everything they wanted", "Has everything they wanted"));
			helper.assertTrue(lines.get(1).equals("I'm a Master of my trade. Odo is waiting for me at home. I wake up glad more days than not."), lines.get(1));
			// ...an unhappy one (hungry, no bed) still dreams, as does a happy Master who isn't married.
			dara = worker(helper, at, "Dara", "aliveworkplace:miner", 5);
			ModAttachments.LAST_MEAL.set(dara, level.getGameTime() - 3 * VillageNeeds.DAY);
			ModAttachments.PARTNER.set(dara, new Couples.Partner(UUID.randomUUID(), Component.literal("Odo"), 12, true));
			helper.assertTrue(!when("{\"happy\": true}").holds(level, dara) && when("{\"happy\": false}").holds(level, dara), "hungry and homeless, yet happy: " + Moods.of(dara));
			tell(helper, me, 10, new Case("an unhappy, married Master", dara, "dream_nether"));
			dara = worker(helper, at, "Dara", "aliveworkplace:miner", 5);
			sleepsAt(level, dara, beds[3]);
			ModAttachments.LAST_MEAL.set(dara, level.getGameTime());
			helper.assertTrue(when("{\"happy\": true}").holds(level, dara), "a happy single Master: " + Moods.of(dara));
			tell(helper, me, 10, new Case("a happy, single Master", dara, "dream_nether"));
			helper.succeed();
		});
	}

	// --- The keepsakes ----------------------------------------------------------------------------------------------

	private static int count(ServerPlayer player, Item item) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			n += stack.is(item) ? stack.getCount() : 0;
		}
		return n;
	}

	private static ItemStack held(ServerPlayer player, Item item) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(item)) {
				return player.getInventory().getItem(i);
			}
		}
		return ItemStack.EMPTY;
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/** The enchantments on {@code stack} (or in it, for a book), as "id=level". */
	private static Set<String> enchantments(ItemStack stack) {
		Set<String> out = new TreeSet<>();
		EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()
			.forEach(e -> out.add(e.getKey().unwrapKey().map(k -> k.location().getPath()).orElse("?") + "=" + e.getIntValue()));
		return out;
	}

	private static String path(ResourceKey<Enchantment> key, int level) {
		return key.location().getPath() + "=" + level;
	}

	/** Each job family's keepsake comes with the 10-heart event: the item, its name, enchantment and lore, as the roadmap lists them. */
	//$ gametest_ticks_batch AREA '100' '"heartKeepsakes"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "heartKeepsakes")
	public void eachFamilyGivesItsKeepsake(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			String village = VillageHalls.name(level, hall).getString();
			ServerPlayer me = player(helper, new BlockPos(5, 2, 6));
			BlockPos at = new BlockPos(5, 2, 4);
			// Cobblemon's Premier Ball where Cobblemon is installed; a snowball where it isn't (as in this suite).
			Item ball = BuiltInRegistries.ITEM.getOptional(Keepsakes.POKE_BALL).orElse(Items.SNOWBALL);
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(Keepsakes.POKE_BALL) || ball == Items.SNOWBALL && Keepsakes.POKE_BALL_FALLBACK == Items.SNOWBALL,
				"without Cobblemon the First Poké Ball is " + ball);
			record Gift(JobFamilies.Family family, Item item, int count, String name, String enchantment, String lore) {
			}
			List<Gift> gifts = List.of(
				new Gift(JobFamilies.Family.BUILDING, Items.IRON_SHOVEL, 1, "Dara's Trowel", path(Enchantments.EFFICIENCY, 2),
					"It smoothed the mortar of the second wall, the one that still stands."),
				new Gift(JobFamilies.Family.MINING, Items.IRON_PICKAXE, 1, "Dara's Lucky Pick", path(Enchantments.FORTUNE, 1),
					"It was in their hand the day the lamp went out, and it found the way back."),
				new Gift(JobFamilies.Family.LAND, Items.TORCHFLOWER_SEEDS, 4, "Dara's Grandmother's Seeds", null,
					"Handed down from the one row that came up the year the harvest failed."),
				new Gift(JobFamilies.Family.ANIMALS, Items.FISHING_ROD, 1, "Dara's Old Rod", path(Enchantments.LUCK_OF_THE_SEA, 2),
					"It has waited beside more water than they can remember."),
				new Gift(JobFamilies.Family.KITCHEN, Items.WRITTEN_BOOK, 1, "Dara's Secret Recipe", null,
					"The dish that made them a cook, written out at last."),
				new Gift(JobFamilies.Family.LEARNING, Items.ENCHANTED_BOOK, 1, "Dara's Annotated Atlas", path(Enchantments.MENDING, 1),
					"The margins hold more than the maps do."),
				new Gift(JobFamilies.Family.HEALING, Items.GOLDEN_APPLE, 1, "Dara's Remedy", null,
					"Kept ready ever since the patient they couldn't save."),
				new Gift(JobFamilies.Family.ARMS, Items.SHIELD, 1, "Dara's Old Shield", path(Enchantments.UNBREAKING, 2),
					"It stood in the gap on the night of their first raid."),
				new Gift(JobFamilies.Family.TRADE, Items.SPYGLASS, 1, "Dara's Spyglass", null,
					"The one thing they ever bought that showed more than was promised."),
				new Gift(JobFamilies.Family.MUSIC, Items.MUSIC_DISC_OTHERSIDE, 1, "Dara's Favourite Record", null,
					"The nearest thing they found to the song nobody sings any more."),
				new Gift(JobFamilies.Family.POKEMON, ball, 1, "Dara's First Poké Ball", null,
					"Their first partner slept in it, when it wasn't asleep on their boots."),
				new Gift(JobFamilies.Family.NONE, Items.CORNFLOWER, 1, "Dara's Pressed Flower", null,
					"Picked on a morning when they had nowhere to be, and kept flat ever since."));
			helper.assertTrue(gifts.size() == JobFamilies.Family.values().length, "a family without a keepsake in this test");
			for (Gift gift : gifts) {
				String what = gift.family().id();
				for (ResourceLocation job : gift.family().jobs) {
					// Every job of the family would give the same thing.
					Villager other = worker(helper, at, "Dara", job.toString(), 3);
					ItemStack theirs = Keepsakes.of(level, other).get(0);
					helper.assertTrue(theirs.is(gift.item()) && theirs.getHoverName().getString().equals(gift.name()), job + " would give " + theirs);
					other.discard();
				}
				Villager dara = worker(helper, at, "Dara", gift.family().jobs.get(0).toString(), 3);
				friends(dara, me, 10);
				me.getInventory().clearContent();
				helper.assertTrue(!Keepsakes.given(dara, me), what + ": given before anything was told");
				// Halfway through the telling there's no keepsake yet.
				HeartEvents.Event event = HeartEvents.pending(level, dara, me.getUUID());
				helper.assertTrue(event != null && event.hearts() == 10, what + ": at 10 hearts they'd tell " + pending(helper, dara, me));
				long start = later(level);
				HeartEvents.tick(level, start);
				helper.assertTrue(HeartEvents.telling(dara) != null && me.getInventory().isEmpty(), what + ": a keepsake after the first line");
				for (int i = 1; i < event.lines().size(); i++) {
					HeartEvents.tick(level, start + (long) i * HeartEvents.LINE_EVERY);
				}
				helper.assertTrue(HeartEvents.telling(dara) == null && told(dara, me).contains(event.id().toString()), what + ": not told to the end");
				ItemStack keepsake = held(me, gift.item());
				helper.assertTrue(!keepsake.isEmpty() && count(me, gift.item()) == gift.count(), what + ": " + count(me, gift.item()) + " of " + gift.item() + " in the inventory");
				helper.assertTrue(keepsake.getHoverName().getString().equals(gift.name()), what + " is named " + keepsake.getHoverName().getString());
				helper.assertTrue(lore(keepsake).equals(List.of(gift.lore(), "A keepsake from Dara of " + village)), what + "'s lore: " + lore(keepsake));
				helper.assertTrue(enchantments(keepsake).equals(gift.enchantment() == null ? Set.of() : Set.of(gift.enchantment())),
					what + "'s enchantments: " + enchantments(keepsake));
				helper.assertTrue(Keepsakes.given(dara, me), what + ": not noted as given");
				if (gift.family() == JobFamilies.Family.KITCHEN) {
					// The cook's recipe is a signed book of two pages, and two pumpkin pies come with it.
					WrittenBookContent book = keepsake.get(DataComponents.WRITTEN_BOOK_CONTENT);
					helper.assertTrue(book != null && book.title().raw().equals("Dara's Secret Recipe") && book.author().equals("Dara") && book.pages().size() == 2,
						"the recipe book: " + book);
					helper.assertTrue(book.pages().get(0).raw().getString().startsWith("Dara's pumpkin pie\n\nOne pumpkin, the kind that thuds when you knock on it.")
						&& book.pages().get(1).raw().getString().startsWith("Bake it until the kitchen smells like the end of autumn."),
						"the recipe: " + book.pages().get(0).raw().getString());
					for (var page : book.pages()) {
						helper.assertTrue(page.raw().getString().length() <= 256 && !page.raw().getString().contains("%"), "a page: " + page.raw().getString());
					}
					helper.assertTrue(count(me, Items.PUMPKIN_PIE) == 2 && held(me, Items.PUMPKIN_PIE).getHoverName().getString().equals("Pumpkin Pie"),
						count(me, Items.PUMPKIN_PIE) + " pumpkin pies with the recipe");
				} else {
					int stacks = 0;
					for (int i = 0; i < me.getInventory().getContainerSize(); i++) {
						stacks += me.getInventory().getItem(i).isEmpty() ? 0 : 1;
					}
					helper.assertTrue(stacks == 1, what + ": " + stacks + " stacks given");
				}
				dara.discard();
			}
			// What the player reads in the chat.
			helper.assertTrue(Component.translatable("message.aliveworkplace.keepsake.given", "Dara", "Dara's Trowel").getString().equals("Dara gave you a keepsake: Dara's Trowel"),
				"the chat line");
			helper.succeed();
		});
	}

	/** A keepsake comes once per player: not again whatever is told later, to each player their own, through a save, a full inventory and the switch off. */
	//$ gametest_ticks_batch AREA '100' '"heartKeepsakeOnce"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "heartKeepsakeOnce")
	public void aKeepsakeComesOncePerPlayer(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer alex = player(helper, new BlockPos(5, 2, 6));
			ServerPlayer jesse = player(helper, new BlockPos(4, 2, 5));
			BlockPos at = new BlockPos(5, 2, 4);
			Villager dara = worker(helper, at, "Dara", "aliveworkplace:miner", 3);
			// Under 10 hearts, nothing: the 8-heart event told gives no keepsake.
			friends(dara, alex, 8);
			HeartEvents.Event love = HeartEvents.pending(level, dara, alex.getUUID());
			listen(helper, dara, alex, later(level), love.lines().size());
			helper.assertTrue(alex.getInventory().isEmpty() && !Keepsakes.given(dara, alex), "a keepsake at 8 hearts");
			// With heart events off nothing is told at 10 hearts, so nothing is given; on again, it is.
			friends(dara, alex, 10);
			HeartEvents.ENABLED = false;
			HeartEvents.tick(level, later(level));
			helper.assertTrue(HeartEvents.telling(dara) == null && alex.getInventory().isEmpty() && !Keepsakes.given(dara, alex), "off, and a keepsake");
			HeartEvents.ENABLED = true;
			HeartEvents.Event dream = HeartEvents.pending(level, dara, alex.getUUID());
			helper.assertTrue(dream != null && dream.id().toString().equals(NS + "dream_master"), "at 10 hearts: " + pending(helper, dara, alex));
			listen(helper, dara, alex, later(level), dream.lines().size());
			helper.assertTrue(count(alex, Items.IRON_PICKAXE) == 1 && Keepsakes.given(dara, alex) && !Keepsakes.given(dara, jesse), "Alex's keepsake: " + count(alex, Items.IRON_PICKAXE));
			helper.assertTrue(Friendship.points(dara, alex.getUUID()) == Friendship.MAX, "points at the top: " + Friendship.points(dara, alex.getUUID()));
			// Asked again, nothing; and nothing more is pending.
			helper.assertTrue(Keepsakes.give(level, dara, alex) == null && count(alex, Items.IRON_PICKAXE) == 1, "a second keepsake for the asking");
			helper.assertTrue(HeartEvents.pending(level, dara, alex.getUUID()) == null, "more to tell: " + pending(helper, dara, alex));
			// Saved and loaded, the villager remembers: a 10-heart event told again (a data pack changed the set) gives nothing.
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(dara.saveWithoutId(new CompoundTag()));
			helper.assertTrue(Friendship.of(copy).bond(alex.getUUID()).keepsake() && !Friendship.of(copy).bond(jesse.getUUID()).keepsake(), "after a reload: " + Friendship.of(copy));
			copy.discard();
			Map<ResourceLocation, JsonElement> files = new TreeMap<>(HeartEvents.files(level.getServer().getResourceManager()));
			files.keySet().removeIf(id -> id.getPath().startsWith("dream_"));
			files.put(ResourceLocation.fromNamespaceAndPath("mypack", "new_dream"),
				JsonParser.parseString("{\"hearts\": 10, \"lines\": [\"a\", \"b\", \"c\"], \"chronicle\": \"c\", \"story\": \"s\"}"));
			HeartEvents.load(files);
			helper.assertTrue(pending(helper, dara, alex).equals("mypack:new_dream"), "a pack's new 10-heart event: " + pending(helper, dara, alex));
			listen(helper, dara, alex, later(level), 3);
			helper.assertTrue(told(dara, alex).contains("mypack:new_dream") && count(alex, Items.IRON_PICKAXE) == 1, "a second keepsake: " + count(alex, Items.IRON_PICKAXE));
			// The other player gets their own, once they are told at 10 hearts; their inventory is full, so it lies at their feet.
			List<ItemStack> pockets = io.github.jcondedata.aliveworkplace.mc.Players.mainItems(jesse);
			for (int i = 0; i < pockets.size(); i++) {
				pockets.set(i, new ItemStack(Items.COBBLESTONE, 64));
			}
			friends(dara, jesse, 10);
			listen(helper, dara, jesse, later(level), 3);
			helper.assertTrue(Keepsakes.given(dara, jesse) && count(jesse, Items.IRON_PICKAXE) == 0, "Jesse's keepsake went into a full inventory");
			List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, jesse.getBoundingBox().inflate(3), e -> e.getItem().is(Items.IRON_PICKAXE));
			helper.assertTrue(dropped.size() == 1 && dropped.get(0).getItem().getHoverName().getString().equals("Dara's Lucky Pick"), "at Jesse's feet: " + dropped.size());
			helper.assertTrue(count(alex, Items.IRON_PICKAXE) == 1, "Alex got Jesse's too");
			// A friendship saved before keepsakes (no "keepsake") reads as not given, with what was told kept.
			Friendship.Data old = Friendship.Data.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString(
				"{\"players\": {\"" + alex.getUUID() + "\": {\"points\": 1000, \"told\": [\"aliveworkplace:born_here\"]}}}")).getOrThrow();
			helper.assertTrue(!old.bond(alex.getUUID()).keepsake() && old.bond(alex.getUUID()).told().equals(List.of("aliveworkplace:born_here")), "an old save: " + old);
			helper.succeed();
		});
	}

	// --- The shipped set --------------------------------------------------------------------------------------------

	/** The shipped events are the roadmap's set, every sentence has its text, and the families agree with the taste files. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"heartSetData"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "heartSetData")
	public void theShippedSetIsWhole(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<ResourceLocation, JsonElement> shipped = HeartEvents.files(level.getServer().getResourceManager());
		Leftovers.after(helper, () -> HeartEvents.load(shipped));
		HeartEvents.load(shipped);
		Map<Integer, Set<String>> byHearts = new TreeMap<>();
		for (HeartEvents.Event event : HeartEvents.all().values()) {
			if (!event.id().getNamespace().equals("aliveworkplace")) {
				continue;
			}
			byHearts.computeIfAbsent(event.hearts(), h -> new TreeSet<>()).add(event.id().getPath());
			List<String> keys = new ArrayList<>(event.lines());
			keys.add(event.chronicle());
			keys.add(event.story());
			Object[] args = {"Dara", "Jesse", "Thornholm", "Mira", "Tomas", "Odo", "Finn", "day 3", "day 12", "A sentence."};
			for (String key : keys) {
				String text = Component.translatable(key, args).getString();
				helper.assertTrue(!text.equals(key) && !text.isBlank() && !text.contains("%"), event.id() + ": no text for " + key + " (" + text + ")");
			}
			helper.assertTrue(event.lines().size() >= 3 && event.lines().size() <= 5, event.id() + ": " + event.lines().size() + " lines");
		}
		Map<Integer, Set<String>> expected = new TreeMap<>(Map.of(
			2, Set.of("born_here", "traveller", "before_hall", "back_from_grave"),
			4, Set.of("work_building", "work_mining", "work_land", "work_animals", "work_water", "work_kitchen", "work_learning", "work_healing", "work_arms",
				"work_trade", "work_music", "work_pokemon", "work_none"),
			6, Set.of("night_hunger", "night_no_bed", "night_raids", "night_illness", "night_lonely", "night_you"),
			8, Set.of("love_married", "love_courting", "love_parent", "love_mourning", "love_parents", "love_village"),
			10, Set.of("dream_master", "dream_house", "dream_city", "dream_festival", "dream_nether", "dream_sea", "dream_content")));
		for (int hearts : expected.keySet()) {
			helper.assertTrue(new TreeSet<>(expected.get(hearts)).equals(byHearts.get(hearts)), "at " + hearts + " hearts: " + byHearts.get(hearts));
		}
		helper.assertTrue(byHearts.keySet().equals(expected.keySet()), "heart levels with events: " + byHearts.keySet());
		// Each level has one event that asks nothing, so everyone has something to tell at 4, 6, 8 and 10 hearts.
		for (String always : List.of("work_none", "night_you", "love_village", "dream_festival")) {
			helper.assertTrue(HeartEvents.all().get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", always)).when().asked() == 0, always + " asks something");
		}
		// The 4-heart events' jobs are the families', and the families are the taste files' (31.6).
		Map<String, Set<String>> tastes = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : level.getServer().getResourceManager()
			.listResources("villager_tastes", p -> p.getNamespace().equals("aliveworkplace") && p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				JsonElement who = JsonParser.parseReader(reader).getAsJsonObject().get("for");
				if (who.isJsonObject() && who.getAsJsonObject().has("family")) {
					Set<String> jobs = new TreeSet<>();
					who.getAsJsonObject().getAsJsonArray("family").forEach(j -> jobs.add(j.getAsString()));
					String path = e.getKey().getPath();
					tastes.put(path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length()), jobs);
				}
			} catch (Exception ex) {
				helper.fail("reading " + e.getKey() + ": " + ex);
			}
		}
		for (JobFamilies.Family family : JobFamilies.Family.values()) {
			Set<String> jobs = new TreeSet<>(family.jobs.stream().map(ResourceLocation::toString).toList());
			String file = family == JobFamilies.Family.NONE ? "no_trade" : family.id();
			helper.assertTrue(jobs.equals(tastes.get(file)), "the " + family.id() + " family is " + jobs + ", its taste file says " + tastes.get(file));
			for (ResourceLocation job : family.jobs) {
				helper.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.containsKey(job) && JobFamilies.of(job) == family, "the job " + job + " of " + family.id());
			}
			if (family != JobFamilies.Family.NONE) {
				Set<String> told = new TreeSet<>();
				for (String event : family == JobFamilies.Family.ANIMALS ? List.of("work_animals", "work_water") : List.of("work_" + family.id())) {
					HeartEvents.all().get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", event)).when().jobs().forEach(j -> told.add(j.toString()));
				}
				helper.assertTrue(told.equals(jobs), "the " + family.id() + " family's 4-heart events are for " + told);
			}
			String name = Keepsakes.name(family, Component.literal("Dara")).getString();
			helper.assertTrue(name.startsWith("Dara's ") && !name.contains("keepsake"), "the " + family.id() + " keepsake's name: " + name);
		}
		// The new conditions are read, and a wrong one names itself.
		HeartEvents.When all = when("{\"trade\": true, \"hungry\": false, \"no_bed\": false, \"raided\": false, \"ill\": false, \"lonely\": false, \"happy\": true,"
			+ " \"level\": 3, \"level_below\": 5, \"home_tier_below\": 2, \"rank_below\": \"town\"}");
		HeartEvents.Now now = all.now();
		helper.assertTrue(all.asked() == 11 && Boolean.TRUE.equals(now.trade()) && Boolean.FALSE.equals(now.hungry()) && Boolean.FALSE.equals(now.noBed())
			&& Boolean.FALSE.equals(now.raided()) && Boolean.FALSE.equals(now.ill()) && Boolean.FALSE.equals(now.lonely()) && Boolean.TRUE.equals(now.happy())
			&& now.level() == 3 && now.levelBelow() == 5 && now.homeTierBelow() == 2 && now.rankBelow() == VillageRanks.Rank.TOWN, "the new conditions: " + all);
		Map<ResourceLocation, JsonElement> files = new TreeMap<>(shipped);
		String good = "\"lines\": [\"a\", \"b\", \"c\"], \"chronicle\": \"c\", \"story\": \"s\"";
		files.put(ResourceLocation.fromNamespaceAndPath("mypack", "bad_rank_below"), JsonParser.parseString("{\"hearts\": 6, \"when\": {\"rank_below\": \"empire\"}, " + good + "}"));
		files.put(ResourceLocation.fromNamespaceAndPath("mypack", "bad_level"), JsonParser.parseString("{\"hearts\": 6, \"when\": {\"level\": \"high\"}, " + good + "}"));
		files.put(ResourceLocation.fromNamespaceAndPath("mypack", "bad_flag"), JsonParser.parseString("{\"hearts\": 6, \"when\": {\"hungry\": \"very\"}, " + good + "}"));
		files.put(ResourceLocation.fromNamespaceAndPath("mypack", "bad_weight"), JsonParser.parseString("{\"hearts\": 6, \"weight\": \"heavy\", " + good + "}"));
		files.put(ResourceLocation.fromNamespaceAndPath("mypack", "heavy"), JsonParser.parseString("{\"hearts\": 6, \"weight\": 9, " + good + "}"));
		List<String> skipped = HeartEvents.load(files).stream().filter(id -> id.getNamespace().equals("mypack")).map(ResourceLocation::getPath).sorted().toList();
		helper.assertTrue(skipped.equals(List.of("bad_flag", "bad_level", "bad_rank_below", "bad_weight")), "skipped as broken: " + skipped);
		helper.assertTrue(HeartEvents.all().get(ResourceLocation.fromNamespaceAndPath("mypack", "heavy")).weight() == 9
			&& HeartEvents.all().get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "night_you")).weight() == 0, "weights");
		helper.succeed();
	}
}
