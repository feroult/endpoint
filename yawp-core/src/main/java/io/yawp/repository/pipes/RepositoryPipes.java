package io.yawp.repository.pipes;

import io.yawp.repository.IdRef;
import io.yawp.repository.Repository;
import io.yawp.repository.models.ObjectHolder;
import io.yawp.repository.query.NoResultException;

public class RepositoryPipes {

    public static void flux(Repository r, Object source) {
        Class<?> endpointClazz = source.getClass();

        if (!isPipeSource(r, endpointClazz)) {
            return;
        }

        for (Class<? extends Pipe> pipeClazz : r.getEndpointFeatures(endpointClazz).getPipes()) {
            Pipe pipe = createPipeInstance(r, pipeClazz);
            pipe.configure(source);
            r.driver().pipes().flux(pipe, source);
        }
    }

    public static void reflux(Repository r, IdRef<?> id) {
        Class<?> endpointClazz = id.getClazz();

        if (!isPipeSource(r, endpointClazz)) {
            return;
        }

        // TODO: Pipes - Think about... Shield may have already loaded this source.
        Object source;
        try {
            source = id.fetch();
        } catch (NoResultException e) {
            return;
        }

        for (Class<? extends Pipe> pipeClazz : r.getEndpointFeatures(endpointClazz).getPipes()) {
            Pipe pipe = createPipeInstance(r, pipeClazz);
            pipe.configure(source);
            r.driver().pipes().reflux(pipe, source);
        }
    }

    public static void updateExisting(Repository r, Object object) {
        Class<?> endpointClazz = object.getClass();

        if (!isPipeSourceOrSink(r, endpointClazz)) {
            return;
        }

        Object oldObject;

        try {
            // TODO: Pipes - Think about... Shield may have already loaded this object.
            oldObject = fetchOldObject(object);
        } catch (NoResultException e) {
            return;
        }

        refluxOld(r, endpointClazz, object, oldObject);
        reflowSink(r, endpointClazz, object, oldObject);
    }

    private static void refluxOld(Repository r, Class<?> endpointClazz, Object source, Object oldSource) {
        if (!isPipeSource(r, endpointClazz)) {
            return;
        }

        for (Class<? extends Pipe> pipeClazz : r.getEndpointFeatures(endpointClazz).getPipes()) {
            Pipe oldPipe = createOldPipeInstance(r, pipeClazz, source, oldSource);

            if (!oldPipe.hasSinks()) {
                continue;
            }

            r.driver().pipes().reflux(oldPipe, source);
        }
    }

    private static void reflowSink(Repository r, Class<?> endpointClazz, Object sink, Object oldSink) {
        if (!isPipeSink(r, endpointClazz)) {
            return;
        }

        for (Class<? extends Pipe> pipeClazz : r.getEndpointFeatures(endpointClazz).getPipesSink()) {
            Pipe pipe = createPipeInstance(r, pipeClazz);
            if (!pipe.reflowCondition(sink, oldSink)) {
                continue;
            }

            r.driver().pipes().reflow(pipe, sink);
        }
    }


    private static Pipe createOldPipeInstance(Repository r, Class<? extends Pipe> pipeClazz, Object object, Object oldObject) {
        Pipe oldPipe = createPipeInstance(r, pipeClazz);
        oldPipe.configure(oldObject);

        Pipe newPipe = createPipeInstance(r, pipeClazz);
        newPipe.configure(object);

        oldPipe.removeSinks(newPipe.getSinks());
        return oldPipe;
    }

    private static Object fetchOldObject(Object object) {
        ObjectHolder objectHolder = new ObjectHolder(object);

        if (objectHolder.getId() == null) {
            throw new NoResultException();
        }

        return objectHolder.getId().fetch();
    }

    private static Pipe createPipeInstance(Repository r, Class<? extends Pipe> pipeClazz) {
        try {
            Pipe pipe = pipeClazz.newInstance();
            pipe.setRepository(r);

            return pipe;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isPipeSourceOrSink(Repository r, Class<?> endpointClazz) {
        return isPipeSource(r, endpointClazz) || isPipeSink(r, endpointClazz);
    }

    public static boolean isPipeSource(Repository r, Class<?> endpointClazz) {
        return r.getFeatures() != null && r.getEndpointFeatures(endpointClazz).getPipes().size() != 0;
    }

    public static boolean isPipeSink(Repository r, Class<?> endpointClazz) {
        return r.getEndpointFeatures(endpointClazz).getPipesSink().size() != 0;
    }
}
