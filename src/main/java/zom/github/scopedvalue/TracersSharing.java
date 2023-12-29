package zom.github.scopedvalue;

import java.util.concurrent.locks.LockSupport;

import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;


public class TracersSharing {
	
    public static final ScopedValue<Tracers> TRACERS_SCOPED_VALUE = ScopedValue.newInstance();

    private static record Tracer(String traceId, String spanId, String parentId) {
    	
    	@Override
    	public String toString() {
			return traceId + ": " + spanId + "(" + parentId + ")";
    	}
    }
    
    private static class Tracers {

    	private final Tracer[] tracers;

    	public Tracers(Tracer[] tracers) {
    		this.tracers = tracers;
    	}

    }
    
	private static class PropagatingTaskDecorator implements TaskDecorator {

		private final Tracers tracers;

	    public PropagatingTaskDecorator(Tracers tracers) {
	        this.tracers = tracers;
	    }
	 
	    @Override
	    public Runnable decorate(Runnable runnable) {
	        final ScopedValue.Carrier carrier = ScopedValue.where(TRACERS_SCOPED_VALUE, tracers);
	        return () -> {
	            carrier.run(runnable);
	        };
	    }
	}
	
	private volatile boolean completed;
	
    public void main() {
    	final Thread mainThread = Thread.currentThread();
        try (SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("pg-")) {
            executor.setVirtualThreads(true);
            executor.setTaskDecorator(new PropagatingTaskDecorator(new Tracers(new Tracer[] {
            		new Tracer("ID", "SpanID", "ParentID")
            })));
            executor.submit(() -> {
            	Tracer[] tracers = TRACERS_SCOPED_VALUE.get().tracers;
            	for(Tracer tracer : tracers) {
            		System.out.println(tracer);
            	}
            	completed = true;
            	LockSupport.unpark(mainThread);
             });
        }
        while (!completed) {
        	LockSupport.park(); // not required for non-virtual, non-daemon Executor's threads
        }
    }
    
    public static void main(String[] args) {
    	new TracersSharing().main();
    }
}