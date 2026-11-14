package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * QA (lane C, 2026-11-11): the Village Hall's Bed ▾ (40.4) and a building on the move (45.4) together, from the player's
 * side. A villager whose house is being moved sleeps at the inn meanwhile; 45.4 gives him the moved bed when it goes up.
 * But if the player, while the house is away, gives him another bed from the Hall's People page (a free bed elsewhere),
 * that choice is the player's: when the Mover puts the old bed up again it shouldn't pull him back out of the bed the
 * player chose and leave that one free for anyone (that check: QaMoveHallBed1111cBugTests). Here, the controls: the pick
 * itself works while the house is away (the People page shows the new bed at once), and with no Hall pick he gets the
 * moved bed (as 45.4 says).
 */
public class QaMoveHallBed1111cGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:huge_area";
	static final BlockPos MIN = new BlockPos(2, 2, 2);
	private static final BlockPos MAX = new BlockPos(8, 6, 8);
	static final BlockPos TO = new BlockPos(16, 2, 16);
	private static final BlockPos DOOR = new BlockPos(5, 3, 2);
	static final BlockPos BED = new BlockPos(4, 3, 7);
	static final BlockPos LECTERN = new BlockPos(7, 3, 4);
	private static final BlockPos HALL = new BlockPos(15, 2, 2);
	private static final BlockPos INN = new BlockPos(25, 2, 3);
	static final BlockPos INN_BED = new BlockPos(24, 2, 6);
	/** The free bed the player gives him from the Hall, out in the open, away from the inn, the hall and both spots. */
	static final BlockPos OTHER_BED = new BlockPos(4, 2, 26);
	private static final int MAX_STEPS = 5000;

	record Setup(MovePlan before, BlockPos hall, BlockPos from, BlockPos to, Villager mover, ResourceLocation id) {
	}

	private static void set(ServerLevel level, BlockPos abs, BlockState state) {
		level.setBlock(abs, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	/** A bed with its head at {@code head} (helper coordinates), its foot to the north. */
	private static void bed(GameTestHelper helper, BlockPos head) {
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		set(helper.getLevel(), helper.absolutePos(head.north()), bed.setValue(BedBlock.PART, BedPart.FOOT));
		set(helper.getLevel(), helper.absolutePos(head), bed.setValue(BedBlock.PART, BedPart.HEAD));
	}

	/** A one-bed house the village knows, an inn with one free bed, a free bed in the open, a Village Hall and a Mover. */
	static Setup setup(GameTestHelper helper, String name) {
		Leftovers.clear(helper);
		HallBeds.forget();
		ServerLevel level = helper.getLevel();
		for (int x = MIN.getX(); x <= MAX.getX(); x++) {
			for (int z = MIN.getZ(); z <= MAX.getZ(); z++) {
				for (int y = MIN.getY(); y <= MAX.getY(); y++) {
					boolean edge = x == MIN.getX() || x == MAX.getX() || z == MIN.getZ() || z == MAX.getZ();
					BlockState state = y == MIN.getY() ? Blocks.COBBLESTONE.defaultBlockState()
						: y == MAX.getY() ? Blocks.SPRUCE_PLANKS.defaultBlockState()
						: edge ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
					set(level, helper.absolutePos(new BlockPos(x, y, z)), state);
				}
			}
		}
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
		set(level, helper.absolutePos(DOOR), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(level, helper.absolutePos(DOOR.above()), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		bed(helper, BED);
		set(level, helper.absolutePos(LECTERN), Blocks.LECTERN.defaultBlockState());
		set(level, helper.absolutePos(INN), ModBlocks.INN_COUNTER.defaultBlockState());
		bed(helper, INN_BED);
		bed(helper, OTHER_BED);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);

		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_hall_bed_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(MIN), new Vec3i(7, 5, 7), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(MIN), Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(id, placement, UUID.randomUUID());
		MovePlan before = MovePlan.capture(level, sites.finishedAt(level, id, helper.absolutePos(MIN)).orElseThrow()).orElseThrow();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos from = helper.absolutePos(MIN);
		BlockPos to = helper.absolutePos(TO);
		Leftovers.after(helper, () -> {
			HallBeds.forget();
			MoveSites.get(level).cancel(level, from);
			MoveSites.get(level).all().stream().filter(s -> s.from().equals(from)).forEach(s -> {
				while (s.stage() != MoveSite.Stage.DONE) {
					MoveSites.get(level).step(level, s);
				}
			});
			PendingMoves.get(level).remove(from);
			sites.forgetFinished(placement);
			sites.finishedIn(level).stream().filter(f -> f.structure().equals(id)).forEach(f -> sites.forgetFinished(f.placement()));
			sites.all().stream().filter(b -> b.structure().equals(id)).map(BuildSite::id).toList().forEach(sites::remove);
		});
		return new Setup(before, hall, from, to, mover(helper, new BlockPos(12, 2, 12)), id);
	}

	private static Villager mover(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at.north(), ModBlocks.MOVERS_CRATE);
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.MOVER));
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(at.north())));
		return villager;
	}

	/** Tomas, who sleeps in the house's bed; the move confirmed on the Move page. */
	static Villager tomas(GameTestHelper helper, Setup s) {
		ServerLevel level = helper.getLevel();
		PendingMoves.get(level).put(new PendingMoves.Move(s.hall(), s.from(), s.id(), s.to(), Rotation.CLOCKWISE_90, null));
		Villager tomas = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 4));
		tomas.setNoAi(true);
		tomas.setCustomName(Component.literal("Tomas"));
		helper.assertTrue(HallBeds.moveIn(level, tomas, helper.absolutePos(BED)), "setup: Tomas didn't get his bed");
		return tomas;
	}

	static MoveSite work(ServerLevel level, Setup s, Predicate<MoveSite> stop) {
		MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
		for (int i = 0; i < MAX_STEPS && site.stage() != MoveSite.Stage.DONE && !stop.test(site); i++) {
			MoverWork.work(level, s.mover(), site);
		}
		return site;
	}

	static BlockPos home(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	static String at(BlockPos pos) {
		return pos == null ? "none" : pos.toShortString();
	}

	/** The Hall's People page, opened as a player does; {@code out[0]} the menu, the result {@code villager}'s row's slot. */
	static int openPeople(GameTestHelper helper, ServerPlayer player, ChoiceMenu[] out, Villager villager) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the sidebar didn't take People");
		int slot = -1;
		for (int i = 0; i < ChoiceMenu.SIZE; i++) {
			CompoundTag row = HallPeople.row(menu.icon(i));
			if (row != null && row.getString("uuid").equals(villager.getStringUUID())) {
				slot = i;
			}
		}
		helper.assertTrue(slot >= 0, "the People page has no row for " + villager.getName().getString() + " while the house is away");
		out[0] = menu;
		return slot;
	}

	/** Job ▾ on {@code villager}'s row, then No job twice (the offer, then the confirm), as a player lets a worker go. */
	static void letGoAtTheHall(GameTestHelper helper, ServerPlayer player, Villager villager) {
		ChoiceMenu[] menu = new ChoiceMenu[1];
		int slot = openPeople(helper, player, menu, villager);
		helper.assertTrue(menu[0].clickMenuButton(player, HallJobs.OPEN + slot), "Job ▾ wasn't taken");
		int none = HallJobs.PICK + BuiltInRegistries.VILLAGER_PROFESSION.getId(VillagerProfession.NONE);
		helper.assertTrue(menu[0].clickMenuButton(player, none) && menu[0].clickMenuButton(player, none), "No job wasn't taken");
	}

	/** The Hall's People page, opened as a player does, and Tomas's Bed ▾ list. */
	private static List<ItemStack> bedList(GameTestHelper helper, ServerPlayer player, ChoiceMenu[] out, Villager villager) {
		int slot = openPeople(helper, player, out, villager);
		helper.assertTrue(out[0].clickMenuButton(player, HallBeds.OPEN + slot), "Bed ▾ wasn't taken");
		return HallBeds.lines(out[0].icon(slot));
	}

	/**
	 * Picks the bed in the open on Tomas's list: the free line whose place reads as the Hall words it ("free · 26 blocks
	 * south-west"; beds left by other tests' areas may be listed too, so it's found by its words, which must be one line's).
	 */
	static void pickTheFreeBed(GameTestHelper helper, ServerPlayer player, Villager tomas) {
		ChoiceMenu[] menu = new ChoiceMenu[1];
		List<ItemStack> lines = bedList(helper, player, menu, tomas);
		String where = VillageHallScreen.where(helper.absolutePos(HALL), helper.absolutePos(OTHER_BED)).getString();
		List<ItemStack> ours = new ArrayList<>();
		for (ItemStack line : lines) {
			if (HallBeds.State.of(HallBeds.mark(line).getString("state")) == HallBeds.State.FREE && line.getHoverName().getString().endsWith(where)) {
				ours.add(line);
			}
		}
		helper.assertTrue(ours.size() == 1, "Bed ▾ has " + ours.size() + " free lines for the bed in the open (" + where + "): "
			+ lines.stream().map(l -> l.getHoverName().getString() + " (" + HallBeds.mark(l).getString("state") + ")").toList());
		helper.assertTrue(menu[0].clickMenuButton(player, HallBeds.PICK + HallBeds.mark(ours.get(0)).getInt("id")), "the pick wasn't taken");
	}

	/**
	 * The pick itself, while the house is packed: the People page lists Tomas, Bed ▾ offers the bed in the open, and the
	 * pick gives it to him at once (his home, the census's bed, held for him), the inn's bed let go.
	 */
	//$ gametest_batch AREA '"qaMoveHallBedMidMove"'
	@GameTest(template = AREA, batch = "qaMoveHallBedMidMove")
	public void aBedCanBeGivenAtTheHallWhileTheHouseIsAway(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "midmove");
		helper.runAfterDelay(2, () -> {
			Villager tomas = tomas(helper, s);
			MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			pickTheFreeBed(helper, listeningPlayer(helper, new ArrayList<>()), tomas);
			BlockPos chosen = helper.absolutePos(OTHER_BED);
			helper.assertTrue(chosen.equals(home(tomas)), "the Hall's pick didn't give Tomas the bed in the open: he sleeps at " + at(home(tomas)));
			helper.assertTrue(chosen.equals(VillageNeeds.bed(level, tomas)), "the People page shows Tomas's bed as " + at(VillageNeeds.bed(level, tomas)));
			helper.assertTrue(level.getPoiManager().getFreeTickets(chosen) == 0, "the bed the player gave Tomas is free for anyone");
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(INN_BED)) == 1, "the inn's bed wasn't let go when he moved out");
			// the move finished while Tomas is here, so he isn't left among the residents waited for (MoveSites is the level's,
			// and MoverResidentsGameTests expects nobody waiting after its own move)
			work(level, s, x -> false);
			helper.assertTrue(MoveSites.get(level).homecomings().stream().noneMatch(r -> r.id().equals(tomas.getUUID())), "Tomas is left waited for");
			helper.succeed();
		});
	}

	/** Control: with no pick at the Hall, Tomas sleeps at the inn while the house is away and in the moved bed after (45.4). */
	//$ gametest_batch AREA '"qaMoveHallBedControl"'
	@GameTest(template = AREA, batch = "qaMoveHallBedControl")
	public void withNoHallPickTheMovedBedIsHis(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "control");
		helper.runAfterDelay(2, () -> {
			Villager tomas = tomas(helper, s);
			MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(helper.absolutePos(INN_BED).equals(home(tomas)), "with his house packed Tomas sleeps at " + at(home(tomas)) + ", not the inn");
			work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos moved = s.before().at(BED.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
			helper.assertTrue(moved.equals(home(tomas)), "Tomas sleeps at " + at(home(tomas)) + ", not the moved bed " + at(moved));
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(INN_BED)) == 1, "the inn's bed wasn't given back");
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(OTHER_BED)) == 1, "the bed in the open was taken");
			helper.succeed();
		});
	}
}
