package io.github.jcondedata.aliveworkplace.compat.raiddens;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.RaidDenLoot;
import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;

/**
 * Cobblemon Raid Dens' tier loot tables, rolled the way its Raid Pouch rolls them (38.4). The tables' pools check the
 * pouch being opened ({@code cobblemonraiddens:raid_pouch}) and whether it's bonus loot ({@code bonus_loot}), loot
 * parameters only Raid Dens holds ({@code RaidLootContexts}, checked against 0.11.3). Raid Dens isn't on the compile
 * classpath, so its three public fields are read by name once at startup; if they're gone the filling stays off and
 * village challenges bring no loot home (the den still goes quiet).
 */
public final class RaidDensLoot implements RaidDenLoot {
	static final String CONTEXTS = "com.necro.raid.dens.common.loot.context.RaidLootContexts";

	private final LootContextParam<ItemStack> pouch;
	private final LootContextParam<Boolean> bonus;
	private final LootContextParamSet use;

	private RaidDensLoot(LootContextParam<ItemStack> pouch, LootContextParam<Boolean> bonus, LootContextParamSet use) {
		this.pouch = pouch;
		this.bonus = bonus;
		this.use = use;
	}

	/** Fills {@link RaidDenLoot#EXTENSION} when Raid Dens' loot parameters can be found. */
	@SuppressWarnings("unchecked")
	public static void init() {
		try {
			Class<?> contexts = Class.forName(CONTEXTS);
			Object pouch = contexts.getField("RAID_POUCH").get(null);
			Object bonus = contexts.getField("BONUS_LOOT").get(null);
			Object use = contexts.getField("RAID_POUCH_USE").get(null);
			if (pouch instanceof LootContextParam<?> p && bonus instanceof LootContextParam<?> b && use instanceof LootContextParamSet u) {
				RaidDenLoot.EXTENSION.register("cobblemonraiddens", new RaidDensLoot((LootContextParam<ItemStack>) p, (LootContextParam<Boolean>) b, u));
				return;
			}
			AliveWorkplace.LOG.warn("Alive Workplace: Raid Dens' loot parameters aren't what 0.11.3 had; village den challenges bring no loot home");
		} catch (ReflectiveOperationException | LinkageError e) {
			AliveWorkplace.LOG.warn("Alive Workplace: can't find Raid Dens' loot parameters ({}); village den challenges bring no loot home", e.toString());
		}
	}

	@Override
	public List<ItemStack> roll(ServerLevel level, ResourceKey<LootTable> table, RandomSource random) {
		ItemStack opened = new ItemStack(BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(RaidDens.DEN.getNamespace(), "raid_pouch"))
			.orElse(Items.BUNDLE));
		LootParams params = new LootParams.Builder(level).withParameter(pouch, opened).withParameter(bonus, false).create(use);
		List<ItemStack> out = new ArrayList<>();
		level.getServer().reloadableRegistries().getLootTable(table).getRandomItems(params, random.nextLong(), out::add);
		return out;
	}
}
