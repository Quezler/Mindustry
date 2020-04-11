package mindustry.world.blocks.production;

import mindustry.content.*;
import mindustry.world.*;

import static mindustry.Vars.netServer;

public class Pulverizer extends GenericCrafter{

    public Pulverizer(String name){
        super(name);

        sync = true;
    }

    @Override
    public void update(Tile tile){
        super.update(tile);

        if(tile.drop() != null){
            if(tile.entity.items.total() <= 1) netServer.titanic.add(tile);
            tile.entity.items.set(Items.scrap, 10);
        }
    }
}
