/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hzau.engine.lifecycle;


/**
 * The list of valid states for components that implement {@link Lifecycle}.
 * See {@link Lifecycle} for the state transition diagram.
 */
public enum LifecycleState {

    //
    NEW(false, null),

    //INITIALIZING阶段是在开始初始化之前进行的一些准备工作，比如资源加载、配置检查等。INITIALIZING 可能表示这个准备阶段的状态。
    INITIALIZING(false, Lifecycle.BEFORE_INIT_EVENT),

    //INITIALIZED阶段是在初始化完成之后的阶段，比如初始化一个线程之后，线程进入了初始化状态，这个时候就可以认为线程已经初始化完成了。INITIALIZED 可能表示这个初始化完成阶段的状态。
    INITIALIZED(false, Lifecycle.AFTER_INIT_EVENT),

    //STARTING_PREP阶段是在开始启动之前进行的一些准备工作，比如初始化、资源加载、配置检查等。STARTING_PREP 可能表示这个准备阶段的状态。
    STARTING_PREP(false, Lifecycle.BEFORE_START_EVENT),

    //STARTING阶段是真正开始启动的阶段，比如启动一个线程、启动一个服务等。STARTING 可能表示这个启动阶段的状态。
    STARTING(true, Lifecycle.START_EVENT),

    //STARTED阶段是在启动完成之后的阶段，比如启动一个线程之后，线程进入了运行状态，这个时候就可以认为线程已经启动完成了。STARTED 可能表示这个启动完成阶段的状态。
    STARTED(true, Lifecycle.AFTER_START_EVENT),

    //STOPPING_PREP阶段是在开始停止之前进行的一些准备工作，比如资源释放、配置检查等。STOPPING_PREP 可能表示这个准备阶段的状态。
    STOPPING_PREP(true, Lifecycle.BEFORE_STOP_EVENT),

    //STOPPING阶段是真正开始停止的阶段，比如停止一个线程、停止一个服务等。STOPPING 可能表示这个停止阶段的状态。
    STOPPING(false, Lifecycle.STOP_EVENT),

    //STOPPED阶段是在停止完成之后的阶段，比如停止一个线程之后，线程进入了停止状态，这个时候就可以认为线程已经停止完成了。STOPPED 可能表示这个停止完成阶段的状态。
    STOPPED(false, Lifecycle.AFTER_STOP_EVENT),

    //DESTROYING阶段是在开始销毁之前进行的一些准备工作，比如资源释放、配置检查等。DESTROYING 可能表示这个准备阶段的状态。
    DESTROYING(false, Lifecycle.BEFORE_DESTROY_EVENT),

    //DESTROYED阶段是在销毁完成之后的阶段，比如销毁一个线程之后，线程进入了销毁状态，这个时候就可以认为线程已经销毁完成了。DESTROYED 可能表示这个销毁完成阶段的状态。
    DESTROYED(false, Lifecycle.AFTER_DESTROY_EVENT),

    //FAILED阶段是在启动或者停止过程中出现异常的阶段，比如启动一个线程的时候，线程启动失败了，这个时候就可以认为线程启动失败了。FAILED 可能表示这个启动失败阶段的状态。
    FAILED(false, null);

    private final boolean available;
    private final String lifecycleEvent;

    LifecycleState(boolean available, String lifecycleEvent) {
        this.available = available;
        this.lifecycleEvent = lifecycleEvent;
    }

    /**
     * May the public methods other than property getters/setters and lifecycle
     * methods be called for a component in this state? It returns
     * <code>true</code> for any component in any of the following states:
     * <ul>
     * <li>{@link #STARTING}</li>
     * <li>{@link #STARTED}</li>
     * <li>{@link #STOPPING_PREP}</li>
     * </ul>
     *
     * @return <code>true</code> if the component is available for use,
     *         otherwise <code>false</code>
     */
    public boolean isAvailable() {
        return available;
    }

    public String getLifecycleEvent() {
        return lifecycleEvent;
    }
}
