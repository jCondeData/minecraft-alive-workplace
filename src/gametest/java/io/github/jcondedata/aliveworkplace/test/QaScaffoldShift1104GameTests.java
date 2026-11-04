package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b-1104-0638) for scaffolding, written from the wiki (docs/wiki/scaffolding.md), claims its section 8 doesn't
 * prove yet: "When its shift ends (night, a panic) it is put down at the column's foot", "a column already up is built
 * on rather than a new one started" and "The request goes away once the builder finds enough in its chests". Same
 * 14-high test pillar as {@link QaScaffoldingGameTests}.
 */
public class QaScaffoldShift1104GameTests {
	private static final Logger LOGGER = LoggerFactory.getLogger("QaScaffoldShift1104");
	private static final String BIG = QaScaffoldingGameTests.BIG;
	private static final int SCAFFOLDING = QaScaffoldingGameTests.SCAFFOLDING;
	/** Ticks the builder has, once his shift ends, to be on the ground. */
	private static final int DOWN_WITHIN = 200;

	/** He's up a column: at least 5 above the ground, in or on a scaffold of his site. */
	private static BlockPos upAColumn(QaScaffoldingGameTests.Site s, int ground) {
		Villager v = s.builder();
		if (v.getY() < ground + 5) {
			return null;
		}
		BlockPos feet = v.blockPosition();
		for (BlockPos p : List.of(feet, feet.below())) {
			if (s.level().getBlockState(p).is(Blocks.SCAFFOLDING) && s.site().scaffolds().contains(p)) {
				return p;
			}
		}
		return null;
	}

	/**
	 * Wiki: "When its shift ends (night, a panic) it is put down at the column's foot", Decisions: "a fall from a tall
	 * column would hurt it". Up the column at least 5 high, night comes (the clock held at 13000): within 200 ticks he
	 * is on the ground in the column he was on (not left up there, not dropped somewhere else), never hurt. In the
	 * morning he finishes the pillar, no scaffold is left and all 64 are back in the chest.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qa1104ScaffoldNight"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qa1104ScaffoldNight")
	public void nightPutsHimDownAtTheColumnsFootUnhurt(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, SCAFFOLDING);
		shiftEnds(helper, s, "night", new ShiftEnd() {
			@Override
			public void begin(BlockPos cell) {
				helper.setDayTime(13000);
			}

			@Override
			public void hold(long since) {
				helper.setDayTime(13000);
			}

			@Override
			public void end() {
				helper.setDayTime(2000);
			}
		});
	}

	/**
	 * The same with a panic: a pillager (NoAI, floating, so it neither moves nor shoots) appears 3 blocks out from his
	 * column at his height while he's up it. He panics, and within 200 ticks he is on the ground in the column he was on,
	 * unhurt. With the pillager gone he finishes, no scaffold left standing, all 64 back in the chest.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qa1104ScaffoldPanic"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qa1104ScaffoldPanic")
	public void aPanicPutsHimDownAtTheColumnsFootUnhurt(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, SCAFFOLDING);
		Pillager[] hostile = {null};
		boolean[] panicked = {false};
		helper.onEachTick(() -> panicked[0] |= s.builder().getBrain().isActive(Activity.PANIC));
		shiftEnds(helper, s, "a panic", new ShiftEnd() {
			@Override
			public void begin(BlockPos cell) {
				BlockPos centre = helper.absolutePos(QaScaffoldingGameTests.PILLAR_AT.offset(2, 0, 2));
				int dx = Integer.signum(cell.getX() - centre.getX());
				int dz = Integer.signum(cell.getZ() - centre.getZ());
				if (Math.abs(cell.getX() - centre.getX()) >= Math.abs(cell.getZ() - centre.getZ())) {
					dz = 0;
				} else {
					dx = 0;
				}
				Pillager p = EntityType.PILLAGER.create(s.level());
				p.setNoAi(true);
				p.setPersistenceRequired();
				p.moveTo(cell.getX() + 0.5 + 3 * dx, s.builder().getY(), cell.getZ() + 0.5 + 3 * dz, 0, 0);
				s.level().addFreshEntity(p);
				hostile[0] = p;
				LOGGER.info("qa1104 panic: pillager at {} (builder on {})", helper.relativePos(p.blockPosition()), helper.relativePos(cell));
			}

			@Override
			public void hold(long since) {
				helper.setDayTime(3000);
				if (since == 300 && hostile[0] != null) {
					helper.assertTrue(panicked[0], "setup: the builder never panicked with a pillager 3 blocks off (activity "
						+ s.builder().getBrain().getActiveNonCoreActivity() + ")");
					hostile[0].discard();
				}
			}

			@Override
			public void end() {
				helper.setDayTime(3000);
			}
		});
	}

	private interface ShiftEnd {
		void begin(BlockPos cell);

		void hold(long since);

		void end();
	}

	/** Shared by night and panic: the shift ends while he's up, he must be put down at that column's foot, then finish. */
	private static void shiftEnds(GameTestHelper helper, QaScaffoldingGameTests.Site s, String what, ShiftEnd how) {
		int ground = helper.absolutePos(QaScaffoldingGameTests.PILLAR_AT).getY();
		long[] at = {-1};
		BlockPos[] cell = {null};
		double[] fromY = {0};
		long[] downAt = {-1};
		String[] downWhere = {null};
		float[] lowestHealth = {Float.MAX_VALUE};
		boolean[] over = {false};
		helper.onEachTick(() -> {
			Villager v = s.builder();
			lowestHealth[0] = Math.min(lowestHealth[0], v.getHealth());
			long t = helper.getTick();
			if (at[0] < 0) {
				helper.setDayTime(2000);
				BlockPos c = upAColumn(s, ground);
				if (c != null) {
					at[0] = t;
					cell[0] = c;
					fromY[0] = v.getY();
					how.begin(c);
					LOGGER.info("qa1104 {}: up at y+{} on {} with {} scaffolds", what, v.getY() - ground, helper.relativePos(c), s.site().scaffolds().size());
				}
				return;
			}
			if (over[0]) {
				how.end();
				return;
			}
			long since = t - at[0];
			how.hold(since);
			if (downAt[0] < 0 && v.getY() < ground + 0.5) {
				downAt[0] = since;
				downWhere[0] = String.format("(%.2f, %.2f, %.2f) relative %s, fallDistance %.2f", v.getX() - helper.absolutePos(BlockPos.ZERO).getX(),
					v.getY() - helper.absolutePos(BlockPos.ZERO).getY(), v.getZ() - helper.absolutePos(BlockPos.ZERO).getZ(),
					helper.relativePos(v.blockPosition()), v.fallDistance);
				LOGGER.info("qa1104 {}: on the ground {} ticks later at {}", what, since, downWhere[0]);
				double off = Math.max(Math.abs(v.getX() - (cell[0].getX() + 0.5)), Math.abs(v.getZ() - (cell[0].getZ() + 0.5)));
				helper.assertTrue(off <= 0.6, "after " + what + " he came down " + off + " blocks off the column he was on at "
					+ helper.relativePos(cell[0]) + " (from y+" + (fromY[0] - ground) + "): at " + downWhere[0]);
			}
			if (since == DOWN_WITHIN) {
				helper.assertTrue(downAt[0] >= 0, DOWN_WITHIN + " ticks after " + what + " he isn't on the ground: at y+" + (v.getY() - ground)
					+ " (" + helper.relativePos(v.blockPosition()) + ", was on " + helper.relativePos(cell[0]) + " at y+" + (fromY[0] - ground)
					+ "), activity " + v.getBrain().getActiveNonCoreActivity());
			}
			if (since == 600) {
				helper.assertTrue(v.getHealth() >= v.getMaxHealth(), "the builder got hurt during " + what + ": health " + v.getHealth());
				over[0] = true;
				how.end();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(at[0] >= 0, "setup: the builder never got up a column (scaffolds " + s.site().scaffolds().size() + ")");
			helper.assertTrue(over[0], "the " + what + " isn't over yet");
			QaScaffoldingGameTests.assertFinished(helper, s);
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			helper.assertTrue(lowestHealth[0] >= s.builder().getMaxHealth(), "the builder was hurt: health fell to " + lowestHealth[0]);
			helper.assertTrue(QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) == SCAFFOLDING, "the chest holds "
				+ QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) + " scaffolding after " + what + ", not " + SCAFFOLDING
				+ " (ground " + QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING) + ", bag " + QaScaffoldingGameTests.bagCount(s.builder()) + ")");
			LOGGER.info("qa1104 {}: down after {} ticks at {}, finished, chest 64", what, downAt[0], downWhere[0]);
		});
	}

	/**
	 * Wiki: "The one needing the fewest new scaffolds wins ..., so a column already up is built on rather than a new one
	 * started". The pillar and its cap are all in reach from one column beside the pillar. Once three scaffolds stand the
	 * builder is saved and loaded (a fresh entity that doesn't remember the column it was putting up; wiki, Saved data:
	 * "finds the half-built column and builds on it"). In each stage every scaffold put up stands in one column (one x,z),
	 * never two; the build finishes with none standing and all 64 back.
	 */
	//$ gametest_ticks_batch BIG '9000' '"qa1104ScaffoldOneColumn"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "qa1104ScaffoldOneColumn")
	public void aColumnAlreadyUpIsBuiltOnAfterAReload(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s0 = QaScaffoldingGameTests.pillar(helper, SCAFFOLDING);
		Villager[] builder = {s0.builder()};
		Map<BuildPlan.Stage, Set<String>> columns = new HashMap<>();
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(3000);
			for (BlockPos p : s0.site().scaffolds()) {
				BlockPos r = helper.relativePos(p);
				columns.computeIfAbsent(s0.site().stage(), k -> new HashSet<>()).add(r.getX() + "," + r.getZ());
			}
			if (!reloaded[0] && s0.site().scaffolds().size() >= 3) {
				reloaded[0] = true;
				Villager old = builder[0];
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(old.save(tag), "the builder wasn't saved");
				old.discard();
				Entity loaded = EntityType.loadEntityRecursive(tag, s0.level(), e -> e);
				helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
				s0.level().addFreshEntity(loaded);
				builder[0] = (Villager) loaded;
				LOGGER.info("qa1104 one column: reloaded with {} up", s0.site().scaffolds().stream().map(helper::relativePos).toList());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "setup: never 3 scaffolds up to reload with");
			QaScaffoldingGameTests.Site s = new QaScaffoldingGameTests.Site(s0.level(), builder[0], s0.site(), s0.plan());
			QaScaffoldingGameTests.assertFinished(helper, s);
			for (Map.Entry<BuildPlan.Stage, Set<String>> e : columns.entrySet()) {
				helper.assertTrue(e.getValue().size() == 1, "in " + e.getKey() + " scaffolding went up in " + e.getValue().size()
					+ " columns (x,z) " + e.getValue() + ", not one");
			}
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			helper.assertTrue(QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) == SCAFFOLDING, "the chest holds "
				+ QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) + " scaffolding, not " + SCAFFOLDING);
			LOGGER.info("qa1104 one column: columns per stage {}", columns);
		});
	}

	/**
	 * Wiki, Asking for it: "the shortfall is added to the site's missing list ... The request goes away once the builder
	 * finds enough in its chests." No scaffolding at first; as soon as the missing list asks for it, 64 go in the chest.
	 * While the build goes on, the request is gone within 1200 ticks; the build ends with no scaffold standing and all
	 * 64 accounted for (chest, ground, bag).
	 */
	//$ gametest_ticks_batch BIG '12000' '"qa1104ScaffoldRequestGoes"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qa1104ScaffoldRequestGoes")
	public void theRequestGoesAwayOnceTheChestHoldsEnough(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, 0);
		long[] refilledAt = {-1};
		long[] goneAt = {-1};
		String[] last = {""};
		helper.onEachTick(() -> {
			helper.setDayTime(3000);
			boolean live = BuildSiteManager.get(s.level()).get(s.site().id()) != null;
			boolean asks = s.site().missing().getOrDefault(Items.SCAFFOLDING, 0) > 0;
			long t = helper.getTick();
			if (refilledAt[0] < 0) {
				if (asks && live) {
					refilledAt[0] = t;
					Container chest = helper.getBlockEntity(QaScaffoldingGameTests.CHEST);
					chest.setItem(5, new ItemStack(Items.SCAFFOLDING, SCAFFOLDING));
					LOGGER.info("qa1104 request: asked for {} at stage {}; 64 put in the chest", s.site().missing().get(Items.SCAFFOLDING), s.site().stage());
				}
				return;
			}
			if (goneAt[0] < 0 && (!asks || !live)) {
				goneAt[0] = t - refilledAt[0];
				LOGGER.info("qa1104 request: gone after {} ticks (site live {}, stage {})", goneAt[0], live, s.site().stage());
			}
			if (live) {
				last[0] = "stage " + s.site().stage() + " status " + s.site().status() + " missing " + s.site().missing() + " scaffolds " + s.site().scaffolds().size()
					+ " board " + QaScaffoldingGameTests.board(s);
			}
			if (goneAt[0] < 0 && t - refilledAt[0] == 1200) {
				throw new GameTestAssertException("1200 ticks after 64 scaffolding went in the chest the site still asks for it: " + last[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(refilledAt[0] >= 0, "setup: scaffolding was never asked for: missing " + s.site().missing());
			helper.assertTrue(goneAt[0] >= 0, "the request is still there");
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: " + last[0]);
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			int total = QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) + QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING)
				+ QaScaffoldingGameTests.bagCount(s.builder());
			helper.assertTrue(total == SCAFFOLDING, SCAFFOLDING + " scaffolding went in, " + total + " came out (chest "
				+ QaScaffoldingGameTests.chestCount(helper, Items.SCAFFOLDING) + ")");
			LOGGER.info("qa1104 request: gone {} ticks after the refill; skipped {}", goneAt[0], s.site().skipped());
		});
	}
}
