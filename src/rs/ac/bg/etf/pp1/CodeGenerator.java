package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;

import java.util.*;

public class CodeGenerator extends VisitorAdaptor {
    private static final int WORD_WIDTH = 4;

	private int varCount;
	
	private int paramCnt;
	
	private int mainPc;

	private Obj currentClass = Tab.noObj;
    private ArrayList<Byte> methodTable;
    private Stack<Integer> doWhileTopStack;
    private ArrayList<Integer> condTrueFixupList;
    private Stack<ArrayList<Integer>> condFalseFixupStack;
    private Stack<ArrayList<Integer>> breakFixupStack;
    private Stack<Integer> skipElseJmpAdrStack;
    private Stack<Boolean> isVirtualCallStack;

    private Stack<Integer> relop;
    private Stack<Integer> addop;
    private Stack<Integer> mulop;

    CodeGenerator() {
        super();
        methodTable = new ArrayList<>();
        doWhileTopStack = new Stack<>();
        condTrueFixupList = new ArrayList<>();
        condFalseFixupStack = new Stack<>();
        breakFixupStack = new Stack<>();
        skipElseJmpAdrStack = new Stack<>();
        isVirtualCallStack = new Stack<>();

        relop = new Stack<>();
        addop = new Stack<>();
        mulop = new Stack<>();

        // generisanje predefinisanih metoda
        Obj chrObj = Tab.find("chr");
        Obj ordObj = Tab.find("ord");
        chrObj.setAdr(Code.pc);
        ordObj.setAdr(Code.pc);
        Code.put(Code.enter);
        Code.put(1);
        Code.put(1);
        Code.put(Code.load_n/* + 0 */);
        Code.put(Code.exit);
        Code.put(Code.return_);

        Obj lenObj = Tab.find("len");
        lenObj.setAdr(Code.pc);
        Code.put(Code.enter);
        Code.put(1);
        Code.put(1);
        Code.put(Code.load_n/* + 0 */);
        Code.put(Code.arraylength);
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    public int getMainPc() {
		return mainPc;
	}

    //****************************Helper functions****************************

    private void addWordToStaticData(int value, int address)
    {
        methodTable.add((byte) Code.const_);
        methodTable.add((byte) ((value >> 16) >> 8));
        methodTable.add((byte) (value >> 16));
        methodTable.add((byte) (value >> 8));
        methodTable.add((byte) value);
        methodTable.add((byte) Code.putstatic);
        methodTable.add((byte) (address >> 8));
        methodTable.add((byte) address);
    }
    private void addNameTerminator()
    {
        addWordToStaticData(-1, Code.dataSize++);
    }
    private void addTableTerminator()
    {
        addWordToStaticData(-2, Code.dataSize++);
    }
    private void addFunctionAddress(int functionAddress)
    {
        addWordToStaticData(functionAddress, Code.dataSize++);
    }
    private void addFunctionEntry(String name, int functionAddressInCodeBuffer)
    {
        for (int j = 0; j < name.length(); j++)
        {
            addWordToStaticData((int)(name.charAt(j)), Code.dataSize++);
        }
        addNameTerminator();
        addFunctionAddress(functionAddressInCodeBuffer);
    }

    //****************************Visitor functions****************************

    public void visit(ClassDecl classDecl) {
        currentClass.setAdr(Code.dataSize);
        Collection<Obj> members = currentClass.getType().getMembers().symbols();
        for (Obj member : members) {
            if (member.getKind() == Obj.Meth) {
                addFunctionEntry(member.getName(), member.getAdr());
            }
        }
        addTableTerminator();

        currentClass = Tab.noObj;
    }

    public void visit(ClassName className) {
        currentClass = className.obj;
    }

	public void visit(MethodSignature methodSignature) {
	    Obj methObj = methodSignature.obj;
        methObj.setAdr(Code.pc);
        if (TabExt.methodAdrFixMap.containsKey(methObj)) {
            for (Obj method : TabExt.methodAdrFixMap.get(methObj)) {
                method.setAdr(Code.pc);
            }
        }

        if ("main".equalsIgnoreCase(methObj.getName())) {
            mainPc = Code.pc;
        }

		Code.put(Code.enter);
        if (TabExt.methodScopeMap.containsKey(methObj)) {
            // hidden 'this'
            Code.put(1 + methObj.getLevel());
        } else {
            Code.put(methObj.getLevel());
        }
		Code.put(methObj.getLocalSymbols().size());

        if ("main".equalsIgnoreCase(methObj.getName())) {
            for (Byte instr : methodTable) {
                Code.put(instr);
            }
            methodTable.clear();
        }
    }

	public void visit(MethodDecl methodDecl) {
	    Obj methObj = methodDecl.getMethodSignature().obj;
	    if (methObj.getType().equals(Tab.noType)) {
            Code.put(Code.exit);
            Code.put(Code.return_);
        } else {
	        Code.put(Code.trap);
	        Code.put(1);
        }
	}

	public void visit(Statement_If statement_if) {
        condFalseFixupStack.pop();
    }

	public void visit(IfCondition_Cond ifCondition_cond) {
        Code.putFalseJump(relop.pop(), 0);
        condFalseFixupStack.peek().add(Code.pc - 2);

        for (int adr : condTrueFixupList) {
            Code.fixup(adr);
        }
    }

    public void visit(IfDummy ifDummy) {
        condFalseFixupStack.add(new ArrayList<>());
    }

    public void visit(OptElseBranch_NoEps optElseBranch_noEps) {
        int skipElseJmpAdr = skipElseJmpAdrStack.pop();
        Code.fixup(skipElseJmpAdr);
    }

    public void visit(ElseDummy elseDummy) {
        Code.putJump(0);
        skipElseJmpAdrStack.add(Code.pc - 2);

        for (int adr : condFalseFixupStack.peek()) {
            Code.fixup(adr);
        }
    }

    public void visit(OptElseBranch_Eps optElseBranch_eps) {
        for (int adr : condFalseFixupStack.peek()) {
            Code.fixup(adr);
        }
    }

	public void visit(Statement_DoWhile statement_doWhile) {
        int top = doWhileTopStack.pop();

        Code.put(Code.jcc + relop.pop());
        Code.put2(top - Code.pc + 1);

        for (int adr : condFalseFixupStack.peek()) {
            Code.fixup(adr);
        }

        for (int adr : condTrueFixupList) {
            Code.put2(adr, (top - adr + 1));
        }

        for (int adr : breakFixupStack.peek()) {
            Code.fixup(adr);
        }

        condFalseFixupStack.pop();
        breakFixupStack.pop();
    }

    public void visit(DoWhileDummy doWhileDummy) {
	    doWhileTopStack.push(Code.pc);
        condFalseFixupStack.push(new ArrayList<>());
        breakFixupStack.push(new ArrayList<>());
    }

    public void visit(Statement_Break statement_break) {
        Code.putJump(0);
        breakFixupStack.peek().add(Code.pc - 2);
    }

    public void visit(Statement_Cont statement_cont) {
        Code.putJump(doWhileTopStack.peek());
    }

	public void visit(Statement_Return statement_return) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public void visit(Statement_Read statement_read) {
	    Obj desigObj = statement_read.getDesignator().obj;
        if (desigObj.getType().equals(Tab.charType)) {
            Code.put(Code.bread);
        } else {
            Code.put(Code.read); // TODO: sta za bool?
        }
        Code.store(desigObj);
    }

    public void visit(Statement_Print statement_print) {
        if (statement_print.getExpr().struct.equals(Tab.charType)) {
            Code.put(Code.const_1);
            Code.put(Code.bprint);
        } else {
            Code.put(Code.const_5); // TODO: sta za bool?
            Code.put(Code.print);
        }
    }

    public void visit(Statement_PrintParam statement_printParam) {
        int width = statement_printParam.getN2();
        if (statement_printParam.getExpr().struct.equals(Tab.charType)) {
            Code.loadConst(width);
            Code.put(Code.bprint);
        } else {
            Code.loadConst(width); // TODO: sta za bool?
            Code.put(Code.print);
        }
    }

	public void visit(DesignatorStatement_Assign designatorStatement_assign) {
	    Code.store(designatorStatement_assign.getDesignator().obj);
    }

    public void visit(DesignatorStatement_Func designatorStatement_func) {
	    if (!designatorStatement_func.getFunctionCall().struct.equals(Tab.noType)) {
            // sklanjamo nepotrebnu vrednost sa esteka
            Code.put(Code.pop);
        }
    }

    public void visit(DesignatorStatement_Inc designatorStatement_inc) {
	    Code.load(designatorStatement_inc.getDesignator().obj);
	    Code.loadConst(1);
	    Code.put(Code.add);
        Code.store(designatorStatement_inc.getDesignator().obj);
    }

    public void visit(DesignatorStatement_Dec designatorStatement_dec) {
        Code.load(designatorStatement_dec.getDesignator().obj);
        Code.loadConst(1);
        Code.put(Code.sub);
        Code.store(designatorStatement_dec.getDesignator().obj);
    }

    public void visit(FunctionCall functionCall) {
        Obj methObj = functionCall.getDesignator().obj;
        if (!TabExt.methodScopeMap.containsKey(methObj)) {
            // globalna funkcija
            int offset = methObj.getAdr() - Code.pc;
            Code.put(Code.call);
            Code.put2(offset);
        } else {
            // klasna metoda
            Code.put(Code.getfield);
            Code.put2(0);
            Code.put(Code.invokevirtual);
            String methName = methObj.getName();
            for (int j = 0; j < methName.length(); j++)
            {
                Code.put4(methName.charAt(j));
            }
            Code.put4(-1);
        }

        isVirtualCallStack.pop();
    }

    public void visit(PreActParsDummy preActParsDummy) {
        Obj methObj = ((FunctionCall)preActParsDummy.getParent()).getDesignator().obj;
        if (TabExt.methodScopeMap.containsKey(methObj)) {
            Code.put(Code.dup);
            isVirtualCallStack.push(true);
        } else {
            isVirtualCallStack.push(false);
        }
    }

    public void visit(ActPar actPar) {
        if (isVirtualCallStack.peek()) {
            Code.put(Code.dup_x1);
            Code.put(Code.pop);
        }
    }

    public void visit(OrDummy orDummy) {
        Code.put(Code.jcc + relop.pop());
        Code.put2(0);
        condTrueFixupList.add(Code.pc - 2);

        for (int adr : condFalseFixupStack.peek()) {
            Code.fixup(adr);
        }
    }

    public void visit(AndDummy andDummy) {
        Code.putFalseJump(relop.pop(), 0);
        condFalseFixupStack.peek().add(Code.pc - 2);
    }

    public void visit(CondFact_Expr condFact_expr) {
        Code.loadConst(0); // value of 'false'
        relop.push(Code.ne);
    }

    public void visit(Expr_Neg expr_neg) {
        Code.put(Code.neg);
    }

    public void visit(Expr_Addop expr_addop) {
        Code.put(addop.pop());
    }

    public void visit(Term_Mulop term_mulop) {
        Code.put(mulop.pop());
    }

    public void visit(Factor_Desig factor_desig) {
        Code.load(factor_desig.getDesignator().obj);
    }

    public void visit(Factor_ConstVal factor_constVal) {
        Code.load(factor_constVal.getConstVal().obj);
    }

    public void visit(Factor_New factor_new) {
        Obj classType = factor_new.getType().obj;
	    int s = WORD_WIDTH * (classType.getType().getNumberOfFields());
	    Code.put(Code.new_);
	    Code.put2(s);

	    Code.put(Code.dup);
	    Code.loadConst(classType.getAdr());
        Code.put(Code.putfield);
        Code.put2(0);
    }

    public void visit(Factor_NewArr factor_newArr) {
        Code.put(Code.newarray);
        Code.put(factor_newArr.struct.getElemType().equals(Tab.charType) ? 0 : 1); // TODO: sta za bool?
    }

    public void visit(Designator_Field designator_field) {
	    Code.load(designator_field.getDesignator().obj);
    }

    public void visit(PreArrIdxDummy preArrIdxDummy) {
        Obj arrObj = ((Designator_Array) preArrIdxDummy.getParent()).getDesignator().obj;
        Code.load(arrObj);
    }

    public void visit(Designator_Ident designator_ident) {
        if (designator_ident.obj.getKind() == Obj.Fld) {
            // implicitno this.IDENT
            Code.put(Code.load_n/* + 0*/);
        } else if (designator_ident.obj.getKind() == Obj.Meth
                && TabExt.methodScopeMap.containsKey(designator_ident.obj) && TabExt.methodScopeMap.get(designator_ident.obj).equals(currentClass)) {
            // da li je metoda iste klase?
            Code.put(Code.load_n/* + 0*/);
        }
    }

    public void visit(Relop_Eq relop_eq) {
        relop.push(Code.eq);
    }

    public void visit(Relop_Neq relop_neq) {
        relop.push(Code.ne);
    }

    public void visit(Relop_Gre relop_gre) {
        relop.push(Code.gt);
    }

    public void visit(Relop_Geq relop_geq) {
        relop.push(Code.ge);
    }

    public void visit(Relop_Les relop_les) {
        relop.push(Code.lt);
    }

    public void visit(Relop_Leq relop_leq) {
        relop.push(Code.le);
    }

    public void visit(Addop_Add addop_add) {
        addop.push(Code.add);
    }

    public void visit(Addop_Sub addop_sub) {
        addop.push(Code.sub);
    }

    public void visit(Mulop_Mul mulop_mul) {
        mulop.push(Code.mul);
    }

    public void visit(Mulop_Div mulop_div) {
        mulop.push(Code.div);
    }

    public void visit(Mulop_Mod mulop_mod) {
        mulop.push(Code.rem);
    }
}
