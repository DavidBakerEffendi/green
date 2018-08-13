package za.ac.sun.cs.green.service.simplify;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import za.ac.sun.cs.green.Green;
import za.ac.sun.cs.green.Instance;
import za.ac.sun.cs.green.expr.Constant;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.IntVariable;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.Variable;
import za.ac.sun.cs.green.expr.Visitor;
import za.ac.sun.cs.green.expr.VisitorException;
import za.ac.sun.cs.green.service.BasicService;

public class ConstantPropagation extends BasicService {

	private static boolean varLeft;
	private static Map<Expression, Expression> varMap;

	public ConstantPropagation(Green solver) {
		super(solver);
	}

	@Override
	public Set<Instance> processRequest(Instance instance) {
		@SuppressWarnings("unchecked")
		Set<Instance> result = (Set<Instance>) instance.getData(getClass());
		if (result == null) {
			final Map<Variable, Variable> map = new HashMap<Variable, Variable>();
			final Expression e = propagate(instance.getFullExpression(), map);
			final Instance i = new Instance(getSolver(), instance.getSource(), null, e);
			result = Collections.singleton(i);
			instance.setData(getClass(), result);
		}
		return result;
	}

	public Expression propagate(Expression expression, Map<Variable, Variable> map) {
		varMap = new HashMap<Expression, Expression>();
		try {
			Expression finalExpression;
			log.log(Level.FINEST, "Before propagation: " + expression);
			/* Find where variables sit relative to constants */
			PositionVisitor positionVisitor = new PositionVisitor();
			expression.accept(positionVisitor);
			/* Map variables */
			VariableVisitor variableVisitor = new VariableVisitor();
			expression.accept(variableVisitor);
			/* Check for illogical statement */
			if (!variableVisitor.isLogical()) {
				log.log(Level.FINEST, "After simplification: " + Operation.FALSE);
				return Operation.FALSE;
			}
			variableVisitor.processMap();
			/* Propagate variables */
			PropagationVisitor propagationVisitor = new PropagationVisitor();
			expression.accept(propagationVisitor);
			expression = propagationVisitor.getExpression();
			log.log(Level.FINEST, "After propagation: " + expression);
			/* Hold a simplification check variable */
			finalExpression = expression;
			/* Simplify */
			SimplificationVisitor simplificationVisitor = new SimplificationVisitor();
			expression.accept(simplificationVisitor);
			Expression simplified = simplificationVisitor.getExpression();
			/* Check if expression can be further simplified */
			if (!finalExpression.equals(simplified)) {
				simplified.accept(variableVisitor);
				/* Check for illogical statement */
				if (!variableVisitor.isLogical()) {
					log.log(Level.FINEST, "After simplification: " + Operation.FALSE);
					return Operation.FALSE;
				}
				simplified.accept(propagationVisitor);
				simplified = propagationVisitor.getExpression();
				finalExpression = simplified;
				simplified.accept(simplificationVisitor);
				simplified = simplificationVisitor.getExpression();
			}
			simplified = simplificationVisitor.getFinalExpression(simplified);
			log.log(Level.FINEST, "After simplification: " + simplified);
			return simplified;
		} catch (VisitorException x) {
			log.log(Level.SEVERE, "encountered an exception -- this should not be happening!", x);
		}
		return null;
	}

	private static class PositionVisitor extends Visitor {
		private Stack<Expression> stack;

		public PositionVisitor() {
			stack = new Stack<Expression>();
		}

		public Expression getExpression() {
			return stack.pop();
		}

		@Override
		public void postVisit(Operation operation) throws VisitorException {
			stack.push(operation);
			Operation.Operator op = operation.getOperator();
			Operation.Operator nop = null;
			switch (op) {
			case EQ:
				nop = Operation.Operator.EQ;
				break;
			case NE:
				nop = Operation.Operator.NE;
				break;
			case LT:
				nop = Operation.Operator.LT;
				break;
			case LE:
				nop = Operation.Operator.LE;
				break;
			case GT:
				nop = Operation.Operator.GT;
				break;
			case GE:
				nop = Operation.Operator.GE;
				break;
			default:
				break;
			}

			if (nop != null) {
				Operation oper = (Operation) stack.pop();
				Expression r;
				Expression l;
				for (Expression exp : oper.getOperands()) {
					stack.push(exp);
				}
				r = stack.pop();
				l = stack.pop();
				/* Map variables to their assigned constant */
				if (nop.equals(Operation.Operator.EQ)) {
					if (r instanceof IntVariable && l instanceof IntConstant) {
						varLeft = false;
					} else if (l instanceof IntVariable && r instanceof IntConstant) {
						varLeft = true;
					}
				}
			}
		}
	}

	private static class VariableVisitor extends Visitor {
		private Stack<Expression> stack;

		private boolean logical = true;

		public boolean isLogical() {
			return logical;
		}

		public VariableVisitor() {
			stack = new Stack<Expression>();
		}

		public Expression getExpression() {
			return stack.pop();
		}

		@Override
		public void postVisit(IntConstant constant) {
			stack.push(constant);
		}

		@Override
		public void postVisit(IntVariable variable) {
			stack.push(variable);
		}

		@Override
		public void postVisit(Operation operation) throws VisitorException {
			stack.push(operation);
			Operation.Operator op = operation.getOperator();
			Operation.Operator nop = null;
			switch (op) {
			case EQ:
				nop = Operation.Operator.EQ;
				break;
			case NE:
				nop = Operation.Operator.NE;
				break;
			case LT:
				nop = Operation.Operator.LT;
				break;
			case LE:
				nop = Operation.Operator.LE;
				break;
			case GT:
				nop = Operation.Operator.GT;
				break;
			case GE:
				nop = Operation.Operator.GE;
				break;
			default:
				break;
			}

			if (nop != null) {
				Operation oper = (Operation) stack.pop();
				Expression r;
				Expression l;
				for (Expression exp : oper.getOperands()) {
					stack.push(exp);
				}
				r = stack.pop();
				l = stack.pop();
				/* Map variables to their assigned constant */
				if (nop.equals(Operation.Operator.EQ)) {
					if (r instanceof IntVariable && l instanceof IntConstant) {
						if (varMap.containsKey(r)) {
							if (!varMap.get(r).equals(l)) {
								/* Then this is making a false assertion */
								logical = false;
							}
						} else {
							varMap.put(r, l);
						}
					} else if (l instanceof IntVariable && r instanceof IntConstant) {
						if (varMap.containsKey(l)) {
							if (!varMap.get(l).equals(r)) {
								/* Then this is making a false assertion */
								logical = false;
							}
						} else {
							varMap.put(l, r);
						}
					} else if (l instanceof IntVariable && r instanceof IntVariable) {
						if (varLeft) {
							varMap.put(l, r);
						} else {
							varMap.put(r, l);
						}
					}
				}
			}
		}

		/**
		 * Looks for symbolic links between already mapped variables and replaces them
		 * with their respective constants.
		 */
		public void processMap() {
			Map<Expression, Expression> tempMap = new HashMap<Expression, Expression>();
			for (int i = 0; i < varMap.size(); i++) {
				/* Find variables assigned to constants */
				for (Expression keyI : varMap.keySet()) {
					if (varMap.get(keyI) instanceof IntConstant) {
						tempMap.put(keyI, varMap.get(keyI));
					}
				}
				/* Find variables assigned to variables who have constants */
				for (Expression keyI : varMap.keySet()) {
					if (varMap.get(keyI) instanceof IntVariable) {
						if (tempMap.containsKey(varMap.get(keyI))) {
							varMap.put(keyI, (Expression) tempMap.get(varMap.get(keyI)));
						}
					}
				}
			}
			// System.out.println("Map:" + map);
		}
	}

	private static class PropagationVisitor extends Visitor {

		private Stack<Expression> stack;

		public PropagationVisitor() {
			stack = new Stack<Expression>();
		}

		public Expression getExpression() {
			return stack.pop();
		}

		@Override
		public void postVisit(IntConstant constant) {
			stack.push(constant);
		}

		@Override
		public void postVisit(IntVariable variable) {
			stack.push(variable);
		}

		@Override
		public void postVisit(Operation operation) throws VisitorException {
			Operation.Operator op = operation.getOperator();
			Operation.Operator nop = null;
			switch (op) {
			case EQ:
				nop = Operation.Operator.EQ;
				break;
			case NE:
				nop = Operation.Operator.NE;
				break;
			case LT:
				nop = Operation.Operator.GT;
				break;
			case LE:
				nop = Operation.Operator.GE;
				break;
			case GT:
				nop = Operation.Operator.LT;
				break;
			case GE:
				nop = Operation.Operator.LE;
				break;
			default:
				break;
			}

			if (nop != null) {
				Expression r = stack.pop();
				Expression l = stack.pop();

				if ((r instanceof IntVariable) && (l instanceof IntVariable)
						&& (((IntVariable) r).getName().compareTo(((IntVariable) l).getName()) < 0)) {
					/*
					 * If variable contains a value and it's on the constant side of equation then
					 * replace it with its assigned constant
					 */
					if (varMap.containsKey(r) && varLeft) {
						stack.push(new Operation(nop, varMap.get(r), l));
					} else if (varMap.containsKey(l) && !varLeft) {
						stack.push(new Operation(nop, r, varMap.get(l)));
					} else {
						stack.push(new Operation(nop, l, r));
					}
				} else if ((r instanceof IntVariable) && (l instanceof IntVariable)) {
					/*
					 * If variable contains a value and it's on the constant side of equation then
					 * replace it with its assigned constant
					 */
					if (varMap.containsKey(r) && varLeft) {
						stack.push(new Operation(nop, varMap.get(r), l));
					} else if (varMap.containsKey(l) && !varLeft) {
						stack.push(new Operation(nop, r, varMap.get(l)));
					} else {
						stack.push(new Operation(nop, l, r));
					}
				} else if ((r instanceof IntVariable) && (l instanceof IntConstant)
						&& !nop.equals(Operation.Operator.EQ)) {
					/* If the variable has constant assigned, replace it here */
					if (varMap.containsKey(r) && !varLeft) {
						System.out.println("Just pushed: " + new Operation(nop, varMap.get(r), l));
						stack.push(new Operation(nop, varMap.get(r), l));
					} else {
						stack.push(new Operation(nop, r, l));
					}
				} else if ((r instanceof IntConstant) && (l instanceof IntVariable)
						&& !nop.equals(Operation.Operator.EQ)) {
					/* If the variable has constant assigned, replace it here */
					if (varMap.containsKey(l) && varLeft) {
						System.out.println("Just pushed: " + new Operation(nop, r, varMap.get(l)));
						stack.push(new Operation(nop, r, varMap.get(l)));
					} else {
						stack.push(new Operation(nop, r, l));
					}
				} else {
					stack.push(new Operation(nop, r, l));
				}
			} else if (op.getArity() == 2) {
				Expression r = stack.pop();
				Expression l = stack.pop();
				/*
				 * If variable contains a value and it's on the constant side of equation then
				 * replace it with its assigned constant
				 */
				if (varMap.containsKey(r)) {
					stack.push(new Operation(op, l, varMap.get(r)));
				} else if (varMap.containsKey(l)) {
					stack.push(new Operation(op, varMap.get(l), r));
				} else {
					stack.push(new Operation(op, l, r));
				}
			} else {
				for (int i = op.getArity(); i > 0; i--) {
					stack.pop();
				}
				stack.push(operation);
			}
		}
	}

	private static class SimplificationVisitor extends Visitor {

		private Stack<Expression> stack;

		private SortedSet<Expression> conjuncts;

		private SortedSet<IntVariable> variableSet;

		private Map<IntVariable, Integer> lowerBounds;

		private Map<IntVariable, Integer> upperBounds;

		private IntVariable boundVariable;

		private Integer bound;

		private int boundCoeff;

		private boolean unsatisfiable;

		private boolean linearInteger;

		public SimplificationVisitor() {
			stack = new Stack<Expression>();
			conjuncts = new TreeSet<Expression>();
			variableSet = new TreeSet<IntVariable>();
			unsatisfiable = false;
			linearInteger = true;
		}

		public SortedSet<IntVariable> getVariableSet() {
			return variableSet;
		}

		public Expression getExpression() {
			if (!linearInteger) {
				return null;
			} else if (unsatisfiable) {
				return Operation.FALSE;
			} else {
				if (!stack.isEmpty()) {
					Expression x = stack.pop();
					if (x.equals(Operation.FALSE)) {
						return Operation.FALSE;
					} else if (!x.equals(Operation.TRUE)) {
						conjuncts.add(x);
					}
				}
				/*
				 * Process bounds returns the output conjuncts which only have '==' operator
				 */
				SortedSet<Expression> newConjuncts = processBounds();
				// new TreeSet<Expression>();
				Expression c = null;
				for (Expression e : newConjuncts) {
					if (e.equals(Operation.FALSE)) {
						return Operation.FALSE;
					} else if (e instanceof Operation) {
						Operation o = (Operation) e;
						if (o.getOperator() == Operation.Operator.GT) {
							e = new Operation(Operation.Operator.LT, scale(-1, o.getOperand(0)), o.getOperand(1));
						} else if (o.getOperator() == Operation.Operator.GE) {
							e = new Operation(Operation.Operator.LE, scale(-1, o.getOperand(0)), o.getOperand(1));
						}
						// New code
						/* Empty */
						// Old code
						/*
						 * o = (Operation) e; if (o.getOperator() == Operation.Operator.GT) { e = new
						 * Operation(Operation.Operator.GE, merge(o.getOperand(0), new IntConstant(-1)),
						 * o.getOperand(1));
						 * 
						 * } else if (o.getOperator() == Operation.Operator.LT) { e = new
						 * Operation(Operation.Operator.LE, merge(o.getOperand(0), new IntConstant(1)),
						 * o.getOperand(1)); }
						 */
					}
					if (c == null) {
						c = e;
					} else {
						c = new Operation(Operation.Operator.AND, c, e);
					}
				}
				return (c == null) ? Operation.TRUE : c;
			}
		}

		public Expression getFinalExpression(Expression simplifiedExpression) {
			stack.push(simplifiedExpression);
			if (!linearInteger) {
				return null;
			} else if (unsatisfiable) {
				return Operation.FALSE;
			} else {
				if (!stack.isEmpty()) {
					Expression x = stack.pop();
					if (x.equals(Operation.FALSE)) {
						return Operation.FALSE;
					} else if (!x.equals(Operation.TRUE)) {
						conjuncts.add(x);
					}
				}
				/*
				 * Process bounds returns the output conjuncts which only have '==' operator
				 */
				SortedSet<Expression> newConjuncts = processFinalBounds();
				// new TreeSet<Expression>();
				Expression c = null;
				for (Expression e : newConjuncts) {
					if (e.equals(Operation.FALSE)) {
						return Operation.FALSE;
					} else if (e instanceof Operation) {
						Operation o = (Operation) e;
						if (o.getOperator() == Operation.Operator.GT) {
							e = new Operation(Operation.Operator.LT, scale(-1, o.getOperand(0)), o.getOperand(1));
						} else if (o.getOperator() == Operation.Operator.GE) {
							e = new Operation(Operation.Operator.LE, scale(-1, o.getOperand(0)), o.getOperand(1));
						}
						// New code
						/* Empty */
						// Old code
						/*
						 * o = (Operation) e; if (o.getOperator() == Operation.Operator.GT) { e = new
						 * Operation(Operation.Operator.GE, merge(o.getOperand(0), new IntConstant(-1)),
						 * o.getOperand(1));
						 * 
						 * } else if (o.getOperator() == Operation.Operator.LT) { e = new
						 * Operation(Operation.Operator.LE, merge(o.getOperand(0), new IntConstant(1)),
						 * o.getOperand(1)); }
						 */
					}
					if (c == null) {
						c = e;
					} else {
						c = new Operation(Operation.Operator.AND, c, e);
					}
				}
				return (c == null) ? Operation.TRUE : c;
			}
		}

		/**
		 * Returns a processed version of the conjuncts.
		 * 
		 * @return
		 */
		private SortedSet<Expression> processBounds() {
			SortedSet<Expression> newConjuncts = new TreeSet<Expression>();
			for (Expression exp : conjuncts) {
				Operation oper = (Operation) exp;

				if (oper.getOperator() == Operation.Operator.EQ) {
					newConjuncts.add(exp);
				}
			}
			return newConjuncts;
		}

		/**
		 * Returns a processed version of the conjuncts.
		 * 
		 * @return
		 */
		private SortedSet<Expression> processFinalBounds() {
			SortedSet<Expression> newConjuncts = new TreeSet<Expression>();
			for (Expression exp : conjuncts) {
				Operation oper = (Operation) exp;

				if (oper.getOperator() == Operation.Operator.EQ) {
					for (Expression component : oper.getOperands()) {
						stack.push(component);
					}
					Expression r = stack.pop();
					Expression l = stack.pop();

					if (((r instanceof IntConstant) && (l instanceof IntVariable))
							|| ((l instanceof IntConstant) && (r instanceof IntVariable))) {
						newConjuncts.add(exp);
					}
				}
			}
			return newConjuncts;
		}

		@SuppressWarnings("unused")
		private void extractBound(Expression e) throws VisitorException {
			if (e instanceof Operation) {
				Operation o = (Operation) e;
				Expression lhs = o.getOperand(0);
				Operation.Operator op = o.getOperator();
				if (isBound(lhs)) {
					switch (op) {
					case EQ:
						lowerBounds.put(boundVariable, bound * boundCoeff);
						upperBounds.put(boundVariable, bound * boundCoeff);
						break;
					case LT:
						if (boundCoeff == 1) {
							upperBounds.put(boundVariable, bound * -1 - 1);
						} else {
							lowerBounds.put(boundVariable, bound + 1);
						}
						break;
					case LE:
						if (boundCoeff == 1) {
							upperBounds.put(boundVariable, bound * -1);
						} else {
							lowerBounds.put(boundVariable, bound);
						}
						break;
					case GT:
						if (boundCoeff == 1) {
							lowerBounds.put(boundVariable, bound * -1 + 1);
						} else {
							upperBounds.put(boundVariable, bound - 1);
						}
						break;
					case GE:
						if (boundCoeff == 1) {
							lowerBounds.put(boundVariable, bound * -1);
						} else {
							upperBounds.put(boundVariable, bound);
						}
						break;
					default:
						break;
					}
				}
			}
		}

		private boolean isBound(Expression lhs) {
			if (!(lhs instanceof Operation)) {
				return false;
			}
			Operation o = (Operation) lhs;
			if (o.getOperator() == Operation.Operator.MUL) {
				if (!(o.getOperand(0) instanceof IntConstant)) {
					return false;
				}
				if (!(o.getOperand(1) instanceof IntVariable)) {
					return false;
				}
				boundVariable = (IntVariable) o.getOperand(1);
				bound = 0;
				if ((((IntConstant) o.getOperand(0)).getValue() == 1)
						|| (((IntConstant) o.getOperand(0)).getValue() == -1)) {
					boundCoeff = ((IntConstant) o.getOperand(0)).getValue();
					return true;
				} else {
					return false;
				}
			} else if (o.getOperator() == Operation.Operator.ADD) {
				if (!(o.getOperand(1) instanceof IntConstant)) {
					return false;
				}
				bound = ((IntConstant) o.getOperand(1)).getValue();
				if (!(o.getOperand(0) instanceof Operation)) {
					return false;
				}
				Operation p = (Operation) o.getOperand(0);
				if (!(p.getOperand(0) instanceof IntConstant)) {
					return false;
				}
				if (!(p.getOperand(1) instanceof IntVariable)) {
					return false;
				}
				boundVariable = (IntVariable) p.getOperand(1);
				if ((((IntConstant) p.getOperand(0)).getValue() == 1)
						|| (((IntConstant) p.getOperand(0)).getValue() == -1)) {
					boundCoeff = ((IntConstant) p.getOperand(0)).getValue();
					return true;
				} else {
					return false;
				}
			} else {
				return false;
			}
		}

		@Override
		public void postVisit(Constant constant) {
			if (linearInteger && !unsatisfiable) {
				if (constant instanceof IntConstant) {
					stack.push(constant);
				} else {
					stack.clear();
					linearInteger = false;
				}
			}
		}

		@Override
		public void postVisit(Variable variable) {
			if (linearInteger && !unsatisfiable) {
				if (variable instanceof IntVariable) {
					variableSet.add((IntVariable) variable);
					stack.push(new Operation(Operation.Operator.MUL, Operation.ONE, variable));
				} else {
					stack.clear();
					linearInteger = false;
				}
			}
		}

		@Override
		public void postVisit(Operation operation) throws VisitorException {
			if (!linearInteger || unsatisfiable) {
				return;
			}
			Operation.Operator op = operation.getOperator();
			switch (op) {
			case AND:
				if (!stack.isEmpty()) {
					Expression x = stack.pop();
					if (!x.equals(Operation.TRUE)) {
						conjuncts.add(x);
					}
				}
				if (!stack.isEmpty()) {
					Expression x = stack.pop();
					if (!x.equals(Operation.TRUE)) {
						conjuncts.add(x);
					}
				}
				break;
			case EQ:
			case NE:
			case LT:
			case LE:
			case GT:
			case GE:
				if (!stack.isEmpty()) {
					Expression e = merge(scale(-1, stack.pop()), stack.pop());
					if (e instanceof IntConstant) {
						int v = ((IntConstant) e).getValue();
						boolean b = true;
						if (op == Operation.Operator.EQ) {
							b = v == 0;
						} else if (op == Operation.Operator.NE) {
							b = v != 0;
						} else if (op == Operation.Operator.LT) {
							b = v < 0;
						} else if (op == Operation.Operator.LE) {
							b = v <= 0;
						} else if (op == Operation.Operator.GT) {
							b = v > 0;
						} else if (op == Operation.Operator.GE) {
							b = v >= 0;
						}
						if (b) {
							stack.push(Operation.TRUE);
						} else {
							stack.push(Operation.FALSE);
							// unsatisfiable = true;
						}
					} else {
						/* This is where something becomes x + c == 0 */
						// New code:
						stack.push(swippySwoppy(e, op));
						// System.out.println("After swippy: " + swippySwoppy(e,
						// op));
						// Old code:
						// stack.push(new Operation(op, e, Operation.ZERO));
					}
				}
				break;
			case ADD:
				stack.push(merge(stack.pop(), stack.pop()));
				break;
			case SUB:
				stack.push(merge(scale(-1, stack.pop()), stack.pop()));
				break;
			case MUL:
				if (stack.size() >= 2) {
					Expression r = stack.pop();
					Expression l = stack.pop();
					if ((l instanceof IntConstant) && (r instanceof IntConstant)) {
						int li = ((IntConstant) l).getValue();
						int ri = ((IntConstant) r).getValue();
						stack.push(new IntConstant(li * ri));
					} else if (l instanceof IntConstant) {
						int li = ((IntConstant) l).getValue();
						stack.push(scale(li, r));
					} else if (r instanceof IntConstant) {
						int ri = ((IntConstant) r).getValue();
						stack.push(scale(ri, l));
					} else {
						stack.clear();
						linearInteger = false;
					}
				}
				break;
			case NOT:
				if (!stack.isEmpty()) {
					Expression e = stack.pop();
					if (e.equals(Operation.TRUE)) {
						e = Operation.FALSE;
					} else if (e.equals(Operation.FALSE)) {
						e = Operation.TRUE;
					} else if (e instanceof Operation) {
						Operation o = (Operation) e;
						switch (o.getOperator()) {
						case NOT:
							e = o.getOperand(0);
							break;
						case EQ:
							e = new Operation(Operation.Operator.NE, o.getOperand(0), o.getOperand(1));
							break;
						case NE:
							e = new Operation(Operation.Operator.EQ, o.getOperand(0), o.getOperand(1));
							break;
						case GE:
							e = new Operation(Operation.Operator.LT, o.getOperand(0), o.getOperand(1));
							break;
						case GT:
							e = new Operation(Operation.Operator.LE, o.getOperand(0), o.getOperand(1));
							break;
						case LE:
							e = new Operation(Operation.Operator.GT, o.getOperand(0), o.getOperand(1));
							break;
						case LT:
							e = new Operation(Operation.Operator.GE, o.getOperand(0), o.getOperand(1));
							break;
						default:
							break;
						}
					} else {
						// We just drop the NOT??
					}
					stack.push(e);
				} else {
					// We just drop the NOT??
				}
				break;
			default:
				break;
			}
		}

		/**
		 * TODO does a swippy swoppy around an operand and removes 1 / -1 coefficient
		 * 
		 * @param oper
		 */
		private Operation swippySwoppy(Expression e, Operator op) {
			// System.out.println("Doin a swippy swoppy on: " + e + " where oper is " + op);
			Operation oper = (Operation) e;
			Stack<Expression> sta = new Stack<Expression>();
			for (Expression exp : oper.getOperands()) {
				sta.push(exp);
			}
			Expression l = sta.pop();
			Expression r = sta.pop();
			// System.out.println("Left: " + l + "\tRight: " + r);

			/* Get rid of coefficient */
			if (r instanceof Operation) {
				for (Expression exp : ((Operation) r).getOperands()) {
					sta.push(exp);
				}
				IntVariable v = (IntVariable) sta.pop();
				IntConstant c = (IntConstant) sta.pop();
				r = v;
				if (c.getValue() == -1) {
					l = new IntConstant(((IntConstant) l).getValue() * -1);
				}
			} else if (l instanceof Operation) {
				for (Expression exp : ((Operation) l).getOperands()) {
					sta.push(exp);
				}
				IntVariable v = (IntVariable) sta.pop();
				IntConstant c = (IntConstant) sta.pop();
				l = v;
				if (c.getValue() == -1) {
					r = new IntConstant(((IntConstant) l).getValue() * -1);
				}
			}

			/* Swip the swoppy */
			if (r instanceof IntConstant) {
				r = new IntConstant(((IntConstant) r).getValue() * -1);
				if (!varLeft)
					return new Operation(op, l, r);
				else
					return new Operation(op, r, l);
			} else if (l instanceof IntConstant) {
				IntVariable var = (IntVariable) r;
				IntConstant cons = (IntConstant) l;
				// if (var.getName().equals("y") && cons.getValue() == -3)
				// System.out.println("Test yy " + r);
				l = new IntConstant(((IntConstant) l).getValue() * -1);
				if (!varLeft)
					return new Operation(op, l, r);
				else
					return new Operation(op, r, l);
			}

			return new Operation(op, e, Operation.ZERO);
		}

		private Expression merge(Expression left, Expression right) {
			Operation l = null;
			Operation r = null;
			int s = 0;
			if (left instanceof IntConstant) {
				s = ((IntConstant) left).getValue();
			} else {
				if (hasRightConstant(left)) {
					s = getRightConstant(left);
					l = getLeftOperation(left);
				} else {
					l = (Operation) left;
				}
			}
			if (right instanceof IntConstant) {
				s += ((IntConstant) right).getValue();
			} else {
				if (hasRightConstant(right)) {
					s += getRightConstant(right);
					r = getLeftOperation(right);
				} else {
					r = (Operation) right;
				}
			}
			SortedMap<Variable, Integer> coefficients = new TreeMap<Variable, Integer>();
			IntConstant c;
			Variable v;
			Integer k;

			// Collect the coefficients of l
			if (l != null) {
				while (l.getOperator() == Operation.Operator.ADD) {
					Operation o = (Operation) l.getOperand(1);
					assert (o.getOperator() == Operation.Operator.MUL);
					c = (IntConstant) o.getOperand(0);
					v = (IntVariable) o.getOperand(1);
					coefficients.put(v, c.getValue());
					l = (Operation) l.getOperand(0);
				}
				assert (l.getOperator() == Operation.Operator.MUL);
				c = (IntConstant) l.getOperand(0);
				v = (IntVariable) l.getOperand(1);
				coefficients.put(v, c.getValue());
			}

			// Collect the coefficients of r
			if (r != null) {
				while (r.getOperator() == Operation.Operator.ADD) {
					Operation o = (Operation) r.getOperand(1);
					assert (o.getOperator() == Operation.Operator.MUL);
					c = (IntConstant) o.getOperand(0);
					v = (IntVariable) o.getOperand(1);
					k = coefficients.get(v);
					if (k == null) {
						coefficients.put(v, c.getValue());
					} else {
						coefficients.put(v, c.getValue() + k);
					}
					r = (Operation) r.getOperand(0);
				}
				assert (r.getOperator() == Operation.Operator.MUL);
				c = (IntConstant) r.getOperand(0);
				v = (IntVariable) r.getOperand(1);
				k = coefficients.get(v);
				if (k == null) {
					coefficients.put(v, c.getValue());
				} else {
					coefficients.put(v, c.getValue() + k);
				}
			}

			Expression lr = null;
			for (Map.Entry<Variable, Integer> e : coefficients.entrySet()) {
				int coef = e.getValue();
				if (coef != 0) {
					Operation term = new Operation(Operation.Operator.MUL, new IntConstant(coef), e.getKey());
					if (lr == null) {
						lr = term;
					} else {
						lr = new Operation(Operation.Operator.ADD, lr, term);
					}
				}
			}
			if ((lr == null) || (lr instanceof IntConstant)) {
				return new IntConstant(s);
			} else if (s == 0) {
				return lr;
			} else {
				return new Operation(Operation.Operator.ADD, lr, new IntConstant(s));
			}
		}

		private boolean hasRightConstant(Expression expression) {
			return isAddition(expression) && (getRightExpression(expression) instanceof IntConstant);
		}

		private int getRightConstant(Expression expression) {
			return ((IntConstant) getRightExpression(expression)).getValue();
		}

		private Expression getLeftExpression(Expression expression) {
			return ((Operation) expression).getOperand(0);
		}

		private Expression getRightExpression(Expression expression) {
			return ((Operation) expression).getOperand(1);
		}

		private Operation getLeftOperation(Expression expression) {
			return (Operation) getLeftExpression(expression);
		}

		private boolean isAddition(Expression expression) {
			// TODO: isAddition test
			// System.out.println("isAddition(" + expression + ")");
			return ((Operation) expression).getOperator() == Operation.Operator.ADD;
		}

		private Expression scale(int factor, Expression expression) {
			if (factor == 0) {
				return Operation.ZERO;
			}
			if (expression instanceof IntConstant) {
				return new IntConstant(factor * ((IntConstant) expression).getValue());
			} else if (expression instanceof IntVariable) {
				return expression;
			} else {
				assert (expression instanceof Operation);
				Operation o = (Operation) expression;
				Operation.Operator p = o.getOperator();
				Expression l = scale(factor, o.getOperand(0));
				Expression r = scale(factor, o.getOperand(1));
				return new Operation(p, l, r);
			}
		}

	}

}