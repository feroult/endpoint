package io.yawp.driver.appengine.pipes.tools.reload;

import com.google.appengine.tools.pipeline.Job1;
import com.google.appengine.tools.pipeline.JobSetting;
import com.google.appengine.tools.pipeline.Value;
import io.yawp.repository.IdRef;
import io.yawp.repository.pipes.Pipe;

import java.util.List;

import static io.yawp.repository.Yawp.yawp;

public class ReloadPipeJob extends Job1<Void, Class<? extends Pipe>> {

    private transient Class<? extends Pipe> pipeClazz;

    private transient Class<?> sinkClazz;

    @Override
    public Value<Void> run(Class<? extends Pipe> pipeClazz) throws Exception {
        init(pipeClazz);
        return execute();
    }

    private void init(Class<? extends Pipe> pipeClazz) {
        this.pipeClazz = pipeClazz;
    }

    private Value<Void> execute() {
        JobSetting.WaitForSetting waitClearSinks = waitFor(futureCall(new ClearSinksJob(), immediate(pipeClazz)));
        waitFor(futureCall(new FlushSourcesJob(), immediate(pipeClazz), waitClearSinks));
        return null;
    }

    private List<? extends IdRef<?>> sinkIds() {
        return yawp(sinkClazz).ids();
    }
}
