package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane e, 2026-11-08), from the builder's promise (BlockEntityData, wiki builder.md: a blueprint copies looks, never
 * anything that hands out free items) on Create's Copper Backtank. A backtank set down as a block keeps the whole item
 * it was (its enchantments, its name, any component) in its data ("Components") and gives that item back when a player
 * right-clicks it with an empty hand and nothing on his back. A player's own Protection IV, Mending backtank set down and saved in a Create
 * schematic (uploaded at the Schematic Table, ROADMAP 39.6) is built by a real builder from a chest holding just its
 * material list, as a player has it (a plain Copper Backtank): the backtank must be built, and taking the built one must
 * give a plain backtank, or every copy hands out the enchantments again.
 */
public class QaCreateSchematicBacktank1108eCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos SRC = new BlockPos(18, 2, 20);
	private static final BlockPos CONTROL = new BlockPos(22, 2, 4);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);

	/** Looked up when used: this class loads before Create registers its blocks. */
	private static Block backtank() {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:copper_backtank"));
	}

	//$ gametest_ticks_batch HUGE_AREA '1200' '"qa_create_schematic_backtank_1108e"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qa_create_schematic_backtank_1108e")
	public void aSchematicOfAnEnchantedBacktankIsBuiltFromAPlainBacktankAsAPlainOne(GameTestHelper helper) {
		build(helper, true);
	}

	/**
	 * The same with the chest holding the list's own item as it stands (Create's technical "copper_backtank_placeable"),
	 * so the backtank is built whatever item the list asks for: the copy must still be plain.
	 */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"qa_create_schematic_backtank_1108e2"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qa_create_schematic_backtank_1108e2")
	public void aSchematicOfAnEnchantedBacktankBuiltFromItsListedItemIsAPlainOne(GameTestHelper helper) {
		build(helper, false);
	}

	/** {@code asAPlayerHasIt}: the chest holds a plain Copper Backtank where the list asks for Create's placeable item. */
	private static void build(GameTestHelper helper, boolean asAPlayerHasIt) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(backtank() != Blocks.AIR, "(setup) no create:copper_backtank");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);

		// The player's own enchanted backtank, set down; the control one beside it shows taking it gives the item back
		setDown(helper, SRC);
		setDown(helper, CONTROL);
		ItemStack taken = takeOnBack(helper, player, CONTROL);
		helper.assertTrue(enchantments(taken) == 2, "(setup) taking the player's own backtank gave " + taken + " with "
			+ taken.getEnchantments() + ", so the test can't tell");
		player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);

		net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate drawn = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
		drawn.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(1, 1, 1), true, Blocks.STRUCTURE_VOID);
		helper.setBlock(SRC, Blocks.AIR);
		net.minecraft.server.MinecraftServer server = level.getServer();
		String owner = "gametest_backtank";
		String file = "tank_" + asAPlayerHasIt + "_" + Long.toHexString(level.getGameTime()) + ".nbt";
		ResourceLocation id = io.github.jcondedata.aliveworkplace.table.CreateSchematics.idFor(owner, file);
		java.nio.file.Path path = io.github.jcondedata.aliveworkplace.table.CreateSchematics.uploadedFolder(server).resolve(owner).resolve(file);
		try {
			java.nio.file.Files.createDirectories(path.getParent());
			net.minecraft.nbt.NbtIo.writeCompressed(drawn.save(new CompoundTag()), path);
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		io.github.jcondedata.aliveworkplace.table.CreateSchematics.sync(server);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(server, id).isPresent(), "the schematic " + file + " wasn't imported as " + id);

		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		BuildPlan plan = site.plan(level);
		Map<Item, Integer> asked = plan.materials();
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : asked.entrySet()) {
			// the material list's items as a player would have them: plain, unenchanted. Create's
			// "copper_backtank_placeable" is the block's own technical item, which no player can get (no recipe, not in the
			// creative tab): a player has the backtank he wears, as Create's Schematicannon asks for (its getRequiredItems)
			Item item = e.getKey();
			if (asAPlayerHasIt && BuiltInRegistries.ITEM.getKey(item).getPath().equals("copper_backtank_placeable")) {
				item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:copper_backtank"));
			}
			chest.setItem(slot++, new ItemStack(item, e.getValue()));
		}
		String list = asked.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).toList().toString();
		ItemStack[] got = {null};
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			if (got[0] == null) {
				helper.assertTrue(helper.getBlockState(ORIGIN).is(backtank()), "no backtank built: " + helper.getBlockState(ORIGIN));
				// taken once: taking it takes the block away
				got[0] = takeOnBack(helper, player, ORIGIN);
			}
			ItemStack built = got[0];
			helper.assertTrue(!built.isEmpty(), "taking the built backtank gave nothing");
			helper.assertTrue(enchantments(built) == 0, "taking the backtank a builder built from a schematic, for the material list " + list
				+ " (plain items), gave a backtank enchanted " + built.getEnchantments() + ": every copy hands the scanned one's"
				+ " enchantments out again");
			player.discard();
		});
	}

	/** Sets down at {@code rel} a copper backtank enchanted Protection IV and Mending, as a player places his own. */
	private static void setDown(GameTestHelper helper, BlockPos rel) {
		ServerLevel level = helper.getLevel();
		ItemStack stack = new ItemStack(backtank().asItem());
		var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		stack.enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 4);
		stack.enchant(enchantments.getOrThrow(Enchantments.MENDING), 1);
		BlockState state = backtank().defaultBlockState();
		helper.setBlock(rel, state);
		BlockPos at = helper.absolutePos(rel);
		backtank().setPlacedBy(level, at, level.getBlockState(at), null, stack);
	}

	/** The survival player, standing (not sneaking) with an empty hand and nothing on his back, takes the backtank at {@code rel}. */
	private static ItemStack takeOnBack(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
		BlockPos at = helper.absolutePos(rel);
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.SOUTH, at, false);
		player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
		return player.getItemBySlot(EquipmentSlot.CHEST).copy();
	}

	private static int enchantments(ItemStack stack) {
		return stack.getEnchantments().size();
	}
}
