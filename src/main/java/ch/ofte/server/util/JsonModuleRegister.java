package ch.ofte.server.util;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.Module;

import java.util.ArrayList;
import java.util.List;

public class JsonModuleRegister {
    private static final List<Module> jacksonModules = new ArrayList<>();

    public static void registerModules(ObjectMapper mapper) {
        mapper.registerModules(jacksonModules);
    }
}
