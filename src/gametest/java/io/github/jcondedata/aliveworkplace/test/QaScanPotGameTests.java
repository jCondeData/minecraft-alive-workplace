package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
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
 * QA (lane B), from the builder's promise (builder page: "Builders never copy what is inside containers"; a blueprint's
 * block data carries looks, "anything that would hand out free items ... is not" copied) and the Scan Tool's guide page:
 * a decorated pot made with four pottery sherds (rare finds from digging), on a stone, is scanned with real clicks, and a
 * builder builds it from a chest holding one plain decorated pot and a stone. Broken with a pickaxe, the built pot shatters into its sherds: none of
 * them may come from nowhere.
 */
public class QaScanPotGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos POT = new BlockPos(20, 2, 20);

	//$ gametest_ticks_batch HUGE '2400' '"qaScanPot"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanPot")
	public void aScannedSherdPotGivesNoFreeSherds(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(POT.below(), net.minecraft.world.level.block.Blocks.STONE); // a stand, so the Table's preview of the scan has a block it draws
		helper.setBlock(POT, net.minecraft.world.level.block.Blocks.DECORATED_POT);
		net.minecraft.world.level.block.entity.BlockEntity pot = helper.getBlockEntity(POT);
		net.minecraft.nbt.CompoundTag data = pot.saveWithoutMetadata(level.registryAccess());
		net.minecraft.nbt.ListTag sherds = new net.minecraft.nbt.ListTag();
		for (String s : List.of("angler", "archer", "blade", "brewer")) {
			sherds.add(net.minecraft.nbt.StringTag.valueOf("minecraft:" + s + "_pottery_sherd"));
		}
		data.put("sherds", sherds);
		pot.loadWithComponents(data, level.registryAccess());
		helper.assertTrue(pot.saveWithoutMetadata(level.registryAccess()).getList("sherds", net.minecraft.nbt.Tag.TAG_STRING).size() == 4,
			"the pot took no sherds: " + pot.saveWithoutMetadata(level.registryAccess()));

		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, POT.below());
		QaStageThreeGameTests.click(helper, player, POT);
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the scan said " + QaStageThreeGameTests.last(seen));
		ItemStack blueprint = ItemStack.EMPTY;
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT)) {
				blueprint = s;
			}
		}
		helper.assertTrue(!blueprint.isEmpty(), "the scan gave no blueprint");
		ResourceLocation id = BlueprintItem.data(blueprint).orElseThrow().structure();
		// The scanned pot itself, broken with a pickaxe, shatters into its four sherds (so a break here is a real one)
		BlockPos source = helper.absolutePos(POT);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
		player.teleportTo(source.getX() + 0.5, source.getY(), source.getZ() + 1.5);
		player.gameMode.destroyBlock(source);
		int fromSource = sherdsAround(level, source);
		helper.assertTrue(fromSource == 4, "the scanned pot itself, broken with a pickaxe, gave " + fromSource + " sherds, not its 4");
		level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(source).inflate(2)).forEach(e -> e.discard());

		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, net.minecraft.world.level.block.Blocks.CHEST);
		((net.minecraft.world.Container) helper.getBlockEntity(CHEST)).setItem(0, new ItemStack(net.minecraft.world.item.Items.DECORATED_POT));
		((net.minecraft.world.Container) helper.getBlockEntity(CHEST)).setItem(1, new ItemStack(net.minecraft.world.item.Items.STONE));
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlockPos stand = helper.absolutePos(new BlockPos(8, 2, 8));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), stand, Rotation.NONE, Mirror.NONE);
		BlockPos origin = stand.above(); // where the pot stands
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException("the builder didn't take the scanned pot " + id);
		}
		int[] broken = {-1};
		helper.succeedWhen(() -> {
			if (broken[0] >= 0) {
				helper.assertTrue(broken[0] == 0, "the pot a builder built from one plain decorated pot shattered into " + broken[0]
					+ " pottery sherds copied from the scan: free sherds for every build");
				return;
			}
			BuildSite now = BuildSiteManager.get(level).get(site.id());
			helper.assertTrue(now == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			helper.assertTrue(level.getBlockState(origin).is(net.minecraft.world.level.block.Blocks.DECORATED_POT), "no pot built: " + level.getBlockState(origin));
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
			player.teleportTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 1.5);
			player.gameMode.destroyBlock(origin);
			int dropped = sherdsAround(level, origin);
			broken[0] = dropped;
			LOGGER.info("qa scan pot: the built pot, broken with a pickaxe, gave {} sherd(s); the chest held a plain pot", dropped);
			helper.assertTrue(dropped == 0, "the pot a builder built from one plain decorated pot shattered into " + dropped
				+ " pottery sherds copied from the scan: free sherds for every build");
		});
	}

	private static int sherdsAround(ServerLevel level, BlockPos pos) {
		int n = 0;
		for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
			new net.minecraft.world.phys.AABB(pos).inflate(2))) {
			if (item.getItem().is(net.minecraft.tags.ItemTags.DECORATED_POT_SHERDS)) {
				n += item.getItem().getCount();
			}
		}
		return n;
	}
}
