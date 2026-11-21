package ru.samogon.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ru.samogon.SamogonMod;
import ru.samogon.block.ProcessBlock;
import ru.samogon.menu.AgingBarrelMenu;
import ru.samogon.menu.BanyaStoveMenu;
import ru.samogon.menu.BreweryMenu;
import ru.samogon.menu.ProcessMenu;

/** Container menus for the processing machines. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SamogonMod.MOD_ID);

    public static final RegistryObject<MenuType<ProcessMenu>> PROCESS =
            MENUS.register("process", () -> IForgeMenuType.create((id, inventory, data) -> {
                var pos = data.readBlockPos();
                ProcessBlock.Kind kind = ProcessBlock.Kind.values()[Math.floorMod(
                        data.readUnsignedByte(), ProcessBlock.Kind.values().length)];
                return ProcessMenu.client(id, inventory, pos, kind);
            }));

    public static final RegistryObject<MenuType<AgingBarrelMenu>> AGING_BARREL =
            MENUS.register("aging_barrel", () -> IForgeMenuType.create((id, inventory, data) ->
                    AgingBarrelMenu.client(id, inventory, data.readBlockPos())));

    public static final RegistryObject<MenuType<BreweryMenu>> BREWERY =
            MENUS.register("brewery", () -> IForgeMenuType.create((id, inventory, data) ->
                    BreweryMenu.client(id, inventory, data.readBlockPos())));

    public static final RegistryObject<MenuType<BanyaStoveMenu>> BANYA_STOVE =
            MENUS.register("banya_stove", () -> IForgeMenuType.create((id, inventory, data) ->
                    BanyaStoveMenu.client(id, inventory, data.readBlockPos())));

    private ModMenus() {}
}
