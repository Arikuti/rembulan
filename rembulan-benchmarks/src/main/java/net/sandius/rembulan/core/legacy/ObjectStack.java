package net.sandius.rembulan.core.legacy;

import net.sandius.rembulan.util.Check;

public class ObjectStack {

	protected final Object[] values;
	protected int top;

	private ObjectStack(int maxSize) {
		Check.positive(maxSize);

		this.values = new Object[maxSize];
		this.top = 0;
	}

	public static ObjectStack newEmptyStack(int maxSize) {
		return new ObjectStack(maxSize);
	}

	public int getMaxSize() {
		return values.length;
	}

	public int getTop() {
		return top;
	}

	public void setTop(int to) {
		Check.inRange(to, 0, values.length);

		if (to < top) {
			for (int i = to; i < top; i++) {
				// clear values above the new top
				values[i] = null;
			}
		}
		top = to;
	}

	public boolean isEmpty() {
		return top == 0;
	}

	public void push(Object[] args) {
		Check.notNull(args);

		if (top + args.length > values.length) {
			throw new IllegalArgumentException("Not enough space in object stack: pushing "
					+ args.length + " values, " + (values.length - top) + " slots free" );
		}

		System.arraycopy(args, 0, values, top, args.length);
		top += args.length;
	}

	public Object get(int i) {
		return values[i];
	}

	public void set(int i, Object o) {
		values[i] = o;
	}

	public View viewFrom(int base) {
		setTop(base);
		return new View(this, base);
	}

	public View rootView() {
		return viewFrom(0);
	}

	public static class View implements Registers {

		public final ObjectStack objectStack;
		public final int offset;

		protected View(ObjectStack objectStack, int offset) {
			this.objectStack = Check.notNull(objectStack);
			this.offset = offset;
		}

		public View from(int offset) {
			return new View(objectStack, this.offset + offset);
		}

		@Override
		public String toString() {
			return "view:" + Integer.toHexString(objectStack.hashCode()) + "/" + offset;
		}

		@Override
		public int size() {
			return objectStack.getMaxSize() - offset;
		}

		@Override
		public void push(Object object) {
			int top = getTop();
			if (top < size()) {
				set(top, object);
				setTop(getTop() + 1);
			}
		}

		@Override
		public Object get(int idx) {
			return objectStack.get(idx + offset);
		}

		@Override
		public void set(int idx, Object object) {
			objectStack.set(idx + offset, object);
		}

		@Override
		public int getTop() {
			return objectStack.getTop() - offset;
		}

		@Override
		public void setTop(int newTop) {
			objectStack.setTop(offset + newTop);
		}

		@Override
		public ReturnTarget returnTargetFrom(int offset) {
			return new ReturnTarget(this, offset);
		}

	}

}
