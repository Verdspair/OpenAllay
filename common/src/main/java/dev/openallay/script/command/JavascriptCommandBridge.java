package dev.openallay.script.command;

import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.host.RhinoHostAdapter;
import java.util.Objects;
import java.util.function.Function;

/** Builds the closed commands.list/describe/run object without exposing Java methods. */
public final class JavascriptCommandBridge {
    private final CommandCapabilityRuntime.RequestCapability capability;
    private final CancellationSignal cancellation;

    JavascriptCommandBridge(
            CommandCapabilityRuntime.RequestCapability capability,
            CancellationSignal cancellation) {
        this.capability = Objects.requireNonNull(capability, "capability");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
    }

    public Scriptable bind(
            Context context, ScriptableObject scope, RhinoHostAdapter adapter) {
        Scriptable commands = context.newObject(scope);
        define(context, scope, commands, "list", 0, ignored -> capability.catalog(), adapter);
        define(
                context,
                scope,
                commands,
                "describe",
                1,
                arguments -> capability.catalog()
                        .describe(string(arguments[0], "commands.describe"))
                        .orElseThrow(() -> new JavascriptExecutionException(
                                "command_path_unavailable",
                                "Command path is unavailable in this request")),
                adapter);
        define(
                context,
                scope,
                commands,
                "run",
                1,
                arguments -> capability.submit(
                        string(arguments[0], "commands.run"), cancellation),
                adapter);
        if (commands instanceof ScriptableObject object) {
            object.preventExtensions();
        }
        return commands;
    }

    private static void define(
            Context context,
            ScriptableObject scope,
            Scriptable target,
            String name,
            int arity,
            Function<Object[], Object> invocation,
            RhinoHostAdapter adapter) {
        BaseFunction function = new BaseFunction(
                scope, ScriptableObject.getFunctionPrototype(scope, context)) {
            @Override
            public String getFunctionName() {
                return name;
            }

            @Override
            public Object call(
                    Context callContext,
                    Scriptable callScope,
                    Scriptable thisObject,
                    Object[] arguments) {
                if (arguments.length != arity) {
                    throw new JavascriptExecutionException(
                            "command_invalid",
                            name + " requires " + arity + " argument(s)");
                }
                return adapter.adapt(invocation.apply(arguments));
            }

            @Override
            public Scriptable construct(
                    Context callContext, Scriptable callScope, Object[] arguments) {
                throw new JavascriptExecutionException(
                        "javascript_host_access_denied",
                        "Command functions are not constructors");
            }
        };
        ScriptableObject.defineProperty(
                target,
                name,
                function,
                ScriptableObject.READONLY | ScriptableObject.PERMANENT,
                context);
    }

    private static String string(Object value, String operation) {
        if (!(value instanceof CharSequence text)) {
            throw new JavascriptExecutionException(
                    "command_invalid", operation + " requires one command string");
        }
        return text.toString();
    }
}
