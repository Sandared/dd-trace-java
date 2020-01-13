package datadog.trace.instrumentation.finagle;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.finagle.FinagleServiceDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import com.twitter.finagle.http.Response;
import com.twitter.util.Future;
import com.twitter.util.FutureEventListener;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import java.lang.reflect.Method;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class FinagleServiceInstrumentation extends Instrumenter.Default {
  public FinagleServiceInstrumentation() {
    super("finagle");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ServerDecorator",
      FinagleServiceInstrumentation.class.getName() + "$Listener"
    };
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return safeHasSuperType(named("com.twitter.finagle.Service"));
    // .and(not(nameStartsWith("com.twitter.finagle.http"))); // Ignore built in services
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("apply"))
            .and(takesArgument(0, named("com.twitter.finagle.http.Request"))),
        FinagleServiceInstrumentation.class.getName() + "$ServiceWrappingAdvice");
  }

  public static class ServiceWrappingAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope startSpanOnApply(@Advice.Origin final Method method) {

      final AgentSpan span = startSpan("finagle.service");
      DECORATE.afterStart(span);

      span.setTag(DDTags.RESOURCE_NAME, DECORATE.spanNameForMethod(method));

      final AgentScope scope = activateSpan(span, true);
      scope.setAsyncPropagation(true);

      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addSpanFinisherOnExit(
        @Advice.Enter final AgentScope scope,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return(readOnly = false) final Future<Response> result) {
      if (throwable != null) {
        DECORATE.onError(scope, throwable);
        DECORATE.beforeFinish(scope);
        scope.close();
      } else {
        TwitterPromiseUtils.setupScopePropagation(result, new Listener(scope));
      }
    }
  }

  public static class Listener implements FutureEventListener<Response> {
    private final AgentScope scope;

    public Listener(final AgentScope scope) {
      this.scope = scope;
    }

    @Override
    public void onSuccess(final Response value) {
      DECORATE.beforeFinish(scope);
      scope.span().finish();
    }

    @Override
    public void onFailure(final Throwable cause) {
      DECORATE.onError(scope, cause);
      DECORATE.beforeFinish(scope);
      scope.span().finish();
    }
  }
}
