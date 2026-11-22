package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.wonder.ColossusPlaque;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (2026-11-22, lane b) of ROADMAP 52.4 (the rebuilt Nether Gate, Waystone Plaza and Mega Shrine) and 52.6 (the eight
 * rebuilt Wonders), written from the items and their blind briefs ({@code tools/blueprints/briefs/nether_gate.md},
 * {@code waystone_plaza.md}, {@code mega_shrine.md}, the Wonders' briefs and critics), from the player's side. Existing
 * tests build both Nether Gates from the plan's own materials ({@code BuilderGameTests}), and pin the Wonders' looks
 * ({@code QaWonderDesigns*}) and the old-drawing fallback ({@code WonderDrawingsGameTests}); what this class adds:
 * <ul>
 * <li>both Nether Gates built from exactly the held blueprint's Material List, nothing skipped; with a flint and steel in
 * the chests the builder lights exactly one portal, in the frame at (4, 2, 3); a restart mid-build and a material short
 * (it waits, then finishes);</li>
 * <li>what the Nether Gate brief pins on its drawings: one cartography table (no Nether Brazier, a gone block), no other
 * job block or bed, storage beside the workstation, the portal frame and the workstation where Level II keeps them;</li>
 * <li>the Waystone Plaza's five looks and the Mega Shrine read from their files (their mods aren't in this suite): the
 * size, one waystone of the culture's kind at (3, 2, 3), the pedestal at (3, 3, 3) on its chiseled block, the trainer's
 * stand free;</li>
 * <li>every Wonder (but the Stadium, a Cobblemon one) loads with four named stages, and the facts the item pins: the
 * Colossus's 29 x 47 x 29, 8,786 counted blocks, one part per stage at its offset; the Great Cathedral's 39 x 53 x 56,
 * ten parts and four bells at y 31; the other counts;</li>
 * <li>the rebuilt Colossus stood up from its four stage drawings counts whole, its upkeep notices one plaque sign
 * broken, its plaque writes on its seven signs, and with a tenth of it gone it is damaged.</li>
 * </ul>
 */
public class QaRebuiltShrinesWonders1122bGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 9);
	/** Level II is 15 wide: further left, as {@code BuilderGameTests.buildsNetherGateII} has it. */
	private static final BlockPos ORIGIN_2 = new BlockPos(7, 2, 8);
	private static final List<BlockPos> CHESTS = List.of(new BlockPos(2, 2, 4), new BlockPos(4, 2, 4), new BlockPos(1, 2, 5), new BlockPos(4, 2, 1));
	/** The portal's inside, bottom-left cell, in the drawing (the brief: kept where it is). */
	private static final BlockPos PORTAL = new BlockPos(4, 2, 3);
	private static final ResourceLocation COLOSSUS = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "colossus");
	private static final ResourceLocation CATHEDRAL = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "great_cathedral");

	// ------------------------------------------------------------------------------------------------------ helpers

	private static String where(BuildSite live) {
		return live == null ? "done" : live.stage() + " " + live.status() + ", skipped " + live.skipped() + ", missing " + live.missing();
	}

	private static Blueprint blueprint(GameTestHelper helper, ResourceLocation id) {
		return BlueprintLibrary.get(helper.getLevel(), id).orElseThrow(() -> new GameTestAssertException(id + " has no blueprint"));
	}

	/** A structure file read as stored (its blocks by id, so another mod's blocks read even without that mod). */
	private record Raw(Vec3i size, Map<BlockPos, CompoundTag> cells) {
		String name(BlockPos p) {
			CompoundTag t = cells.get(p);
			return t == null ? "minecraft:air" : t.getString("Name");
		}

		String prop(BlockPos p, String key) {
			CompoundTag t = cells.get(p);
			return t == null ? "" : t.getCompound("Properties").getString(key);
		}
	}

	private static Raw raw(GameTestHelper helper, String path) {
		ResourceLocation file = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "structure/" + path + ".nbt");
		var resource = helper.getLevel().getServer().getResourceManager().getResource(file)
			.orElseThrow(() -> new GameTestAssertException("no structure file " + file));
		CompoundTag tag;
		try (InputStream in = resource.open()) {
			tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
		} catch (java.io.IOException e) {
			throw new GameTestAssertException(file + " can't be read: " + e);
		}
		ListTag size = tag.getList("size", Tag.TAG_INT);
		ListTag palette = tag.contains("palette") ? tag.getList("palette", Tag.TAG_COMPOUND) : tag.getList("palettes", Tag.TAG_LIST).getList(0);
		Map<BlockPos, CompoundTag> cells = new HashMap<>();
		for (Tag t : tag.getList("blocks", Tag.TAG_COMPOUND)) {
			CompoundTag b = (CompoundTag) t;
			ListTag pos = b.getList("pos", Tag.TAG_INT);
			cells.put(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), palette.getCompound(b.getInt("state")));
		}
		return new Raw(new Vec3i(size.getInt(0), size.getInt(1), size.getInt(2)), cells);
	}

	private static String id(BlockState s) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
	}

	private static BlueprintData.Placement at(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(Ids.of(helper.getLevel().dimension()), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	/** A finished site of {@code wonder} placed at {@code origin}, recorded with today's drawing (not registered in any manager). */
	private static WonderSite finished(GameTestHelper helper, ResourceLocation wonder, BlockPos origin) {
		return new WonderSite(UUID.randomUUID(), wonder, helper.absolutePos(BlockPos.ZERO), Optional.empty(), "Owner", 4, 1L,
			Optional.of(2L), Optional.of(at(helper, origin)), Optional.empty(), List.of(), Optional.empty(), false, true, List.of(), WonderSite.Kept.WHOLE,
			Optional.of(Wonders.drawing(wonder)));
	}

	/** The box (relative to the Wonder's corner) every part of every stage of {@code wonder} fills. */
	private static Vec3i wholeSize(GameTestHelper helper, Wonder wonder) {
		int mx = 0, my = 0, mz = 0;
		for (Wonder.Stage stage : wonder.stages()) {
			for (Wonder.Part part : stage.parts()) {
				Vec3i s = helper.getLevel().getStructureManager().get(part.part())
					.orElseThrow(() -> new GameTestAssertException(part.part() + " has no structure file")).getSize();
				mx = Math.max(mx, part.offset().getX() + s.getX());
				my = Math.max(my, part.offset().getY() + s.getY());
				mz = Math.max(mz, part.offset().getZ() + s.getZ());
			}
		}
		return new Vec3i(mx, my, mz);
	}

	// ------------------------------------------------------------- 1. the Nether Gates, built by a real Builder

	/**
	 * The Nether Gate from exactly the held blueprint's list, plus one flint and steel in a chest: it finishes as drawn
	 * with nothing skipped, and the builder lights exactly one portal, in the frame whose inside starts at (4, 2, 3),
	 * standing across x (the brief: one frame, lit by the builder; a second frame would take a second flint and steel).
	 */
	//$ gametest_ticks_batch BIG '30000' '"qaShrines1122b_netherGateFromExactlyItsListLightsOnePortal"'
	@GameTest(template = BIG, timeoutTicks = 30000, batch = "qaShrines1122b_netherGateFromExactlyItsListLightsOnePortal")
	public void netherGateFromExactlyItsListLightsOnePortal(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaDecorations1104fGameTests.Job job = QaDecorations1104fGameTests.exact(helper, StarterBlueprints.NETHER_GATE, ORIGIN);
		boolean put = false;
		for (BlockPos chest : CHESTS) {
			Container c = helper.getBlockEntity(chest);
			for (int slot = 0; slot < c.getContainerSize() && !put; slot++) {
				if (c.getItem(slot).isEmpty()) {
					c.setItem(slot, new ItemStack(Items.FLINT_AND_STEEL));
					put = true;
				}
			}
		}
		if (!put) { // the list fills the four chests: one more beside the bench, as a player would put it
			helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST);
			Container extra = helper.getBlockEntity(new BlockPos(1, 2, 1));
			extra.setItem(0, new ItemStack(Items.FLINT_AND_STEEL));
		}
		helper.succeedWhen(() -> {
			BuildSite live = BuildSiteManager.get(helper.getLevel()).get(job.site().id());
			helper.assertTrue(live == null, "the nether gate from exactly its list is still building: " + where(live));
			helper.assertTrue(job.site().skipped() == 0, "the nether gate skipped " + job.site().skipped() + " block(s)");
			// every block as drawn, but the portal's inside: drawn air, lit by the builder
			List<BlockPos> unfinished = job.plan().unfinished(helper.getLevel()).stream()
				.filter(p -> !helper.getLevel().getBlockState(p).is(Blocks.NETHER_PORTAL)).toList();
			helper.assertTrue(unfinished.isEmpty(), "the nether gate: " + unfinished.size() + " block(s) not as drawn, e.g. "
				+ unfinished.stream().limit(5).map(p -> helper.relativePos(p).subtract(ORIGIN).toShortString() + "=" + helper.getLevel().getBlockState(p)).toList());
			Vec3i s = StarterBlueprints.NETHER_GATE.size();
			List<BlockPos> portal = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(ORIGIN, ORIGIN.offset(s.getX() - 1, s.getY() - 1, s.getZ() - 1))) {
				if (helper.getBlockState(p).is(Blocks.NETHER_PORTAL)) {
					portal.add(p.subtract(ORIGIN).immutable());
				}
			}
			helper.assertTrue(portal.contains(PORTAL), "the finished Nether Gate has no lit portal at " + PORTAL + ": portal blocks " + portal);
			helper.assertTrue(portal.stream().allMatch(p -> p.getZ() == PORTAL.getZ()), "the builder lit portal blocks off the frame's plane: " + portal);
			helper.assertTrue(portal.size() >= 6 && portal.size() <= 21 * 21, "the lit portal is " + portal.size() + " blocks");
			helper.assertTrue(helper.getBlockState(ORIGIN.offset(PORTAL)).getValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS)
				== net.minecraft.core.Direction.Axis.X, "the portal doesn't stand across x, facing the front");
		});
	}

	/** Nether Gate II from exactly the held blueprint's list (at Level II's own origin): finished as drawn, nothing skipped. */
	//$ gametest_ticks_batch BIG '40000' '"qaShrines1122b_netherGateIIFromExactlyItsList"'
	@GameTest(template = BIG, timeoutTicks = 40000, batch = "qaShrines1122b_netherGateIIFromExactlyItsList")
	public void netherGateIIFromExactlyItsList(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaDecorations1104fGameTests.Job job = QaDecorations1104fGameTests.exact(helper, StarterBlueprints.NETHER_GATE_2, ORIGIN_2);
		helper.succeedWhen(() -> QaDecorations1104fGameTests.assertExactlyBuilt(helper, job));
	}

	/**
	 * A server restart in the middle of a Nether Gate (the site and the builder saved and loaded, once it is in its
	 * STRUCTURE stage): the builder carries on and it finishes as drawn with nothing skipped.
	 */
	//$ gametest_ticks_batch BIG '30000' '"qaShrines1122b_netherGateResumesAfterARestartMidBuild"'
	@GameTest(template = BIG, timeoutTicks = 30000, batch = "qaShrines1122b_netherGateResumesAfterARestartMidBuild")
	public void netherGateResumesAfterARestartMidBuild(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaDecorations1104fGameTests.Job job = QaDecorations1104fGameTests.exact(helper, StarterBlueprints.NETHER_GATE, ORIGIN);
		ServerLevel level = helper.getLevel();
		boolean[] restarted = {false};
		int[] skipped = {0};
		helper.onEachTick(() -> {
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			if (live != null) {
				skipped[0] = live.skipped();
			}
			if (!restarted[0] && live != null && live.stage() == BuildPlan.Stage.STRUCTURE && live.placed() > 20) {
				restarted[0] = true;
				var registries = level.registryAccess();
				level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
				Villager old = job.villager();
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(old.save(tag), "the builder wasn't saved");
				old.discard();
				Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
				helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
				level.addFreshEntity(loaded);
				LOGGER.info("qa shrines 1122b: restarted the nether gate at {}", where(BuildSiteManager.get(level).get(job.site().id())));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0], "the nether gate never reached its STRUCTURE stage with blocks placed");
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			helper.assertTrue(live == null, "after the restart the nether gate is still building: " + where(live));
			List<BlockPos> unfinished = job.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), "after the restart " + unfinished.size() + " block(s) of the nether gate are not as drawn");
			helper.assertTrue(skipped[0] == 0, "after the restart the nether gate skipped " + skipped[0] + " block(s)");
		});
	}

	/**
	 * The Nether Gate with its obsidian left out of the chests: it doesn't finish, its site says it is missing obsidian;
	 * once the player puts exactly the missing count in a chest it finishes as drawn, nothing skipped.
	 */
	//$ gametest_ticks_batch BIG '30000' '"qaShrines1122b_netherGateShortOfObsidianWaitsThenFinishes"'
	@GameTest(template = BIG, timeoutTicks = 30000, batch = "qaShrines1122b_netherGateShortOfObsidianWaitsThenFinishes")
	public void netherGateShortOfObsidianWaitsThenFinishes(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaDecorations1104fGameTests.Job job = QaDecorations1104fGameTests.exact(helper, StarterBlueprints.NETHER_GATE, ORIGIN);
		ServerLevel level = helper.getLevel();
		Item short_ = Items.OBSIDIAN;
		Integer asked = job.list().get(short_);
		helper.assertTrue(asked != null && asked >= 10, "the Nether Gate's list asks for " + asked + " obsidian (a frame needs at least 10)");
		int count = asked;
		for (BlockPos chest : CHESTS) {
			Container c = helper.getBlockEntity(chest);
			for (int slot = 0; slot < c.getContainerSize(); slot++) {
				if (c.getItem(slot).is(short_)) {
					c.setItem(slot, ItemStack.EMPTY);
				}
			}
		}
		boolean[] sawWait = {false};
		boolean[] restocked = {false};
		int[] skipped = {0};
		int[] doneAt = {-1};
		helper.onEachTick(() -> {
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			if (live != null) {
				skipped[0] = live.skipped();
				if (!restocked[0] && live.missing().containsKey(short_)) {
					sawWait[0] = true;
				}
			}
			if (!restocked[0] && helper.getTick() >= 6000) {
				helper.assertTrue(live != null, "the nether gate finished without its obsidian (" + count + " asked for)");
				helper.assertTrue(sawWait[0], "the nether gate never said it was missing obsidian: " + where(live));
				restocked[0] = true;
				Container c = helper.getBlockEntity(CHESTS.get(0));
				int left = count;
				for (int slot = 0; slot < c.getContainerSize() && left > 0; slot++) {
					if (c.getItem(slot).isEmpty()) {
						int take = Math.min(left, 64);
						c.setItem(slot, new ItemStack(short_, take));
						left -= take;
					}
				}
				helper.assertTrue(left == 0, "setup: no room in the chest to restock");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restocked[0], "not restocked yet");
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			helper.assertTrue(live == null, "restocked, the nether gate is still building: " + where(live));
			List<BlockPos> unfinished = job.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), "restocked, " + unfinished.size() + " block(s) of the nether gate are not as drawn");
			helper.assertTrue(skipped[0] == 0, "restocked, the nether gate skipped " + skipped[0] + " block(s)");
		});
	}

	// ------------------------------------------------------------------- 2. what the briefs pin on the drawings

	/**
	 * Both Nether Gates, from their drawings: an obsidian frame under and round the inside at (4, 2, 3) (bottom row y 1),
	 * the inside empty; exactly one workstation, a cartography table (the brazier is gone), within 32 of the portal; no
	 * other job block, no bed; a chest or barrel within 8 across and 4 up or down of it; Level II keeps the frame and the
	 * table where Level I has them.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void netherGatesHaveTheirFrameWorkstationAndStorage(GameTestHelper helper) {
		List<String> bad = new ArrayList<>();
		Map<String, BlockPos> tables = new LinkedHashMap<>();
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.NETHER_GATE, StarterBlueprints.NETHER_GATE_2)) {
			String n = e.id().getPath();
			Map<BlockPos, BlockState> at = new HashMap<>();
			for (Blueprint.Entry b : blueprint(helper, e.id()).blocks()) {
				at.put(b.pos(), b.state());
			}
			for (int x = PORTAL.getX(); x <= PORTAL.getX() + 1; x++) {
				for (int y = PORTAL.getY(); y <= PORTAL.getY() + 2; y++) {
					BlockState in = at.getOrDefault(new BlockPos(x, y, PORTAL.getZ()), Blocks.AIR.defaultBlockState());
					if (!in.isAir()) {
						bad.add(n + ": the portal's inside at " + x + "," + y + " holds " + in);
					}
				}
				if (!at.getOrDefault(new BlockPos(x, PORTAL.getY() - 1, PORTAL.getZ()), Blocks.AIR.defaultBlockState()).is(Blocks.OBSIDIAN)) {
					bad.add(n + ": no obsidian under the portal's inside at x " + x);
				}
			}
			List<BlockPos> jobs = new ArrayList<>();
			List<BlockPos> storage = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> b : at.entrySet()) {
				BlockState s = b.getValue();
				if (id(s).equals("aliveworkplace:nether_brazier")) {
					bad.add(n + ": a Nether Brazier (a gone block) at " + b.getKey());
				}
				// (a barrel is the brief's storage, though vanilla's fisherman could take one: storage, not a workstation here)
				if (!s.is(Blocks.BARREL) && (PoiTypes.forState(s).isPresent() || s.getBlock() instanceof net.minecraft.world.level.block.BedBlock)) {
					jobs.add(b.getKey());
				}
				if (s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.TRAPPED_CHEST)) {
					storage.add(b.getKey());
				}
			}
			if (jobs.size() != 1 || !at.get(jobs.get(0)).is(Blocks.CARTOGRAPHY_TABLE)) {
				bad.add(n + ": job blocks and beds " + jobs.stream().map(p -> p.toShortString() + "=" + id(at.get(p))).toList() + ", wanted one cartography table");
				continue;
			}
			BlockPos table = jobs.get(0);
			tables.put(n, table);
			if (table.distSqr(PORTAL) > 32 * 32) {
				bad.add(n + ": the cartography table at " + table + " is more than 32 from the portal");
			}
			boolean near = storage.stream().anyMatch(p -> Math.abs(p.getX() - table.getX()) <= 8 && Math.abs(p.getZ() - table.getZ()) <= 8
				&& Math.abs(p.getY() - table.getY()) <= 4);
			if (!near) {
				bad.add(n + ": no chest or barrel within 8 across, 4 up or down of the table at " + table + " (storage " + storage + ")");
			}
		}
		if (tables.size() == 2 && !tables.get("nether_gate").equals(tables.get("nether_gate_2"))) {
			bad.add("Level II moves the cartography table: I " + tables.get("nether_gate") + ", II " + tables.get("nether_gate_2"));
		}
		helper.assertTrue(bad.isEmpty(), "nether gates: " + bad);
		helper.succeed();
	}

	/**
	 * The Waystone Plaza's five looks, read from their files: each 7 x 5 x 7, exactly one block of the waystones mod, its
	 * culture's stone, lower half at (3, 2, 3) (and its upper half above it), set as a village's stone; no storage, bell or
	 * other job block (a chest would be taken for the builder's supply).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyWaystonePlazaLookHasOneStoneOfItsKind(GameTestHelper helper) {
		Map<String, String> looks = new LinkedHashMap<>();
		looks.put("waystone_plaza", "waystones:waystone");
		looks.put("looks/desert/waystone_plaza", "waystones:sandy_waystone");
		looks.put("looks/savanna/waystone_plaza", "waystones:mud_bricks_waystone");
		looks.put("looks/snowy/waystone_plaza", "waystones:deepslate_waystone");
		looks.put("looks/taiga/waystone_plaza", "waystones:mossy_waystone");
		BlockPos stone = new BlockPos(3, 2, 3);
		List<String> bad = new ArrayList<>();
		for (Map.Entry<String, String> look : looks.entrySet()) {
			Raw r = raw(helper, look.getKey());
			String n = look.getKey();
			if (!r.size().equals(new Vec3i(7, 5, 7))) {
				bad.add(n + ": " + r.size().toShortString());
			}
			if (!r.name(stone).equals(look.getValue()) || !r.prop(stone, "half").equals("lower") || !r.prop(stone, "origin").equals("village")) {
				bad.add(n + ": at (3, 2, 3) " + r.name(stone) + " half=" + r.prop(stone, "half") + " origin=" + r.prop(stone, "origin")
					+ ", wanted " + look.getValue() + " lower, village");
			}
			if (!r.name(stone.above()).equals(look.getValue()) || !r.prop(stone.above(), "half").equals("upper")) {
				bad.add(n + ": at (3, 3, 3) " + r.name(stone.above()) + " half=" + r.prop(stone.above(), "half"));
			}
			for (Map.Entry<BlockPos, CompoundTag> c : r.cells().entrySet()) {
				String name = c.getValue().getString("Name");
				BlockPos p = c.getKey();
				if (name.startsWith("waystones:") && !p.equals(stone) && !p.equals(stone.above())) {
					bad.add(n + ": a second waystones block " + name + " at " + p.toShortString());
				}
				if (name.matches("minecraft:(chest|trapped_chest|barrel|bell|lectern|composter|smoker|blast_furnace|cartography_table|brewing_stand"
					+ "|fletching_table|grindstone|loom|smithing_table|stonecutter|cauldron)") || name.endsWith("_bed")) {
					bad.add(n + ": " + name + " at " + p.toShortString());
				}
			}
		}
		helper.assertTrue(bad.isEmpty(), "waystone plazas: " + bad);
		helper.succeed();
	}

	/**
	 * The Mega Shrine, read from its file: 7 x 6 x 7; the pedestal at (3, 3, 3) on the chiseled Mega Meteorid block at
	 * (3, 2, 3); the trainer's stand in front of it, (3, 3, 2) and (3, 4, 2), free to stand in over solid footing at
	 * (3, 2, 2); the seven blocks the guide names each in it; no job block, bed or storage.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void megaShrineHasItsPedestalStandAndNamedBlocks(GameTestHelper helper) {
		Raw r = raw(helper, "mega_shrine");
		List<String> bad = new ArrayList<>();
		if (!r.size().equals(new Vec3i(7, 6, 7))) {
			bad.add("size " + r.size().toShortString());
		}
		if (!r.name(new BlockPos(3, 3, 3)).equals("mega_showdown:pedestal")) {
			bad.add("at (3, 3, 3) " + r.name(new BlockPos(3, 3, 3)));
		}
		if (!r.name(new BlockPos(3, 2, 3)).equals("mega_showdown:chiseled_mega_meteorid_block")) {
			bad.add("under the pedestal " + r.name(new BlockPos(3, 2, 3)));
		}
		for (BlockPos p : List.of(new BlockPos(3, 3, 2), new BlockPos(3, 4, 2))) {
			String name = r.name(p);
			if (!(name.equals("minecraft:air") || name.endsWith("_carpet") || name.endsWith("_pressure_plate"))) {
				bad.add("the trainer's stand at " + p.toShortString() + " holds " + name);
			}
		}
		String footing = r.name(new BlockPos(3, 2, 2));
		if (footing.equals("minecraft:air") || r.prop(new BlockPos(3, 2, 2), "type").equals("bottom") && footing.endsWith("_slab")) {
			bad.add("no solid footing under the trainer's stand: " + footing + " " + r.prop(new BlockPos(3, 2, 2), "type"));
		}
		java.util.Set<String> names = new java.util.HashSet<>();
		r.cells().values().forEach(t -> names.add(t.getString("Name")));
		for (String want : List.of("mega_showdown:pedestal", "mega_showdown:chiseled_mega_meteorid_block", "mega_showdown:mega_meteorid_brick",
			"minecraft:deepslate_brick_stairs", "minecraft:deepslate_brick_wall", "minecraft:amethyst_cluster", "minecraft:soul_lantern")) {
			if (!names.contains(want)) {
				bad.add("no " + want);
			}
		}
		for (String name : names) {
			if (name.matches("minecraft:(chest|trapped_chest|barrel|bell|lectern|composter|smoker|blast_furnace|cartography_table|brewing_stand"
				+ "|fletching_table|grindstone|loom|smithing_table|stonecutter)") || name.endsWith("_bed")) {
				bad.add("a job, bed or storage block " + name);
			}
		}
		helper.assertTrue(bad.isEmpty(), "mega shrine: " + bad);
		helper.succeed();
	}

	/**
	 * Every shrine 52.4 lists loads as a blueprint (or, for the modded ones, has its file), its structure is the size its
	 * entry declares, and its name reads in words in English (not the id or a fallback).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyShrineHasItsSizeAndAName(GameTestHelper helper) {
		List<String> bad = new ArrayList<>();
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.NETHER_GATE, StarterBlueprints.NETHER_GATE_2)) {
			Vec3i size = blueprint(helper, e.id()).size();
			if (!size.equals(e.size())) {
				bad.add(e.id() + ": drawn " + size.toShortString() + ", declared " + e.size().toShortString());
			}
		}
		Vec3i mega = raw(helper, "mega_shrine").size();
		if (!mega.equals(StarterBlueprints.MEGA_SHRINE.size())) {
			bad.add("mega_shrine drawn " + mega.toShortString() + ", declared " + StarterBlueprints.MEGA_SHRINE.size().toShortString());
		}
		Map<String, String> want = Map.of("nether_gate", "Nether Gate", "nether_gate_2", "Nether Gate II", "waystone_plaza", "Waystone Plaza",
			"mega_shrine", "Mega Shrine");
		for (Map.Entry<String, String> w : want.entrySet()) {
			String name = Blueprints.displayName(ResourceLocation.fromNamespaceAndPath("aliveworkplace", w.getKey())).getString();
			if (!name.equals(w.getValue())) {
				bad.add(w.getKey() + " reads \"" + name + "\", wanted \"" + w.getValue() + "\"");
			}
		}
		helper.assertTrue(bad.isEmpty(), "shrines: " + bad);
		helper.succeed();
	}

	// ----------------------------------------------------------------------------------------- 3. the Wonders

	/**
	 * Every Wonder (but the Stadium, which loads only with Cobblemon) is loaded with no problem, has four stages, each
	 * stage's name and the Wonder's name read in words (not a lang key), and every part of every stage loads as a
	 * blueprint with blocks; today's drawing is the key its file names.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyRebuiltWonderLoadsWithFourNamedStages(GameTestHelper helper) {
		List<String> bad = new ArrayList<>();
		for (String path : List.of("colossus", "great_cathedral", "great_library", "great_wall", "hanging_gardens", "lighthouse", "observatory")) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace", path);
			Wonder w = Wonders.loaded().get(id);
			if (w == null) {
				bad.add(path + " not loaded: " + Wonders.problems().get(id));
				continue;
			}
			String name = Wonders.name(id).getString();
			if (name.isBlank() || name.contains(".") || name.contains("_")) {
				bad.add(path + "'s name reads \"" + name + "\"");
			}
			if (w.stages().size() != 4) {
				bad.add(path + " has " + w.stages().size() + " stages");
			}
			for (Wonder.Stage st : w.stages()) {
				String title = st.nameText().getString();
				if (title.equals(st.name()) || title.isBlank()) {
					bad.add(path + ": stage title untranslated " + st.name());
				}
				for (Wonder.Part part : st.parts()) {
					Optional<Blueprint> b = BlueprintLibrary.get(helper.getLevel(), part.part());
					if (b.isEmpty() || b.get().blocks().isEmpty()) {
						bad.add(path + ": part " + part.part() + " doesn't load");
					}
				}
			}
			if (!Wonders.drawing(id).matches("drawn_[0-9a-f]{9}")) {
				bad.add(path + ": drawing key " + Wonders.drawing(id));
			}
		}
		helper.assertTrue(bad.isEmpty(), "wonders: " + bad);
		helper.succeed();
	}

	/**
	 * The facts 52.6 pins on the rebuilt drawings: the Colossus is 29 x 47 x 29 with one part per stage at (0,0,0),
	 * (9,13,8), (6,13,8), (11,34,7) and WonderUpkeep counts 8,786 of its blocks; its seven plaque signs are dark oak wall
	 * signs in one row at y 8, z 2, the ones ColossusPlaque reads; nine lit campfires; no waxed or weathered copper. The
	 * Great Cathedral is 39 x 53 x 56, ten parts (4, 1, 3, 2), four bells at y 31, counted 14,221; the Great Library
	 * counted 14,853, the Great Wall 13,285.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theRebuiltWondersMatchWhatTheItemPins(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> bad = new ArrayList<>();
		BlockPos origin = new BlockPos(1, 2, 1);
		BlockPos o = helper.absolutePos(origin);

		Wonder colossus = Wonders.loaded().get(COLOSSUS);
		helper.assertTrue(colossus != null, "the Colossus isn't loaded");
		Vec3i cs = wholeSize(helper, colossus);
		if (!cs.equals(new Vec3i(29, 47, 29))) {
			bad.add("Colossus is " + cs.toShortString());
		}
		List<List<BlockPos>> offsets = colossus.stages().stream().map(st -> st.parts().stream().map(Wonder.Part::offset).toList()).toList();
		if (!offsets.equals(List.of(List.of(BlockPos.ZERO), List.of(new BlockPos(9, 13, 8)), List.of(new BlockPos(6, 13, 8)), List.of(new BlockPos(11, 34, 7))))) {
			bad.add("Colossus parts at " + offsets);
		}
		WonderSite cSite = finished(helper, COLOSSUS, origin);
		List<WonderUpkeep.Block> cBlocks = WonderUpkeep.blocks(level, cSite);
		if (cBlocks.size() != 8786) {
			bad.add("Colossus counted against " + cBlocks.size() + " blocks");
		}
		List<BlockPos> signs = new ArrayList<>();
		int lit = 0;
		int campfires = 0;
		for (WonderUpkeep.Block b : cBlocks) {
			BlockState s = b.state();
			String id = id(s);
			if (s.getBlock() instanceof WallSignBlock) {
				signs.add(b.pos().subtract(o));
				if (!s.is(Blocks.DARK_OAK_WALL_SIGN)) {
					bad.add("Colossus plaque sign of " + id);
				}
			}
			if (s.is(Blocks.CAMPFIRE)) {
				campfires++;
				lit += s.getValue(CampfireBlock.LIT) ? 1 : 0;
			}
			if (id.contains("copper") && (id.contains("waxed") || id.contains("exposed") || id.contains("weathered") || id.contains("oxidized"))) {
				bad.add("Colossus copper " + id + " at " + b.pos().subtract(o).toShortString());
				break;
			}
		}
		if (signs.size() != 7 || !signs.stream().allMatch(p -> p.getY() == 8 && p.getZ() == 2)) {
			bad.add("Colossus wall signs " + signs);
		}
		List<BlockPos> plaque = ColossusPlaque.signs(level, cSite).stream().map(p -> p.subtract(o)).toList();
		if (plaque.size() != 7 || !new java.util.HashSet<>(plaque).equals(new java.util.HashSet<>(signs))) {
			bad.add("ColossusPlaque reads " + plaque + ", the drawing's signs are " + signs);
		}
		if (campfires != 9 || lit != 9) {
			bad.add("Colossus campfires " + campfires + ", lit " + lit);
		}

		Wonder cathedral = Wonders.loaded().get(CATHEDRAL);
		helper.assertTrue(cathedral != null, "the Great Cathedral isn't loaded");
		Vec3i gs = wholeSize(helper, cathedral);
		if (!gs.equals(new Vec3i(39, 53, 56))) {
			bad.add("Great Cathedral is " + gs.toShortString());
		}
		List<Integer> parts = cathedral.stages().stream().map(st -> st.parts().size()).toList();
		if (!parts.equals(List.of(4, 1, 3, 2))) {
			bad.add("Great Cathedral parts per stage " + parts);
		}
		WonderSite gSite = finished(helper, CATHEDRAL, origin);
		List<WonderUpkeep.Block> gBlocks = WonderUpkeep.blocks(level, gSite);
		List<BlockPos> bells = gBlocks.stream().filter(b -> b.state().is(Blocks.BELL)).map(b -> b.pos().subtract(o)).toList();
		if (bells.size() != 4 || !bells.stream().allMatch(p -> p.getY() == 31)) {
			bad.add("Great Cathedral bells " + bells);
		}
		if (gBlocks.size() != 14221) {
			bad.add("Great Cathedral counted against " + gBlocks.size());
		}
		Map<String, Integer> counts = Map.of("great_library", 14853, "great_wall", 13285);
		for (Map.Entry<String, Integer> c : counts.entrySet()) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace", c.getKey());
			int n = WonderUpkeep.blocks(level, finished(helper, id, origin)).size();
			if (n != c.getValue()) {
				bad.add(c.getKey() + " counted against " + n + ", the item says " + c.getValue());
			}
		}
		helper.assertTrue(bad.isEmpty(), "rebuilt wonders: " + bad);
		helper.succeed();
	}

	/**
	 * The rebuilt Colossus stood up from its four stage drawings (each placed at its offset, a later stage over an
	 * earlier one) at a finished site: its upkeep counts it whole (100%); one plaque sign broken, the count notices
	 * (under 100, still standing); put back, whole again; the plaque writes on all seven signs, once; with more than a
	 * tenth of it gone it is damaged.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaWonders1122b_colossusStandsWholeAndItsUpkeepNoticesDamage"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaWonders1122b_colossusStandsWholeAndItsUpkeepNoticesDamage")
	public void colossusStandsWholeAndItsUpkeepNoticesDamage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		BlockPos corner = helper.absolutePos(new BlockPos(0, 2, 0));
		BlockPos far = corner.offset(28, 46, 28);
		Runnable wipe = () -> {
			for (BlockPos p : BlockPos.betweenClosed(corner, far)) {
				if (!level.getBlockState(p).isAir()) {
					level.removeBlockEntity(p);
					level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
				}
			}
		};
		wipe.run();
		BlockPos hallRel = new BlockPos(0, 2, 29);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallRel);
		WonderManager manager = WonderManager.get(level);
		WonderSite begun = manager.begin(COLOSSUS, hall, null, "", 1);
		manager.place(begun.id(), new BlueprintData.Placement(Ids.of(level.dimension()), corner, Rotation.NONE, Mirror.NONE), corner.offset(-1, 0, -1));
		manager.finish(begun.id(), 1);
		manager.setDrawing(begun.id(), Wonders.drawing(COLOSSUS));
		int standing = WonderUpkeep.STANDING;
		Leftovers.after(helper, () -> {
			WonderUpkeep.STANDING = standing;
			manager.remove(begun.id());
			wipe.run();
		});
		Wonder colossus = Wonders.loaded().get(COLOSSUS);
		StructurePlaceSettings settings = new StructurePlaceSettings().addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR).setIgnoreEntities(true);
		for (Wonder.Stage st : colossus.stages()) {
			for (Wonder.Part part : st.parts()) {
				StructureTemplate t = level.getStructureManager().get(part.part()).orElseThrow();
				BlockPos at = corner.offset(part.offset());
				t.placeInWorld(level, at, at, settings, RandomSource.create(1122L), 2 | 16);
			}
		}
		UUID id = begun.id();
		int whole = WonderUpkeep.countNow(level, id);
		helper.assertTrue(whole == 100, "the Colossus stood up from its four stage drawings counts " + whole + "% (want 100)");
		WonderSite site = manager.site(id).orElseThrow();
		List<BlockPos> signs = ColossusPlaque.signs(level, site);
		helper.assertTrue(signs.size() == 7, "the plaque has " + signs.size() + " signs");
		BlockPos sign = signs.get(3);
		BlockState was = level.getBlockState(sign);
		helper.assertTrue(was.getBlock() instanceof WallSignBlock, "no plaque sign stands at " + sign + ": " + was);
		level.setBlock(sign, Blocks.AIR.defaultBlockState(), 2 | 16);
		int less = WonderUpkeep.countNow(level, id);
		helper.assertTrue(less == 99 && !manager.site(id).orElseThrow().kept().damaged(), "one plaque sign broken: " + less + "%, " + manager.site(id).orElseThrow().kept());
		level.setBlock(sign, was, 2 | 16);
		helper.assertTrue(WonderUpkeep.countNow(level, id) == 100, "the sign put back, not whole");
		int written = ColossusPlaque.write(level, manager.site(id).orElseThrow());
		helper.assertTrue(written == 7, "the plaque wrote on " + written + " signs, wanted 7");
		for (BlockPos p : signs) {
			helper.assertTrue(level.getBlockEntity(p) instanceof SignBlockEntity s && !s.getText(true).getMessage(1, false).getString().isBlank(),
				"the plaque sign at " + p + " is blank after writing");
		}
		helper.assertTrue(ColossusPlaque.write(level, manager.site(id).orElseThrow()) == 0, "the plaque wrote over its own signs again");
		List<WonderUpkeep.Block> pick = new ArrayList<>(WonderUpkeep.blocks(level, manager.site(id).orElseThrow()));
		net.minecraft.Util.shuffle(pick, RandomSource.create(11226L));
		for (WonderUpkeep.Block b : pick.subList(0, (int) Math.ceil(pick.size() * 0.11))) {
			level.removeBlockEntity(b.pos());
			level.setBlock(b.pos(), Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		int broken = WonderUpkeep.countNow(level, id);
		helper.assertTrue(broken < 90 && manager.site(id).orElseThrow().kept().damaged(), "11% broken: " + broken + "%, " + manager.site(id).orElseThrow().kept());
		LOGGER.info("qa wonders 1122b: colossus whole 100, one sign {}, 11% broken {}", less, broken);
		helper.succeed();
	}
}
