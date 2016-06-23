package net.sandius.rembulan.compiler.ir;

import net.sandius.rembulan.util.Check;

public class UpStore extends BodyNode {

	private final UpVar uv;
	private final Val src;

	public UpStore(UpVar uv, Val src) {
		this.uv = Check.notNull(uv);
		this.src = Check.notNull(src);
	}

	public UpVar upval() {
		return uv;
	}

	public Val src() {
		return src;
	}

	@Override
	public void accept(IRVisitor visitor) {
		visitor.visit(this);
	}

}
