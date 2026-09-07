package com.github.agentdock.core.internal;

import java.util.concurrent.ThreadFactory;

/** 内核执行器共用的守护线程工厂。 */
public final class ExecutorSupport {

    private ExecutorSupport() {
    }

    public static ThreadFactory daemonThreadFactory(String threadName) {
        return runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        };
    }
}
