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
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane b, 2026-11-22), from the spec of B1067-B1070 (ROADMAP; wiki create.md: "A built Create block is a freshly
 * placed one, from a scan, an uploaded file or a Create schematic alike"). Every case is a hand-written file a player
 * uploads at the Table (BlueprintImporter, folder "uploads/<player>"), built by a real builder from a chest holding the
 * material list as plain items:
 * - a Blaze Burner keeps its goggles and train hat (looks) but never its creative fuel or heat (B1067);
 * - a Netherite Backtank asks for the Netherite Backtank a player wears and is built (B1068);
 * - a Backtank full of air, with Capacity and enchantments in its data, is built empty and gives a plain backtank (B1069);
 * - a Clipboard with unbreakable, lore, a glint and epic rarity as components drops a plain clipboard (B1070);
 * - vanilla blocks too: a banner keeps its pattern but not its components, a chest carries none (B1070);
 * - and all of it the same across a save and reload of the build site mid-job.
 */
public class QaCreateFreshBlocks1122bCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	/** The file's blocks are drawn here, two apart along x. */
	private static final BlockPos SRC = new BlockPos(10, 2, 22);
	private static final BlockPos CONTROL = new BlockPos(22, 2, 4);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 10);

	private static Block block(String id) {
		Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
		if (b == Blocks.AIR) {
			throw new GameTestAssertException("(setup) no " + id);
		}
		return b;
	}

	// ---------------------------------------------------------------- B1067

	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_fresh_blocks_1122b_burner"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_fresh_blocks_1122b_burner")
	public void aBurnerFromAFileKeepsItsGogglesAndHatButNoFuel(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, burnerWithBlaze());
		edit(helper, SRC, t -> {
			t.putBoolean("isCreative", true);
			t.putBoolean("Goggles", true);
		});
		helper.setBlock(SRC.east(2), burnerWithBlaze());
		edit(helper, SRC.east(2), t -> {
			t.putInt("fuelLevel", 2);
			t.putInt("burnTimeRemaining", 1_000_000);
			t.putBoolean("TrainHat", true);
		});
		CompoundTag a = data(helper, SRC);
		CompoundTag b = data(helper, SRC.east(2));
		helper.assertTrue(a.getBoolean("isCreative") && a.getBoolean("Goggles") && b.getInt("burnTimeRemaining") > 0 && b.getBoolean("TrainHat"),
			"(setup) the burners didn't take the data, so the test can't tell: " + a + " / " + b);
		CompoundTag file = draw(helper, 3, null);
		Job job = start(helper, file, "burner", true);
		helper.succeedWhen(() -> {
			job.assertDone();
			CompoundTag first = builtData(helper, ORIGIN, block("create:blaze_burner"));
			CompoundTag second = builtData(helper, ORIGIN.east(2), block("create:blaze_burner"));
			List<String> wrong = new ArrayList<>();
			if (first.getBoolean("isCreative") || second.getBoolean("isCreative")) {
				wrong.add("built creative");
			}
			if (first.getInt("fuelLevel") != 0 || second.getInt("fuelLevel") != 0 || first.getInt("burnTimeRemaining") > 0
				|| second.getInt("burnTimeRemaining") > 0) {
				wrong.add("built with fuel (" + first.getInt("fuelLevel") + "/" + first.getInt("burnTimeRemaining") + ", "
					+ second.getInt("fuelLevel") + "/" + second.getInt("burnTimeRemaining") + ")");
			}
			if (!first.getBoolean("Goggles")) {
				wrong.add("the first lost its goggles (a look, which stays)");
			}
			if (!second.getBoolean("TrainHat")) {
				wrong.add("the second lost its train hat (a look, which stays)");
			}
			helper.assertTrue(wrong.isEmpty(), "Blaze Burners built from an uploaded file, for " + job.list + ": " + wrong);
		});
	}

	// ---------------------------------------------------------------- B1068 + B1069

	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_fresh_blocks_1122b_netherite"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_fresh_blocks_1122b_netherite")
	public void aNetheriteBacktankFromAFileAsksForTheWornOneAndIsBuiltPlain(GameTestHelper helper) {
		backtank(helper, "create:netherite_backtank", false);
	}

	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_fresh_blocks_1122b_air"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_fresh_blocks_1122b_air")
	public void aCopperBacktankFullOfAirIsBuiltEmptyAndGivesAPlainOne(GameTestHelper helper) {
		backtank(helper, "create:copper_backtank", false);
	}

	/** The same as the copper one, the build site saved and reloaded while it waits for its materials. */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_fresh_blocks_1122b_air_reload"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_fresh_blocks_1122b_air_reload")
	public void aCopperBacktankFullOfAirIsBuiltEmptyAcrossASaveAndReload(GameTestHelper helper) {
		backtank(helper, "create:copper_backtank", true);
	}

	private static void backtank(GameTestHelper helper, String id, boolean reload) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Block tank = block(id);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		// set down from an enchanted item, then given air and capacity by hand
		setDownEnchanted(helper, SRC, tank);
		edit(helper, SRC, t -> {
			t.putInt("Air", 900);
			t.putInt("CapacityEnchantment", 3);
		});
		CompoundTag src = data(helper, SRC);
		helper.assertTrue(src.getInt("Air") == 900, "(setup) the backtank didn't keep its air, so the test can't tell: " + src);
		// control: a backtank loaded with exactly this data gives an enchanted backtank back
		helper.setBlock(CONTROL, tank.defaultBlockState());
		helper.getBlockEntity(CONTROL).loadWithComponents(src.copy(), level.registryAccess());
		ItemStack control = takeOnBack(helper, player, CONTROL);
		helper.assertTrue(control.getEnchantments().size() == 2, "(setup) the control backtank gave " + control + " "
			+ control.getEnchantments() + ", so the test can't tell");
		player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);

		CompoundTag file = draw(helper, 1, null);
		Job job = start(helper, file, "tank_" + reload, !reload);
		Item worn = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
		helper.assertTrue(job.asked.containsKey(worn), "the material list for an uploaded " + id + " is " + job.list
			+ ": it doesn't ask for the " + id + " a player wears");
		helper.assertTrue(job.asked.keySet().stream().noneMatch(i -> BuiltInRegistries.ITEM.getKey(i).getPath().contains("placeable")),
			"the material list for an uploaded " + id + " asks for Create's technical placeable item: " + job.list);
		if (reload) {
			job.reloadThenFill(helper, 100);
		}
		ItemStack[] got = {null};
		CompoundTag[] built = {null};
		helper.succeedWhen(() -> {
			job.assertDone();
			if (got[0] == null) {
				built[0] = builtData(helper, ORIGIN, tank);
				got[0] = takeOnBack(helper, player, ORIGIN);
			}
			CompoundTag d = built[0];
			helper.assertTrue(d.getInt("Air") == 0, "the " + id + " built from an uploaded file holds air " + d.getInt("Air")
				+ " (a fresh one holds none): " + d);
			helper.assertTrue(d.getInt("CapacityEnchantment") == 0, "the built " + id + " keeps Capacity " + d.getInt("CapacityEnchantment"));
			// Create saves an empty "Components" on a fresh backtank too: empty is plain
			helper.assertTrue(d.getCompound("Components").isEmpty() && d.getCompound("components").isEmpty(), "the built " + id
				+ " keeps item components: " + d);
			ItemStack taken = got[0];
			helper.assertTrue(taken.is(worn), "taking the built " + id + " gave " + taken);
			helper.assertTrue(taken.getEnchantments().isEmpty(), "taking the built " + id + " gave one enchanted " + taken.getEnchantments());
			helper.assertTrue(airOf(taken) == 0, "taking the built " + id + " gave one with air " + airOf(taken));
			player.discard();
		});
	}

	// ---------------------------------------------------------------- B1070

	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_fresh_blocks_1122b_clipboard"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_fresh_blocks_1122b_clipboard")
	public void aClipboardFromAFileWithUnbreakableAndLoreDropsAPlainOne(GameTestHelper helper) {
		clipboard(helper, false);
	}

	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_fresh_blocks_1122b_clipboard_reload"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_fresh_blocks_1122b_clipboard_reload")
	public void aClipboardFromAFileDropsAPlainOneAcrossASaveAndReload(GameTestHelper helper) {
		clipboard(helper, true);
	}

	private static void clipboard(GameTestHelper helper, boolean reload) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Block clipboard = block("create:clipboard");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		helper.setBlock(SRC, onTheFloor(clipboard));
		Tag components = loot(level);
		CompoundTag file = draw(helper, 1, entries -> entries.get(0).put("components", components.copy()));
		// control: a clipboard holding this data drops a clipboard with them
		helper.setBlock(CONTROL, onTheFloor(clipboard));
		helper.getBlockEntity(CONTROL).loadWithComponents(nbtAt(file, 0).copy(), level.registryAccess());
		String control = describe(breakAndGather(helper, player, CONTROL));
		helper.assertTrue(control.contains("unbreakable") && control.contains("lore"), "(setup) the control clipboard dropped "
			+ control + ", so the test can't tell");
		Job job = start(helper, file, "clip_" + reload, !reload);
		if (reload) {
			job.reloadThenFill(helper, 100);
		}
		String[] got = {null};
		helper.succeedWhen(() -> {
			job.assertDone();
			if (got[0] == null) {
				builtData(helper, ORIGIN, clipboard);
				got[0] = describe(breakAndGather(helper, player, ORIGIN));
			}
			helper.assertTrue(got[0].contains("clipboard"), "breaking the built clipboard dropped no clipboard: " + got[0]);
			helper.assertTrue(!got[0].contains("unbreakable") && !got[0].contains("lore") && !got[0].contains("glint")
				&& !got[0].contains("epic") && !got[0].contains("sharpness"),
				"breaking the clipboard built from an uploaded file dropped " + got[0] + ": the file's components handed out");
			player.discard();
		});
	}

	/** Vanilla block entities from a file with "components": a banner (its pattern stays) and a named chest. */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_fresh_blocks_1122b_vanilla"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_fresh_blocks_1122b_vanilla")
	public void aBannerAndAChestFromAFileCarryNoComponentsButKeepTheirPattern(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		helper.setBlock(SRC, Blocks.RED_BANNER);
		edit(helper, SRC, t -> {
			ListTag patterns = new ListTag();
			CompoundTag p = new CompoundTag();
			p.putString("pattern", "minecraft:stripe_top");
			p.putString("color", "black");
			patterns.add(p);
			t.put("patterns", patterns);
		});
		helper.assertTrue(!data(helper, SRC).getList("patterns", Tag.TAG_COMPOUND).isEmpty(), "(setup) the banner took no pattern");
		helper.setBlock(SRC.east(2), Blocks.CHEST);
		edit(helper, SRC.east(2), t -> t.putString("CustomName", "\"Bob's chest\""));
		Tag components = loot(level);
		CompoundTag file = draw(helper, 3, entries -> {
			for (CompoundTag e : entries) {
				e.put("components", components.copy());
			}
		});
		Job job = start(helper, file, "vanilla", true);
		String[] dropped = {null};
		helper.succeedWhen(() -> {
			job.assertDone();
			helper.assertTrue(helper.getBlockState(ORIGIN).is(Blocks.RED_BANNER), "no banner built: " + helper.getBlockState(ORIGIN));
			helper.assertTrue(helper.getBlockState(ORIGIN.east(2)).is(Blocks.CHEST), "no chest built: " + helper.getBlockState(ORIGIN.east(2)));
			if (dropped[0] == null) {
				List<String> wrong = new ArrayList<>();
				for (int dx : new int[] {0, 2}) {
					BlockEntity be = helper.getBlockEntity(ORIGIN.east(dx));
					DataComponentMap kept = be.components();
					if (kept.has(DataComponents.LORE) || kept.has(DataComponents.UNBREAKABLE) || kept.has(DataComponents.ENCHANTMENTS)
						|| kept.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) || kept.has(DataComponents.RARITY)) {
						wrong.add(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath() + " keeps " + kept);
					}
				}
				helper.assertTrue(wrong.isEmpty(), "vanilla blocks built from an uploaded file keep its item components: " + wrong);
				CompoundTag banner = helper.getBlockEntity(ORIGIN).saveWithoutMetadata(level.registryAccess());
				helper.assertTrue(!banner.getList("patterns", Tag.TAG_COMPOUND).isEmpty(), "the built banner lost its pattern (a look, which"
					+ " stays): " + banner);
				dropped[0] = describe(breakAndGather(helper, player, ORIGIN)) + describe(breakAndGather(helper, player, ORIGIN.east(2)));
			}
			helper.assertTrue(dropped[0].contains("red_banner") && dropped[0].contains("chest"), "breaking the built banner and chest dropped " + dropped[0]);
			helper.assertTrue(!dropped[0].contains("unbreakable") && !dropped[0].contains("lore") && !dropped[0].contains("glint")
				&& !dropped[0].contains("epic") && !dropped[0].contains("sharpness"),
				"breaking the banner and chest built from an uploaded file dropped " + dropped[0]);
			player.discard();
		});
	}

	// ---------------------------------------------------------------- the job

	private static final class Job {
		final ServerLevel level;
		final BuildSite site;
		final Map<Item, Integer> asked;
		final String list;
		final Container chest;

		Job(ServerLevel level, BuildSite site, Map<Item, Integer> asked, Container chest) {
			this.level = level;
			this.site = site;
			this.asked = asked;
			this.chest = chest;
			this.list = asked.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey())).toList().toString();
		}

		void fill() {
			int slot = 0;
			for (Map.Entry<Item, Integer> e : asked.entrySet()) {
				chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
			}
		}

		/** The site, waiting for materials, is saved and loaded back at {@code delay}; then the chest is filled. */
		void reloadThenFill(GameTestHelper helper, int delay) {
			helper.runAfterDelay(delay, () -> {
				BuildSite live = BuildSiteManager.get(level).get(site.id());
				helper.assertTrue(live != null, "the build finished with an empty chest");
				var registries = level.registryAccess();
				level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
				helper.assertTrue(BuildSiteManager.get(level).get(site.id()) != null, "the build site didn't survive a save and reload");
				fill();
			});
		}

		void assertDone() {
			BuildSite live = BuildSiteManager.get(level).get(site.id());
			if (live != null) {
				throw new GameTestAssertException("still building: " + live.stage() + " " + live.status() + " missing " + live.missing());
			}
		}
	}

	/** Uploads {@code file} and has a builder start it; {@code fill}: the chest holds the list now (else later). */
	private static Job start(GameTestHelper helper, CompoundTag file, String name, boolean fill) {
		ServerLevel level = helper.getLevel();
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
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/qa_fresh_1122b", name + "_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
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
		helper.setBlock(CHEST, Blocks.CHEST);
		Job job = new Job(level, site, site.plan(level).materials(), helper.getBlockEntity(CHEST));
		if (fill) {
			job.fill();
		}
		return job;
	}

	/**
	 * Draws the {@code length} blocks from {@code SRC} east into a structure file (the game's own format), clears them,
	 * and lets {@code edit} change each block entity's saved data in the file, in order along x.
	 */
	private static CompoundTag draw(GameTestHelper helper, int length, Consumer<List<CompoundTag>> edit) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(length, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int dx = 0; dx < length; dx++) {
			helper.setBlock(SRC.east(dx), Blocks.AIR);
		}
		CompoundTag file = template.save(new CompoundTag());
		if (edit != null) {
			List<CompoundTag> entries = new ArrayList<>();
			for (int x = 0; x < length; x++) {
				CompoundTag nbt = nbtAt(file, x);
				if (nbt != null) {
					entries.add(nbt);
				}
			}
			edit.accept(entries);
		}
		return file;
	}

	/** The saved data of the file's block at x, or null. */
	private static CompoundTag nbtAt(CompoundTag file, int x) {
		ListTag blocks = file.getList("blocks", Tag.TAG_COMPOUND);
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag b = blocks.getCompound(i);
			if (b.getList("pos", Tag.TAG_INT).getInt(0) == x && b.contains("nbt")) {
				return b.getCompound("nbt");
			}
		}
		return null;
	}

	private static CompoundTag data(GameTestHelper helper, BlockPos rel) {
		return helper.getBlockEntity(rel).saveWithoutMetadata(helper.getLevel().registryAccess());
	}

	private static CompoundTag builtData(GameTestHelper helper, BlockPos rel, Block expected) {
		helper.assertTrue(helper.getBlockState(rel).is(expected), "no " + BuiltInRegistries.BLOCK.getKey(expected) + " built: " + helper.getBlockState(rel));
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(rel));
		helper.assertTrue(be != null, "the built " + BuiltInRegistries.BLOCK.getKey(expected) + " has no block entity");
		return be.saveWithoutMetadata(helper.getLevel().registryAccess());
	}

	private static void edit(GameTestHelper helper, BlockPos rel, Consumer<CompoundTag> change) {
		BlockEntity be = helper.getBlockEntity(rel);
		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = be.saveWithoutMetadata(registries);
		change.accept(tag);
		be.loadWithComponents(tag, registries);
		be.setChanged();
	}

	/** Sharpness 10, unbreakable, a lore line, a forced glint and epic rarity, as saved item components. */
	private static Tag loot(ServerLevel level) {
		var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemEnchantments.Mutable sharp = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		sharp.set(enchantments.getOrThrow(Enchantments.SHARPNESS), 10);
		DataComponentMap map = DataComponentMap.builder()
			.set(DataComponents.ENCHANTMENTS, sharp.toImmutable())
			.set(DataComponents.UNBREAKABLE, new Unbreakable(true))
			.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("free loot"))))
			.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
			.set(DataComponents.RARITY, Rarity.EPIC)
			.build();
		return DataComponentMap.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), map).getOrThrow();
	}

	/** A burner with its blaze in it (an empty one has no block entity). */
	private static BlockState burnerWithBlaze() {
		return withProperty(block("create:blaze_burner").defaultBlockState(), "blaze", "smouldering");
	}

	private static BlockState onTheFloor(Block clipboard) {
		BlockState state = clipboard.defaultBlockState();
		return state.getProperties().stream().anyMatch(p -> p.getName().equals("face")) ? withProperty(state, "face", "floor") : state;
	}

	private static BlockState withProperty(BlockState state, String name, String value) {
		for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
			if (property.getName().equals(name)) {
				return with(state, property, value);
			}
		}
		throw new GameTestAssertException("(setup) " + state + " has no '" + name + "' property");
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property, String value) {
		return state.setValue(property, property.getValue(value).orElseThrow(() -> new GameTestAssertException("(setup) no " + value + " for " + property)));
	}

	/** Sets down at {@code rel} a backtank enchanted Protection IV and Mending, as a player places his own. */
	private static void setDownEnchanted(GameTestHelper helper, BlockPos rel, Block tank) {
		ServerLevel level = helper.getLevel();
		ItemStack stack = new ItemStack(tank.asItem());
		var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		stack.enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 4);
		stack.enchant(enchantments.getOrThrow(Enchantments.MENDING), 1);
		helper.setBlock(rel, tank.defaultBlockState());
		BlockPos at = helper.absolutePos(rel);
		tank.setPlacedBy(level, at, level.getBlockState(at), null, stack);
	}

	/** The survival player, with an empty hand and nothing on his back, takes the backtank at {@code rel}. */
	private static ItemStack takeOnBack(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
		BlockPos at = helper.absolutePos(rel);
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.SOUTH, at, false);
		player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
		return player.getItemBySlot(EquipmentSlot.CHEST).copy();
	}

	/** The air a backtank item holds (Create's air component, found by id), 0 if none. */
	private static int airOf(ItemStack stack) {
		int air = 0;
		for (var c : stack.getComponents()) {
			ResourceLocation key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(c.type());
			if (key != null && key.getNamespace().equals("create") && key.getPath().contains("air") && c.value() instanceof Integer i) {
				air += i;
			}
		}
		return air;
	}

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
			StringBuilder b = new StringBuilder(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
			if (!s.getEnchantments().isEmpty()) {
				b.append(" ").append(s.getEnchantments().toString().toLowerCase());
			}
			if (s.has(DataComponents.UNBREAKABLE)) {
				b.append(" unbreakable");
			}
			if (!s.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().isEmpty()) {
				b.append(" lore");
			}
			if (s.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) {
				b.append(" glint");
			}
			if (s.getOrDefault(DataComponents.RARITY, Rarity.COMMON) == Rarity.EPIC && s.getPrototype().getOrDefault(DataComponents.RARITY, Rarity.COMMON) != Rarity.EPIC) {
				b.append(" epic");
			}
			out.add(b.toString());
		}
		return out.toString();
	}
}
