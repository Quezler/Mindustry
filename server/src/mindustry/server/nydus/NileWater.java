package mindustry.server.nydus;

import arc.*;
import arc.func.*;
import mindustry.content.*;
import mindustry.plugin.*;
import mindustry.type.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class NileWater implements ApplicationListener{

    private Boolf<Block> filter = b -> b.hasLiquids && b.consumes.liquidfilters.get(Liquids.water.id) && b.category != Category.turret;

    @Override
    public void init(){
        netServer.admins.addChatFilter((player, text) -> {

            if(Nydus.water_from_the_nile.active()){
                if(text.contains("water from the nile")){
                    for(int x = 0; x < world.width(); x++){
                        for(int y = 0; y < world.height(); y++){
                            Tile t = world.tile(x, y);
                            if(t != null && t.getTeam() == player.getTeam() && filter.get(t.block) && t.entity != null && t.entity.liquids != null){
                                t.block.handleLiquid(t, t, Liquids.water, 100);
                                netServer.titanic.add(t);
                            }
                        }
                    }
                }
            }

            // content.blocks().each(filter, b -> Log.info(b.name));

            // multi-press
            // cryofluidmixer
            // force-projector
            // turbine-generator
            // mechanical-drill
            // pneumatic-drill
            // laser-drill
            // blast-drill
            // cultivator
            // oil-extractor

            return text;
        });
    }
}
