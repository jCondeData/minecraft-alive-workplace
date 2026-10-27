package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * QA for {@code disabledArcs} in play (docs/wiki/config-switches.md: "Story arc ids that never start; a running one
 * ends at its next round. In the file only."; ROADMAP 31.4). Written from the spec: the existing tests cover only
 * {@code storyArcs} as a whole. Uses the test pack's three-chapter arc (chance 1.0, so by chance it always starts when
 * it may) in a Hamlet, where no arc of ours can start.
 */
public class QaDisabledArcsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final ResourceLocation THREE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/three_chapters");
	private static final long DAY = VillageNeeds.DAY;

	/** The file's list reaches the game, by full id or (for ours) by its path; an empty list disables nothing. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaDisabledArcsFromTheFile(GameTestHelper helper) {
		Set<String> before = Arcs.DISABLED;
		try {
			WorkplaceConfig.parse("{\"disabledArcs\": [\"aliveworkplace_test:test/three_chapters\", \"lost_caravan\"]}").apply();
			helper.assertTrue(Arcs.disabled(THREE), "the full id in the file didn't disable it: " + Arcs.DISABLED);
			helper.assertTrue(Arcs.disabled(LostCaravanStory.ARC), "our arc by its path didn't disable it: " + Arcs.DISABLED);
			helper.assertTrue(!Arcs.disabled(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("bandit_king")), "an arc not listed is disabled");
			WorkplaceConfig.parse("{}").apply();
			helper.assertTrue(!Arcs.disabled(THREE) && !Arcs.disabled(LostCaravanStory.ARC), "the default list disables something: " + Arcs.DISABLED);
		} finally {
			new WorkplaceConfig().apply();
			Arcs.DISABLED = before;
		}
		helper.succeed();
	}

	/**
	 * A disabled arc never starts by chance (its chance is 1.0, every roll would start it); with it listed by its id
	 * nothing starts over 20 rolls, and once it's taken off the list the first roll starts it.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaDisabledArcByChance"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDisabledArcByChance")
	public void qaADisabledArcNeverStartsByChance(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		setDay(level, Chronicle.day(level) + 1);
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = Arcs.runningCount(level.getServer()) + 5;
		Arcs.DISABLED = Set.of(THREE.toString());
		int started = Arcs.STARTED.get();
		for (int seed = 0; seed < 20; seed++) {
			ArcState s = Arcs.tryStart(level, hall, RandomSource.create(seed), 1);
			helper.assertTrue(s == null || !s.id.equals(THREE.toString()), "the disabled arc started by chance (seed " + seed + ")");
			if (s != null) {
				Arcs.stopAll(level, hall);
			}
		}
		helper.assertTrue(Arcs.running(level, hall) == null, "an arc runs: " + Arcs.running(level, hall));
		helper.assertTrue(Arcs.STARTED.get() == started, "arcs began while the only startable one was disabled: " + (Arcs.STARTED.get() - started));
		Arcs.DISABLED = Set.of();
		ArcState s = Arcs.tryStart(level, hall, RandomSource.create(1), 1);
		helper.assertTrue(s != null && s.id.equals(THREE.toString()), "taken off the list it didn't start: " + s);
		helper.succeed();
	}
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDisabledLostCaravan")
	public void qaADisabledLostCaravanNeverGoesMissing(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		BlockPos a = helper.absolutePos(HALL);
		int chance = LostCaravanStory.CHANCE;
		try {
			LostCaravanStory.CHANCE = 1; // every caravan on a long road would go missing
			Arcs.AT_ONCE = 20;
			Arcs.DISABLED = Set.of("lost_caravan");
			for (int seed = 0; seed < 5; seed++) {
				boolean lost = LostCaravanStory.lose(level, a, a.offset(400, 0, 0), List.of(new ItemStack(Items.BREAD, 4)), RandomSource.create(seed));
				helper.assertTrue(!lost && Arcs.running(level, a) == null, "the disabled Lost Caravan started (seed " + seed + ")");
			}
		} finally {
			LostCaravanStory.CHANCE = chance;
		}
		helper.succeed();
	}

	/**
	 * A running arc disabled ends at its next round: quietly (no ending or failure told or chronicled), its quests and
	 * its spawned mob gone. Taken off the list again, the village starts another arc normally.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaDisabledArcEnds"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDisabledArcEnds")
	public void qaARunningArcDisabledEndsAtItsNextRound(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(HALL.south(2));
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		setDay(level, Chronicle.day(level) + 1);
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = 20;
		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null, "the test arc didn't start");
		while (Arcs.running(level, hall) != null && Arcs.running(level, hall).chapter < 2) {
			Arcs.next(level, hall);
		}
		ArcEffects.check(level);
		helper.assertTrue(grubbs(level, hall).size() == 1, "no Grubb spawned in chapter 3");
		helper.assertTrue(!arcQuests(level, hall).isEmpty(), "chapter 3 posted no quest");
		int storyLines = chronicle(entity).size();

		Arcs.DISABLED = Set.of(THREE.toString());
		helper.assertTrue(Arcs.running(level, hall) != null, "it ended before its next round");
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == null, "the disabled arc carried on at its next round");
		helper.assertTrue(grubbs(level, hall).isEmpty(), "its spawned mob stayed");
		helper.assertTrue(arcQuests(level, hall).isEmpty(), "its quests stayed: " + arcQuests(level, hall).size());
		List<String> after = chronicle(entity);
		helper.assertTrue(after.size() == storyLines, "it didn't end quietly: " + after.subList(storyLines, after.size()));
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == null, "it came back while disabled");

		Arcs.DISABLED = Set.of();
		ArcState again = Arcs.tryStart(level, hall, RandomSource.create(2), 1);
		helper.assertTrue(again != null && again.id.equals(THREE.toString()) && Arcs.running(level, hall) == again,
			"re-enabled, the village didn't start an arc: " + (again == null ? null : again.id + " chapter " + again.chapter));
		helper.assertTrue(chronicle(entity).stream().filter(l -> l.contains("the test of three began")).count() == 2,
			"its first chapter didn't begin anew: " + chronicle(entity));
		helper.succeed();
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	/** The settings these tests change, put back when the test ends. Once per test. */
	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		boolean enabled = Arcs.ENABLED;
		Set<String> disabled = Arcs.DISABLED;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.ENABLED = enabled;
			Arcs.DISABLED = disabled;
		});
	}

	/** A hall at {@code at} whose own rounds post no quests; its arcs end with the test. */
	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return entity;
	}

	private static Arcs.Arc arc(GameTestHelper helper) {
		Arcs.Arc arc = Arcs.get(THREE).orElse(null);
		helper.assertTrue(arc != null, "the test pack's arc didn't load");
		return arc;
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * DAY + 1000);
	}

	private static List<String> chronicle(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.STORY).map(e -> e.text().getString().toLowerCase()).toList();
	}

	private static List<?> arcQuests(ServerLevel level, BlockPos hall) {
		return Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
	}

	private static List<Zombie> grubbs(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(Zombie.class, new AABB(hall).inflate(24), z -> z.isAlive() && z.getTags().contains(ArcEffects.roleTag("grubb")));
	}

	private static CommandSourceStack op(ServerLevel level, BlockPos at, List<String> said) {
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
		return new CommandSourceStack(out, Vec3.atCenterOf(at.south(2)), Vec2.ZERO, level, 2, "test", Component.literal("test"), level.getServer(), null);
	}
}
