package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

/**
 * 45.5: a moved building's chests, furniture and decorations arrive as they were. A small house with every vanilla
 * container kind (a chest, a double chest, a barrel, a shulker box, each with its own items, a named sword among them), a
 * sign with text on both sides, a banner, an item frame inside and one hanging on its outer wall, and an armor stand
 * wearing a helmet, moved by a Mover a quarter turn away: everything arrives slot for slot and nothing is doubled
 * (every item in the test area counted before and after, none loose). What the village changed after the move started
 * (an item taken off the frame and the stand, one put in the chest) is what moves, not the plan's older picture; the
 * move carries its contents through a save and reload in the middle of taking it down and of putting it up; and a
 * piece of a furniture mod no longer installed when a saved move is read (its id reads as air) is left out while the
 * rest moves (the furniture mods' absence; the compat suite moves their pieces).
 */
public class MoverBelongingsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos MIN = new BlockPos(2, 2, 2);
	private static final BlockPos MAX = new BlockPos(8, 6, 8);
	private static final BlockPos TO = new BlockPos(16, 2, 16);
	private static final BlockPos HALL = new BlockPos(15, 2, 2);
	private static final BlockPos CHEST = new BlockPos(3, 3, 4);
	private static final BlockPos DOUBLE_RIGHT = new BlockPos(4, 3, 4);
	private static final BlockPos DOUBLE_LEFT = new BlockPos(5, 3, 4);
	private static final BlockPos BARREL = new BlockPos(6, 3, 4);
	private static final BlockPos SHULKER = new BlockPos(7, 3, 5);
	private static final BlockPos SIGN = new BlockPos(4, 3, 6);
	private static final BlockPos BANNER = new BlockPos(5, 3, 7);
	private static final BlockPos FRAME = new BlockPos(3, 4, 5);
	/** A frame hanging on the house's north wall from outside (in front of it, just out of its box). */
	private static final BlockPos OUTSIDE_FRAME = new BlockPos(5, 4, 1);
	private static final BlockPos STAND = new BlockPos(6, 3, 6);
	/** A piece of furniture (a fence standing in for it) whose mod is gone when the saved move is read. */
	private static final BlockPos GONE = new BlockPos(7, 3, 7);
	private static final int MAX_STEPS = 5000;

	private record Setup(MovePlan before, BlockPos hall, BlockPos from, BlockPos to, Villager mover) {
	}

	private static void set(ServerLevel level, BlockPos abs, BlockState state) {
		level.setBlock(abs, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private static Container container(GameTestHelper helper, BlockPos at, BlockState state) {
		set(helper.getLevel(), helper.absolutePos(at), state);
		return (Container) helper.getLevel().getBlockEntity(helper.absolutePos(at));
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
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
		container(helper, CHEST, chest).setItem(0, new ItemStack(Items.BREAD, 12));
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal("Grandpa's sword"));
		container(helper, CHEST, chest).setItem(13, sword);
		container(helper, DOUBLE_RIGHT, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT)).setItem(5, new ItemStack(Items.OAK_LOG, 30));
		container(helper, DOUBLE_LEFT, chest.setValue(ChestBlock.TYPE, ChestType.LEFT)).setItem(20, new ItemStack(Items.IRON_INGOT, 7));
		container(helper, BARREL, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP)).setItem(3, new ItemStack(Items.COAL, 20));
		container(helper, SHULKER, Blocks.BLUE_SHULKER_BOX.defaultBlockState()).setItem(26, new ItemStack(Items.EMERALD, 64));
		set(level, helper.absolutePos(SIGN), Blocks.OAK_SIGN.defaultBlockState());
		SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(helper.absolutePos(SIGN));
		sign.setText(new SignText().setMessage(0, Component.literal("Welcome")).setMessage(1, Component.literal("to Thornholm")), true);
		sign.setText(new SignText().setMessage(0, Component.literal("Mind the step")), false);
		set(level, helper.absolutePos(BANNER), Blocks.RED_BANNER.defaultBlockState());
		set(level, helper.absolutePos(GONE), Blocks.OAK_FENCE.defaultBlockState());
		ItemFrame frame = new ItemFrame(level, helper.absolutePos(FRAME), Direction.EAST);
		frame.setItem(new ItemStack(Items.DIAMOND));
		level.addFreshEntity(frame);
		ItemFrame outside = new ItemFrame(level, helper.absolutePos(OUTSIDE_FRAME), Direction.NORTH);
		outside.setItem(new ItemStack(Items.CLOCK));
		level.addFreshEntity(outside);
		ArmorStand stand = new ArmorStand(level, helper.absolutePos(STAND).getX() + 0.5, helper.absolutePos(STAND).getY(), helper.absolutePos(STAND).getZ() + 0.5);
		stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		level.addFreshEntity(stand);

		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "belongings_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(MIN), new Vec3i(7, 5, 7), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(MIN), Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(id, placement, UUID.randomUUID());
		BuildSiteManager.Finished finished = sites.finishedAt(level, id, helper.absolutePos(MIN)).orElseThrow();
		MovePlan before = MovePlan.capture(level, finished).orElseThrow();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos from = helper.absolutePos(MIN);
		PendingMoves.get(level).put(new PendingMoves.Move(hall, from, id, helper.absolutePos(TO), Rotation.CLOCKWISE_90, null));
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
		BlockPos at = new BlockPos(12, 2, 12);
		helper.setBlock(at.north(), ModBlocks.MOVERS_CRATE);
		Villager mover = helper.spawn(EntityType.VILLAGER, at);
		mover.setNoAi(true);
		mover.setVillagerData(mover.getVillagerData().setProfession(ModVillagers.MOVER));
		mover.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE,
			net.minecraft.core.GlobalPos.of(level.dimension(), helper.absolutePos(at.north())));
		return new Setup(before, hall, from, helper.absolutePos(TO), mover);
	}

	private static MoveSite work(ServerLevel level, Setup s, java.util.function.Predicate<MoveSite> stop) {
		MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
		for (int i = 0; i < MAX_STEPS && site.stage() != MoveSite.Stage.DONE && !stop.test(site); i++) {
			MoverWork.work(level, s.mover(), site);
		}
		return site;
	}

	private static MoveSite reload(ServerLevel level, Setup s) {
		CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
		return MoveSites.get(level).forBuilding(s.from()).orElseThrow();
	}

	private static BlockPos turned(Setup s, BlockPos helperPos) {
		return s.before().at(helperPos.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
	}

	private static AABB area(GameTestHelper helper) {
		return new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(30);
	}

	private static String items(ServerLevel level, BlockPos abs) {
		if (!(level.getBlockEntity(abs) instanceof Container c)) {
			return "no container at " + abs + " (" + level.getBlockState(abs) + ")";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack stack = c.getItem(i);
			if (!stack.isEmpty()) {
				out.append(i).append('=').append(stack.getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(stack.getItem()));
				if (stack.has(DataComponents.CUSTOM_NAME)) {
					out.append(" \"").append(stack.getHoverName().getString()).append('"');
				}
				out.append(';');
			}
		}
		return out.toString();
	}

	/** Every item in the test area: in containers, on frames and stands, and loose; by item, so a doubling shows. */
	private static java.util.Map<String, Integer> census(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		java.util.Map<String, Integer> out = new java.util.TreeMap<>();
		java.util.function.Consumer<ItemStack> add = stack -> {
			if (!stack.isEmpty()) {
				out.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
			}
		};
		BlockPos lo = helper.absolutePos(new BlockPos(0, 1, 0));
		for (BlockPos pos : BlockPos.betweenClosed(lo, lo.offset(29, 20, 29))) {
			if (level.getBlockEntity(pos) instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					add.accept(c.getItem(i));
				}
			}
		}
		for (Entity e : level.getEntities((Entity) null, area(helper), e -> true)) {
			if (e instanceof ItemFrame f) {
				add.accept(f.getItem());
			} else if (e instanceof ArmorStand a) {
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					add.accept(a.getItemBySlot(slot));
				}
			} else if (e instanceof ItemEntity item) {
				add.accept(item.getItem());
			}
		}
		return out;
	}

	private static int loose(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper)).size();
	}

	/** What's wrong with the building's blocks at the new spot (null: all there), leaving out {@code missing} (helper positions). */
	private static String standing(ServerLevel level, Setup s, List<BlockPos> missing) {
		List<BlockPos> gone = missing.stream().map(p -> turned(s, p)).toList();
		for (MovePlan.Placed p : s.before().placed(s.to(), Rotation.CLOCKWISE_90)) {
			if (gone.contains(p.pos())) {
				if (!level.getBlockState(p.pos()).isAir()) {
					return "a left-out block was put up at " + p.pos() + ": " + level.getBlockState(p.pos());
				}
				continue;
			}
			if (!MaterialRules.matches(level.getBlockState(p.pos()), p.state())) {
				return p.pos() + " is " + level.getBlockState(p.pos()) + ", not " + p.state();
			}
		}
		return null;
	}

	/**
	 * The checks after a whole move: block for block, each container slot for slot, the sign's text, the frames, the stand,
	 * nothing loose and every item in the area counted the same as before.
	 */
	private static void assertArrived(GameTestHelper helper, Setup s, String chest, String frameItem, boolean helmet,
									  java.util.Map<String, Integer> census, List<BlockPos> missing) {
		ServerLevel level = helper.getLevel();
		String wrong = standing(level, s, missing);
		helper.assertTrue(wrong == null, "the house at its new spot: " + wrong);
		helper.assertValueEqual(items(level, turned(s, CHEST)), chest, "the chest");
		helper.assertValueEqual(items(level, turned(s, DOUBLE_RIGHT)), "5=30 minecraft:oak_log;", "the double chest's right half");
		helper.assertValueEqual(items(level, turned(s, DOUBLE_LEFT)), "20=7 minecraft:iron_ingot;", "the double chest's left half");
		helper.assertValueEqual(items(level, turned(s, BARREL)), "3=20 minecraft:coal;", "the barrel");
		helper.assertValueEqual(items(level, turned(s, SHULKER)), "26=64 minecraft:emerald;", "the shulker box");
		helper.assertTrue(level.getBlockEntity(turned(s, SIGN)) instanceof SignBlockEntity, "no sign at " + turned(s, SIGN));
		SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(turned(s, SIGN));
		helper.assertValueEqual(sign.getFrontText().getMessage(0, false).getString() + "/" + sign.getFrontText().getMessage(1, false).getString(),
			"Welcome/to Thornholm", "the sign's front");
		helper.assertValueEqual(sign.getBackText().getMessage(0, false).getString(), "Mind the step", "the sign's back");
		helper.assertTrue(level.getBlockState(turned(s, BANNER)).is(Blocks.RED_BANNER), "the banner: " + level.getBlockState(turned(s, BANNER)));
		List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, area(helper));
		helper.assertTrue(frames.size() == 2, "two frames, not " + frames);
		ItemFrame inside = frames.stream().filter(f -> f.blockPosition().distSqr(turned(s, FRAME)) <= 2).findFirst().orElse(null);
		helper.assertTrue(inside != null && (frameItem == null ? inside.getItem().isEmpty() : BuiltInRegistries.ITEM.getKey(inside.getItem().getItem()).getPath().equals(frameItem)),
			"the frame inside: " + frames);
		// the outer wall's frame: on the turned north wall's outside (east, a quarter turn), still with its clock
		ItemFrame outside = frames.stream().filter(f -> f != inside).findFirst().orElseThrow();
		helper.assertTrue(outside.getItem().is(Items.CLOCK) && outside.getDirection() == Direction.EAST
			&& outside.blockPosition().distSqr(turned(s, OUTSIDE_FRAME)) <= 2, "the frame on the outer wall: " + outside + " facing " + outside.getDirection());
		List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, area(helper));
		helper.assertTrue(stands.size() == 1 && stands.get(0).blockPosition().distSqr(turned(s, STAND)) <= 2
			&& stands.get(0).getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) == helmet, "the armor stand: " + stands);
		helper.assertTrue(loose(helper) == 0, "items dropped: " + loose(helper));
		helper.assertValueEqual(census(helper), census, "every item in the area");
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(MIN), helper.absolutePos(MAX))) {
			if (!helper.getLevel().getBlockState(pos).isAir()) {
				helper.fail("the old spot still has " + level.getBlockState(pos) + " at " + pos);
			}
		}
	}

	private static final String CHEST_ITEMS = "0=12 minecraft:bread;13=1 minecraft:diamond_sword \"Grandpa's sword\";";

	/** Every container kind, the sign, the banner, both frames and the stand arrive as they were; nothing doubled. */
	//$ gametest_batch AREA '"moverBelongings"'
	@GameTest(template = AREA, batch = "moverBelongings")
	public void everyContainerAndDecorationArrivesAsItWas(GameTestHelper helper) {
		Setup s = setup(helper, "all");
		java.util.Map<String, Integer> census = census(helper);
		helper.assertTrue(census.getOrDefault("minecraft:clock", 0) == 1 && s.before().things().size() == 3,
			"the plan's things (the frame on the outer wall too): " + s.before().things().size());
		MoveSite site = work(helper.getLevel(), s, x -> false);
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move didn't end: " + site.stage());
		assertArrived(helper, s, CHEST_ITEMS, "diamond", true, census, List.of());
		helper.succeed();
	}

	/**
	 * What the village changed after the move started is what moves: the diamond taken off the frame and the helmet off
	 * the stand don't come back (the plan's older picture had them), and the apple put in the chest arrives.
	 */
	//$ gametest_batch AREA '"moverBelongingsChanged"'
	@GameTest(template = AREA, batch = "moverBelongingsChanged")
	public void whatChangedSinceThePlanMovesNotThePlansPicture(GameTestHelper helper) {
		Setup s = setup(helper, "changed");
		ServerLevel level = helper.getLevel();
		MoveSites.get(level).work(level, s.hall()).orElseThrow(); // the site starts: the plan is captured now
		List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, area(helper), f -> f.getItem().is(Items.DIAMOND));
		helper.assertTrue(frames.size() == 1, "the frame with the diamond: " + frames);
		frames.get(0).setItem(ItemStack.EMPTY); // a player takes the diamond (it's theirs now: out of the area)
		ArmorStand stand = level.getEntitiesOfClass(ArmorStand.class, area(helper)).get(0);
		stand.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); // and the helmet
		((Container) level.getBlockEntity(helper.absolutePos(CHEST))).setItem(1, new ItemStack(Items.APPLE, 3));
		java.util.Map<String, Integer> census = census(helper);
		MoveSite site = work(level, s, x -> false);
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move didn't end: " + site.stage());
		assertArrived(helper, s, "0=12 minecraft:bread;1=3 minecraft:apple;13=1 minecraft:diamond_sword \"Grandpa's sword\";", null, false, census, List.of());
		helper.succeed();
	}

	/**
	 * Saved and reloaded half way down (the chests packed, the things too) and half way up: everything arrives once, from
	 * the saved record; the packed things are in the save.
	 */
	//$ gametest_batch AREA '"moverBelongingsReload"'
	@GameTest(template = AREA, batch = "moverBelongingsReload")
	public void contentsSurviveASaveAndReloadMidMove(GameTestHelper helper) {
		Setup s = setup(helper, "reload");
		ServerLevel level = helper.getLevel();
		java.util.Map<String, Integer> census = census(helper);
		BlockPos chest = helper.absolutePos(CHEST);
		MoveSite site = work(level, s, x -> level.getBlockState(chest).isAir());
		helper.assertTrue(site.stage() == MoveSite.Stage.DISMANTLE && site.packedContainers().size() >= 1, "the chest isn't packed yet: " + site.stage());
		CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess()).getList("sites", Tag.TAG_COMPOUND).getCompound(0);
		helper.assertTrue(saved.getList("packed_things", Tag.TAG_COMPOUND).size() == 3, "the packed things aren't saved: " + saved.get("packed_things"));
		helper.assertTrue(level.getEntitiesOfClass(ItemFrame.class, area(helper)).isEmpty(), "the frames weren't packed");
		site = reload(level, s);
		site = work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE);
		helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE, "not putting it up: " + site.stage());
		site = reload(level, s);
		site = work(level, s, x -> false);
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move didn't end: " + site.stage());
		assertArrived(helper, s, CHEST_ITEMS, "diamond", true, census, List.of());
		helper.succeed();
	}

	/**
	 * A saved move with a piece of a furniture mod that's no longer installed (Lucky's Cozy Home's chair, say): the save
	 * still reads, the piece (gone from the world with its mod) is left out, and everything else arrives.
	 */
	//$ gametest_batch AREA '"moverBelongingsModGone"'
	@GameTest(template = AREA, batch = "moverBelongingsModGone")
	public void aPieceOfAModNoLongerInstalledIsLeftOut(GameTestHelper helper) {
		Setup s = setup(helper, "gone");
		ServerLevel level = helper.getLevel();
		java.util.Map<String, Integer> census = census(helper);
		MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
		CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
		// the fence's palette entry becomes a Cozy Home chair, which this game doesn't know, as if the mod were removed
		int renamed = 0;
		for (Tag t : saved.getList("sites", Tag.TAG_COMPOUND)) {
			ListTag palette = ((CompoundTag) t).getCompound("plan").getList("palette", Tag.TAG_COMPOUND);
			for (Tag p : palette) {
				if (((CompoundTag) p).getString("Name").equals("minecraft:oak_fence")) {
					((CompoundTag) p).putString("Name", "cozyhome:oak_chair");
					((CompoundTag) p).remove("Properties");
					renamed++;
				}
			}
		}
		helper.assertTrue(renamed == 1, "the fence in the saved plan: " + renamed + " (" + saved.getAllKeys() + ")");
		helper.assertTrue(!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("cozyhome:oak_chair")), "Cozy Home is installed here");
		set(level, helper.absolutePos(GONE), Blocks.AIR.defaultBlockState()); // gone from the world with its mod
		level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
		site = MoveSites.get(level).forBuilding(s.from()).orElse(null);
		helper.assertTrue(site != null, "the saved move didn't read");
		site = work(level, s, x -> false);
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.skipped() == 1, "the move: " + site.stage() + ", left out " + site.skipped());
		assertArrived(helper, s, CHEST_ITEMS, "diamond", true, census, List.of(GONE));
		helper.succeed();
	}
}
