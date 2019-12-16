package io.anuke.mindustry.core.typedefs;

import java.lang.reflect.Field;

public class TSField implements TSConvertable {
    public Class type;
    public TSField(Field field){
        type = field.getType();
    }
    @Override
    public String toString(TypeConverter tc) {
        tc.resolveClass(type);
        return tc.toTSType(type);
    }
}
