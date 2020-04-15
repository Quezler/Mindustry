package mindustry.world.blocks.production;

import arc.*;
import mindustry.content.*;
import mindustry.plugin.*;
import mindustry.world.*;

import static mindustry.Vars.netServer;

public class Pulverizer extends GenericCrafter{

    public Pulverizer(String name){
        super(name);

        sync = true;
        rebuildable = false;
    }

    @Override
    public void update(Tile tile){
        super.update(tile);

        if(Nydus.pulverizer_on_ore.active() && tile.overlay().itemDrop != null){
            if(tile.entity.items.total() <= 1) netServer.titanic.add(tile);
            if(tile.entity.items.total() <= 1) tile.entity.items.set(Items.scrap, 100);
        }
    }

    @Override
    public void unloaded(Tile tile, Tile by){
        Core.app.post(() -> {
            if(tile.entity != null) tile.entity.kill();
        });
    }
}
