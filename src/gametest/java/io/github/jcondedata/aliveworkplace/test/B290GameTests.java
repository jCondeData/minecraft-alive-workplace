package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.SiteList;
import io.github.jcondedata.aliveworkplace.hall.FreeJobsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * B290: a builder working from a Builder's Workshop's own Blueprint Table, some 34 blocks from the village Storehouse,
 * counts and takes the Storehouse's materials whoever owns which (a hired builder and the village's own Storehouse in a
 * village whose hall the player owns; the village's own builder on the player's build and the player's Storehouse), and
 * the player moves a builder to another table with a sneak-click, without breaking either.
 */
public class B290GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	/** The workshop's table, with its own chest beside it. */
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos TABLE_CHEST = new BlockPos(2, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos HALL = new BlockPos(2, 2, 26);
	/** About 34 blocks from the table, with its chests around it. */
	private static final BlockPos STOREHOUSE = new BlockPos(26, 2, 26);
	private static final BlockPos[] STORE_CHESTS = {new BlockPos(23, 2, 26), new BlockPos(26, 2, 23), new BlockPos(23, 2, 23)};
	private static final UUID JESSE = UUID.fromString("5f1b2c3d-0000-4000-8000-000000000290");

	private record Setup(ServerLevel level, Villager builder, BuildSite site, BuildPlan plan) {
	}

	/**
	 * The owner's case: his builder (hired by handing over a blueprint) at the workshop table, the village's own Storehouse
	 * (nobody placed it: a builder put it up for the Steward) in the village whose hall he owns. The overhead line, the
	 * Book and Quill list and the fetching all count the Storehouse's stone, and the hut gets built from it.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"b290Hired"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "b290Hired")
	public void aHiredBuilderAtAWorkshopTableBuildsFromTheVillageStorehouse(GameTestHelper helper) {
		Setup s = setup(helper, null);
		ModAttachments.BUILDER_EMPLOYER.set(s.builder(), new Employer(JESSE, "Jesse"));
		hall(helper, JESSE);
		io.github.jcondedata.aliveworkplace.work.Village.forget(s.builder());
		assertCountsTheStore(helper, s);
		helper.succeedWhen(() -> assertBuiltFromStore(helper, s));
	}

	/**
	 * A player-owned Storehouse in the village, and the village's own (unhired) builder working on that player's build:
	 * the Storehouse counts for him too, with no Village Hall about.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"b290PlayerStore"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "b290PlayerStore")
	public void aPlayersStorehouseFeedsTheVillagesBuilderOnTheirBuild(GameTestHelper helper) {
		Setup s = setup(helper, JESSE);
		((StorehouseBlockEntity) helper.getBlockEntity(STOREHOUSE)).setOwner(JESSE, "Jesse");
		io.github.jcondedata.aliveworkplace.work.Village.forget(s.builder());
		helper.assertTrue(ModAttachments.BUILDER_EMPLOYER.get(s.builder()) == null, "the builder is hired");
		assertCountsTheStore(helper, s);
		helper.succeedWhen(() -> assertBuiltFromStore(helper, s));
	}

	/**
	 * Someone else's Storehouse (a player who isn't a friend) is still not the builder's: he waits for what it holds,
	 * and nothing is taken from it.
	 */
	//$ gametest_ticks_batch HUGE '400' '"b290Stranger"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "b290Stranger")
	public void aStrangersStorehouseStaysTheirs(GameTestHelper helper) {
		Setup s = setup(helper, JESSE);
		((StorehouseBlockEntity) helper.getBlockEntity(STOREHOUSE)).setOwner(UUID.fromString("5f1b2c3d-0000-4000-8000-000000000291"), "Stranger");
		io.github.jcondedata.aliveworkplace.work.Village.forget(s.builder());
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(left(helper, Items.COBBLESTONE) == 25, "cobblestone was taken from a stranger's Storehouse");
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) != null, "built without the stranger's stone");
			helper.succeed();
		});
	}

	/**
	 * Two Blueprint Tables: a sneak-click on the second with an empty hand, standing by the builder, moves him and his
	 * build there; both tables stand, the first is free again, his status names the new one, and after a save and
	 * reload he still works there. A second sneak-click says he already does.
	 */
	//$ gametest_batch HUGE '"b290Move"'
	@GameTest(template = HUGE, batch = "b290Move")
	public void aSneakClickMovesTheBuilderToAnotherTable(GameTestHelper helper) {
		Setup s = setup(helper, JESSE);
		ServerLevel level = s.level();
		BlockPos second = new BlockPos(20, 2, 4);
		helper.setBlock(second, ModBlocks.BLUEPRINT_TABLE);
		BlockPos secondAbs = helper.absolutePos(second);
		BlockPos firstAbs = helper.absolutePos(TABLE);
		ensurePoi(level, secondAbs);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 by = helper.absoluteVec(Vec3.atBottomCenterOf(TABLE.south(2)));
		player.moveTo(by.x, by.y, by.z);
		player.setShiftKeyDown(true);
		click(level, player, secondAbs);
		helper.assertTrue(Builders.benchPos(s.builder()).filter(secondAbs::equals).isPresent(),
			"the builder still works at " + Builders.benchPos(s.builder()));
		helper.assertTrue(level.getBlockState(firstAbs).is(ModBlocks.BLUEPRINT_TABLE) && level.getBlockState(secondAbs).is(ModBlocks.BLUEPRINT_TABLE),
			"a table was broken");
		helper.assertTrue(level.getPoiManager().getFreeTickets(firstAbs) == 1, "the old table isn't free again");
		helper.assertTrue(secondAbs.equals(s.site().bench()), "the build still draws on " + s.site().bench());
		String where = secondAbs.getX() + " " + secondAbs.getY() + " " + secondAbs.getZ();
		helper.assertTrue(names(Builders.statusText(level, s.site(), s.builder()), "message.aliveworkplace.status.table", where),
			"the status doesn't name the new table: " + Builders.statusText(level, s.site(), s.builder()));
		// already there: said so, nothing changes
		click(level, player, secondAbs);
		helper.assertTrue(Builders.benchPos(s.builder()).filter(secondAbs::equals).isPresent(), "a second click moved him");
		// saved and loaded: still at the new table
		CompoundTag tag = new CompoundTag();
		s.builder().saveWithoutId(tag);
		s.builder().discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		helper.assertTrue(copy.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(g -> g.pos().equals(secondAbs)).isPresent(),
			"after a reload he works at " + Builders.benchPos(copy));
		// another builder can now take the first table: it's free, and the moved one doesn't fight for it
		Villager other = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Builders.employ(level, other, firstAbs);
		helper.assertTrue(Builders.benchPos(other).filter(firstAbs::equals).isPresent(), "the old table couldn't be taken");
		// moving the copy back to the first table is refused: it's taken
		Vec3 byFirst = helper.absoluteVec(Vec3.atBottomCenterOf(TABLE.south(2)));
		copy.moveTo(byFirst.x, byFirst.y, byFirst.z);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.BenchMoves.refusal(level, copy, firstAbs) != null, "a taken table was offered");
		helper.succeed();
	}

	/** With nobody near, a sneak-click opens the table as before (no builder is moved from afar). */
	//$ gametest_batch HUGE '"b290Alone"'
	@GameTest(template = HUGE, batch = "b290Alone")
	public void aSneakClickWithNoBuilderNearMovesNobody(GameTestHelper helper) {
		Setup s = setup(helper, JESSE);
		ServerLevel level = s.level();
		BlockPos second = new BlockPos(24, 2, 4);
		helper.setBlock(second, ModBlocks.BLUEPRINT_TABLE);
		BlockPos secondAbs = helper.absolutePos(second);
		ensurePoi(level, secondAbs);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 by = helper.absoluteVec(Vec3.atBottomCenterOf(second.south(2)));
		player.moveTo(by.x, by.y, by.z);
		player.setShiftKeyDown(true);
		helper.assertTrue(!io.github.jcondedata.aliveworkplace.build.BenchMoves.sneakUse(player, secondAbs), "a far builder was moved");
		helper.assertTrue(Builders.benchPos(s.builder()).filter(helper.absolutePos(TABLE)::equals).isPresent(), "the builder moved");
		helper.succeed();
	}

	/** The Village Hall's free job blocks page offers a free Blueprint Table to the village's builder at another table. */
	//$ gametest_batch HUGE '"b290Hall"'
	@GameTest(template = HUGE, batch = "b290Hall")
	public void theHallOffersAFreeTableToTheVillagesBuilder(GameTestHelper helper) {
		Setup s = setup(helper, JESSE);
		ServerLevel level = s.level();
		hall(helper, JESSE);
		BlockPos second = new BlockPos(20, 2, 4);
		helper.setBlock(second, ModBlocks.BLUEPRINT_TABLE);
		BlockPos secondAbs = helper.absolutePos(second);
		ensurePoi(level, secondAbs);
		FreeJobsPage.FreeBlock free = FreeJobsPage.at(level, helper.absolutePos(HALL), secondAbs);
		helper.assertTrue(free != null, "the free table isn't on the page");
		helper.assertTrue(free.takers().contains(s.builder()), "the builder isn't offered the free table: " + free.takers());
		io.github.jcondedata.aliveworkplace.build.BenchMoves.move(level, s.builder(), secondAbs);
		helper.assertTrue(Builders.benchPos(s.builder()).filter(secondAbs::equals).isPresent(), "not moved");
		helper.assertTrue(FreeJobsPage.at(level, helper.absolutePos(HALL), secondAbs) == null, "the table he took is still free");
		helper.assertTrue(FreeJobsPage.at(level, helper.absolutePos(HALL), helper.absolutePos(TABLE)) != null, "the table he left isn't free");
		helper.succeed();
	}

	// --- helpers -----------------------------------------------------------------------------------------------------

	/** Whether {@code text} holds the line {@code key} with {@code arg} as its first argument. */
	private static boolean names(Component text, String key, String arg) {
		if (text.getContents() instanceof TranslatableContents t && t.getKey().equals(key) && t.getArgs().length > 0
			&& (t.getArgs()[0] instanceof Component c ? c.getString() : String.valueOf(t.getArgs()[0])).equals(arg)) {
			return true;
		}
		if (text.getContents() instanceof TranslatableContents t) {
			for (Object a : t.getArgs()) {
				if (a instanceof Component c && names(c, key, arg)) {
					return true;
				}
			}
		}
		for (Component sibling : text.getSiblings()) {
			if (names(sibling, key, arg)) {
				return true;
			}
		}
		return false;
	}

	private static void click(ServerLevel level, ServerPlayer player, BlockPos table) {
		level.getBlockState(table).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false));
	}

	private static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	/** A Village Hall in the village, owned by {@code owner}; gone when the test ends. */
	private static void hall(GameTestHelper helper, UUID owner) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos abs = helper.absolutePos(HALL);
		ensurePoi(level, abs);
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setOwner(owner, "Jesse");
		VillageHalls.forgetNearest();
		Leftovers.after(helper, () -> {
			level.removeBlock(abs, false);
			VillageHalls.forgetNearest();
		});
	}

	private static Setup setup(GameTestHelper helper, @org.jetbrains.annotations.Nullable UUID siteOwner) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.storehouses(helper);
		Leftovers.village(helper, 48);
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		ensurePoi(level, helper.absolutePos(STOREHOUSE));
		for (BlockPos chest : STORE_CHESTS) {
			helper.setBlock(chest, Blocks.CHEST);
		}
		// the Storehouse holds the stone and the door; the workshop's own chest the planks and the torch
		fill(helper.getBlockEntity(STORE_CHESTS[0]), new ItemStack(Items.COBBLESTONE, 25));
		fill(helper.getBlockEntity(STORE_CHESTS[2]), new ItemStack(Items.OAK_DOOR));
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(TABLE_CHEST, Blocks.CHEST);
		fill(helper.getBlockEntity(TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.TORCH));
		Villager builder = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSite site = siteOwner == null ? Builders.start(level, builder, null, TEST_HUT, placement)
			: Builders.start(level, builder, siteOwner, "Jesse", TEST_HUT, placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + TEST_HUT);
		}
		return new Setup(level, builder, site, plan);
	}

	/** The overhead's "Short of" line, the Book and Quill list and the status count what the Storehouse holds. */
	private static void assertCountsTheStore(GameTestHelper helper, Setup s) {
		List<Component> more = BuilderStatusSync.status(s.level(), s.site(), s.builder()).more();
		helper.assertTrue(!more.isEmpty() && more.get(0).getContents() instanceof TranslatableContents t
			&& t.getKey().equals("message.aliveworkplace.overhead.has_all"), "the overhead counts only the table's chests: " + more);
		helper.assertTrue(more.size() == 2 && more.get(1).getContents() instanceof TranslatableContents t2
			&& t2.getKey().equals("message.aliveworkplace.overhead.supplies_storehouse"), "the overhead doesn't name the storehouse: " + more);
		for (BlueprintSupplies.Line line : SiteList.checklist(s.level(), s.site(), s.builder()).lines()) {
			helper.assertTrue(line.toBring() == 0, "the Book and Quill list asks for " + line.toBring() + " " + line.item());
		}
		Builders.statusText(s.level(), s.site(), s.builder());
		helper.assertTrue(s.site().missing().isEmpty(), "the status says it needs " + s.site().missing());
	}

	private static void assertBuiltFromStore(GameTestHelper helper, Setup s) {
		helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage=" + s.site().stage()
			+ " status=" + s.site().status() + " missing=" + s.site().missing());
		List<BlockPos> unfinished = s.plan().unfinished(s.level());
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
		for (Item item : new Item[] {Items.COBBLESTONE, Items.OAK_DOOR}) {
			helper.assertTrue(left(helper, item) == 0, "the storehouse still holds " + left(helper, item) + " " + item);
		}
	}

	private static int left(GameTestHelper helper, Item item) {
		int left = 0;
		for (BlockPos chest : STORE_CHESTS) {
			left += ((Container) helper.getBlockEntity(chest)).countItem(item);
		}
		return left;
	}

	private static void fill(Object container, ItemStack... items) {
		for (int i = 0; i < items.length; i++) {
			((Container) container).setItem(i, items[i].copy());
		}
	}
}
