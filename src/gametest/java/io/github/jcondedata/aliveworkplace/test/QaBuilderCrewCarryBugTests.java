package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * BUG (QA lane-b-1026-0338, not registered; intermittent, 1 run in 6): a helper's bag filled with 8 of the hut's
 * materials with 2 blocks left (wiki: "never more of a thing than the stretch of work ahead still needs").
 * QA (lane-b-1026-0338) of builder crews, from the spec: docs/wiki/builder.md ("Up to 3 idle Builders ... help one
 * site, each claiming steps ahead of the lead", "Idle Builders walk over and help other Builders' builds nearby", the
 * bag "empties at the chests when it finishes or stops helping", "never more of a thing than the stretch of work ahead
 * still needs", game rule {@code workplaceBuildersHelp} "Idle Builders help builds near their bench", "Safe at height
 * ... (lead or helper)") and docs/guide/builder.md ("Up to three builders with nothing to do join a build near their
 * table"). Not repeated here: the crew's speed and exact material list (BuilderCrewGameTests), three helpers not four
 * (QaBuilderLimitsGameTests) and the Guild's five (GuildGameTests).
 */
public class QaBuilderCrewCarryBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos LEAD = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos[] MATE_TABLES = {new BlockPos(13, 2, 2), new BlockPos(15, 2, 2), new BlockPos(2, 2, 13)};

	/** A lead, its hut under way, and {@code mates} idle builders at their own tables nearby. */
	private record Crew(ServerLevel level, Villager[] all, BuildSite site, BuildPlan plan, Map<Item, Integer> materials,
		BlueprintData.Placement placement) {
		Villager lead() {
			return all[0];
		}

		List<Villager> mates() {
			return List.of(all).subList(1, all.length);
		}

		boolean finished() {
			return BuildSiteManager.get(level).get(site.id()) == null;
		}
	}

	/** Clean area and rules: helping on, a quick build delay, no village protection, no sites nearby from earlier batches. */
	private static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		var rules = level.getGameRules();
		boolean help = rules.getRule(ModGameRules.BUILDERS_HELP).get();
		int delay = rules.getRule(ModGameRules.BUILD_DELAY).get();
		rules.getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		Leftovers.after(helper, () -> {
			io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection;
			rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
		});
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite other : new ArrayList<>(sites.all())) {
			if (other.bench() != null && other.bench().closerThan(helper.absolutePos(TABLE), 3 * Builders.MAX_SITE_DISTANCE)) {
				sites.remove(other.id());
			}
		}
	}

	/** The lead's hut started with {@code stock} times its materials in the chest, and {@code mates} idle builders. */
	private static Crew crew(GameTestHelper helper, int mates, int stock) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager[] all = new Villager[mates + 1];
		all[0] = helper.spawn(EntityType.VILLAGER, LEAD);
		Builders.employ(level, all[0], helper.absolutePos(TABLE));
		for (int i = 0; i < mates; i++) {
			helper.setBlock(MATE_TABLES[i], ModBlocks.BUILDERS_BENCH);
			all[i + 1] = helper.spawn(EntityType.VILLAGER, MATE_TABLES[i].south().east());
			Builders.employ(level, all[i + 1], helper.absolutePos(MATE_TABLES[i]));
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, all[0], null, TEST_HUT, placement);
		helper.assertTrue(site != null, "setup: the hut didn't start");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "setup: no plan for the test hut");
		Map<Item, Integer> materials = plan.materials();
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			int left = e.getValue() * stock;
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		return new Crew(level, all, site, plan, materials, placement);
	}

	private static Map<Item, Integer> bag(Villager villager) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (ItemStack stack : ModAttachments.BUILDER_BAG.getOrCreate(villager).stacks()) {
			if (!stack.isEmpty()) {
				out.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return out;
	}

	/** The hut's materials in the chest, in every living crew member's bag and lying on the ground round the area. */
	private static Map<Item, Integer> held(GameTestHelper helper, Crew c) {
		Container chest = helper.getBlockEntity(CHEST);
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Item item : c.materials().keySet()) {
			int n = chest.countItem(item);
			for (Villager v : c.all()) {
				if (v.isAlive()) {
					n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
				}
			}
			for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> c.materials().containsKey(e.getItem().getItem()))) {
				if (e.getItem().getItem() == item) {
					n += e.getItem().getCount();
				}
			}
			out.put(item, n);
		}
		return out;
	}

	private static Map<Item, Integer> times(Map<Item, Integer> materials, int k) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		materials.forEach((item, n) -> out.put(item, n * k));
		return out;
	}

	private static String where(Crew c) {
		StringBuilder s = new StringBuilder("stage " + c.site().stage() + " status " + c.site().status() + " placed " + c.site().placed()
			+ " missing " + c.site().missing() + "; bags:");
		for (Villager v : c.all()) {
			s.append(' ').append(bag(v)).append(Builders.isHelping(v) ? " (helping)" : "").append(v.isAlive() ? "" : " (dead)");
		}
		return s.toString();
	}

	private static void assertBuilt(GameTestHelper helper, Crew c, String when) {
		helper.assertTrue(c.finished(), when + ": the hut isn't finished: " + where(c));
		List<BlockPos> wrong = c.plan().unfinished(c.level());
		helper.assertTrue(wrong.isEmpty(), when + ": " + wrong.size() + " block(s) of the hut missing or wrong (a hole), e.g. "
			+ wrong.stream().limit(5).map(p -> p.subtract(c.placement().origin()).toShortString()).toList());
	}

	// --- the bag, at the end and on the way -------------------------------------------------------------------------

	/**
	 * Wiki: the bag holds "never more of a thing than the stretch of work ahead still needs" and "empties at the chests
	 * when it finishes or stops helping"; "Idle Builders ... help"; "Safe at height ... (lead or helper)". Two helpers on
	 * a hut with twice its materials in the chest: a helper on the job takes no fall damage; no bag ever fills with more of
	 * the hut's materials than blocks are left to place; once the hut is done every helper is idle again with an empty
	 * bag, exactly the spare half is back in the chest (nothing lost, nothing doubled), and a fall hurts the idle helper.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaCrewCarryBug"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaCrewCarryBug")
	public void noBagFillsPastTheBlocksLeft(GameTestHelper helper) {
		prepare(helper);
		Crew c = crew(helper, 2, 2);
		boolean[] fallTried = {false};
		int[] most = {0};
		int[] doneAt = {-1};
		int[] lastCarried = new int[c.all().length];
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (c.finished()) {
				if (doneAt[0] < 0) {
					doneAt[0] = (int) helper.getTick();
				}
				return;
			}
			int left = c.plan().unfinished(c.level()).size();
			for (int i = 0; i < c.all().length; i++) {
				Villager v = c.all()[i];
				int carried = 0;
				for (Item item : c.materials().keySet()) {
					carried += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
				}
				most[0] = Math.max(most[0], carried);
				// The moment a bag fills (a fetch from the chest or a hand from a crewmate) it holds no more than is left to place.
				helper.assertTrue(carried <= lastCarried[i] || carried <= left, "builder " + i + " took more of the hut's materials ("
					+ carried + ") than " + left + " blocks left to place (" + bag(v) + ")");
				lastCarried[i] = carried;
			}
			for (Villager mate : c.mates()) {
				if (!fallTried[0] && Builders.isHelping(mate)) {
					fallTried[0] = true;
					float before = mate.getHealth();
					mate.causeFallDamage(12, 1, c.level().damageSources().fall());
					helper.assertTrue(mate.getHealth() == before, "a helper on the job took fall damage: " + before + " -> " + mate.getHealth());
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fallTried[0], "no idle builder ever helped: " + where(c));
			assertBuilt(helper, c, "with two helpers");
			for (Villager mate : c.mates()) {
				helper.assertTrue(!Builders.isHelping(mate) && !ModAttachments.BUILDER_JOB.has(mate), "a helper is still on the finished hut's job");
				helper.assertTrue(bag(mate).isEmpty(), "a helper's bag isn't empty after the hut is done: " + bag(mate));
			}
			helper.assertTrue(bag(c.lead()).isEmpty(), "the lead's bag isn't empty after the hut is done: " + bag(c.lead()));
			Map<Item, Integer> held = held(helper, c);
			helper.assertTrue(held.equals(c.materials()), "after the build the chest should hold exactly the spare " + c.materials()
				+ " but the hut's materials held are " + held);
			Villager idle = c.mates().get(0);
			float before = idle.getHealth();
			idle.causeFallDamage(12, 1, c.level().damageSources().fall());
			helper.assertTrue(idle.getHealth() < before, "an idle builder (no build) took no fall damage: " + before + " -> " + idle.getHealth());
			LOGGER.info("qa crew end: done at tick {}, most carried by one builder {}", doneAt[0], most[0]);
		});
	}
}
