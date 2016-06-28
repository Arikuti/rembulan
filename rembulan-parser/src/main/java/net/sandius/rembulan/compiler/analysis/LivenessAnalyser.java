package net.sandius.rembulan.compiler.analysis;

import net.sandius.rembulan.compiler.BasicBlock;
import net.sandius.rembulan.compiler.Blocks;
import net.sandius.rembulan.compiler.IRFunc;
import net.sandius.rembulan.compiler.ir.AbstractVal;
import net.sandius.rembulan.compiler.ir.IRNode;
import net.sandius.rembulan.compiler.ir.Label;
import net.sandius.rembulan.compiler.ir.PhiVal;
import net.sandius.rembulan.compiler.ir.UpVar;
import net.sandius.rembulan.compiler.ir.Val;
import net.sandius.rembulan.compiler.ir.Var;
import net.sandius.rembulan.compiler.ir.VarStore;
import net.sandius.rembulan.compiler.util.BlockUtils;
import net.sandius.rembulan.parser.util.Util;
import net.sandius.rembulan.util.Check;

import java.util.*;

public class LivenessAnalyser {

	private final IRFunc fn;

	private final Map<IRNode, Set<Var>> varLiveIn;
	private final Map<IRNode, Set<AbstractVal>> valLiveIn;

	private Map<Label, Set<Var>> endVarLiveIn;
	private Map<Label, Set<AbstractVal>> endValLiveIn;

	private LivenessAnalyser(IRFunc fn) {
		this.fn = Check.notNull(fn);

		this.varLiveIn = new HashMap<>();
		this.valLiveIn = new HashMap<>();

		this.endVarLiveIn = new HashMap<>();
		this.endValLiveIn = new HashMap<>();
	}

	public static LivenessInfo computeLiveness(IRFunc fn) {
		LivenessAnalyser analyser = new LivenessAnalyser(fn);
		return analyser.analyse();
	}

	public LivenessInfo analyse() {
		Blocks blocks = fn.blocks();

		Map<Label, BasicBlock> index = blocks.index();
		Map<Label, Set<Label>> in = BlockUtils.inLabels(index, blocks.entryLabel());

		// initialise
		{
			for (Label l : index.keySet()) {
				endVarLiveIn.put(l, new HashSet<Var>());
				endValLiveIn.put(l, new HashSet<AbstractVal>());
			}

			Iterator<IRNode> ns = BlockUtils.nodeIterator(blocks);
			while (ns.hasNext()) {
				IRNode n = ns.next();
				varLiveIn.put(n, new HashSet<Var>());
				valLiveIn.put(n, new HashSet<AbstractVal>());
			}
		}

		Stack<Label> open = new Stack<>();

		// make sure we'll visit all labels at least once
		for (Label l : BlockUtils.labelsBreadthFirst(index, blocks.entryLabel())) {
			open.push(l);
		}

		while (!open.isEmpty()) {
			Label l = open.pop();

			LivenessVisitor visitor = new LivenessVisitor(
					endVarLiveIn.get(l),
					endValLiveIn.get(l));

			boolean changed = processBlock(visitor, index.get(l));

			for (Label inl : in.get(l)) {
				boolean nextChg;

				System.out.println("Pushing into label " + inl);

				System.out.print("\t\t(var) {" + Util.iterableToString(endVarLiveIn.get(inl), ", ") + "}");
				System.out.print(" -> ");

				nextChg = endVarLiveIn.get(inl).addAll(visitor.currentVarLiveIn());

				System.out.println("{" + Util.iterableToString(endVarLiveIn.get(inl), ", ") + "}");


				System.out.print("\t\t(val) {" + Util.iterableToString(endValLiveIn.get(inl), ", ") + "}");
				System.out.print(" -> ");

				nextChg |= endValLiveIn.get(inl).addAll(visitor.currentValLiveIn());

				System.out.println("{" + Util.iterableToString(endValLiveIn.get(inl), ", ") + "}");

				if (nextChg) {
					System.out.println("\t\t(change!)");
					if (open.contains(inl)) {
						open.remove(inl);
					}
					open.push(inl);
				}

			}

		}

		return new LivenessInfo(varLiveIn, valLiveIn);
	}

	private boolean processBlock(LivenessVisitor visitor, BasicBlock block) {
		// iterating backwards
		boolean changed = processNode(visitor, block.end());

		for (int i = block.body().size() - 1; i >= 0; i--) {
			changed |= processNode(visitor, block.body().get(i));
		}

		return changed;

	}

	private boolean processNode(LivenessVisitor visitor, IRNode node) {
		Check.notNull(node);
		System.out.println("In node " + node);

		final Set<AbstractVal> valLive = valLiveIn.get(node);
		final Set<Var> varLive = varLiveIn.get(node);

		node.accept(visitor);

		boolean varSame = visitor.currentVarLiveIn().equals(varLive);
		boolean valSame = visitor.currentValLiveIn().equals(valLive);

		if (!varSame) {
			System.out.println("\t\t(var) {" + Util.iterableToString(varLive, ", ") + "} -> {" + Util.iterableToString(visitor.currentVarLiveIn(), ", ") + "}");
			varLive.clear();
			varLive.addAll(visitor.currentVarLiveIn());
		}

		if (!valSame) {
			System.out.println("\t\t(val) {" + Util.iterableToString(valLive, ", ") + "} -> {" + Util.iterableToString(visitor.currentValLiveIn(), ", ") + "}");
			valLive.clear();
			valLive.addAll(visitor.currentValLiveIn());
		}

		return !varSame || !valSame;
	}

	private class LivenessVisitor extends AbstractUseDefVisitor {

		private Set<Var> currentVarLiveIn;
		private Set<AbstractVal> currentValLiveIn;

		public LivenessVisitor(Set<Var> currentVarLiveIn, Set<AbstractVal> currentValLiveIn) {
			this.currentVarLiveIn = new HashSet<>(Check.notNull(currentVarLiveIn));
			this.currentValLiveIn = new HashSet<>(Check.notNull(currentValLiveIn));
		}

		public Set<Var> currentVarLiveIn() {
			return currentVarLiveIn;
		}

		public Set<AbstractVal> currentValLiveIn() {
			return currentValLiveIn;
		}

		@Override
		protected void def(Val v) {
			currentValLiveIn.remove(v);
		}

		@Override
		protected void use(Val v) {
			currentValLiveIn.add(v);
		}

		@Override
		protected void def(PhiVal pv) {
			currentValLiveIn.remove(pv);
		}

		@Override
		protected void use(PhiVal pv) {
			currentValLiveIn.add(pv);
		}

		@Override
		protected void def(Var v) {
			currentVarLiveIn.remove(v);
		}

		@Override
		protected void use(Var v) {
			currentVarLiveIn.add(v);
		}

		@Override
		protected void def(UpVar uv) {
			// no effect on liveness
		}

		@Override
		protected void use(UpVar uv) {
			// no effect on liveness
		}

		@Override
		public void visit(VarStore node) {
			use(node.src());
			use(node.var());  // Note that this is a use, not a def
		}

	}

}
