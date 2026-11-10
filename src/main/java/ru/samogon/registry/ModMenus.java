package ru.samogon.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.samogon.SamogonMod;
import ru.samogon.block.ProcessBlock;
import ru.samogon.menu.AgingBarrelMenu;
import ru.samogon.menu.BreweryMenu;
import ru.samogon.menu.ProcessMenu;

/** Container menus for the processing machines. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SamogonMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ProcessMenu>> PROCESS =
            MENUS.register("process", () -> IMenuTypeExtension.create((id, inventory, data) -> {
                var pos = data.readBlockPos();
                ProcessBlock.Kind kind = ProcessBlock.Kind.values()[Math.floorMod(
                        data.readUnsignedByte(), ProcessBlock.Kind.values().length)];
                return ProcessMenu.client(id, inventory, pos, kind);
            }));

    public static final DeferredHolder<MenuType<?>, MenuType<AgingBarrelMenu>> AGING_BARREL =
            MENUS.register("aging_barrel", () -> IMenuTypeExtension.create((id, inventory, data) ->
                    AgingBarrelMenu.client(id, inventory, data.readBlockPos())));

    public static final DeferredHolder<MenuType<?>, MenuType<BreweryMenu>> BREWERY =
            MENUS.register("brewery", () -> IMenuTypeExtension.create((id, inventory, data) ->
                    BreweryMenu.client(id, inventory, data.readBlockPos())));

    private ModMenus() {}
}
