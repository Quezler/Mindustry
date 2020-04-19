package mindustry.world.blocks.power;

import arc.*;
import mindustry.content.*;
import mindustry.plugin.*;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.world.*;

import static mindustry.Vars.netServer;

public class SingleTypeGenerator extends ItemLiquidGenerator{

    protected final int timerSolar = timers++;

    public SingleTypeGenerator(String name){
        super(name);
        defaults = true;
    }

    @Override
    protected float getItemEfficiency(Item item){
        return 1f;
    }

    @Override
    protected float getLiquidEfficiency(Liquid liquid){
        return 0f;
    }

    @Override
    public void update(Tile tile){
        super.update(tile);

        if(tile.block == Blocks.differentialGenerator && Nydus.dyson_sphere.active()){
            if(tile.entity.timer.get(timerSolar, 60f * 2.5f * 4f)){
                int max = tile.entity.proximity().count(t -> t.block == Blocks.largeSolarPanel);
                max = acceptStack(Items.pyratite, max, tile, null);
                if (max > 0) netServer.titanic.add(tile);
                if (max > 0) tile.entity.items.add(Items.pyratite, max);
            }
        }
    }

    @Override
    public void unloaded(Tile tile, Item item, Tile other){
        if(item == Items.pyratite){
            Core.app.post(() -> {
                if(tile.entity != null) tile.entity.kill();
            });
        }
    }
}
