package org.hzau.engine.lifecycle;

import javax.management.ObjectName;

public class Registry {
    final private static Registry instance = new Registry();
    private Registry() {
    }
    public static Registry getRegistry() {
        return instance;
    }
    public void unregisterComponent(String name) {

    }
    public void registerComponent(Object object,ObjectName objectName) {

    }
}
