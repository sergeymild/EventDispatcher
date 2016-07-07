package com.sergeymild.event_dispatcher;


import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

class EventObject {
    private static final String ANDROID_PACKAGE_PREFIX = "android.";
    private static final String JAVA_PACKAGE_PREFIX = "java.";
    private WeakReference<Object> target;
    private Map<String, MethodInfo> methods;
    private List<String> onceInvokedMethods;

    EventObject(Object target, boolean findInSuperClass) {
        Logger.log("    -newEventObject for target: [" + target.getClass().getSimpleName() + "]");
        this.target = new WeakReference<>(target);
        if (methods == null) {
            methods = new HashMap<>();

            Object targetObject = this.target.get();
            if (targetObject != null) {
                getListenerMethods(targetObject.getClass(), findInSuperClass);
            }
        }
    }

    private void getListenerMethods(Class<?> klass, boolean findInSuperClass) {
        Logger.log("        -getListenerMethods");
        if (klass == null) return;
        Method[] declaredMethods = klass.getDeclaredMethods();
        for (Method method : declaredMethods) {
            Logger.log("            -method: [" + method.getName() + "()]");
            if (method.isBridge()) continue;
            parseAnnotations(method);
        }

        if (findInSuperClass) {
            Class<?> superclass = klass.getSuperclass();
            String packageName = superclass.getPackage().getName();
            if (!packageName.startsWith(ANDROID_PACKAGE_PREFIX) && !packageName.startsWith(JAVA_PACKAGE_PREFIX)) {
                getListenerMethods(superclass, findInSuperClass);
            }
        }
    }

    void invokeMethod(final String eventName, final Object... args) {
        final MethodInfo methodInfo = methods.get(name(eventName));
        final Object o = target.get();
        if (methodInfo != null && o != null) {
            methodInfo.method.setAccessible(true);
            if (methodInfo.onMainThread) {
                EventDispatcher.getInstance().handler.post(new Runnable() {
                    @Override public void run() {EventObject.this.invokeMethod(methodInfo, o, args);}
                });
            } else {
                invokeMethod(methodInfo, o, args);
            }
            if (onceInvokedMethods != null && onceInvokedMethods.contains(methodInfo.method.getName())) {
                methods.remove(name(eventName));
                onceInvokedMethods.remove(methodInfo.method.getName());
            }
        }
    }

    private void invokeMethod(MethodInfo methodInfo, Object o, Object[] args) {
        try {
            methodInfo.method.invoke(o, args);
            Logger.log("                -invokeMethod: "+o.getClass().getSimpleName()+"[" + methodInfo.method.getName() + "("+Arrays.toString(args)+")]");
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private void parseAnnotations(Method method) {
        Logger.log("                -parseAnnotations");
        if (method.isAnnotationPresent(Subscribe.class)) {
            Logger.log("                    -found @Subscribe");
            Subscribe subscribe = method.getAnnotation(Subscribe.class);
            methods.put(name(subscribe.value()), new MethodInfo(method, false));

            if (subscribe.once()) {
                if (onceInvokedMethods == null) onceInvokedMethods = new ArrayList<>();
                onceInvokedMethods.add(method.getName());
            }
        }

        if (method.isAnnotationPresent(SubscribeOnMainThread.class)) {
            Logger.log("                    -found @SubscribeOnMainThread");
            SubscribeOnMainThread subscribe = method.getAnnotation(SubscribeOnMainThread.class);
            methods.put(name(subscribe.value()), new MethodInfo(method, true));

            if (subscribe.once()) {
                Logger.log("                    -subscribe once");
                if (onceInvokedMethods == null) onceInvokedMethods = new ArrayList<>();
                onceInvokedMethods.add(method.getName());
            }
        }
    }

    Object getTarget() {
        return target.get();
    }

    private String name(String methodName) {
        return methodName.trim().toLowerCase();
    }

    MethodInfo getMethod(String eventName) {
        return methods.get(name(eventName));
    }


    private static class MethodInfo {
        private final Method method;
        private final boolean onMainThread;

        private MethodInfo(Method method, boolean onMainThread) {
            this.method = method;
            this.onMainThread = onMainThread;
        }
    }


    @Override
    public String toString() {
        return "["+target.getClass().getSimpleName()+"]";
    }
}
