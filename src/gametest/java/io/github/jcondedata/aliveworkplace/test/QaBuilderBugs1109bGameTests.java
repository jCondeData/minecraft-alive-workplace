package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintNames;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * QA (qa-lane-b-1109-2137, 2026-11-09): the neighbours of five fixed builder bugs, written from each bug's stated
 * expectation.
 * <ul>
 * <li>B874: every block a player finds but can't hold in survival (all seven infested blocks, suspicious sand and gravel,
 * a petrified oak slab single and double) asks its base material, and a PAID build from a chest holding just that list
 * finishes, each block built as drawn, the chest's items used up.</li>
 * <li>B1030/B1105: the one ground rule keeps workers and paths off every hurting ground (magma, a lit campfire, a soul
 * campfire, a cactus, a pointed dripstone, a lava cauldron); a builder's route never steps on magma, goes round it when
 * it can and finds none when magma is the only way; a worker's standing spot is never on magma. (A path into a fire on netherrack, where a
 * worker won't stand: QaBuilderBugs1109bBugTests on tests/builder-bugs-1109b.)</li>
 * <li>B1045: an uploaded stand saved invisible and invulnerable, one with a scale of 16 and no gravity, and a marker stand
 * are put up as a survival player's: visible, normal size, breakable with two punches.</li>
 * <li>B1046: uploaded file names keep their words (accents and other scripts too) but no formatting codes, obfuscation,
 * tabs or other control characters; a name of nothing but codes falls back to the id's name; names saved before the fix
 * are cleaned on load.</li>
 * </ul>
 */
public class QaBuilderBugs1109bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:build_area";

	// ------------------------------------------------------------------------------------------------ B874

	/**
	 * B874 (builder.md, Materials: "some blocks you cannot hold in survival use their base material"): a scan of every
	 * infested block, suspicious sand and gravel and a petrified oak slab (single and double) asks only base materials, and
	 * a paid builder with exactly that list in a chest builds every block back as drawn.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qa1109bItemless"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qa1109bItemless")
	public void everyUnobtainableBlockIsPaidWithItsBaseMaterialAndBuiltAsDrawn(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.INFESTED_STONE);
		s.put(1, 0, 0, Blocks.INFESTED_COBBLESTONE);
		s.put(2, 0, 0, Blocks.INFESTED_STONE_BRICKS);
		s.put(3, 0, 0, Blocks.INFESTED_MOSSY_STONE_BRICKS);
		s.put(4, 0, 0, Blocks.INFESTED_CRACKED_STONE_BRICKS);
		s.put(5, 0, 0, Blocks.INFESTED_CHISELED_STONE_BRICKS);
		s.put(6, 0, 0, Blocks.INFESTED_DEEPSLATE);
		s.put(0, 0, 2, Blocks.SUSPICIOUS_SAND);
		s.put(2, 0, 2, Blocks.SUSPICIOUS_GRAVEL);
		s.put(4, 0, 2, Blocks.PETRIFIED_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
		s.put(6, 0, 2, Blocks.PETRIFIED_OAK_SLAB.defaultBlockState());
		QaScanRebuild1102dGameTests.Built b = QaScanItemless1103GameTests.paidRoundTrip(helper, s);
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> asked = b.b().site().plan(level).materials();
		Map<Item, Integer> want = new LinkedHashMap<>();
		want.put(Items.STONE, 1);
		want.put(Items.COBBLESTONE, 1);
		want.put(Items.STONE_BRICKS, 1);
		want.put(Items.MOSSY_STONE_BRICKS, 1);
		want.put(Items.CRACKED_STONE_BRICKS, 1);
		want.put(Items.CHISELED_STONE_BRICKS, 1);
		want.put(Items.DEEPSLATE, 1);
		want.put(Items.SAND, 1);
		want.put(Items.GRAVEL, 1);
		want.put(Items.OAK_SLAB, 3);
		helper.assertTrue(asked.equals(want), "the list asks " + asked + ", not the base materials " + want);
		Container chest = QaScanItemless1103GameTests.stock(helper, asked);
		helper.succeedWhen(() -> {
			b.done(helper);
			Villager builder = level.getEntitiesOfClass(Villager.class, helper.getBounds()).stream().findFirst().orElse(null);
			List<String> left = new ArrayList<>();
			for (Item item : want.keySet()) {
				int n = QaScanItemless1103GameTests.held(chest, builder, item);
				if (n != 0) {
					left.add(n + " " + item);
				}
			}
			helper.assertTrue(left.isEmpty(), "the build is done but the builder didn't use " + left);
			QaBasementGround1104GameTests.LOGGER.info("qa 1109b B874: asked {}, all built as drawn, chest used up", asked);
		});
	}

	// ------------------------------------------------------------------------------------------------ B1030 / B1105

	/**
	 * B1105's "like campfires and other hurting blocks": no ground that burns or pricks whoever stands on it is ground
	 * for a worker or a path, each standing on stone; plain stone at the same spot is ground for both.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void noHurtingBlockIsGroundForWorkersOrPaths(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockState[] hurting = {
			Blocks.MAGMA_BLOCK.defaultBlockState(),
			Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true),
			Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true),
			Blocks.CACTUS.defaultBlockState(),
			Blocks.POINTED_DRIPSTONE.defaultBlockState(),
			Blocks.LAVA_CAULDRON.defaultBlockState(),
		};
		List<String> wrong = new ArrayList<>();
		StringBuilder all = new StringBuilder();
		for (int i = 0; i < hurting.length; i++) {
			BlockPos base = new BlockPos(1 + 2 * i, 2, 4);
			helper.setBlock(base, hurting[i].is(Blocks.CACTUS) ? Blocks.SAND.defaultBlockState() : Blocks.STONE.defaultBlockState());
			helper.setBlock(base.above(), hurting[i]);
			BlockPos abs = helper.absolutePos(base.above());
			BlockState placed = level.getBlockState(abs);
			helper.assertTrue(placed.is(hurting[i].getBlock()), "(setup) " + hurting[i] + " is " + placed);
			boolean worker = Walker.canStand(level, abs.above());
			boolean path = Paths.standOn(level, abs, placed);
			BlockPos walk = Paths.walkable(level, abs.above());
			boolean pathFeet = walk != null && walk.equals(abs.above());
			all.append(placed.getBlock().getDescriptionId()).append(worker ? "+" : "-").append(path ? "+" : "-").append(' ');
			if (worker || path || pathFeet) {
				wrong.add(placed + ": worker " + worker + ", path " + path + ", walkable " + walk);
			}
		}
		QaBasementGround1104GameTests.LOGGER.info("qa 1109b hurting grounds (worker, path): {}", all);
		helper.assertTrue(wrong.isEmpty(), "a hurting block is taken for ground: " + wrong);
		BlockPos spot = new BlockPos(1, 3, 4);
		helper.setBlock(spot, Blocks.STONE);
		BlockPos abs = helper.absolutePos(spot);
		helper.assertTrue(Walker.canStand(level, abs.above()) && Paths.standOn(level, abs, level.getBlockState(abs)), "(control) stone isn't ground");
		helper.succeed();
	}

	/**
	 * B1105 through the builder's own route (Paths.route, which lays the path from a finished build to the hall): on a
	 * raised stone causeway with a magma block in it, the route goes round the magma when a detour exists, finds no way
	 * when the magma is the only way, and walks straight over the same spot once it is stone.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aBuildersRouteNeverStepsOnMagma(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// a causeway at helper y = 4 (feet at 5), out of reach of the floor (feet at 2)
		for (int x = 1; x <= 9; x++) {
			helper.setBlock(new BlockPos(x, 4, 3), Blocks.STONE);
		}
		BlockPos magma = new BlockPos(5, 4, 3);
		helper.setBlock(magma, Blocks.MAGMA_BLOCK);
		BlockPos start = helper.absolutePos(new BlockPos(1, 5, 3));
		BlockPos goal = helper.absolutePos(new BlockPos(9, 5, 3));
		List<BlockPos> only = Paths.route(level, start, goal, null);
		helper.assertTrue(only.isEmpty(), "with magma the only way, the route steps on it: " + only);
		// a detour round it
		for (int x = 4; x <= 6; x++) {
			helper.setBlock(new BlockPos(x, 4, 4), Blocks.STONE);
		}
		List<BlockPos> round = Paths.route(level, start, goal, null);
		helper.assertFalse(round.isEmpty(), "no route round the magma block, though a detour is there");
		for (BlockPos p : round) {
			helper.assertFalse(level.getBlockState(p.below()).is(Blocks.MAGMA_BLOCK), "the route steps on magma at " + p + ": " + round);
		}
		helper.assertTrue(round.stream().anyMatch(p -> p.getZ() == helper.absolutePos(new BlockPos(0, 0, 4)).getZ()), "the route " + round + " didn't take the detour");
		// control: the same spot as stone is walked straight over
		helper.setBlock(magma, Blocks.STONE);
		List<BlockPos> straight = Paths.route(level, start, goal, null);
		helper.assertTrue(straight.contains(helper.absolutePos(magma.above())), "(control) stone in the magma's place isn't walked over: " + straight);
		helper.succeed();
	}

	/**
	 * B1105 through a worker's own spot finder (Walker.standingSpot, used by every job to choose where to stand): with a
	 * magma block right beside the target and the worker standing there, the spot chosen is never on the magma; on stone
	 * it is that very spot.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aWorkersStandingSpotIsNeverOnMagma(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos target = helper.absolutePos(new BlockPos(8, 3, 8));
		helper.setBlock(new BlockPos(8, 2, 8), Blocks.STONE);
		helper.setBlock(new BlockPos(8, 3, 8), Blocks.OAK_LOG);
		BlockPos ground = new BlockPos(7, 2, 8);
		BlockPos feet = helper.absolutePos(ground.above());
		helper.setBlock(ground, Blocks.STONE);
		BlockPos onStone = Walker.standingSpot(level, target, feet, 4.5);
		helper.assertTrue(feet.equals(onStone), "(control) the worker's own spot on stone isn't chosen: " + onStone);
		helper.setBlock(ground, Blocks.MAGMA_BLOCK);
		BlockPos chosen = Walker.standingSpot(level, target, feet, 4.5);
		helper.assertTrue(chosen != null, "no standing spot at all beside the target, though the floor is all round it");
		helper.assertFalse(level.getBlockState(chosen.below()).is(Blocks.MAGMA_BLOCK), "the worker is sent to stand on magma at " + chosen);
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------ B1045

	/**
	 * B1045's neighbours: an uploaded file whose stands carry the looks only commands give (Invisible with Invulnerable
	 * and a name; a scale of 16 with NoGravity; a Marker) next to a plain stand. Every stand the builder puts up is
	 * visible, normal size, not a marker, not invulnerable, falls, and breaks in two punches from a survival player.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1109bStands"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1109bStands")
	public void commandOnlyStandsFromAnUploadAreBuiltAsAPlayersStands(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos src = new BlockPos(20, 2, 22);
		BlockPos origin = new BlockPos(10, 2, 10);
		BlockPos bench = new BlockPos(2, 2, 2);
		for (int dx = 0; dx < 7; dx++) {
			helper.setBlock(src.east(dx), Blocks.STONE);
		}
		List<ArmorStand> made = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			made.add(helper.spawn(EntityType.ARMOR_STAND, src.east(2 * i).above()));
		}
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(src), new Vec3i(7, 2, 1), true, Blocks.STRUCTURE_VOID);
		CompoundTag file = template.save(new CompoundTag());
		ListTag entities = file.getList("entities", Tag.TAG_COMPOUND);
		helper.assertTrue(entities.size() == 4, "(setup) the file holds " + entities.size() + " stands, not 4");
		int edited = 0;
		for (int i = 0; i < entities.size(); i++) {
			CompoundTag e = entities.getCompound(i);
			CompoundTag nbt = e.getCompound("nbt");
			double x = e.getList("pos", Tag.TAG_DOUBLE).getDouble(0);
			int slot = (int) Math.floor(x) / 2; // 0: plain, 1: ghost, 2: giant, 3: marker
			if (slot == 1) {
				nbt.putBoolean("Invisible", true);
				nbt.putBoolean("Invulnerable", true);
				nbt.putString("CustomName", "\"Ghost\"");
				edited++;
			} else if (slot == 2) {
				ListTag attributes = new ListTag();
				CompoundTag scale = new CompoundTag();
				scale.putString("id", "minecraft:generic.scale");
				scale.putDouble("base", 16.0);
				attributes.add(scale);
				nbt.put("attributes", attributes);
				nbt.putBoolean("NoGravity", true);
				edited++;
			} else if (slot == 3) {
				nbt.putBoolean("Marker", true);
				nbt.putBoolean("Invisible", true);
				edited++;
			}
		}
		helper.assertTrue(edited == 3, "(setup) edited " + edited + " stands, not 3");
		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(file, out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		made.forEach(ArmorStand::discard);
		for (int dx = 0; dx < 7; dx++) {
			helper.setBlock(src.east(dx), Blocks.AIR);
		}
		ResourceLocation id;
		try {
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/qa_stander_1109b",
				"odd_stands_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
		} catch (Exception e) {
			throw new GameTestAssertException("(setup) the upload didn't import: " + e);
		}
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, bench.east());
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		AABB area = new AABB(helper.absolutePos(origin)).inflate(9);
		boolean[] judged = {false};
		helper.succeedWhen(() -> {
			if (judged[0]) {
				return;
			}
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, area, ArmorStand::isAlive);
			helper.assertTrue(stands.size() >= 3, "the builder put up " + stands.size() + " stands, not the plain, ghost and giant ones (skipped " + site.skipped() + ")");
			judged[0] = true;
			List<String> wrong = new ArrayList<>();
			for (ArmorStand stand : stands) {
				String what = "stand at " + stand.blockPosition().subtract(helper.absolutePos(origin)).toShortString();
				if (stand.isInvisible()) {
					wrong.add(what + " is invisible");
				}
				if (stand.isInvulnerable()) {
					wrong.add(what + " is invulnerable");
				}
				if (stand.isMarker()) {
					wrong.add(what + " is a marker");
				}
				if (stand.isNoGravity()) {
					wrong.add(what + " has no gravity");
				}
				if (Math.abs(stand.getScale() - 1.0F) > 0.001F) {
					wrong.add(what + " has scale " + stand.getScale());
				}
				player.teleportTo(stand.getX() + 1.0, stand.getY(), stand.getZ() + 1.0);
				player.attack(stand);
				player.attack(stand);
				if (stand.isAlive()) {
					wrong.add(what + " survives two punches");
				}
			}
			player.discard();
			helper.assertTrue(wrong.isEmpty(), "uploaded stands aren't a survival player's: " + wrong);
			QaBasementGround1104GameTests.LOGGER.info("qa 1109b B1045: {} stands put up, each visible, normal, breakable", stands.size());
		});
	}

	// ------------------------------------------------------------------------------------------------ B1046

	/**
	 * B1046's neighbours, through the upload import: obfuscation, a tab and a delete character go, a lone trailing section
	 * sign goes, accents and other scripts stay, and a name of nothing but codes is never shown blank or coded.
	 */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '100' '"qa1109bNames"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qa1109bNames")
	public void uploadedNamesKeepEveryWordButNoCodeOrControlCharacter(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos src = new BlockPos(0, 1, 0);
		helper.setBlock(src, Blocks.STONE);
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(src), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(template.save(new CompoundTag()), out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		helper.setBlock(src, Blocks.AIR);
		String tag = Long.toHexString(level.getGameTime());
		List<String> wrong = new ArrayList<>();
		check(helper, bytes, "§kSecret§r Vault " + tag + ".nbt", "Secret Vault " + tag, wrong);
		check(helper, bytes, "Tab\tHouse " + tag + ".nbt", "Tab House " + tag, wrong);
		check(helper, bytes, "Del\u007fete " + tag + ".nbt", "Del ete " + tag, wrong);
		check(helper, bytes, "Château Été " + tag + "§.nbt", "Château Été " + tag, wrong);
		check(helper, bytes, "城の家 " + tag + ".nbt", "城の家 " + tag, wrong);
		check(helper, bytes, "§a§l§k.nbt", null, wrong);
		helper.assertTrue(wrong.isEmpty(), "uploaded names: " + wrong);
		helper.succeed();
	}

	private static void check(GameTestHelper helper, byte[] bytes, String fileName, String words, List<String> wrong) {
		ResourceLocation id;
		String safe = fileName.replace("§", "<section sign>").replace("\t", "<tab>").replace("\u007f", "<del>");
		try {
			id = BlueprintImporter.importBytes(helper.getLevel().getServer(), "uploads/qa_namer_1109b", fileName, bytes).id();
		} catch (Exception e) {
			wrong.add("'" + safe + "' didn't import: " + e);
			return;
		}
		String shown = Blueprints.displayName(id).getString();
		String shownSafe = shown.replace("§", "<section sign>").replace("\t", "<tab>").replace("\u007f", "<del>").replace("\n", "<line break>");
		if (shown.chars().anyMatch(c -> c == '§' || c < 32 || c == 127)) {
			wrong.add("'" + safe + "' is shown with a code or control character: '" + shownSafe + "'");
		}
		if (shown.isBlank()) {
			wrong.add("'" + safe + "' is shown blank");
		}
		if (words != null && !shown.equals(words)) {
			wrong.add("'" + safe + "' is shown as '" + shownSafe + "', not '" + words + "'");
		}
	}

	/**
	 * B1046 "also for names saved before": a world saved with coded names loads them as plain text, and a saved name of
	 * nothing but codes is forgotten (the build is called by its id).
	 */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '100' '"qa1109bNames"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qa1109bNames")
	public void namesSavedWithCodesLoadAsPlainText(GameTestHelper helper) {
		CompoundTag names = new CompoundTag();
		names.putString("aliveworkplace:uploads/qa_old/red_hall", "§4§lRed§r\nHall");
		names.putString("aliveworkplace:uploads/qa_old/only_codes", "§a§k");
		names.putString("aliveworkplace:uploads/qa_old/cafe", "Jesse's Café");
		CompoundTag saved = new CompoundTag();
		saved.put("names", names);
		BlueprintNames.Data data = BlueprintNames.Data.load(saved, helper.getLevel().registryAccess());
		String red = data.name(ResourceLocation.parse("aliveworkplace:uploads/qa_old/red_hall")).orElse("<none>");
		helper.assertTrue(red.equals("Red Hall"), "a saved coded name loads as '" + red.replace("§", "<section sign>").replace("\n", "<line break>") + "', not 'Red Hall'");
		helper.assertTrue(data.name(ResourceLocation.parse("aliveworkplace:uploads/qa_old/only_codes")).isEmpty(),
			"a saved name of nothing but codes is kept: '" + data.name(ResourceLocation.parse("aliveworkplace:uploads/qa_old/only_codes")).orElse("").replace("§", "<section sign>") + "'");
		String cafe = data.name(ResourceLocation.parse("aliveworkplace:uploads/qa_old/cafe")).orElse("<none>");
		helper.assertTrue(cafe.equals("Jesse's Café"), "a plain saved name changed on load: '" + cafe + "'");
		// and put() (the Table's rename and every import) keeps the words of a coded name
		ResourceLocation id = ResourceLocation.parse("aliveworkplace:uploads/qa_old/put_" + Long.toHexString(helper.getLevel().getGameTime()));
		BlueprintNames.put(helper.getLevel().getServer(), id, "§cRed§r §nBarn");
		String shown = Blueprints.displayName(id).getString();
		BlueprintNames.put(helper.getLevel().getServer(), id, "");
		helper.assertTrue(shown.equals("Red Barn"), "a name put with codes is shown as '" + shown.replace("§", "<section sign>") + "', not 'Red Barn'");
		helper.succeed();
	}
}
