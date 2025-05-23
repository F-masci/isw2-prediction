package it.isw2.prediction.model;

import java.util.ArrayList;
import java.util.List;

public class Method {

    private final String methodName;
    private final String className;
    private final String packageName;

    private List<Version> versions = new ArrayList<>();

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

    public void addVersion(Version version) {
        if (this.versions.contains(version)) return;
        this.versions.add(version);
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