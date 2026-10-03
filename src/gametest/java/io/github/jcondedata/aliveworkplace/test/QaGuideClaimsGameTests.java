package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-1003-1533, ROADMAP 26.2a): the Guide Book tells players how to give villagers their jobs. These follow its
 * pages word for word, through the same sneak-right-click a player makes, for the items the guide names that no other
 * test tries: "Sweet berries, glow berries or an apple at a composter … make an Orchard Keeper", "Raw food at a smoker
 * makes a Chef".
 */
public class QaGuideClaimsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	static final BlockPos STATION = new BlockPos(3, 2, 3);
	static final BlockPos STANDING = new BlockPos(4, 2, 4);

	/**
	 * Each item, given to a villager doing the station's vanilla job ({@code reset}), makes them {@code expected}.
	 * Returns what went wrong, one line per item.
	 */
	static List<String> follow(GameTestHelper helper, Block block, ItemStack reset, VillagerProfession vanilla,
		VillagerProfession expected, List<Item> items) {
		helper.setBlock(STATION, block);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		List<String> wrong = new ArrayList<>();
		for (Item item : items) {
			StationsSpecGameTests.rightClick(player, villager, reset.copy(), true);
			if (StationsSpecGameTests.job(villager) != vanilla) {
				wrong.add("setup: " + reset + " gave " + StationsSpecGameTests.name(StationsSpecGameTests.job(villager)));
				continue;
			}
			StationsSpecGameTests.rightClick(player, villager, new ItemStack(item), true);
			VillagerProfession now = StationsSpecGameTests.job(villager);
			if (now != expected) {
				wrong.add(item + " at a " + block.getName().getString() + " gave " + StationsSpecGameTests.name(now));
			}
		}
		helper.getLevel().getServer().getPlayerList().remove(player);
		return wrong;
	}

	/** Guide, Orchard Keeper page: "Sweet berries, glow berries or an apple at a composter … make an Orchard Keeper." */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaGuideEveryOrchardItemMakesAnOrchardKeeper(GameTestHelper helper) {
		List<String> wrong = follow(helper, Blocks.COMPOSTER, new ItemStack(Items.WHEAT), VillagerProfession.FARMER,
			ModVillagers.ORCHARD_KEEPER, List.of(Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.APPLE));
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		org.slf4j.LoggerFactory.getLogger("aliveworkplace").info("[test] guide: sweet berries, glow berries and an apple each make an orchard keeper");
		helper.succeed();
	}

	/** Guide, Chef page: "Raw food at a smoker makes a Chef." Raw meat and raw fish, the foods vanilla's smoker cooks. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaGuideRawMeatAndFishAtASmokerMakeAChef(GameTestHelper helper) {
		List<String> wrong = follow(helper, Blocks.SMOKER, new ItemStack(Items.LEAD), VillagerProfession.BUTCHER, ModVillagers.CHEF,
			List.of(Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT, Items.COD, Items.SALMON, Items.POTATO));
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		org.slf4j.LoggerFactory.getLogger("aliveworkplace").info("[test] guide: every raw meat, raw fish and a potato make a chef");
		helper.succeed();
	}
}
