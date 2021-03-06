package jd.controlling.downloadcontroller;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import jd.plugins.DownloadLink;

import org.appwork.utils.net.throttledconnection.ThrottledConnection;
import org.appwork.utils.net.throttledconnection.ThrottledConnectionHandler;
import org.appwork.utils.speedmeter.SpeedMeterInterface;
import org.appwork.utils.speedmeter.SpeedMeterInterface.Resolution;

public class ManagedThrottledConnectionHandler implements ThrottledConnectionHandler {
    protected CopyOnWriteArrayList<ThrottledConnection> connections = new CopyOnWriteArrayList<ThrottledConnection>();
    protected AtomicInteger                             limit       = new AtomicInteger(0);
    protected AtomicLong                                traffic     = new AtomicLong(0l);
    protected DownloadSpeedManager                      managedBy   = null;

    public ManagedThrottledConnectionHandler() {
    }

    @Deprecated
    public ManagedThrottledConnectionHandler(DownloadLink link) {
    }

    public void addThrottledConnection(ThrottledConnection con) {
        if (connections.addIfAbsent(con)) {
            final DownloadSpeedManager lmanagedBy = managedBy;
            if (lmanagedBy != null && lmanagedBy.getLimit() > 0 || getLimit() > 0) {
                con.setLimit(10);
            }
            con.setHandler(this);
        }
    }

    public List<ThrottledConnection> getConnections() {
        return connections;
    }

    public int getLimit() {
        return limit.get();
    }

    public int getSpeed() {
        int ret = 0;
        for (ThrottledConnection con : connections) {
            ret += ((SpeedMeterInterface) con).getValue(Resolution.SECONDS);
        }
        return ret;
    }

    public long getTraffic() {
        long ret = traffic.get();
        for (ThrottledConnection con : connections) {
            ret += con.transfered();
        }
        return ret;
    }

    public void removeThrottledConnection(ThrottledConnection con) {
        if (connections.remove(con)) {
            traffic.addAndGet(con.transfered());
            con.setHandler(null);
        }
    }

    public void setLimit(int newLimit) {
        this.limit.set(Math.max(0, newLimit));
    }

    public int size() {
        return connections.size();
    }

    protected void setManagedBy(DownloadSpeedManager downloadSpeedManager) {
        this.managedBy = downloadSpeedManager;
    }
}
