package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.Movers;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

/**
 * 45.3: the Mover. A small house the hall knows (a door, a bed, a wall torch, a chest and a barrel with items, an item
 * frame) is moved by a hired Mover from the move confirmed on the Hall's Move page ({@link PendingMoves}): taken down
 * top-down and put up turned a quarter at the new spot, block for block ({@link MaterialRules#matches}) with each
 * container's own items and nothing dropped; through a save and reload in every stage; with a block broken half way
 * (whoever broke it has it, nothing duplicated); called off half way (everything goes back up where it was); and with
 * two Movers sharing the job; with his Mover's Crate gone (nowhere to pack the building: he waits and says why, and
 * carries on when it's back); and with config {@code movers} off (he does nothing, and no jobless villager takes the
 * crate). The job itself: registered under the id the Hall asks for, at its own crate.
 */
public class MoverGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	/** The house (helper coordinates): 7 × 5 × 7, lowest corner MIN. */
	private static final BlockPos MIN = new BlockPos(2, 2, 2);
	private static final BlockPos MAX = new BlockPos(8, 6, 8);
	/** Where it goes (helper coordinates), its new lowest corner, turned a quarter. */
	private static final BlockPos TO = new BlockPos(16, 2, 16);
	private static final BlockPos DOOR = new BlockPos(5, 3, 2);
	private static final BlockPos CHEST = new BlockPos(4, 3, 4);
	private static final BlockPos BARREL = new BlockPos(6, 3, 4);
	private static final BlockPos BED_FOOT = new BlockPos(3, 3, 6);
	private static final BlockPos BED_HEAD = new BlockPos(3, 3, 7);
	private static final BlockPos TORCH = new BlockPos(7, 4, 5);
	private static final BlockPos FRAME = new BlockPos(3, 4, 4);
	private static final BlockPos HALL = new BlockPos(15, 2, 2);
	private static final int MAX_STEPS = 5000;

	private static void set(ServerLevel level, BlockPos abs, BlockState state) {
		level.setBlock(abs, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	/** One move set up: the house, the hall's record of it, the move confirmed, its plan as it stood, and a Mover. */
	private record Setup(BlueprintData.Placement placement, MovePlan before, BlockPos hall, BlockPos from, BlockPos to, Villager mover) {
	}

	private static Setup setup(GameTestHelper helper, String name) {
		Leftovers.clear(helper);
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
		set(level, helper.absolutePos(CHEST), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
		Container chest = (Container) level.getBlockEntity(helper.absolutePos(CHEST));
		chest.setItem(0, new ItemStack(Items.BREAD, 12));
		chest.setItem(26, new ItemStack(Items.EMERALD, 64));
		set(level, helper.absolutePos(BARREL), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
		Container barrel = (Container) level.getBlockEntity(helper.absolutePos(BARREL));
		barrel.setItem(3, new ItemStack(Items.COAL, 20));
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		set(level, helper.absolutePos(BED_FOOT), bed.setValue(BedBlock.PART, BedPart.FOOT));
		set(level, helper.absolutePos(BED_HEAD), bed.setValue(BedBlock.PART, BedPart.HEAD));
		set(level, helper.absolutePos(TORCH), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST));
		ItemFrame frame = new ItemFrame(level, helper.absolutePos(FRAME), Direction.EAST);
		frame.setItem(new ItemStack(Items.DIAMOND));
		level.addFreshEntity(frame);

		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "mover_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(MIN), new Vec3i(7, 5, 7), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(MIN), Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(id, placement, UUID.randomUUID());
		BuildSiteManager.Finished finished = sites.finishedAt(level, id, helper.absolutePos(MIN)).orElseThrow();
		MovePlan before = MovePlan.capture(level, finished).orElseThrow();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos from = helper.absolutePos(MIN);
		BlockPos to = helper.absolutePos(TO);
		PendingMoves.get(level).put(new PendingMoves.Move(hall, from, id, to, Rotation.CLOCKWISE_90, null));
		Leftovers.after(helper, () -> {
			MoveSites.get(level).cancel(level, from);
			MoveSites.get(level).all().stream().filter(s -> s.from().equals(from)).forEach(s -> {
				while (s.stage() != MoveSite.Stage.DONE) {
					MoveSites.get(level).step(level, s);
				}
			});
			PendingMoves.get(level).remove(from);
			sites.forgetFinished(placement);
			sites.finishedIn(level).stream().filter(f -> f.structure().equals(id)).forEach(f -> sites.forgetFinished(f.placement()));
		});
		return new Setup(placement, before, hall, from, to, mover(helper, new BlockPos(12, 2, 12)));
	}

	/** A Mover at {@code at}, hired at his own Mover's Crate just north of him (where he packs what he takes down). */
	private static Villager mover(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at.north(), ModBlocks.MOVERS_CRATE);
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.MOVER));
		villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE,
			net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(at.north())));
		return villager;
	}

	/** Lets the Mover work until {@code stop} says so or the move is over; the site worked on. */
	private static MoveSite work(ServerLevel level, Setup s, java.util.function.Predicate<MoveSite> stop) {
		MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
		for (int i = 0; i < MAX_STEPS && site.stage() != MoveSite.Stage.DONE && !stop.test(site); i++) {
			MoverWork.work(level, s.mover(), site);
		}
		return site;
	}

	/** A reload: the saved sites written and read back in place of the live ones; the site picked up again. */
	private static MoveSite reload(ServerLevel level, Setup s) {
		CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
		return MoveSites.get(level).forBuilding(s.from()).orElseThrow();
	}

	/** What's wrong with the building standing at {@code min} turned by {@code rotation} (null: it's all there). */
	private static String standing(ServerLevel level, MovePlan plan, BlockPos min, Rotation rotation, List<BlockPos> missing) {
		int wrong = 0;
		String first = null;
		for (MovePlan.Placed p : plan.placed(min, rotation)) {
			if (missing.contains(p.pos())) {
				if (!level.getBlockState(p.pos()).isAir()) {
					return "the broken block at " + p.pos() + " was put up again: " + level.getBlockState(p.pos());
				}
				continue;
			}
			if (!MaterialRules.matches(level.getBlockState(p.pos()), p.state())) {
				wrong++;
				if (first == null) {
					first = p.pos() + " is " + level.getBlockState(p.pos()) + ", not " + p.state();
				}
			}
		}
		return wrong == 0 ? null : wrong + " blocks wrong, first " + first;
	}

	private static String items(ServerLevel level, BlockPos abs) {
		if (!(level.getBlockEntity(abs) instanceof Container c)) {
			return "no container at " + abs + " (" + level.getBlockState(abs) + ")";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack stack = c.getItem(i);
			if (!stack.isEmpty()) {
				out.append(i).append('=').append(stack.getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(stack.getItem())).append(';');
			}
		}
		return out.toString();
	}

	private static BlockPos turned(Setup s, BlockPos helperPos, ServerLevel level, GameTestHelper helper) {
		return s.before().at(helperPos.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
	}

	/** The checks after a whole move: block for block at the new spot, the containers' own items, the old spot empty. */
	private static void assertMoved(GameTestHelper helper, ServerLevel level, Setup s, List<BlockPos> missing) {
		String wrong = standing(level, s.before(), s.to(), Rotation.CLOCKWISE_90, missing);
		helper.assertTrue(wrong == null, "the house at its new spot: " + wrong);
		String chest = items(level, turned(s, CHEST, level, helper));
		helper.assertTrue(chest.equals("0=12 minecraft:bread;26=64 minecraft:emerald;"), "the chest moved with " + chest);
		String barrel = items(level, turned(s, BARREL, level, helper));
		helper.assertTrue(barrel.equals("3=20 minecraft:coal;"), "the barrel moved with " + barrel);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(MIN), helper.absolutePos(MAX))) {
			if (!helper.getLevel().getBlockState(pos).isAir()) {
				helper.fail("the old spot still has " + level.getBlockState(pos) + " at " + pos);
			}
		}
		AABB area = new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(30);
		List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, area);
		helper.assertTrue(frames.size() == 1 && frames.get(0).getItem().is(Items.DIAMOND)
			&& frames.get(0).blockPosition().distSqr(turned(s, FRAME, level, helper)) <= 2, "the item frame: " + frames);
		helper.assertTrue(PendingMoves.get(level).forBuilding(s.from()).isEmpty(), "the move still waits");
		helper.assertTrue(MoveSites.get(level).forBuilding(s.from()).isEmpty(), "the site wasn't put away");
		Optional<BuildSiteManager.Finished> record = BuildSiteManager.get(level).finishedIn(level).stream()
			.filter(f -> f.structure().equals(s.before().structure())).findFirst();
		helper.assertTrue(record.isPresent() && record.get().placement().rotation() == Rotation.CLOCKWISE_90
			&& MovePlan.box(level, record.get()).isInside(s.to()) && !MovePlan.box(level, record.get()).isInside(s.from()),
			"the hall's record didn't follow the building: " + record);
	}

	private static int loose(ServerLevel level, GameTestHelper helper) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(30)).size();
	}

	/** The job: its id is the one the Hall asks for, its crate is its workstation, in the creative tab and craftable. */
	//$ gametest_batch '"aliveworkplace_test:build_area"' '"moverJob"'
	@GameTest(template = "aliveworkplace_test:build_area", batch = "moverJob")
	public void theMoverIsAJobAtItsOwnCrate(GameTestHelper helper) {
		helper.assertTrue(Movers.PROFESSION.equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(ModVillagers.MOVER)), "the Mover's id");
		helper.assertTrue(ModVillagers.MOVER.acquirableJobSite().test(BuiltInRegistries.POINT_OF_INTEREST_TYPE.wrapAsHolder(ModVillagers.MOVERS_CRATE_POI_TYPE)),
			"a jobless villager doesn't take the Mover's Crate");
		helper.assertTrue(ModVillagers.isWorker(ModVillagers.MOVER), "the Mover doesn't get the workers' shift");
		helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "movers_crate")).isPresent(),
			"no recipe for the Mover's Crate");
		Villager villager = mover(helper, new BlockPos(2, 2, 2));
		helper.assertTrue(Movers.isMover.test(villager), "a villager with the job isn't a Mover to the Hall");
		helper.assertTrue(new ItemStack(ModBlocks.MOVERS_CRATE).getHoverName().getString().equals("Mover's Crate"), "the crate's name");
		String done = net.minecraft.network.chat.Component.translatable("message.aliveworkplace.move.done", "Starter Cottage", 212).getString();
		helper.assertTrue(done.equals("The Starter Cottage stands at its new spot: 212 blocks moved"), done);
		String back = net.minecraft.network.chat.Component.translatable("message.aliveworkplace.move.put_back", "Starter Cottage").getString();
		helper.assertTrue(back.equals("The Starter Cottage is back where it was: the move is called off"), back);
		String job = net.minecraft.network.chat.Component.translatable("entity.minecraft.villager.mover").getString();
		helper.assertTrue(job.equals("Mover"), job);
		helper.succeed();
	}

	/** A whole move: taken down, put up turned a quarter, block for block, every container with its own items, nothing dropped. */
	//$ gametest_batch AREA '"moverWhole"'
	@GameTest(template = AREA, batch = "moverWhole")
	public void aMoverMovesAWholeBuilding(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "whole");
		helper.runAfterDelay(2, () -> {
			MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the building didn't come down: " + site.stage());
			helper.assertTrue(site.packed() == s.before().blocks().size(), site.packed() + " packed of " + s.before().blocks().size());
			helper.assertTrue(site.packedContainers().size() == 2, "the chest and the barrel are packed apart: " + site.packedContainers().size());
			helper.assertTrue(loose(level, helper) == 0, "something was dropped while it came down");
			var status = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(s.mover(), level.getGameTime());
			helper.assertTrue(status != null && status.title().getString().equals("Moving a building")
				&& status.line().getString().equals("Putting it up: 0 of " + s.before().blocks().size() + " blocks"),
				"the Mover's line: " + (status == null ? "none" : status.title().getString() + " / " + status.line().getString()));
			work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.skipped() == 0, "the move ended " + site.stage() + ", skipped " + site.skipped());
			assertMoved(helper, level, s, List.of());
			helper.assertTrue(loose(level, helper) == 0, "something was dropped");
			helper.succeed();
		});
	}

	/** Saved and reloaded in every stage (twice in each): the move carries on and ends the same, nothing twice. */
	//$ gametest_batch AREA '"moverReload"'
	@GameTest(template = AREA, batch = "moverReload")
	public void aMoveCarriesOnAfterAReloadInEachStage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "reload");
		helper.runAfterDelay(2, () -> {
			MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
			int steps = 0;
			java.util.Set<MoveSite.Stage> reloadedIn = java.util.EnumSet.noneOf(MoveSite.Stage.class);
			while (site.stage() != MoveSite.Stage.DONE && steps++ < MAX_STEPS) {
				MoveSite.Stage stage = site.stage();
				MoverWork.work(level, s.mover(), site);
				if (steps % 7 == 0 && site.stage() != MoveSite.Stage.DONE) {
					reloadedIn.add(site.stage());
					site = reload(level, s);
				}
			}
			helper.assertTrue(reloadedIn.containsAll(List.of(MoveSite.Stage.DISMANTLE, MoveSite.Stage.FOUNDATION, MoveSite.Stage.STRUCTURE,
				MoveSite.Stage.DECORATION)), "reloaded only in " + reloadedIn);
			assertMoved(helper, level, s, List.of());
			helper.assertTrue(loose(level, helper) == 0, "something was dropped");
			helper.succeed();
		});
	}

	/** A wall block broken by a player half way through taking it down: the player has it, it isn't put up again or doubled. */
	//$ gametest_batch AREA '"moverBroken"'
	@GameTest(template = AREA, batch = "moverBroken")
	public void aBlockBrokenMidMoveIsNotPutUpTwice(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "broken");
		BlockPos wall = new BlockPos(2, 3, 5);
		helper.runAfterDelay(2, () -> {
			MoveSite site = work(level, s, x -> x.packed() >= 40);
			helper.assertTrue(level.getBlockState(helper.absolutePos(wall)).is(Blocks.OAK_PLANKS), "the wall block came down too soon");
			level.destroyBlock(helper.absolutePos(wall), true);
			work(level, s, x -> false);
			helper.assertTrue(site.skipped() == 1, "skipped " + site.skipped());
			assertMoved(helper, level, s, List.of(turned(s, wall, level, helper)));
			helper.assertTrue(loose(level, helper) == 1, "the broken block should lie there once: " + loose(level, helper) + " items");
			helper.succeed();
		});
	}

	/** Called off on the Move page half way through putting it up: what went up comes down, everything back where it was. */
	//$ gametest_batch AREA '"moverCancel"'
	@GameTest(template = AREA, batch = "moverCancel")
	public void aMoveCalledOffHalfWayPutsEverythingBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "cancel");
		helper.runAfterDelay(2, () -> {
			MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE);
			helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE, "it didn't get half way: " + site.stage());
			PendingMoves.get(level).remove(s.from()); // the Move page's Call off
			work(level, s, x -> false);
			helper.assertTrue(site.canceled() && site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			var status = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(s.mover(), level.getGameTime());
			helper.assertTrue(status != null && status.line().getString().equals("Called off: putting it back where it was"),
				"the Mover's line: " + (status == null ? "none" : status.line().getString()));
			String wrong = standing(level, s.before(), s.from(), Rotation.NONE, List.of());
			helper.assertTrue(wrong == null, "the house back at its old spot: " + wrong);
			String chest = items(level, helper.absolutePos(CHEST));
			helper.assertTrue(chest.equals("0=12 minecraft:bread;26=64 minecraft:emerald;"), "the chest is back with " + chest);
			for (MovePlan.Placed p : s.before().placed(s.to(), Rotation.CLOCKWISE_90)) {
				helper.assertTrue(level.getBlockState(p.pos()).isAir(), "the new spot still has " + level.getBlockState(p.pos()));
			}
			helper.assertTrue(level.getEntitiesOfClass(ItemFrame.class, new AABB(helper.absolutePos(FRAME)).inflate(1)).size() == 1, "the frame isn't back");
			helper.assertTrue(BuildSiteManager.get(level).finishedAt(level, s.before().structure(), s.from()).isPresent(), "the hall forgot the building");
			helper.assertTrue(MoveSites.get(level).forBuilding(s.from()).isEmpty(), "the site wasn't put away");
			helper.assertTrue(loose(level, helper) == 0, "something was dropped");
			helper.succeed();
		});
	}

	/** Two Movers of a village share one move: the same site, each swing a step, done in half the swings each. */
	//$ gametest_batch AREA '"moverTwo"'
	@GameTest(template = AREA, batch = "moverTwo")
	public void twoMoversShareAMove(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "two");
		Villager second = mover(helper, new BlockPos(13, 2, 12));
		helper.runAfterDelay(2, () -> {
			MoveSite a = MoveSites.get(level).work(level, s.hall()).orElseThrow();
			MoveSite b = MoveSites.get(level).work(level, s.hall()).orElseThrow();
			helper.assertTrue(a == b, "the two Movers work on different sites");
			int swings = 0;
			while (a.stage() != MoveSite.Stage.DONE && swings < MAX_STEPS) {
				MoverWork.work(level, s.mover(), a);
				MoverWork.work(level, second, a);
				swings++;
			}
			helper.assertTrue(MoveSites.get(level).all().stream().noneMatch(x -> x.hall().equals(s.hall())), "a second site started");
			helper.assertTrue(swings <= s.before().blocks().size() + 10, "each Mover swung " + swings + " times for " + s.before().blocks().size() + " blocks");
			assertMoved(helper, level, s, List.of());
			helper.succeed();
		});
	}

	/**
	 * The "full storage" case: what he takes down goes into his own crates, so with his Mover's Crate broken half way
	 * through taking the house down he has nowhere to pack it. He waits (nothing more comes down, nothing is dropped) and
	 * his line says why; with a crate back he carries on and the move ends whole.
	 */
	//$ gametest_batch AREA '"moverNoCrate"'
	@GameTest(template = AREA, batch = "moverNoCrate")
	public void aMoverWithoutHisCrateWaitsAndSaysWhy(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "nocrate");
		BlockPos crate = new BlockPos(12, 2, 11);
		helper.runAfterDelay(2, () -> {
			MoveSite site = work(level, s, x -> x.packed() >= 30);
			int packed = site.packed();
			helper.assertTrue(site.stage() == MoveSite.Stage.DISMANTLE && packed >= 30, "it didn't get half way down: " + site.stage() + ", " + packed);
			helper.setBlock(crate, Blocks.AIR); // his crate is gone
			for (int i = 0; i < 50; i++) {
				helper.assertFalse(MoverWork.work(level, s.mover(), site), "he took a block down with no crate to pack it in");
			}
			helper.assertTrue(site.packed() == packed && site.stage() == MoveSite.Stage.DISMANTLE, "with no crate: " + site.packed() + " packed, " + site.stage());
			helper.assertTrue(MoverWork.NO_CRATE.equals(site.waiting()), "he doesn't wait: " + site.waiting());
			var status = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(s.mover(), level.getGameTime());
			helper.assertTrue(status != null && status.line().getString().equals("Waiting for his Mover's Crate: nowhere to pack the building"),
				"the Mover's line: " + (status == null ? "none" : status.line().getString()));
			helper.assertTrue(loose(level, helper) == 0, "something was dropped while he waited");
			helper.setBlock(crate, ModBlocks.MOVERS_CRATE); // a crate again
			work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.waiting() == null, "the move ended " + site.stage() + ", waiting " + site.waiting());
			assertMoved(helper, level, s, List.of());
			helper.assertTrue(loose(level, helper) == 0, "something was dropped");
			helper.succeed();
		});
	}

	/** Config {@code movers} off: a Mover does nothing (the house stands, the move waits) and a jobless villager doesn't take the crate. */
	//$ gametest_batch AREA '"moverOff"'
	@GameTest(template = AREA, batch = "moverOff")
	public void withMoversOffAMoverDoesNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "off");
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager jobless = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 12));
		jobless.setNoAi(true);
		helper.runAfterDelay(2, () -> {
			BlockPos crate = helper.absolutePos(new BlockPos(12, 2, 11));
			var poi = level.getPoiManager().getType(crate).orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("no POI at the crate"));
			java.util.function.Supplier<Boolean> seen = () -> {
				Villager before = io.github.jcondedata.aliveworkplace.work.WorkerLimits.thinker();
				io.github.jcondedata.aliveworkplace.work.WorkerLimits.thinking(jobless);
				try {
					return io.github.jcondedata.aliveworkplace.work.WorkerLimits.hideFull(java.util.stream.Stream.of(com.mojang.datafixers.util.Pair.of(poi, crate))).findAny().isPresent();
				} finally {
					io.github.jcondedata.aliveworkplace.work.WorkerLimits.thinking(before);
				}
			};
			helper.assertTrue(MoverWork.site(level, s.mover()).isPresent(), "with movers on the Mover has no move to work on (setup)");
			helper.assertTrue(seen.get(), "with movers on a jobless villager doesn't see the crate (setup)");
			boolean was = MoverWork.ENABLED;
			MoverWork.ENABLED = false;
			try {
				helper.assertTrue(MoverWork.site(level, s.mover()).isEmpty(), "movers off: the Mover still has a move to work on");
				helper.assertFalse(seen.get(), "movers off: a jobless villager still sees the Mover's Crate as a workstation");
				MoveSite site = MoveSites.get(level).forBuilding(s.from()).orElseThrow();
				for (int i = 0; i < 100; i++) {
					helper.assertFalse(MoverWork.work(level, s.mover(), site), "movers off: he worked");
				}
				helper.assertTrue(site.packed() == 0 && site.stage() == MoveSite.Stage.DISMANTLE, "movers off: " + site.packed() + " packed, " + site.stage());
			} finally {
				MoverWork.ENABLED = was;
			}
			String wrong = standing(level, s.before(), s.from(), Rotation.NONE, List.of());
			helper.assertTrue(wrong == null, "movers off, the house: " + wrong);
			helper.assertTrue(PendingMoves.get(level).forBuilding(s.from()).isPresent(), "movers off, the move doesn't wait");
			helper.assertTrue(text("aliveworkplace.config.movers").equals("Movers"), "the switch's name");
			helper.assertTrue(text("aliveworkplace.config.movers.tooltip").contains("Off: no new Movers, those hired stand idle, and moves wait."),
				"the switch's tooltip: " + text("aliveworkplace.config.movers.tooltip"));
			helper.succeed();
		});
	}

	private static String text(String key) {
		return net.minecraft.network.chat.Component.translatable(key).getString();
	}
}
