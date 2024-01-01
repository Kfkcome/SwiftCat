package org.hzau;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.annotation.WebServlet;
import org.apache.commons.cli.*;
import org.hzau.classloader.Resource;
import org.hzau.classloader.WebAppClassLoader;
import org.hzau.connector.HttpConnector;
import org.hzau.engine.lifecycle.LifecycleException;
import org.hzau.threadpool.StandardThreadExecutor;
import org.hzau.utils.ClassPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.jar.JarFile;

public class Bootstrap {

    Logger logger = LoggerFactory.getLogger(getClass());

    public static void main(String[] args) throws Exception {
        String warFile = null;
        String customConfigPath = null;


        //TODO:为什么只能加载war包，而不是目录文件
        //TODO:添加host层使得可以配置多个webapps目录 比如webapps1 webapps2
        File file=new File("./webapps");
        if (!file.exists()) {
            file.mkdir();
        }
        for (File listFile : file.listFiles()) {

            //TODO:实现可以配置context的docBase也就是servlet所在的目录 也就是webapps/the value of docBase
            //TODO:并且如果不在yml中配置的话 也能使用默认配置 将webapps下所有servlet都注册
            String[] split = listFile.getName().split("\\.");
            if(listFile.isFile()&&split[split.length-1].equals("war")){
                warFile=listFile.getAbsolutePath();
            }
            else{
                //TODO:处理目录文件
            }
        }
        new Bootstrap().start(warFile, customConfigPath);
    }

    public void start(String warFile, String customConfigPath) throws IOException, LifecycleException {
        //TODO:为什么每次只能加载一个war包
        Path warPath = parseWarFile(warFile);

        // extract war if necessary:
        Path[] ps = extractWarIfNecessary(warPath);

        String webRoot = ps[0].getParent().getParent().toString();
        logger.info("set web root: {}", webRoot);

        // load configs:
        String defaultConfigYaml = ClassPathUtils.readString("/server.yml");
        String customConfigYaml = null;
        if (customConfigPath != null) {
            logger.info("load external config {}...", customConfigPath);
            try {
                customConfigYaml = Files.readString(Paths.get(customConfigPath), StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.error("Could not read config: " + customConfigPath, e);
                System.exit(1);
                return;
            }
        }
        Config config;
        Config customConfig;
        try {
            config = loadConfig(defaultConfigYaml);
        } catch (JacksonException e) {
            logger.error("Parse default config failed.", e);
            throw new RuntimeException(e);
        }
        if (customConfigYaml != null) {
            try {
                customConfig = loadConfig(customConfigYaml);
            } catch (JacksonException e) {
                logger.error("Parse custom config failed: " + customConfigPath, e);
                throw new RuntimeException(e);
            }
            // copy custom-config to default-config:
            try {
                merge(config, customConfig);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        // set classloader:
        var classLoader = new WebAppClassLoader(ps[0], ps[1]);

        // scan class:
        //TODO: 修复一次加载出来所有类的问题 应该动态的按需加载
        Set<Class<?>> classSet = new HashSet<>();
        //-----------------------
        Consumer<Resource> handler = (r) -> {//定义一个Consumer<Resource>类型的handler 相当于一个函数
            if (r.getName().endsWith(".class")) {//如果是class文件
                //获取类名
                String className = r.getName().substring(0, r.getName().length() - 6).replace('/', '.');
                if (className.endsWith("module-info") || className.endsWith("package-info")) {
                    return;
                }
                Class<?> clazz;
                try {
                    clazz = classLoader.loadClass(className);
                } catch (ClassNotFoundException e) {
                    logger.warn("load class '{}' failed: {}: {}", className, e.getClass().getSimpleName(), e.getMessage());
                    return;
                } catch (NoClassDefFoundError err) {
                    logger.error("load class '{}' failed: {}: {}", className, err.getClass().getSimpleName(), err.getMessage());
                    return;
                }
                if (clazz.isAnnotationPresent(WebServlet.class)) {//如果标记了WebServlet注解
                    logger.info("Found @WebServlet: {}", clazz.getName());
                    classSet.add(clazz);
                }
                if (clazz.isAnnotationPresent(WebFilter.class)) {//如果标记了WebFilter注解
                    logger.info("Found @WebFilter: {}", clazz.getName());
                    classSet.add(clazz);
                }
                if (clazz.isAnnotationPresent(WebListener.class)) {//如果标记了WebListener注解
                    logger.info("Found @WebListener: {}", clazz.getName());
                    classSet.add(clazz);
                }
            }
        };


        //-----------------------

        classLoader.scanClassPath(handler);
        classLoader.scanJar(handler);
        List<Class<?>> autoScannedClasses = new ArrayList<>(classSet);

        // executor:
        if (config.server.enableVirtualThread) {
            logger.info("Virtual thread is enabled.");
        }
//        ExecutorService executor = config.server.enableVirtualThread ? Executors.newVirtualThreadPerTaskExecutor()
//                : new ThreadPoolExecutor(0, config.server.threadPoolSize, 0L, TimeUnit.MILLISECONDS,
//                        new LinkedBlockingQueue<>());

        //----------------------------

        //定义线程池大小 TODO:包装线程池使其遵循生命周期管理 用JMX监控线程池
//        int threadPoolSize = config.server.threadPoolSize; // 假设这是您配置的线程池大小
//        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        StandardThreadExecutor standardExecutor = new StandardThreadExecutor();
        // 启动线程池等
        standardExecutor.start();

        //----------------------------启动从connector启动开始 TODO: connector的生命周期管理
        //TODO: 应该配置几个Connector就主注册几个 Connector 而不是只注册一个
        try (HttpConnector connector = new HttpConnector(config, webRoot, standardExecutor, classLoader, autoScannedClasses)) {
            for (; ; ) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        logger.info("SwiftCat http server was shutdown.");
    }

    // return classes and lib path:
    Path[] extractWarIfNecessary(Path warPath) throws IOException {
        if (Files.isDirectory(warPath)) {
            logger.info("war is directy: {}", warPath);
            Path classesPath = warPath.resolve("WEB-INF/classes");
            Path libPath = warPath.resolve("WEB-INF/lib");
            Files.createDirectories(classesPath);
            Files.createDirectories(libPath);
            return new Path[]{classesPath, libPath};
        }
        Path extractPath = createExtractTo(warPath.getFileName().toString());
        logger.info("extract '{}' to '{}'", warPath, extractPath);
        JarFile war = new JarFile(warPath.toFile());
        war.stream().sorted((e1, e2) -> e1.getName().compareTo(e2.getName())).forEach(entry -> {
            if (!entry.isDirectory()) {
                Path file = extractPath.resolve(entry.getName());
                Path dir = file.getParent();
                if (!Files.isDirectory(dir) && !Files.exists(dir)) {
                    try {
                        Files.createDirectories(dir);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                try (InputStream in = war.getInputStream(entry)) {
                    Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        // check WEB-INF/classes and WEB-INF/lib:
        Path classesPath = extractPath.resolve("WEB-INF/classes");
        Path libPath = extractPath.resolve("WEB-INF/lib");
        Files.createDirectories(classesPath);
        Files.createDirectories(libPath);
        return new Path[]{classesPath, libPath};
    }

    Path parseWarFile(String warFile) {
        Path warPath = Path.of(warFile).toAbsolutePath().normalize();
        if (!Files.isRegularFile(warPath) && !Files.isDirectory(warPath)) {
            System.err.printf("war file '%s' was not found.\n", warFile);
            System.exit(1);
        }
        return warPath;
    }

    Path createExtractTo(String warPackageName) throws IOException {
        Path tmp = Paths.get("./webapps/"+warPackageName.split("\\.")[0]);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                deleteDir(tmp);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
        return tmp;
    }

    void deleteDir(Path p) throws IOException {
        Files.list(p).forEach(c -> {//FIXME:为什么项目的lib目录下的文件删除不掉
            /// error: .\webapps\hello-webapp-1\WEB-INF\lib\logback-classic-1.4.6.jar: 另一个程序正在使用此文件，进程无法访问
            try {
                if (Files.isDirectory(c)) {
                    deleteDir(c);
                } else {
                    Files.delete(c);
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
                throw new UncheckedIOException(e);
            }
        });
        Files.delete(p);
    }

    Config loadConfig(String config) throws JacksonException {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()).setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper.readValue(config, Config.class);
    }

    static void merge(Object source, Object override) throws ReflectiveOperationException {
        for (Field field : source.getClass().getFields()) {
            Object overrideFieldValue = field.get(override);
            if (overrideFieldValue != null) {
                Class<?> type = field.getType();
                if (type == String.class || type.isPrimitive() || Number.class.isAssignableFrom(type)) {
                    // source.xyz = override.xyz:
                    field.set(source, overrideFieldValue);
                } else if (Map.class.isAssignableFrom(type)) {
                    // source.map.putAll(override.map):
                    @SuppressWarnings("unchecked")
                    Map<String, String> sourceMap = (Map<String, String>) field.get(source);
                    @SuppressWarnings("unchecked")
                    Map<String, String> overrideMap = (Map<String, String>) overrideFieldValue;
                    sourceMap.putAll(overrideMap);
                } else {
                    // merge(source.xyz, override.xyz):
                    merge(field.get(source), overrideFieldValue);
                }
            }
        }
    }
}
