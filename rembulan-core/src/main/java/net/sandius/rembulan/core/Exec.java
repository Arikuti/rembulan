package net.sandius.rembulan.core;

import net.sandius.rembulan.util.Check;
import net.sandius.rembulan.util.Cons;
import net.sandius.rembulan.util.Ptr;

import java.util.Iterator;

public class Exec {

	private final PreemptionContext preemptionContext;
	private final LuaState state;

	private Cons<CallInfo> callStack;

	public Exec(PreemptionContext preemptionContext, LuaState state) {
		this.preemptionContext = Check.notNull(preemptionContext);
		this.state = Check.notNull(state);

		callStack = null;
	}

	public PreemptionContext getPreemptionContext() {
		return preemptionContext;
	}

	public LuaState getState() {
		return state;
	}

	public boolean isPaused() {
		return callStack != null;
	}

	public Cons<CallInfo> getCallStack() {
		return callStack;
	}

	public void pushCall(CallInfo ci) {
		Check.notNull(ci);

		if (callStack != null) {
			throw new IllegalStateException("Pushing a call in paused state");
		}
		else {
			callStack = new Cons<>(ci);
		}
	}

	// return true if execution was paused, false if execution is finished
	// in other words: returns true iff isPaused() == true afterwards
	public boolean resume() {
		Ptr<Object> tail = new Ptr<>();

		while (callStack != null) {
			CallInfo top = callStack.car;
			callStack = callStack.cdr;

//			System.out.println("Will resume " + top.toString());
//			System.out.println("Call stack now: " + Cons.toString(callStack, " "));

			try {
				tail.clear();
				top.resume();
			}
			catch (ControlThrowable ct) {
//				System.out.println("Control event: " + ct.toString());
				Iterator<CallInfo> it = ct.frameIterator();
//				System.out.println("Call stack before: " + Cons.toString(callStack, " "));
				while (it.hasNext()) {
					callStack = new Cons<>(it.next(), callStack);
				}
//				System.out.println("Call stack after: " + Cons.toString(callStack, " "));

				assert (callStack != null);
				return true;  // we're paused
			}
		}

		// call stack is null, we're not paused
		return false;
	}

}
