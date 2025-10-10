package ch.ofte.server;

import ch.ofte.commons.exception.BaseLogicException;
import ch.ofte.commons.exception.InvalidDataException;
import ch.ofte.commons.exception.InvalidServiceException;
import ch.ofte.commons.exception.NoSuchServiceDefinitionException;
import ch.ofte.server.util.JsonProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

@RestController("apiController")
@RestControllerAdvice
@RequestMapping("/api")
public class BaseApiController {
    @GetMapping("/**")
    public String doGet() {
        return "";
    }

    @Autowired
    private ApplicationContext applicationContext;
    private final Logger logger = LogManager.getLogger(this.getClass());

    private Method findCorrectMethod(Class<?> beanClass, String serviceName, String method) {
        for(Method m : beanClass.getDeclaredMethods()) {
            if (m.getName().equals(method)) {
                return m;
            }
        }
        throw new InvalidServiceException(serviceName);
    }

    private record InvokeOption(boolean streamingInvoke) {
        static InvokeOption withStreaming() {
            return new InvokeOption(true);
        }
        static InvokeOption withOutStreaming() {
            return new InvokeOption(false);
        }
    }

    private Object invoke(String serviceName, String method, JsonNode body) {
        return invoke(serviceName, method, body, InvokeOption.withOutStreaming());
    };

    private Object invoke(String serviceName, String method, JsonNode body, InvokeOption option) {
        try {
            Object bean = applicationContext.getBean(serviceName);
            Class<?> beanClass = bean.getClass();
            if (!beanClass.isAnnotationPresent(Service.class)) throw new NoSuchServiceDefinitionException(serviceName);

            Method m = findCorrectMethod(beanClass, serviceName, method);

            if (Flux.class.isAssignableFrom(m.getReturnType()) && (option == null || !option.streamingInvoke)) {
                throw new InvalidServiceException(serviceName, "This method is required to be called with the \"s\" prefix! " +
                        "Example usage: /api/s/serviceName/methodName");
            }

            Optional<Class<?>> parameterType = Arrays.stream(m.getParameterTypes()).findFirst();
            if (parameterType.isEmpty()) { // Service method accept 0 argument
                return m.invoke(bean);
            }

            String jsonBody = body == null ? "" : body.toString();

            Object argument = JsonProcessor.toObject(jsonBody, parameterType.get());
            if (argument != null && !argument.getClass().getSimpleName().equalsIgnoreCase(parameterType.get().getSimpleName())) {
                throw new InvalidDataException(String.format("Invalid data type, %s is provided, but %s is required.",
                        argument.getClass().getSimpleName(), parameterType.get().getSimpleName()));
            }
            return m.invoke(bean, argument);
        } catch (NoSuchBeanDefinitionException noBeanException) {
            throw new NoSuchServiceDefinitionException(serviceName);
        } catch (BeansException | IllegalAccessException | InvocationTargetException ex) {
            throw new InvalidServiceException(serviceName);
        }
    }

    @PostMapping(path = "/{serviceName}/{method}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<String> doRpc(@PathVariable String serviceName, @PathVariable String method, @RequestBody(required = false) JsonNode body) {
        return new ResponseEntity<>(JsonProcessor.toJson(invoke(serviceName, method, body)), HttpStatus.OK);
    }

    @PostMapping(path = "/s/{serviceName}/{method}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public ResponseEntity<Flux<?>> doStreamingRpc(@PathVariable String serviceName, @PathVariable String method, @RequestBody(required = false) JsonNode body) {
        return new ResponseEntity<>((Flux<?>) invoke(serviceName, method, body, InvokeOption.withStreaming()), HttpStatus.OK);
    }

    @ExceptionHandler(exception = BaseLogicException.class, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleBaseLogicException(BaseLogicException ex) {
        logger.error("> Logical error occurred.");
        logger.error(ex.getMessage(), ex.getCause());
        return new ResponseEntity<>(JsonProcessor.toJson(ex), HttpStatusCode.valueOf(ex.getCode()));
    }
}
