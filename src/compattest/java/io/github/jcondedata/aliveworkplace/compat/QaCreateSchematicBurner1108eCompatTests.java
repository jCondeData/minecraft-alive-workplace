package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane e, 2026-11-08), from the builder's promise (BlockEntityData, wiki builder.md: a blueprint copies looks, never
 * anything that hands out free goods, "the lava in a Create tank" among them; B429: a scanned lit furnace is built
 * cold, its fuel never copied) on Create's Blaze Burner. A burner keeps its fuel in its data: which fuel ("fuelLevel":
 * none, normal, a blaze cake's), how long it burns ("burnTimeRemaining"), and whether it is a creative burner fed a
 * Creative Blaze Cake ("isCreative", heat forever, creative mode only). A Create schematic (uploaded at the Schematic
 * Table, ROADMAP 39.6) of a creative burner and of one superheated for years is built by a real builder from a chest
 * holding exactly its material list: the burners must be built with no fuel, as a fresh burner is placed.
 */
public class QaCreateSchematicBurner1108eCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos SRC = new BlockPos(18, 2, 20);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);
	/** Looked up when used: this class loads before Create registers its blocks. */
	private static Block burner() {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:blaze_burner"));
	}

	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_create_schematic_burner_1108e"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_create_schematic_burner_1108e")
	public void aSchematicOfACreativeAndASuperheatedBurnerIsBuiltWithNoFuel(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(burner() != Blocks.AIR, "(setup) no create:blaze_burner");
		// the creative burner, two blocks east the one with a blaze cake's heat for Integer.MAX_VALUE ticks (about 3.4 years)
		helper.setBlock(SRC, lit());
		fuel(helper, SRC, tag -> tag.putBoolean("isCreative", true));
		helper.setBlock(SRC.east(2), lit());
		fuel(helper, SRC.east(2), tag -> {
			tag.putInt("fuelLevel", 2);
			tag.putInt("burnTimeRemaining", Integer.MAX_VALUE);
		});
		String before = fuelOf(helper.getBlockEntity(SRC), level) + " / " + fuelOf(helper.getBlockEntity(SRC.east(2)), level);
		helper.assertTrue(before.contains("creative") && before.contains("burn " + Integer.MAX_VALUE),
			"(setup) the burners didn't take the fuel, so the test can't tell: " + before);
		net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate drawn = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
		drawn.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(3, 1, 1), true, Blocks.STRUCTURE_VOID);
		for (int dx = 0; dx < 3; dx++) {
			helper.setBlock(SRC.east(dx), Blocks.AIR);
		}
		net.minecraft.server.MinecraftServer server = level.getServer();
		String owner = "gametest_burner";
		String file = "hot_works_" + Long.toHexString(level.getGameTime()) + ".nbt";
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
			chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
		}
		String list = asked.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).toList().toString();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<String> free = new ArrayList<>();
			for (int dx : new int[] {0, 2}) {
				BlockPos at = helper.absolutePos(ORIGIN.east(dx));
				helper.assertTrue(level.getBlockState(at).is(burner()), "no blaze burner built: " + level.getBlockState(at));
				String fuel = fuelOf(level.getBlockEntity(at), level);
				if (!fuel.equals("no fuel")) {
					free.add("the burner at +" + dx + " built with " + fuel);
				}
			}
			helper.assertTrue(free.isEmpty(), "a builder built a schematic's Blaze Burners still fueled, for the material list " + list
				+ " (a fresh burner has no fuel; a creative one needs a Creative Blaze Cake): " + free);
		});
	}

	/** A burner with its blaze in it (an empty one, heat "none", has no block entity), not yet fed. */
	private static net.minecraft.world.level.block.state.BlockState lit() {
		net.minecraft.world.level.block.state.BlockState state = burner().defaultBlockState();
		for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
			if (property.getName().equals("blaze")) {
				return with(state, property, "smouldering");
			}
		}
		throw new GameTestAssertException("(setup) create:blaze_burner has no 'blaze' property: " + state);
	}

	private static <T extends Comparable<T>> net.minecraft.world.level.block.state.BlockState with(
		net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property, String value) {
		return state.setValue(property, property.getValue(value).orElseThrow(() -> new GameTestAssertException("(setup) no " + value + " for " + property)));
	}

	/** Changes the saved data of the burner at {@code rel} with {@code change} and loads it back. */
	private static void fuel(GameTestHelper helper, BlockPos rel, java.util.function.Consumer<CompoundTag> change) {
		BlockEntity be = helper.getBlockEntity(rel);
		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = be.saveWithoutMetadata(registries);
		change.accept(tag);
		be.loadWithComponents(tag, registries);
		be.setChanged();
	}

	/** "no fuel", or what fuel the burner's saved data holds. */
	private static String fuelOf(BlockEntity be, ServerLevel level) {
		if (be == null) {
			return "no block entity";
		}
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		List<String> parts = new ArrayList<>();
		if (tag.getBoolean("isCreative")) {
			parts.add("creative");
		}
		if (tag.getInt("fuelLevel") != 0) {
			parts.add("fuel level " + tag.getInt("fuelLevel"));
		}
		if (tag.getInt("burnTimeRemaining") > 0) {
			parts.add("burn " + tag.getInt("burnTimeRemaining"));
		}
		return parts.isEmpty() ? "no fuel" : String.join(", ", parts);
	}
}
