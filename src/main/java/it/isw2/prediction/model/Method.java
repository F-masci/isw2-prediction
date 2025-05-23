package it.isw2.prediction.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Method {

    private final String methodName;
    private final String className;
    private final String packageName;

    private List<Version> versions = new ArrayList<>();
    private Map<Version, Integer> locPerVersion = new HashMap<>();

    public Method(String className, String packageName, String methodName) {
        this.className = className;
        this.packageName = packageName;
        this.methodName = methodName;
    }

    public String getFullName() {
        return packageName + "." + className + "." + methodName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    /* --- VERSIONS --- */

    public boolean isInVersion(Version version) {
        return this.versions.contains(version);
    }

    public List<Version> getVersions() {
        return versions;
    }

    public void addVersion(Version version, int loc) {
        if (this.versions.contains(version)) return;
        this.versions.add(version);
        this.locPerVersion.put(version, loc);
    }

    /* --- FEATURES --- */

    public int getLOC(Version version) {
        return locPerVersion.get(version);
    }

    /* --- FORMATTER --- */

    @Override
    public String toString() {
        return "Method: " + getFullName();
    }

    /* --- EQUALS & HASHCODE --- */

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Method method = (Method) obj;
        return getFullName().equals(method.getFullName());
    }

    @Override
    public int hashCode() {
        return getFullName().hashCode();
    }

}