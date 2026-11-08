package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA 2026-11-07 (b), roadmap 23.1a: a lead Builder short of a material takes it from a crewmate's bag. A crewmate
 * working close by (within 8 blocks) passes it over where it stands, only one further away is walked to. The lead has
 * no chest by its bench and there is no Storehouse, so the crewmate's bag is the only stone there is: every stone is
 * counted before and after (crewmate's bag, lead's bag and pockets, the ground, the block built).
 * <p>
 * The crewmate is a villager with no AI (so it stands exactly where the test puts it) that the site counts as a helper
 * working on it (BuildSite#seen every tick, as a helper's own work does); the lead is a real Builder in a glass cell
 * started through Builders.start with a one-stone scanned blueprint, so it can leave only by hopping.
 */
public class QaCrewPass1107bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	/** The spec's close-by distance (23.1a; BuilderWork.PASS_DISTANCE). */
	static final double PASS = 8.0;
	private static final BlockPos SRC = new BlockPos(25, 2, 25);
	/** The lead, walled in a glass cell on a ledge, so it can only hop. */
	static final BlockPos LEAD = new BlockPos(3, 8, 3);
	private static final BlockPos BENCH = new BlockPos(2, 8, 3);
	/** The one stone to build, on its own pillar beside the cell. */
	static final BlockPos TARGET = new BlockPos(3, 8, 6);

	record Run(ServerLevel level, Villager[] lead, Villager[] mate, BuildSite site) {
		Villager l() {
			return lead[0];
		}

		Villager m() {
			return mate[0];
		}
	}

	/** The lead in its cell with the build started, and a crewmate holding {@code stone} stone at {@code mateAt}. */
	static Run setUp(GameTestHelper helper, int buildDelay, int stone, Vec3 mateAt) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.STONE);
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC);
		helper.setBlock(SRC, Blocks.AIR);

		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(buildDelay, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);

		for (int y = 2; y < TARGET.getY(); y++) {
			helper.setBlock(new BlockPos(TARGET.getX(), y, TARGET.getZ()), Blocks.STONE);
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(LEAD.offset(dx, -1, dz), Blocks.STONE);
				for (int dy = 0; dy <= 2; dy++) {
					BlockPos p = LEAD.offset(dx, dy, dz);
					if (!p.equals(LEAD) && !p.equals(LEAD.above())) {
						helper.setBlock(p, Blocks.GLASS);
					}
				}
			}
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager lead = helper.spawn(EntityType.VILLAGER, LEAD);
		Builders.employ(level, lead, helper.absolutePos(BENCH));

		Villager mate = EntityType.VILLAGER.create(level);
		if (mate == null) {
			throw new GameTestAssertException("no villager made");
		}
		mate.setNoAi(true);
		mate.moveTo(mateAt.x, mateAt.y, mateAt.z, 0, 0);
		level.addFreshEntity(mate);
		ModAttachments.BUILDER_BAG.getOrCreate(mate).addAll(Items.STONE, stone);

		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(TARGET),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, lead, null, id, placement);
		helper.assertTrue(site != null, "the one-block build didn't start");
		site.seen(mate.getUUID(), level.getGameTime());
		return new Run(level, new Villager[] {lead}, new Villager[] {mate}, site);
	}

	/** The lead's feet when it stands in its cell. */
	static Vec3 cell(GameTestHelper helper) {
		return Vec3.atBottomCenterOf(helper.absolutePos(LEAD));
	}

	static int bag(Villager v) {
		return ModAttachments.BUILDER_BAG.getOrCreate(v).count(Items.STONE);
	}

	static int onGround(GameTestHelper helper) {
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(30, 30, 30).inflate(4);
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.isAlive() && e.getItem().is(Items.STONE)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	static int placed(GameTestHelper helper) {
		return helper.getBlockState(TARGET).is(Blocks.STONE) ? 1 : 0;
	}

	/** Every stone there is: both bags, the lead's pockets, the ground, the one built. */
	static int total(GameTestHelper helper, Run r) {
		return bag(r.m()) + bag(r.l()) + r.l().getInventory().countItem(Items.STONE) + onGround(helper) + placed(helper);
	}

	static String rel(GameTestHelper helper, Vec3 abs) {
		Vec3 o = Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO));
		return String.format("(%.2f, %.2f, %.2f)", abs.x - o.x, abs.y - o.y, abs.z - o.z);
	}

	static String where(GameTestHelper helper, Run r) {
		return "stage " + r.site().stage() + ", status " + r.site().status() + ", lead at " + rel(helper, r.l().position())
			+ ", mate bag " + bag(r.m()) + ", lead bag " + bag(r.l()) + ", ground " + onGround(helper) + ", built " + placed(helper) + ", skipped " + r.site().skipped();
	}

	/** A stone pillar ledge under {@code at} and the two blocks before it, for the crewmate to stand on (and the lead to reach). */
	static void ledge(GameTestHelper helper, BlockPos at) {
		for (int y = 2; y < at.getY(); y++) {
			for (int dx = -2; dx <= 0; dx++) {
				helper.setBlock(new BlockPos(at.getX() + dx, y, at.getZ()), Blocks.STONE);
			}
		}
	}

	/** Keeps the crewmate counted as working on the site, as its own work would. */
	static void keepOnCrew(Run r) {
		if (r.m().isAlive()) {
			r.site().seen(r.m().getUUID(), r.level().getGameTime());
		}
	}

	/** The first tick stone left the crewmate's bag: the lead's feet then, and its distance to the crewmate. */
	static final class Pass {
		int tick = -1;
		Vec3 leadAt;
		double dist = -1;
		int before;

		void watch(GameTestHelper helper, Run r, int[] last) {
			int now = bag(r.m());
			if (tick < 0 && now < last[0]) {
				tick = (int) helper.getTick();
				leadAt = r.l().position();
				dist = Math.sqrt(r.l().distanceToSqr(r.m()));
				before = last[0];
			}
			last[0] = now;
		}
	}

	/**
	 * The crewmate stands exactly 8 blocks from the lead (kept there each tick, the limit of "close by"). The lead must
	 * take the stone over while still in its cell, never walking or hopping to the crewmate; then build it, one stone
	 * used and none made or lost.
	 */
	//$ gametest_ticks_batch HUGE '900' '"qa1107bCrewPassAtLimit"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qa1107bCrewPassAtLimit")
	public void aCrewmateExactlyEightBlocksAwayPassesTheStoneOverWithoutTheLeadMoving(GameTestHelper helper) {
		Vec3 start = cell(helper);
		ledge(helper, LEAD.offset(8, 0, 0)); // the crewmate stands on ground the lead could walk or hop to
		Run r = setUp(helper, 2, 1, start.add(PASS, 0, 0));
		Pass pass = new Pass();
		int[] last = {bag(r.m())};
		double[] maxStray = {0};
		helper.onEachTick(() -> {
			if (pass.tick < 0) {
				// the crewmate stays exactly at the limit, wherever the lead shuffles in its cell
				Vec3 l = r.l().position();
				r.m().moveTo(l.x + PASS, l.y, l.z, 0, 0);
				maxStray[0] = Math.max(maxStray[0], l.distanceTo(start));
			}
			keepOnCrew(r);
			pass.watch(helper, r, last);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(pass.tick >= 0, "the stone never left the crewmate's bag: " + where(helper, r));
			helper.assertTrue(Math.abs(pass.dist - PASS) < 1e-6, "setup: the crewmate was " + pass.dist + " blocks away when passing, not exactly " + PASS);
			helper.assertTrue(pass.leadAt.distanceTo(start) < 0.5 && maxStray[0] < 0.5,
				"the lead moved to fetch from a crewmate exactly " + PASS + " blocks away: took the stone at " + rel(helper, pass.leadAt)
					+ ", its cell is " + rel(helper, start) + " (strayed up to " + String.format("%.2f", maxStray[0]) + ")");
			helper.assertTrue(placed(helper) == 1 && r.site().stage() == BuildPlan.Stage.DONE, "not built yet: " + where(helper, r));
			helper.assertTrue(total(helper, r) == 1 && bag(r.m()) == 0 && onGround(helper) == 0,
				"wanted the crewmate's one stone built and nothing else: " + where(helper, r));
			LOGGER.info("qa 1107b crew pass at the limit: passed at tick {} over {} blocks with the lead in its cell; built, total 1",
				pass.tick, String.format("%.6f", pass.dist));
		});
	}

	/**
	 * The crewmate stands 9 blocks from the lead, one block beyond "close by", on a ledge the lead can hop to. The stone
	 * must never cross more than 8 blocks: the lead leaves its cell and takes it from within reach of the crewmate, then
	 * builds it, one stone used.
	 */
	//$ gametest_ticks_batch HUGE '1500' '"qa1107bCrewPassBeyond"'
	@GameTest(template = HUGE, timeoutTicks = 1500, batch = "qa1107bCrewPassBeyond")
	public void aCrewmateNineBlocksAwayIsWalkedToBeforeTheStoneChangesBags(GameTestHelper helper) {
		Vec3 start = cell(helper);
		ledge(helper, LEAD.offset(9, 0, 0));
		Run r = setUp(helper, 2, 1, start.add(PASS + 1, 0, 0));
		Pass pass = new Pass();
		int[] last = {bag(r.m())};
		double[] closest = {Double.MAX_VALUE};
		helper.onEachTick(() -> {
			keepOnCrew(r);
			if (pass.tick < 0) {
				closest[0] = Math.min(closest[0], Math.sqrt(r.l().distanceToSqr(r.m())));
			}
			pass.watch(helper, r, last);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(pass.tick >= 0, "the stone never left the crewmate's bag (lead came within "
				+ String.format("%.2f", closest[0]) + "): " + where(helper, r));
			helper.assertTrue(pass.dist <= PASS, "the stone was passed over " + String.format("%.3f", pass.dist)
				+ " blocks, beyond the " + PASS + " of close by (lead at " + rel(helper, pass.leadAt) + ", cell " + rel(helper, start) + ")");
			helper.assertTrue(pass.leadAt.distanceTo(start) >= 1, "the lead took the stone from its cell, " + String.format("%.3f", pass.dist)
				+ " blocks from the crewmate");
			helper.assertTrue(placed(helper) == 1 && r.site().stage() == BuildPlan.Stage.DONE, "not built yet: " + where(helper, r));
			helper.assertTrue(total(helper, r) == 1 && onGround(helper) == 0, "wanted the one stone built and nothing else: " + where(helper, r));
			LOGGER.info("qa 1107b crew pass beyond: walked from {} to {}, took the stone at tick {} from {} blocks; built",
				rel(helper, start), rel(helper, pass.leadAt), pass.tick, String.format("%.2f", pass.dist));
		});
	}

	/**
	 * A crewmate close by holds 40 stone; the build needs one. Watched every tick until the lead's job is over: the
	 * stone in both bags, the lead's pockets, the ground and the block built always adds up to 40, and the build ends.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qa1107bCrewPassCount"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qa1107bCrewPassCount")
	public void everyStoneOfTheCrewmatesBagIsAccountedForEveryTick(GameTestHelper helper) {
		final int stock = 40;
		Vec3 start = cell(helper);
		Run r = setUp(helper, 2, stock, start.add(4, 0, 0));
		String[] off = {null};
		helper.onEachTick(() -> {
			keepOnCrew(r);
			int t = total(helper, r);
			if (off[0] == null && t != stock) {
				off[0] = "at tick " + helper.getTick() + " there were " + t + " stone, not " + stock + ": " + where(helper, r);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(off[0] == null, off[0] == null ? "" : off[0]);
			helper.assertTrue(placed(helper) == 1 && r.site().stage() == BuildPlan.Stage.DONE, "not built yet: " + where(helper, r));
			helper.assertTrue(helper.getTick() > 40 && Builders.activeSite(r.level(), r.l()) == null, "the lead's job isn't over yet: " + where(helper, r));
			helper.assertTrue(total(helper, r) == stock, "stone made or lost after the job: " + where(helper, r));
			LOGGER.info("qa 1107b crew pass count: {} stone every tick; at the end {}", stock, where(helper, r));
		});
	}

	/**
	 * The server restarts (lead and crewmate both saved and loaded) the tick after the stone changed bags, before it is
	 * built. Both bags hold after the reload what they held before, the build finishes, one stone used in all.
	 */
	//$ gametest_ticks_batch HUGE '1500' '"qa1107bCrewPassReload"'
	@GameTest(template = HUGE, timeoutTicks = 1500, batch = "qa1107bCrewPassReload")
	public void aRestartRightAfterThePassLosesAndDoublesNothing(GameTestHelper helper) {
		final int stock = 5;
		Vec3 start = cell(helper);
		Run r = setUp(helper, 40, stock, start.add(5, 0, 0));
		int[] reloadedAt = {-1};
		int[] before = {-1, -1};
		int[] after = {-1, -1};
		String[] off = {null};
		helper.onEachTick(() -> {
			keepOnCrew(r);
			if (reloadedAt[0] < 0 && bag(r.l()) > 0 && placed(helper) == 0) {
				before[0] = bag(r.l());
				before[1] = bag(r.m());
				reloadedAt[0] = (int) helper.getTick();
				r.lead()[0] = reload(r.level(), r.l());
				r.mate()[0] = reload(r.level(), r.m());
				after[0] = bag(r.l());
				after[1] = bag(r.m());
				keepOnCrew(r);
			}
			int t = total(helper, r);
			if (off[0] == null && t != stock) {
				off[0] = "at tick " + helper.getTick() + " there were " + t + " stone, not " + stock + ": " + where(helper, r);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadedAt[0] >= 0, "the lead never carried the crewmate's stone unbuilt: " + where(helper, r));
			helper.assertTrue(before[0] == after[0] && before[1] == after[1], "bags before the reload lead " + before[0] + ", mate "
				+ before[1] + "; after lead " + after[0] + ", mate " + after[1]);
			helper.assertTrue(off[0] == null, off[0] == null ? "" : off[0]);
			helper.assertTrue(placed(helper) == 1 && r.site().stage() == BuildPlan.Stage.DONE, "not built after the reload: " + where(helper, r));
			helper.assertTrue(r.m().isNoAi(), "the crewmate lost its NoAI flag in the reload (setup)");
			LOGGER.info("qa 1107b crew pass reload: at tick {} lead {} mate {} kept; built; {}", reloadedAt[0], after[0], after[1], where(helper, r));
		});
	}

	static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}
}
