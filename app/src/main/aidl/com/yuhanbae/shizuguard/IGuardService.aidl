package com.yuhanbae.shizuguard;

interface IGuardService {
    void destroy() = 16777114;
    String runCommand(String cmd) = 1;
    boolean setPackageEnabled(String packageName, boolean enabled) = 2;
    boolean forceStopPackage(String packageName) = 3;
    boolean clampBackground(String packageName, boolean restrict) = 4;
    int batchSetEnabled(in List<String> packages, boolean enabled) = 5;
    long getPackageRamKb(String packageName) = 6;
    String getMemorySummary() = 7;
    String getTopWakelocks() = 8;
}
