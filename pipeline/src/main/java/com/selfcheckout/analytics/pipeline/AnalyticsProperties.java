package com.selfcheckout.analytics.pipeline;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** The policy and bounded pipe sizes; defaults preserve the shared API contract. */
@Component
@Validated
@ConfigurationProperties("selfcheckout.analytics")
public class AnalyticsProperties {
    @Min(1) private int windowSize = 1000;
    @Min(1) private int slideInterval = 500;
    @Min(1) private int popularItemsLimit = 10;
    private int lowStockThreshold = 50;
    @Min(1) private int inputCapacity = 2048;
    @Min(1) private int windowCapacity = 16;
    @Min(1) private int snapshotCapacity = 16;
    @Min(1) private long admissionTimeoutMs = 5000;
    @Min(1) private long queryTimeoutMs = 5000;
    @Min(1) private long shutdownTimeoutMs = 30000;

    public int getWindowSize() { return windowSize; }
    public void setWindowSize(int value) { windowSize = value; }
    public int getSlideInterval() { return slideInterval; }
    public void setSlideInterval(int value) { slideInterval = value; }
    public int getPopularItemsLimit() { return popularItemsLimit; }
    public void setPopularItemsLimit(int value) { popularItemsLimit = value; }
    public int getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(int value) { lowStockThreshold = value; }
    public int getInputCapacity() { return inputCapacity; }
    public void setInputCapacity(int value) { inputCapacity = value; }
    public int getWindowCapacity() { return windowCapacity; }
    public void setWindowCapacity(int value) { windowCapacity = value; }
    public int getSnapshotCapacity() { return snapshotCapacity; }
    public void setSnapshotCapacity(int value) { snapshotCapacity = value; }
    public long getAdmissionTimeoutMs() { return admissionTimeoutMs; }
    public void setAdmissionTimeoutMs(long value) { admissionTimeoutMs = value; }
    public long getQueryTimeoutMs() { return queryTimeoutMs; }
    public void setQueryTimeoutMs(long value) { queryTimeoutMs = value; }
    public long getShutdownTimeoutMs() { return shutdownTimeoutMs; }
    public void setShutdownTimeoutMs(long value) { shutdownTimeoutMs = value; }
}
