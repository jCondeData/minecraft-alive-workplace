package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.mechanic.CreateBuilds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.table.CreateSchematics;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane E, Create and config, 2026-11-02): the M39 switches as their config text promises (WorkplaceConfig).
 * {@code createBuilds} off: the Blueprint Table doesn't offer the Watermill, the Windmill or the Workshop (nor a style of
 * them), yet each still loads by id for a blueprint already handed out; on again, they're back. {@code createSchematics}
 * off: "the table leaves Create's schematics alone (those already imported stay)": a schematic imported while it was on
 * stays at the table and in the library, and opening the table with the switch off doesn't take it away.
 */
public class QaCreateSwitchesOff1102CompatTests implements FabricGameTest {
	private static final BlockPos TABLE = new BlockPos(1, 1, 1);

	private static List<ResourceLocation> listed(MinecraftServer server) {
		return TableServer.listing(server).stream().map(e -> e.id()).toList();
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa_create_builds_off"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa_create_builds_off")
	public void createBuildsOffTakesThemOffTheTableButTheyStillLoadById(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		boolean was = CreateBuilds.ENABLED;
		try {
			CreateBuilds.ENABLED = false;
			List<ResourceLocation> off = listed(server);
			for (ResourceLocation id : off) {
				helper.assertTrue(!CreateBuilds.isCreateBuild(id), "createBuilds is off, but the table offers " + id
					+ (id.equals(BlueprintStyles.base(id)) ? "" : " (a style of " + BlueprintStyles.base(id) + ")"));
			}
			for (StarterBlueprints.Entry entry : StarterBlueprints.CREATE_ONLY) {
				helper.assertTrue(BlueprintLibrary.get(server, entry.id()).isPresent(),
					entry.id() + " no longer loads by id with createBuilds off: a blueprint already handed out would build nothing");
			}
			helper.assertTrue(off.size() > 10, "the table offers only " + off.size() + " builds with createBuilds off");
			CreateBuilds.ENABLED = true;
			List<ResourceLocation> on = listed(server);
			for (StarterBlueprints.Entry entry : StarterBlueprints.CREATE_ONLY) {
				helper.assertTrue(on.contains(entry.id()), entry.id() + " isn't back at the table with createBuilds on again");
			}
			helper.assertTrue(on.containsAll(off), "turning createBuilds on again took other builds off the table");
		} finally {
			CreateBuilds.ENABLED = was;
		}
		helper.succeed();
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa_create_schematics_off"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa_create_schematics_off")
	public void createSchematicsOffKeepsTheOnesAlreadyImported(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String owner = "gametest_qa_kept";
		ResourceLocation id = CreateSchematics.idFor(owner, "kept_mill.nbt");
		clear(server, owner, id);
		boolean was = CreateSchematics.ENABLED;
		ServerPlayer player = playerAtTable(helper);
		try {
			CreateSchematics.ENABLED = true;
			write(server, owner, "kept_mill.nbt", structure(3));
			helper.useBlock(TABLE, player); // an empty hand opens the table, which lists the uploaded file
			helper.assertTrue(BlueprintLibrary.get(server, id).isPresent(), "the uploaded schematic wasn't imported with the switch on");
			helper.assertTrue(listed(server).contains(id), "the imported schematic isn't at the table with the switch on");

			CreateSchematics.ENABLED = false;
			helper.useBlock(TABLE, player);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			helper.useBlock(TABLE, player);
			helper.assertTrue(BlueprintLibrary.get(server, id).isPresent(),
				"createSchematics off took an already imported schematic out of the library (its config text: those already imported stay)");
			helper.assertTrue(listed(server).contains(id),
				"createSchematics off took an already imported schematic off the table (its config text: those already imported stay)");
		} finally {
			CreateSchematics.ENABLED = was;
			server.getPlayerList().remove(player);
			clear(server, owner, id);
		}
		helper.succeed();
	}

	// --- helpers (as CreateSchematicCompatTests writes Create's files) -------------------------------------------------

	private static CompoundTag structure(int side) {
		CompoundTag tag = new CompoundTag();
		tag.put("size", ints(side, 1, side));
		ListTag palette = new ListTag();
		CompoundTag stone = new CompoundTag();
		stone.putString("Name", "minecraft:stone");
		palette.add(stone);
		tag.put("palette", palette);
		ListTag blocks = new ListTag();
		for (int x = 0; x < side; x++) {
			for (int z = 0; z < side; z++) {
				CompoundTag b = new CompoundTag();
				b.put("pos", ints(x, 0, z));
				b.putInt("state", 0);
				blocks.add(b);
			}
		}
		tag.put("blocks", blocks);
		tag.put("entities", new ListTag());
		tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		return tag;
	}

	private static ListTag ints(int... values) {
		ListTag list = new ListTag();
		for (int v : values) {
			list.add(IntTag.valueOf(v));
		}
		return list;
	}

	private static void write(MinecraftServer server, String owner, String name, CompoundTag tag) {
		Path file = CreateSchematics.uploadedFolder(server).resolve(owner).resolve(name);
		try {
			Files.createDirectories(file.getParent());
			NbtIo.writeCompressed(tag, file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void clear(MinecraftServer server, String owner, ResourceLocation... ids) {
		Path dir = CreateSchematics.uploadedFolder(server).resolve(owner);
		try {
			if (Files.isDirectory(dir)) {
				try (Stream<Path> walk = Files.walk(dir)) {
					for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
						Files.deleteIfExists(p);
					}
				}
			}
			for (ResourceLocation id : ids) {
				Files.deleteIfExists(server.getStructureManager().createAndValidatePathToGeneratedStructure(id, ".nbt"));
				server.getStructureManager().remove(id);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static ServerPlayer playerAtTable(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos table = helper.absolutePos(TABLE);
		player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
		return player;
	}
}
