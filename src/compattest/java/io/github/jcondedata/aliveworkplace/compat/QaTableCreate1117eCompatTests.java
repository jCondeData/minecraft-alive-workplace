package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-e-1117-0113) for 48.1 (the Blueprint Table and Book in Copperline: "list, preview, size, materials, style
 * filter, one copper Place") with Create installed, written from the spec and create.md: the new screen draws what the
 * server sends, so for every Create-powered build (39.4, 39.8) the server must send a materials column that names real
 * items, Create's own machines among them, a preview that draws Create's blocks, and every style the rail offers must
 * itself have details and a preview; and the copper Get Blueprint button must hand each one out for one blank.
 */
public class QaTableCreate1117eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:build_area";
	private static final BlockPos TABLE = new BlockPos(3, 2, 3);

	/**
	 * Every Create build at the Table: its details list only real, non-air items with counts above zero and none twice,
	 * at least one of them Create's; its preview draws at least one of Create's blocks; and every style the rail lists
	 * for it has details and a preview of its own (a style that answers nothing would be a dead button on the rail).
	 */
	//$ gametest_ticks 'FabricGameTest.EMPTY_STRUCTURE' '600'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 600)
	public void everyCreateBuildHasMaterialsAPreviewAndLiveStyles(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry entry : StarterBlueprints.CREATE_ONLY) {
			ResourceLocation id = entry.id();
			Optional<TablePayloads.Details> d = TableServer.details(helper.getLevel(), null, id);
			if (d.isEmpty()) {
				problems.add(id + " has no details at the Table");
				continue;
			}
			Set<ResourceLocation> seen = new HashSet<>();
			boolean create = false;
			for (TablePayloads.Material m : d.get().materials()) {
				if (m.count() <= 0 || !BuiltInRegistries.ITEM.containsKey(m.item()) || BuiltInRegistries.ITEM.get(m.item()) == Items.AIR || !seen.add(m.item())) {
					problems.add(id + ": material " + m.item() + " x" + m.count());
				}
				create |= m.item().getNamespace().equals("create");
			}
			if (!create) {
				problems.add(id + "'s materials name nothing of Create's: " + seen);
			}
			Optional<TablePayloads.Preview> p = TableServer.preview(server, id);
			if (p.isEmpty() || p.get().blocks().length == 0) {
				problems.add(id + " has no preview");
			} else {
				boolean createBlock = false;
				for (int state : p.get().palette()) {
					BlockState s = Block.stateById(state);
					createBlock |= BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals("create");
				}
				if (!createBlock) {
					problems.add(id + "'s preview draws none of Create's blocks");
				}
			}
			for (TablePayloads.StyleChoice style : d.get().styles()) {
				ResourceLocation styled = BlueprintStyles.styled(id, style.name());
				if (TableServer.details(helper.getLevel(), null, styled).isEmpty() || TableServer.preview(server, styled).isEmpty()) {
					problems.add(id + " in style " + style.name() + " has no details or preview");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The copper Get Blueprint for each Create build, as the screen sends it: none is refused (sold-only, unknown or
	 * locked) and each blueprint handed out names its build. (The mock player is creative, so blanks aren't spent here;
	 * the main suite's table tests check the blank.)
	 */
	//$ gametest_batch AREA '"qaTableCreate1117e"'
	@GameTest(template = AREA, batch = "qaTableCreate1117e")
	public void getBlueprintHandsOutEveryCreateBuild(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos table = helper.absolutePos(TABLE);
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.getInventory().clearContent();
		p.moveTo(Vec3.atBottomCenterOf(table).add(1.5, 0, 0));
		List<StarterBlueprints.Entry> builds = StarterBlueprints.CREATE_ONLY;
		p.getInventory().setItem(0, new ItemStack(ModItems.BLANK_BLUEPRINT, builds.size()));
		p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry entry : builds) {
			if (!TableServer.take(p, table, entry.id())) {
				problems.add(entry.id() + " was refused");
			}
		}
		Set<ResourceLocation> carried = new HashSet<>();
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			BlueprintItem.data(p.getInventory().getItem(i)).ifPresent(d -> carried.add(BlueprintStyles.base(d.structure())));
		}
		for (StarterBlueprints.Entry entry : builds) {
			if (!carried.contains(entry.id())) {
				problems.add(entry.id() + " isn't carried");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}
