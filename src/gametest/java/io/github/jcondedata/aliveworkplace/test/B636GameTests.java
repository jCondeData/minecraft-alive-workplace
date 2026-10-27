package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B636 (owner, live server): a builder short of something later in his build goes on building with what he has, and the
 * Storehouse's board listed what he lacks only once he stood waiting at that block, so the Porter and the village's jobs
 * started on it only then. Now the board lists the shortfall the moment the site is short, while he works, after a save
 * and load too, and drops it once it's supplied.
 */
public class B636GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos STOREHOUSE = new BlockPos(14, 2, 2);

	/** The test hut with everything but its torch (its last block) in the builder's chest, a Storehouse in the village. */
	private static BuildSite start(GameTestHelper helper, Villager[] builder) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.village(helper, 48);
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
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		chest.setItem(2, new ItemStack(Items.OAK_DOOR));
		builder[0] = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, builder[0], helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder[0], null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE));
		if (site == null || site.plan(level) == null) {
			throw new GameTestAssertException("setup: the test hut didn't start");
		}
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		return site;
	}

	/** How many torches the Storehouse's board asks for (0: none), as a player opening it sees it. */
	private static int torchesAsked(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
		for (int i = StorehouseBoard.FIRST_REQUEST; i < ChoiceMenu.SIZE; i++) {
			if (board.icon(i).is(Items.TORCH)) {
				return board.icon(i).getCount();
			}
		}
		return 0;
	}

	/** Still building the walls, not standing waiting for anything: the case the board used to miss. */
	private static boolean buildingOn(ServerLevel level, BuildSite site) {
		return BuildSiteManager.get(level).get(site.id()) != null && site.status() != BuildSite.Status.WAITING_FOR_MATERIALS
			&& (site.stage() == BuildPlan.Stage.CLEAR || site.stage() == BuildPlan.Stage.FOUNDATION || site.stage() == BuildPlan.Stage.STRUCTURE);
	}

	/**
	 * No torch anywhere: while the builder is still on the walls (not waiting), the board already asks for the 1 torch; a
	 * player's click on it hands one over into his chest and it's off the board at once; the hut is then finished whole.
	 */
	//$ gametest_ticks_batch AREA '2400' '"b636Board"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b636Board")
	public void whatABuilderLacksIsOnTheBoardWhileHeBuilds(GameTestHelper helper) {
		Villager[] builder = new Villager[1];
		BuildSite site = start(helper, builder);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		long[] askedAt = {-1};
		long[] givenAt = {-1};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (askedAt[0] >= 0 || !buildingOn(level, site)) {
				return;
			}
			int asked = torchesAsked(helper, player);
			if (asked == 0) {
				return;
			}
			helper.assertTrue(asked == 1, "the board asks for " + asked + " torches, the hut needs 1");
			askedAt[0] = helper.getTick();
			player.getInventory().add(new ItemStack(Items.TORCH, 3));
			ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
			for (int i = StorehouseBoard.FIRST_REQUEST; i < ChoiceMenu.SIZE; i++) {
				if (board.icon(i).is(Items.TORCH)) {
					board.press(i, player);
					break;
				}
			}
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.TORCH) == 1, "the click put " + chest.countItem(Items.TORCH) + " torches in the builder's chest");
			helper.assertTrue(player.getInventory().countItem(Items.TORCH) == 2, "the player kept " + player.getInventory().countItem(Items.TORCH));
			helper.assertTrue(torchesAsked(helper, player) == 0, "the board still asks for a torch after it was given");
			givenAt[0] = helper.getTick();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(askedAt[0] >= 0, "the board never asked for the torch while the builder was on the walls (stage " + site.stage()
				+ ", status " + site.status() + ", missing " + site.missing() + ")");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: stage " + site.stage() + " status " + site.status()
				+ " missing " + site.missing());
			List<BlockPos> wrong = site.plan(level).unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut wrong");
			helper.assertTrue(site.skipped() == 0, site.skipped() + " step(s) skipped");
		});
	}

	/**
	 * Listed while he builds, then the world saved and loaded (the sites and the builder from their saves): still listed
	 * while he builds on; a torch put in his chest takes it off the board while the build is still going.
	 */
	//$ gametest_ticks_batch AREA '2400' '"b636Reload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b636Reload")
	public void theShortfallIsListedAgainAfterAReloadAndGoesWhenSupplied(GameTestHelper helper) {
		Villager[] builder = new Villager[1];
		BuildSite first = start(helper, builder);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BuildSite[] site = {first};
		long[] marks = {-1, -1, -1, -1}; // listed, reloaded, listed again, off the board once supplied
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (marks[0] < 0) {
				if (buildingOn(level, site[0]) && torchesAsked(helper, player) == 1) {
					marks[0] = helper.getTick();
				}
			} else if (marks[1] < 0) {
				builder[0] = reload(helper, level, builder[0]);
				BuildSite loaded = Builders.activeSite(level, builder[0]);
				helper.assertTrue(loaded != null, "no site for the builder after the reload");
				site[0] = loaded;
				marks[1] = helper.getTick();
			} else if (marks[2] < 0) {
				if (buildingOn(level, site[0]) && torchesAsked(helper, player) == 1) {
					marks[2] = helper.getTick();
					Container chest = helper.getBlockEntity(CHEST);
					chest.setItem(chest.getContainerSize() - 1, new ItemStack(Items.TORCH));
				}
			} else if (marks[3] < 0 && BuildSiteManager.get(level).get(site[0].id()) != null && torchesAsked(helper, player) == 0) {
				marks[3] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(marks[0] >= 0, "the board never asked for the torch while the builder was on the walls");
			helper.assertTrue(marks[2] >= 0, "after the reload (tick " + marks[1] + ") the board didn't ask for the torch while he built on (stage "
				+ site[0].stage() + ", status " + site[0].status() + ", missing " + site[0].missing() + ")");
			helper.assertTrue(marks[3] >= 0, "a torch was put in the chest at tick " + marks[2] + " but the board asked for it until the build ended");
			helper.assertTrue(BuildSiteManager.get(level).get(site[0].id()) == null, "still building: stage " + site[0].stage() + " status "
				+ site[0].status() + " missing " + site[0].missing());
			List<BlockPos> wrong = site[0].plan(level).unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut wrong");
		});
	}

	/** As a save and a load of the world would: every build site from its save, and the builder from his. */
	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}
}
