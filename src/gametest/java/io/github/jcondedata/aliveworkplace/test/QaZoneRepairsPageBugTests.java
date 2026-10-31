package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.RepairAsks;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.BitSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (36.20 x 36.21): the village is its claimed area, the hall's radius or any cell painted into one of the City Plan's
 * zones (a zone painted in the plan's corner lies past the radius), and upkeep uses the same rule. The Village Hall's
 * Repairs page lists the village's buildings that need a repair; a repair asked about while nobody is online waits there
 * ("otherwise it waits on the Repairs page"). So a building the Builder keeps up in a painted corner zone, past the
 * radius, has to be on that village's Repairs page, or its held question can never be answered.
 *
 * <p>Scaled down like {@link QaReachZoneCornerGameTests}: hall radius 12, so the plan's grid is ±16 blocks with cells of
 * one block and the whole village fits one huge area (no far chunks to wait for). The build's owner is offline (a UUID no
 * player has), so the question is held for the page. The control is the same build inside the radius.
 */
public class QaZoneRepairsPageBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos BENCH = HALL.offset(-4, 0, 4);
	private static final BlockPos CHEST = HALL.offset(-4, 0, 6);

	/** The build in the painted south-east corner: origin 11 east, 13 south of the hall (17 blocks off, radius 12). */
	//$ gametest_ticks_batch AREA '400' '"qaZoneRepairsPage"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaZoneRepairsPage")
	public void aDamagedBuildInAPaintedCornerZoneIsOnTheRepairsPage(GameTestHelper helper) {
		run(helper, HALL.offset(11, 0, 13), true);
	}

	/** The control: the same build 8 west, 6 north of the hall, inside its radius. */
	//$ gametest_ticks_batch AREA '400' '"qaZoneRepairsPageControl"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaZoneRepairsPageControl")
	public void aDamagedBuildInsideTheRadiusIsOnTheRepairsPage(GameTestHelper helper) {
		run(helper, HALL.offset(-8, 0, -6), false);
	}

	private static void run(GameTestHelper helper, BlockPos wallAt, boolean corner) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean protection = VillageProtection.ENABLED;
		boolean enabled = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageProtection.ENABLED = protection;
			Upkeep.ENABLED = enabled;
			Upkeep.ASK = asks;
			VillageHalls.forgetNearest();
		});
		VillageHalls.RADIUS = 12;
		VillageProtection.ENABLED = false;
		Upkeep.ENABLED = true;
		Upkeep.ASK = true;
		helper.setDayTime(2000); // working hours
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos hall = helper.absolutePos(HALL);
		helper.assertTrue(CityPlan.half() == 16, "the plan's grid reaches " + CityPlan.half() + " blocks from the hall, not 16");

		// a Homes zone painted over the plan's south-east corner (both tests: the control's build just isn't in it)
		BitSet cells = new BitSet();
		for (int dx = 10; dx <= 15; dx++) {
			for (int dz = 12; dz <= 15; dz++) {
				int cell = CityPlan.cellAt(hall, hall.offset(dx, 0, dz));
				helper.assertTrue(cell >= 0, "the corner cell " + dx + "," + dz + " isn't on the plan's grid");
				cells.set(cell);
			}
		}
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));

		// the finished wall: four planks east from wallAt, the second knocked out
		for (int x = 0; x < 4; x++) {
			helper.setBlock(wallAt.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation wall = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_zone_repairs_" + Long.toHexString(level.getGameTime())
			+ "_" + helper.absolutePos(wallAt).getX() + "_" + helper.absolutePos(wallAt).getZ());
		level.getStructureManager().getOrCreate(wall).fillFromWorld(level, helper.absolutePos(wallAt), new Vec3i(4, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(wallAt.offset(1, 0, 0), Blocks.AIR);
		BlockPos origin = helper.absolutePos(wallAt);
		BlockPos centre = helper.absolutePos(wallAt.offset(2, 0, 0));
		double off = Math.sqrt(origin.distSqr(hall));
		if (corner) {
			helper.assertTrue(off > VillageHalls.RADIUS && Math.sqrt(centre.distSqr(hall)) > VillageHalls.RADIUS,
				"the setup: the corner build is only " + off + " blocks from the hall (radius " + VillageHalls.RADIUS + ")");
			helper.assertTrue(Reach.claims(level, hall, origin) && Reach.claims(level, hall, centre),
				"the setup: the corner build isn't in the village's claimed area");
		} else {
			helper.assertTrue(off <= VillageHalls.RADIUS, "the setup: the control build is " + off + " blocks from the hall");
		}

		// the builder at a bench inside the radius, hired by an owner who isn't online
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.offset(1, 0, 1));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		UUID owner = UUID.nameUUIDFromBytes(("qa-zone-repairs-offline-" + corner).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner, "QaOffline"));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(wall, placement, owner);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));

		helper.runAfterDelay(2, () -> {
			helper.assertTrue(level.getServer().getPlayerList().getPlayer(owner) == null, "the setup: the build's owner is online");
			helper.assertTrue(Reach.inSameVillage(level, helper.absolutePos(BENCH), origin), "the setup: the bench and the build aren't in one village");
			if (!ModAttachments.BUILDER_JOB.has(builder)) {
				Upkeep.look(level, builder);
			}
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder), "a repair started before anyone said yes");
			RepairAsks.Ask ask = manager.repairAsk(placement);
			helper.assertTrue(ask != null && ask.state() == RepairAsks.State.ASKED && ask.missing() == 1,
				"the builder didn't ask about the damaged wall " + Math.round(off) + " blocks from the hall: " + ask);

			boolean listed = RepairAsks.entries(level, hall).stream().anyMatch(e -> e.building().placement().equals(placement));
			int waiting = RepairAsks.waiting(level, hall);

			// the page itself, through its tab, opened by a player (not the build's owner: he's offline)
			ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
			Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(viewer));
			ChoiceMenu menu = VillageHallScreen.forTest(viewer, hall);
			int tab = HallPages.slot(RepairsPage.PAGE);
			helper.assertTrue(tab >= VillageHallScreen.PAGE_ROW, "the Repairs page has no tab: " + tab);
			menu.press(tab, viewer);
			ItemStack first = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(listed && waiting == 1 && first.is(Items.CRACKED_STONE_BRICKS),
				(corner ? "a damaged build in the village's painted corner zone (origin " + Math.round(off) + " blocks from the hall, radius "
					+ VillageHalls.RADIUS + ", in the claimed area), its repair asked about and held while its owner is offline, "
					: "a damaged build inside the hall's radius, its repair asked about and held, ")
					+ "isn't on the village's Repairs page: listed=" + listed + ", waiting=" + waiting + ", first slot " + first.getHoverName().getString());
			helper.succeed();
		});
	}
}
