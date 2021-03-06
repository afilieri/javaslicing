package kkkjjjmmm.slicer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.visitor.ModifierVisitor;

public class SVFVisitor extends ModifierVisitor<Map<Statement, Statement>> {

	String SVFname = "variable";
	int SVFi = 1;

	@Override
	public Node visit(BlockStmt n, final Map<Statement, Statement> arg) {
		Map<Statement, Statement> extractedConditions = new HashMap<>();
		super.visit(n, extractedConditions);

		if (!extractedConditions.isEmpty()) {
			List<Statement> blockStatements = n.getStatements();
			NodeList<Statement> updatedSVFStatements = new NodeList<>();

			for (Statement stmt : blockStatements) {
				// TODO the hashes of identical nodes do not match!
				Optional<Map.Entry<Statement, Statement>> extracted = extractedConditions.entrySet().stream()
						.filter(entry -> entry.getKey().toString().equals(stmt.toString())).findFirst();

				if (extracted.isPresent()) {
					updatedSVFStatements.add(extracted.get().getValue());
				}
				updatedSVFStatements.add(stmt);
			}
			return new BlockStmt(updatedSVFStatements);
		}
		return n;
	}

	// Map<Node,Node> if() -> q1 = !i && !d
	@Override
	public Node visit(IfStmt n, final Map<Statement, Statement> arg) {
		super.visit(n, arg);
		Expression condition = n.getCondition();
		BlockStmt newb = new BlockStmt();

		String newVarName = SVFname + SVFi++;
		ExpressionStmt extractedCondition = new ExpressionStmt(
				new AssignExpr(new VariableDeclarationExpr(PrimitiveType.booleanType(), newVarName), condition,
						AssignExpr.Operator.ASSIGN));
		IfStmt cloned = n.clone();
		arg.put(cloned, extractedCondition);
		cloned.setCondition(new NameExpr(newVarName));
		newb.addStatement(extractedCondition);
		newb.addStatement(cloned);
		return newb;
	}

	@Override
	public Node visit(WhileStmt n, final Map<Statement, Statement> arg) {
		super.visit(n, arg);
		Expression condition = n.getCondition();
		BlockStmt whileWrapper = new BlockStmt();
		String newVarName = SVFname + SVFi++;
		ExpressionStmt extractedCondition = new ExpressionStmt(
				new AssignExpr(new VariableDeclarationExpr(PrimitiveType.booleanType(), newVarName), condition,
						AssignExpr.Operator.ASSIGN));
		WhileStmt cloned = n.clone();
		arg.put(cloned, extractedCondition);
		cloned.setCondition(new NameExpr(newVarName));
		Statement whileBody = cloned.getBody();
		if (whileBody instanceof BlockStmt) {
			BlockStmt blockInsideWhile = (BlockStmt) whileBody;
			blockInsideWhile.addStatement(new ExpressionStmt(
					new AssignExpr(new NameExpr(newVarName), condition, AssignExpr.Operator.ASSIGN)));
		} else {
			//TODO handle single-statement while loops, e.g.: 
			//  while(iterator.hasNext())
			//    doSomething()
			BlockStmt newb = new BlockStmt();
			newb.addStatement(whileBody);
			newb.addStatement(new ExpressionStmt(
					new AssignExpr(new NameExpr(newVarName), condition, AssignExpr.Operator.ASSIGN)));
			cloned.setBody(newb);
		}
		whileWrapper.addStatement(extractedCondition);
		whileWrapper.addStatement(cloned);
		return whileWrapper;
	}

	@Override
	public Node visit(MethodCallExpr n, final Map<Statement, Statement> arg) {
		String name = n.getName().asString();
		if (!name.equals("Observe")) {
			return n;
		}
		NodeList<Expression> args = n.getArguments();
		if (args.size() != 1) {
			System.out.println("wrong number of arguments!");
			return n;
		}
		Expression obsArg = args.get(0);

		//if (obsArg instanceof NameExpr && arg != null) {
			//NameExpr variableInsideObs = (NameExpr) obsArg;
		String newVarName = SVFname + SVFi++;
		// ExpressionStmt extractedArgument = new ExpressionStmt(
		// new AssignExpr(new VariableDeclarationExpr(PrimitiveType.booleanType(),
		// newVarName),
		// variableInsideObs, AssignExpr.Operator.ASSIGN));
		ExpressionStmt extractedArgument = new ExpressionStmt(
				new AssignExpr(new VariableDeclarationExpr(PrimitiveType.booleanType(), newVarName), 
						obsArg,AssignExpr.Operator.ASSIGN));
		Node node = n.getParentNode().get();
		arg.put((ExpressionStmt) node, extractedArgument);
		n.setArgument(0, new NameExpr(newVarName));
		//}

		return n;
	}
}
