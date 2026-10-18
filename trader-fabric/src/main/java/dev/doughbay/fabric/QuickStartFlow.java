package dev.doughbay.fabric;

import dev.doughbay.core.execution.ExecutionResult;
import java.util.function.Supplier;

/** A single explicit opt-in after review; refuses recovery/active-session holds before saving caps. */
final class QuickStartFlow {
    interface Save {String apply(long trade,long session);}
    static String validate(long trade,long session){
        return trade<=0||session<=0||trade>session?"Enter positive limits; per trade must not exceed the session limit.":null;
    }
    static ExecutionResult start(long trade,long session,Supplier<String> before,Save save,Supplier<ExecutionResult> start){
        String invalid=validate(trade,session);if(invalid!=null)return ExecutionResult.refused(invalid);
        String blocked=before.get();if(blocked!=null&&!blocked.isBlank())return ExecutionResult.refused(blocked);
        String failure=save.apply(trade,session);if(failure!=null&&!failure.isBlank())return ExecutionResult.refused(failure);
        var result=start.get();
        return result.ok()?result:ExecutionResult.refused("Limits saved; trading has not started. "+result.detail());
    }
}
