package org.kovacstelekes.lambdatool;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BbLambdaTool<T> implements LambdaTool<T> {
    private final T buddy;
    private final CapturingInvocationHandler invocationHandler;

    private BbLambdaTool(Class<T> type) {
        try {
            invocationHandler = new CapturingInvocationHandler();
            buddy = new ByteBuddy()
                    .subclass(type)
                    .method(ElementMatchers.any())
                    .intercept(InvocationHandlerAdapter.of(invocationHandler))
                    .make()
                    .load(type.getClassLoader())
                    .getLoaded()
                    .getConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> BbLambdaTool<T> forType(Class<T> type) {
        return new BbLambdaTool<>(type);
    }

    @Override
    public Method whichMethod(Consumer<T> parameterlessMethodInvocation) {
        return captureInvokedMethod(parameterlessMethodInvocation);
    }

    @Override
    public Method whichMethod(BiConsumer<T, ?> methodInvocationWithSingleParameter) {
        return captureInvokedMethod(enhancer ->
                methodInvocationWithSingleParameter.accept(enhancer, null)
        );
    }

    @Override
    public Method whichMethod(MethodCallWithTwoParameters<T> methodInvocationWithTwoParameters) {
        return captureInvokedMethod(enhancer ->
                methodInvocationWithTwoParameters.accept(enhancer, null, null)
        );
    }

    private Method captureInvokedMethod(Consumer<T> invocation) {
        invocationHandler.reset();
        invocation.accept(buddy);
        return invocationHandler.invokedMethod();
    }

    private static class CapturingInvocationHandler implements InvocationHandler {
        private Method invokedMethod;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            // side effect: method is captured
            this.invokedMethod = method;
            // return value is ignored by caller
            return null;
        }

        private void reset() {
            invokedMethod = null;
        }

        private Method invokedMethod() {
            return invokedMethod;
        }
    }
}
