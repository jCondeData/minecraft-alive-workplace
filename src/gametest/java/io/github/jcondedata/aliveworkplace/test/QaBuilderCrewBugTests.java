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
 * BUG (QA lane-b-1026-0338, not registered): a helper's death ends the lead's build. From the spec of builder crews, from the spec: docs/wiki/builder.md ("Up to 3 idle Builders ... help one
 * site, each claiming steps ahead of the lead", "Idle Builders walk over and help other Builders' builds nearby", the
 * bag "empties at the chests when it finishes or stops helping", "never more of a thing than the stretch of work ahead
 * still needs", game rule {@code workplaceBuildersHelp} "Idle Builders help builds near their bench", "Safe at height
 * ... (lead or helper)") and docs/guide/builder.md ("Up to three builders with nothing to do join a build near their
 * table"). Not repeated here: the crew's speed and exact material list (BuilderCrewGameTests), three helpers not four
 * (QaBuilderLimitsGameTests) and the Guild's five (GuildGameTests).
 */
public class QaBuilderCrewBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
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

	// --- a helper killed --------------------------------------------------------------------------------------------

	/**
	 * A helper killed while it has a step claimed and materials in its bag: the lead and the other helper still build
	 * every block of the hut (no hole where its claimed steps were), and the hut's materials are never doubled: what is
	 * left in the chest, the bags and on the ground is at most the spare (twice the list was stocked).
	 */
	//$ gametest_ticks_batch AREA '5000' '"qaCrewKilled"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaCrewKilled")
	public void aHelperKilledMidBuildLeavesNoHoleAndDoublesNothing(GameTestHelper helper) {
		prepare(helper);
		Crew c = crew(helper, 2, 2);
		String[] killed = {null};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (killed[0] != null || c.finished()) {
				return;
			}
			for (Villager mate : c.mates()) {
				if (Builders.isHelping(mate) && !bag(mate).isEmpty() && c.site().claim(mate.getUUID()) != null) {
					killed[0] = "claimed " + c.site().claim(mate.getUUID()).subtract(c.placement().origin()).toShortString() + ", bag " + bag(mate)
						+ ", " + where(c);
					mate.kill();
					helper.assertTrue(!c.finished(), "BUG: a helper killed mid-build ended the lead's build: its site is gone with "
						+ c.plan().unfinished(c.level()).size() + " blocks unbuilt (" + killed[0] + ")");
					LOGGER.info("qa crew killed a helper at tick {}: {}", helper.getTick(), killed[0]);
					return;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(killed[0] != null, "setup: no helper ever had a step claimed and a full bag: " + where(c));
			assertBuilt(helper, c, "after a helper was killed (" + killed[0] + ")");
			Map<Item, Integer> held = held(helper, c);
			for (Map.Entry<Item, Integer> e : c.materials().entrySet()) {
				helper.assertTrue(held.get(e.getKey()) <= e.getValue(), "a killed helper's materials were doubled: of " + e.getKey()
					+ " the spare is " + e.getValue() + " but " + held.get(e.getKey()) + " are held (" + killed[0] + ")");
			}
		});
	}

}
