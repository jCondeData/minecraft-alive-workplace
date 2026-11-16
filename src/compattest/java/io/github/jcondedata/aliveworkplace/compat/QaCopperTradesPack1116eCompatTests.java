package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.ui.CopperTradeMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane E, 2026-11-16), ROADMAP 44.2 in the whole pack (Create, Cobblemon, CobbleDollars and RCT loaded): the trades of
 * the mod's own jobs open in Copperline ({@code aliveworkplace:trades}), never vanilla's {@code minecraft:merchant}, the
 * player's way (a sneak-click: every mod's use-entity hook first, then the villager). The plain suite
 * ({@code CopperlineTradesGameTests}) can't see the jobs that exist only with Create, the Mechanic and the Conductor, whose
 * trades sell Create's parts, nor another mod taking the click first. Written from the spec, not the code.
 */
public class QaCopperTradesPack1116eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final int RESULT = 2;

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
		player.moveTo(at.x, at.y, at.z);
		return player;
	}

	private static Villager villager(GameTestHelper helper, VillagerProfession job, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(1));
		v.setVillagerXp(0);
		return v;
	}

	/** A sneak-click with an empty hand: the use-entity hooks of every mod first, then the villager itself, as the server does. */
	private static void sneakClick(ServerPlayer player, Villager villager) {
		player.setShiftKeyDown(true);
		try {
			InteractionResult hooked = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
			if (hooked == InteractionResult.PASS) {
				player.interactOn(villager, InteractionHand.MAIN_HAND);
			}
		} finally {
			player.setShiftKeyDown(false);
		}
	}

	private static String menuId(ServerPlayer player) {
		if (player.containerMenu == player.inventoryMenu) {
			return "nothing";
		}
		ResourceLocation id = BuiltInRegistries.MENU.getKey(player.containerMenu.getType());
		return String.valueOf(id);
	}

	/** Pays for offer {@code i}, chooses it as the screen's click does and shift-clicks the goods out; what came out. */
	private static ItemStack trade(ServerPlayer player, int i) {
		CopperTradeMenu menu = (CopperTradeMenu) player.containerMenu;
		MerchantOffer offer = menu.getOffers().get(i);
		player.getInventory().clearContent();
		player.getInventory().add(offer.getCostA().copy());
		if (!offer.getCostB().isEmpty()) {
			player.getInventory().add(offer.getCostB().copy());
		}
		menu.setSelectionHint(i);
		menu.tryMoveItems(i);
		menu.clicked(RESULT, 0, ClickType.QUICK_MOVE, player);
		ItemStack got = ItemStack.EMPTY;
		for (int s = 0; s < player.getInventory().getContainerSize(); s++) {
			ItemStack stack = player.getInventory().getItem(s);
			if (ItemStack.isSameItem(stack, offer.getResult())) {
				got = stack.copy();
			}
		}
		return got;
	}

	/**
	 * The Mechanic and the Conductor (jobs only Create brings) sneak-clicked open their trades in Copperline, and every one
	 * of their first-level offers goes through, Create's shafts and vanilla's rails reaching the player's pack.
	 */
	//$ gametest_ticks AREA '100'
	@GameTest(template = AREA, timeoutTicks = 100)
	public void theCreateJobsTradeInCopperlineAndEveryOfferGoesThrough(GameTestHelper helper) {
		helper.assertTrue(ModVillagers.MECHANIC != null && ModVillagers.CONDUCTOR != null, "(control) Create's jobs aren't registered in the pack");
		ServerPlayer player = player(helper);
		List<String> problems = new ArrayList<>();
		int traded = 0;
		for (VillagerProfession job : List.of(ModVillagers.MECHANIC, ModVillagers.CONDUCTOR)) {
			Villager v = villager(helper, job, new BlockPos(3, 2, 3));
			String name = BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).getPath();
			int offers = v.getOffers().size();
			if (offers < 2) {
				problems.add(name + " has " + offers + " offers at level 1");
			}
			sneakClick(player, v);
			if (!(player.containerMenu instanceof CopperTradeMenu) || player.containerMenu.getType() != ModBlocks.TRADES_MENU) {
				problems.add(name + "'s sneak-click opened " + menuId(player) + ", not aliveworkplace:trades");
				player.closeContainer();
				v.discard();
				continue;
			}
			CopperTradeMenu menu = (CopperTradeMenu) player.containerMenu;
			if (menu.getOffers().size() != offers) {
				problems.add(name + "'s screen lists " + menu.getOffers().size() + " of " + offers + " offers");
			}
			for (int i = 0; i < Math.min(offers, menu.getOffers().size()); i++) {
				MerchantOffer offer = v.getOffers().get(i);
				int uses = offer.getUses();
				ItemStack want = offer.getResult().copy();
				ItemStack got = trade(player, i);
				if (got.getCount() < want.getCount() || offer.getUses() != uses + 1) {
					problems.add(name + " offer " + i + " (" + want + "): got " + got + ", uses " + uses + " -> " + offer.getUses());
				} else {
					traded++;
				}
			}
			player.closeContainer();
			if (v.getTradingPlayer() != null) {
				problems.add(name + " still trades with the player after the screen closed");
			}
			v.discard();
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.assertTrue(traded >= 4, "only " + traded + " trades went through");
		helper.succeed();
	}

	/**
	 * Every one of the mod's jobs with trades in the whole pack (Create's and Cobblemon's jobs included), sneak-clicked the
	 * player's way with every other mod's hooks in play: none opens vanilla's merchant screen. A job whose click another
	 * screen of ours (or a compat mod's) answers is fine; vanilla's screen for one of ours is the 44.2 miss.
	 */
	//$ gametest_ticks AREA '100'
	@GameTest(template = AREA, timeoutTicks = 100)
	public void noJobOfTheModOpensVanillasTradeScreenInThePack(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		List<String> copper = new ArrayList<>();
		List<String> vanilla = new ArrayList<>();
		for (ResourceLocation id : BuiltInRegistries.VILLAGER_PROFESSION.keySet()) {
			if (!id.getNamespace().equals(AliveWorkplace.MOD_ID)) {
				continue;
			}
			VillagerProfession job = BuiltInRegistries.VILLAGER_PROFESSION.get(id);
			Villager v = villager(helper, job, new BlockPos(3, 2, 3));
			if (v.getVillagerData().getProfession() != job || v.getOffers().isEmpty()) {
				v.discard();
				continue;
			}
			sneakClick(player, v);
			if (player.containerMenu == player.inventoryMenu) {
				// no screen (a job whose sneak-click another of the mod's hooks answers without one)
			} else if (player.containerMenu.getType() == ModBlocks.TRADES_MENU) {
				copper.add(id.getPath());
			} else if (player.containerMenu.getType() == MenuType.MERCHANT) {
				vanilla.add(id.getPath());
			}
			if (player.containerMenu != player.inventoryMenu) {
				player.closeContainer();
			}
			v.discard();
		}
		helper.assertTrue(vanilla.isEmpty(), "the mod's jobs whose trades open vanilla's screen in the pack: " + vanilla);
		helper.assertTrue(copper.contains("mechanic") && copper.contains("conductor"),
			"Create's jobs didn't trade in Copperline in the pack (Copperline: " + copper + ")");
		helper.succeed();
	}

	/**
	 * A Conductor whose offer was used, saved and loaded again (a server restart): the loaded one still trades in Copperline
	 * with the same offers and the use kept.
	 */
	//$ gametest_ticks AREA '100'
	@GameTest(template = AREA, timeoutTicks = 100)
	public void aConductorSavedAndLoadedStillTradesInCopperlineWithItsOffersKept(GameTestHelper helper) {
		helper.assertTrue(ModVillagers.CONDUCTOR != null, "(control) no Conductor in the pack");
		ServerPlayer player = player(helper);
		Villager before = villager(helper, ModVillagers.CONDUCTOR, new BlockPos(3, 2, 3));
		sneakClick(player, before);
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu, "the Conductor's sneak-click opened " + menuId(player));
		ItemStack got = trade(player, 1);
		helper.assertTrue(!got.isEmpty(), "(control) the Conductor's second offer didn't trade");
		player.closeContainer();
		List<String> offersBefore = new ArrayList<>();
		for (MerchantOffer o : before.getOffers()) {
			offersBefore.add(o.getCostA() + ">" + o.getResult() + "x" + o.getUses());
		}
		CompoundTag saved = new CompoundTag();
		before.saveWithoutId(saved);
		before.discard();

		Villager after = EntityType.VILLAGER.create(helper.getLevel());
		helper.assertTrue(after != null, "(control) no villager");
		after.load(saved);
		after.setUUID(java.util.UUID.randomUUID());
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
		after.moveTo(at.x, at.y, at.z);
		after.setNoAi(true);
		helper.getLevel().addFreshEntity(after);
		List<String> offersAfter = new ArrayList<>();
		for (MerchantOffer o : after.getOffers()) {
			offersAfter.add(o.getCostA() + ">" + o.getResult() + "x" + o.getUses());
		}
		helper.assertTrue(after.getVillagerData().getProfession() == ModVillagers.CONDUCTOR, "the loaded villager isn't a Conductor: "
			+ after.getVillagerData().getProfession());
		helper.assertTrue(offersAfter.equals(offersBefore), "offers before the save " + offersBefore + ", after " + offersAfter);
		sneakClick(player, after);
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu && player.containerMenu.getType() == ModBlocks.TRADES_MENU,
			"the loaded Conductor's sneak-click opened " + menuId(player));
		helper.assertTrue(((CopperTradeMenu) player.containerMenu).getOffers().size() == offersAfter.size(), "the loaded Conductor's screen lists "
			+ ((CopperTradeMenu) player.containerMenu).getOffers().size() + " of " + offersAfter.size() + " offers");
		player.closeContainer();
		after.discard();
		helper.succeed();
	}
}
