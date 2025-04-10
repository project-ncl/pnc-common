/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.common.concurrent.mdc;

import io.opentelemetry.context.Context;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class MDCScheduledThreadPoolExecutor extends MDCThreadPoolExecutor implements ScheduledExecutorService {

    ScheduledExecutorService scheduledExecutor;

    public MDCScheduledThreadPoolExecutor(ScheduledExecutorService scheduledExecutor) {
        this.scheduledExecutor = scheduledExecutor;
        super.executorService = Context.taskWrapping(scheduledExecutor);
    }

    public MDCScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
        scheduledExecutor = new ScheduledThreadPoolExecutor(corePoolSize, threadFactory);
        super.executorService = Context.taskWrapping(scheduledExecutor);
    }

    public MDCScheduledThreadPoolExecutor(int corePoolSize) {
        scheduledExecutor = new ScheduledThreadPoolExecutor(corePoolSize);
        super.executorService = Context.taskWrapping(scheduledExecutor);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return scheduledExecutor.schedule(MDCWrappers.wrap(command), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return scheduledExecutor.schedule(MDCWrappers.wrap(callable), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return scheduledExecutor.scheduleAtFixedRate(MDCWrappers.wrap(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return scheduledExecutor.scheduleWithFixedDelay(MDCWrappers.wrap(command), initialDelay, delay, unit);
    }
}
