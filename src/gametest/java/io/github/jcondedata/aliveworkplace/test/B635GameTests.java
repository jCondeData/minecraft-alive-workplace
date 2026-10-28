package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.FreeJobsPage;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Bug B635 (owner, live server): a Carpenter's Workshop built by the builder stood next to the Village Hall, but its
 * crafting table was never a free Carpenter block: not on the Free job blocks page, not on Job ▾ in the People table,
 * so nobody could be made a Carpenter from the hall. The hall only looked at blocks a jobless villager takes by
 * themselves (the game's acquirable_job_site tag), and the crafting table is left out of that tag on purpose (only an
 * item or the hall gives its job). Every check goes through the player's own entry points: the builder builds the
 * workshop, the hall is right-clicked, People, Job ▾ and a job are clicked as the client sends them.
 */
public class B635GameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 24);
	private static final BlockPos BLUEPRINT_TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos WORKSHOP = new BlockPos(9, 2, 6);
	private static final BlockPos TABLE = new BlockPos(15, 2, 19);
	private static final BlockPos LECTERN = new BlockPos(20, 2, 24);

	/** The hall; its village reaches {@code radius} blocks, all of it inside the test's area but for the build's far corners. */
	private static void village(GameTestHelper helper, int radius) {
		Leftovers.clear(helper);
		HallSections.forget();
		HallJobs.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = radius;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager jobless(GameTestHelper helper, BlockPos at, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		v.setCustomName(Component.literal(name));
		return v;
	}

	/** Opens the hall as a player does (a right-click on the block) and its People section (the sidebar's click). */
	private static ChoiceMenu openPeople(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the sidebar didn't take People");
		return menu;
	}

	private static int slotOf(GameTestHelper helper, ChoiceMenu menu, Villager villager) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			CompoundTag row = HallPeople.row(menu.icon(slot));
			if (row != null && row.getString("uuid").equals(villager.getStringUUID())) {
				return slot;
			}
		}
		helper.fail("no row for " + villager.getName().getString());
		return -1;
	}

	private static List<ItemStack> jobDropdown(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager villager) {
		helper.assertTrue(menu.clickMenuButton(player, HallJobs.OPEN + slotOf(helper, menu, villager)), "Job ▾ wasn't taken");
		return HallJobs.lines(menu.icon(slotOf(helper, menu, villager)));
	}

	private static int id(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getId(job);
	}

	private static ItemStack carpenterLine(GameTestHelper helper, List<ItemStack> lines) {
		for (ItemStack line : lines) {
			CompoundTag mark = HallJobs.mark(line);
			if (mark != null && mark.getInt("id") == id(ModVillagers.CARPENTER) && !mark.getString("state").equals("header")) {
				return line;
			}
		}
		helper.fail("no Carpenter line in " + lines.stream().map(l -> l.getHoverName().getString()).toList());
		return ItemStack.EMPTY;
	}

	private static String state(ItemStack line) {
		return HallJobs.mark(line).getString("state");
	}

	private static void pick(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, VillagerProfession job) {
		helper.assertTrue(menu.clickMenuButton(player, HallJobs.PICK + id(job)), "the pick wasn't taken");
	}

	private static Optional<BlockPos> site(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
	}

	private static boolean listedAsCarpenter(ServerLevel level, BlockPos hall, BlockPos table) {
		return VillageHalls.freeStations(level, hall).stream()
			.anyMatch(f -> f.pos().equals(table) && f.profession() == ModVillagers.CARPENTER);
	}

	/**
	 * The owner's case: the builder builds a Carpenter's Workshop by the hall. Its crafting table is then on the Free job
	 * blocks page as a Carpenter's, Job ▾ for a jobless villager says "Carpenter · 1 free Crafting Table", and one click
	 * makes them the Carpenter there, without walking to it; the table then leaves the list.
	 */
	//$ gametest_ticks_batch AREA '9000' '"b635BuiltWorkshop"'
	@GameTest(template = AREA, timeoutTicks = 9000, batch = "b635BuiltWorkshop")
	public void aBuiltWorkshopsTableIsAFreeCarpenterBlock(GameTestHelper helper) {
		village(helper, 20);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		helper.setBlock(BLUEPRINT_TABLE, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, BLUEPRINT_TABLE.offset(1, 0, 1));
		Builders.employ(level, builder, helper.absolutePos(BLUEPRINT_TABLE));
		Villager pip = jobless(helper, new BlockPos(12, 2, 22), "Pip");
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(WORKSHOP), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, StarterBlueprints.CARPENTERS_WORKSHOP.id(), placement);
		helper.assertTrue(site != null, "the workshop didn't start");
		UUID id = site.id();
		BoundingBox box = site.plan(level).bounds();
		helper.onEachTick(() -> helper.setDayTime(2000));
		// The hall's checks run once, when the build is done (a player opens the hall then); a failure is kept and reported.
		String[] failed = {null};
		boolean[] checked = {false};
		helper.succeedWhen(() -> {
			if (failed[0] != null) {
				throw new net.minecraft.gametest.framework.GameTestAssertException(failed[0]);
			}
			if (checked[0]) {
				return;
			}
			helper.assertTrue(BuildSiteManager.get(level).get(id) == null || site.isDone(), "still building: " + site.stage() + " " + site.status());
			try {
				checkTheHall(helper, level, box, pip);
			} catch (net.minecraft.gametest.framework.GameTestAssertException e) {
				failed[0] = e.getMessage();
				throw e;
			}
			checked[0] = true;
		});
	}

	private static void checkTheHall(GameTestHelper helper, ServerLevel level, BoundingBox box, Villager pip) {
		{
			BlockPos table = null;
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
					table = pos.immutable();
				}
			}
			helper.assertTrue(table != null, "the workshop has no crafting table");
			BlockPos hall = helper.absolutePos(HALL);
			helper.assertTrue(level.getPoiManager().getType(table).map(h -> h.is(ModVillagers.CRAFTING_TABLE_POI)).orElse(false),
				"the builder's table has no workstation record: " + level.getPoiManager().getType(table));
			helper.assertTrue(listedAsCarpenter(level, hall, table), "the workshop's table isn't a free Carpenter block: " + VillageHalls.freeStations(level, hall));
			BlockPos at = table;
			helper.assertTrue(FreeJobsPage.blocks(level, hall).stream().anyMatch(b -> b.station().pos().equals(at) && b.jobs().contains(ModVillagers.CARPENTER)),
				"not on the Free job blocks page");
			List<String> seen = new ArrayList<>();
			ServerPlayer player = listeningPlayer(helper, seen);
			ChoiceMenu menu = openPeople(helper, player);
			ItemStack line = carpenterLine(helper, jobDropdown(helper, player, menu, pip));
			helper.assertTrue(line.getHoverName().getString().equals("Carpenter · 1 free Crafting Table") && state(line).equals("free"),
				"the Carpenter line: " + line.getHoverName().getString() + " " + state(line));
			pick(helper, player, menu, ModVillagers.CARPENTER);
			helper.assertTrue(pip.getVillagerData().getProfession() == ModVillagers.CARPENTER && site(pip).equals(Optional.of(table)),
				"Pip isn't the Carpenter at the table: " + pip.getVillagerData() + " " + site(pip));
			helper.assertTrue(seen.contains("Pip is now a Carpenter"), "not told: " + seen);
			helper.assertTrue(!listedAsCarpenter(level, hall, table), "the taken table is still listed free");
		}
	}

	/**
	 * A working villager (a Novice librarian) is retrained as a Carpenter from Job ▾ at a crafting table placed as the
	 * builder places blocks; saved and reloaded, she's still the Carpenter at the table.
	 */
	//$ gametest_ticks_batch AREA '200' '"b635Retrain"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b635Retrain")
	public void aWorkingVillagerIsMadeACarpenter(GameTestHelper helper) {
		village(helper, 8);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		helper.setBlock(LECTERN, Blocks.LECTERN);
		Villager dara = helper.spawn(EntityType.VILLAGER, LECTERN.offset(1, 0, 1));
		dara.setNoAi(true);
		Jobs.employ(level, dara, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		dara.setVillagerXp(5);
		dara.setCustomName(Component.literal("Dara"));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 5));
		helper.runAfterDelay(3, () -> {
			BlockPos table = helper.absolutePos(TABLE);
			ChoiceMenu menu = openPeople(helper, player);
			ItemStack line = carpenterLine(helper, jobDropdown(helper, player, menu, dara));
			helper.assertTrue(line.getHoverName().getString().equals("Carpenter · 1 free Crafting Table") && state(line).equals("free"),
				"the Carpenter line: " + line.getHoverName().getString() + " " + state(line));
			pick(helper, player, menu, ModVillagers.CARPENTER);
			pick(helper, player, menu, ModVillagers.CARPENTER);
			helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.CARPENTER && site(dara).equals(Optional.of(table)),
				"Dara isn't the Carpenter at the table: " + dara.getVillagerData() + " " + site(dara));
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "a Novice's price isn't 5");
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(LECTERN)) == 1, "the lectern isn't free");
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(dara.saveWithoutId(new CompoundTag()));
			helper.assertTrue(copy.getVillagerData().getProfession() == ModVillagers.CARPENTER && site(copy).equals(Optional.of(table)),
				"loaded as " + copy.getVillagerData() + " at " + site(copy));
			copy.discard();
			helper.succeed();
		});
	}

	/**
	 * The likeliest break: the table is already a Carpenter's. It isn't offered as free, Job ▾ for a jobless villager says
	 * to place a Crafting Table, and the click changes nothing; when that Carpenter's table is broken and placed again it's
	 * listed free once more.
	 */
	//$ gametest_ticks_batch AREA '200' '"b635Claimed"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b635Claimed")
	public void aClaimedTableIsNotOffered(GameTestHelper helper) {
		village(helper, 8);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, TABLE.offset(1, 0, 1));
		carpenter.setNoAi(true);
		Jobs.employ(level, carpenter, helper.absolutePos(TABLE), ModVillagers.CRAFTING_TABLE_POI, ModVillagers.CARPENTER);
		Villager pip = jobless(helper, new BlockPos(12, 2, 22), "Pip");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos table = helper.absolutePos(TABLE);
			helper.assertTrue(!listedAsCarpenter(level, hall, table), "a Carpenter's table is listed free");
			ChoiceMenu menu = openPeople(helper, player);
			ItemStack line = carpenterLine(helper, jobDropdown(helper, player, menu, pip));
			helper.assertTrue(line.getHoverName().getString().equals("Carpenter · place a Crafting Table first") && state(line).equals("no_block"),
				"the Carpenter line: " + line.getHoverName().getString() + " " + state(line));
			pick(helper, player, menu, ModVillagers.CARPENTER);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.NONE && site(pip).isEmpty(), "Pip changed: " + pip.getVillagerData());
			helper.assertTrue(carpenter.getVillagerData().getProfession() == ModVillagers.CARPENTER, "the Carpenter lost the table");
			carpenter.discard();
			helper.setBlock(TABLE, Blocks.AIR);
			helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
			helper.assertTrue(listedAsCarpenter(level, hall, table), "the table placed again isn't listed free");
			helper.succeed();
		});
	}
}
