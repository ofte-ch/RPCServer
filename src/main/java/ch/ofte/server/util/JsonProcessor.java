package ch.ofte.server.util;

import java.lang.reflect.Method;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ofte.commons.exception.InvalidDataException;

public class JsonProcessor {
    private static Logger logger = LogManager.getLogger(JsonProcessor.class);
    private static ObjectMapper mapper = new ObjectMapper();

    static {
        JsonModuleRegister.registerModules(mapper);
    }

    public static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("Something went wrong while converting object to JSON");
            logger.trace(e);
        }
        return "{}";
    }

    @SuppressWarnings("unchecked")
    public static <T> T toObject(String json, Class<T> clazz) {
        if (Number.class.isAssignableFrom(clazz) || clazz.isPrimitive()) {
            // Support boxed number type
            if (json.isEmpty()) json = "0";
            try {
                if (Number.class.isAssignableFrom(clazz)) return clazz.cast(NumberUtils.createNumber(json));
                if (NumberUtils.isCreatable(json)) {
                    Number n = NumberUtils.createNumber(json);
                    Method m = Number.class.getDeclaredMethod(clazz.getName() + "Value");
                    return (T) m.invoke(n);
                }

                // TODO: handle potential data type
                return null;
            } catch (NumberFormatException e) {
                throw new InvalidDataException("The required type for this service's method is: " + clazz.getSimpleName());
            } catch (Exception e) {
                // Number is unparsable.
                logger.error("Cannot parse number to the correct primitive type!");
                return null;
            }
        }

        try {
            return mapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("Something went wrong while converting JSON to entity: {}", clazz.getSimpleName());
            logger.trace(e);
        }

        try {
            logger.info("Using fallback object parser solution...");
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.error("Cannot create new instance of the provided class: {}", clazz.getSimpleName());
            logger.trace(e);
        }

        return null;
    }
}
