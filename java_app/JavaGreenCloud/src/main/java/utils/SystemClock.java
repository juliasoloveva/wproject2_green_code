package utils;

public class SystemClock {
    private long PCR = 0x0;

    public void setPCR(long PCR) {
        this.PCR = PCR;
    }

    public long getPCR() {
        return this.PCR;
    }
}
