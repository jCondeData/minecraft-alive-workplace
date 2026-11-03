package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.ClassMigration;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane c, 2026-11-03), verifying three fixed player-text bugs from their specs, where the finders' tests stop:
 * <ul>
 * <li>B862: {@code /workplace classes reseed} and {@code status} through the server's own command dispatcher in a small
 * village (the finder's test calls the line builders only): one villager cleared reads "1 villager lost their class and is
 * given one" even when the village has two (the count is who was cleared, not who lives there), two read plural; the status
 * reads "1 of 1 villager has", "0 of 1 villager has", "1 of 2 villagers has" and "2 of 2 villagers have".</li>
 * <li>B866: the Bandit King's tribute without CobbleDollars, both the chronicle and chat, as literal text (the finder's
 * bug test compares with {@code TradePage.money} itself): exactly 1.00 taken from a treasury that isn't a multiple of
 * three (3.02) reads "1 emerald"; 0.99, 1.99, 1.01, 1.1 and 0.01 read as decimals in the plural.</li>
 * <li>B853: the blessed founding line with two settlers stays plural (the finder covers one blessed and two unblessed).</li>
 * </ul>
 */
public class QaWordsVerify1103cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String COLONY = "aliveworkplace_test:colony_area";
	private static final BlockPos HALL = new BlockPos(4, 2, 4);

	// ---- B862: the operators' classes command, run through the dispatcher -------------------------------------------

	/**
	 * A small village round a hall named Thornholm, classes on with the real class files, {@code classed} of the
	 * {@code total} villagers with a class; after the hall's POI registers, {@code check} gets a runner of the command.
	 */
	private static void village(GameTestHelper helper, int total, int classed, boolean seeding, Consumer<Command> check) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean enabled = SocialClasses.ENABLED;
		boolean auto = ClassMigration.AUTO;
		VillageHalls.RADIUS = 8;
		ClassMigration.AUTO = false; // the hall's own round never seeds anyone during the test
		SocialClasses.ENABLED = false; // until the POI is in (the command finds the hall by it)
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		SocialClasses.forget();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			ClassMigration.AUTO = auto;
			SocialClasses.ENABLED = enabled;
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
		});
		helper.assertTrue(!SocialClasses.ladder().isEmpty(), "setup: no class files loaded");
		ResourceLocation cls = SocialClasses.ladder().get(0).id();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setCustomName(Component.literal("Thornholm"));
		entity.setClasses(seeding ? 0 : ClassMigration.VERSION, 0);
		List<Villager> villagers = new ArrayList<>();
		for (int i = 0; i < total; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2 + 2 * i, 2, 2));
			v.setNoAi(true);
			if (i < classed) {
				ModAttachments.SOCIAL_CLASS.set(v, cls);
			}
			villagers.add(v);
		}
		helper.runAfterDelay(2, () -> {
			SocialClasses.ENABLED = true;
			helper.assertValueEqual(VillageHalls.villagers(level, hall).size(), total, "setup: the village's residents");
			check.accept(new Command(helper, level, hall, villagers));
			helper.succeed();
		});
	}

	/** Runs {@code /workplace classes <what>} as an operator standing by the hall and returns what it said. */
	private record Command(GameTestHelper helper, ServerLevel level, BlockPos hall, List<Villager> villagers) {
		List<String> run(String what) {
			List<String> said = new ArrayList<>();
			CommandSource out = new CommandSource() {
				@Override
				public void sendSystemMessage(Component message) {
					said.add(message.getString());
				}

				@Override
				public boolean acceptsSuccess() {
					return true;
				}

				@Override
				public boolean acceptsFailure() {
					return true;
				}

				@Override
				public boolean shouldInformAdmins() {
					return false;
				}
			};
			CommandSourceStack source = new CommandSourceStack(out, Vec3.atCenterOf(hall.south(2)), Vec2.ZERO, level, 2, "qa", Component.literal("qa"),
				level.getServer(), null);
			level.getServer().getCommands().performPrefixedCommand(source, "workplace classes " + what);
			return said;
		}
	}

	private static void says(GameTestHelper helper, List<String> said, String expected, String what) {
		helper.assertTrue(said.contains(expected), what + ": want '" + expected + "', the command said " + said);
	}

	private static void classesCleared(Command c) {
		c.helper().assertTrue(c.villagers().stream().noneMatch(ModAttachments.SOCIAL_CLASS::has), "the reseed left a villager with a class");
	}

	/** Two villagers, one of them with a class: the reseed clears one and says so in the singular. */
	//$ gametest_ticks_batch AREA '100' '"qaWords1103cReseedOne"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWords1103cReseedOne")
	public void qaAReseedThatClearsOneVillagerReadsSingular(GameTestHelper helper) {
		village(helper, 2, 1, false, c -> {
			List<String> said = c.run("reseed");
			says(helper, said, "Thornholm will find its places again: 1 villager lost their class and is given one over the next rounds.", "reseed of one");
			helper.assertTrue(said.stream().noneMatch(s -> s.contains("1 villagers") || s.contains("are given one")), "plural words for one: " + said);
			classesCleared(c);
		});
	}

	/** A lone villager with a class in a one-villager village: the reseed reads singular, and a second reseed reads "0 villagers". */
	//$ gametest_ticks_batch AREA '100' '"qaWords1103cReseedLone"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWords1103cReseedLone")
	public void qaAReseedOfALoneVillagerReadsSingularThenZeroPlural(GameTestHelper helper) {
		village(helper, 1, 1, false, c -> {
			says(helper, c.run("reseed"), "Thornholm will find its places again: 1 villager lost their class and is given one over the next rounds.", "reseed of the one");
			classesCleared(c);
			says(helper, c.run("reseed"), "Thornholm will find its places again: 0 villagers lost their class and are given one over the next rounds.",
				"a second reseed, nobody left with a class");
		});
	}

	/** Two villagers with a class: the reseed stays plural. */
	//$ gametest_ticks_batch AREA '100' '"qaWords1103cReseedTwo"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWords1103cReseedTwo")
	public void qaAReseedThatClearsTwoVillagersReadsPlural(GameTestHelper helper) {
		village(helper, 2, 2, false, c -> {
			says(helper, c.run("reseed"), "Thornholm will find its places again: 2 villagers lost their class and are given one over the next rounds.", "reseed of two");
			classesCleared(c);
		});
	}

	/** A one-villager village being seeded: "1 of 1 villager has a class", and after a reseed "0 of 1 villager has". */
	//$ gametest_ticks_batch AREA '100' '"qaWords1103cStatusOne"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWords1103cStatusOne")
	public void qaTheStatusOfAOneVillagerVillageReadsSingular(GameTestHelper helper) {
		village(helper, 1, 1, true, c -> {
			List<String> said = c.run("status");
			says(helper, said, "Thornholm is finding its places: 1 of 1 villager has a class so far.", "status, 1 of 1");
			helper.assertTrue(said.stream().noneMatch(s -> s.contains("villagers") || s.contains("have a class")), "plural words for 1 of 1: " + said);
			c.run("reseed");
			says(helper, c.run("status"), "Thornholm is finding its places: 0 of 1 villager has a class so far.", "status after the reseed, 0 of 1");
		});
	}

	/** A two-villager village being seeded: "1 of 2 villagers has a class", then with both "2 of 2 villagers have a class". */
	//$ gametest_ticks_batch AREA '100' '"qaWords1103cStatusTwo"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWords1103cStatusTwo")
	public void qaTheStatusOfATwoVillagerVillageReadsPlural(GameTestHelper helper) {
		village(helper, 2, 1, true, c -> {
			says(helper, c.run("status"), "Thornholm is finding its places: 1 of 2 villagers has a class so far.", "status, 1 of 2");
			ModAttachments.SOCIAL_CLASS.set(c.villagers().get(1), SocialClasses.ladder().get(0).id());
			says(helper, c.run("status"), "Thornholm is finding its places: 2 of 2 villagers have a class so far.", "status, 2 of 2");
		});
	}

	// ---- B866: the Bandit King's tribute, without CobbleDollars ------------------------------------------------------

	/** The King outlasts the storm with {@code treasury} hundredths in the hall: both lines read {@code count}, {@code left} is kept. */
	private static void tribute(GameTestHelper helper, int treasury, String count, int left) {
		QaKingTribute1103dGameTests.Tribute t = QaKingTribute1103dGameTests.tribute(helper, treasury, "qaWords" + treasury);
		helper.assertValueEqual(t.treasuryAfter(), left, "the treasury after the tribute (hundredths)");
		QaKingTribute1103dGameTests.assertLines(helper, t, count);
		helper.succeed();
	}

	/** 3.02 emeralds: exactly 1.00 taken (not a multiple of three), "1 emerald" in both lines; 2.02 left. */
	//$ gametest_ticks_batch HUGE '60' '"qaWords1103cTribute302"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWords1103cTribute302")
	public void qaExactlyOneEmeraldTakenFromThreeOhTwoReadsSingular(GameTestHelper helper) {
		tribute(helper, 302, "1 emerald", 202);
	}

	/** 2.99 emeralds: 0.99 taken, "0.99 emeralds" (never "0 emeralds" or "0.99 emerald"). */
	//$ gametest_ticks_batch HUGE '60' '"qaWords1103cTribute299"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWords1103cTribute299")
	public void qaNinetyNineHundredthsTakenReadsAsADecimal(GameTestHelper helper) {
		tribute(helper, 299, "0.99 emeralds", 200);
	}

	/** 5.99 emeralds: 1.99 taken, "1.99 emeralds" (never "1 emerald"). */
	//$ gametest_ticks_batch HUGE '60' '"qaWords1103cTribute599"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWords1103cTribute599")
	public void qaOneNinetyNineTakenReadsAsADecimal(GameTestHelper helper) {
		tribute(helper, 599, "1.99 emeralds", 400);
	}

	/** 3.03 emeralds: 1.01 taken, "1.01 emeralds" (the hundredth kept, plural just past one). */
	//$ gametest_ticks_batch HUGE '60' '"qaWords1103cTribute303"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWords1103cTribute303")
	public void qaOneOhOneTakenReadsPlural(GameTestHelper helper) {
		tribute(helper, 303, "1.01 emeralds", 202);
	}

	/** 3.30 emeralds: 1.10 taken, written as the treasury writes it, "1.1 emeralds". */
	//$ gametest_ticks_batch HUGE '60' '"qaWords1103cTribute330"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWords1103cTribute330")
	public void qaOneTenTakenReadsOnePointOne(GameTestHelper helper) {
		tribute(helper, 330, "1.1 emeralds", 220);
	}

	/** 0.03 emeralds: one hundredth taken, "0.01 emeralds". */
	//$ gametest_ticks_batch HUGE '60' '"qaWords1103cTribute3"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWords1103cTribute3")
	public void qaOneHundredthTakenReadsAsADecimal(GameTestHelper helper) {
		tribute(helper, 3, "0.01 emeralds", 2);
	}

	// ---- B853: the blessed founding line with two settlers -----------------------------------------------------------

	/** Tomas and Dara with the Founder's blessing: chat, the mother's chronicle and the colony's own stay plural. */
	//$ gametest_ticks_batch COLONY '300' '"qaWords1103cBlessedTwo"'
	@GameTest(template = COLONY, timeoutTicks = 300, batch = "qaWords1103cBlessedTwo")
	public void qaTwoBlessedSettlersStayPlural(GameTestHelper helper) {
		try {
			// the finder's whole arrival (setup, clean-up and every assertion on the three places it is read), reused as is
			Method arrival = QaColonyWordsVerify1103GameTests.class.getDeclaredMethod("arrival", GameTestHelper.class, List.class, boolean.class);
			arrival.setAccessible(true);
			arrival.invoke(null, helper, List.of("Tomas", "Dara"), true);
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof RuntimeException r) {
				throw r;
			}
			throw new RuntimeException(e.getCause());
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("setup: QaColonyWordsVerify1103GameTests.arrival is gone", e);
		}
	}
}
