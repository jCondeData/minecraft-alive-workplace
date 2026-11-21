package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane e, 2026-11-08), from the builder's promise (BlockEntityData, wiki builder.md: a blueprint copies looks, never
 * anything that hands out free items) and the upload rule (workplaceAllowUploads, on by default: any player's file).
 * Every block entity saves the item components it was placed with ("components"), and Create's Clipboard gives them
 * all back on the item it drops when broken (ClipboardBlock.applyComponentsToDropStack). A hand-written file can give a
 * clipboard's data any component: here Sharpness 255 and +1000 attack damage. The clipboard a builder builds from that
 * file, for the plain clipboard its material list asks, must drop a plain clipboard.
 */
public class QaCreateFileClipboard1108eCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos SRC = new BlockPos(18, 2, 20);
	private static final BlockPos CONTROL = new BlockPos(22, 2, 4);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);

	/** Looked up when used: this class loads before Create registers its blocks. */
	private static Block clipboard() {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:clipboard"));
	}

	//$ gametest_ticks_batch HUGE_AREA '1200' '"qa_create_file_clipboard_1108e"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qa_create_file_clipboard_1108e")
	public void aClipboardFromAnUploadedFileDropsAPlainClipboard(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(clipboard() != Blocks.AIR, "(setup) no create:clipboard");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);

		// The file: one clipboard on the floor, written by the game, its data then given the components by hand
		helper.setBlock(SRC, onTheFloor());
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(SRC, Blocks.AIR);
		CompoundTag file = template.save(new CompoundTag());
		CompoundTag data = file.getList("blocks", Tag.TAG_COMPOUND).getCompound(0).getCompound("nbt");
		data.put("components", strongComponents(level));

		// Control: a clipboard loaded with exactly this data drops a clipboard with them when broken
		helper.setBlock(CONTROL, onTheFloor());
		helper.getBlockEntity(CONTROL).loadWithComponents(data.copy(), level.registryAccess());
		String control = describe(breakAndGather(helper, player, CONTROL));
		helper.assertTrue(control.contains("sharpness") && control.contains("attack"), "(setup) the control clipboard dropped " + control
			+ ", so the test can't tell");

		byte[] bytes;
		try {
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			NbtIo.writeCompressed(file, out);
			bytes = out.toByteArray();
		} catch (java.io.IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		ResourceLocation id;
		try {
			// the import a player's upload at the Table goes through (TableServer: folder "uploads/<player>")
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/qa_clipboard",
				"clipboard_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
		} catch (Exception e) {
			throw new GameTestAssertException("(setup) the upload didn't import: " + e);
		}

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
		// a chest of exactly the material list, plain items as a player has them
		helper.setBlock(CHEST, Blocks.CHEST);
		net.minecraft.world.Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (java.util.Map.Entry<net.minecraft.world.item.Item, Integer> e : site.plan(level).materials().entrySet()) {
			chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
		}
		String[] got = {null};
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			if (got[0] == null) {
				helper.assertTrue(helper.getBlockState(ORIGIN).is(clipboard()), "no clipboard built: " + helper.getBlockState(ORIGIN));
				// broken once: breaking it takes the block away
				got[0] = describe(breakAndGather(helper, player, ORIGIN));
			}
			helper.assertTrue(got[0].contains("clipboard"), "breaking the built clipboard dropped no clipboard: " + got[0]);
			helper.assertTrue(!got[0].contains("sharpness") && !got[0].contains("attack"), "breaking the clipboard a builder built from"
				+ " an uploaded file dropped " + got[0] + ": the file's hand-written components (Sharpness 255, +1000 attack"
				+ " damage) handed out on a plain clipboard");
			player.discard();
		});
	}

	/** Sharpness 255 and +1000 attack damage in the main hand, as saved item components. */
	private static Tag strongComponents(ServerLevel level) {
		var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemEnchantments.Mutable sharp = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		sharp.set(enchantments.getOrThrow(Enchantments.SHARPNESS), 255);
		ItemAttributeModifiers attack = ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(ResourceLocation.parse("aliveworkplace_compat:qa_attack"), 1000.0,
				AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.build();
		DataComponentMap map = DataComponentMap.builder()
			.set(DataComponents.ENCHANTMENTS, sharp.toImmutable())
			.set(DataComponents.ATTRIBUTE_MODIFIERS, attack)
			.build();
		return DataComponentMap.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), map).getOrThrow();
	}

	private static BlockState onTheFloor() {
		BlockState state = clipboard().defaultBlockState();
		for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
			if (property.getName().equals("face")) {
				return with(state, property, "floor");
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property, String value) {
		return state.setValue(property, property.getValue(value).orElseThrow(() -> new GameTestAssertException("(setup) no " + value + " for " + property)));
	}

	/** The survival player breaks the block at {@code rel}; every item it gave, in his inventory or on the ground. */
	private static List<ItemStack> breakAndGather(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(rel);
		player.getInventory().clearContent();
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5);
		player.gameMode.destroyBlock(at);
		List<ItemStack> all = new ArrayList<>();
		for (ItemStack s : player.getInventory().items) {
			if (!s.isEmpty()) {
				all.add(s.copy());
			}
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2))) {
			all.add(e.getItem().copy());
			e.discard();
		}
		return all;
	}

	private static String describe(List<ItemStack> stacks) {
		List<String> out = new ArrayList<>();
		for (ItemStack s : stacks) {
			out.add(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath() + " " + s.getEnchantments().toString().toLowerCase()
				+ (s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty() ? "" : " attack modifiers"));
		}
		return out.toString();
	}
}
