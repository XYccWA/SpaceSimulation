package org.xyccwa.space_simulation.util;

public class AccelerationData {
    private double lastAcceleration;
    private int duration;
    private long startTime;

    public AccelerationData(double initialAcceleration) {
        this.lastAcceleration = initialAcceleration;
        this.duration = 0;
        this.startTime = System.currentTimeMillis();
    }

    public double getLastAcceleration() {
        return lastAcceleration;
    }

    public void setLastAcceleration(double acceleration) {
        this.lastAcceleration = acceleration;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void incrementDuration() {
        this.duration++;
    }

    public void resetTimer() {
        this.duration = 0;
        this.startTime = System.currentTimeMillis();
    }

    public long getStartTime() {
        return startTime;
    }
}