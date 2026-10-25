package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Gifted;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Mourning;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane c, 2026-10-25), written from ROADMAP 34.19, 34.19a, 34.20 and 34.21 at the gaps the builder's tests leave:
 * the charm's refusals and acceptance as the player sees them on the action bar through the real sneak-right-click,
 * at the elder boundary (1199 vs 1200 grown days), one charm one villager from a stack of two; an ageless Master still
 * retiring and mentoring an Apprentice (not only a Novice) at 50%; nobody retiring a day short of elder, a jobless baby
 * never a successor; an elder whose time has come not passing while awake by day and the village's word of a passing
 * (with mourning off) in both wordings; the Book of Families at exactly 400 from both sides; the 60 family names; and the
 * rules quoted in the config tooltips matching the game's own numbers.
 */
public class QaElderLifeGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation PRODIGY = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "prodigy");
	private static final ResourceLocation OLD_SAGE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "old_sage");
	private static final String BADGE = "❦ Ageless: will never leave us";

	/**
	 * Through the player's own sneak-right-click with a stack of two charms: a Gifted villager one day short of elder is
	 * refused with the days on the action bar; the next day she takes one; a second click on her is refused; a plain
	 * Journeyman elder is refused with the reason; a Legend takes the second charm. Two charms, two villagers.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaElderCharmClicks"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaElderCharmClicks")
	public void qaTheCharmByClickAtTheElderBoundaryOneCharmOneVillager(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager ida = villager(helper, new BlockPos(5, 2, 6), "Ida", VillagerProfession.LIBRARIAN, 2);
		Villager bram = villager(helper, new BlockPos(7, 2, 6), "Bram", VillagerProfession.FARMER, 3);
		Villager odo = villager(helper, new BlockPos(9, 2, 6), "Odo", VillagerProfession.MASON, 1);
		List<Component> bar = new ArrayList<>();
		ServerPlayer player = listener(helper, bar, new ArrayList<>());
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(ida, today - (LifeStages.ELDER_DAYS - 1));
			LifeStages.setAdultSince(bram, today - 1250);
			LifeStages.setAdultSince(odo, today - 1300);
			Gifted.set(ida, PRODIGY);
			helper.assertTrue(Gifted.of(ida) != null, "the test's gift didn't load");
			ModAttachments.LEGEND.set(odo, LegendData.settled(OLD_SAGE, "", Optional.of(hallPos), today - 1, "test"));
			player.setShiftKeyDown(true);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.EVERGREEN_CHARM, 2));

			click(player, ida);
			heard(helper, bar, "Ida is not an elder yet (grown 1199 of 1200 days): the Evergreen Charm is for elders.");
			helper.assertTrue(player.getMainHandItem().getCount() == 2 && !LifeStages.isAgeless(ida), "refused a day short, yet: " + player.getMainHandItem());

			LifeStages.setAdultSince(ida, today - LifeStages.ELDER_DAYS);
			click(player, ida);
			heard(helper, bar, "Ida (Gifted) takes the Evergreen Charm and will never leave us.");
			helper.assertTrue(LifeStages.isAgeless(ida) && player.getMainHandItem().getCount() == 1, "the day she's an elder: " + player.getMainHandItem());

			click(player, ida);
			heard(helper, bar, "Ida is already ageless.");
			helper.assertTrue(player.getMainHandItem().getCount() == 1, "a second charm on one villager was used: " + player.getMainHandItem());

			click(player, bram);
			heard(helper, bar, "Bram won't take it. The charm is for an elder who is a Master of their trade, Gifted, a Legend, or happy (mood 80 or more) "
				+ "for 20 days running. Happy days so far: 0.");
			helper.assertTrue(player.getMainHandItem().getCount() == 1 && !LifeStages.isAgeless(bram), "a Journeyman elder took it");

			click(player, odo);
			heard(helper, bar, "Odo (a Legend) takes the Evergreen Charm and will never leave us.");
			helper.assertTrue(player.getMainHandItem().isEmpty() && LifeStages.isAgeless(odo), "the Legend: " + player.getMainHandItem());
			helper.assertTrue(lore(level, hallPos, ida).contains(BADGE) && lore(level, hallPos, odo).contains(BADGE) && !lore(level, hallPos, bram).contains(BADGE),
				"the badges: " + lore(level, hallPos, bram));
			long lines = chronicle(hall).stream().filter(l -> l.contains(" will never leave us")).count();
			helper.assertTrue(lines == 2, "chronicle lines for two charms: " + chronicle(hall));
			helper.succeed();
		});
	}

	/** An ageless Master still retires (34.19a): hands over after two days, stays ageless, and mentors an Apprentice of the trade, not a Journeyman. */
	//$ gametest_ticks_batch AREA '100' '"qaElderAgelessRetires"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaElderAgelessRetires")
	public void qaAnAgelessMasterRetiresAndMentorsAnApprentice(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		BlockPos composter = new BlockPos(3, 2, 3);
		helper.setBlock(composter, Blocks.COMPOSTER);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.NONE, 1);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.NONE, 1);
		Villager eli = villager(helper, new BlockPos(9, 2, 6), "Eli", VillagerProfession.FARMER, 2);
		Villager ann = villager(helper, new BlockPos(13, 2, 6), "Ann", VillagerProfession.FARMER, 3);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			Jobs.employ(level, bram, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
			bram.setVillagerData(bram.getVillagerData().setLevel(5));
			bram.setVillagerXp(250);
			long clock = level.getDayTime();
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1250);
			for (Villager v : List.of(tom, eli, ann)) {
				LifeStages.setAdultSince(v, today - 20);
			}
			helper.assertTrue(LifeStages.makeAgeless(level, bram, Component.literal("Tester")).outcome() == LifeStages.Outcome.ACCEPTED, "Bram takes the charm");
			try {
				Retirement.round(level, hallPos, List.of(bram, tom));
				Retirement.Shadow shadow = Retirement.shadow(tom);
				helper.assertTrue(shadow != null && shadow.elder().equals(bram.getUUID()), "an ageless elder doesn't start retiring: " + shadow);
				level.setDayTime(clock + 2 * VillageNeeds.DAY);
				Retirement.round(level, hallPos, List.of(bram, tom));
				helper.assertTrue(Retirement.isRetired(bram) && tom.getVillagerData().getProfession() == VillagerProfession.FARMER
					&& tom.getVillagerData().getLevel() == 4, "the hand-over from an ageless Master: " + tom.getVillagerData());
				long day = Chronicle.day(level);
				helper.assertTrue(LifeStages.isAgeless(bram) && LifeStages.isElder(bram, day) && LifeStages.daysLeft(bram, day + 1000) == -1, "Bram lost his charm retiring");
				helper.assertTrue(LifeStages.walk(bram) == LifeStages.ELDER_WALK, "a retired ageless elder's walk: " + LifeStages.walk(bram));

				// Mentoring: the Apprentice of his old trade, not the Journeyman, nor Tom (an Expert now).
				Retirement.round(level, hallPos, List.of(bram, tom, eli, ann));
				Retirement.Teacher teacher = Retirement.teacher(eli);
				helper.assertTrue(teacher != null && teacher.mentor().equals(bram.getUUID()), "the Apprentice isn't taught: " + teacher);
				helper.assertTrue(Retirement.teacher(ann) == null && Retirement.teacher(tom) == null, "a Journeyman or Expert taught");
				helper.assertTrue(Retirement.extraXp(eli, 10, RandomSource.create(7)) == 5, "the Apprentice's 50%: " + Retirement.extraXp(eli, 10, RandomSource.create(7)));
				helper.assertTrue(Retirement.extraXp(ann, 10, RandomSource.create(7)) == 0, "the Journeyman's extra");
				helper.assertTrue(lore(level, hallPos, eli).contains("taught by Bram"), "Eli's card: " + lore(level, hallPos, eli));
				List<String> card = lore(level, hallPos, bram);
				helper.assertTrue(card.contains("Retired Farmer · mentors the young") && card.contains(BADGE), "Bram's card: " + card);
			} finally {
				level.setDayTime(clock);
			}
			helper.succeed();
		});
	}

	/**
	 * A day short of elder, a worker never retires and the hall says nothing; from the day they're an elder, a jobless
	 * baby (even their own) is never the successor and the hall says the village needs someone; a grown jobless villager
	 * then takes over and the line goes.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaElderRetireBoundary"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaElderRetireBoundary")
	public void qaOnlyAnElderRetiresAndNeverToABaby(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FLETCHER, 3);
		Villager pip = villager(helper, new BlockPos(7, 2, 6), "Pip", VillagerProfession.NONE, 1);
		pip.setBaby(true);
		Villager tom = villager(helper, new BlockPos(9, 2, 6), "Tom", VillagerProfession.NONE, 1);
		String waiting = "Bram would like to retire: the village needs a jobless villager to take over";
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long today = Chronicle.day(level);
			ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Ann"), Component.literal("Bram"), "", "minecraft:fletcher", false,
				false, false, null, bram.getUUID()));
			LifeStages.setAdultSince(bram, today - (LifeStages.ELDER_DAYS - 1));
			LifeStages.setAdultSince(tom, today - 20);
			Retirement.round(level, hallPos, List.of(bram, tom));
			helper.assertTrue(Retirement.shadow(tom) == null, "a worker a day short of elder started retiring");
			helper.assertTrue(!lore(level, hallPos, bram).contains(waiting), "a day short of elder: " + lore(level, hallPos, bram));

			LifeStages.setAdultSince(bram, today - LifeStages.ELDER_DAYS);
			Retirement.round(level, hallPos, List.of(bram, pip));
			helper.assertTrue(Retirement.shadow(pip) == null && pip.getVillagerData().getProfession() == VillagerProfession.NONE, "a baby is learning the trade");
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.FLETCHER && !Retirement.isRetired(bram), "Bram retired to a baby");
			helper.assertTrue(lore(level, hallPos, bram).contains(waiting), "only a baby jobless, the hall: " + lore(level, hallPos, bram));

			Retirement.round(level, hallPos, List.of(bram, pip, tom));
			helper.assertTrue(Retirement.shadow(tom) != null && Retirement.shadow(tom).elder().equals(bram.getUUID()), "Tom isn't taking over");
			helper.assertTrue(!lore(level, hallPos, bram).contains(waiting), "the hall's line stayed: " + lore(level, hallPos, bram));
			helper.succeed();
		});
	}

	/**
	 * An elder whose time has come stays alive awake at noon and passes once asleep, leaving a grave; with the village's
	 * mourning off, players near the hall are told, in the wording for charms on and for charms off.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaElderPassingWord"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaElderPassingWord")
	public void qaAnElderPassesOnlyAsleepAndTheVillageIsTold(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		boolean mourning = Mourning.ENABLED;
		Leftovers.after(helper, () -> Mourning.ENABLED = mourning);
		BlockPos[] beds = {new BlockPos(5, 2, 4), new BlockPos(9, 2, 4)};
		for (BlockPos bed : beds) {
			helper.setBlock(bed, Blocks.RED_BED);
		}
		Villager dara = villager(helper, new BlockPos(5, 2, 6), "Dara", VillagerProfession.FARMER, 5);
		Villager tom = villager(helper, new BlockPos(9, 2, 6), "Tom", VillagerProfession.FARMER, 5);
		List<Component> chat = new ArrayList<>();
		ServerPlayer player = listener(helper, new ArrayList<>(), chat);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			BlockPos near = hallPos.south(2);
			player.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
			long clock = level.getDayTime();
			try {
				level.setDayTime(clock - Math.floorMod(clock, VillageNeeds.DAY) + 6000); // noon, the same day
				long today = Chronicle.day(level);
				for (Villager v : List.of(dara, tom)) {
					LifeStages.setAdultSince(v, today - LifeStages.ELDER_DAYS - LifeStages.PASSING_DAYS);
				}
				Mourning.ENABLED = false;
				helper.assertTrue(LifeStages.daysLeft(dara, today) == 0, "Dara's time hasn't come: " + LifeStages.daysLeft(dara, today));
				LifeStages.round(level, hallPos, List.of(dara, tom));
				helper.assertTrue(dara.isAlive() && tom.isAlive(), "an elder passed awake at noon");

				dara.startSleeping(helper.absolutePos(beds[0]));
				LifeStages.round(level, hallPos, List.of(dara));
				helper.assertTrue(!dara.isAlive(), "Dara didn't pass asleep");
				heard(helper, chat, "Dara, an elder, passed away in the night. An Undertaker can bring them back, and an Evergreen Charm keeps an elder for good.");

				LifeStages.AGELESS = false;
				tom.startSleeping(helper.absolutePos(beds[1]));
				LifeStages.round(level, hallPos, List.of(tom));
				helper.assertTrue(!tom.isAlive(), "Tom didn't pass asleep");
				heard(helper, chat, "Tom, an elder, passed away in the night.");
				List<String> graves = graves(helper).stream().map(g -> g.name().getString()).sorted().toList();
				helper.assertTrue(graves.equals(List.of("Dara", "Tom")), "the graves: " + graves);
			} finally {
				level.setDayTime(clock);
			}
			helper.succeed();
		});
	}

	/**
	 * The Book of Families at exactly 400 forgets nobody, and writing someone in again (a death dated) forgets nobody;
	 * the 401st forgets only the longest dead, never the more recently dead or the living.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheBookAtExactlyFourHundred(GameTestHelper helper) {
		FamilyBook.Book book = new FamilyBook.Book();
		RandomSource random = RandomSource.create(34021);
		List<UUID> ids = new ArrayList<>();
		for (int n = 0; n < FamilyBook.MAX - 1; n++) {
			ids.add(id(random));
			book.put(person(ids.get(n), "L" + n, n, -1));
		}
		UUID early = id(random);
		book.put(person(early, "Early", 0, 5));
		helper.assertTrue(book.size() == 400 && book.get(early) != null && ids.stream().allMatch(i -> book.get(i) != null), "at 400 someone was forgotten: " + book.size());
		// Two of the living die (written in again, dated): still 400.
		UUID late = ids.get(10);
		book.put(person(late, "L10", 10, 90));
		UUID middle = ids.get(20);
		book.put(person(middle, "L20", 20, 50));
		helper.assertTrue(book.size() == 400 && book.get(late).dead() && book.get(middle).dead(), "a death dated changed the book's size: " + book.size());
		// The 401st: Early (dead since day 5) goes; the others dead later stay.
		UUID newcomer = id(random);
		book.put(person(newcomer, "New", 500, -1));
		helper.assertTrue(book.size() == 400 && book.get(early) == null, "the longest dead kept: " + book.size());
		helper.assertTrue(book.get(newcomer) != null && book.get(late) != null && book.get(middle) != null, "the wrong one was forgotten");
		// Another: the one dead since day 50 next, not the one dead since day 90.
		UUID another = id(random);
		book.put(person(another, "Another", 501, -1));
		helper.assertTrue(book.size() == 400 && book.get(middle) == null && book.get(late) != null && book.get(another) != null, "after the 402nd");
		helper.assertTrue(ids.stream().filter(i -> !i.equals(late) && !i.equals(middle)).allMatch(i -> book.get(i) != null), "someone living was forgotten");
		helper.succeed();
	}

	/**
	 * Sixty family names, each its own, none missing; every founded family is one of them, and they spread; and the rules
	 * the config's tooltips and the charm's refusal quote are the game's own numbers.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaSixtyFamilyNamesAndTheRulesAsTold(GameTestHelper helper) {
		helper.assertTrue(FamilyBook.NAMES_COUNT == 60, "family names: " + FamilyBook.NAMES_COUNT);
		Set<String> names = new HashSet<>();
		for (int n = 1; n <= FamilyBook.NAMES_COUNT; n++) {
			String name = FamilyBook.familyName(n).getString();
			helper.assertTrue(!name.startsWith("family_name.") && !name.isBlank() && !name.contains(" "), "family name " + n + ": " + name);
			helper.assertTrue(names.add(name), "a family name twice: " + name);
		}
		RandomSource random = RandomSource.create(60);
		Set<Integer> founded = new HashSet<>();
		for (int i = 0; i < 2000; i++) {
			int f = FamilyBook.founded(id(random));
			helper.assertTrue(f >= 1 && f <= FamilyBook.NAMES_COUNT, "a family out of the list: " + f);
			founded.add(f);
		}
		helper.assertTrue(founded.size() == FamilyBook.NAMES_COUNT, "2000 founders use only " + founded.size() + " of the 60 names");

		String passing = Component.translatable("aliveworkplace.config.elderPassing.tooltip").getString();
		helper.assertTrue(passing.contains("after " + LifeStages.PASSING_DAYS + " elder days"), "elderPassing's tooltip: " + passing);
		String ageless = Component.translatable("aliveworkplace.config.agelessElders.tooltip").getString();
		helper.assertTrue(ageless.contains("happy for " + LifeStages.HAPPY_DAYS + " days"), "agelessElders' tooltip: " + ageless);
		String retire = Component.translatable("aliveworkplace.config.elderRetirement.tooltip").getString();
		helper.assertTrue(retire.contains("every " + Retirement.EVERY_DAYS + " days") && retire.contains("for " + Retirement.SHADOW_DAYS + " days")
			&& retire.contains(Math.round(Retirement.MENTOR_BONUS * 100) + "% faster") && Retirement.EVERY_DAYS == 3 && Retirement.SHADOW_DAYS == 2
			&& Retirement.MENTOR_BONUS == 0.5f, "elderRetirement's tooltip: " + retire);
		helper.assertTrue(LifeStages.HAPPY_MOOD == 80 && LifeStages.HAPPY_DAYS == 20 && LifeStages.PASSING_DAYS == 400, "the spec's numbers");
		helper.succeed();
	}

	// --- Helpers ----------------------------------------------------------------------------------------------------

	private static UUID id(RandomSource random) {
		return new UUID(random.nextLong(), random.nextLong());
	}

	private static FamilyBook.Person person(UUID id, String name, long born, long died) {
		return new FamilyBook.Person(id, Component.literal(name), 1, born, false, died, null, null, null, "minecraft:farmer");
	}

	/** The latest line {@code seen} holds is {@code text}. */
	private static void heard(GameTestHelper helper, List<Component> seen, String text) {
		helper.assertTrue(!seen.isEmpty() && seen.get(seen.size() - 1).getString().equals(text),
			"expected \"" + text + "\", saw " + seen.stream().map(Component::getString).toList());
	}

	/** A right-click as the game sends it: the main hand's turn, then the off hand's if the first passed. */
	private static void click(ServerPlayer player, Villager villager) {
		net.minecraft.world.InteractionResult r = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
		if (r == net.minecraft.world.InteractionResult.PASS) {
			UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.OFF_HAND, villager, null);
		}
	}

	/** A survival player who keeps every action-bar line ({@code bar}) and chat line ({@code chat}) they're shown. */
	private static ServerPlayer listener(GameTestHelper helper, List<Component> bar, List<Component> chat) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qa-elder-listener"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				(overlay ? bar : chat).add(message);
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	/** A Village Hall (radius 16 for the test); the life-stage and retirement switches are on again when the test ends. */
	static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			LifeStages.AGELESS = true;
			LifeStages.PASSING = true;
			LifeStages.AGES = true;
			LifeStages.ELDER_DAYS = 1200;
			Retirement.ENABLED = true;
			VillageNeeds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		if (job != VillagerProfession.NONE) {
			v.setVillagerXp(1);
		}
		return v;
	}

	static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		return hall;
	}

	static List<String> chronicle(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	static List<String> lore(ServerLevel level, BlockPos hall, Villager villager) {
		ItemLore lore = VillageHallScreen.person(level, hall, villager).get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static List<GraveBlockEntity> graves(GameTestHelper helper) {
		List<GraveBlockEntity> found = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(22, 6, 22)))) {
			if (helper.getLevel().getBlockEntity(p) instanceof GraveBlockEntity grave) {
				found.add(grave);
			}
		}
		return found;
	}
}
