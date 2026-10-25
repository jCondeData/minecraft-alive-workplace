package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane b, 2026-10-25) for B388, from its spec, not the code: "a Carpenter can't take the crafting table in a
 * spruce-style building ... expected: the vanilla crafting table stays". The data tests (QaCarvedLibraryBugTests,
 * QaCarvedLibraryCompatTests) check the style's swap rules; this one goes the player's way: a builder builds the Starter
 * Cottage in the Spruce style with Carved Wood installed, its crafting table is the vanilla one and a Carpenter's job
 * site, and a jobless villager by it, sneak-right-clicked with oak planks (villager-jobs.md: "Carpenter | Crafting Table +
 * planks"), becomes a Carpenter working at that table.
 */
public class QaSpruceCarpenterCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";

	//$ gametest_ticks_batch HUGE_AREA '24000' '"qa_spruce_carpenter_hire"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 24000, batch = "qa_spruce_carpenter_hire")
	public void aCarpenterIsHiredAtTheSpruceCottagesCraftingTable(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = BlueprintStyles.styled(AliveWorkplace.id("starter_cottage"), "spruce");
		Blueprint styled = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("no " + id));
		BuildSite site = start(helper, id);
		BlockPos origin = site.placement().origin();
		helper.assertTrue(styled.blocks().stream().anyMatch(e -> id(e.state()).startsWith("carved_wood:")),
			"the spruce cottage has no Carved Wood block: this test checks nothing about the style");
		List<BlockPos> tables = styled.blocks().stream().filter(e -> e.state().is(Blocks.CRAFTING_TABLE)).map(e -> origin.offset(e.pos())).toList();
		long otherTables = styled.blocks().stream().filter(e -> id(e.state()).contains("crafting_table") && !e.state().is(Blocks.CRAFTING_TABLE)).count();
		helper.assertTrue(tables.size() == 1 && otherTables == 0, "the spruce cottage's crafting tables: " + tables.size() + " vanilla, "
			+ otherTables + " other (the vanilla cottage has one, the Carpenter's)");
		BlockPos table = tables.get(0);
		java.util.concurrent.atomic.AtomicReference<Villager> hired = new java.util.concurrent.atomic.AtomicReference<>();
		helper.succeedWhen(() -> {
			if (hired.get() == null) {
				helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
					"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
				// (whether every other block stands is B433's: the Spruce cottage's lanterns on the flower boxes are skipped)
				helper.assertTrue(level.getBlockState(table).is(Blocks.CRAFTING_TABLE), "built at the table's spot: " + level.getBlockState(table));
				helper.assertTrue(level.getPoiManager().getType(table).map(h -> h.is(ModVillagers.CRAFTING_TABLE_POI)).orElse(false),
					"the built crafting table is no Carpenter's job site: " + level.getPoiManager().getType(table));
				for (Villager other : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(48), o -> !o.getUUID().equals(site.builder()))) {
					other.discard();
				}
				BlockPos stand = standNear(level, table);
				Villager v = EntityType.VILLAGER.create(level);
				v.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0, 0);
				v.setNoAi(true);
				level.addFreshEntity(v);
				ServerPlayer player = helper.makeMockServerPlayerInLevel();
				player.setGameMode(GameType.SURVIVAL);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 4));
				player.setShiftKeyDown(true);
				player.moveTo(v.getX() + 1.5, v.getY(), v.getZ(), 90f, 0f);
				player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(v, true, InteractionHand.MAIN_HAND,
					new Vec3(0, v.getBbHeight() / 2, 0)));
				if (v.getVillagerData().getProfession() == VillagerProfession.NONE) {
					player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(v, true, InteractionHand.MAIN_HAND));
				}
				hired.set(v);
			}
			Villager v = hired.get();
			helper.assertTrue(v.getVillagerData().getProfession() == ModVillagers.CARPENTER
				&& v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).map(table::equals).orElse(false),
				"sneak-clicked with planks by the spruce cottage's crafting table, the villager is " + v.getVillagerData().getProfession()
					+ " with job site " + v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> helper.relativePos(g.pos()).toShortString()).orElse("-")
					+ " (the table at " + helper.relativePos(table).toShortString() + ")");
		});
	}

	/** A builder on {@code id} at (9, 2, 3), his chests (not barrels: a barrel is a job site) holding what the plan costs. */
	private static BuildSite start(GameTestHelper helper, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		new WorkplaceConfig().apply();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 3)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + id);
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos chest = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(chest, Blocks.CHEST);
			Container container = helper.getBlockEntity(chest);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		return site;
	}

	/** An open floor spot within two blocks of {@code pos}. */
	private static BlockPos standNear(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.withinManhattan(pos, 2, 1, 2)) {
			if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.below()).isSolidRender(level, p.below())) {
				return p.immutable();
			}
		}
		throw new GameTestAssertException("no room beside the crafting table at " + pos);
	}

	private static String id(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}
}
