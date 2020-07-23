package ml.dent.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class DaemonThreadFactory implements ThreadFactory {

    public ThreadFactory threadFactory;

    public DaemonThreadFactory() {
        threadFactory = Executors.defaultThreadFactory();
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = threadFactory.newThread(r);
        thread.setDaemon(true);
        return thread;
    }
}
