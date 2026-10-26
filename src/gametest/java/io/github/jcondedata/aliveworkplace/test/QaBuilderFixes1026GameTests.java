package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.Scaffolds;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane-b-1026-0938) of the builder fixes landed on 2026-10-26, from the spec (ROADMAP B488, B513, B514, B516, 35.6;
 * docs/wiki/builder.md and docs/wiki/scaffolding.md; en_us.json):
 * <ul>
 * <li>B516's kin: the LEAD killed while two helpers work (builder.md: a hired builder killed with a build "drops ... the
 * blueprint where he fell and his owner reads builder_died"; the crew stops, nothing doubled); a helper killed and the
 * sites and builders saved and loaded straight after (the lead still finishes, no hole, nothing doubled, no blueprint
 * dropped, the owner not told his builder died).</li>
 * <li>B514's kin: the queued take-down's line exact, and a queued build still announced as a build with its materials
 * line.</li>
 * <li>35.6: {@code builderScaffolding} switched off while scaffolds stand (they still come down, all back in the chest);
 * a cancel with scaffolds up and the supply chest full (no scaffold destroyed: in the chest, the bag or on the
 * ground).</li>
 * </ul>
 * Not repeated: a helper killed mid-build (QaBuilderCrewBugTests), a tower's builder killed with and without a restart
 * (QaWallTowerBugGameTests), the cancelled take-down's placement and sneak hand-back (QaTakeDownGameTests).
 */
public class QaBuilderFixes1026GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos LEAD = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos[] MATE_TABLES = {new BlockPos(13, 2, 2), new BlockPos(15, 2, 2)};

	// --- crews (B516 kin) ---------------------------------------------------------------------------------------------

	private record Crew(ServerLevel level, Villager[] all, UUID siteId, BuildPlan plan, Map<Item, Integer> stock,
		BlueprintData.Placement placement, QaStageFiveGameTests.Client owner) {
		BuildSite site() {
			return BuildSiteManager.get(level).get(siteId);
		}

		List<Villager> mates() {
			return List.of(all).subList(1, all.length);
		}
	}

	private static void crewRules(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		var rules = level.getGameRules();
		boolean help = rules.getRule(ModGameRules.BUILDERS_HELP).get();
		int delay = rules.getRule(ModGameRules.BUILD_DELAY).get();
		boolean free = rules.getRule(ModGameRules.FREE_MATERIALS).get();
		rules.getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		rules.getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection;
			rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			rules.getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite other : new ArrayList<>(sites.all())) {
			if (other.bench() != null && other.bench().closerThan(helper.absolutePos(TABLE), 3 * Builders.MAX_SITE_DISTANCE)) {
				sites.remove(other.id());
			}
		}
	}

	/** A player's hut started by the lead with twice its materials in the chest, two idle builders at their tables nearby. */
	private static Crew crew(GameTestHelper helper, String name) {
		crewRules(helper);
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client owner = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(name));
		helper.setBlock(TABLE, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager[] all = new Villager[MATE_TABLES.length + 1];
		all[0] = helper.spawn(EntityType.VILLAGER, LEAD);
		Builders.employ(level, all[0], helper.absolutePos(TABLE));
		for (int i = 0; i < MATE_TABLES.length; i++) {
			helper.setBlock(MATE_TABLES[i], ModBlocks.BUILDERS_BENCH);
			all[i + 1] = helper.spawn(EntityType.VILLAGER, MATE_TABLES[i].south().east());
			Builders.employ(level, all[i + 1], helper.absolutePos(MATE_TABLES[i]));
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, all[0], owner.player(), TEST_HUT, placement);
		helper.assertTrue(site != null, "setup: the hut didn't start");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "setup: no plan for the test hut");
		Map<Item, Integer> stock = new LinkedHashMap<>();
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			stock.put(e.getKey(), e.getValue() * 2);
			int left = e.getValue() * 2;
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		UUID id = site.id();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(id));
		return new Crew(level, all, id, plan, stock, placement, owner);
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

	/** Every unit of the hut's materials: placed in the hut, in the chest, in a living builder's bag, on the ground. */
	private static Map<Item, Integer> accounted(GameTestHelper helper, Crew c) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		Container chest = helper.getBlockEntity(CHEST);
		for (Item item : c.stock().keySet()) {
			out.put(item, chest.countItem(item));
		}
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : c.plan().steps(stage)) {
				if (MaterialRules.matches(c.level().getBlockState(step.pos()), step.state())) {
					for (MaterialRules.Requirement r : step.requirements()) {
						out.computeIfPresent(r.item(), (k, n) -> n + r.count());
					}
				}
			}
		}
		for (Villager v : c.all()) {
			if (v.isAlive()) {
				bag(v).forEach((item, n) -> out.computeIfPresent(item, (k, m) -> m + n));
			}
		}
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(12))) {
			out.computeIfPresent(e.getItem().getItem(), (k, m) -> m + e.getItem().getCount());
		}
		return out;
	}

	private static int blueprintsOnTheGround(GameTestHelper helper) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(12))) {
			n += e.getItem().is(ModItems.BLUEPRINT) ? e.getItem().getCount() : 0;
		}
		return n;
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	private static String where(Crew c) {
		BuildSite s = c.site();
		StringBuilder out = new StringBuilder(s == null ? "site gone" : "stage " + s.stage() + " status " + s.status() + " missing " + s.missing());
		out.append("; bags:");
		for (Villager v : c.all()) {
			out.append(' ').append(bag(v)).append(Builders.isHelping(v) ? " (helping)" : "").append(v.isAlive() ? "" : " (dead)");
		}
		return out.toString();
	}

	/**
	 * The lead (not a helper) killed while both helpers work: builder.md keeps the lead's death as before B516 ("a hired
	 * builder killed with a build ... drops ... the blueprint where he fell and his owner reads builder_died with the
	 * build's name and the spot"). So: the site is gone at once, exactly one blueprint lies on the ground, the owner reads
	 * builder_died with the hut's name; the helpers stop helping within 200 ticks and nothing more is built after that;
	 * and the hut's materials are neither doubled nor lost (placed + chest + living bags + ground = what was stocked).
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaFix1026LeadKilled"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaFix1026LeadKilled")
	public void theLeadKilledWithTwoHelpersEndsTheBuildOnceAndDoublesNothing(GameTestHelper helper) {
		Crew c = crew(helper, "qaFxL");
		long[] killedAt = {-1};
		int[] frozen = {-1};
		String[] at = {null};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (killedAt[0] < 0) {
				BuildSite s = c.site();
				if (s == null) {
					helper.fail("setup: the hut finished before the lead could be killed with both helpers on it: " + where(c));
				}
				long helping = c.mates().stream().filter(Builders::isHelping).count();
				if (helping == 2 && c.mates().stream().anyMatch(m -> !bag(m).isEmpty()) && s.stage() == BuildPlan.Stage.STRUCTURE) {
					at[0] = where(c);
					c.owner().seen().clear();
					BlockPos fell = c.all()[0].blockPosition();
					c.all()[0].kill();
					killedAt[0] = helper.getTick();
					helper.assertTrue(c.site() == null, "the lead died but his site is still open: " + where(c));
					String hut = Blueprints.displayName(TEST_HUT).getString();
					String line = "Your builder died while building " + hut + ". Their tools and the blueprint dropped at " + fell.getX() + " " + fell.getY() + " " + fell.getZ() + ".";
					helper.assertTrue(texts(c.owner().seen()).contains(line), "the owner wasn't told \"" + line + "\": " + texts(c.owner().seen()));
					LOGGER.info("qa fix1026 killed the lead at tick {}: {}", helper.getTick(), at[0]);
				}
				return;
			}
			long since = helper.getTick() - killedAt[0];
			if (since == 200) {
				for (Villager m : c.mates()) {
					helper.assertTrue(!Builders.isHelping(m), "200 ticks after the lead died a helper still helps: " + where(c));
				}
				frozen[0] = c.plan().unfinished(c.level()).size();
			} else if (since > 200 && c.plan().unfinished(c.level()).size() != frozen[0]) {
				helper.fail("blocks of the dead lead's hut changed after the crew stopped: " + frozen[0] + " unbuilt, then "
					+ c.plan().unfinished(c.level()).size());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(killedAt[0] >= 0 && helper.getTick() - killedAt[0] >= 400, "waiting (killed at " + killedAt[0] + ")");
			helper.assertTrue(blueprintsOnTheGround(helper) == 1, blueprintsOnTheGround(helper) + " blueprints on the ground after the lead died (wanted 1)");
			helper.assertTrue(frozen[0] > 0, "the hut was finished though its lead died (" + frozen[0] + " unbuilt)");
			Map<Item, Integer> now = accounted(helper, c);
			helper.assertTrue(now.equals(c.stock()), "the hut's materials after the lead died: placed + chest + bags + ground is " + now
				+ ", stocked " + c.stock() + " (killed with " + at[0] + "; now " + where(c) + ")");
		});
	}

	/**
	 * B516 with a restart: a helper killed while it holds a claimed step and materials, then the sites and every living
	 * builder saved and loaded straight away. The lead and the other helper finish the hut with no hole; nothing doubled
	 * or lost (placed + chest + bags + ground = the stock); no blueprint dropped; the owner never reads builder_died.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaFix1026HelperReload"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaFix1026HelperReload")
	public void aHelperKilledThenASaveAndLoadStillFinishesTheHutWhole(GameTestHelper helper) {
		Crew c = crew(helper, "qaFxH");
		String[] killed = {null};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (killed[0] != null || c.site() == null) {
				return;
			}
			for (int i = 1; i < c.all().length; i++) {
				Villager mate = c.all()[i];
				BuildSite s = c.site();
				if (Builders.isHelping(mate) && !bag(mate).isEmpty() && s.claim(mate.getUUID()) != null) {
					killed[0] = "bag " + bag(mate) + ", " + where(c);
					c.owner().seen().clear();
					mate.kill();
					helper.assertTrue(c.site() != null, "a helper's death ended the lead's build: " + killed[0]);
					// the restart: sites and every living builder saved and loaded
					var registries = c.level().registryAccess();
					c.level().getDataStorage().set(BuildSiteManager.NAME,
						BuildSiteManager.load(BuildSiteManager.get(c.level()).save(new CompoundTag(), registries), registries));
					helper.assertTrue(c.site() != null, "the hut's site was lost in the save after a helper died");
					for (int j = 0; j < c.all().length; j++) {
						Villager v = c.all()[j];
						if (!v.isAlive()) {
							continue;
						}
						CompoundTag tag = new CompoundTag();
						helper.assertTrue(v.save(tag), "a builder wasn't saved");
						v.discard();
						Entity loaded = EntityType.loadEntityRecursive(tag, c.level(), e -> e);
						helper.assertTrue(loaded instanceof Villager, "a builder didn't load as a villager: " + loaded);
						c.level().addFreshEntity(loaded);
						c.all()[j] = (Villager) loaded;
					}
					LOGGER.info("qa fix1026 killed a helper and reloaded at tick {}: {}", helper.getTick(), killed[0]);
					return;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(killed[0] != null, "setup: no helper ever had a claimed step and a full bag: " + where(c));
			helper.assertTrue(c.site() == null, "the hut isn't finished: " + where(c));
			List<BlockPos> wrong = c.plan().unfinished(c.level());
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut missing after a helper died and a reload (" + killed[0] + ")");
			helper.assertTrue(blueprintsOnTheGround(helper) == 0, "a blueprint dropped though the lead lived");
			helper.assertTrue(texts(c.owner().seen()).stream().noneMatch(t -> t.startsWith("Your builder died")),
				"the owner was told his builder died: " + texts(c.owner().seen()));
			Map<Item, Integer> now = accounted(helper, c);
			helper.assertTrue(now.equals(c.stock()), "after a helper died and a reload, placed + chest + bags + ground is " + now
				+ ", stocked " + c.stock() + " (" + killed[0] + ")");
		});
	}

	// --- queued lines (B514 kin) ----------------------------------------------------------------------------------------

	private static ServerLevel soloRules(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		var rules = level.getGameRules();
		int delay = rules.getRule(ModGameRules.BUILD_DELAY).get();
		boolean help = rules.getRule(ModGameRules.BUILDERS_HELP).get();
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		rules.getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> {
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection;
		});
		return level;
	}

	private static Villager soloBuilder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, LEAD);
		Builders.employ(level, villager, helper.absolutePos(TABLE));
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (villager.getUUID().equals(site.builder())) {
					sites.remove(site.id());
				}
			}
		});
		return villager;
	}

	static BlueprintData.Placement standingHut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(HUT);
		level.getStructureManager().get(TEST_HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(7L), Block.UPDATE_ALL);
		return new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
	}

	static ItemStack placed(BlueprintData.Placement placement) {
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, null);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(placement)));
		return blueprint;
	}

	static void handOver(QaStageFiveGameTests.Client c, Villager villager, boolean sneak) {
		c.player().setShiftKeyDown(sneak);
		QaStageSixGameTests.clickVillager(c.player(), villager, c.seen());
		c.player().setShiftKeyDown(false);
	}

	/**
	 * B514 and the line it keeps: a busy builder given a take-down (sneak hand-over over a standing hut) says exactly
	 * "Builder will take down Test Hut after Test Hut (number 1 in line). It needs no materials." (builder.md's example,
	 * American English), and given a second ordinary blueprint it still says the build's line, "... will build Test Hut
	 * after Test Hut (number 2 in line). Stock the materials for it too."
	 */
	//$ gametest_ticks_batch AREA '200' '"qaFix1026QueueLines"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFix1026QueueLines")
	public void queuedTakeDownAndQueuedBuildEachSayTheirOwnLine(GameTestHelper helper) {
		ServerLevel level = soloRules(helper);
		BlueprintData.Placement standing = standingHut(helper);
		Villager villager = soloBuilder(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaFxQ"));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placed(new BlueprintData.Placement(level.dimension().location(),
			helper.absolutePos(new BlockPos(11, 2, 0)), Rotation.NONE, Mirror.NONE)));
		handOver(c, villager, false);
		helper.assertTrue(Builders.activeSite(level, villager) != null, "the first hand-over started nothing: " + texts(c.seen()));
		String name = villager.getDisplayName().getString();
		String hut = Blueprints.displayName(TEST_HUT).getString();
		c.seen().clear();
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placed(standing));
		handOver(c, villager, true);
		String takeDown = name + " will take down " + hut + " after " + hut + " (number 1 in line). It needs no materials.";
		helper.assertTrue(texts(c.seen()).contains(takeDown), "a queued take-down said " + texts(c.seen()) + ", wanted \"" + takeDown + "\"");
		c.seen().clear();
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placed(new BlueprintData.Placement(level.dimension().location(),
			helper.absolutePos(new BlockPos(0, 2, 11)), Rotation.NONE, Mirror.NONE)));
		handOver(c, villager, false);
		List<BuildSite> queue = Builders.queue(level, villager);
		helper.assertTrue(queue.size() == 2 && queue.get(0).isDeconstruction() && !queue.get(1).isDeconstruction(),
			"the queue after a take-down and a build: " + queue + ": " + texts(c.seen()));
		String build = name + " will build " + hut + " after " + hut + " (number 2 in line). Stock the materials for it too.";
		helper.assertTrue(texts(c.seen()).contains(build), "a queued build said " + texts(c.seen()) + ", wanted \"" + build + "\"");
		LOGGER.info("qa fix1026 queue lines: \"{}\" / \"{}\"", takeDown, build);
		helper.succeed();
	}

	// --- scaffolding (35.6) ----------------------------------------------------------------------------------------------

	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos S_CHEST = new BlockPos(3, 2, 5);
	private static final BlockPos S_VILLAGER = new BlockPos(4, 2, 4);
	private static final BlockPos PILLAR_AT = new BlockPos(11, 2, 11);
	private static final int SCAFFOLDING = 64;

	private record Pillar(ServerLevel level, Villager builder, BuildSite site, BuildPlan plan) {
	}

	private static Pillar pillar(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		var rules = level.getGameRules();
		int delay = rules.getRule(ModGameRules.BUILD_DELAY).get();
		boolean help = rules.getRule(ModGameRules.BUILDERS_HELP).get();
		boolean free = rules.getRule(ModGameRules.FREE_MATERIALS).get();
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		rules.getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		rules.getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			rules.getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, S_VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, PILLAR,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(PILLAR_AT), Rotation.NONE, Mirror.NONE));
		if (site == null) {
			throw new GameTestAssertException("the pillar didn't start");
		}
		UUID id = site.id();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(id));
		helper.setBlock(S_CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(S_CHEST);
		chest.setItem(0, new ItemStack(Items.STONE_BRICKS, 14));
		chest.setItem(1, new ItemStack(Items.POLISHED_ANDESITE, 25));
		chest.setItem(2, new ItemStack(Items.SCAFFOLDING, SCAFFOLDING));
		return new Pillar(level, villager, site, site.plan(level));
	}

	private static int standingScaffolds(GameTestHelper helper) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(21, 29, 21)))) {
			n += helper.getLevel().getBlockState(p).is(Blocks.SCAFFOLDING) ? 1 : 0;
		}
		return n;
	}

	/** Scaffolding as items: in the chest, the living builder's bag, lying on the ground. */
	private static int[] scaffoldingItems(GameTestHelper helper, Pillar p) {
		Container chest = helper.getBlockEntity(S_CHEST);
		int inChest = chest.countItem(Items.SCAFFOLDING);
		int inBag = p.builder().isAlive() ? ModAttachments.BUILDER_BAG.getOrCreate(p.builder()).count(Items.SCAFFOLDING) : 0;
		int loose = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(12))) {
			loose += e.getItem().is(Items.SCAFFOLDING) ? e.getItem().getCount() : 0;
		}
		return new int[]{inChest, inBag, loose};
	}

	/**
	 * scaffolding.md: scaffolds are taken down "when a stage's list is finished", and builderScaffolding off means
	 * "those blocks are skipped as before". The switch is turned off while scaffolds stand mid-build: the pillar's build
	 * still ends, not one scaffold is left standing, the site lists none, and all 64 are back in the chest (none lost,
	 * none in the bag, none on the ground).
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaFix1026ScaffoldOff"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaFix1026ScaffoldOff")
	public void scaffoldingSwitchedOffWithScaffoldsUpStillTakesThemAllDown(GameTestHelper helper) {
		boolean was = Scaffolds.ENABLED;
		Leftovers.after(helper, () -> Scaffolds.ENABLED = was);
		Scaffolds.ENABLED = true;
		Pillar p = pillar(helper);
		int[] upWhenOff = {0};
		helper.onEachTick(() -> {
			if (upWhenOff[0] == 0 && p.site().scaffolds().size() >= 2) {
				upWhenOff[0] = p.site().scaffolds().size();
				Scaffolds.ENABLED = false;
				LOGGER.info("qa fix1026 switched scaffolding off with {} up at tick {}", upWhenOff[0], helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(upWhenOff[0] > 0, "no scaffolding up yet");
			helper.assertTrue(BuildSiteManager.get(p.level()).get(p.site().id()) == null, "still building: stage " + p.site().stage()
				+ " scaffolds " + p.site().scaffolds().size());
			helper.assertTrue(standingScaffolds(helper) == 0, standingScaffolds(helper) + " scaffolding block(s) left standing after the build (switched off with "
				+ upWhenOff[0] + " up)");
			helper.assertTrue(p.site().scaffolds().isEmpty(), "the site still lists scaffolds: " + p.site().scaffolds());
			int[] items = scaffoldingItems(helper, p);
			helper.assertTrue(items[0] == SCAFFOLDING, "scaffolding after the build: " + items[0] + " in the chest, " + items[1] + " in the bag, "
				+ items[2] + " on the ground; wanted all " + SCAFFOLDING + " in the chest");
		});
	}

	/**
	 * scaffolding.md: a canceled site's scaffolds are "all taken down at once, into the supply chests by its bench (else
	 * dropped there)". Cancelled with scaffolds up and the supply chest stuffed full (every free slot filled with dirt and
	 * every part stack topped up at the moment of the cancel): none is left standing and none destroyed: the chest, the bag and the ground hold all 64.
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaFix1026ScaffoldFull"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaFix1026ScaffoldFull")
	public void cancelWithTheChestFullDropsTheScaffoldingAndLosesNone(GameTestHelper helper) {
		boolean was = Scaffolds.ENABLED;
		Leftovers.after(helper, () -> Scaffolds.ENABLED = was);
		Scaffolds.ENABLED = true;
		Pillar p = pillar(helper);
		int[] cancelledWith = {0};
		int[] toppedUp = {0};
		helper.onEachTick(() -> {
			if (cancelledWith[0] == 0 && p.site().scaffolds().size() >= 2) {
				cancelledWith[0] = p.site().scaffolds().size();
				Container chest = helper.getBlockEntity(S_CHEST);
				for (int i = 0; i < chest.getContainerSize(); i++) {
					ItemStack in = chest.getItem(i);
					if (in.isEmpty()) {
						chest.setItem(i, new ItemStack(Items.DIRT, 64));
					} else if (in.getCount() < in.getMaxStackSize()) {
						// no room in a part stack either: a part stack of scaffolding is topped up, and counted
						toppedUp[0] += in.is(Items.SCAFFOLDING) ? in.getMaxStackSize() - in.getCount() : 0;
						in.setCount(in.getMaxStackSize());
						chest.setItem(i, in);
					}
				}
				Builders.cancel(p.level(), p.site());
				LOGGER.info("qa fix1026 cancelled with a full chest and {} scaffolds up", cancelledWith[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(cancelledWith[0] > 0, "no scaffolding up yet");
			helper.assertTrue(BuildSiteManager.get(p.level()).get(p.site().id()) == null, "the build wasn't cancelled");
			helper.assertTrue(standingScaffolds(helper) == 0, standingScaffolds(helper) + " scaffolding block(s) left standing after the cancel");
			int[] items = scaffoldingItems(helper, p);
			helper.assertTrue(items[0] + items[1] + items[2] == SCAFFOLDING + toppedUp[0], "scaffolding after a cancel with the chest full: " + items[0] + " in the chest, "
				+ items[1] + " in the bag, " + items[2] + " on the ground; wanted " + SCAFFOLDING + " + " + toppedUp[0] + " topped up in all (cancelled with "
				+ cancelledWith[0] + " up)");
			helper.assertTrue(items[2] > 0, "nothing was dropped though the chest was full (" + items[0] + " in the chest, " + items[1] + " in the bag)");
		});
	}
}
