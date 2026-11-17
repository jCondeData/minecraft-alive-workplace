package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, 2026-11-17, lane-e-1117-1013, third pass): a player's own Create blocks inside a house of an OLD drawing,
 * refreshed (36.21, the hall's Repairs page) to a drawing REBUILT from scratch under the same id (Milestones 49-52,
 * tools/blueprints/REBUILD.md). 36.21: "Never touched: chests and their contents ... and anything a player placed or
 * changed". The drawings are those of the gametest helper RebuildDrawings1117e (copied here: the compat source set
 * can't see the gametest classes): OLD a 5x5 cobblestone floor with an oak plank ring (door at the front, x 2), NEW a
 * 7x3 stone brick floor with a spruce plank ring (door at the back, x 3). Each test puts a two-block Item Vault holding
 * 48 emeralds and a Shaft in the house, one where the new drawing wants a block and one where the old drawing had a
 * block the new one drops, then refreshes through the button with a restart (sites and builder saved and loaded) mid-way.
 * Moves and take-downs of old-drawing houses (B1372-B1374) and repairs (B1369) are not tested here.
 */
public class QaRebuildCreate1117e3CompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";
	static final BlockPos HALL = new BlockPos(14, 2, 14);
	static final BlockPos HOUSE = new BlockPos(4, 2, 6);
	static final BlockPos STORE = new BlockPos(2, 2, 3);
	static final BlockPos BENCH = new BlockPos(2, 2, 2);
	static final int EMERALDS = 48;

	/** Outside the old plot, on the new drawing's front wall (spruce wanted there): local to the house. */
	static final List<BlockPos> NEW_WANTS = List.of(new BlockPos(5, 1, 0), new BlockPos(6, 1, 0));
	/** On the old drawing's back wall (oak planks there), which the new drawing drops: local to the house. */
	static final List<BlockPos> OLD_DROPS = List.of(new BlockPos(1, 1, 4), new BlockPos(2, 1, 4));

	/**
	 * The vault (two blocks along x) on the new drawing's front wall, outside the old plot; the shaft on the old drawing's
	 * back wall, which the new drawing drops. Both stay, the vault one store with its 48 emeralds, nothing loose.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa_e1117e3_rebuild_vault_new"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_e1117e3_rebuild_vault_new")
	public void aVaultWhereTheNewDrawingWantsWallsAndAShaftOnTheDroppedWallSurviveARefresh(GameTestHelper helper) {
		refresh(helper, "vnew", NEW_WANTS, OLD_DROPS.get(1));
	}

	/**
	 * Swapped: the vault on the old drawing's back wall (dropped by the new drawing), the shaft on the new drawing's front
	 * wall outside the old plot. Both stay, the vault one store with its 48 emeralds, nothing loose.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa_e1117e3_rebuild_vault_old"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_e1117e3_rebuild_vault_old")
	public void aVaultOnTheDroppedWallAndAShaftWhereTheNewDrawingWantsAWallSurviveARefresh(GameTestHelper helper) {
		refresh(helper, "vold", OLD_DROPS, NEW_WANTS.get(0));
	}

	// ---- the shared run ---------------------------------------------------------------------------------------------

	private static void refresh(GameTestHelper helper, String what, List<BlockPos> vault, BlockPos shaft) {
		ResourceLocation id = id(helper, what);
		Stage s = stage(helper, id);
		for (Blueprint.Entry e : oldDrawing()) {
			put(helper, at(e.pos()), e.state());
		}
		BlockState vaultState = QaCreateMoverContents1110eCompatTests.with(
			QaCreateMoverContents1110eCompatTests.block("create:item_vault").defaultBlockState(), "axis", "x");
		for (BlockPos p : vault) {
			put(helper, at(p), vaultState);
		}
		put(helper, at(shaft), QaCreateMoverContents1110eCompatTests.block("create:shaft").defaultBlockState());
		BuildSiteManager.get(s.level()).recordFinished(s.id(), s.placement(), s.owner().getUUID());
		int spruceWanted = 15 - (int) NEW_WANTS.stream().filter(p -> vault.contains(p) || p.equals(shaft)).count();

		Villager[] builder = {null};
		boolean[] pressed = {false};
		boolean[] reloaded = {false};
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(QaCreateMoverContents1110eCompatTests.fill(s.level(), helper.absolutePos(at(vault.get(0))), Items.EMERALD, EMERALDS) != null,
				"(setup) the vault didn't take " + EMERALDS + " emeralds");
			assertVault(helper, vault, "(setup) before the refresh");
			rebuild(s.level(), id);
			reload(helper, s.level(), null); // the restart with the new jar
			builder[0] = builder(helper, s);
			ChoiceMenu menu = VillageHallScreen.forTest(s.owner(), helper.absolutePos(HALL));
			menu.press(HallPages.slot(RepairsPage.PAGE), s.owner());
			helper.assertTrue(menu.icon(4).is(Items.ANVIL), "not on the Repairs page: " + menu.icon(4));
			int slot = refreshSlot(menu);
			helper.assertTrue(slot >= 0, "no Refresh entry on the Repairs page for the rebuilt house");
			menu.press(slot, s.owner());
			helper.assertTrue(menu.icon(RepairsPage.REFRESH).is(Items.BRUSH), "no Refresh button: " + menu.icon(RepairsPage.REFRESH));
			menu.press(RepairsPage.REFRESH, s.owner());
			pressed[0] = Refreshes.siteOf(s.level(), s.placement()) != null;
			helper.assertTrue(pressed[0], "the button started no refresh with a free builder");
		});
		helper.onEachTick(() -> {
			if (!pressed[0] || reloaded[0] || Refreshes.siteOf(s.level(), s.placement()) == null
				|| !s.level().getBlockState(helper.absolutePos(at(new BlockPos(0, 1, 4)))).isAir()) {
				return;
			}
			reloaded[0] = true;
			// mid-refresh: the Create blocks must still stand as they were before the restart
			assertCreate(helper, vault, shaft, "mid-refresh, before the restart");
			builder[0] = reload(helper, s.level(), builder[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never restarted mid-refresh");
			helper.assertTrue(sites(s) == 0, "still refreshing: " + BuildSiteManager.get(s.level()).all().stream()
				.filter(x -> x.placement().equals(s.placement())).map(x -> x.stage() + " " + x.status()).toList());
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder[0]), "the builder is still at it");
			List<BlockPos> oldLeft = new ArrayList<>(where(helper, Blocks.COBBLESTONE));
			oldLeft.addAll(where(helper, Blocks.OAK_PLANKS));
			helper.assertTrue(oldLeft.isEmpty(), "the old drawing's blocks are left at " + oldLeft.size() + " spots, " + oldLeft.stream().limit(6).toList());
			helper.assertTrue(where(helper, Blocks.STONE_BRICKS).size() == 21, "stone brick floor: " + where(helper, Blocks.STONE_BRICKS).size() + " of 21");
			helper.assertTrue(where(helper, Blocks.SPRUCE_PLANKS).size() == spruceWanted, "spruce walls: " + where(helper, Blocks.SPRUCE_PLANKS).size()
				+ " of " + spruceWanted + " (15 less the player's Create blocks on the new wall)");
			assertCreate(helper, vault, shaft, "after the refresh");
			assertNothingLoose(helper, s);
		});
	}

	/** The vault's blocks and the shaft stand where the player put them, the vault one store holding the 48 once. */
	private static void assertCreate(GameTestHelper helper, List<BlockPos> vault, BlockPos shaft, String when) {
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(at(shaft))).is(QaCreateMoverContents1110eCompatTests.block("create:shaft")),
			when + ": the player's shaft at " + shaft.toShortString() + " is gone: " + helper.getLevel().getBlockState(helper.absolutePos(at(shaft))));
		assertVault(helper, vault, when);
	}

	private static void assertVault(GameTestHelper helper, List<BlockPos> vault, String when) {
		StringBuilder seen = new StringBuilder();
		boolean ok = true;
		for (BlockPos p : vault) {
			BlockPos abs = helper.absolutePos(at(p));
			boolean is = helper.getLevel().getBlockState(abs).is(QaCreateMoverContents1110eCompatTests.block("create:item_vault"));
			long n = is ? QaCreateMoverContents1110eCompatTests.held(helper.getLevel(), abs, Items.EMERALD) : -1;
			ok &= is && n == EMERALDS;
			seen.append(p.toShortString()).append('=').append(is ? n : "not a vault " + helper.getLevel().getBlockState(abs)).append("; ");
		}
		helper.assertTrue(ok, when + ": the player's two-block vault isn't one vault reading " + EMERALDS + " emeralds through each block: " + seen);
	}

	/** No emeralds, vault or shaft lying loose, and none of them in the builder's store. */
	private static void assertNothingLoose(GameTestHelper helper, Stage s) {
		Item vault = QaCreateMoverContents1110eCompatTests.block("create:item_vault").asItem();
		Item shaft = QaCreateMoverContents1110eCompatTests.block("create:shaft").asItem();
		List<String> loose = new ArrayList<>();
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8))) {
			if (e.getItem().is(Items.EMERALD) || e.getItem().is(vault) || e.getItem().is(shaft)) {
				loose.add(e.getItem().getCount() + " " + e.getItem().getItem());
			}
		}
		helper.assertTrue(loose.isEmpty(), "lying loose: " + loose);
		Container store = helper.getBlockEntity(STORE);
		helper.assertTrue(store.countItem(Items.EMERALD) == 0 && store.countItem(vault) == 0 && store.countItem(shaft) == 0,
			"the player's things went into the builder's store: " + store.countItem(Items.EMERALD) + " emeralds, "
				+ store.countItem(vault) + " vaults, " + store.countItem(shaft) + " shafts");
	}

	private static int refreshSlot(ChoiceMenu menu) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).is(Items.PAINTING) || menu.icon(slot).is(Items.SCAFFOLDING)) {
				return slot;
			}
		}
		return -1;
	}

	// ---- copied from the gametest helper RebuildDrawings1117e (unfurnished old drawing) ----------------------------

	record Stage(ServerLevel level, ResourceLocation id, BlueprintData.Placement placement, ServerPlayer owner) {
	}

	static List<Blueprint.Entry> oldDrawing() {
		List<Blueprint.Entry> out = new ArrayList<>();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				out.add(new Blueprint.Entry(new BlockPos(x, 0, z), Blocks.COBBLESTONE.defaultBlockState(), null));
				boolean ring = x == 0 || x == 4 || z == 0 || z == 4;
				if (ring && !(x == 2 && z == 0)) {
					out.add(new Blueprint.Entry(new BlockPos(x, 1, z), Blocks.OAK_PLANKS.defaultBlockState(), null));
				}
			}
		}
		return out;
	}

	static List<Blueprint.Entry> newDrawing() {
		List<Blueprint.Entry> out = new ArrayList<>();
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 3; z++) {
				out.add(new Blueprint.Entry(new BlockPos(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState(), null));
				boolean ring = x == 0 || x == 6 || z == 0 || z == 2;
				if (ring && !(x == 3 && z == 2)) {
					out.add(new Blueprint.Entry(new BlockPos(x, 1, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), null));
				}
			}
		}
		return out;
	}

	static void install(ServerLevel level, ResourceLocation id, Blueprint drawing) {
		var manager = level.getStructureManager();
		manager.remove(id);
		manager.getOrCreate(id).load(Lookup.lookup(BuiltInRegistries.BLOCK), BlueprintFiles.toStructureNbt(drawing));
	}

	/** The new jar: the id's drawing replaced by the new one, the old one shipped in its history. */
	static void rebuild(ServerLevel level, ResourceLocation id) {
		install(level, id, new Blueprint(id, new Vec3i(7, 2, 3), newDrawing()));
		Refreshes.addHistory(id, "before_rebuild", new Blueprint(id, new Vec3i(5, 2, 5), oldDrawing()));
	}

	static ResourceLocation id(GameTestHelper helper, String what) {
		BlockPos p = helper.absolutePos(HOUSE);
		// ends in a letter: a name ending _<digits> would read as an upgrade tier
		return ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_rbc1117e_" + what + "_" + Long.toHexString(helper.getLevel().getGameTime())
			+ "_" + Math.abs(p.getX()) + "x" + Math.abs(p.getZ()) + "h");
	}

	static void put(GameTestHelper helper, BlockPos rel, BlockState state) {
		helper.getLevel().setBlock(helper.absolutePos(rel), state, Block.UPDATE_ALL);
	}

	static BlockPos at(BlockPos local) {
		return HOUSE.offset(local);
	}

	static Stage stage(GameTestHelper helper, ResourceLocation id) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		BuildSiteManager sitesNow = BuildSiteManager.get(level);
		int reach = VillageHalls.RADIUS + 30;
		for (BuildSiteManager.Finished f : sitesNow.finishedNear(level, BlockPos.containing(helper.getBounds().getCenter()), reach)) {
			sitesNow.forgetFinished(f.placement());
		}
		helper.setDayTime(2000);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (BlockPos p : BlockPos.betweenClosed(HOUSE, HOUSE.offset(6, 3, 4))) {
			put(helper, p.immutable(), Blocks.AIR.defaultBlockState());
		}
		install(level, id, new Blueprint(id, new Vec3i(5, 2, 5), oldDrawing()));
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			Refreshes.removeHistory(id);
			level.getStructureManager().remove(id);
			BuildSiteManager.get(level).forgetFinished(placement);
			level.getServer().getPlayerList().remove(owner);
		});
		helper.setBlock(STORE, Blocks.CHEST);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		return new Stage(level, id, placement, owner);
	}

	static Villager builder(GameTestHelper helper, Stage s) {
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(s.level(), builder, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(s.owner().getUUID(), s.owner().getGameProfile().getName()));
		return builder;
	}

	/** A restart: the sites and the builder saved and loaded back. */
	static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		if (old == null) {
			return null;
		}
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}

	static long sites(Stage s) {
		return BuildSiteManager.get(s.level()).all().stream().filter(site -> site.placement().equals(s.placement())).count();
	}

	static List<BlockPos> where(GameTestHelper helper, Block block) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(6, 2, 4))) {
			if (helper.getLevel().getBlockState(helper.absolutePos(at(p))).is(block)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** A reset run when the test ends, pass or fail, ahead of the runner's own listener (as the gametest Leftovers.after). */
	static void after(GameTestHelper helper, Runnable reset) {
		try {
			java.lang.reflect.Field field = GameTestHelper.class.getDeclaredField("testInfo");
			field.setAccessible(true);
			GameTestInfo info = (GameTestInfo) field.get(helper);
			java.lang.reflect.Field listeners = GameTestInfo.class.getDeclaredField("listeners");
			listeners.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<GameTestListener> list = (List<GameTestListener>) listeners.get(info);
			list.add(0, new GameTestListener() {
				private void run() {
					try {
						reset.run();
					} catch (RuntimeException e) {
						io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.error("A reset after a test failed", e);
					}
				}

				@Override
				public void testStructureLoaded(GameTestInfo test) {
				}

				@Override
				public void testPassed(GameTestInfo test, GameTestRunner runner) {
					run();
				}

				@Override
				public void testFailed(GameTestInfo test, GameTestRunner runner) {
					run();
				}

				@Override
				public void testAddedForRerun(GameTestInfo old, GameTestInfo test, GameTestRunner runner) {
				}
			});
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
