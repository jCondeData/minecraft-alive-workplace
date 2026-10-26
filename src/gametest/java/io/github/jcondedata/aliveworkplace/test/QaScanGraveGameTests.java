package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane B), from the builder's promise (builder page: "Builders never copy what is inside containers";
 * BlockEntityData: a blueprint's block data carries looks, "anything that would hand out free items or mobs is not"
 * copied, "a caged mob") and the Scan Tool's guide page: a village's grave holding a dead Librarian (as the Undertaker
 * revives them) is scanned with real clicks and built again by a builder: the new grave is empty, nobody to revive in it,
 * so a scan never copies a villager.
 */
public class QaScanGraveGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);

	//$ gametest_ticks_batch HUGE '2400' '"qaScanGrave"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanGrave")
	public void aScannedGraveIsBuiltEmpty(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager dead = helper.spawn(EntityType.VILLAGER, new BlockPos(20, 2, 20));
		dead.setVillagerData(dead.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN).setLevel(3));
		dead.setCustomName(Component.literal("Odo"));
		dead.kill();
		List<BlockPos> graves = Graves.near(level, helper.absolutePos(new BlockPos(20, 2, 20)), 8);
		helper.assertTrue(!graves.isEmpty(), "the Librarian left no grave");
		BlockPos grave = graves.get(0);
		helper.assertTrue(level.getBlockEntity(grave) instanceof GraveBlockEntity g && !g.villager().isEmpty(), "the grave holds nobody");
		BlockPos rel = new BlockPos(20, 2, 20).offset(grave.subtract(helper.absolutePos(new BlockPos(20, 2, 20))));

		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, rel.below().west());
		QaStageThreeGameTests.click(helper, player, rel.east());
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the scan said " + QaStageThreeGameTests.last(seen)
			+ " (grave at " + rel.toShortString() + ": " + level.getBlockState(grave) + "; marks " + player.getMainHandItem().get(io.github.jcondedata.aliveworkplace.registry.ModComponents.SCAN)
			+ ", grave abs " + grave.toShortString() + ")");
		ItemStack blueprint = ItemStack.EMPTY;
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT)) {
				blueprint = s;
			}
		}
		helper.assertTrue(!blueprint.isEmpty(), "the scan gave no blueprint");
		ResourceLocation id = BlueprintItem.data(blueprint).orElseThrow().structure();

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
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException("the builder didn't take the scanned grave " + id);
		}
		BlockPos copy = origin.offset(1, 1, 0);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status());
			helper.assertTrue(level.getBlockState(copy).is(ModBlocks.GRAVE), "no grave built where the scan had it: " + level.getBlockState(copy));
			GraveBlockEntity built = (GraveBlockEntity) level.getBlockEntity(copy);
			helper.assertTrue(built.villager().isEmpty(), "the grave built from a scan holds the dead villager again ("
				+ built.villager().getString("CustomName") + ", " + built.villager().getAllKeys().size() + " keys of villager data): the Undertaker could revive a copy");
			LOGGER.info("qa scan grave: the grave built from a scan is empty");
		});
	}
}
