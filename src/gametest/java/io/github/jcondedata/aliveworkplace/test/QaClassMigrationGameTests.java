package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.CaravanSights;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.ClassesPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.people.ClassMigration;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * QA for switching classes on in every village at once (ROADMAP 34.22), written from the spec: the 16-a-round edge from
 * both sides, Burgher as the ceiling, households sharing a class, who is skipped, the edges of the grace, ages from the
 * UUID, a reload part-way, who hears it, the treasury and the switch off part-way. Each test stages its own small hall
 * saved before 1.8 and runs alone in its batch. Where a test needs to steer who qualifies, it loads the real class files
 * with their needs swapped for ones it controls (fed now, a varied diet, and each class's luxury), keeping their taxes.
 */
public class QaClassMigrationGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	// --- staging ------------------------------------------------------------------------------------------------------

	static String cls(Villager v) {
		ResourceLocation c = ModAttachments.SOCIAL_CLASS.get(v);
		return c == null ? "none" : c.getPath();
	}

	/** A hall saved before 1.8 at {@link #HALL}, classes on, a 16-block village; {@code steered} swaps the needs. */
	static VillageHallBlockEntity oldHall(GameTestHelper helper, boolean steered) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		Map<ResourceLocation, JsonElement> files = SocialClasses.files(level.getServer().getResourceManager());
		SocialClasses.load(steered ? steered(files) : files);
		SocialClasses.forget();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply(); // classes off again (GameTests), the grace back to 7
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
		});
		BlockPos hallPos = helper.absolutePos(HALL);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(hallPos);
		hall.setRank(VillageRanks.Rank.CITY);
		hall.setNeeds(new VillageNeeds.Needs(20, 20, 20, 20, 20, 0, 0, 1f)); // its own round waits its turn
		CompoundTag old = hall.saveWithFullMetadata(level.registryAccess());
		old.remove("classesVersion");
		old.remove("classGraceUntil");
		old.remove("families");
		hall.loadWithComponents(old, level.registryAccess());
		hall.setRank(VillageRanks.Rank.CITY);
		return hall;
	}

	/** The real class files, taxes and all, with needs the test steers: Artisan fed now; Burgher and Noble fed and a varied diet; each a luxury. */
	private static Map<ResourceLocation, JsonElement> steered(Map<ResourceLocation, JsonElement> real) {
		Map<ResourceLocation, JsonElement> out = new HashMap<>(real);
		out.put(AliveWorkplace.id("artisan"), withNeeds(real.get(AliveWorkplace.id("artisan")),
			"[{\"type\":\"fed_days\",\"days\":3},{\"type\":\"luxury\",\"id\":\"aliveworkplace:work_clothes\"}]"));
		out.put(AliveWorkplace.id("burgher"), withNeeds(real.get(AliveWorkplace.id("burgher")),
			"[{\"type\":\"fed_days\",\"days\":3},{\"type\":\"diet\",\"kind\":\"varied\"},{\"type\":\"luxury\",\"id\":\"aliveworkplace:fine_clothes\"}]"));
		out.put(AliveWorkplace.id("noble"), withNeeds(real.get(AliveWorkplace.id("noble")),
			"[{\"type\":\"fed_days\",\"days\":3},{\"type\":\"diet\",\"kind\":\"varied\"},{\"type\":\"luxury\",\"id\":\"aliveworkplace:noble_robes\"}]"));
		return out;
	}

	private static JsonElement withNeeds(JsonElement file, String needs) {
		JsonObject copy = file.getAsJsonObject().deepCopy();
		copy.add("needs", JsonParser.parseString(needs).getAsJsonArray());
		copy.add("wants", new JsonArray());
		return copy;
	}

	static Villager villager(GameTestHelper helper, int i, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, slot(i));
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	/** A villager with a chosen UUID (to set the seeding order). */
	static Villager villager(GameTestHelper helper, int i, String name, UUID id) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.setUUID(id);
		Vec3 p = helper.absoluteVec(Vec3.atBottomCenterOf(slot(i)));
		v.moveTo(p.x, p.y, p.z, 0, 0);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		helper.getLevel().addFreshEntity(v);
		return v;
	}

	/** Places on the floor (y = 2), clear of the hall. */
	private static BlockPos slot(int i) {
		int x = 1 + 2 * (i % 10);
		int z = 1 + 2 * (i / 10);
		return new BlockPos(x, 2, z);
	}

	static List<Villager> singles(GameTestHelper helper, int n) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(villager(helper, i, "Single" + i));
		}
		return out;
	}

	static void wellFed(ServerLevel level, Villager v, boolean varied) {
		ModAttachments.LAST_MEAL.set(v, level.getGameTime());
		ModAttachments.RECENT_MEALS.set(v, varied
			? List.of(ResourceLocation.withDefaultNamespace("bread"), ResourceLocation.withDefaultNamespace("baked_potato"), ResourceLocation.withDefaultNamespace("cooked_cod"))
			: List.of(ResourceLocation.withDefaultNamespace("bread")));
	}

	static void marry(Villager a, Villager b) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), 1, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), 1, true));
	}

	static int seedAll(ServerLevel level, VillageHallBlockEntity hall, long today) {
		int rounds = 0;
		while (hall.classesVersion() < ClassMigration.VERSION && rounds < 10) {
			ClassMigration.round(level, hall.getBlockPos(), hall, today);
			rounds++;
		}
		return rounds;
	}

	static long seededCount(List<Villager> who) {
		return who.stream().filter(ModAttachments.SOCIAL_CLASS::has).count();
	}

	static long classEntries(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CLASS).count();
	}

	static String snapshot(Villager v) {
		return cls(v) + " " + SocialClasses.progress(v) + " " + LifeStages.adultSince(v) + " " + FamilyBook.family(v);
	}

	/** A player who keeps every chat line they're shown, standing at {@code at}. */
	static ServerPlayer listener(GameTestHelper helper, List<Component> seen, Vec3 at, String name) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SPECTATOR);
		player.teleportTo(at.x, at.y, at.z);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	static long heardSeeding(List<Component> heard) {
		return heard.stream().filter(c -> c.getString().contains("found their places")).count();
	}

	/** The villagers, saved and loaded again (a server restart). */
	private static List<Villager> reload(ServerLevel level, List<Villager> who) {
		List<Villager> out = new ArrayList<>();
		for (Villager old : who) {
			CompoundTag tag = old.saveWithoutId(new CompoundTag());
			old.discard();
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(tag);
			level.addFreshEntity(copy);
			out.add(copy);
		}
		return out;
	}

	// --- 1. Sixteen a round, from both sides ---------------------------------------------------------------------------

	/**
	 * Exactly 16 residents (plus a mercenary, an inn guest and a caravan's party member) are all seeded by one round,
	 * which finishes: the ones passing through neither count toward the 16 nor hold the finish back, and the chronicle
	 * counts only residents.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaClassSixteen"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassSixteen")
	public void exactlySixteenFinishInOneRound(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = oldHall(helper, false);
		List<Villager> residents = singles(helper, 16);
		Villager mercenary = villager(helper, 20, "Sword");
		ModAttachments.MERCENARY_UNTIL.set(mercenary, level.getGameTime() + 100_000);
		Villager guest = villager(helper, 21, "Wanderer");
		ModAttachments.TRAVELLER.set(guest, new Traveller(level.getGameTime(), 3));
		Villager party = villager(helper, 22, "Caravan");
		party.addTag(CaravanSights.TAG);
		long today = Chronicle.day(level);

		ClassMigration.Round r = ClassMigration.round(level, hall.getBlockPos(), hall, today);
		helper.assertTrue(r != null && r.seeded() == 16 && r.finished(), "16 residents, one round: " + r);
		helper.assertTrue(seededCount(residents) == 16, "every resident seeded: " + seededCount(residents));
		helper.assertTrue(hall.classesVersion() == ClassMigration.VERSION && hall.classGraceUntil() == today + 7, "finished: " + hall.classesVersion());
		for (Villager skipped : List.of(mercenary, guest, party)) {
			helper.assertTrue(!ModAttachments.SOCIAL_CLASS.has(skipped) && LifeStages.adultSince(skipped) == null && FamilyBook.family(skipped) == 0,
				skipped.getName().getString() + " was seeded: " + cls(skipped));
		}
		String name = VillageHalls.name(level, hall.getBlockPos()).getString();
		List<String> lines = hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CLASS).map(e -> e.text().getString()).toList();
		helper.assertTrue(lines.equals(List.of("The people of " + name + " found their places: 16 peasants")), "the chronicle: " + lines);
		helper.succeed();
	}

	/** 17 residents: the first round seeds 16 and isn't finished; the second seeds exactly the one left and finishes. */
	//$ gametest_ticks_batch AREA '200' '"qaClassSeventeen"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassSeventeen")
	public void seventeenNeedASecondRoundOfOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = oldHall(helper, false);
		List<Villager> residents = singles(helper, 17);
		long today = Chronicle.day(level);
		ClassMigration.Round first = ClassMigration.round(level, hall.getBlockPos(), hall, today);
		helper.assertTrue(first != null && first.seeded() == 16 && !first.finished() && seededCount(residents) == 16, "first round: " + first);
		helper.assertTrue(hall.classesVersion() == 0 && classEntries(hall) == 0, "not finished after 16 of 17");
		ClassMigration.Round second = ClassMigration.round(level, hall.getBlockPos(), hall, today);
		helper.assertTrue(second != null && second.seeded() == 1 && second.finished() && seededCount(residents) == 17, "second round: " + second);
		helper.assertTrue(hall.classesVersion() == ClassMigration.VERSION && classEntries(hall) == 1, "finished once");
		helper.succeed();
	}

	/**
	 * 15 singles sorted first and a married couple last: the couple sits on the 16th place and is seeded together in the
	 * same round, into one class, never one partner a round apart from the other.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaClassCoupleAtTheEdge"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassCoupleAtTheEdge")
	public void aCoupleOnTheSixteenthPlaceIsSeededTogether(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = oldHall(helper, true);
		RandomSource random = RandomSource.create(3422L);
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < 17; i++) {
			ids.add(Mth.createInsecureUUID(random));
		}
		ids.sort(Comparator.naturalOrder());
		List<Villager> singles = new ArrayList<>();
		for (int i = 0; i < 15; i++) {
			singles.add(villager(helper, i, "Single" + i, ids.get(i)));
		}
		Villager wife = villager(helper, 15, "Dara", ids.get(15));
		Villager husband = villager(helper, 16, "Tomas", ids.get(16));
		marry(wife, husband);
		wellFed(level, wife, true); // she alone would be a Burgher, he alone a Peasant
		long today = Chronicle.day(level);
		ClassMigration.Round first = ClassMigration.round(level, hall.getBlockPos(), hall, today);
		helper.assertTrue(first != null && seededCount(singles) == 15, "the singles go first: " + first);
		helper.assertTrue(ModAttachments.SOCIAL_CLASS.has(wife) == ModAttachments.SOCIAL_CLASS.has(husband),
			"the couple was split across rounds: " + cls(wife) + " / " + cls(husband));
		seedAll(level, hall, today);
		helper.assertTrue(cls(wife).equals(cls(husband)) && !cls(wife).equals("none"), "one household, one class: " + cls(wife) + " / " + cls(husband));
		helper.succeed();
	}

	// --- 2. Who gets what ----------------------------------------------------------------------------------------------

	/**
	 * With every Noble need but luxuries holding, seeding gives Burgher at most; a fed villager without a varied diet is an
	 * Artisan, an unfed one a Peasant; a married couple share one class even when only one of them qualifies alone; their
	 * child takes the household's class and a child of nobody here lives at the floor.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaClassCeiling"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassCeiling")
	public void seedingNeverMakesANoble(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = oldHall(helper, true);
		Villager rich = villager(helper, 0, "Rich");
		wellFed(level, rich, true);
		Villager fed = villager(helper, 1, "Fed");
		wellFed(level, fed, false);
		Villager poor = villager(helper, 2, "Poor");
		Villager wife = villager(helper, 3, "Dara");
		Villager husband = villager(helper, 4, "Tomas");
		marry(wife, husband);
		wellFed(level, wife, true);
		wellFed(level, husband, true);
		Villager child = villager(helper, 5, "Mira");
		child.setBaby(true);
		child.setAge(-24000 * 10);
		ModAttachments.PARENTS.set(child, new Families.Parents(wife.getDisplayName(), husband.getDisplayName(), "", "", false, false, false,
			wife.getUUID(), husband.getUUID()));
		Villager orphan = villager(helper, 6, "Pip");
		orphan.setBaby(true);
		orphan.setAge(-24000 * 10);
		Villager mixedA = villager(helper, 7, "Ada");
		Villager mixedB = villager(helper, 8, "Bo");
		marry(mixedA, mixedB);
		wellFed(level, mixedA, true); // only one of the two fed

		long today = Chronicle.day(level);
		seedAll(level, hall, today);
		helper.assertTrue(hall.classesVersion() == ClassMigration.VERSION, "seeded");
		List<Villager> all = List.of(rich, fed, poor, wife, husband, child, orphan, mixedA, mixedB);
		helper.assertTrue(all.stream().noneMatch(v -> cls(v).equals("noble")), "a Noble at the switch: Nobles are earned");
		helper.assertTrue(cls(rich).equals("burgher"), "every non-luxury need of a Noble holds, so a Burgher: " + cls(rich));
		helper.assertTrue(cls(fed).equals("artisan"), "fed, a plain diet: " + cls(fed));
		helper.assertTrue(cls(poor).equals("peasant"), "unfed: " + cls(poor));
		helper.assertTrue(cls(wife).equals("burgher") && cls(husband).equals("burgher"), "the couple: " + cls(wife) + " / " + cls(husband));
		helper.assertTrue(cls(child).equals("burgher"), "the child takes the household's: " + cls(child));
		helper.assertTrue(cls(orphan).equals("peasant"), "a child of nobody here: " + cls(orphan));
		helper.assertTrue(cls(mixedA).equals(cls(mixedB)) && !cls(mixedA).equals("none"), "a married couple share one class: " + cls(mixedA) + " / " + cls(mixedB));
		helper.succeed();
	}

	// --- 3. The grace at its edges -------------------------------------------------------------------------------------

	/**
	 * Two Artisans lacking their Work Clothes, each one dawn from falling: on the last day of the grace (day 7) the first
	 * keeps his class; on the day after it (day 8) the second falls to Peasant.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaClassGraceEdge"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassGraceEdge")
	public void theLastGraceDayHoldsTheFallTheDayAfterDoesNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = oldHall(helper, true);
		Villager last = villager(helper, 0, "Last");
		Villager after = villager(helper, 1, "After");
		wellFed(level, last, false);
		wellFed(level, after, false);
		long today = Chronicle.day(level);
		seedAll(level, hall, today);
		helper.assertTrue(cls(last).equals("artisan") && cls(after).equals("artisan"), "both start Artisans: " + cls(last) + " " + cls(after));
		helper.assertTrue(ClassMigration.daysLeft(level, hall.getBlockPos(), today + 6) == 1 && ClassMigration.daysLeft(level, hall.getBlockPos(), today + 7) == 0,
			"7 days of grace: day 7 is today + 6");

		int fall = SocialClasses.FALL_DAYS;
		wellFed(level, last, false);
		ModAttachments.CLASS_PROGRESS.set(last, new SocialClasses.Progress(0, fall - 1, 10, today + 5));
		SocialClasses.check(level, hall.getBlockPos(), List.of(last), today + 6, 8);
		helper.assertTrue(cls(last).equals("artisan"), "fell on the last day of the grace: " + cls(last));

		wellFed(level, after, false);
		ModAttachments.CLASS_PROGRESS.set(after, new SocialClasses.Progress(0, fall - 1, 10, today + 6));
		SocialClasses.check(level, hall.getBlockPos(), List.of(after), today + 7, 8);
		helper.assertTrue(cls(after).equals("peasant"), "the day after the grace he should fall: " + cls(after));
		helper.succeed();
	}

	/** An elder farmer with a jobless son starts no retirement on the last day of the grace; the day after, he does. */
	//$ gametest_ticks_batch AREA '200' '"qaClassGraceRetire"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassGraceRetire")
	public void noRetiringOnTheLastGraceDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = oldHall(helper, false);
		Villager elder = villager(helper, 0, "Old Farmer");
		Villager son = villager(helper, 1, "Son");
		long realDay = Chronicle.day(level);
		seedAll(level, hall, realDay - 6); // seeded six days ago: today is the grace's last day
		helper.assertTrue(ClassMigration.daysLeft(level, hall.getBlockPos(), realDay) == 1, "today is the last day: "
			+ ClassMigration.daysLeft(level, hall.getBlockPos(), realDay));
		elder.setVillagerData(elder.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(3));
		LifeStages.setAdultSince(elder, realDay - 1380);
		ModAttachments.PARENTS.set(son, new Families.Parents(elder.getDisplayName(), Component.literal("Ilse"), "", "", true, false, false, elder.getUUID(), null));
		List<Villager> grown = List.of(elder, son);
		Retirement.round(level, hall.getBlockPos(), grown);
		helper.assertTrue(!ModAttachments.RETIRE_SHADOW.has(son), "the son started learning on the grace's last day");
		hall.setClasses(ClassMigration.VERSION, realDay); // a day on: the grace is over
		Retirement.round(level, hall.getBlockPos(), grown);
		helper.assertTrue(ModAttachments.RETIRE_SHADOW.has(son), "the day after the grace the elder's son starts learning");
		helper.succeed();
	}

	/**
	 * The hall's clock counts down in the en_us words: "Settling in: 5 days left" two days in, "Settling in: 1 day left" on
	 * the last day, and nothing the day after; the hall's button shows the same on the real day.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaClassSettlingText"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassSettlingText")
	public void theSettlingInClockCountsDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = oldHall(helper, false);
		singles(helper, 3);
		long realDay = Chronicle.day(level);
		seedAll(level, hall, realDay - 2);
		BlockPos at = hall.getBlockPos();
		helper.assertTrue(first(level, at, realDay).equals("Settling in: 5 days left"), "two days in: " + first(level, at, realDay));
		var button = ClassesPage.settlingButton(level, at);
		helper.assertTrue(button != null && button.getHoverName().getString().equals("Settling in: 5 days left"),
			"the hall's button: " + (button == null ? null : button.getHoverName().getString()));
		helper.assertTrue(first(level, at, realDay + 4).equals("Settling in: 1 day left"), "the last day: " + first(level, at, realDay + 4));
		helper.assertTrue(ClassMigration.settlingLines(level, at, realDay + 5).isEmpty(), "the day after the grace");
		helper.succeed();
	}

	private static String first(ServerLevel level, BlockPos hall, long day) {
		List<Component> lines = ClassMigration.settlingLines(level, hall, day);
		return lines.isEmpty() ? "(nothing)" : lines.get(0).getString();
	}

	/**
	 * {@code classGraceDays} 1: the chat line says "a day", the clock "1 day left" and the next day it's over.
	 * {@code classGraceDays} 0: the chat line has no grace, nothing settles in and the village is not held quiet.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaClassShortGrace"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassShortGrace")
	public void aOneDayAndANoDayGrace(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = oldHall(helper, false);
		List<Villager> residents = singles(helper, 2);
		List<Component> heard = new ArrayList<>();
		listener(helper, heard, Vec3.atCenterOf(hall.getBlockPos().offset(-3, 0, -3)), "qa-grace");
		BlockPos at = hall.getBlockPos();
		String name = VillageHalls.name(level, at).getString();
		long today = Chronicle.day(level);

		ClassMigration.GRACE_DAYS = 1;
		seedAll(level, hall, today);
		helper.assertTrue(first(level, at, today).equals("Settling in: 1 day left") && ClassMigration.settlingLines(level, at, today + 1).isEmpty(),
			"a one-day grace: " + first(level, at, today));
		List<String> said = heard.stream().map(Component::getString).filter(s -> s.contains("found their places")).toList();
		helper.assertTrue(said.equals(List.of("The people of " + name + " found their places: 2 peasants. They have a day to settle in: "
			+ "the Village Hall's Classes page says what each class will need.")), "one day: " + said);

		// The same village switched on again with no grace at all.
		heard.clear();
		residents.forEach(v -> ModAttachments.SOCIAL_CLASS.remove(v));
		hall.setClasses(0, 0);
		ClassMigration.GRACE_DAYS = 0;
		seedAll(level, hall, today);
		helper.assertTrue(hall.classesVersion() == ClassMigration.VERSION && ClassMigration.settlingLines(level, at, today).isEmpty()
			&& !ClassMigration.quiet(level, at, today), "no grace: nothing settles in");
		said = heard.stream().map(Component::getString).filter(s -> s.contains("found their places")).toList();
		helper.assertTrue(said.equals(List.of("The people of " + name + " found their places: 2 peasants.")), "no grace: " + said);
		helper.succeed();
	}

	// --- 4. Ages -------------------------------------------------------------------------------------------------------

	/**
	 * Over 500 villager-style UUIDs: every age is under the spread, the spread is under the elder age (so nobody is an
	 * elder, or nearing passing, for the 200 days after), ages are spread one or a few to a day, and the same UUID gets the
	 * same age. A whole village seeded on a later day counts the same days back for each villager.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaClassAges"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassAges")
	public void agesComeFromTheUuidAndMakeNoElder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(ClassMigration.AGE_SPREAD < LifeStages.ELDER_DAYS, "the spread reaches the elder age");
		RandomSource random = RandomSource.create(1822L);
		Map<Integer, Integer> perDay = new HashMap<>();
		for (int i = 0; i < 500; i++) {
			UUID id = Mth.createInsecureUUID(random);
			int days = ClassMigration.ageDays(id);
			helper.assertTrue(days >= 0 && days < ClassMigration.AGE_SPREAD, "age out of the spread: " + days);
			helper.assertTrue(ClassMigration.ageDays(new UUID(id.getMostSignificantBits(), id.getLeastSignificantBits())) == days, "the same UUID, another age");
			perDay.merge(days, 1, Integer::sum);
		}
		int most = perDay.values().stream().max(Integer::compare).orElse(0);
		helper.assertTrue(perDay.size() >= 300 && most <= 6, "ages bunch up: " + perDay.size() + " days, at most " + most + " on one");

		VillageHallBlockEntity hall = oldHall(helper, false);
		List<Villager> residents = singles(helper, 12);
		long today = Chronicle.day(level);
		seedAll(level, hall, today);
		Map<UUID, Long> grownFor = new HashMap<>();
		for (Villager v : residents) {
			Long since = LifeStages.adultSince(v);
			helper.assertTrue(since != null && today - since == ClassMigration.ageDays(v.getUUID()), v.getName().getString() + ": " + since);
			for (long day = today; day <= today + (LifeStages.ELDER_DAYS - ClassMigration.AGE_SPREAD); day += 50) {
				helper.assertTrue(!LifeStages.isElder(v, day) && LifeStages.daysLeft(v, day) == -1, v.getName().getString() + " is an elder on day " + day);
			}
			grownFor.put(v.getUUID(), today - since);
		}
		// The same village, first loaded 40 days later: the same days grown for everyone.
		for (Villager v : residents) {
			ModAttachments.SOCIAL_CLASS.remove(v);
			ModAttachments.ADULT_SINCE.remove(v);
		}
		hall.setClasses(0, 0);
		seedAll(level, hall, today + 40);
		for (Villager v : residents) {
			helper.assertTrue(today + 40 - LifeStages.adultSince(v) == grownFor.get(v.getUUID()), v.getName().getString() + " got another age");
		}
		helper.succeed();
	}

	// --- 5. A reload part-way, and a second full round ------------------------------------------------------------------

	/**
	 * After the first round of 20, the server restarts (hall and villagers through NBT) and one seeded Peasant has since got
	 * fed and a varied diet: the second round seeds only the four left (one of them, now fed and varied, a Burgher), keeps
	 * the seeded Peasant a Peasant with his age and family name, and a further round changes nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaClassReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassReload")
	public void aReloadPartWayReseedsOnlyTheRest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = oldHall(helper, true);
		List<Villager> residents = singles(helper, 20);
		long today = Chronicle.day(level);
		ClassMigration.Round first = ClassMigration.round(level, hall.getBlockPos(), hall, today);
		helper.assertTrue(first != null && first.seeded() == 16 && !first.finished(), "first round: " + first);
		Villager changed = residents.stream().filter(ModAttachments.SOCIAL_CLASS::has).findFirst().orElseThrow();
		Villager late = residents.stream().filter(v -> !ModAttachments.SOCIAL_CLASS.has(v)).findFirst().orElseThrow();
		helper.assertTrue(cls(changed).equals("peasant"), "unfed: " + cls(changed));
		wellFed(level, changed, true);
		wellFed(level, late, true);
		Map<UUID, String> before = new HashMap<>();
		residents.stream().filter(ModAttachments.SOCIAL_CLASS::has).forEach(v -> before.put(v.getUUID(), snapshot(v)));
		UUID changedId = changed.getUUID();
		UUID lateId = late.getUUID();

		hall.loadWithComponents(hall.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
		List<Villager> again = reload(level, residents);
		helper.assertTrue(hall.classesVersion() == 0 && seededCount(again) == 16, "the reload kept who was seeded: " + seededCount(again));
		ClassMigration.Round second = ClassMigration.round(level, hall.getBlockPos(), hall, today);
		helper.assertTrue(second != null && second.seeded() == 4 && second.finished(), "only the 4 left: " + second);
		for (Villager v : again) {
			if (before.containsKey(v.getUUID())) {
				helper.assertTrue(snapshot(v).equals(before.get(v.getUUID())), v.getName().getString() + " was seeded again: " + snapshot(v) + " was " + before.get(v.getUUID()));
			}
			if (v.getUUID().equals(changedId)) {
				helper.assertTrue(cls(v).equals("peasant"), "seeded before the restart, kept his class: " + cls(v));
			}
			if (v.getUUID().equals(lateId)) {
				helper.assertTrue(cls(v).equals("burgher"), "seeded after the restart, as he is now: " + cls(v));
			}
		}
		Map<UUID, String> done = new HashMap<>();
		again.forEach(v -> done.put(v.getUUID(), snapshot(v)));
		long grace = hall.classGraceUntil();
		helper.assertTrue(ClassMigration.round(level, hall.getBlockPos(), hall, today) == null
			&& ClassMigration.round(level, hall.getBlockPos(), hall, today + 1) == null, "a later round did something");
		again.forEach(v -> helper.assertTrue(snapshot(v).equals(done.get(v.getUUID())), v.getName().getString() + " changed in a later round"));
		helper.assertTrue(classEntries(hall) == 1 && hall.classGraceUntil() == grace, "told once, the same grace");
		helper.succeed();
	}

	// --- 6. Who hears it ------------------------------------------------------------------------------------------------

	/**
	 * One chat line, once, to the player just inside the village (15 blocks out); none to the one just outside (19
	 * blocks); later rounds and a newcomer's seeding add no line and no chronicle entry.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaClassHeard"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassHeard")
	public void onlyPlayersInTheVillageHearItOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = oldHall(helper, false);
		singles(helper, 18);
		List<Component> inside = new ArrayList<>();
		List<Component> outside = new ArrayList<>();
		BlockPos at = hall.getBlockPos();
		listener(helper, inside, Vec3.atBottomCenterOf(at.offset(VillageHalls.RADIUS - 1, 0, 0)), "qa-inside");
		listener(helper, outside, Vec3.atBottomCenterOf(at.offset(VillageHalls.RADIUS + 3, 0, 0)), "qa-outside");
		long today = Chronicle.day(level);
		seedAll(level, hall, today);
		helper.assertTrue(heardSeeding(inside) == 1, "the player in the village: " + heardSeeding(inside));
		helper.assertTrue(heardSeeding(outside) == 0, "the player outside the village heard it");
		Villager newcomer = villager(helper, 19, "Newcomer");
		ClassMigration.round(level, at, hall, today + 1);
		ClassMigration.round(level, at, hall, today + 2);
		helper.assertTrue(cls(newcomer).equals("peasant"), "the newcomer: " + cls(newcomer));
		helper.assertTrue(heardSeeding(inside) == 1 && classEntries(hall) == 1, "told again: " + heardSeeding(inside) + " lines, " + classEntries(hall) + " entries");
		helper.succeed();
	}

	// --- 7. The treasury -----------------------------------------------------------------------------------------------

	/**
	 * Workers of every seeded class (and jobless ones): after seeding, no worker's share is below what he paid before
	 * (1, a Peasant's today), and the village's takings are at least what they were.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaClassTreasury"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassTreasury")
	public void theTreasuryNeverTakesLess(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = oldHall(helper, true);
		List<Villager> workers = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			Villager w = villager(helper, i, "Worker" + i);
			w.setVillagerData(w.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
			if (i % 3 == 1) {
				wellFed(level, w, false);
			} else if (i % 3 == 2) {
				wellFed(level, w, true);
			}
			workers.add(w);
		}
		List<Villager> jobless = new ArrayList<Villager>();
		for (int i = 6; i < 10; i++) {
			Villager j = villager(helper, i, "Idle" + i);
			if (i % 2 == 0) {
				wellFed(level, j, true);
			}
			jobless.add(j);
		}
		Map<UUID, Float> sharesBefore = new HashMap<>();
		workers.forEach(w -> sharesBefore.put(w.getUUID(), ClassPerks.share(w, ClassPerks.classOf(w))));
		float before = ClassPerks.tax(workers, jobless).shares();
		seedAll(level, hall, Chronicle.day(level));
		Set<String> classes = new HashSet<>();
		workers.forEach(w -> classes.add(cls(w)));
		helper.assertTrue(classes.containsAll(Set.of("peasant", "artisan", "burgher")), "workers of every seeded class: " + classes);
		for (Villager w : workers) {
			float now = ClassPerks.share(w, ClassPerks.classOf(w));
			helper.assertTrue(now >= sharesBefore.get(w.getUUID()), w.getName().getString() + " (" + cls(w) + ") pays less: "
				+ sharesBefore.get(w.getUUID()) + " to " + now);
		}
		float after = ClassPerks.tax(workers, jobless).shares();
		helper.assertTrue(after >= before, "the takings dropped: " + before + " to " + after);
		helper.succeed();
	}

	// --- 8. Switched off part-way ---------------------------------------------------------------------------------------

	/**
	 * {@code villageClasses} off after the first of two rounds: rounds do nothing and keep nothing held back, the 16 seeded
	 * keep everything; on again, the next round seeds only the 4 left and finishes with one chronicle entry for all 20.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaClassOffPartWay"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassOffPartWay")
	public void switchedOffPartWayLosesNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = oldHall(helper, false);
		List<Villager> residents = singles(helper, 20);
		long today = Chronicle.day(level);
		ClassMigration.round(level, hall.getBlockPos(), hall, today);
		Map<UUID, String> firstSixteen = new HashMap<>();
		residents.stream().filter(ModAttachments.SOCIAL_CLASS::has).forEach(v -> firstSixteen.put(v.getUUID(), snapshot(v)));
		helper.assertTrue(firstSixteen.size() == 16, "first round: " + firstSixteen.size());

		SocialClasses.ENABLED = false;
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(ClassMigration.round(level, hall.getBlockPos(), hall, today + i) == null, "a round with classes off did something");
		}
		helper.assertTrue(seededCount(residents) == 16 && hall.classesVersion() == 0 && !ClassMigration.seeding(level, hall.getBlockPos()),
			"classes off: " + seededCount(residents) + " seeded, version " + hall.classesVersion());
		residents.stream().filter(v -> firstSixteen.containsKey(v.getUUID()))
			.forEach(v -> helper.assertTrue(snapshot(v).equals(firstSixteen.get(v.getUUID())), v.getName().getString() + " lost something while off"));

		SocialClasses.ENABLED = true;
		ClassMigration.Round r = ClassMigration.round(level, hall.getBlockPos(), hall, today + 3);
		helper.assertTrue(r != null && r.seeded() == 4 && r.finished(), "on again: " + r);
		residents.stream().filter(v -> firstSixteen.containsKey(v.getUUID()))
			.forEach(v -> helper.assertTrue(snapshot(v).equals(firstSixteen.get(v.getUUID())), v.getName().getString() + " was seeded twice"));
		String name = VillageHalls.name(level, hall.getBlockPos()).getString();
		List<String> lines = hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CLASS).map(e -> e.text().getString()).toList();
		helper.assertTrue(lines.equals(List.of("The people of " + name + " found their places: 20 peasants")), "the chronicle: " + lines);
		helper.assertTrue(hall.classGraceUntil() == today + 3 + 7, "the grace starts when it finishes: " + hall.classGraceUntil());
		helper.succeed();
	}
}
