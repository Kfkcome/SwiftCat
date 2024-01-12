package org.hzau.classloader;

import java.nio.file.Path;
import java.util.Objects;

public class Resource {
    public final Path path;
    public final String name;

    public Resource(Path path, String name) {
        this.path = path;
        this.name = name;
    }

    public Path getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Resource resource = (Resource) o;
        return Objects.equals(path, resource.path) && Objects.equals(name, resource.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, name);
    }

    @Override
    public String toString() {
        return "Resource{" + "path=" + path + ", name='" + name + '\'' + '}';
    }
}
