package org.hzau.threadpool;

import org.hzau.engine.lifecycle.Lifecycle;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public interface MyExecutor extends Executor , Lifecycle {
    public String getName();

    void execute(Runnable command, long timeout, TimeUnit unit);

}
