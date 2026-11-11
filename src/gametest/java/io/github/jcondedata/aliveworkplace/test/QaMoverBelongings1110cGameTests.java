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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-11-10, run 1110c): 45.5 "Chests, furniture and decorations arrive as they were", written from the spec.
 * Containers come back with exactly their items (a player's chest included, nothing copied or doubled), banners, signs,
 * item frames and armor stands too; through a turned move, a move called off half way, and a save and reload while
 * everything is packed. Every vanilla block that holds things (trapped chest, hopper, dispenser, dropper, furnace,
 * brewing stand, chiseled bookshelf, decorated pot, jukebox, lectern, a named shulker box), stacks whose components must
 * survive (names, enchantments, damage, a book's pages, a shulker box item's contents), a double chest that must stay one
 * double chest, both sides of dyed, glowing and waxed signs (standing, on a wall, hanging), and frames, a glow frame, a
 * posed armor stand, a painting, a patterned banner and a potted flower turned with the building.
 */
public class QaMoverBelongings1110cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos MIN = new BlockPos(2, 2, 2);
	private static final BlockPos MAX = new BlockPos(8, 6, 8);
	private static final BlockPos TO = new BlockPos(16, 2, 16);
	private static final BlockPos HALL = new BlockPos(15, 2, 2);
	private static final int MAX_STEPS = 6000;

	// every container kind (test 1, and the census tests)
	private static final BlockPos TRAPPED = new BlockPos(3, 3, 3);
	private static final BlockPos HOPPER = new BlockPos(4, 3, 3);
	private static final BlockPos DISPENSER = new BlockPos(5, 3, 3);
	private static final BlockPos DROPPER = new BlockPos(6, 3, 3);
	private static final BlockPos FURNACE = new BlockPos(7, 3, 3);
	private static final BlockPos BREWING = new BlockPos(7, 3, 5);
	private static final BlockPos BOOKSHELF = new BlockPos(7, 3, 6);
	private static final BlockPos POT = new BlockPos(7, 3, 7);
	private static final BlockPos JUKEBOX = new BlockPos(6, 3, 7);
	private static final BlockPos LECTERN = new BlockPos(5, 3, 7);
	private static final BlockPos SHULKER = new BlockPos(4, 3, 7);

	// decorations (test 5, and the census tests)
	private static final BlockPos FRAME = new BlockPos(3, 4, 4); // on the west wall's inside, facing east
	private static final BlockPos GLOW_FRAME = new BlockPos(3, 4, 6);
	private static final BlockPos PAINTING = new BlockPos(5, 4, 3); // on the north wall's inside, facing south
	private static final BlockPos STAND = new BlockPos(5, 3, 5);
	private static final BlockPos BANNER = new BlockPos(3, 3, 5);
	private static final BlockPos FLOWER_POT = new BlockPos(3, 3, 6);

	private record Setup(MovePlan before, BlockPos hall, BlockPos from, BlockPos to, Rotation rotation, Villager mover) {
	}

	private static void set(ServerLevel level, BlockPos abs, BlockState state) {
		level.setBlock(abs, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private static Container container(GameTestHelper helper, BlockPos at, BlockState state) {
		set(helper.getLevel(), helper.absolutePos(at), state);
		return (Container) helper.getLevel().getBlockEntity(helper.absolutePos(at));
	}

	/**
	 * The small house of MoverBelongingsGameTests (cobblestone floor, plank walls, spruce roof, 7x5x7 at MIN), furnished by
	 * {@code furnish}, recorded as a finished building and asked to move to TO turned by {@code rotation}. With
	 * {@code furnishAfterTemplate} the building's blueprint is the bare house and the furniture is a player's, added after.
	 */
	private static Setup setup(GameTestHelper helper, String name, Rotation rotation, Consumer<GameTestHelper> furnish, boolean furnishAfterTemplate) {
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
		if (!furnishAfterTemplate) {
			furnish.accept(helper);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1110c_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(MIN), new Vec3i(7, 5, 7), false, Blocks.STRUCTURE_VOID);
		if (furnishAfterTemplate) {
			furnish.accept(helper);
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(MIN), Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(id, placement, UUID.randomUUID());
		BuildSiteManager.Finished finished = sites.finishedAt(level, id, helper.absolutePos(MIN)).orElseThrow();
		MovePlan before = MovePlan.capture(level, finished).orElseThrow();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos from = helper.absolutePos(MIN);
		PendingMoves.get(level).put(new PendingMoves.Move(hall, from, id, helper.absolutePos(TO), rotation, null));
		Leftovers.after(helper, () -> {
			MoveSites.get(level).cancel(level, from);
			MoveSites.get(level).all().stream().filter(s -> s.from().equals(from)).forEach(s -> {
				for (int i = 0; i < MAX_STEPS && s.stage() != MoveSite.Stage.DONE; i++) {
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
		mover.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(at.north())));
		return new Setup(before, hall, from, helper.absolutePos(TO), rotation, mover);
	}

	private static MoveSite work(ServerLevel level, Setup s, Predicate<MoveSite> stop) {
		MoveSite site = MoveSites.get(level).forBuilding(s.from()).orElseGet(() -> MoveSites.get(level).work(level, s.hall()).orElseThrow());
		for (int i = 0; i < MAX_STEPS && site.stage() != MoveSite.Stage.DONE && !stop.test(site); i++) {
			MoverWork.work(level, s.mover(), site);
		}
		return site;
	}

	/** A server restart: both saved records written and read back in place of the live ones; the site picked up again. */
	private static MoveSite restart(ServerLevel level, Setup s) {
		CompoundTag pending = PendingMoves.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(PendingMoves.NAME, PendingMoves.load(pending, level.registryAccess()));
		CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
		return MoveSites.get(level).forBuilding(s.from()).orElseThrow();
	}

	/** Where a block of the house (helper position) stands after the move. */
	private static BlockPos turned(Setup s, BlockPos helperPos) {
		return s.before().at(helperPos.subtract(MIN), s.to(), s.rotation());
	}

	private static AABB area(GameTestHelper helper) {
		return new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(30);
	}

	private static String key(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	/** A container's slots, "slot=count id;" each. */
	private static String items(ServerLevel level, BlockPos abs) {
		if (!(level.getBlockEntity(abs) instanceof Container c)) {
			return "no container at " + abs + " (" + level.getBlockState(abs) + ")";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack stack = c.getItem(i);
			if (!stack.isEmpty()) {
				out.append(i).append('=').append(stack.getCount()).append(' ').append(key(stack)).append(';');
			}
		}
		return out.toString();
	}

	/** Every item in the test area, by id: in containers, on lecterns, frames and stands, and loose. */
	private static Map<String, Integer> census(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, Integer> out = new TreeMap<>();
		Consumer<ItemStack> add = stack -> {
			if (!stack.isEmpty()) {
				out.merge(key(stack), stack.getCount(), Integer::sum);
			}
		};
		BlockPos lo = helper.absolutePos(new BlockPos(0, 1, 0));
		for (BlockPos pos : BlockPos.betweenClosed(lo, lo.offset(29, 20, 29))) {
			if (level.getBlockEntity(pos) instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					add.accept(c.getItem(i));
				}
			} else if (level.getBlockEntity(pos) instanceof LecternBlockEntity l) {
				add.accept(l.getBook());
			}
		}
		for (Entity e : level.getEntities((Entity) null, area(helper), e -> true)) {
			if (e instanceof ItemFrame f) {
				add.accept(f.getItem());
				out.merge(e instanceof GlowItemFrame ? "(glow frame)" : "(frame)", 1, Integer::sum);
			} else if (e instanceof ArmorStand a) {
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					add.accept(a.getItemBySlot(slot));
				}
				out.merge("(armor stand)", 1, Integer::sum);
			} else if (e instanceof Painting) {
				out.merge("(painting)", 1, Integer::sum);
			} else if (e instanceof ItemEntity item) {
				add.accept(item.getItem());
				out.merge("(loose)", item.getItem().getCount(), Integer::sum);
			}
		}
		return out;
	}

	private static String looseItems(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper)).stream()
			.map(e -> e.getItem().getCount() + " " + key(e.getItem())).toList().toString();
	}

	/** What's wrong with the house's blocks at {@code min} turned by {@code rotation} (null: all there as planned). */
	private static String standing(ServerLevel level, Setup s, BlockPos min, Rotation rotation) {
		int wrong = 0;
		String first = null;
		for (MovePlan.Placed p : s.before().placed(min, rotation)) {
			if (!MaterialRules.matches(level.getBlockState(p.pos()), p.state())) {
				wrong++;
				first = first != null ? first : p.pos().toShortString() + " is " + level.getBlockState(p.pos()) + ", not " + p.state();
			}
		}
		return wrong == 0 ? null : wrong + " blocks wrong, first " + first;
	}

	private static MoveSite moveAll(GameTestHelper helper, Setup s) {
		MoveSite site = work(helper.getLevel(), s, x -> false);
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE && !site.canceled(), "the move didn't end: " + site.stage() + (site.canceled() ? " canceled" : ""));
		String wrong = standing(helper.getLevel(), s, s.to(), s.rotation());
		helper.assertTrue(wrong == null, "the house at its new spot: " + wrong);
		return site;
	}

	// ---------------------------------------------------------------- furnishing

	private static final String TRAPPED_HOLDS = "0=5 minecraft:gold_ingot;26=1 minecraft:tnt;";
	private static final String HOPPER_HOLDS = "2=9 minecraft:wheat;";
	private static final String DISPENSER_HOLDS = "4=16 minecraft:arrow;";
	private static final String DROPPER_HOLDS = "0=3 minecraft:egg;8=1 minecraft:bone;";
	private static final String FURNACE_HOLDS = "0=8 minecraft:raw_iron;1=4 minecraft:coal;2=3 minecraft:iron_ingot;";
	private static final String BREWING_HOLDS = "0=1 minecraft:potion;2=1 minecraft:potion;3=2 minecraft:nether_wart;4=5 minecraft:blaze_powder;";
	private static final String BOOKSHELF_HOLDS = "0=1 minecraft:book;4=1 minecraft:enchanted_book;";
	private static final String POT_HOLDS = "0=7 minecraft:diamond;";
	private static final String JUKEBOX_HOLDS = "0=1 minecraft:music_disc_cat;";
	private static final String SHULKER_HOLDS = "0=64 minecraft:cobblestone;13=2 minecraft:ender_pearl;";

	private static ItemStack book(String title, int pages) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> list = new ArrayList<>();
		for (int i = 1; i <= pages; i++) {
			list.add(Filterable.passThrough(Component.literal(title + ", page " + i)));
		}
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), "Jesse", 0, list, true));
		return book;
	}

	private static void containers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Container trapped = container(helper, TRAPPED, Blocks.TRAPPED_CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
		// names first: applying an item's components also sets the container's contents from it (empty here)
		ItemStack trappedName = new ItemStack(Items.TRAPPED_CHEST);
		trappedName.set(DataComponents.CUSTOM_NAME, Component.literal("Do not open"));
		level.getBlockEntity(helper.absolutePos(TRAPPED)).applyComponentsFromItemStack(trappedName);
		trapped.setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
		trapped.setItem(26, new ItemStack(Items.TNT));
		container(helper, HOPPER, Blocks.HOPPER.defaultBlockState()).setItem(2, new ItemStack(Items.WHEAT, 9));
		container(helper, DISPENSER, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.WEST)).setItem(4, new ItemStack(Items.ARROW, 16));
		Container dropper = container(helper, DROPPER, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.UP));
		dropper.setItem(0, new ItemStack(Items.EGG, 3));
		dropper.setItem(8, new ItemStack(Items.BONE));
		Container furnace = container(helper, FURNACE, Blocks.FURNACE.defaultBlockState());
		furnace.setItem(0, new ItemStack(Items.RAW_IRON, 8));
		furnace.setItem(1, new ItemStack(Items.COAL, 4));
		furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 3));
		Container brewing = container(helper, BREWING, Blocks.BREWING_STAND.defaultBlockState());
		ItemStack awkward = new ItemStack(Items.POTION);
		awkward.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.AWKWARD));
		brewing.setItem(0, awkward.copy());
		brewing.setItem(2, awkward.copy());
		brewing.setItem(3, new ItemStack(Items.NETHER_WART, 2));
		brewing.setItem(4, new ItemStack(Items.BLAZE_POWDER, 5));
		Container shelf = container(helper, BOOKSHELF, Blocks.CHISELED_BOOKSHELF.defaultBlockState()
			.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.WEST));
		shelf.setItem(0, new ItemStack(Items.BOOK));
		shelf.setItem(4, new ItemStack(Items.ENCHANTED_BOOK));
		container(helper, POT, Blocks.DECORATED_POT.defaultBlockState()).setItem(0, new ItemStack(Items.DIAMOND, 7));
		container(helper, JUKEBOX, Blocks.JUKEBOX.defaultBlockState()).setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
		set(level, helper.absolutePos(LECTERN), Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH));
		BlockPos lectern = helper.absolutePos(LECTERN);
		helper.assertTrue(LecternBlock.tryPlaceBook(null, level, lectern, level.getBlockState(lectern), book("Thornholm records", 4)), "the book wasn't put on the lectern");
		LecternBlockEntity l = (LecternBlockEntity) level.getBlockEntity(lectern);
		CompoundTag tag = l.saveWithoutMetadata(level.registryAccess());
		tag.putInt("Page", 2);
		l.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(l.getPage() == 2, "the lectern isn't open on page 3: " + l.getPage());
		Container shulker = container(helper, SHULKER, Blocks.LIME_SHULKER_BOX.defaultBlockState());
		ItemStack named = new ItemStack(Items.LIME_SHULKER_BOX);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Spare stone"));
		level.getBlockEntity(helper.absolutePos(SHULKER)).applyComponentsFromItemStack(named);
		shulker.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		shulker.setItem(13, new ItemStack(Items.ENDER_PEARL, 2));
	}

	private static void decorations(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemFrame frame = new ItemFrame(level, helper.absolutePos(FRAME), Direction.EAST);
		frame.setItem(new ItemStack(Items.COMPASS));
		frame.setRotation(3);
		level.addFreshEntity(frame);
		GlowItemFrame glow = new GlowItemFrame(level, helper.absolutePos(GLOW_FRAME), Direction.EAST);
		glow.setItem(new ItemStack(Items.GOLDEN_APPLE));
		level.addFreshEntity(glow);
		Painting painting = new Painting(level, helper.absolutePos(PAINTING), Direction.SOUTH,
			level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).getHolderOrThrow(PaintingVariants.KEBAB));
		level.addFreshEntity(painting);
		ArmorStand stand = new ArmorStand(level, helper.absolutePos(STAND).getX() + 0.5, helper.absolutePos(STAND).getY(), helper.absolutePos(STAND).getZ() + 0.5);
		CompoundTag pose = stand.saveWithoutId(new CompoundTag());
		pose.putBoolean("Small", true);
		pose.putBoolean("ShowArms", true);
		pose.putBoolean("NoBasePlate", true);
		CompoundTag poses = new CompoundTag();
		ListTag head = new ListTag();
		head.add(FloatTag.valueOf(20f));
		head.add(FloatTag.valueOf(0f));
		head.add(FloatTag.valueOf(0f));
		poses.put("Head", head);
		pose.put("Pose", poses);
		stand.load(pose);
		stand.setYRot(0f); // facing south
		stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
		stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		stand.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		level.addFreshEntity(stand);
		set(level, helper.absolutePos(BANNER), Blocks.WHITE_BANNER.defaultBlockState());
		ItemStack banner = new ItemStack(Items.WHITE_BANNER);
		banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
			.add(level.registryAccess().registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.STRIPE_TOP), DyeColor.RED)
			.add(level.registryAccess().registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.CROSS), DyeColor.BLUE)
			.build());
		level.getBlockEntity(helper.absolutePos(BANNER)).applyComponentsFromItemStack(banner);
		set(level, helper.absolutePos(FLOWER_POT), Blocks.POTTED_POPPY.defaultBlockState());
	}

	private static void everything(GameTestHelper helper) {
		containers(helper);
		decorations(helper);
	}

	/** Every container is back where {@code where} says, slot for slot. */
	private static void assertContainers(GameTestHelper helper, java.util.function.Function<BlockPos, BlockPos> where, String when) {
		ServerLevel level = helper.getLevel();
		helper.assertValueEqual(items(level, where.apply(TRAPPED)), TRAPPED_HOLDS, when + ": the trapped chest");
		helper.assertValueEqual(items(level, where.apply(HOPPER)), HOPPER_HOLDS, when + ": the hopper");
		helper.assertValueEqual(items(level, where.apply(DISPENSER)), DISPENSER_HOLDS, when + ": the dispenser");
		helper.assertValueEqual(items(level, where.apply(DROPPER)), DROPPER_HOLDS, when + ": the dropper");
		helper.assertValueEqual(items(level, where.apply(FURNACE)), FURNACE_HOLDS, when + ": the furnace");
		helper.assertValueEqual(items(level, where.apply(BREWING)), BREWING_HOLDS, when + ": the brewing stand");
		helper.assertValueEqual(items(level, where.apply(BOOKSHELF)), BOOKSHELF_HOLDS, when + ": the chiseled bookshelf");
		helper.assertValueEqual(items(level, where.apply(POT)), POT_HOLDS, when + ": the decorated pot");
		helper.assertValueEqual(items(level, where.apply(JUKEBOX)), JUKEBOX_HOLDS, when + ": the jukebox");
		helper.assertValueEqual(items(level, where.apply(SHULKER)), SHULKER_HOLDS, when + ": the shulker box");
		helper.assertTrue(level.getBlockEntity(where.apply(LECTERN)) instanceof LecternBlockEntity, when + ": no lectern at " + where.apply(LECTERN));
		LecternBlockEntity lectern = (LecternBlockEntity) level.getBlockEntity(where.apply(LECTERN));
		WrittenBookContent content = lectern.getBook().get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(lectern.getBook().is(Items.WRITTEN_BOOK) && content != null && content.pages().size() == 4
			&& content.title().raw().equals("Thornholm records"), when + ": the lectern's book: " + lectern.getBook() + " " + content);
		helper.assertValueEqual(lectern.getPage(), 2, when + ": the page the lectern is open on");
		helper.assertTrue(level.getBlockState(where.apply(LECTERN)).getValue(LecternBlock.HAS_BOOK), when + ": the lectern shows no book");
		helper.assertTrue(level.getBlockState(where.apply(JUKEBOX)).getValue(JukeboxBlock.HAS_RECORD), when + ": the jukebox shows no record");
		BlockState shelf = level.getBlockState(where.apply(BOOKSHELF));
		helper.assertTrue(shelf.getValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(0)) && shelf.getValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(4))
			&& !shelf.getValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(1)), when + ": the bookshelf shows the wrong books: " + shelf);
		helper.assertValueEqual(name(level, where.apply(SHULKER)), "Spare stone", when + ": the shulker box's name");
		helper.assertValueEqual(name(level, where.apply(TRAPPED)), "Do not open", when + ": the trapped chest's name");
	}

	private static String name(ServerLevel level, BlockPos abs) {
		return level.getBlockEntity(abs) instanceof Nameable n && n.getCustomName() != null ? n.getCustomName().getString() : "(no name)";
	}

	// ---------------------------------------------------------------- tests

	/**
	 * Every vanilla block that holds things arrives a quarter turn away with exactly its items, slot for slot: trapped
	 * chest, hopper, dispenser, dropper, furnace (input, fuel and output), brewing stand, chiseled bookshelf (showing its
	 * books), decorated pot, jukebox (with its disc), lectern (its book, open on the same page) and a shulker box; the
	 * named ones keep their names; nothing in the area is doubled or dropped.
	 */
	//$ gametest_batch AREA '"qa_c1110c_belongings_kinds"'
	@GameTest(template = AREA, batch = "qa_c1110c_belongings_kinds")
	public void everyVanillaContainerKindArrivesSlotForSlot(GameTestHelper helper) {
		Setup s = setup(helper, "kinds", Rotation.CLOCKWISE_90, QaMoverBelongings1110cGameTests::containers, false);
		assertContainers(helper, helper::absolutePos, "before the move (the test's own setup)");
		Map<String, Integer> census = census(helper);
		moveAll(helper, s);
		assertContainers(helper, p -> turned(s, p), "after the move");
		helper.assertValueEqual(helper.getLevel().getBlockState(turned(s, DISPENSER)).getValue(DispenserBlock.FACING), Direction.NORTH,
			"the dispenser (facing west) turned a quarter clockwise");
		helper.assertValueEqual(looseItems(helper), "[]", "items dropped");
		helper.assertValueEqual(census(helper), census, "every item in the area");
		helper.succeed();
	}

	/**
	 * A player's chest of treasured gear (a named, enchanted, worn pickaxe, a signed book, a shulker box item full of
	 * diamonds, a potion, dyed leather, and a named stack put in after the move started) keeps every stack exactly,
	 * components and all, through a half-turn move with a server restart while the whole house is packed in the crates.
	 */
	//$ gametest_batch AREA '"qa_c1110c_belongings_gear"'
	@GameTest(template = AREA, batch = "qa_c1110c_belongings_gear")
	public void aPlayersGearKeepsEveryComponentThroughARestartWhilePacked(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos chestAt = new BlockPos(5, 3, 5);
		ItemStack[] gear = new ItemStack[27];
		Setup s = setup(helper, "gear", Rotation.CLOCKWISE_180, h -> {
			ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
			pick.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.EFFICIENCY), 4);
			pick.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING), 3);
			pick.setDamageValue(321);
			pick.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
			gear[0] = pick;
			gear[1] = book("Jesse's diary", 3);
			ItemStack box = new ItemStack(Items.RED_SHULKER_BOX);
			box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 64), ItemStack.EMPTY, new ItemStack(Items.NETHERITE_INGOT, 3))));
			gear[5] = box;
			ItemStack potion = new ItemStack(Items.POTION);
			potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.LONG_SWIFTNESS));
			gear[9] = potion;
			ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
			boots.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366CC, true));
			gear[26] = boots;
			gear[13] = new ItemStack(Items.OAK_LOG, 64);
			Container chest = container(h, chestAt, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
			for (int i = 0; i < gear.length; i++) {
				if (gear[i] != null) {
					chest.setItem(i, gear[i].copy());
				}
			}
		}, false);
		MoveSites.get(level).work(level, s.hall()).orElseThrow(); // the move starts: its plan is taken now
		// the player puts one more thing in after the move started: it moves too, through the restart
		ItemStack late = new ItemStack(Items.GOLDEN_CARROT, 5);
		late.set(DataComponents.CUSTOM_NAME, Component.literal("For the road"));
		gear[20] = late;
		((Container) level.getBlockEntity(helper.absolutePos(chestAt))).setItem(20, late.copy());
		MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
		helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house didn't come all down: " + site.stage());
		helper.assertTrue(!(level.getBlockEntity(helper.absolutePos(chestAt)) instanceof Container), "the chest wasn't packed");
		site = restart(level, s);
		site = work(level, s, x -> false);
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE && !site.canceled(), "the move didn't end: " + site.stage());
		BlockPos at = turned(s, chestAt);
		helper.assertTrue(level.getBlockEntity(at) instanceof Container, "no chest at " + at + ": " + level.getBlockState(at));
		Container chest = (Container) level.getBlockEntity(at);
		for (int i = 0; i < gear.length; i++) {
			ItemStack want = gear[i] == null ? ItemStack.EMPTY : gear[i];
			helper.assertTrue(ItemStack.matches(chest.getItem(i), want), "slot " + i + " holds " + chest.getItem(i)
				+ " " + chest.getItem(i).getComponentsPatch() + ", not " + want + " " + want.getComponentsPatch());
		}
		helper.assertValueEqual(level.getBlockState(at).getValue(ChestBlock.FACING), Direction.WEST, "the chest (facing east) turned half round");
		helper.assertValueEqual(looseItems(helper), "[]", "items dropped");
		helper.succeed();
	}

	/**
	 * A double chest turned a quarter counterclockwise is still ONE double chest (both halves joined, 54 slots when
	 * opened) with each half's items in that half, and the player's view of it (the joined 54 slots) is in the same order.
	 */
	//$ gametest_batch AREA '"qa_c1110c_belongings_double"'
	@GameTest(template = AREA, batch = "qa_c1110c_belongings_double")
	public void aDoubleChestStaysOneDoubleChestWhenTurned(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos right = new BlockPos(4, 3, 5);
		BlockPos left = new BlockPos(5, 3, 5);
		Setup s = setup(helper, "double", Rotation.COUNTERCLOCKWISE_90, h -> {
			BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
			Container r = container(h, right, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
			r.setItem(0, new ItemStack(Items.APPLE, 1));
			r.setItem(26, new ItemStack(Items.BREAD, 2));
			Container l = container(h, left, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
			l.setItem(0, new ItemStack(Items.CARROT, 3));
			l.setItem(26, new ItemStack(Items.POTATO, 4));
		}, false);
		BlockState was = level.getBlockState(helper.absolutePos(left));
		Container joinedBefore = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, was, level, helper.absolutePos(left), true);
		helper.assertTrue(joinedBefore != null && joinedBefore.getContainerSize() == 54, "the double chest isn't joined before the move");
		String viewBefore = view(joinedBefore);
		moveAll(helper, s);
		BlockPos newRight = turned(s, right);
		BlockPos newLeft = turned(s, left);
		helper.assertValueEqual(items(level, newRight), "0=1 minecraft:apple;26=2 minecraft:bread;", "the right half");
		helper.assertValueEqual(items(level, newLeft), "0=3 minecraft:carrot;26=4 minecraft:potato;", "the left half");
		BlockState r = level.getBlockState(newRight);
		BlockState l = level.getBlockState(newLeft);
		helper.assertTrue(r.is(Blocks.CHEST) && l.is(Blocks.CHEST) && r.getValue(ChestBlock.TYPE) == ChestType.RIGHT && l.getValue(ChestBlock.TYPE) == ChestType.LEFT
			&& r.getValue(ChestBlock.FACING) == Direction.EAST && l.getValue(ChestBlock.FACING) == Direction.EAST, "the halves: " + r + " / " + l);
		helper.assertValueEqual(newRight.relative(ChestBlock.getConnectedDirection(r)), newLeft, "the right half joins the left");
		Container joined = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, l, level, newLeft, true);
		helper.assertTrue(joined != null && joined.getContainerSize() == 54, "opening it shows " + (joined == null ? "nothing" : joined.getContainerSize() + " slots"));
		helper.assertValueEqual(view(joined), viewBefore, "the opened double chest's 54 slots");
		helper.assertValueEqual(looseItems(helper), "[]", "items dropped");
		helper.succeed();
	}

	private static String view(Container c) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (!c.getItem(i).isEmpty()) {
				out.append(i).append('=').append(c.getItem(i).getCount()).append(' ').append(key(c.getItem(i))).append(';');
			}
		}
		return out.toString();
	}

	/**
	 * Signs arrive as they were and turned with the house: a standing sign (front dyed red and glowing, its back blue,
	 * waxed), a sign on a wall (still on its wall, facing turned) and a hanging sign under the roof, each with both sides.
	 */
	//$ gametest_batch AREA '"qa_c1110c_belongings_signs"'
	@GameTest(template = AREA, batch = "qa_c1110c_belongings_signs")
	public void signsKeepBothSidesTheirDyeGlowAndWaxAndTurn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos standingAt = new BlockPos(5, 3, 5);
		BlockPos wallAt = new BlockPos(3, 4, 5); // on the west wall's inside
		BlockPos hangingAt = new BlockPos(6, 5, 6); // under the roof
		Setup s = setup(helper, "signs", Rotation.CLOCKWISE_90, h -> {
			set(level, h.absolutePos(standingAt), Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 3));
			SignBlockEntity standing = (SignBlockEntity) level.getBlockEntity(h.absolutePos(standingAt));
			standing.setText(new SignText().setMessage(0, Component.literal("North Road")).setMessage(3, Component.literal("2 km"))
				.setColor(DyeColor.RED).setHasGlowingText(true), true);
			standing.setText(new SignText().setMessage(1, Component.literal("Thornholm")).setColor(DyeColor.BLUE), false);
			standing.setWaxed(true);
			set(level, h.absolutePos(wallAt), Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.EAST));
			SignBlockEntity wall = (SignBlockEntity) level.getBlockEntity(h.absolutePos(wallAt));
			wall.setText(new SignText().setMessage(1, Component.literal("Kitchen")), true);
			wall.setText(new SignText().setMessage(2, Component.literal("(the back)")), false);
			set(level, h.absolutePos(hangingAt), Blocks.OAK_HANGING_SIGN.defaultBlockState());
			SignBlockEntity hanging = (SignBlockEntity) level.getBlockEntity(h.absolutePos(hangingAt));
			hanging.setText(new SignText().setMessage(0, Component.literal("Inn")), true);
			hanging.setText(new SignText().setMessage(0, Component.literal("Rooms")).setHasGlowingText(true), false);
		}, false);
		moveAll(helper, s);
		BlockPos standingNow = turned(s, standingAt);
		helper.assertTrue(level.getBlockEntity(standingNow) instanceof SignBlockEntity, "no standing sign at " + standingNow + ": " + level.getBlockState(standingNow));
		SignBlockEntity standing = (SignBlockEntity) level.getBlockEntity(standingNow);
		helper.assertValueEqual(lines(standing.getFrontText()), "North Road|||2 km", "the standing sign's front");
		helper.assertValueEqual(lines(standing.getBackText()), "|Thornholm||", "the standing sign's back");
		helper.assertTrue(standing.getFrontText().getColor() == DyeColor.RED && standing.getFrontText().hasGlowingText(),
			"the front's dye and glow: " + standing.getFrontText().getColor() + " glowing " + standing.getFrontText().hasGlowingText());
		helper.assertTrue(standing.getBackText().getColor() == DyeColor.BLUE && !standing.getBackText().hasGlowingText(),
			"the back's dye and glow: " + standing.getBackText().getColor() + " glowing " + standing.getBackText().hasGlowingText());
		helper.assertTrue(standing.isWaxed(), "the standing sign lost its wax (players could edit it now)");
		helper.assertValueEqual(level.getBlockState(standingNow).getValue(StandingSignBlock.ROTATION), 7, "the standing sign turned a quarter (3 + 4)");
		BlockPos wallNow = turned(s, wallAt);
		BlockState wallState = level.getBlockState(wallNow);
		helper.assertTrue(wallState.is(Blocks.SPRUCE_WALL_SIGN) && wallState.getValue(WallSignBlock.FACING) == Direction.SOUTH,
			"the wall sign (facing east) turned with its wall: " + wallState);
		helper.assertTrue(wallState.canSurvive(level, wallNow), "the wall sign hangs on nothing at " + wallNow);
		SignBlockEntity wall = (SignBlockEntity) level.getBlockEntity(wallNow);
		helper.assertValueEqual(lines(wall.getFrontText()) + " / " + lines(wall.getBackText()), "|Kitchen|| / ||(the back)|", "the wall sign's sides");
		BlockPos hangingNow = turned(s, hangingAt);
		helper.assertTrue(level.getBlockEntity(hangingNow) instanceof SignBlockEntity && level.getBlockState(hangingNow).is(Blocks.OAK_HANGING_SIGN),
			"no hanging sign at " + hangingNow + ": " + level.getBlockState(hangingNow));
		SignBlockEntity hanging = (SignBlockEntity) level.getBlockEntity(hangingNow);
		helper.assertValueEqual(lines(hanging.getFrontText()) + " / " + lines(hanging.getBackText()), "Inn||| / Rooms|||", "the hanging sign's sides");
		helper.assertTrue(hanging.getBackText().hasGlowingText(), "the hanging sign's back lost its glow");
		helper.assertValueEqual(looseItems(helper), "[]", "items dropped (a sign popped off?)");
		helper.succeed();
	}

	private static String lines(SignText text) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < 4; i++) {
			out.append(i == 0 ? "" : "|").append(text.getMessage(i, false).getString());
		}
		return out.toString();
	}

	/**
	 * Decorations turned with the house a quarter clockwise: the item frame keeps its compass and how it's turned in the
	 * frame, the glow frame stays a glow frame with its apple, the painting keeps its picture and hangs on the turned
	 * wall, the armor stand keeps all six items, its pose and flags and faces the turned way, the banner keeps its
	 * patterns, and the potted flower is still potted.
	 */
	//$ gametest_batch AREA '"qa_c1110c_belongings_decor"'
	@GameTest(template = AREA, batch = "qa_c1110c_belongings_decor")
	public void framesStandsPaintingsAndBannersArriveTurned(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "decor", Rotation.CLOCKWISE_90, QaMoverBelongings1110cGameTests::decorations, false);
		Map<String, Integer> census = census(helper);
		helper.assertTrue(s.before().things().size() == 4, "the plan's things (frame, glow frame, painting, stand): " + s.before().things().size());
		moveAll(helper, s);
		List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, area(helper), f -> !(f instanceof GlowItemFrame));
		List<GlowItemFrame> glows = level.getEntitiesOfClass(GlowItemFrame.class, area(helper));
		helper.assertTrue(frames.size() == 1 && glows.size() == 1, "one frame and one glow frame, not " + frames + " and " + glows);
		ItemFrame frame = frames.get(0);
		helper.assertTrue(frame.getItem().is(Items.COMPASS) && frame.getRotation() == 3, "the frame: " + frame.getItem() + " turned " + frame.getRotation() + " in it, not a compass turned 3");
		helper.assertTrue(frame.getDirection() == Direction.SOUTH && frame.getPos().equals(turned(s, FRAME)),
			"the frame (facing east at " + FRAME + ") is at " + frame.getPos() + " facing " + frame.getDirection() + ", not " + turned(s, FRAME) + " facing south");
		GlowItemFrame glow = glows.get(0);
		helper.assertTrue(glow.getItem().is(Items.GOLDEN_APPLE) && glow.getDirection() == Direction.SOUTH && glow.getPos().equals(turned(s, GLOW_FRAME)),
			"the glow frame: " + glow.getItem() + " at " + glow.getPos() + " facing " + glow.getDirection());
		List<Painting> paintings = level.getEntitiesOfClass(Painting.class, area(helper));
		helper.assertTrue(paintings.size() == 1, "one painting, not " + paintings);
		Painting painting = paintings.get(0);
		helper.assertTrue(painting.getVariant().is(PaintingVariants.KEBAB) && painting.getDirection() == Direction.WEST && painting.getPos().equals(turned(s, PAINTING)),
			"the painting: " + painting.getVariant().unwrapKey() + " at " + painting.getPos() + " facing " + painting.getDirection()
				+ ", not kebab at " + turned(s, PAINTING) + " facing west");
		List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, area(helper));
		helper.assertTrue(stands.size() == 1, "one armor stand, not " + stands);
		ArmorStand stand = stands.get(0);
		helper.assertValueEqual(key(stand.getItemBySlot(EquipmentSlot.HEAD)) + "," + key(stand.getItemBySlot(EquipmentSlot.CHEST)) + ","
				+ key(stand.getItemBySlot(EquipmentSlot.LEGS)) + "," + key(stand.getItemBySlot(EquipmentSlot.FEET)) + ","
				+ key(stand.getItemBySlot(EquipmentSlot.MAINHAND)) + "," + key(stand.getItemBySlot(EquipmentSlot.OFFHAND)),
			"minecraft:golden_helmet,minecraft:iron_chestplate,minecraft:leather_leggings,minecraft:diamond_boots,minecraft:iron_sword,minecraft:shield",
			"the armor stand's items");
		helper.assertTrue(stand.isSmall() && stand.isShowArms() && stand.isNoBasePlate() && Math.abs(stand.getHeadPose().getX() - 20f) < 0.01f,
			"the stand's pose: small " + stand.isSmall() + ", arms " + stand.isShowArms() + ", no plate " + stand.isNoBasePlate() + ", head " + stand.getHeadPose().getX());
		helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.getYRot() - 90f)) < 1f, "the stand (facing south) faces " + stand.getYRot() + "°, not west (90°)");
		helper.assertTrue(stand.blockPosition().equals(turned(s, STAND)), "the stand stands at " + stand.blockPosition() + ", not " + turned(s, STAND));
		BlockPos bannerNow = turned(s, BANNER);
		helper.assertTrue(level.getBlockEntity(bannerNow) instanceof BannerBlockEntity, "no banner at " + bannerNow + ": " + level.getBlockState(bannerNow));
		BannerPatternLayers layers = ((BannerBlockEntity) level.getBlockEntity(bannerNow)).getPatterns();
		helper.assertTrue(layers.layers().size() == 2 && layers.layers().get(0).pattern().is(BannerPatterns.STRIPE_TOP) && layers.layers().get(0).color() == DyeColor.RED
			&& layers.layers().get(1).pattern().is(BannerPatterns.CROSS) && layers.layers().get(1).color() == DyeColor.BLUE, "the banner's patterns: " + layers.layers());
		helper.assertTrue(level.getBlockState(turned(s, FLOWER_POT)).is(Blocks.POTTED_POPPY), "the potted poppy: " + level.getBlockState(turned(s, FLOWER_POT)));
		helper.assertValueEqual(looseItems(helper), "[]", "items dropped");
		helper.assertValueEqual(census(helper), census, "every item and thing in the area");
		helper.succeed();
	}

	/**
	 * Called off half way through putting it up at the new spot (some containers and things already there): everything is
	 * taken back to the old spot, each container slot for slot, each frame and the stand where they were, and every item
	 * and thing in the area counted the same as before the move began; nothing loose.
	 */
	//$ gametest_batch AREA '"qa_c1110c_belongings_cancel_up"'
	@GameTest(template = AREA, batch = "qa_c1110c_belongings_cancel_up")
	public void calledOffHalfWayUpEveryItemComesBackOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "cancel_up", Rotation.CLOCKWISE_90, QaMoverBelongings1110cGameTests::everything, false);
		Map<String, Integer> census = census(helper);
		BlockPos firstContainer = turned(s, TRAPPED);
		MoveSite site = work(level, s, x -> x.stage().rebuilding() && level.getBlockEntity(firstContainer) instanceof Container);
		helper.assertTrue(site.stage().rebuilding(), "the move never got to putting it up: " + site.stage());
		for (int i = 0; i < 6 && site.stage() != MoveSite.Stage.DONE; i++) {
			MoverWork.work(level, s.mover(), site);
		}
		helper.assertTrue(site.stage() != MoveSite.Stage.DONE, "the move finished before it could be called off");
		PendingMoves.get(level).remove(s.from()); // the Move page's Call off
		site = work(level, s, x -> false);
		backAsItWas(helper, s, site, census, "called off half way up");
		helper.succeed();
	}

	/**
	 * Called off half way through taking it down (some containers and things packed, the rest still standing), then the
	 * server restarts before the Mover goes on: everything goes back once, nothing doubled from the packed and the
	 * standing copies, nothing lost.
	 */
	//$ gametest_batch AREA '"qa_c1110c_belongings_cancel_down"'
	@GameTest(template = AREA, batch = "qa_c1110c_belongings_cancel_down")
	public void calledOffHalfWayDownAndRestartedNothingDoubled(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "cancel_down", Rotation.CLOCKWISE_90, QaMoverBelongings1110cGameTests::everything, false);
		Map<String, Integer> census = census(helper);
		BlockPos lectern = helper.absolutePos(LECTERN);
		BlockPos trapped = helper.absolutePos(TRAPPED);
		MoveSite site = work(level, s, x -> level.getBlockState(lectern).isAir() || level.getBlockState(trapped).isAir());
		helper.assertTrue(site.stage() == MoveSite.Stage.DISMANTLE, "not half way down: " + site.stage());
		int stillStanding = 0;
		for (BlockPos p : List.of(TRAPPED, HOPPER, DISPENSER, DROPPER, FURNACE, BREWING, BOOKSHELF, POT, JUKEBOX, LECTERN, SHULKER)) {
			stillStanding += level.getBlockState(helper.absolutePos(p)).isAir() ? 0 : 1;
		}
		helper.assertTrue(stillStanding > 0 && stillStanding < 11, "not half way through the containers: " + stillStanding + " of 11 still standing");
		PendingMoves.get(level).remove(s.from());
		site = restart(level, s);
		site = work(level, s, x -> false);
		backAsItWas(helper, s, site, census, "called off half way down, then restarted");
		helper.succeed();
	}

	/**
	 * The server restarts every 3 of the Mover's steps through the whole move (every stage, the containers and things
	 * packed, half put up, the frames half hung): everything arrives once, as it was, and nothing is doubled or dropped.
	 */
	//$ gametest_batch AREA '"qa_c1110c_belongings_restarts"'
	@GameTest(template = AREA, batch = "qa_c1110c_belongings_restarts")
	public void restartsEveryFewStepsThroughTheWholeMoveNothingDoubled(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "restarts", Rotation.CLOCKWISE_90, QaMoverBelongings1110cGameTests::everything, false);
		Map<String, Integer> census = census(helper);
		MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
		int restarts = 0;
		java.util.Set<MoveSite.Stage> seen = java.util.EnumSet.noneOf(MoveSite.Stage.class);
		for (int i = 1; i <= MAX_STEPS && site.stage() != MoveSite.Stage.DONE; i++) {
			MoverWork.work(level, s.mover(), site);
			if (i % 3 == 0 && site.stage() != MoveSite.Stage.DONE) {
				seen.add(site.stage());
				site = restart(level, s);
				restarts++;
			}
		}
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE && !site.canceled(), "the move didn't end: " + site.stage());
		helper.assertTrue(seen.containsAll(List.of(MoveSite.Stage.DISMANTLE, MoveSite.Stage.STRUCTURE, MoveSite.Stage.DECORATION)),
			"restarted in " + seen + " only (" + restarts + " restarts)");
		String wrong = standing(level, s, s.to(), s.rotation());
		helper.assertTrue(wrong == null, "the house at its new spot: " + wrong);
		assertContainers(helper, p -> turned(s, p), "restarted " + restarts + " times");
		helper.assertValueEqual(looseItems(helper), "[]", "items dropped");
		helper.assertValueEqual(census(helper), census, "every item and thing in the area after " + restarts + " restarts");
		helper.succeed();
	}

	/**
	 * Called off in the last stage, while the decorations go up at the new spot (the walls and containers already there):
	 * everything comes back to the old spot once, the frames, painting and stand too, and nothing is left at the new spot.
	 */
	//$ gametest_batch AREA '"qa_c1110c_belongings_cancel_last"'
	@GameTest(template = AREA, batch = "qa_c1110c_belongings_cancel_last")
	public void calledOffInTheLastStageEverythingComesBackOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "cancel_last", Rotation.CLOCKWISE_90, QaMoverBelongings1110cGameTests::everything, false);
		Map<String, Integer> census = census(helper);
		MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.DECORATION);
		helper.assertTrue(site.stage() == MoveSite.Stage.DECORATION, "the move never got to its last stage: " + site.stage());
		MoverWork.work(level, s.mover(), site);
		helper.assertTrue(site.stage() == MoveSite.Stage.DECORATION, "the last stage was over in one step: " + site.stage());
		PendingMoves.get(level).remove(s.from());
		site = work(level, s, x -> false);
		backAsItWas(helper, s, site, census, "called off in the last stage");
		AABB newSpot = new AABB(s.to()).expandTowards(7, 5, 7).inflate(0.5);
		helper.assertValueEqual(level.getEntities((Entity) null, newSpot, e -> !(e instanceof Villager)).toString(), "[]", "things left at the new spot");
		int atNew = 0;
		for (MovePlan.Placed p : s.before().placed(s.to(), s.rotation())) {
			atNew += level.getBlockState(p.pos()).isAir() ? 0 : 1;
		}
		helper.assertValueEqual(atNew, 0, "blocks left standing at the new spot");
		helper.succeed();
	}

	private static void backAsItWas(GameTestHelper helper, Setup s, MoveSite site, Map<String, Integer> census, String when) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.canceled(), when + ": the move ended " + site.stage() + (site.canceled() ? " canceled" : " NOT canceled"));
		String wrong = standing(level, s, s.from(), Rotation.NONE);
		helper.assertTrue(wrong == null, when + ": the house at its old spot: " + wrong);
		assertContainers(helper, helper::absolutePos, when);
		helper.assertValueEqual(looseItems(helper), "[]", when + ": items dropped");
		helper.assertValueEqual(census(helper), census, when + ": every item and thing in the area");
		ItemFrame frame = level.getEntitiesOfClass(ItemFrame.class, area(helper), f -> f.getItem().is(Items.COMPASS)).stream().findFirst().orElse(null);
		helper.assertTrue(frame != null && frame.getPos().equals(helper.absolutePos(FRAME)) && frame.getDirection() == Direction.EAST,
			when + ": the compass frame: " + (frame == null ? "gone" : frame.getPos() + " facing " + frame.getDirection()));
		List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, area(helper));
		helper.assertTrue(stands.size() == 1 && stands.get(0).blockPosition().equals(helper.absolutePos(STAND)), when + ": the armor stand: " + stands);
		helper.assertTrue(level.getBlockEntity(helper.absolutePos(BANNER)) instanceof BannerBlockEntity b && b.getPatterns().layers().size() == 2,
			when + ": the banner's patterns");
	}

	/**
	 * A player's own chest, barrel and frame, added after the house was built (not in its blueprint), move with it: the
	 * chest and barrel arrive with their items and the frame with its map; nothing stays behind at the old spot.
	 */
	//$ gametest_batch AREA '"qa_c1110c_belongings_added"'
	@GameTest(template = AREA, batch = "qa_c1110c_belongings_added")
	public void aChestAPlayerAddedAfterTheBuildMovesToo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos chestAt = new BlockPos(4, 3, 4);
		BlockPos barrelAt = new BlockPos(6, 3, 6);
		BlockPos frameAt = new BlockPos(7, 4, 5); // on the east wall's inside, facing west
		Setup s = setup(helper, "added", Rotation.CLOCKWISE_90, h -> {
			container(h, chestAt, Blocks.CHEST.defaultBlockState()).setItem(7, new ItemStack(Items.EMERALD, 33));
			container(h, barrelAt, Blocks.BARREL.defaultBlockState()).setItem(0, new ItemStack(Items.SALMON, 11));
			ItemFrame frame = new ItemFrame(level, h.absolutePos(frameAt), Direction.WEST);
			frame.setItem(new ItemStack(Items.FILLED_MAP));
			level.addFreshEntity(frame);
		}, true);
		Map<String, Integer> census = census(helper);
		moveAll(helper, s);
		helper.assertValueEqual(items(level, turned(s, chestAt)), "7=33 minecraft:emerald;", "the player's chest");
		helper.assertValueEqual(items(level, turned(s, barrelAt)), "0=11 minecraft:salmon;", "the player's barrel");
		List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, area(helper));
		helper.assertTrue(frames.size() == 1 && frames.get(0).getItem().is(Items.FILLED_MAP) && frames.get(0).getPos().equals(turned(s, frameAt)),
			"the player's frame: " + frames + (frames.isEmpty() ? "" : " at " + frames.get(0).getPos() + ", not " + turned(s, frameAt)));
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(MIN), helper.absolutePos(MAX))) {
			helper.assertTrue(level.getBlockState(pos).isAir(), "the old spot still has " + level.getBlockState(pos) + " at " + pos);
		}
		helper.assertValueEqual(looseItems(helper), "[]", "items dropped");
		helper.assertValueEqual(census(helper), census, "every item in the area");
		helper.succeed();
	}
}
