package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 28.13, the parts that need no Cobblemon (this suite runs without it): the Pokémon jobs' builds are left out
 * of the Blueprint Table (but the Gem Grotto, whose builder test is here), each upgrade keeps most of its first tier,
 * their names and their place on the Village Map.
 * With Cobblemon (a builder builds each, job block and all): {@code PokemonBuildsCompatTests}.
 */
public class PokemonBuildsGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";

	/**
	 * Without Cobblemon none of them is in the Blueprint Table (their job blocks are Cobblemon's or for its jobs), but
	 * the Gem Grotto, both tiers: the Gem Grower works without Cobblemon, at a stonecutter.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePokemonJobBuildsNeedCobblemon(GameTestHelper helper) {
		List<ResourceLocation> listed = TableServer.listing(helper.getLevel().getServer()).stream().map(e -> e.id()).toList();
		helper.assertTrue(StarterBlueprints.JOB_BUILDS.size() >= 10, "only " + StarterBlueprints.JOB_BUILDS.size() + " builds");
		helper.assertTrue(StarterBlueprints.VANILLA_JOB_BUILDS.equals(List.of(StarterBlueprints.GEM_GROTTO, StarterBlueprints.GEM_GROTTO_2)),
			"vanilla job builds: " + StarterBlueprints.VANILLA_JOB_BUILDS);
		for (StarterBlueprints.Entry entry : StarterBlueprints.JOB_BUILDS) {
			boolean vanilla = StarterBlueprints.VANILLA_JOB_BUILDS.contains(entry);
			helper.assertTrue(StarterBlueprints.COBBLEMON_ONLY.contains(entry) != vanilla, entry.id() + (vanilla ? " is" : " isn't") + " Cobblemon-only");
			helper.assertTrue(listed.contains(entry.id()) == vanilla, entry.id() + (vanilla ? " isn't" : " is") + " in the Blueprint Table without Cobblemon");
			helper.assertTrue(BlueprintLibrary.list(helper.getLevel().getServer(), true).contains(entry.id()), entry.id() + " should still load for operators");
		}
		helper.succeed();
	}

	/** The Gem Grotto: its stonecutter (the Gem Grower's) by the door, the lava behind glass, the amethyst niche. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"gem_grotto_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "gem_grotto_build")
	public void aBuilderBuildsTheGemGrotto(GameTestHelper helper) {
		buildGrotto(helper, StarterBlueprints.GEM_GROTTO, List.of());
	}

	/** Gem Grotto II: the chamber behind, its four crystal cores plain deepslate without Cobblemon 1.8. */
	//$ gametest_ticks_batch HUGE_AREA '60000' '"gem_grotto_2_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 60000, batch = "gem_grotto_2_build")
	public void aBuilderBuildsGemGrottoII(GameTestHelper helper) {
		buildGrotto(helper, StarterBlueprints.GEM_GROTTO_2, StarterBlueprints.GEM_GROTTO_2_CORES);
	}

	/**
	 * Builds {@code entry} by (9, 2, 3) from barrels holding exactly its materials (a lava bucket among them); when it's
	 * done every block is right, nothing was skipped, the stonecutter stands by the door and is a job site, the six lava
	 * blocks are in their basin behind the glass and nowhere else, nothing burns, the niche has its amethyst, and each
	 * of {@code deepslate} is plain deepslate.
	 */
	private static void buildGrotto(GameTestHelper helper, StarterBlueprints.Entry entry, List<BlockPos> deepslate) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlockPos origin = new BlockPos(9, 2, 3);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + entry.id());
		}
		helper.assertTrue(plan.materials().getOrDefault(Items.LAVA_BUCKET, 0) == 6, "the lava costs " + plan.materials().get(Items.LAVA_BUCKET) + " buckets");
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		BlockPos at = placement.origin();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(3)
				.map(p -> p.subtract(at).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
			BlockPos cutter = at.offset(4, 1, 2);
			helper.assertTrue(level.getBlockState(cutter).is(Blocks.STONECUTTER), "no stonecutter by the door: " + level.getBlockState(cutter));
			helper.assertTrue(level.getPoiManager().getType(cutter).isPresent(), "the stonecutter is no job site");
			int lava = 0;
			for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, 0, -2), at.offset(entry.size().getX() + 1, entry.size().getY(), entry.size().getZ() + 1))) {
				BlockState state = level.getBlockState(p);
				helper.assertFalse(state.is(Blocks.FIRE), "fire at " + p.subtract(at).toShortString());
				if (state.getFluidState().is(FluidTags.LAVA)) {
					BlockPos spot = p.subtract(at);
					helper.assertTrue(spot.getY() == 1 && spot.getX() >= 5 && spot.getX() <= 7 && spot.getZ() >= 5 && spot.getZ() <= 6,
						"lava got out to " + spot.toShortString());
					helper.assertTrue(state.getFluidState().isSource(), "flowing lava at " + spot.toShortString());
					lava++;
				}
			}
			helper.assertTrue(lava == 6, lava + " lava blocks, not 6");
			for (int x = 5; x <= 7; x++) {
				helper.assertTrue(level.getBlockState(at.offset(x, 1, 4)).is(Blocks.GLASS), "no glass before the lava at x " + x);
			}
			helper.assertTrue(level.getBlockState(at.offset(0, 1, 6)).is(Blocks.AMETHYST_BLOCK)
				&& level.getBlockState(at.offset(1, 1, 6)).is(Blocks.AMETHYST_CLUSTER), "the amethyst niche isn't there");
			for (BlockPos core : deepslate) {
				helper.assertTrue(level.getBlockState(at.offset(core)).is(Blocks.DEEPSLATE), "no deepslate core at " + core.toShortString()
					+ ": " + level.getBlockState(at.offset(core)));
			}
		});
	}

	/** Each build comes in two tiers, and the second is drawn over the first: it keeps at least 60% of its blocks. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachPokemonJobUpgradeKeepsItsBase(GameTestHelper helper) {
		int upgrades = 0;
		for (StarterBlueprints.Entry entry : StarterBlueprints.JOB_BUILDS) {
			Optional<ResourceLocation> baseId = BlueprintUpgrades.baseOf(entry.id());
			if (baseId.isEmpty()) {
				helper.assertTrue(StarterBlueprints.JOB_BUILDS.stream().anyMatch(e -> e.id().equals(BlueprintUpgrades.upgradeOf(entry.id()))),
					entry.id() + " has no upgrade");
				continue;
			}
			upgrades++;
			Blueprint base = BlueprintLibrary.get(helper.getLevel(), baseId.get()).orElseThrow();
			Blueprint upgrade = BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow();
			helper.assertTrue(upgrade.size().getX() >= base.size().getX() && upgrade.size().getY() >= base.size().getY()
				&& upgrade.size().getZ() >= base.size().getZ(), entry.id() + " is smaller than " + baseId.get());
			Map<BlockPos, BlockState> up = new HashMap<>();
			upgrade.blocks().forEach(e -> up.put(e.pos(), e.state()));
			long solid = base.blocks().stream().filter(e -> !e.state().isAir()).count();
			long kept = base.blocks().stream().filter(e -> !e.state().isAir() && e.state().equals(up.get(e.pos()))).count();
			helper.assertTrue(solid > 150, baseId.get() + " is only " + solid + " blocks");
			helper.assertTrue(kept >= solid * 0.6, entry.id() + " keeps only " + kept + " of " + baseId.get() + "'s " + solid + " blocks");
		}
		helper.assertTrue(upgrades * 2 == StarterBlueprints.JOB_BUILDS.size(), upgrades + " upgrades for " + StarterBlueprints.JOB_BUILDS.size() + " builds");
		helper.succeed();
	}

	/** On the Village Map with the farms (the Daycare with the care buildings), and none of them counts as a Pokémon Center. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePokemonJobBuildsAreOnTheMap(GameTestHelper helper) {
		for (StarterBlueprints.Entry entry : StarterBlueprints.JOB_BUILDS) {
			VillageMaps.Kind kind = VillageMaps.kindOf(entry.id()).orElse(null);
			helper.assertTrue(kind == VillageMaps.Kind.FARMS || kind == VillageMaps.Kind.CARE, entry.id() + " is on the map as " + kind);
		}
		helper.assertTrue(VillageMaps.kindOf(StarterBlueprints.DAYCARE_2.id()).orElse(null) == VillageMaps.Kind.CARE, "the Daycare cares");
		helper.assertTrue(VillageAdvice.wantsPokemonCenter(true, VillageRanks.Rank.TOWN,
			StarterBlueprints.JOB_BUILDS.stream().map(StarterBlueprints.Entry::id).toList()), "a Camp Kitchen isn't a Pokémon Center");
		helper.succeed();
	}

	/** Every name a player reads for them is in English. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePokemonJobBuildsAreTranslated(GameTestHelper helper) {
		Language english = Language.getInstance();
		for (StarterBlueprints.Entry entry : StarterBlueprints.JOB_BUILDS) {
			String key = "blueprint." + entry.id().getNamespace() + "." + entry.id().getPath();
			helper.assertTrue(english.has(key), "no English for " + key);
		}
		helper.assertTrue(english.getOrDefault("blueprint.aliveworkplace.camp_kitchen_2").equals("Camp Kitchen II"),
			english.getOrDefault("blueprint.aliveworkplace.camp_kitchen_2"));
		helper.succeed();
	}
}
