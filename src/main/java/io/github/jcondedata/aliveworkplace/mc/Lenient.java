package io.github.jcondedata.aliveworkplace.mc;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saved lists and maps that load what they can (B791). A save may name an item of a mod taken out of the pack since; the
 * strict codecs ({@link Codec#listOf}, {@link Codec#unboundedMap}) then fail as a whole, and Fabric's attachment loader
 * drops a whole attachment on any error (a builder's whole bag for one stack). These decode entry by entry, drop only
 * the entries that can't be read (with a warning, as vanilla drops a chest's unknown stack) and succeed with the rest.
 * They write exactly what the strict codecs write, so saves are unchanged both ways.
 */
public final class Lenient {
	/** A list of {@code element}, keeping the entries that load. {@code what} names it in the warning. */
	public static <E> Codec<List<E>> list(Codec<E> element, String what) {
		Codec<List<E>> strict = element.listOf();
		return new Codec<>() {
			@Override
			public <T> DataResult<Pair<List<E>, T>> decode(DynamicOps<T> ops, T input) {
				return ops.getList(input).map(entries -> {
					List<E> out = new ArrayList<>();
					entries.accept(t -> {
						DataResult<E> r = element.parse(ops, t);
						r.result().ifPresentOrElse(out::add, () -> warn(what, r));
					});
					return Pair.of(ImmutableList.copyOf(out), input);
				});
			}

			@Override
			public <T> DataResult<T> encode(List<E> value, DynamicOps<T> ops, T prefix) {
				return strict.encode(value, ops, prefix);
			}

			@Override
			public String toString() {
				return "Lenient.list[" + element + "]";
			}
		};
	}

	/** A map of {@code key} to {@code value}, keeping the entries that load. {@code what} names it in the warning. */
	public static <K, V> Codec<Map<K, V>> map(Codec<K> key, Codec<V> value, String what) {
		Codec<Map<K, V>> strict = Codec.unboundedMap(key, value);
		return new Codec<>() {
			@Override
			public <T> DataResult<Pair<Map<K, V>, T>> decode(DynamicOps<T> ops, T input) {
				return ops.getMapEntries(input).map(entries -> {
					Map<K, V> out = new LinkedHashMap<>();
					entries.accept((kt, vt) -> {
						DataResult<K> k = key.parse(ops, kt);
						DataResult<V> v = value.parse(ops, vt);
						if (k.result().isPresent() && v.result().isPresent()) {
							out.put(k.result().get(), v.result().get());
						} else {
							warn(what, k.result().isPresent() ? v : k);
						}
					});
					return Pair.of(ImmutableMap.copyOf(out), input);
				});
			}

			@Override
			public <T> DataResult<T> encode(Map<K, V> map, DynamicOps<T> ops, T prefix) {
				return strict.encode(map, ops, prefix);
			}

			@Override
			public String toString() {
				return "Lenient.map[" + key + " -> " + value + "]";
			}
		};
	}

	private static void warn(String what, DataResult<?> failed) {
		String why = failed.error().map(DataResult.Error::message).orElse("unreadable");
		AliveWorkplace.LOG.warn("Dropped an entry of a saved {} that no longer loads (a mod taken out?): {}", what, why);
	}

	private Lenient() {
	}
}
