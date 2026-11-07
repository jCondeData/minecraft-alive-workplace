package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA 2026-11-07 (lane e, saves), B1019 (roadmap 23.1a; builder.md, Materials: "a Builder carries never more of a thing
 * than the stretch of work ahead still needs"). A lead Builder with no chest by his bench and no Storehouse takes what
 * his build needs from a crewmate's bag. Here the build is several blocks (three stone, or two stone and two bricks),
 * and the server restarts (every build site, the lead and the crewmate saved and loaded) right after the take, or the
 * crewmate alone is reloaded. Every tick: no stone or brick is made or lost (both bags, the lead's pockets, the ground,
 * the blocks built), and the lead never carries more of a thing than the unbuilt blocks still need. At the end nothing
 * lies on the ground and the crewmate's bag holds all the build didn't use.
 * <p>
 * The lead's cell, bench and crewmate are QaCrewPass1107bGameTests's (the lead can only hop; the crewmate has no AI and
 * is kept on the crew each tick). The build is a small scanned blueprint on stone pillars beside the cell.
 */
public class QaCrewSpareSaves1107eGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final String HUGE = QaCrewPass1107bGameTests.HUGE;
	private static final BlockPos LEAD = QaCrewPass1107bGameTests.LEAD;
	private static final BlockPos BENCH = LEAD.offset(-1, 0, 0);
	/** Where the blueprint is scanned (cleared after). */
	private static final BlockPos SRC = new BlockPos(24, 2, 25);
	/** The build's origin: its bottom row runs x 2..4 at z 6, every block within reach of the lead's cell. */
	private static final BlockPos ORIGIN = new BlockPos(2, 8, 6);

	private static final Map<BlockPos, Block> THREE_STONE = shape(Blocks.STONE, Blocks.STONE, Blocks.STONE, null);
	private static final Map<BlockPos, Block> TWO_KINDS = shape(Blocks.STONE, Blocks.STONE, Blocks.BRICKS, Blocks.BRICKS);

	/** Bottom row x 0..2 and, if not null, one block on top of the middle. */
	private static Map<BlockPos, Block> shape(Block a, Block b, Block c, Block top) {
		Map<BlockPos, Block> m = new LinkedHashMap<>();
		m.put(new BlockPos(0, 0, 0), a);
		m.put(new BlockPos(1, 0, 0), b);
		m.put(new BlockPos(2, 0, 0), c);
		if (top != null) {
			m.put(new BlockPos(1, 1, 0), top);
		}
		return m;
	}

	/** The lead, the crewmate (both replaced on a reload) and the site's id (the live site is looked up each time). */
	private static final class Run {
		final ServerLevel level;
		final Map<BlockPos, Block> shape;
		final Map<Item, Integer> stock;
		final UUID siteId;
		Villager lead;
		Villager mate;

		Run(ServerLevel level, Map<BlockPos, Block> shape, Map<Item, Integer> stock, UUID siteId, Villager lead, Villager mate) {
			this.level = level;
			this.shape = shape;
			this.stock = stock;
			this.siteId = siteId;
			this.lead = lead;
			this.mate = mate;
		}

		BuildSite site() {
			return BuildSiteManager.get(level).get(siteId);
		}
	}

	private static Run setUp(GameTestHelper helper, int buildDelay, Map<BlockPos, Block> shape, Map<Item, Integer> stock, Vec3 mateAt) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (Map.Entry<BlockPos, Block> e : shape.entrySet()) {
			helper.setBlock(SRC.offset(e.getKey()), e.getValue());
		}
		boolean tall = shape.keySet().stream().anyMatch(p -> p.getY() > 0);
		BlockPos far = SRC.offset(2, tall ? 1 : 0, 0);
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, far);
		for (BlockPos p : BlockPos.betweenClosed(SRC, far)) {
			helper.setBlock(p.immutable(), Blocks.AIR);
		}

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

		// stone pillars under the bottom row
		for (int dx = 0; dx <= 2; dx++) {
			for (int y = 2; y < ORIGIN.getY(); y++) {
				helper.setBlock(new BlockPos(ORIGIN.getX() + dx, y, ORIGIN.getZ()), Blocks.STONE);
			}
		}
		// the lead's glass cell on a stone floor
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
		stock.forEach((item, n) -> ModAttachments.BUILDER_BAG.getOrCreate(mate).addAll(item, n));

		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, lead, null, id, placement);
		helper.assertTrue(site != null, "the build didn't start");
		site.seen(mate.getUUID(), level.getGameTime());
		return new Run(level, shape, stock, site.id(), lead, mate);
	}

	private static void keepOnCrew(Run r) {
		BuildSite site = r.site();
		if (site != null && r.mate.isAlive()) {
			site.seen(r.mate.getUUID(), r.level.getGameTime());
		}
	}

	private static int bag(Villager v, Item item) {
		return ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
	}

	private static int onGround(GameTestHelper helper, Item item) {
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(30, 30, 30).inflate(4);
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.isAlive() && e.getItem().is(item)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static int placed(GameTestHelper helper, Run r, Item item) {
		int n = 0;
		for (Map.Entry<BlockPos, Block> e : r.shape.entrySet()) {
			if (e.getValue().asItem() == item && helper.getBlockState(ORIGIN.offset(e.getKey())).is(e.getValue())) {
				n++;
			}
		}
		return n;
	}

	private static int needs(Run r, Item item) {
		return (int) r.shape.values().stream().filter(b -> b.asItem() == item).count();
	}

	private static int total(GameTestHelper helper, Run r, Item item) {
		return bag(r.mate, item) + bag(r.lead, item) + r.lead.getInventory().countItem(item) + onGround(helper, item) + placed(helper, r, item);
	}

	private static String where(GameTestHelper helper, Run r) {
		BuildSite site = r.site();
		StringBuilder s = new StringBuilder(site == null ? "site gone" : "stage " + site.stage() + ", status " + site.status() + ", skipped " + site.skipped());
		for (Item item : r.stock.keySet()) {
			s.append("; ").append(item).append(": mate bag ").append(bag(r.mate, item)).append(", lead bag ").append(bag(r.lead, item))
				.append(", lead pockets ").append(r.lead.getInventory().countItem(item)).append(", ground ").append(onGround(helper, item))
				.append(", built ").append(placed(helper, r, item)).append('/').append(needs(r, item));
		}
		return s.toString();
	}

	/** A server restart: every build site, the lead and the crewmate saved and loaded. */
	private static void restart(Run r) {
		var registries = r.level.registryAccess();
		r.level.getDataStorage().set(BuildSiteManager.NAME,
			BuildSiteManager.load(BuildSiteManager.get(r.level).save(new CompoundTag(), registries), registries));
		r.lead = QaCrewPass1107bGameTests.reload(r.level, r.lead);
		r.mate = QaCrewPass1107bGameTests.reload(r.level, r.mate);
		keepOnCrew(r);
	}

	/**
	 * Every tick: each kind adds up to the stock, and the lead carries no more of a kind than its unbuilt blocks need.
	 * The first break is kept in {@code off[0]}.
	 */
	private static void watch(GameTestHelper helper, Run r, String[] off) {
		if (off[0] != null) {
			return;
		}
		for (Map.Entry<Item, Integer> e : r.stock.entrySet()) {
			Item item = e.getKey();
			int t = total(helper, r, item);
			if (t != e.getValue()) {
				off[0] = "at tick " + helper.getTick() + " there were " + t + " " + item + ", not " + e.getValue() + ": " + where(helper, r);
				return;
			}
			int unbuilt = needs(r, item) - placed(helper, r, item);
			int carried = bag(r.lead, item) + r.lead.getInventory().countItem(item);
			if (carried > unbuilt) {
				off[0] = "at tick " + helper.getTick() + " the lead carried " + carried + " " + item + " with only " + unbuilt
					+ " left to build (builder.md: never more than the work ahead still needs): " + where(helper, r);
				return;
			}
		}
	}

	private static boolean anyCarried(Run r) {
		return r.stock.keySet().stream().anyMatch(i -> bag(r.lead, i) > 0);
	}

	private static boolean nonePlaced(GameTestHelper helper, Run r) {
		return r.stock.keySet().stream().allMatch(i -> placed(helper, r, i) == 0);
	}

	/** Built in full, the lead's job over, nothing on the ground, the crewmate's bag holding the stock less what was built. */
	private static void assertFinished(GameTestHelper helper, Run r, String[] off) {
		helper.assertTrue(off[0] == null, off[0] == null ? "" : off[0]);
		for (Item item : r.stock.keySet()) {
			helper.assertTrue(placed(helper, r, item) == needs(r, item), "not built yet: " + where(helper, r));
		}
		helper.assertTrue(Builders.activeSite(r.level, r.lead) == null, "the lead's job isn't over yet: " + where(helper, r));
		for (Map.Entry<Item, Integer> e : r.stock.entrySet()) {
			Item item = e.getKey();
			int left = e.getValue() - needs(r, item);
			helper.assertTrue(onGround(helper, item) == 0 && bag(r.mate, item) == left && bag(r.lead, item) == 0,
				"wanted nothing of " + item + " on the ground, the crewmate's bag back to " + left + " and the lead's empty: " + where(helper, r));
		}
	}

	/**
	 * The server restarts the tick the lead first carries the crewmate's stone, before any of the three blocks is
	 * built. Before and after the restart he holds no more than the three still needed; the build finishes; the
	 * crewmate's bag ends with 37 of its 40 and none lies on the ground.
	 */
	//$ gametest_ticks_batch HUGE '1500' '"qa1107eCrewSpareRestart"'
	@GameTest(template = HUGE, timeoutTicks = 1500, batch = "qa1107eCrewSpareRestart")
	public void aRestartRightAfterTheTakeLeavesNoSpareOnTheGround(GameTestHelper helper) {
		Run r = setUp(helper, 10, THREE_STONE, Map.of(Items.STONE, 40), QaCrewPass1107bGameTests.cell(helper).add(4, 0, 0));
		String[] off = {null};
		int[] at = {-1, -1, -1};
		helper.onEachTick(() -> {
			keepOnCrew(r);
			if (at[0] < 0 && anyCarried(r) && nonePlaced(helper, r)) {
				at[0] = (int) helper.getTick();
				at[1] = bag(r.lead, Items.STONE);
				restart(r);
				at[2] = bag(r.lead, Items.STONE);
			}
			watch(helper, r, off);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(at[0] >= 0, "the lead never carried the crewmate's stone unbuilt: " + where(helper, r));
			helper.assertTrue(at[1] == at[2], "the lead's bag held " + at[1] + " stone before the restart and " + at[2] + " after");
			helper.assertTrue(r.mate.isNoAi(), "the crewmate lost its NoAI flag in the reload (setup)");
			assertFinished(helper, r, off);
			LOGGER.info("qa 1107e crew spare restart: took {} at tick {}, kept over the restart; {}", at[1], at[0], where(helper, r));
		});
	}

	/**
	 * The crewmate alone is reloaded twice: before the lead first takes from it, and again while the lead holds what
	 * it took, the build unfinished. Its 64 stone hold over each reload, the lead never holds more than what is left to build, and 61 come back.
	 */
	//$ gametest_ticks_batch HUGE '1500' '"qa1107eCrewSpareMateReload"'
	@GameTest(template = HUGE, timeoutTicks = 1500, batch = "qa1107eCrewSpareMateReload")
	public void aCrewmateReloadedMidJobKeepsAllTheBuildDidNotUse(GameTestHelper helper) {
		Run r = setUp(helper, 10, THREE_STONE, Map.of(Items.STONE, 64), QaCrewPass1107bGameTests.cell(helper).add(4, 0, 0));
		String[] off = {null};
		int[] reloads = {0};
		String[] lost = {null};
		Runnable reload = () -> {
			int before = bag(r.mate, Items.STONE);
			r.mate = QaCrewPass1107bGameTests.reload(r.level, r.mate);
			keepOnCrew(r);
			reloads[0]++;
			if (lost[0] == null && bag(r.mate, Items.STONE) != before) {
				lost[0] = "the crewmate held " + before + " stone before reload " + reloads[0] + " and " + bag(r.mate, Items.STONE) + " after";
			}
		};
		reload.run(); // before the lead has taken anything
		helper.onEachTick(() -> {
			keepOnCrew(r);
			if (reloads[0] == 1 && anyCarried(r) && placed(helper, r, Items.STONE) < 3) {
				reload.run(); // while the lead holds the stone it took, the build unfinished
			}
			watch(helper, r, off);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloads[0] == 2, "the crewmate was reloaded " + reloads[0] + " times, not 2: " + where(helper, r));
			helper.assertTrue(lost[0] == null, lost[0] == null ? "" : lost[0]);
			assertFinished(helper, r, off);
			LOGGER.info("qa 1107e crew spare mate reload: {}", where(helper, r));
		});
	}

	/**
	 * Boundary: the crewmate holds exactly the three stone the build needs. A restart right after the take; all three
	 * are built, the crewmate's bag ends empty and nothing lies on the ground.
	 */
	//$ gametest_ticks_batch HUGE '1500' '"qa1107eCrewSpareExact"'
	@GameTest(template = HUGE, timeoutTicks = 1500, batch = "qa1107eCrewSpareExact")
	public void aCrewmateWithExactlyWhatIsNeededEndsEmptyAcrossARestart(GameTestHelper helper) {
		Run r = setUp(helper, 10, THREE_STONE, Map.of(Items.STONE, 3), QaCrewPass1107bGameTests.cell(helper).add(4, 0, 0));
		String[] off = {null};
		boolean[] restarted = {false};
		helper.onEachTick(() -> {
			keepOnCrew(r);
			if (!restarted[0] && anyCarried(r) && nonePlaced(helper, r)) {
				restarted[0] = true;
				restart(r);
			}
			watch(helper, r, off);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0], "the lead never carried the crewmate's stone unbuilt: " + where(helper, r));
			assertFinished(helper, r, off);
			LOGGER.info("qa 1107e crew spare exact: {}", where(helper, r));
		});
	}

	/**
	 * Boundary: the crewmate holds two stone, the build needs three, and there is no other stone. A restart right after
	 * the take. 700 ticks on (the lead long done with what it had): two built, nothing made, none on the ground, none
	 * left in either bag, and the lead never carried more than was left to build.
	 */
	//$ gametest_ticks_batch HUGE '900' '"qa1107eCrewSpareShort"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qa1107eCrewSpareShort")
	public void aCrewmateShortOfTheBuildGivesWhatItHasAndNoneIsLostAcrossARestart(GameTestHelper helper) {
		Run r = setUp(helper, 10, THREE_STONE, Map.of(Items.STONE, 2), QaCrewPass1107bGameTests.cell(helper).add(4, 0, 0));
		String[] off = {null};
		boolean[] restarted = {false};
		helper.onEachTick(() -> {
			keepOnCrew(r);
			if (!restarted[0] && anyCarried(r) && nonePlaced(helper, r)) {
				restarted[0] = true;
				restart(r);
			}
			watch(helper, r, off);
		});
		helper.runAtTickTime(700, () -> {
			helper.assertTrue(restarted[0], "the lead never carried the crewmate's stone unbuilt: " + where(helper, r));
			helper.assertTrue(off[0] == null, off[0] == null ? "" : off[0]);
			helper.assertTrue(placed(helper, r, Items.STONE) == 2 && bag(r.mate, Items.STONE) == 0 && bag(r.lead, Items.STONE) == 0
				&& r.lead.getInventory().countItem(Items.STONE) == 0 && onGround(helper, Items.STONE) == 0,
				"wanted the crewmate's two stone built and nothing else anywhere: " + where(helper, r));
			LOGGER.info("qa 1107e crew spare short: {}", where(helper, r));
			helper.succeed();
		});
	}

	/**
	 * Two kinds from one bag: two stone and two bricks to build, the crewmate holding a full stack of each. A restart
	 * the tick the lead first carries any of it. Per kind the lead never holds more than its unbuilt blocks need; at
	 * the end 62 of each are back in the crewmate's bag and none lies on the ground.
	 */
	//$ gametest_ticks_batch HUGE '1800' '"qa1107eCrewSpareTwoKinds"'
	@GameTest(template = HUGE, timeoutTicks = 1800, batch = "qa1107eCrewSpareTwoKinds")
	public void twoKindsFromOneBagAcrossARestartLeaveNoSpareOfEither(GameTestHelper helper) {
		Map<Item, Integer> stock = new LinkedHashMap<>();
		stock.put(Items.STONE, 64);
		stock.put(Items.BRICKS, 64);
		Run r = setUp(helper, 10, TWO_KINDS, stock, QaCrewPass1107bGameTests.cell(helper).add(4, 0, 0));
		String[] off = {null};
		boolean[] restarted = {false};
		helper.onEachTick(() -> {
			keepOnCrew(r);
			if (!restarted[0] && anyCarried(r) && nonePlaced(helper, r)) {
				restarted[0] = true;
				restart(r);
			}
			watch(helper, r, off);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0], "the lead never carried the crewmate's blocks unbuilt: " + where(helper, r));
			assertFinished(helper, r, off);
			LOGGER.info("qa 1107e crew spare two kinds: {}", where(helper, r));
		});
	}
}
