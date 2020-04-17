package mindustry.entities.units;

import arc.math.*;
import mindustry.*;
import mindustry.entities.type.*;
import mindustry.gen.*;
import mindustry.plugin.*;
import mindustry.type.*;

public class UnitDrops{

    public static void dropItems(BaseUnit unit){
        TileEntity core = unit.getClosestEnemyCore();

        if(!Nydus.enemy_item_drops.active() || unit.item().amount == 0 || unit.item().item == null) return;

        if(core != null && core.dst(unit) <= Vars.mineTransferRange * 3){
            unit.item().amount = core.tile.block().acceptStack(unit.item().item, unit.item().amount, core.tile, null);
            Call.transferItemTo(unit.item().item, unit.item().amount, unit.x + Mathf.range(2f), unit.y + Mathf.range(2f), core.tile);
        }
    }

    public static void seed(BaseUnit unit){
        if(!Vars.state.rules.unitDrops) return;

        unit.item().item = Vars.content.items().select(i -> i.type == ItemType.material).random();
        unit.item().amount = (int)(unit.maxHealth() / 50);
    }
}
