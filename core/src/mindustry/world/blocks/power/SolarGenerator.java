package mindustry.world.blocks.power;

import arc.func.*;
import arc.math.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.plugin.*;
import mindustry.plugin.spidersilk.SpiderSilk.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class SolarGenerator extends PowerGenerator{

    public SolarGenerator(String name){
        super(name);
        // Remove the BlockFlag.producer flag to make this a lower priority target than other generators.
        flags = EnumSet.of();
        entityType = GeneratorEntity::new;
    }

    @Override
    public void update(Tile tile){
        tile.<GeneratorEntity>ent().productionEfficiency = state.rules.solarPowerMultiplier < 0 ? (state.rules.lighting ? 1f - state.rules.ambientLight.a : 1f) : state.rules.solarPowerMultiplier;

        if(tile.block == Blocks.largeSolarPanel && Nydus.zombies_on_your_lawn.active()){
            if(Mathf.chance(0.001f) && !tile.getTeam().cores().isEmpty()) Call.transferItemTo(Items.phasefabric, 1, tile.drawx(), tile.drawy(), state.teams.closestCore(tile.drawx(), tile.drawy(), tile.getTeam()).tile);
        }
    }

    @Override
    public void setStats(){
        super.setStats();
        // Solar Generators don't really have an efficiency (yet), so for them 100% = 1.0f
        stats.remove(generationType);
        stats.add(generationType, powerProduction * 60.0f, StatUnit.powerSecond);
    }

    @Override
    public void silk(Tile tile, Cons<Silk> cons){
        if(tile.block == Blocks.solarPanel){
            if(tile.getAroundTiles(new Array<>()).count(i -> i.block == Blocks.air || i.block == Blocks.solarPanel || i.block instanceof StaticWall) == 8){

                float aligned =
                (tile.getNearby(+3, +0) != null && tile.getNearby(+3, +0).block == Blocks.largeSolarPanel ? 1f : 0f) +
                (tile.getNearby(-3, -0) != null && tile.getNearby(-3, -0).block == Blocks.largeSolarPanel ? 1f : 0f) +
                (tile.getNearby(+0, +3) != null && tile.getNearby(+0, +3).block == Blocks.largeSolarPanel ? 1f : 0f) +
                (tile.getNearby(-0, -3) != null && tile.getNearby(-0, -3).block == Blocks.largeSolarPanel ? 1f : 0f);

                if(aligned > 0f){
                    cons.get(new Silk(tile){{
                        requirements = Blocks.largeSolarPanel.requirements;
                        trigger = () -> construct(Blocks.largeSolarPanel);
                        size = 3;
                    }});
                }
            }
        }
    }
}
