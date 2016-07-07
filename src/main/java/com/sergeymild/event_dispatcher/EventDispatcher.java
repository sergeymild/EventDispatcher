package com.sergeymild.event_dispatcher;


import android.os.Handler;
import android.os.Looper;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EventDispatcher {
    private EventDispatcher() {/*No instances.*/}

    final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Object, EventObject> observers = new WeakHashMap<>();
    private final Map<Object, Set<String>> ignoreEvents = new WeakHashMap<>();
    private final ThreadLocal<ConcurrentLinkedQueue<EventStruct>> eventsToDispatch =
            new ThreadLocal<ConcurrentLinkedQueue<EventStruct>>() {
                @Override
                protected ConcurrentLinkedQueue<EventStruct> initialValue() {
                    return new ConcurrentLinkedQueue<>();
                }
            };

    private final ThreadLocal<Boolean> isDispatching = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    private static class SingletonHolder {
        private static final EventDispatcher INSTANCE = new EventDispatcher();
    }

    static EventDispatcher getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public static void enableLogger(boolean isEnabled) {
        Logger.isLoggerEnabled = isEnabled;
    }

    /**
     * Registers all handler methods on object to receive methods to provide events.
     *
     * @param object object whose handler methods should be registered.
     * @throws NullPointerException if the object is null.
     */
    public static void register(Object object, boolean findInSuperClass) {
        getInstance().registerInstance(object, findInSuperClass);
    }
    private void registerInstance(Object object, boolean findInSuperClass) {
        if (object == null)
            throw new NullPointerException("Object to register must not be null.");

        if (observers.get(object) == null) {
            observers.put(object, new EventObject(object, findInSuperClass));
        }
    }


    public static void register(Object object) {
        Logger.log("-register: [" + String.valueOf(object.getClass().getSimpleName()) + "]");
        getInstance().registerInstance(object);
    }
    private void registerInstance(Object object) {
        register(object, false);
    }

    /**
     * Unregisters all handler methods for this object.
     *
     * @param object object whose handler methods should be unregistered.
     * @throws NullPointerException if the object is null.
     */
    public static void unregister(Object object) {
        getInstance().unregisterInstance(object);
    }

    public static void unregister(Object object, String eventName) {
        getInstance().unregisterInstance(object, eventName);
    }

    private void unregisterInstance(Object object) {

        if (object == null)
            throw new NullPointerException("Object to unregister must not be null.");

        observers.remove(object);
        Set<String> ignoredEventsSet = ignoreEvents.remove(object);

        if (ignoredEventsSet != null) {
            ignoredEventsSet.clear();
            ignoredEventsSet = null;
        }
        Logger.log("- object: [" + object.getClass().getSimpleName() +"] is unregistered");
    }

    private void unregisterInstance(Object object, String eventName) {
        if (object == null)
            throw new NullPointerException("Object to unregister must not be null.");
        Logger.log("-add to ignore Events "+object.getClass().getSimpleName()+"[" + eventName +"]");

        if (eventName == null || "".equals(eventName)) {
            unregister(object);
        }

        EventObject eventObject = observers.get(object);
        if (eventObject != null) {
            Set<String> ignoredEventsSet = ignoreEvents.get(object);
            if (ignoredEventsSet == null) {
                ignoredEventsSet = new HashSet<>();
                ignoreEvents.put(object, ignoredEventsSet);
            }
            ignoredEventsSet.add(eventName);
            Logger.log("    -event: [" + eventName +"] added to ignore");
        }
    }

    /**
     * Posts an event to all registered handlers.
     *
     * @param eventName to post.
     * @throws NullPointerException if the event is null.
     */
    public static void post(String eventName, Object... argumentsForInvokingMethod) {
        Logger.log("-post event: [" + eventName + "(args: "+Arrays.toString(argumentsForInvokingMethod)+")]");
        getInstance().postInstance(eventName, argumentsForInvokingMethod);
    }

    private void postInstance(String eventName, Object... argumentsForInvokingMethod) {
        if (eventName == null)
            throw new NullPointerException("EventName to post must not be null.");

        for (Object key : observers.keySet()) {
            EventObject listener = observers.get(key);
            if (listener.getMethod(eventName) != null) {
                Object target = listener.getTarget();

                if (target != null) {
                    Logger.log("    -target: [" + target.getClass().getSimpleName()+"]");
                    Set<String> ignoredEventsSet = ignoreEvents.get(target);
                    Logger.log("        -ignoredEventsSet: " +ignoredEventsSet);
                    if (ignoredEventsSet != null && ignoredEventsSet.contains(eventName)) {
                        Logger.log("        -event: [" + eventName + "] -> is ignored");
                        return;
                    }
                }
                enqueueEvent(eventName, listener, argumentsForInvokingMethod);
            }
        }
        dispatchQueuedEvents();
    }

    private void enqueueEvent(String eventName, EventObject event, Object... argumentsForInvokingMethod) {
        Logger.log("        -enqueueEvent: [" + eventName + "]");
        eventsToDispatch.get().offer(new EventStruct(eventName, event, argumentsForInvokingMethod));
    }

    private void dispatchQueuedEvents() {
        if (isDispatching.get()) return;
        isDispatching.set(true);

        try {
            while (true) {
                EventStruct eventStruct = eventsToDispatch.get().poll();
                if (eventStruct == null) break;
                dispatch(eventStruct.eventName, eventStruct.event, eventStruct.argumentsForInvokingMethod);
            }
        } finally {
            isDispatching.set(false);
        }
    }

    private void dispatch(String eventName, EventObject event, Object... argumentsForInvokingMethod) {
        Logger.log("            -dispatch: [" + eventName + "(args: "+Arrays.toString(argumentsForInvokingMethod)+")]");
        event.invokeMethod(eventName, argumentsForInvokingMethod);
    }

    private void throwExceptionIfNull(Object... args) {
        for (Object o : args) {
            if (o == null) {
                throw new IllegalArgumentException("EventName and TargetObject must be not null");
            }
        }
    }

    static class EventStruct {
        final EventObject event;
        final String eventName;
        final Object[] argumentsForInvokingMethod;

        EventStruct(String eventName, EventObject event, Object... argumentsForInvokingMethod) {
            this.event = event;
            this.argumentsForInvokingMethod = argumentsForInvokingMethod;
            this.eventName = eventName;
        }
    }
}
