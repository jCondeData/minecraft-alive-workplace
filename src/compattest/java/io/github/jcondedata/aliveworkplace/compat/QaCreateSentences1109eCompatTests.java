package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.table.CreateSchematics;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-11-09), Create's sentences no test read word for word yet (langcheck: their keys appear in no test,
 * and no test held their rendered text), from 39.2, 39.3 and 39.6's Done when ("every new sentence a player reads"):
 * <ul>
 * <li>39.3: a farmer milling at a turning village millstone says, in this order, "Machine work: fetching Wheat for the
 * Millstone", "Machine work: working the Millstone" and "Machine work: bringing what the Millstone made to the store",
 * and the flour reaches the store;</li>
 * <li>39.2: a Mechanic with a hole in a finished build and no part for it says "Mechanic: needs Shaft to mend the
 * &lt;build's name&gt;", the build named as the table names it;</li>
 * <li>39.6: a written Schematic imported at the table says "Create schematic &lt;file&gt; (uploaded by &lt;player&gt;):
 * Imported &lt;id&gt; (x × y × z)", with the one-block and the many-blocks line for blocks of mods not installed.</li>
 * </ul>
 * Also the Mechanic loaded holding the part a hole needs with a full store (kills a survivor of lane E's local mutation).
 */
public class QaCreateSentences1109eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos MILL = new BlockPos(6, 3, 2);

	/** Every line {@code villager} says from now on, as "title: line", in the order first said. */
	private static List<String> listen(GameTestHelper helper, Villager villager) {
		Set<String> said = new LinkedHashSet<>();
		List<String> view = new ArrayList<>();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(villager, helper.getLevel().getGameTime());
			if (e != null && said.add(e.title().getString() + ": " + e.line().getString())) {
				view.add(e.title().getString() + ": " + e.line().getString());
			}
		});
		return view;
	}

	/** 39.3: the farmer's three lines on a milling trip, in order, and the flour in the store. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1109_mill_trip_says"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1109_mill_trip_says")
	public void aFarmerSaysEachStepOfAMillingTrip(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		MachineWorkCompatTests.villageWorkshop(helper);
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.WHEAT, 20));
		helper.setBlock(MILL.below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
		helper.setBlock(MILL, MechanicCompatTests.create("millstone").defaultBlockState());
		helper.setBlock(STATION, Blocks.COMPOSTER);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(helper.getLevel(), farmer, helper.absolutePos(STATION), VillagerProfession.FARMER);
		helper.assertTrue(farmer.getVillagerData().getProfession() == VillagerProfession.FARMER, "setup: no farmer at the composter");
		helper.setDayTime(2000);
		Item flour = MechanicCompatTests.createItem("wheat_flour");
		List<String> said = listen(helper, farmer);
		String fetch = "Machine work: fetching Wheat for the Millstone";
		String feed = "Machine work: working the Millstone";
		String back = "Machine work: bringing what the Millstone made to the store";
		helper.succeedWhen(() -> {
			helper.assertTrue(MechanicCompatTests.count(chest, flour) > 0, "no flour in the store yet; the farmer said " + said);
			helper.assertTrue(said.contains(fetch) && said.contains(feed) && said.contains(back), "the farmer said " + said);
			helper.assertTrue(said.indexOf(fetch) < said.indexOf(feed) && said.indexOf(feed) < said.indexOf(back),
				"the farmer's lines are out of order: " + said);
			for (String line : said) {
				helper.assertTrue(!line.contains("%") && !line.contains("aliveworkplace.") && !line.contains("create."),
					"a line with a raw key or placeholder: " + line);
			}
			MachineWorkCompatTests.clearMachines(helper);
		});
	}

	/** 39.2: a hole and no part: the Mechanic names the part and the build as the table names it. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1109_mechanic_needs_says"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1109_mechanic_needs_says")
	public void aMechanicWithoutThePartSaysWhatAndForWhichBuild(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		List<BlockPos> line = MechanicCompatTests.finishedLine(helper);
		ResourceLocation built = null;
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedIn(level)) {
			if (f.structure().getPath().startsWith("compat_test/mechanic_line_")) {
				built = f.structure();
			}
		}
		helper.assertTrue(built != null, "setup: the finished line isn't recorded");
		MechanicCompatTests.chest(helper, new ItemStack(Items.WHEAT, 4));
		Villager mechanic = MechanicCompatTests.mechanic(helper);
		helper.setBlock(line.get(0), Blocks.AIR);
		helper.setDayTime(2000);
		String expected = "Mechanic: needs Shaft to mend the " + Blueprints.displayName(built).getString();
		List<String> said = listen(helper, mechanic);
		helper.succeedWhen(() -> {
			helper.assertTrue(said.contains(expected), "expected \"" + expected + "\", the Mechanic said " + said);
			helper.assertTrue(helper.getBlockState(line.get(0)).isAir(), "a shaft came from nowhere");
		});
	}

	/**
	 * 39.2 (B884 kin), from lane E's local mutation of MechanicWork line 425 ('&& to ||': every part in hand stashed,
	 * even one a hole still needs): a Mechanic who comes back holding the one cogwheel a hole needs (loaded mid-mend), with
	 * the store full to the brim, puts it in the hole. It never goes on the ground while he waits saying he needs one.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1109_mechanic_holds_needed"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1109_mechanic_holds_needed")
	public void aMechanicHoldingThePartAHoleNeedsPutsItInEvenWithTheStoreFull(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Item cog = MechanicCompatTests.createItem("cogwheel");
		List<BlockPos> line = MechanicCompatTests.finishedLine(helper);
		ItemStack[] full = new ItemStack[27];
		for (int i = 0; i < full.length; i++) {
			full[i] = new ItemStack(Items.COBBLESTONE, 64);
		}
		Container store = MechanicCompatTests.chest(helper, full);
		Villager mechanic = MechanicCompatTests.mechanic(helper);
		mechanic.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(cog));
		BlockPos hole = line.get(3);
		helper.setBlock(hole, Blocks.AIR);
		helper.setDayTime(2000);
		List<String> said = listen(helper, mechanic);
		helper.succeedWhen(() -> {
			int ground = 0;
			for (net.minecraft.world.entity.item.ItemEntity drop : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				helper.getBounds().inflate(8))) {
				ground += drop.getItem().is(cog) ? drop.getItem().getCount() : 0;
			}
			helper.assertTrue(ground == 0, ground + " cogwheel(s) on the ground; he said " + said);
			helper.assertTrue(helper.getBlockState(hole).is(MechanicCompatTests.create("cogwheel")),
				"the hole isn't mended: " + helper.getBlockState(hole) + "; he holds " + mechanic.getMainHandItem() + " and said " + said);
			helper.assertTrue(mechanic.getMainHandItem().isEmpty(), "he still holds " + mechanic.getMainHandItem());
			helper.assertTrue(MechanicCompatTests.count(store, cog) == 0, "a cogwheel went into the full store");
		});
	}

	/** 39.6: the import line, word for word, with nothing unknown, one unknown block, and five of four mods. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aWrittenSchematicImportedSaysWhatCameOfIt(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String owner = "gametest_qa_e1109_says";
		ResourceLocation plain = CreateSchematics.idFor(owner, "hut.nbt");
		ResourceLocation one = CreateSchematics.idFor(owner, "shed.nbt");
		ResourceLocation many = CreateSchematics.idFor(owner, "barn.nbt");
		clear(server, owner, plain, one, many);
		try {
			Path dir = CreateSchematics.uploadedFolder(server).resolve(owner);
			writeTo(dir.resolve("hut.nbt"), structure(3, 1, List.of()));
			writeTo(dir.resolve("shed.nbt"), structure(2, 3, List.of("othermod:gizmo")));
			writeTo(dir.resolve("barn.nbt"), structure(2, 4, List.of("moda:a", "modb:b", "modc:c", "modd:d", "moda:a")));

			String hut = CreateSchematics.importItem(server, schematic("hut.nbt", owner)).message().getString();
			helper.assertTrue(hut.equals("Create schematic hut.nbt (uploaded by " + owner + "): Imported " + plain + " (3 × 1 × 3)"),
				"the plain import says: " + hut);

			String shed = CreateSchematics.importItem(server, schematic("shed.nbt", owner)).message().getString();
			String shedWant = "Create schematic shed.nbt (uploaded by " + owner + "): Imported " + one
				+ " (2 × 3 × 2). 1 block from a mod that isn't installed became air: othermod:gizmo.";
			helper.assertTrue(shed.equals(shedWant), "one unknown block: expected \"" + shedWant + "\", said \"" + shed + "\"");

			String barn = CreateSchematics.importItem(server, schematic("barn.nbt", owner)).message().getString();
			String barnStart = "Create schematic barn.nbt (uploaded by " + owner + "): Imported " + many
				+ " (2 × 4 × 2). 5 blocks from mods that aren't installed became air: ";
			helper.assertTrue(barn.startsWith(barnStart) && barn.endsWith(" and 1 more."),
				"five unknown blocks of four mods: \"" + barn + "\"");
			String names = barn.substring(barnStart.length(), barn.length() - " and 1 more.".length());
			helper.assertTrue(names.split(", ").length == 3 && Stream.of(names.split(", ")).distinct().count() == 3
				&& Stream.of(names.split(", ")).allMatch(n -> List.of("moda:a", "modb:b", "modc:c", "modd:d").contains(n)),
				"the line should name three of the four unknown blocks once each: " + names);
		} finally {
			clear(server, owner, plain, one, many);
		}
		helper.succeed();
	}

	// --- helpers (as in QaCreateSchematicFilesCompatTests) ------------------------------------------------------------

	@SuppressWarnings("unchecked")
	private static ItemStack schematic(String file, String owner) {
		ItemStack stack = new ItemStack(Lookup.value(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("create", "schematic")));
		stack.set((DataComponentType<String>) Lookup.value(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ResourceLocation.fromNamespaceAndPath("create", "schematic_file")), file);
		stack.set((DataComponentType<String>) Lookup.value(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ResourceLocation.fromNamespaceAndPath("create", "schematic_owner")), owner);
		return stack;
	}

	/**
	 * A structure file as Create saves one: {@code side} × {@code height} × {@code side}, stone, except the first blocks of
	 * the bottom layer, which are {@code unknown}'s ids in order (blocks of mods not installed).
	 */
	private static CompoundTag structure(int side, int height, List<String> unknown) {
		CompoundTag tag = new CompoundTag();
		tag.put("size", ints(side, height, side));
		ListTag palette = new ListTag();
		List<String> names = new ArrayList<>(List.of("minecraft:stone"));
		for (String id : unknown) {
			if (!names.contains(id)) {
				names.add(id);
			}
		}
		for (String name : names) {
			CompoundTag entry = new CompoundTag();
			entry.putString("Name", name);
			palette.add(entry);
		}
		tag.put("palette", palette);
		ListTag blocks = new ListTag();
		int i = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < side; x++) {
				for (int z = 0; z < side; z++) {
					CompoundTag b = new CompoundTag();
					b.put("pos", ints(x, y, z));
					b.putInt("state", i < unknown.size() ? names.indexOf(unknown.get(i)) : 0);
					blocks.add(b);
					i++;
				}
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

	private static void writeTo(Path file, CompoundTag tag) {
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
}
