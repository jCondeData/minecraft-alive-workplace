package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA lane B (2026-11-21, lane-b-1121-2138), the Stone House's and the Townhouse's biome looks (ROADMAP 49.10b, by the
 * rules of 49.10a) and B1431 (the desert Stone House II's added bedroom), written from the briefs
 * ({@code tools/blueprints/briefs/stone_house.md}, {@code townhouse.md}) and the roadmap text, not from the recipes.
 *
 * <p>What {@link QaRebuiltLooks1121bGameTests} already covers (every bed and front door of every look walked to, a look's
 * upgrade keeps the beds, job blocks and chests in their cells) isn't repeated here. This class adds the looks built
 * live by a Builder across a save and reload (the desert Stone House II and the savanna Townhouse II), a live desert
 * upgrade I to II with a claimed bed and a player's chest, the look a Steward asks a plot for in a real desert, savanna,
 * taiga, snowy and plains biome, the briefs' rules per look level (beds, furniture-spot kinds, chimney, glass, plot,
 * 60 % kept), and a look build one material short.
 */
public class QaStoneTownLooks1121cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** 30 x 30 x 30 (huge_area is only 20 tall). */
	private static final String TALL = "aliveworkplace_test:tall_area";

	private static final List<StarterBlueprints.Entry> STONE = List.of(StarterBlueprints.STONE_HOUSE, StarterBlueprints.STONE_HOUSE_2,
		StarterBlueprints.STONE_HOUSE_3, StarterBlueprints.STONE_HOUSE_4);
	private static final List<StarterBlueprints.Entry> TOWN = List.of(StarterBlueprints.TOWNHOUSE, StarterBlueprints.TOWNHOUSE_2,
		StarterBlueprints.TOWNHOUSE_3, StarterBlueprints.TOWNHOUSE_4);
	private static final Map<String, List<StarterBlueprints.Entry>> STONE_LOOKS = new LinkedHashMap<>();
	private static final Map<String, List<StarterBlueprints.Entry>> TOWN_LOOKS = new LinkedHashMap<>();

	static {
		STONE_LOOKS.put("desert", StarterBlueprints.STONE_HOUSE_DESERT);
		STONE_LOOKS.put("savanna", StarterBlueprints.STONE_HOUSE_SAVANNA);
		STONE_LOOKS.put("taiga", StarterBlueprints.STONE_HOUSE_TAIGA);
		TOWN_LOOKS.put("desert", StarterBlueprints.TOWNHOUSE_DESERT);
		TOWN_LOOKS.put("savanna", StarterBlueprints.TOWNHOUSE_SAVANNA);
	}

	/** FurnishedHomesGameTests' FULL set (both briefs: every furnished-room kind; Stone House I without the couch). */
	private static final Set<FurnitureSpot.Kind> FULL = EnumSet.of(FurnitureSpot.Kind.BED, FurnitureSpot.Kind.CHAIR, FurnitureSpot.Kind.TABLE,
		FurnitureSpot.Kind.NIGHTSTAND, FurnitureSpot.Kind.LAMP, FurnitureSpot.Kind.COUCH, FurnitureSpot.Kind.SHELF, FurnitureSpot.Kind.CROCKERY,
		FurnitureSpot.Kind.CLOCK, FurnitureSpot.Kind.PICTURE, FurnitureSpot.Kind.BLINDS);

	/** The bed Stone House II adds (B1431: shut off in the desert look). */
	private static final BlockPos ADDED_BED = new BlockPos(11, 1, 3);

	// ---- drawings -----------------------------------------------------------------------------------------------------

	private static Blueprint blueprint(GameTestHelper helper, ResourceLocation id) {
		return BlueprintLibrary.get(helper.getLevel(), id).orElseThrow(() -> new AssertionError("missing blueprint " + id));
	}

	private static List<BlockPos> heads(Blueprint b) {
		return QaRebuiltLooks1121bGameTests.bedHeads(QaRebuiltLooks1121bGameTests.cells(b));
	}

	private static Set<FurnitureSpot.Kind> kinds(ResourceLocation id) {
		Set<FurnitureSpot.Kind> out = EnumSet.noneOf(FurnitureSpot.Kind.class);
		BlueprintFurniture.of(id).forEach(s -> out.add(s.kind()));
		return out;
	}

	private static boolean fullCube(BlockState s) {
		return Block.isShapeFullBlock(s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
	}

	// ---- live set-up ----------------------------------------------------------------------------------------------------

	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	/** Up to 12 barrels of 27 slots, out of the way of a build at x >= 9, z >= 8. */
	private static final List<BlockPos> BARRELS = List.of(new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4),
		new BlockPos(6, 2, 4), new BlockPos(1, 2, 6), new BlockPos(2, 2, 6), new BlockPos(3, 2, 6), new BlockPos(4, 2, 6), new BlockPos(5, 2, 6),
		new BlockPos(6, 2, 6), new BlockPos(0, 2, 5));

	private static List<ItemStack> stacks(Map<Item, Integer> needs) {
		List<ItemStack> out = new ArrayList<>();
		needs.forEach((item, count) -> {
			for (int left = count; left > 0; ) {
				int k = Math.min(left, item.getDefaultMaxStackSize());
				out.add(new ItemStack(item, k));
				left -= k;
			}
		});
		return out;
	}

	/** Fills as many barrels as {@code stock} needs and returns them. */
	private static List<BlockPos> stock(GameTestHelper helper, List<ItemStack> stock) {
		int need = Math.max(1, (stock.size() + 26) / 27);
		helper.assertTrue(need <= BARRELS.size(), "(setup) the test needs more barrels: " + stock.size() + " stacks");
		List<BlockPos> used = new ArrayList<>();
		for (int i = 0; i < need; i++) {
			BlockPos b = BARRELS.get(i);
			helper.setBlock(b, Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(b);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
			used.add(b);
		}
		return used;
	}

	private static void rules(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
	}

	/** A restart: the sites saved and loaded back, and the builder saved, removed and loaded back. */
	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load back: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}

	private static List<BlockPos> builtBeds(ServerLevel level, BlockPos origin, Vec3i size) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/**
	 * After {@code done} reports the build finished, a villager (no AI, so he stays put) stands just inside the front door and
	 * the game's own pathfinder must find a way to each of {@code beds()}; then the test succeeds.
	 */
	private static final class Walk {
		Villager walker;
		int waited;
		boolean ok;
	}

	/**
	 * The cell just inside the front door (the briefs: "every bed can be walked to from the front door"): the lowest door
	 * nearest the z = 0 side, one step in. (From out in the street the game's pathfinder runs out of nodes on the way up
	 * to the Townhouse's top floor, the drawing's as much as the looks', though it reaches the top landing: see the
	 * class notes.)
	 */
	static BlockPos insideFrontDoor(ServerLevel level, BlockPos origin, Vec3i size) {
		BlockPos best = null;
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, 3, size.getZ() - 1))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock && s.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
				== net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER && (best == null || p.getZ() < best.getZ()
				|| p.getZ() == best.getZ() && p.getY() < best.getY())) {
				best = p.immutable();
			}
		}
		if (best == null) {
			throw new AssertionError("no front door in the house at " + origin.toShortString());
		}
		return best.south();
	}

	/**
	 * A bed vanilla's paths can't plan to (they have no ladders) still counts when the mod's own claim does: the way
	 * {@code AcquirePoiMixin} lets a villager claim a bed up a ladder ({@code BedLadders.pathUpALadder}, B304), as the
	 * Townhouse III's tower bedroom (14, 10, 14) is reached.
	 */
	private static boolean upALadder(ServerLevel level, Villager walker, BlockPos bed) {
		var home = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.HOME);
		return io.github.jcondedata.aliveworkplace.work.BedLadders.pathUpALadder(walker, Set.of(com.mojang.datafixers.util.Pair.of(home, bed))) != null;
	}

	private static void walkToBeds(GameTestHelper helper, Walk walk, BlockPos originRel, Vec3i size, List<BlockPos> beds) {
		BlockPos origin = helper.absolutePos(originRel);
		if (walk.walker == null) {
			BlockPos in = insideFrontDoor(helper.getLevel(), origin, size);
			walk.walker = helper.spawn(EntityType.VILLAGER, originRel.offset(in.subtract(origin)));
			walk.walker.setNoAi(true);
			return;
		}
		if (++walk.waited < 10 || walk.ok) {
			return;
		}
		walk.walker.setOnGround(true);
		List<String> cut = new ArrayList<>();
		for (BlockPos bed : beds) {
			var path = walk.walker.getNavigation().createPath(bed, 1);
			if ((path == null || !path.canReach()) && !upALadder(helper.getLevel(), walk.walker, bed)) {
				cut.add(bed.subtract(origin).toShortString() + (path == null ? " (no path)" : " (gets to " + path.getTarget().subtract(origin).toShortString()
					+ ", ends " + (path.getEndNode() == null ? "?" : path.getEndNode().asBlockPos().subtract(origin).toShortString()) + ")"));
			}
		}
		if (!cut.isEmpty()) {
			cut.add("walker at " + walk.walker.blockPosition().subtract(origin).toShortString());
		}
		walk.walker.discard();
		helper.assertTrue(cut.isEmpty(), "the built house has beds no villager finds a path to from its front door: " + cut);
		walk.ok = true;
	}

	/**
	 * A real Builder builds {@code id} at {@code origin} from exactly its material list, saved and loaded (the sites and
	 * the builder) once 40 blocks of STRUCTURE are placed. When the site is gone (DONE) every block must be as drawn, the
	 * site must never have skipped a step, nothing of the list may be left over; then {@code built} checks the house and
	 * a villager just inside the front door must find a path to every built bed.
	 */
	private static void buildAcrossAReload(GameTestHelper helper, ResourceLocation id, BlockPos originRel, Consumer<BlueprintData.Placement> built) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		rules(helper);
		Blueprint blueprint = blueprint(helper, id);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(originRel), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BuildPlan plan = BuildPlan.create(blueprint, placement);
		Map<Item, Integer> needs = new LinkedHashMap<>(plan.materials());
		List<BlockPos> barrels = stock(helper, stacks(needs));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, placement);
		helper.assertTrue(site != null, id + " didn't start");
		UUID siteId = site.id();
		Villager[] who = {builder};
		boolean[] reloaded = {false};
		boolean[] finished = {false};
		int[] skipped = {0};
		Walk walk = new Walk();
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 11000) {
				helper.setDayTime(2000);
			}
			if (walk.ok) {
				return;
			}
			if (finished[0]) {
				walkToBeds(helper, walk, originRel, blueprint.size(), builtBeds(level, placement.origin(), blueprint.size()));
				return;
			}
			BuildSite s = BuildSiteManager.get(level).get(siteId);
			if (s != null) {
				skipped[0] = Math.max(skipped[0], s.skipped());
				if (!reloaded[0] && s.stage() == BuildPlan.Stage.STRUCTURE && s.placed() >= 40) {
					reloaded[0] = true;
					who[0] = reload(helper, level, who[0]);
					BuildSite back = BuildSiteManager.get(level).get(siteId);
					helper.assertTrue(back != null && back.structure().equals(id), "the site came back from its save as "
						+ (back == null ? "nothing" : back.structure()) + ", not " + id);
				}
				return;
			}
			helper.assertTrue(reloaded[0], id + " was finished before it was ever saved and loaded mid-STRUCTURE");
			helper.assertTrue(skipped[0] == 0, id + " skipped " + skipped[0] + " step(s) on its way to DONE");
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of " + id + " not as drawn after the build, e.g. "
				+ wrong.stream().limit(5).map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			Map<Item, Integer> left = new LinkedHashMap<>();
			List<Container> containers = new ArrayList<>();
			barrels.forEach(b -> containers.add(helper.getBlockEntity(b)));
			containers.add(helper.getBlockEntity(CHEST));
			containers.add(who[0].getInventory());
			for (Container c : containers) {
				for (int slot = 0; slot < c.getContainerSize(); slot++) {
					ItemStack st = c.getItem(slot);
					if (!st.isEmpty() && needs.containsKey(st.getItem())) {
						left.merge(st.getItem(), st.getCount(), Integer::sum);
					}
				}
			}
			helper.assertTrue(left.isEmpty(), "materials left over after building " + id + " (it should use exactly its list): " + left);
			built.accept(placement);
			finished[0] = true;
		});
		helper.succeedWhen(() -> helper.assertTrue(walk.ok, "not built and walked yet"));
	}

	// ---- live builds ----------------------------------------------------------------------------------------------------

	/**
	 * B1431 and 49.10b in the world: a Builder builds the desert Stone House II (the level B1431 fixed) from exactly its
	 * materials across a save and reload; it ends DONE with every block as drawn and none skipped, holds 3 beds (the
	 * brief: II exactly 3) including the added bedroom's at (11, 1, 3), and a villager inside the front door finds a path to every
	 * bed, the added bedroom's too.
	 */
	//$ gametest_ticks_batch HUGE '50000' '"qaStoneTown1121cDesertII"'
	@GameTest(template = HUGE, timeoutTicks = 50000, batch = "qaStoneTown1121cDesertII")
	public void qaABuilderBuildsTheDesertStoneHouseIIAcrossAReloadAndEveryBedIsReached(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry entry = StarterBlueprints.STONE_HOUSE_DESERT.get(1);
		buildAcrossAReload(helper, entry.id(), new BlockPos(9, 2, 9), placement -> {
			List<BlockPos> beds = builtBeds(level, placement.origin(), entry.size());
			helper.assertTrue(beds.size() == 3, "the built desert Stone House II has " + beds.size() + " beds, the brief says exactly 3");
			BlockPos added = placement.origin().offset(ADDED_BED);
			helper.assertTrue(beds.contains(added), "no bed head at the added bedroom's (11, 1, 3) in the built desert Stone House II: "
				+ beds.stream().map(b -> b.subtract(placement.origin()).toShortString()).toList());
			for (BlockPos bed : beds) {
				helper.assertTrue(Homes.at(level, bed).map(Homes.Home::tier).orElse(0) == 2, "a bed of the built desert Stone House II isn't a tier-2 home: "
					+ bed.subtract(placement.origin()).toShortString() + " " + Homes.at(level, bed));
			}
		});
	}

	/**
	 * The same for a Townhouse look at a higher level: a Builder builds the savanna Townhouse II from exactly its
	 * materials across a save and reload, DONE, every block as drawn, none skipped, at least as many beds as Townhouse I
	 * as drawn (an upgrade never removes a bed), each a home, and every bed reached by the game's pathfinder.
	 */
	//$ gametest_ticks_batch TALL '90000' '"qaStoneTown1121cSavannaTownII"'
	@GameTest(template = TALL, timeoutTicks = 90000, batch = "qaStoneTown1121cSavannaTownII")
	public void qaABuilderBuildsTheSavannaTownhouseIIAcrossAReloadAndEveryBedIsReached(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry entry = StarterBlueprints.TOWNHOUSE_SAVANNA.get(1);
		int drawnBeds = heads(blueprint(helper, StarterBlueprints.TOWNHOUSE_2.id())).size();
		buildAcrossAReload(helper, entry.id(), new BlockPos(9, 2, 8), placement -> {
			List<BlockPos> beds = builtBeds(level, placement.origin(), entry.size());
			helper.assertTrue(beds.size() == drawnBeds, "the built savanna Townhouse II has " + beds.size() + " beds, Townhouse II as drawn " + drawnBeds);
			for (BlockPos bed : beds) {
				helper.assertTrue(Homes.at(level, bed).isPresent(), "a bed of the built savanna Townhouse II isn't a home: " + bed.subtract(placement.origin()).toShortString());
			}
		});
	}

	/**
	 * A live look upgrade: a finished desert Stone House I (placed and recorded) with both beds claimed by villagers and
	 * a player's diamonds in each of its chests and barrels. The upgrade a player is offered for it
	 * ({@link BlueprintUpgrades#upgradeOf}) must be the desert II; a Builder stocked with exactly what the upgrade still
	 * needs builds it. When DONE: every block of desert II as drawn, both beds still there and still claimed (no free
	 * ticket), the villagers' homes still on them and now tier 2, three beds, every diamond still in its container, and a
	 * villager inside the front door finds a path to the added bedroom's bed at (11, 1, 3).
	 */
	//$ gametest_ticks_batch HUGE '45000' '"qaStoneTown1121cDesertUpgrade"'
	@GameTest(template = HUGE, timeoutTicks = 45000, batch = "qaStoneTown1121cDesertUpgrade")
	public void qaUpgradingADesertStoneHouseToIIKeepsItsClaimedBedsAndAPlayersChests(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		rules(helper);
		ResourceLocation one = StarterBlueprints.STONE_HOUSE_DESERT.get(0).id();
		ResourceLocation two = BlueprintUpgrades.upgradeOf(one);
		helper.assertTrue(two.equals(StarterBlueprints.STONE_HOUSE_DESERT.get(1).id()), "the desert Stone House I upgrades to " + two + ", not the desert II");
		BlockPos origin = helper.absolutePos(new BlockPos(9, 2, 9));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		level.getStructureManager().get(one).orElseThrow(() -> new AssertionError("no template " + one))
			.placeInWorld(level, origin, origin, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1121L), 2);
		BuildSiteManager.get(level).recordFinished(one, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		Vec3i size1 = StarterBlueprints.STONE_HOUSE_DESERT.get(0).size();
		List<BlockPos> beds = builtBeds(level, origin, size1);
		helper.assertTrue(beds.size() == 2, "(setup) the desert Stone House I placed with " + beds.size() + " beds, the brief says 2");
		List<Villager> sleepers = new ArrayList<>();
		for (int k = 0; k < beds.size(); k++) {
			BlockPos bed = beds.get(k);
			helper.assertTrue(level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1).isPresent(),
				"(setup) the bed at " + bed.subtract(origin).toShortString() + " couldn't be claimed");
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(6 + 2 * k, 2, 7));
			v.setNoAi(true);
			v.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
			sleepers.add(v);
		}
		List<BlockPos> stores = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size1.getX() - 1, size1.getY() - 1, size1.getZ() - 1))) {
			BlockState s = level.getBlockState(p);
			if ((s.getBlock() instanceof ChestBlock || s.getBlock() instanceof BarrelBlock) && level.getBlockEntity(p) instanceof BaseContainerBlockEntity c) {
				c.setItem(0, new ItemStack(Items.DIAMOND, 7));
				stores.add(p.immutable());
			}
		}
		helper.assertTrue(!stores.isEmpty(), "(setup) the desert Stone House I has no chest or barrel (the brief: a barrel downstairs, a chest upstairs)");
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Vec3i size2 = StarterBlueprints.STONE_HOUSE_DESERT.get(1).size();
		var report = BlueprintSupplies.check(level, new BlueprintData(two, Optional.of(size2), Optional.of(placement)))
			.orElseThrow(() -> new AssertionError("no supply report for the upgrade"));
		List<ItemStack> stock = new ArrayList<>();
		for (var m : report.missing()) {
			for (int left = m.count(); left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(!stock.isEmpty(), "(setup) the upgrade needs nothing: " + report);
		stock(helper, stock);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, two, placement);
		helper.assertTrue(site != null, "the desert Stone House II didn't start over the finished desert I");
		UUID id = site.id();
		boolean[] finished = {false};
		Walk walk = new Walk();
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 11000) {
				helper.setDayTime(2000);
			}
			if (walk.ok) {
				return;
			}
			if (finished[0]) {
				walkToBeds(helper, walk, new BlockPos(9, 2, 9), size2, List.of(origin.offset(ADDED_BED)));
				return;
			}
			if (BuildSiteManager.get(level).get(id) != null) {
				return;
			}
			List<BlockPos> wrong = BuildPlan.create(blueprint(helper, two), placement).unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the desert Stone House II not as drawn after the upgrade, e.g. "
				+ wrong.stream().limit(5).map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).toList());
			for (int k = 0; k < beds.size(); k++) {
				BlockPos bed = beds.get(k);
				String where = bed.subtract(origin).toShortString();
				helper.assertTrue(level.getBlockState(bed).getBlock() instanceof BedBlock, "the claimed bed at " + where + " is gone after the upgrade");
				helper.assertTrue(level.getPoiManager().existsAtPosition(PoiTypes.HOME, bed), "the claimed bed at " + where + " is no home POI after the upgrade");
				helper.assertTrue(level.getPoiManager().getFreeTickets(bed) == 0, "the claimed bed at " + where + " was freed by the upgrade (broken and re-placed)");
				helper.assertTrue(sleepers.get(k).getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).filter(bed::equals).isPresent(),
					"the villager of the bed at " + where + " lost his home: " + sleepers.get(k).getBrain().getMemory(MemoryModuleType.HOME));
				helper.assertTrue(Homes.at(level, bed).map(Homes.Home::tier).orElse(0) == 2, "the bed at " + where + " isn't a tier-2 home after the upgrade: "
					+ Homes.at(level, bed));
			}
			int now = builtBeds(level, origin, size2).size();
			helper.assertTrue(now == 3, "the desert Stone House II built over I has " + now + " beds, the brief says 3");
			for (BlockPos p : stores) {
				int diamonds = level.getBlockEntity(p) instanceof Container c ? c.countItem(Items.DIAMOND) : -1;
				helper.assertTrue(diamonds == 7, "the player's container at " + p.subtract(origin).toShortString() + " holds " + diamonds
					+ " diamonds after the upgrade, not 7 (" + level.getBlockState(p) + ")");
			}
			sleepers.forEach(Entity::discard);
			finished[0] = true;
		});
		helper.succeedWhen(() -> helper.assertTrue(walk.ok, "not upgraded and walked yet"));
	}

	/**
	 * A look build one material short: the desert Stone House I stocked with everything but its most-used wall block
	 * (above the ground, so not a LANDSCAPE block that never waits). The builder must stop and wait for it (status
	 * WAITING_FOR_MATERIALS naming it), stay unfinished for 600 more ticks, and, once the exact count is put in a barrel,
	 * resume and finish DONE with every block as drawn and none skipped.
	 */
	//$ gametest_ticks_batch HUGE '45000' '"qaStoneTown1121cShort"'
	@GameTest(template = HUGE, timeoutTicks = 45000, batch = "qaStoneTown1121cShort")
	public void qaADesertStoneHouseOneMaterialShortWaitsAndResumesWhenSupplied(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		rules(helper);
		ResourceLocation id = StarterBlueprints.STONE_HOUSE_DESERT.get(0).id();
		Blueprint blueprint = blueprint(helper, id);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 9)),
			Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BuildPlan plan = BuildPlan.create(blueprint, placement);
		Map<Item, Integer> needs = new LinkedHashMap<>(plan.materials());
		Map<Item, Integer> walls = new LinkedHashMap<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			Item item = e.state().getBlock().asItem();
			if (e.pos().getY() >= 2 && needs.containsKey(item) && fullCube(e.state())) {
				walls.merge(item, 1, Integer::sum);
			}
		}
		Item short_ = walls.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
			.orElseThrow(() -> new AssertionError("(setup) no full wall block in " + id));
		int count = needs.remove(short_);
		stock(helper, stacks(needs));
		BlockPos late = BARRELS.get(BARRELS.size() - 1);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, placement);
		helper.assertTrue(site != null, id + " didn't start");
		UUID siteId = site.id();
		long[] waitingSince = {-1};
		boolean[] supplied = {false};
		int[] skipped = {0};
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 11000) {
				helper.setDayTime(2000);
			}
			BuildSite s = BuildSiteManager.get(level).get(siteId);
			if (supplied[0]) {
				if (s != null) {
					skipped[0] = Math.max(skipped[0], s.skipped());
				}
				return;
			}
			helper.assertTrue(s != null, id + " finished DONE without its " + count + " " + short_ + " ever being supplied");
			skipped[0] = Math.max(skipped[0], s.skipped());
			if (waitingSince[0] < 0) {
				if (s.status() == BuildSite.Status.WAITING_FOR_MATERIALS && s.missing().containsKey(short_)) {
					waitingSince[0] = helper.getTick();
				}
				return;
			}
			if (helper.getTick() - waitingSince[0] < 600) {
				return;
			}
			helper.assertTrue(s.stage() != BuildPlan.Stage.DONE, id + " reached DONE while still short of " + short_);
			helper.assertTrue(s.missing().containsKey(short_), id + " stopped asking for " + short_ + " without getting it: missing " + s.missing()
				+ ", status " + s.status() + ", stage " + s.stage());
			helper.assertTrue(!plan.unfinished(level).isEmpty(), id + " stands finished while short of " + short_);
			helper.setBlock(late, Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(late);
			List<ItemStack> more = stacks(Map.of(short_, count));
			for (int i = 0; i < more.size(); i++) {
				barrel.setItem(i, more.get(i));
			}
			supplied[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(supplied[0], "never waited for " + short_);
			BuildSite s = BuildSiteManager.get(level).get(siteId);
			helper.assertTrue(s == null, s == null ? "" : "still building after " + short_ + " was supplied: " + s.stage() + " " + s.status() + ", missing " + s.missing());
			helper.assertTrue(skipped[0] == 0, id + " skipped " + skipped[0] + " step(s) instead of waiting for " + short_);
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of " + id + " not as drawn after the late supply, e.g. "
				+ wrong.stream().limit(5).map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
		});
	}

	// ---- the player's (the Steward's) entry point -------------------------------------------------------------------

	private static void fillBiome(ServerLevel level, BlockPos at, String biome) {
		String cmd = "fillbiome " + (at.getX() - 6) + " " + (at.getY() - 4) + " " + (at.getZ() - 6) + " " + (at.getX() + 6) + " " + (at.getY() + 4)
			+ " " + (at.getZ() + 6) + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}

	private static StewardWishes.Wish wish(ResourceLocation blueprint) {
		return new StewardWishes.Wish(AliveWorkplace.id("test/qa_stone_town_looks"), new StewardRules.Effect(StewardRules.Kind.BUILD, Optional.of(blueprint),
			Optional.of("homes"), Optional.empty(), Optional.empty()), 50, "steward.aliveworkplace.why.beds", List.of(2L));
	}

	/**
	 * 49.10a's pick through the Steward: a village hall standing in a real desert, savanna, taiga, snowy or plains biome
	 * asks its homes plot ({@link StewardWishes#plotFor(ServerLevel, BlockPos, StewardWishes.Wish)}) for each level of the
	 * Stone House and the Townhouse. Its own culture's look comes first where one ships (Stone House: desert, savanna,
	 * taiga; Townhouse: desert, savanna), else the drawing; the drawing is always among the fallbacks; every blueprint
	 * asked for is in the library (no unshipped snowy or taiga look); each is the same level as the one wished for.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAStewardAsksForItsBiomesLookOfEveryStoneHouseAndTownhouseLevel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(1, 1, 1));
		String old = level.getBiome(hall).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		Map<String, String> biomes = new LinkedHashMap<>();
		biomes.put("minecraft:desert", "desert");
		biomes.put("minecraft:savanna", "savanna");
		biomes.put("minecraft:taiga", "taiga");
		biomes.put("minecraft:snowy_plains", "snowy");
		biomes.put("minecraft:plains", "plains");
		List<String> problems = new ArrayList<>();
		try {
			for (Map.Entry<String, String> b : biomes.entrySet()) {
				fillBiome(level, hall, b.getKey());
				String culture = b.getValue();
				for (int fam = 0; fam < 2; fam++) {
					List<StarterBlueprints.Entry> levels = fam == 0 ? STONE : TOWN;
					Map<String, List<StarterBlueprints.Entry>> looks = fam == 0 ? STONE_LOOKS : TOWN_LOOKS;
					for (int lv = 0; lv < levels.size(); lv++) {
						ResourceLocation drawn = levels.get(lv).id();
						ResourceLocation want = looks.containsKey(culture) ? looks.get(culture).get(lv).id() : drawn;
						Optional<Plots.Request> req = StewardWishes.plotFor(level, hall, wish(drawn));
						if (req.isEmpty()) {
							problems.add(b.getKey() + ": no plot asked for " + drawn);
							continue;
						}
						List<ResourceLocation> asked = req.get().blueprints();
						if (!asked.get(0).equals(want)) {
							problems.add(b.getKey() + ": the Steward tries " + asked.get(0) + " first for " + drawn + ", not " + want);
						}
						if (!asked.contains(drawn)) {
							problems.add(b.getKey() + ": " + drawn + " as drawn isn't a fallback: " + asked);
						}
						if (asked.size() != 1 + looks.size()) {
							problems.add(b.getKey() + ": " + asked.size() + " blueprints tried for " + drawn + ", want " + (1 + looks.size()) + ": " + asked);
						}
						for (ResourceLocation a : asked) {
							if (BlueprintLibrary.get(level, a).isEmpty()) {
								problems.add(b.getKey() + ": the Steward would ask for " + a + ", which isn't in the library");
							}
							if (BlueprintUpgrades.tier(a) != lv + 1) {
								problems.add(b.getKey() + ": " + a + " (tried for " + drawn + ") is level " + BlueprintUpgrades.tier(a));
							}
						}
						if (!req.get().zoneKind().equals("homes")) {
							problems.add(b.getKey() + ": " + drawn + " asked in a " + req.get().zoneKind() + " zone");
						}
					}
				}
			}
		} finally {
			fillBiome(level, hall, old);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- the briefs per look level ---------------------------------------------------------------------------------------

	/**
	 * 49.10a's rule ("the same rooms, beds, job blocks and furniture spots, so every home test holds") with the briefs'
	 * numbers, for every shipped look level of both families:
	 * <ul>
	 *   <li>bed heads: the same count as the drawn level; Stone House I exactly 2 on two different heights, II exactly 3,
	 *   III more than II, IV no more than III; Townhouse I at least 2 (two households), no level fewer than the one below;</li>
	 *   <li>furniture spots ({@code blueprint_furniture/looks/...}): every kind the drawn level has, and the brief's set
	 *   (Stone House I every kind but the couch, II-IV and every Townhouse level the full set);</li>
	 *   <li>Stone House I: a barrel on the ground floor and exactly one chest on the upper bed's floor;</li>
	 *   <li>Townhouse I: a campfire standing on a full solid block (the chimney) and at least 6 panes or bars;</li>
	 *   <li>the plot: as wide and deep as the drawn level; Townhouse I within 15 x 28 x 16, II and III within 21 x 28 x 22;</li>
	 *   <li>every upgrade of a look keeps at least 60 % of its level below's non-air blocks in the same cells.</li>
	 * </ul>
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryLookLevelHoldsItsBriefsBedsFurnitureAndPlot(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (int fam = 0; fam < 2; fam++) {
			boolean stone = fam == 0;
			List<StarterBlueprints.Entry> levels = stone ? STONE : TOWN;
			for (Map.Entry<String, List<StarterBlueprints.Entry>> look : (stone ? STONE_LOOKS : TOWN_LOOKS).entrySet()) {
				int[] beds = new int[levels.size()];
				Blueprint below = null;
				for (int lv = 0; lv < levels.size(); lv++) {
					ResourceLocation id = look.getValue().get(lv).id();
					ResourceLocation drawnId = levels.get(lv).id();
					Blueprint b = blueprint(helper, id);
					Blueprint drawn = blueprint(helper, drawnId);
					Map<BlockPos, BlockState> cells = QaRebuiltLooks1121bGameTests.cells(b);
					List<BlockPos> heads = QaRebuiltLooks1121bGameTests.bedHeads(cells);
					String n = id.getPath();
					checked++;
					beds[lv] = heads.size();
					int drawnBeds = heads(drawn).size();
					if (heads.size() != drawnBeds) {
						problems.add(n + " has " + heads.size() + " beds, " + drawnId.getPath() + " " + drawnBeds);
					}
					// furniture spots
					Set<FurnitureSpot.Kind> has = kinds(id);
					Set<FurnitureSpot.Kind> want = EnumSet.copyOf(FULL);
					if (stone && lv == 0) {
						want.remove(FurnitureSpot.Kind.COUCH);
					}
					want.addAll(kinds(drawnId));
					Set<FurnitureSpot.Kind> lacking = EnumSet.copyOf(want);
					lacking.removeAll(has);
					if (!lacking.isEmpty()) {
						problems.add(n + " lacks furniture spots of kind " + lacking + " (has " + has + ")");
					}
					// plot
					Vec3i s = b.size();
					if (s.getX() != drawn.size().getX() || s.getZ() != drawn.size().getZ()) {
						problems.add(n + " is " + s.toShortString() + ", " + drawnId.getPath() + " " + drawn.size().toShortString() + " (not the same plot)");
					}
					if (!stone && lv == 0 && (s.getX() > 15 || s.getY() > 28 || s.getZ() > 16)) {
						problems.add(n + " (" + s.toShortString() + ") is over Townhouse I's plot, 15 x 28 x 16");
					}
					if (!stone && (lv == 1 || lv == 2) && (s.getX() > 21 || s.getY() > 28 || s.getZ() > 22)) {
						problems.add(n + " (" + s.toShortString() + ") is over Townhouse II/III's plot, 21 x 28 x 22");
					}
					// level-specific musts
					if (stone && lv == 0) {
						if (heads.size() == 2 && heads.get(0).getY() == heads.get(1).getY()) {
							problems.add(n + "'s two beds are on one floor (the brief: one downstairs, one upstairs)");
						}
						int ground = b.basement();
						boolean barrel = cells.entrySet().stream().anyMatch(e -> e.getValue().getBlock() instanceof BarrelBlock
							&& e.getKey().getY() >= ground && e.getKey().getY() <= ground + 2);
						if (!barrel) {
							problems.add(n + " has no barrel on the ground floor");
						}
						if (heads.size() == 2) {
							int upper = Math.max(heads.get(0).getY(), heads.get(1).getY());
							long chests = cells.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof ChestBlock && e.getKey().getY() == upper).count();
							if (chests != 1) {
								problems.add(n + " has " + chests + " chests on the upstairs bedroom's floor (y " + upper + "), the brief says exactly 1");
							}
						}
					}
					if (!stone && lv == 0) {
						boolean chimney = cells.entrySet().stream().anyMatch(e -> e.getValue().getBlock() instanceof CampfireBlock
							&& fullCube(QaRebuiltLooks1121bGameTests.at(cells, e.getKey().below())));
						if (!chimney) {
							problems.add(n + " has no campfire standing on a full solid block (the brief's chimney)");
						}
						long glass = cells.values().stream().filter(st -> st.getBlock() instanceof IronBarsBlock).count();
						if (glass < 6) {
							problems.add(n + " has " + glass + " panes or bars, the brief says at least 6");
						}
					}
					// kept cells
					if (below != null) {
						Map<BlockPos, BlockState> lo = QaRebuiltLooks1121bGameTests.cells(below);
						int solid = 0;
						int kept = 0;
						for (Map.Entry<BlockPos, BlockState> e : lo.entrySet()) {
							if (e.getValue().isAir()) {
								continue;
							}
							solid++;
							if (QaRebuiltLooks1121bGameTests.at(cells, e.getKey()).getBlock() == e.getValue().getBlock()) {
								kept++;
							}
						}
						if (solid > 0 && kept * 100 < solid * 60) {
							problems.add(n + " keeps only " + kept + " of " + solid + " blocks of its level below in their cells (under 60 %)");
						}
					}
					below = b;
				}
				if (stone && (beds[0] != 2 || beds[1] != 3 || beds[2] <= beds[1] || beds[3] > beds[2])) {
					problems.add(look.getKey() + " Stone House beds " + java.util.Arrays.toString(beds) + ": the brief wants 2, 3, more than II, no more than III");
				}
				if (!stone && (beds[0] < 2 || beds[1] < beds[0] || beds[2] < beds[1] || beds[3] < beds[2])) {
					problems.add(look.getKey() + " Townhouse beds " + java.util.Arrays.toString(beds) + ": the brief wants at least 2 in I and no level fewer than below");
				}
			}
		}
		helper.assertTrue(checked == 20, "(setup) " + checked + " look levels checked, want 20 (12 Stone House, 8 Townhouse)");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- the game's pathfinder on every look's drawing ------------------------------------------------------------------

	/** Levels I-III of both families: each look and, beside it, the drawing (IV's above-ground part is III's). */
	static List<ResourceLocation> pathIds(boolean looksOnly) {
		List<ResourceLocation> out = new ArrayList<>();
		for (int fam = 0; fam < 2; fam++) {
			List<StarterBlueprints.Entry> levels = fam == 0 ? STONE : TOWN;
			for (int lv = 0; lv < 3; lv++) {
				if (!looksOnly) {
					out.add(levels.get(lv).id());
				}
				for (List<StarterBlueprints.Entry> look : (fam == 0 ? STONE_LOOKS : TOWN_LOOKS).values()) {
					out.add(look.get(lv).id());
				}
			}
		}
		return out;
	}

	/**
	 * The game's own pathfinder (the arbiter QaRebuiltLooks1121bGameTests ran only on the desert Stone House I and II): each
	 * drawing placed alone in the 30-cube area, a villager standing just inside its front door finds a path to every bed
	 * (or, for a bed up a ladder, the mod's ladder claim does).
	 */
	static void pathsToEveryBedOf(GameTestHelper helper, ResourceLocation id) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos originRel = new BlockPos(5, 2, 6);
		BlockPos at = helper.absolutePos(originRel);
		var template = level.getStructureManager().get(id).orElseThrow(() -> new AssertionError("missing template " + id));
		template.placeInWorld(level, at, at, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1121L), 2);
		Vec3i size = template.getSize();
		List<BlockPos> beds = builtBeds(level, at, size);
		helper.assertTrue(!beds.isEmpty(), "(setup) no beds placed for " + id);
		BlockPos in = insideFrontDoor(level, at, size);
		Villager v = helper.spawn(EntityType.VILLAGER, originRel.offset(in.subtract(at)));
		v.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			v.setOnGround(true);
			List<String> cut = new ArrayList<>();
			for (BlockPos bed : beds) {
				var path = v.getNavigation().createPath(bed, 1);
				if ((path == null || !path.canReach()) && !upALadder(level, v, bed)) {
					cut.add(bed.subtract(at).toShortString());
				}
			}
			v.discard();
			helper.assertTrue(cut.isEmpty(), id.getPath() + ": no path from inside the front door " + in.subtract(at).toShortString() + " to the beds " + cut);
			helper.succeed();
		});
	}

	@net.minecraft.gametest.framework.GameTestGenerator
	public java.util.Collection<net.minecraft.gametest.framework.TestFunction> qaPathsToEveryBedOfEveryLook() {
		List<net.minecraft.gametest.framework.TestFunction> out = new ArrayList<>();
		for (ResourceLocation id : pathIds(false)) {
			String name = "qastonetownlooks1121cgametests.qapaths_" + id.getPath().replace('/', '_');
			out.add(new net.minecraft.gametest.framework.TestFunction(name, name, TALL, 100, 0, true, helper -> pathsToEveryBedOf(helper, id)));
		}
		return out;
	}
}
