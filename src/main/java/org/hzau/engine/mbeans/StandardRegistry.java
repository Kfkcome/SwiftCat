package org.hzau.engine.mbeans;

import javax.management.*;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.ModelMBean;

import org.apache.commons.modeler.ManagedBean;
import org.apache.commons.modeler.Registry;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;

/**
 * 类功能描述：将 MBean 注册到 MBean 服务器中
 *
 * @author kfk
 * @date 2023/12/28
 * TODO:实现JMX注册
 */
public class StandardRegistry {
    private Registry registry = null;
    private MBeanServer platformMBeanServer = null;
    final private static StandardRegistry instance = new StandardRegistry();
    private StandardRegistry() {
        registry = Registry.getRegistry(null, null);
        InputStream stream = null;
        try {
            //TODO:改变配置文件位置
            stream = new FileInputStream("src/main/java/org/hzau/engine/mbeans/mbeans-descriptors.xml");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        try {
            registry.loadMetadata(stream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        platformMBeanServer = ManagementFactory.getPlatformMBeanServer();

    }
    public static StandardRegistry getRegistry() {
        return instance;
    }

    /**
     * 功能描述：从JMXserver中卸载MBean
     *
     * @param name
     * @author kfk
     * @date 2023/12/28
     */
    public void unregisterComponent(String name) {
        try {
            platformMBeanServer.unregisterMBean(new ObjectName(name));
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        } catch (MBeanRegistrationException e) {
            throw new RuntimeException(e);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
        //TODO:处理异常
    }

    /**
     * 功能描述：往JMXServer中注册MBean
     *
     * @param object
     * @param objectName
     * @author kfk
     * @date 2023/12/28
     */
    public void registerComponent(Object object,ObjectName objectName) {
        ManagedBean managed =null;
        try {
            managed=registry.findManagedBean(object.getClass(),objectName.getKeyProperty("type"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ModelMBean objectMBean = null;
        try {
            objectMBean = managed.createMBean(object);
            platformMBeanServer.registerMBean(objectMBean, objectName);
        } catch (InstanceNotFoundException | InvalidTargetObjectTypeException | MBeanException |
                 NotCompliantMBeanException | InstanceAlreadyExistsException e) {
            //todo:处理异常
            throw new RuntimeException(e);
        }
    }
}
