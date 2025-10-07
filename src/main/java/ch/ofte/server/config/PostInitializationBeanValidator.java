package ch.ofte.server.config;

import java.lang.reflect.Method;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import ch.ofte.commons.exception.InvalidServiceSpecificationException;

@Component
public class PostInitializationBeanValidator implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();
        if (clazz.isAnnotationPresent(Service.class)) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getParameterCount() > 1) {
                    throw new InvalidServiceSpecificationException(String.format("Method \"%s\" from the service \"%s\" " +
                                    "should have only one parameter",
                            m.getName(), clazz.getSimpleName()));
                }
            }
        }
        return bean;
    }
}