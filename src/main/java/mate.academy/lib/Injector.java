package mate.academy.lib;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import mate.academy.Main;

public class Injector {
    private static final Injector injector = new Injector();
    private static final Set<String> SELF_PACKAGES = children(Main.class.getPackage());
    private static final Set<Class<?>> CLASS_SET = SELF_PACKAGES.stream()
            .flatMap(s -> Injector.findAllClasses(s).stream())
            .collect(Collectors.toSet());
    private static final Set<Class<?>> COMPONENTS = CLASS_SET.stream()
            .filter(c -> c.isAnnotationPresent(Component.class))
            .collect(Collectors.toSet());

    private final Map<Class<?>, Class<?>> implementations = new HashMap<>();

    private Injector() {
    }

    public static Injector getInjector() {
        return injector;
    }

    public static Set<String> children(Package parent) {
        String parentPath = parent.getName().replaceAll("\\.", "/");
        return ClassLoader.getSystemClassLoader()
                .resources(parentPath)
                .map(url -> {
                    try {
                        return Path.of(url.toURI());
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                })
                .flatMap(path -> {
                    try {
                        return Files.walk(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(Files::isDirectory)
                .map(path -> Path.of("./").toAbsolutePath().relativize(path))
                .map(Path::toString)
                .map(path -> path.substring(path.indexOf(parentPath.split("/")[0])))
                .map(path -> path.replaceAll("[\\\\/]", "."))
                .peek(System.out::println)
                .collect(Collectors.toSet());
    }

    public Object getInstance(Class<?> interfaceClazz) {
        if (!CLASS_SET.contains(interfaceClazz)) {
            throw new UnsupportedOperationException();
        }
        Class<?> implementation;
        if (!implementations.containsKey(interfaceClazz)) {
            implementation = findImplementation(interfaceClazz);
            implementations.put(interfaceClazz, implementation);
        } else {
            implementation = implementations.get(interfaceClazz);
        }
        Field[] fields = implementation.getDeclaredFields();
        Object o;
        try {
            o = implementation.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException
                 | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        for (Field field : fields) {
            if (field.isAnnotationPresent(Inject.class)) {
                Object instance = getInstance(field.getType());
                try {
                    field.setAccessible(true);
                    field.set(o, instance);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return o;
    }

    private static Class<?> findImplementation(Class<?> interfaceClazz) {
        System.out.println("Finding implementation for: " + interfaceClazz);
        if (interfaceClazz.isInterface()) {
            return COMPONENTS.stream()
                    .filter(c -> Arrays.asList(c.getInterfaces()).contains(interfaceClazz))
                    .findFirst().orElseThrow();
        }
        return interfaceClazz;
    }

    private static Set<Class<?>> findAllClasses(String classPath) {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        return loader.resources(classPath.replaceAll("\\.", "/"))
                .map(url -> {
                    try {
                        return url.toURI();
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(Path::of)
                .flatMap(path -> {
                    try {
                        return Files.walk(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(Path::toString)
                .map(path -> {
                    int lastDot = path.lastIndexOf('.');
                    if (lastDot > 0) {
                        return path.substring(0, lastDot);
                    }
                    return path;
                })
                .map(path -> path.replaceAll("[\\\\/]", "."))
                .map(classpath -> classpath.substring(classpath.lastIndexOf("mate")))
                .map(classpath -> {
                    try {
                        return loader.loadClass(classpath);
                    } catch (ClassNotFoundException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
