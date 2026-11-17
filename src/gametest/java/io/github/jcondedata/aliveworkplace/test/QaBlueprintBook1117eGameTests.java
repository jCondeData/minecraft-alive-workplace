package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintTableBlockEntity;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.github.jcondedata.aliveworkplace.ui.BlueprintBookLayout;
import io.github.jcondedata.aliveworkplace.ui.BlueprintBookLayout.Boxes;
import io.github.jcondedata.aliveworkplace.ui.BlueprintBookLayout.View;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-e-1117-0113) for 48.1, the Blueprint Table and the Blueprint Book in Copperline, written from the spec ("list,
 * preview, size, materials, style filter, one copper Place") and the wiki (blueprint-library.md, copperline.md): the
 * screen is new, the server side of its buttons is not, so these go through the packets the new buttons send and the
 * real library the new rail and list draw. Covered: the style rail holds every style the real library offers for any
 * listed build, at the GUI scales players use and on the compact boundary; a style the build doesn't have, a blueprint
 * that no longer exists and a Get Blueprint from too far are refused with the blank kept; a shelf holding a blueprint
 * that no longer exists still lists it, survives a save and reload and gives it back; every listed build's materials
 * name real items with positive counts, and a missing id gets no details at all.
 */
public class QaBlueprintBook1117eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos TABLE = new BlockPos(3, 2, 3);
	private static final ResourceLocation COTTAGE = StarterBlueprints.STARTER_COTTAGE.id();
	private static final ResourceLocation GONE = AliveWorkplace.id("qa_1117e_deleted_build");
	/** Screens in GUI pixels: 1280x720 at scales 3 and 4, 1920x1080 at 4, the smallest (320x180), and the compact boundary. */
	private static final int[][] SCREENS = {{427, 240}, {320, 180}, {480, 270}, {400, 220}, {399, 240}, {400, 219}, {640, 360}, {960, 540}};

	private static boolean within(int[] inner, int[] outer) {
		return inner[0] >= outer[0] && inner[1] >= outer[1] && inner[0] + inner[2] <= outer[0] + outer[2] && inner[1] + inner[3] <= outer[1] + outer[3];
	}

	private static String box(int[] b) {
		return "[" + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + "]";
	}

	private static int carried(ServerPlayer p, ResourceLocation id) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (BlueprintItem.data(s).map(d -> BlueprintStyles.base(d.structure()).equals(id)).orElse(false)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static int blanks(ServerPlayer p) {
		return p.getInventory().countItem(ModItems.BLANK_BLUEPRINT);
	}

	private static QaStageFiveGameTests.Client clientAtTable(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		QaStageFiveGameTests.Client c = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		c.player().getInventory().clearContent();
		c.player().getInventory().selected = 0;
		c.player().moveTo(Vec3.atBottomCenterOf(helper.absolutePos(TABLE)).add(1.5, 0, 0));
		c.sent();
		c.seen().clear();
		return c;
	}

	/**
	 * The style rail draws "As drawn" plus every style the server offers for the chosen build, one cell each, with no
	 * scrolling: for the build with the most styles in the real library, every cell must sit inside the rail, be big
	 * enough to read (10 px) and be the one a click on its middle picks, at every GUI scale and on both sides of the
	 * compact boundary.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyStyleOfTheMostStyledBuildFitsTheRail(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ResourceLocation most = null;
		int max = 0;
		for (TablePayloads.Entry e : TableServer.listing(server)) {
			int n = TableServer.styles(server, e.id()).size();
			if (n > max) {
				max = n;
				most = e.id();
			}
		}
		helper.assertTrue(most != null && max > 0, "no listed build offers any style, so the rail can't be checked");
		int count = max + 1;
		List<String> problems = new ArrayList<>();
		for (int[] s : SCREENS) {
			int[] tabs = {60, 70};
			Boxes b = BlueprintBookLayout.of(s[0], s[1], 90, 64, tabs, View.LIBRARY, new int[]{80, 80});
			int[][] cells = BlueprintBookLayout.styleEntries(b, count);
			for (int i = 0; i < cells.length; i++) {
				int[] c = cells[i];
				if (!within(c, b.rail()) || c[3] < 10 || CopperLayout.at(cells, c[0] + c[2] / 2.0, c[1] + c[3] / 2.0) != i) {
					problems.add(s[0] + "x" + s[1] + ": style " + i + " of " + count + " " + box(c) + " is outside the rail " + box(b.rail())
						+ ", under 10 px or not clickable");
					break;
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), most + " has " + max + " styles; " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Get Blueprint (the copper button) with a style id the build doesn't have, as a stale or forged click would send:
	 * refused with the "can't be built in that style" line naming the build, the blank kept, nothing handed out. Then the
	 * same build as drawn is handed out for one blank.
	 */
	//$ gametest_batch AREA '"qaBook1117eStyle"'
	@GameTest(template = AREA, batch = "qaBook1117eStyle")
	public void aStyleTheBuildLacksIsRefusedAndKeepsTheBlank(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = clientAtTable(helper);
		ServerPlayer p = c.player();
		BlockPos table = helper.absolutePos(TABLE);
		p.getInventory().setItem(5, new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
		ResourceLocation forged = BlueprintStyles.styled(COTTAGE, "qa_no_such_style");
		c.send(new TablePayloads.Take(table, forged));
		helper.assertTrue(blanks(p) == 2 && carried(p, COTTAGE) == 0, "a style that doesn't exist: blanks " + blanks(p) + ", cottages " + carried(p, COTTAGE));
		Component line = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.table.style_unsupported");
		helper.assertTrue(line != null, "not told the style is refused: " + QaStageFiveGameTests.keys(c.seen()));
		String english = JewellerGameTests.english(line);
		helper.assertTrue(english.endsWith(" can't be built in that style.") && !english.contains("aliveworkplace:") && !english.contains("%"),
			"the refusal reads: " + english);
		c.seen().clear();
		c.send(new TablePayloads.Take(table, COTTAGE));
		helper.assertTrue(blanks(p) == 1 && carried(p, COTTAGE) == 1, "as drawn: blanks " + blanks(p) + ", cottages " + carried(p, COTTAGE));
		helper.succeed();
	}

	/**
	 * A row picked before the library changed (a build deleted from the blueprints folder): Get Blueprint for it is refused
	 * with "Unknown blueprint", the blank kept; RequestDetails for it sends nothing the screen would draw as a build.
	 * And a Get Blueprint sent from 20 blocks away is refused too.
	 */
	//$ gametest_batch AREA '"qaBook1117eStale"'
	@GameTest(template = AREA, batch = "qaBook1117eStale")
	public void aStaleOrFarGetBlueprintHandsNothingOut(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = clientAtTable(helper);
		ServerPlayer p = c.player();
		BlockPos table = helper.absolutePos(TABLE);
		p.getInventory().setItem(0, new ItemStack(ModItems.BLANK_BLUEPRINT, 3));
		c.send(new TablePayloads.Take(table, GONE));
		helper.assertTrue(blanks(p) == 3 && carried(p, GONE) == 0, "a deleted build: blanks " + blanks(p) + ", handed out " + carried(p, GONE));
		helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.blueprint.unknown"), "not told it's unknown: " + QaStageFiveGameTests.keys(c.seen()));
		c.sent();
		c.send(new TablePayloads.RequestDetails(GONE));
		List<TablePayloads.Details> details = c.sent(TablePayloads.Details.class);
		helper.assertTrue(details.stream().allMatch(d -> d.materials().isEmpty() && d.styles().isEmpty()),
			"details of a deleted build list materials or styles: " + details);
		helper.assertTrue(TableServer.details(helper.getLevel(), null, GONE).isEmpty() || TableServer.details(helper.getLevel(), null, GONE).get().materials().isEmpty(),
			"the server makes up materials for a build that doesn't exist");
		p.moveTo(Vec3.atBottomCenterOf(table).add(20, 0, 0));
		c.send(new TablePayloads.Take(table, COTTAGE));
		helper.assertTrue(blanks(p) == 3 && carried(p, COTTAGE) == 0, "from 20 blocks away: blanks " + blanks(p) + ", cottages " + carried(p, COTTAGE));
		helper.succeed();
	}

	/**
	 * The Stored tab with a blueprint whose build was deleted (an old save after a pack update): the tab still lists it with
	 * its count, the shelf keeps it through a save and reload (an old-shape tag without the "stored" list loads empty), and
	 * Take One Out gives it back, so nothing a player stored is lost.
	 */
	//$ gametest_batch AREA '"qaBook1117eShelf"'
	@GameTest(template = AREA, batch = "qaBook1117eShelf")
	public void aStoredBlueprintOfADeletedBuildIsListedKeptAndGivenBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = clientAtTable(helper);
		ServerPlayer p = c.player();
		BlockPos table = helper.absolutePos(TABLE);
		ItemStack gone = BlueprintItem.create(GONE, null);
		p.getInventory().setItem(0, gone.copy());
		p.getInventory().setItem(1, gone.copy());
		p.getInventory().setItem(2, BlueprintItem.create(COTTAGE, null));
		c.send(new TablePayloads.Store(table, true));
		TablePayloads.Open open = TableServer.openPayload(p, table);
		int listedGone = open.stored().stream().filter(s -> ItemStack.isSameItemSameComponents(s.stack(), gone)).mapToInt(TablePayloads.Stored::count).sum();
		helper.assertTrue(open.storedCount() == 3 && listedGone == 2 && open.stored().size() == 2,
			"the Stored tab lists " + open.stored() + " (" + open.storedCount() + " in all); expected the deleted build x2 and the cottage");
		var registries = helper.getLevel().registryAccess();
		BlockEntity be = helper.getBlockEntity(TABLE);
		helper.assertTrue(be instanceof BlueprintTableBlockEntity, "no shelf");
		CompoundTag tag = be.saveWithFullMetadata(registries);
		BlockEntity back = BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), tag, registries);
		helper.assertTrue(back instanceof BlueprintTableBlockEntity shelf && shelf.count() == 3 && shelf.groups().size() == 2,
			"after a reload the shelf holds " + (back instanceof BlueprintTableBlockEntity s ? s.groups() : back));
		CompoundTag old = tag.copy();
		old.remove("stored");
		BlockEntity oldBack = BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), old, registries);
		helper.assertTrue(oldBack instanceof BlueprintTableBlockEntity o && o.count() == 0, "a table saved before the shelf didn't load empty: " + oldBack);
		c.seen().clear();
		c.send(new TablePayloads.TakeStored(table, gone.copy()));
		helper.assertTrue(carried(p, GONE) == 1, "Take One Out of the deleted build gave " + carried(p, GONE) + " (saw " + QaStageFiveGameTests.keys(c.seen()) + ")");
		helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.table.took_out"), "no 'Took ... out' line: " + QaStageFiveGameTests.keys(c.seen()));
		helper.succeed();
	}

	/**
	 * The materials column and its "Not carried" word name items: for every unlocked build the library lists, the details
	 * name only real, non-air items, each with a count above zero and none twice, and the size the list shows is the
	 * blueprint's own (positive on every axis). The "Not carried" lines read in English with a real item name.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyListedBuildNamesRealMaterials(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (TablePayloads.Entry e : TableServer.listing(server)) {
			if (e.sizeX() <= 0 || e.sizeY() <= 0 || e.sizeZ() <= 0) {
				problems.add(e.id() + " lists size " + e.sizeX() + "x" + e.sizeY() + "x" + e.sizeZ());
			}
			Optional<TablePayloads.Details> d = TableServer.details(helper.getLevel(), null, e.id());
			if (d.isEmpty()) {
				continue;
			}
			checked++;
			java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
			for (TablePayloads.Material m : d.get().materials()) {
				var item = BuiltInRegistries.ITEM.get(m.item());
				if (m.count() <= 0 || item == Items.AIR || !BuiltInRegistries.ITEM.containsKey(m.item()) || !seen.add(m.item())) {
					problems.add(e.id() + ": " + m.item() + " x" + m.count());
				}
			}
			if (problems.size() > 10) {
				break;
			}
		}
		helper.assertTrue(checked > 10, "only " + checked + " builds had details");
		helper.assertTrue(problems.isEmpty(), "materials the column can't name: " + String.join("; ", problems));
		String one = Component.translatable("screen.aliveworkplace.table.carried_missing_one", Items.OAK_PLANKS.getDescription()).getString();
		String more = Component.translatable("screen.aliveworkplace.table.carried_missing_more", Items.OAK_PLANKS.getDescription()).getString();
		helper.assertTrue("Not carried: Oak Planks".equals(one) && "Not carried: Oak Planks and more".equals(more)
			&& "You carry all of it".equals(Component.translatable("screen.aliveworkplace.table.carried_all").getString()),
			"the materials' word reads: " + one + " / " + more);
		helper.succeed();
	}
}
