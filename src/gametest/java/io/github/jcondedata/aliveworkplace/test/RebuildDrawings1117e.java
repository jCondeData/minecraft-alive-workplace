package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Shared set-up of QaRebuildSaves1117eGameTests and QaRebuildSaves1117eBugTests (QA lane E, 2026-11-17): a blueprint
 * whose drawing is replaced under the same id by a totally different one, as Milestones 49-52 do to every family
 * (tools/blueprints/REBUILD.md: "Ids never change; standing buildings and sites mid-build keep their old drawing until a
 * refresh"). The new jar is modelled as the older refresh tests model it: the id's structure template is replaced in the
 * server's template manager and the old drawing is added to the shipped history ({@link Refreshes#addHistory}, what
 * {@code history.py} ships), and a restart as the sites and the builder saved and loaded back.
 *
 * <p>The OLD drawing: a 5x5 cobblestone floor, a ring of oak planks on it (doorway at the front, x 2), and (furnished)
 * a chest and a lectern at the front and a bed at the back. The NEW drawing: wider and shallower (7x3), a stone brick
 * floor and a ring of spruce planks with its doorway at the back (x 3): another footprint, another door, no bed, no job
 * block. The upgrade's next level is the new drawing with a smooth stone roof on it.
 */
final class RebuildDrawings1117e {
	static final String AREA = "aliveworkplace_test:big_area";
	static final BlockPos HALL = new BlockPos(14, 2, 14);
	static final BlockPos HOUSE = new BlockPos(4, 2, 6);
	static final BlockPos STORE = new BlockPos(2, 2, 3);
	static final BlockPos BENCH = new BlockPos(2, 2, 2);
	static final BlockPos CHEST = new BlockPos(1, 1, 1);
	static final BlockPos LECTERN = new BlockPos(3, 1, 1);
	static final BlockPos BED_FOOT = new BlockPos(1, 1, 3);
	static final BlockPos BED_HEAD = new BlockPos(2, 1, 3);
	/** The player's own block on the old drawing's back wall (where it had oak planks). */
	static final BlockPos OWN_ON_OLD = new BlockPos(4, 1, 4);
	/** The player's own block outside the old plot, where the new drawing has a wall. */
	static final BlockPos OWN_OUTSIDE = new BlockPos(5, 1, 0);

	private RebuildDrawings1117e() {
	}

	record Stage(ServerLevel level, ResourceLocation id, BlueprintData.Placement placement, ServerPlayer owner) {
	}

	static List<Blueprint.Entry> oldDrawing(boolean furnished) {
		List<Blueprint.Entry> out = new ArrayList<>();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				out.add(new Blueprint.Entry(new BlockPos(x, 0, z), Blocks.COBBLESTONE.defaultBlockState(), null));
				boolean ring = x == 0 || x == 4 || z == 0 || z == 4;
				if (ring && !(x == 2 && z == 0)) {
					out.add(new Blueprint.Entry(new BlockPos(x, 1, z), Blocks.OAK_PLANKS.defaultBlockState(), null));
				}
			}
		}
		if (furnished) {
			out.add(new Blueprint.Entry(CHEST, Blocks.CHEST.defaultBlockState(), null));
			out.add(new Blueprint.Entry(LECTERN, Blocks.LECTERN.defaultBlockState(), null));
			BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
			out.add(new Blueprint.Entry(BED_FOOT, bed.setValue(BedBlock.PART, BedPart.FOOT), null));
			out.add(new Blueprint.Entry(BED_HEAD, bed.setValue(BedBlock.PART, BedPart.HEAD), null));
		}
		return out;
	}

	static List<Blueprint.Entry> newDrawing(boolean roof) {
		List<Blueprint.Entry> out = new ArrayList<>();
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 3; z++) {
				out.add(new Blueprint.Entry(new BlockPos(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState(), null));
				boolean ring = x == 0 || x == 6 || z == 0 || z == 2;
				if (ring && !(x == 3 && z == 2)) {
					out.add(new Blueprint.Entry(new BlockPos(x, 1, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), null));
				}
				if (roof) {
					out.add(new Blueprint.Entry(new BlockPos(x, 2, z), Blocks.SMOOTH_STONE.defaultBlockState(), null));
				}
			}
		}
		return out;
	}

	static Blueprint oldBlueprint(ResourceLocation id, boolean furnished) {
		return new Blueprint(id, new Vec3i(5, 2, 5), oldDrawing(furnished));
	}

	static Blueprint newBlueprint(ResourceLocation id, boolean roof) {
		return new Blueprint(id, new Vec3i(7, roof ? 3 : 2, 3), newDrawing(roof));
	}

	/** Ships {@code drawing} as the blueprint {@code id} (a fresh template, as a new jar's after a restart). */
	static void install(ServerLevel level, ResourceLocation id, Blueprint drawing) {
		StructureTemplateManager manager = level.getStructureManager();
		manager.remove(id);
		manager.getOrCreate(id).load(Lookup.lookup(BuiltInRegistries.BLOCK), BlueprintFiles.toStructureNbt(drawing));
	}

	/**
	 * The new jar: {@code id}'s drawing replaced by the new one, the old one shipped in its history (as {@code history.py}
	 * keeps it).
	 */
	static void rebuild(ServerLevel level, ResourceLocation id, boolean furnishedOld) {
		install(level, id, newBlueprint(id, false));
		Refreshes.addHistory(id, "before_rebuild", oldBlueprint(id, furnishedOld));
	}

	static ResourceLocation id(GameTestHelper helper, String what) {
		BlockPos at = helper.absolutePos(HOUSE);
		// ends in a letter: a name ending _<digits> would read as an upgrade tier
		return ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_rb1117e_" + what + "_" + Long.toHexString(helper.getLevel().getGameTime())
			+ "_" + Math.abs(at.getX()) + "x" + Math.abs(at.getZ()) + "h");
	}

	static void put(GameTestHelper helper, BlockPos rel, BlockState state) {
		helper.getLevel().setBlock(helper.absolutePos(rel), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	static BlockPos at(BlockPos local) {
		return HOUSE.offset(local);
	}

	/** Common: a cleared area, the hall, the owner, the drawing {@code id} as first shipped, the clean-up. */
	static Stage stage(GameTestHelper helper, ResourceLocation id, boolean furnished) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS;
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = 16;
		helper.setDayTime(2000);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (BlockPos p : BlockPos.betweenClosed(HOUSE, HOUSE.offset(6, 3, 4))) {
			put(helper, p.immutable(), Blocks.AIR.defaultBlockState());
		}
		install(level, id, oldBlueprint(id, furnished));
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> {
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			Refreshes.removeHistory(id);
			level.getStructureManager().remove(id);
			level.getStructureManager().remove(io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades.upgradeOf(id));
			BuildSiteManager.get(level).forgetFinished(placement);
			level.getServer().getPlayerList().remove(owner);
		});
		helper.setBlock(STORE, Blocks.CHEST);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		return new Stage(level, id, placement, owner);
	}

	/**
	 * A finished house standing as the OLD drawing drew it (furnished: 7 emeralds in its chest), recorded finished for
	 * {@code owner}; with {@code playersBlocks} the owner's glowstone on its back wall and outside its plot.
	 */
	static void standOld(GameTestHelper helper, Stage s, boolean furnished, boolean playersBlocks) {
		for (Blueprint.Entry e : oldDrawing(furnished)) {
			put(helper, at(e.pos()), e.state());
		}
		if (furnished) {
			Container chest = helper.getBlockEntity(at(CHEST));
			chest.setItem(0, new ItemStack(Items.EMERALD, 7));
		}
		if (playersBlocks) {
			put(helper, at(OWN_ON_OLD), Blocks.GLOWSTONE.defaultBlockState());
			put(helper, at(OWN_OUTSIDE), Blocks.GLOWSTONE.defaultBlockState());
		}
		BuildSiteManager.get(s.level()).recordFinished(s.id(), s.placement(), s.owner().getUUID());
	}

	/** Someone sleeps in the old drawing's bed (its home ticket taken), once the bed's POI is there. */
	static void claimBed(GameTestHelper helper) {
		BlockPos head = helper.absolutePos(at(BED_HEAD));
		var pois = helper.getLevel().getPoiManager();
		helper.assertTrue(pois.existsAtPosition(PoiTypes.HOME, head), "(setup) the bed has no home POI");
		pois.take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(head), head, 1);
		helper.assertTrue(pois.getFreeTickets(head) == 0, "(setup) the bed wasn't claimed");
	}

	static boolean bedClaimed(GameTestHelper helper) {
		BlockPos head = helper.absolutePos(at(BED_HEAD));
		var pois = helper.getLevel().getPoiManager();
		return pois.existsAtPosition(PoiTypes.HOME, head) && pois.getFreeTickets(head) == 0;
	}

	static Villager builder(GameTestHelper helper, Stage s) {
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(s.level(), builder, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(s.owner().getUUID(), s.owner().getGameProfile().getName()));
		return builder;
	}

	/** A restart: the sites and the builder saved and loaded back. */
	static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		if (old == null) {
			return null;
		}
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}

	static long sites(Stage s) {
		return BuildSiteManager.get(s.level()).all().stream().filter(site -> site.placement().equals(s.placement())).count();
	}

	/** Where in the house's plot (old and new together, roof included) {@code block} stands, as local spots. */
	static List<BlockPos> where(GameTestHelper helper, Block block) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(6, 2, 4))) {
			if (helper.getLevel().getBlockState(helper.absolutePos(at(p))).is(block)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	static UUID uuid(Villager v) {
		return v == null ? null : v.getUUID();
	}
}
