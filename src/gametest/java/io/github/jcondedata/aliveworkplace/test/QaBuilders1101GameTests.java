package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1101-0338) for B696 from its spec ("a Carpenter turned the logs stocked for one build into planks for
 * another that shared the chest, and the first waited for ever"; wiki builder.md: a bench's chests are those within 8
 * blocks across "and 4 blocks up or down"). What another open build still needs stays kept back in a shared chest
 * exactly when the chest is one of that build's bench's chests, and only what it still needs:
 * <ul>
 *   <li>the 8-block reach across holds on both sides of its edge, and at its corner (8 east and 8 south);</li>
 *   <li>a take-down or a canceled build keeps nothing back;</li>
 *   <li>a build's own needs are never kept back from its own Carpenter (only the others' are);</li>
 *   <li>a build part done (blocks already in the world) or with goods in its builder's bag keeps back only the rest.</li>
 * </ul>
 * QaReservedReach1031GameTests covers the 4/5 blocks up or down; QaBuildReserve1101GameTests the village storehouse.
 * Each test has its own batch (build sites are shared state).
 */
public class QaBuilders1101GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH_A = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(3, 2, 6);
	private static final BlockPos ROW_A = new BlockPos(4, 2, 24);
	private static final BlockPos ROW_B = new BlockPos(4, 2, 27);

	private record Setup(ServerLevel level, Villager a, Villager b, BuildSite siteA, BuildSite siteB, List<BlockPos> chests) {
		int kept(Item item, BuildSite except) {
			Map<Item, Integer> kept = Builders.reservedIn(level, chests, except);
			return kept.getOrDefault(MaterialFamilies.key(item), 0);
		}
	}

	private static Villager builder(GameTestHelper helper, BlockPos bench) {
		helper.setBlock(bench.below(), Blocks.STONE);
		helper.setBlock(bench, ModBlocks.BLUEPRINT_TABLE);
		QaBuilderDisruptionGameTests.ensurePoi(helper.getLevel(), helper.absolutePos(bench));
		helper.setBlock(bench.north().below(), Blocks.STONE);
		Villager v = helper.spawn(EntityType.VILLAGER, bench.north());
		v.setNoAi(true); // nobody builds: only what each build still needs matters
		Builders.employ(helper.getLevel(), v, helper.absolutePos(bench));
		return v;
	}

	private static BuildSite start(GameTestHelper helper, Villager builder, ResourceLocation id, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin),
			Rotation.NONE, Mirror.NONE));
		if (site == null || site.plan(level) == null) {
			throw new GameTestAssertException("no build of " + id);
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return site;
	}

	/** A row of {@code length} {@code block}s saved as a blueprint, the spot cleared again. */
	private static ResourceLocation row(GameTestHelper helper, String name, BlockPos origin, int length, net.minecraft.world.level.block.Block block) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < length; x++) {
			helper.setBlock(origin.offset(x, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1101_builders_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), new Vec3i(length, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < length; x++) {
			helper.setBlock(origin.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
	}

	/**
	 * A's build (3 spruce planks, bench at BENCH_A, the shared chest one of A's) and B's build (3 spruce logs) with B's
	 * bench {@code dx} east and {@code dz} south of the shared chest, at the chest's height.
	 */
	private static Setup setup(GameTestHelper helper, int dx, int dz, String name) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation planks = row(helper, name + "_a", ROW_A, 3, Blocks.SPRUCE_PLANKS);
		ResourceLocation logs = row(helper, name + "_b", ROW_B, 3, Blocks.SPRUCE_LOG);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager a = builder(helper, BENCH_A);
		Villager b = builder(helper, CHEST.offset(dx, 0, dz));
		BuildSite siteA = start(helper, a, planks, ROW_A);
		BuildSite siteB = start(helper, b, logs, ROW_B);
		return new Setup(level, a, b, siteA, siteB, List.of(helper.absolutePos(CHEST)));
	}

	/** B's bench exactly 8 blocks east of the shared chest: the chest is B's, so B's 3 logs are kept from A's Carpenter. */
	//$ gametest_ticks_batch HUGE '40' '"qaBuilders1101East8"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaBuilders1101East8")
	public void aChestEightAcrossFromAnotherBenchKeepsItsBuildsLogs(GameTestHelper helper) {
		Setup s = setup(helper, 8, 0, "east8");
		int kept = s.kept(Items.SPRUCE_LOG, s.siteA());
		helper.assertTrue(kept == 3, "a chest 8 blocks across from B's bench is B's chest, but " + kept + " of B's 3 spruce logs are kept back");
		helper.succeed();
	}

	/** B's bench 9 blocks east of the shared chest: not B's chest, so nothing in it is kept for B. */
	//$ gametest_ticks_batch HUGE '40' '"qaBuilders1101East9"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaBuilders1101East9")
	public void aChestNineAcrossFromAnotherBenchKeepsNothingForIt(GameTestHelper helper) {
		Setup s = setup(helper, 9, 0, "east9");
		int kept = s.kept(Items.SPRUCE_LOG, s.siteA());
		helper.assertTrue(kept == 0, "a chest 9 blocks across from B's bench isn't B's chest, but " + kept + " spruce logs are kept back for B");
		helper.succeed();
	}

	/** The reach is a square: B's bench 8 east and 8 south of the chest (its corner) still has the chest; 8 and 9 doesn't. */
	//$ gametest_ticks_batch HUGE '40' '"qaBuilders1101Corner"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaBuilders1101Corner")
	public void theReachsCornerHoldsOnBothSides(GameTestHelper helper) {
		Setup s = setup(helper, 8, 8, "corner");
		int kept = s.kept(Items.SPRUCE_LOG, s.siteA());
		helper.assertTrue(kept == 3, "a chest at the corner of B's reach (8 east, 8 south) keeps back " + kept + " of B's 3 logs");
		// the same build, its bench one further south: outside the square
		BlockPos far = helper.absolutePos(CHEST.offset(8, 0, 9));
		BlockPos chest = helper.absolutePos(CHEST);
		helper.assertTrue(!io.github.jcondedata.aliveworkplace.build.SupplyContainers.inReach(far, chest),
			"a chest 8 east and 9 south of a bench counts as its chest");
		helper.succeed();
	}

	/** B's build turned into a take-down needs nothing, so it keeps nothing back; nor does an empty list of chests. */
	//$ gametest_ticks_batch HUGE '40' '"qaBuilders1101TakeDown"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaBuilders1101TakeDown")
	public void aTakeDownSharingTheChestKeepsNothingBack(GameTestHelper helper) {
		Setup s = setup(helper, 4, 0, "takedown");
		helper.assertTrue(s.kept(Items.SPRUCE_LOG, s.siteA()) == 3, "set-up: B's 3 logs aren't kept back: " + s.kept(Items.SPRUCE_LOG, s.siteA()));
		helper.assertTrue(Builders.reservedIn(s.level(), List.of(), s.siteA()).isEmpty(), "no chests, but something is kept back");
		s.siteB().setDeconstruction();
		int kept = s.kept(Items.SPRUCE_LOG, s.siteA());
		helper.assertTrue(kept == 0, "B's take-down still keeps back " + kept + " spruce logs in the shared chest");
		helper.succeed();
	}

	/** The player cancels B's build: its logs are free for A's Carpenter at once. */
	//$ gametest_ticks_batch HUGE '40' '"qaBuilders1101Cancel"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaBuilders1101Cancel")
	public void aCanceledBuildFreesTheSharedChestAtOnce(GameTestHelper helper) {
		Setup s = setup(helper, 4, 0, "cancel");
		helper.assertTrue(s.kept(Items.SPRUCE_LOG, s.siteA()) == 3, "set-up: B's 3 logs aren't kept back");
		Builders.cancel(s.level(), s.siteB());
		int kept = s.kept(Items.SPRUCE_LOG, s.siteA());
		helper.assertTrue(kept == 0, "B's canceled build still keeps back " + kept + " spruce logs");
		helper.succeed();
	}

	/**
	 * Both benches reach the shared chest. Asked for B's build, only A's planks are kept back (B's own logs are B's to
	 * use); asked for A's, only B's logs. A build never locks its own materials away from itself.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaBuilders1101Own"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaBuilders1101Own")
	public void aBuildsOwnNeedsAreNeverKeptFromItself(GameTestHelper helper) {
		Setup s = setup(helper, 4, 0, "own");
		helper.assertTrue(s.kept(Items.SPRUCE_LOG, s.siteB()) == 0, "B's own logs are kept back from B's own Carpenter: " + s.kept(Items.SPRUCE_LOG, s.siteB()));
		helper.assertTrue(s.kept(Items.SPRUCE_PLANKS, s.siteB()) == 3, "A's 3 planks aren't kept back from B: " + s.kept(Items.SPRUCE_PLANKS, s.siteB()));
		helper.assertTrue(s.kept(Items.SPRUCE_PLANKS, s.siteA()) == 0, "A's own planks are kept back from A: " + s.kept(Items.SPRUCE_PLANKS, s.siteA()));
		helper.assertTrue(s.kept(Items.SPRUCE_LOG, s.siteA()) == 3, "B's 3 logs aren't kept back from A: " + s.kept(Items.SPRUCE_LOG, s.siteA()));
		helper.succeed();
	}

	/** B's build is two-thirds done (2 of its logs already in the world, mid-job): only the last log is kept back. */
	//$ gametest_ticks_batch HUGE '40' '"qaBuilders1101Partly"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaBuilders1101Partly")
	public void aPartlyBuiltBuildKeepsBackOnlyWhatItStillNeeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation planks = row(helper, "partly_a", ROW_A, 3, Blocks.SPRUCE_PLANKS);
		ResourceLocation logs = row(helper, "partly_b", ROW_B, 3, Blocks.SPRUCE_LOG);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager a = builder(helper, BENCH_A);
		Villager b = builder(helper, CHEST.offset(4, 0, 0));
		BuildSite siteA = start(helper, a, planks, ROW_A);
		// B's builder already set two logs before the count (never counted yet, so nothing is cached)
		helper.setBlock(ROW_B, Blocks.SPRUCE_LOG);
		helper.setBlock(ROW_B.east(), Blocks.SPRUCE_LOG);
		start(helper, b, logs, ROW_B);
		Map<Item, Integer> kept = Builders.reservedIn(level, List.of(helper.absolutePos(CHEST)), siteA);
		int n = kept.getOrDefault(MaterialFamilies.key(Items.SPRUCE_LOG), 0);
		helper.assertTrue(n == 1, "B's build has 2 of its 3 logs in, but " + n + " logs are kept back for it (expected 1)");
		helper.succeed();
	}

	/**
	 * Saved and loaded mid-job (the build list reloaded, as on a server restart): the loaded builds keep the same things
	 * back as before, B's logs for A and A's planks for B, and neither its own.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaBuilders1101Reload"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaBuilders1101Reload")
	public void theSharedChestKeepsTheSameBackAfterAReload(GameTestHelper helper) {
		Setup s = setup(helper, 4, 0, "reload");
		ServerLevel level = s.level();
		helper.assertTrue(s.kept(Items.SPRUCE_LOG, s.siteA()) == 3, "set-up: B's 3 logs aren't kept back before the reload");
		net.minecraft.core.HolderLookup.Provider registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME,
			BuildSiteManager.load(BuildSiteManager.get(level).save(new net.minecraft.nbt.CompoundTag(), registries), registries));
		BuildSite a = BuildSiteManager.get(level).get(s.siteA().id());
		BuildSite b = BuildSiteManager.get(level).get(s.siteB().id());
		helper.assertTrue(a != null && b != null && a != s.siteA(), "set-up: the build list wasn't reloaded");
		Map<Item, Integer> forA = Builders.reservedIn(level, s.chests(), a);
		Map<Item, Integer> forB = Builders.reservedIn(level, s.chests(), b);
		helper.assertTrue(forA.getOrDefault(MaterialFamilies.key(Items.SPRUCE_LOG), 0) == 3 && !forA.containsKey(MaterialFamilies.key(Items.SPRUCE_PLANKS)),
			"after a reload, kept back from A: " + forA + " (expected only B's 3 spruce logs)");
		helper.assertTrue(forB.getOrDefault(MaterialFamilies.key(Items.SPRUCE_PLANKS), 0) == 3 && !forB.containsKey(MaterialFamilies.key(Items.SPRUCE_LOG)),
			"after a reload, kept back from B: " + forB + " (expected only A's 3 spruce planks)");
		helper.succeed();
	}

	/** B's builder already carries 2 spruce logs for his build: they aren't counted twice, so 1 is kept in the chest. */
	//$ gametest_ticks_batch HUGE '40' '"qaBuilders1101Carried"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaBuilders1101Carried")
	public void whatTheOtherBuilderCarriesIsntKeptInTheChestToo(GameTestHelper helper) {
		Setup s = setup(helper, 4, 0, "carried");
		ModAttachments.BUILDER_BAG.set(s.b(), BuilderBag.of(List.of(new ItemStack(Items.SPRUCE_LOG, 2))));
		int kept = s.kept(Items.SPRUCE_LOG, s.siteA());
		helper.assertTrue(kept == 1, "B carries 2 of the 3 logs his build needs, but " + kept + " are kept back in the chest (expected 1)");
		ModAttachments.BUILDER_BAG.set(s.b(), BuilderBag.of(List.of(new ItemStack(Items.SPRUCE_LOG, 5))));
		helper.assertTrue(s.kept(Items.SPRUCE_LOG, s.siteA()) == 0, "B carries more logs than his build needs, but "
			+ s.kept(Items.SPRUCE_LOG, s.siteA()) + " are still kept back");
		helper.succeed();
	}
}
