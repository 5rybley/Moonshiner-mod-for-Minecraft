package ru.samogon.block;

/**
 * A block that hosts a {@link ProcessBlockEntity} and names the process kind it
 * runs. {@link ProcessBlock} carries one kind per block; a multiblock such as
 * the bottle rack runs its step from the anchor only. Both expose the same
 * {@code kind()}, so the shared block entity, the menu and the block item need
 * no special case.
 */
public interface ProcessMachine {
    ProcessBlock.Kind kind();
}
