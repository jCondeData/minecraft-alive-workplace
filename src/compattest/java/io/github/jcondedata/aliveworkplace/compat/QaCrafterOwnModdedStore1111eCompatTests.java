package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.with;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane E, 2026-11-11): B1156/B1170 (a crafter uses the makings kept by his OWN workstation for a builder, guide
 * crafter.md, glassblower.md) with the pack's storage, which QaCrafterOwnChest1111cGameTests (vanilla chests) and
 * QaCraftersModdedStore1111bCompatTests (modded storage by the builder's table, not the crafter's) leave out. The spec
 * (M37): "every job that reads chests also works with" modded storage; 39.2: Create's machines are never anyone's
 * supply chest. So: the Glassblower's own Create Item Vault and the Mason's own Sophisticated barrel are used exactly
 * as a chest would be (one batch, the rest left), also saved and loaded mid-job; sand left in a Create Basin by the kiln
 * is a machine's, never taken, and the builder's line names the sand as short; and a Create Item Vault as the builder's
 * own supply gets the Glassblower's glass once. Every watched item in the area (any store, bags, inventories, the
 * ground, built blocks) is counted every second: nothing doubled, nothing lost. Each test has its own batch.
 */
public class QaCrafterOwnModdedStore1111eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	static final BlockPos STATION = new BlockPos(14, 2, 2);
	static final BlockPos OWN = STATION.east();
	private static final BlockPos ROW = new BlockPos(6, 2, 8);
	private static final int SETTLE = 400;
	private static final int LOOK = 1000;

	record Asker(BuildSite site, BuildPlan plan) {
	}

	/** A quantity crafting must keep: the weighted sum of these items. */
	record Law(String name, Map<Item, Integer> weights) {
		static Law of(String name, Object... pairs) {
			Map<Item, Integer> w = new LinkedHashMap<>();
			for (int i = 0; i < pairs.length; i += 2) {
				w.put((Item) pairs[i], (Integer) pairs[i + 1]);
			}
			return new Law(name, w);
		}

		int of(Map<Item, Integer> tally) {
			int sum = 0;
			for (Map.Entry<Item, Integer> e : weights.entrySet()) {
				sum += e.getValue() * tally.getOrDefault(e.getKey(), 0);
			}
			return sum;
		}
	}

	static final Law SAND_AND_GLASS = Law.of("sand and glass", Items.SAND, 1, Items.GLASS, 1);

	// ------------------------------------------------------------------------------------------------------ tests

	/** 8 sand and 2 coal in a one-block Create Item Vault by the Glass Kiln, 4 glass asked: 4 sand and 1 coal fired, 4 left. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwnMod1111eVault"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwnMod1111eVault")
	public void theGlassblowersOwnCreateVaultIsFiredForTheBuildersGlass(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.GLASS, 4, 1, "vault"), Blocks.CHEST.defaultBlockState());
		vault(helper, OWN);
		Villager blower = glassblower(helper, new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 2));
		ownIsListed(helper);
		watch(helper, a, blower.getUUID(), List.of(SAND_AND_GLASS), (start, end) -> {
			used(helper, start, end, Items.SAND, 4);
			used(helper, start, end, Items.COAL, 1);
			exactly(helper, end, Items.GLASS, 4);
			helper.assertTrue(held(helper, OWN, Items.SAND) == 4 && held(helper, OWN, Items.COAL) == 1, "his vault should keep 4 sand and 1 coal: "
				+ contents(helper, OWN));
		});
	}

	/** 10 stone bricks in a Sophisticated Storage barrel by the Mason's stonecutter, 4 slabs asked: 2 bricks cut, 8 left. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwnMod1111eSoph"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwnMod1111eSoph")
	public void theMasonsOwnSophisticatedBarrelIsCutForTheBuildersSlabs(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.STONE_BRICK_SLAB, 4, 1, "soph"), Blocks.CHEST.defaultBlockState());
		helper.setBlock(OWN, QaCraftersModdedStore1111bCompatTests.soph("barrel"));
		helper.setBlock(STATION, Blocks.STONECUTTER);
		put(helper, OWN, new ItemStack(Items.STONE_BRICKS, 10));
		Villager mason = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
		ownIsListed(helper);
		watch(helper, a, mason.getUUID(), List.of(Law.of("stone in half-bricks", Items.STONE_BRICKS, 2, Items.STONE_BRICK_SLAB, 1)), (start, end) -> {
			used(helper, start, end, Items.STONE_BRICKS, 2);
			exactly(helper, end, Items.STONE_BRICK_SLAB, 4);
			helper.assertTrue(held(helper, OWN, Items.STONE_BRICKS) == 8, "his barrel should keep 8 stone bricks: " + contents(helper, OWN));
		});
	}

	/**
	 * 4 sand in a Create Basin beside the Glass Kiln (a machine's, 39.2) and a coal in the builder's chest, 4 glass asked:
	 * the basin's sand is never taken, the Glassblower makes nothing, and the builder's line says the glass can't be made yet.
	 */
	//$ gametest_ticks_batch AREA '1100' '"qaOwnMod1111eBasin"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaOwnMod1111eBasin")
	public void sandInACreateBasinByTheKilnIsNeverTaken(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.GLASS, 4, 1, "basin"), Blocks.CHEST.defaultBlockState(), new ItemStack(Items.COAL, 1));
		set(helper.getLevel(), helper.absolutePos(OWN), block("create:basin").defaultBlockState());
		QaCreateMoverContents1110eCompatTests.fill(helper.getLevel(), helper.absolutePos(OWN), Items.SAND, 4);
		helper.assertTrue(held(helper, OWN, Items.SAND) == 4, "setup: the basin holds " + contents(helper, OWN));
		helper.setBlock(STATION, ModBlocks.GLASS_KILN);
		Villager blower = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), blower, helper.absolutePos(STATION), ModVillagers.GLASS_KILN_POI, ModVillagers.GLASSBLOWER);
		helper.onEachTick(() -> {
			helper.assertTrue(held(helper, OWN, Items.SAND) == 4, "the basin's sand was taken: " + contents(helper, OWN) + "; the Glassblower carries "
				+ ModAttachments.BUILDER_BAG.getOrCreate(blower).stacks() + "; line: " + line(helper, a));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) == 0, "the Glassblower made glass from a machine's sand");
		});
		helper.runAfterDelay(LOOK, () -> {
			// which making the line names with Create loaded is B1195's test (QaCrafterOwnModdedStore1111eBugCompatTests)
			String text = line(helper, a);
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of ") && text.endsWith(" to make 4 Glass"), "the builder's line: '"
				+ text + "', expected 'Can't be made yet: short of ... to make 4 Glass'");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(blower) == null, "the Glassblower holds a job he can't finish: " + ModAttachments.CRAFTER_JOB.get(blower));
			helper.succeed();
		});
	}

	/** The vault test saved and loaded mid-job: 4 glass asked, he is reloaded while firing and while carrying; 4 sand, 1 coal used. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwnMod1111eVaultReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwnMod1111eVaultReload")
	public void theGlassblowerReloadedWithHisOwnVaultFiresExactlyTheAskedGlass(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.GLASS, 4, 1, "vaultreload"), Blocks.CHEST.defaultBlockState());
		vault(helper, OWN);
		Villager blower = glassblower(helper, new ItemStack(Items.SAND, 6), new ItemStack(Items.COAL, 2));
		ownIsListed(helper);
		Map<String, AtomicBoolean> seen = reloads(helper, blower.getUUID());
		watch(helper, a, blower.getUUID(), List.of(SAND_AND_GLASS), (start, end) -> {
			used(helper, start, end, Items.SAND, 4);
			used(helper, start, end, Items.COAL, 1);
			exactly(helper, end, Items.GLASS, 4);
			helper.assertTrue(held(helper, OWN, Items.SAND) == 2 && held(helper, OWN, Items.COAL) == 1, "his vault should keep 2 sand and 1 coal: "
				+ contents(helper, OWN));
			seen.forEach((what, happened) -> helper.assertTrue(happened.get(), "never happened: " + what + ", so the reload wasn't tested"));
		});
	}

	/**
	 * The builder's own supply is a Create Item Vault (no chest) holding 4 sand and a coal; the Glassblower has nothing:
	 * the glass is fired from the vault's sand, comes back once and is built.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaOwnMod1111eBuilderVault"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwnMod1111eBuilderVault")
	public void aBuildersCreateVaultGetsTheGlassOnce(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.GLASS, 4, 1, "buildervault"), vaultState(), new ItemStack(Items.SAND, 4), new ItemStack(Items.COAL, 1));
		helper.setBlock(STATION, ModBlocks.GLASS_KILN);
		Villager blower = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), blower, helper.absolutePos(STATION), ModVillagers.GLASS_KILN_POI, ModVillagers.GLASSBLOWER);
		List<BlockPos> supplies = SupplyContainers.find(helper.getLevel(), helper.absolutePos(BENCH), null);
		helper.assertTrue(supplies.contains(helper.absolutePos(CHEST)), "setup: the builder's vault isn't one of his supplies: " + supplies);
		watch(helper, a, blower.getUUID(), List.of(SAND_AND_GLASS), (start, end) -> {
			used(helper, start, end, Items.SAND, 4);
			used(helper, start, end, Items.COAL, 1);
			exactly(helper, end, Items.GLASS, 4);
		});
	}


	// ------------------------------------------------------------------------------------------------------ set-up

	static BlockState vaultState() {
		return with(block("create:item_vault").defaultBlockState(), "axis", "x");
	}

	static void vault(GameTestHelper helper, BlockPos rel) {
		set(helper.getLevel(), helper.absolutePos(rel), vaultState());
		helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(rel)) != null, "setup: the vault has no block entity");
	}

	/** A Glassblower at a Glass Kiln at STATION, his own store at OWN (already placed) holding {@code own}. */
	static Villager glassblower(GameTestHelper helper, ItemStack... own) {
		helper.setBlock(STATION, ModBlocks.GLASS_KILN);
		put(helper, OWN, own);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), ModVillagers.GLASS_KILN_POI, ModVillagers.GLASSBLOWER);
		return v;
	}

	private static void ownIsListed(GameTestHelper helper) {
		List<BlockPos> his = SupplyContainers.find(helper.getLevel(), helper.absolutePos(STATION), null);
		helper.assertTrue(his.contains(helper.absolutePos(OWN)), "setup: the store by his workstation isn't one of his: " + his);
	}

	static void prepare(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AABB around = helper.getBounds().inflate(48);
		for (Entity e : level.getEntitiesOfClass(Entity.class, around, e -> !(e instanceof Player))) {
			e.discard();
		}
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : List.copyOf(manager.all())) {
			if (site.bench() != null && around.inflate(Builders.MAX_SITE_DISTANCE).contains(net.minecraft.world.phys.Vec3.atCenterOf(site.bench()))) {
				manager.remove(site.id());
			}
		}
		AABB box = helper.getBounds();
		for (BlockPos storehouse : level.getPoiManager().findAll(h -> h.is(ModVillagers.STOREHOUSE_POI),
			p -> !box.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)), BlockPos.containing(box.getCenter()), 48 + 40,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
			level.removeBlock(storehouse, false);
		}
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		// the compat world's clock stands still: in work hours
		helper.setDayTime(3000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		PartnerShowsCompatTests.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
	}

	/** A blueprint of {@code dx} x {@code dz} {@code block}s on the floor at ROW. */
	static ResourceLocation area(GameTestHelper helper, Block block, int dx, int dz, String what) {
		for (int x = 0; x < dx; x++) {
			for (int z = 0; z < dz; z++) {
				helper.setBlock(ROW.offset(x, 0, z), block);
			}
		}
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat",
			"qaownmod1111e_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(dx, 1, dz), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < dx; x++) {
			for (int z = 0; z < dz; z++) {
				helper.setBlock(ROW.offset(x, 0, z), Blocks.AIR);
			}
		}
		return id;
	}

	/** A builder at the Builder's Bench at BENCH, his supply ({@code store}) at CHEST holding {@code items}, building {@code id} at ROW. */
	static Asker asker(GameTestHelper helper, ResourceLocation id, BlockState store, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		set(level, helper.absolutePos(CHEST), store);
		put(helper, CHEST, items);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site == null ? null : site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("setup: blueprint not found: " + id);
		}
		PartnerShowsCompatTests.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return new Asker(site, plan);
	}

	/** Puts the stacks into the store at {@code rel} (any kind), as a hopper would. */
	static void put(GameTestHelper helper, BlockPos rel, ItemStack... stacks) {
		for (ItemStack s : stacks) {
			ItemStack rest = SupplyContainers.insert(helper.getLevel(), List.of(helper.absolutePos(rel)), s.copy());
			helper.assertTrue(rest.isEmpty(), "setup: " + rest + " didn't go into " + rel);
		}
	}

	static String line(GameTestHelper helper, Asker a) {
		BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
		if (live == null) {
			return "(the build is done)";
		}
		Component line = JobShortages.line(helper.getLevel(), live.id(), live.missing(), helper.absolutePos(BENCH));
		return line == null ? null : line.getString();
	}

	/** Saves and loads the crafter the first time he is making, and the first time he carries what he made. */
	private static Map<String, AtomicBoolean> reloads(GameTestHelper helper, UUID crafter) {
		Map<String, AtomicBoolean> seen = new LinkedHashMap<>();
		AtomicBoolean crafting = new AtomicBoolean();
		AtomicBoolean delivering = new AtomicBoolean();
		seen.put("the crafter reloaded while making", crafting);
		seen.put("the crafter reloaded while carrying", delivering);
		ServerLevel level = helper.getLevel();
		helper.onEachTick(() -> {
			if (!(level.getEntity(crafter) instanceof Villager v)) {
				return;
			}
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(v);
			if (job == null) {
				return;
			}
			if (!crafting.get() && job.phase() == CrafterJob.Phase.CRAFTING) {
				crafting.set(true);
				reload(level, v);
			} else if (!delivering.get() && job.phase() == CrafterJob.Phase.DELIVERING) {
				delivering.set(true);
				reload(level, v);
			}
		});
		return seen;
	}

	private static void reload(ServerLevel level, Villager v) {
		CompoundTag tag = new CompoundTag();
		v.saveWithoutId(tag);
		v.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		Village.forget(copy);
	}

	/**
	 * Every 20 ticks no law's quantity is above its start (nothing doubled). When the build is done, {@link #SETTLE} more
	 * ticks with the crafter making nothing more, then every law holds exactly (nothing lost) and {@code end} checks.
	 */
	static void watch(GameTestHelper helper, Asker a, UUID crafter, List<Law> laws, BiConsumer<Map<Item, Integer>, Map<Item, Integer>> end) {
		ServerLevel level = helper.getLevel();
		List<Item> items = new ArrayList<>(List.of(Items.COAL, Items.CHARCOAL));
		laws.forEach(l -> l.weights().keySet().stream().filter(i -> !items.contains(i)).forEach(items::add));
		Map<Item, Integer> start = tally(helper, items);
		long t0 = level.getGameTime();
		AtomicLong doneAt = new AtomicLong(-1);
		AtomicLong craftedAtDone = new AtomicLong();
		helper.onEachTick(() -> {
			long t = level.getGameTime() - t0;
			if (t % 20 != 0) {
				return;
			}
			Map<Item, Integer> now = tally(helper, items);
			for (Law law : laws) {
				if (law.of(now) > law.of(start)) {
					throw new GameTestAssertException("at tick " + t + " the " + law.name() + " add up to " + law.of(now) + ", more than the " + law.of(start)
						+ " at the start: something was doubled; " + state(helper, a, crafter, start, now));
				}
			}
			if (BuildSiteManager.get(level).get(a.site().id()) != null) {
				return;
			}
			if (doneAt.get() < 0) {
				doneAt.set(t);
				craftedAtDone.set(crafted(level, crafter));
				return;
			}
			helper.assertTrue(crafted(level, crafter) == craftedAtDone.get(), "at tick " + t + ", " + (t - doneAt.get()) + " ticks after the build was done, "
				+ "the crafter made more; " + state(helper, a, crafter, start, now));
			if (t - doneAt.get() < SETTLE) {
				return;
			}
			List<BlockPos> unfinished = a.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build; " + state(helper, a, crafter, start, now));
			for (Law law : laws) {
				helper.assertTrue(law.of(now) == law.of(start), "the " + law.name() + " add up to " + law.of(now) + ", not " + law.of(start) + ": some was lost; "
					+ state(helper, a, crafter, start, now));
			}
			helper.assertTrue(crafted(level, crafter) >= 1, "the crafter's count says he made nothing; " + state(helper, a, crafter, start, now));
			try {
				end.accept(start, now);
			} catch (GameTestAssertException e) {
				throw new GameTestAssertException(e.getMessage() + "; " + state(helper, a, crafter, start, now));
			}
			helper.succeed();
		});
	}

	private static int crafted(ServerLevel level, UUID id) {
		return level.getEntity(id) instanceof Villager v ? ModAttachments.ITEMS_CRAFTED.getOrElse(v, 0) : -1;
	}

	static void used(GameTestHelper helper, Map<Item, Integer> start, Map<Item, Integer> end, Item item, int expected) {
		int used = start.getOrDefault(item, 0) - end.getOrDefault(item, 0);
		helper.assertTrue(used == expected, used + " " + item + " were used, the spec's one batch for the asked amount is " + expected);
	}

	static void exactly(GameTestHelper helper, Map<Item, Integer> end, Item item, int expected) {
		int n = end.getOrDefault(item, 0);
		helper.assertTrue(n == expected, n + " " + item + " exist in the area, expected " + expected);
	}

	/** What the item storage at absolute {@code pos} holds, read through Fabric's transfer API (any side, the first that answers). */
	private static Map<Item, Long> stored(ServerLevel level, BlockPos pos) {
		Map<Item, Long> out = new LinkedHashMap<>();
		List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
		sides.add(0, null);
		for (Direction side : sides) {
			Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, side);
			if (storage == null) {
				continue;
			}
			try (Transaction tx = Transaction.openOuter()) {
				for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
					out.merge(view.getResource().getItem(), view.getAmount(), Long::sum);
				}
			}
			return out;
		}
		return out;
	}

	private static long held(GameTestHelper helper, BlockPos rel, Item item) {
		return stored(helper.getLevel(), helper.absolutePos(rel)).getOrDefault(item, 0L);
	}

	private static String contents(GameTestHelper helper, BlockPos rel) {
		return stored(helper.getLevel(), helper.absolutePos(rel)).toString();
	}

	/** Every one of {@code items} in the area: built blocks above the floor, every store, villagers' bags and inventories, the ground. */
	private static Map<Item, Integer> tally(GameTestHelper helper, List<Item> items) {
		Map<Item, Integer> out = new HashMap<>();
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds().inflate(3);
		int floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
		for (BlockPos p : BlockPos.betweenClosed((int) box.minX, (int) box.minY, (int) box.minZ, (int) box.maxX, (int) box.maxY, (int) box.maxZ)) {
			BlockState state = level.getBlockState(p);
			if (state.isAir()) {
				continue;
			}
			if (p.getY() >= floor && items.contains(state.getBlock().asItem())) {
				out.merge(state.getBlock().asItem(), 1, Integer::sum);
			}
			if (state.hasBlockEntity()) {
				stored(level, p.immutable()).forEach((item, n) -> {
					if (items.contains(item)) {
						out.merge(item, (int) (long) n, Integer::sum);
					}
				});
			}
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, box.inflate(16))) {
			for (Item item : items) {
				int n = ModAttachments.BUILDER_BAG.getOrCreate(v).count(item) + v.getInventory().countItem(item);
				if (n > 0) {
					out.merge(item, n, Integer::sum);
				}
			}
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box.inflate(16))) {
			if (items.contains(e.getItem().getItem())) {
				out.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
			}
		}
		return out;
	}

	private static String state(GameTestHelper helper, Asker a, UUID crafter, Map<Item, Integer> start, Map<Item, Integer> now) {
		ServerLevel level = helper.getLevel();
		BuildSite live = BuildSiteManager.get(level).get(a.site().id());
		List<String> out = new ArrayList<>();
		out.add("site " + (live == null ? "done" : live.stage() + "/" + live.status() + " missing " + live.missing()));
		out.add("line " + line(helper, a));
		if (level.getEntity(crafter) instanceof Villager v) {
			out.add(BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()) + " made " + ModAttachments.ITEMS_CRAFTED.getOrElse(v, 0)
				+ " job " + ModAttachments.CRAFTER_JOB.get(v) + " bag " + ModAttachments.BUILDER_BAG.getOrCreate(v).stacks());
		} else {
			out.add("crafter gone");
		}
		out.add("builder's store " + contents(helper, CHEST));
		out.add("own " + contents(helper, OWN));
		out.add("start " + start + " now " + now);
		return String.join("; ", out);
	}
}
