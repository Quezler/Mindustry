package mindustry.server.nydus;

import arc.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.plugin.*;
import mindustry.world.blocks.power.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class PowerFlower implements ApplicationListener{

    @Override
    public void init(){
        Events.on(BlockBuildEndEvent.class, event -> {
            if(!Nydus.dyson_sphere.active()) return;
            Core.app.post(() -> {
                final int[] solar = {0};
                state.teams.getActive().each(teamData -> {
                    indexer.getAllied(teamData.team, BlockFlag.solar).each(tile -> {
                        if(tile.block instanceof SolarGenerator){
                            solar[0] += (tile.block.size * tile.block.size);
                        }
                    });
                });

                float multiplier = 1f + (solar[0] * 0.01f);

                if(multiplier == state.rules.solarPowerMultiplier) return;
                state.rules.solarPowerMultiplier = multiplier;
                Call.onSetRules(state.rules);
            });
        });
    }
}
