package io.github.jcondedata.aliveworkplace.work;

/**
 * Marks a block entity whose items belong to one player (a mailbox, a shop's price list): workers never
 * treat it as a supply chest, so they don't take from it or drop their haul into it.
 */
public interface PrivateContainer {
}
