package decaf.backend;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;

import decaf.Driver;
import decaf.dataflow.BasicBlock;
import decaf.machdesc.Register;
import decaf.tac.Tac;
import decaf.tac.Temp;


class InferenceGraph {
	public Set<Temp> nodes = new HashSet<>();
	public Map<Temp, Set<Temp>> neighbours = new HashMap<>();
	public Map<Temp, Integer> nodeDeg = new HashMap<>();
	public BasicBlock bb;
	public Register[] regs;
	//public Register fp;
	public Temp fp;
	public Set<Temp> liveUseLoad = new HashSet<>();


	private void clear() {
		nodes.clear();
		neighbours.clear();
		nodeDeg.clear();
		liveUseLoad.clear();
	}


	public void alloc(BasicBlock bb, Register[] regs, Temp fp) {
		this.regs = regs;
		this.bb = bb;
		this.fp = fp;
		while (true) {
			clear();
			makeGraph();
			if (color())
				break;
			// For simplicity, omit handling for spilling.
		}
	}


	private void addNode(Temp node) {
		if (nodes.contains(node)) return;
		//if (node.reg != null && node.reg.equals(fp)) return;
		if (node.equals(fp)) return;
		//System.out.println("add " + node);
		nodes.add(node);
		neighbours.put(node, new HashSet<Temp>());
		nodeDeg.put(node, 0);
	}


	private void removeNode(Temp n) {
		nodes.remove(n);
		for (Temp m : neighbours.get(n))
			if (nodes.contains(m))
				nodeDeg.put(m, nodeDeg.get(m) - 1);
	}


	private void addEdge(Temp a, Temp b) {
		//System.out.println(a + " " + b);
		if (neighbours.get(a).contains(b)) return;
		neighbours.get(a).add(b);
		neighbours.get(b).add(a);
		nodeDeg.put(a, nodeDeg.get(a) + 1);
		nodeDeg.put(b, nodeDeg.get(b) + 1);
	}


	private boolean color() {
		if (nodes.isEmpty())
			return true;

		// Try to find a node with less than K neighbours
		Temp n = null;
		for (Temp t : nodes) {
			if (nodeDeg.get(t) < regs.length) {
				n = t;
				break;
			}
		}

		if (n != null) {
			// We've found such a node.
			removeNode(n);
			boolean subColor = color();
			n.reg = chooseAvailableRegister(n);
			return subColor;
		} else {
			throw new IllegalArgumentException(
					"Coloring with spilling is not yet supported");
		}
	}


	Register chooseAvailableRegister(Temp n) {
		Set<Register> usedRegs = new HashSet<>();
		for (Temp m : neighbours.get(n)) {
			if (m.reg == null) continue;
			usedRegs.add(m.reg);
		}
		for (Register r : regs)
			if (!usedRegs.contains(r))
				return r;
		return null;
	}


	void makeGraph() {
		// First identify all nodes. 
		// Each value is a node.
		makeNodes();
		// Then build inference edges:
		// It's your job to decide what values should be linked.
		makeEdges();
	}


	void makeNodes() {
		for (Tac tac = bb.tacList; tac != null; tac = tac.next) {
			switch (tac.opc) {
				case ADD: case SUB: case MUL: case DIV: case MOD:
				case LAND: case LOR: case GTR: case GEQ: case EQU:
				case NEQ: case LEQ: case LES:
					addNode(tac.op0); addNode(tac.op1); addNode(tac.op2);
					break;

				case NEG: case LNOT: case ASSIGN:
					addNode(tac.op0); addNode(tac.op1);
					break;

				case LOAD_VTBL: case LOAD_IMM4: case LOAD_STR_CONST:
					addNode(tac.op0);
					break;

				case INDIRECT_CALL:
					addNode(tac.op1);
				case DIRECT_CALL:
					// tac.op0 is used to hold the return value.
					// If we are calling a function with void type, then tac.op0 is null.
					if (tac.op0 != null) addNode(tac.op0);
					break;

				case PARM:
					addNode(tac.op0);
					break;

				case LOAD:
				case STORE:
					addNode(tac.op0); addNode(tac.op1);
					break;

				case BRANCH: case BEQZ: case BNEZ: case RETURN:
					throw new IllegalArgumentException();
			}
		}
	}

	void makeEdgesHelper(Temp tmp, Set<Temp> liveOut) {
		for (Temp t: liveOut) {
			if (!t.equals(tmp) && neighbours.containsKey(t)) {
				addEdge(tmp, t);
			}
		}
	}

	// With your definition of inference graphs, build the edges.
	void makeEdges() {
		for (Tac tac = bb.tacList; tac != null; tac = tac.next) {
			switch (tac.opc) {
				case ADD: case SUB: case MUL: case DIV: case MOD:
				case LAND: case LOR: case GTR: case GEQ: case EQU:
				case NEQ: case LEQ: case LES:
					// op0 is defined
					makeEdgesHelper(tac.op0, tac.liveOut);

				case NEG: case LNOT: case ASSIGN:
					// op0 is defined
					makeEdgesHelper(tac.op0, tac.liveOut);

				case LOAD_VTBL: case LOAD_IMM4: case LOAD_STR_CONST:
					// op0 is defined
					makeEdgesHelper(tac.op0, tac.liveOut);

				case INDIRECT_CALL:
				case DIRECT_CALL:
					// op0 maybe null
					if (tac.op0 != null) {
						makeEdgesHelper(tac.op0, tac.liveOut);
					}

				case PARM:
					break;

				case LOAD:
					makeEdgesHelper(tac.op0, tac.liveOut);

				case STORE:
					break;

				case BRANCH: case BEQZ: case BNEZ: case RETURN:
					throw new IllegalArgumentException();
			}
		}
	}
}

