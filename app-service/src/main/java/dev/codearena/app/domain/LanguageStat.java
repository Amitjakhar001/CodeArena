package dev.codearena.app.domain;

public class LanguageStat {
    private long count;
    private long accepted;
    private long avgTimeMs;

    public LanguageStat() {}

    public LanguageStat(long count, long accepted, long avgTimeMs) {
        this.count = count;
        this.accepted = accepted;
        this.avgTimeMs = avgTimeMs;
    }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }

    public long getAccepted() { return accepted; }
    public void setAccepted(long accepted) { this.accepted = accepted; }

    public long getAvgTimeMs() { return avgTimeMs; }
    public void setAvgTimeMs(long avgTimeMs) { this.avgTimeMs = avgTimeMs; }
}
