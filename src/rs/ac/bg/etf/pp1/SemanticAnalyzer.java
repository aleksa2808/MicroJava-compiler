package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.*;

import java.util.*;

public class SemanticAnalyzer extends VisitorAdaptor {

    Struct currentType = null;
    Struct currentClass = null;
    Obj currentMethod = null;
    ArrayList<Obj> currentMethFormPars = null;
    Stack<ArrayList<Struct>> currentActParTypesStack;

    Map<Struct, Struct> inheritanceMap;

    boolean returnFound = false;
    boolean inDoWhileLoop = false;

    boolean errorDetected = false;

    static class TabExt {
        static Struct boolType = new Struct(1005); // zasto moramo da hardkodujemo...
    }

    SemanticAnalyzer() {
        currentActParTypesStack = new Stack<>();
        inheritanceMap = new HashMap<>();

        Tab.init(); // Universe scope
        Tab.insert(Obj.Type, "bool", TabExt.boolType);
    }

//********* Helper functions *****************

    private void report_error(String message/*, Object info*/) {
        errorDetected = true;
        StringBuilder msg = new StringBuilder(message);
//        if (info instanceof Symbol)
//            msg.append(" na liniji ").append(((Symbol)info).left);
        System.out.println("ERROR: " + msg.toString());
    }

    private void report_info(String message/*, Object info*/) {
        StringBuilder msg = new StringBuilder(message);
//        if (info instanceof Symbol)
//            msg.append(" na liniji ").append(((Symbol)info).left);
        System.out.println(msg.toString());
    }

    private Obj getMethodThis(Obj methObj) {
        Obj thisObj = null;

        if (methObj != null && methObj.getKind() == Obj.Meth) {
            Collection<Obj> methMembers = methObj.getLocalSymbols();
            for (Obj methMember : methMembers) {
                if ("this".equals(methMember.getName())) {
                    thisObj = methMember;
                    break;
                }
            }
        }

        return thisObj;
    }

    private boolean canOverride(MethodSignature methodSignature, Obj superClassMeth) {
        // provera return tipa
        if (!methodSignature.getVoidableType().struct.equals(superClassMeth.getType())) {
            return false;
        }

        // provera broja formalnih parametara
        int numOfFPs = superClassMeth.getLevel();
        if (currentMethFormPars.size() != numOfFPs) {
            return false;
        }

        // provera tipova formalnih parametara
        Collection<Obj> superClassMethLocals = superClassMeth.getLocalSymbols();
        Iterator<Obj> it1 = superClassMethLocals.iterator();
        Iterator<Obj> it2 = currentMethFormPars.iterator();

        // preskoci 'this'
        it1.next();
        for (int i = 0; i < numOfFPs; i++) {
            Obj superClassMethFP = it1.next();
            Obj currentMethFP = it2.next();

            if (!currentMethFP.getType().equals(superClassMethFP.getType())) {
                return false;
            }
        }

        return true;
    }

    private Obj findByName(String name, Collection<Obj> collection) {
        for (Obj item : collection) {
            if (item.getName().equals(name)) {
                return item;
            }
        }

        return null;
    }

    private boolean assignableTo(Struct srcType, Struct dstType) {
        if (srcType.assignableTo(dstType)) {
            return true;
        }

        // provera nasledjenosti
        if (srcType.getKind() == Struct.Class && dstType.getKind() == Struct.Class) {
            Struct superType = inheritanceMap.get(srcType);

            while (superType != null) {
                if (superType.equals(dstType)) {
                    return true;
                }
                superType = inheritanceMap.get(superType);
            }
        }

        return false;
    }

//********* Visitor functions *****************


    public void visit(Program program) {
        Tab.chainLocalSymbols(program.getProgName().obj);
        Tab.closeScope();
	}

    public void visit(ProgName progName) {
        report_info("Pronadjen glavni program " + progName.getI1() + " na liniji " + progName.getLine());

        progName.obj = Tab.insert(Obj.Prog, progName.getI1(), Tab.noType);
        Tab.openScope();
    }

    public void visit(ConstDecl constDecl) {
        currentType = null;
	}

	public void visit(ConstDeclDummy constDeclDummy) {
        currentType = ((ConstDecl)constDeclDummy.getParent()).getType().struct;
    }

    public void visit(ConstValAssign constValAssign) {
        report_info("Pronadjena konstantna vrednost " + constValAssign.getI1() + " na liniji " + constValAssign.getLine());

        Obj valObj = constValAssign.getConstVal().obj;
        if (valObj.getType().equals(currentType)) {
            if (Tab.currentScope().findSymbol(constValAssign.getI1()) == null) {
                Obj temp = Tab.insert(valObj.getKind(), constValAssign.getI1(), valObj.getType());
                temp.setAdr(valObj.getAdr());
            } else {
                report_error("Ime " + constValAssign.getI1() + " na liniji " + constValAssign.getLine() + " je vec deklarisano!");
            }
        } else {
            report_error("Nekompatibilni tipovi podataka na liniji " + constValAssign.getLine());
        }
    }

	public void visit(ConstVal_Num constVal_num) {
        constVal_num.obj = new Obj(Obj.Con, "", Tab.intType, constVal_num.getN1(), Obj.NO_VALUE);
    }

    public void visit(ConstVal_Char constVal_char) {
        constVal_char.obj = new Obj(Obj.Con, "", Tab.charType, constVal_char.getC1(), Obj.NO_VALUE);
    }

    public void visit(ConstVal_Bool constVal_bool) {
        constVal_bool.obj = new Obj(Obj.Con, "", TabExt.boolType, Boolean.valueOf(constVal_bool.getB1()) ? 1 : 0, Obj.NO_VALUE);
    }

    public void visit(VarDecl varDecl) {
        currentType = null;
    }

    public void visit(GlobalVarDecl globalVarDecl) {
        currentType = null;
    }

    public void visit(FieldVarDecl fieldVarDecl) {
        currentType = null;
    }

    public void visit(FieldVarDeclLbrace fieldVarDeclLbrace) {
        currentType = null;
    }

    public void visit(TypeDummy typeDummy) {
        currentType = typeDummy.getType().struct;
    }

    public void visit(VarDeclDefine varDeclDefine) {
        report_info("Pronadjena promenljiva " + varDeclDefine.getI1() + " na liniji " + varDeclDefine.getLine());

        if (Tab.currentScope().findSymbol(varDeclDefine.getI1()) == null) {
            int kind;
            if (currentClass != null && currentMethod == null) {
                kind = Obj.Fld;
            } else {
                kind = Obj.Var;
            }

            if (varDeclDefine.getOptArrayIndicator() instanceof OptArrayIndicator_Eps) {
                Tab.insert(kind, varDeclDefine.getI1(), currentType);
            } else {
                Tab.insert(kind, varDeclDefine.getI1(), new Struct(Struct.Array, currentType));
            }
        } else {
            report_error("Ime " + varDeclDefine.getI1() + " na liniji " + varDeclDefine.getLine() + " je vec deklarisano!");
        }
    }

    public void visit(ClassDecl classDecl) {
        if (currentClass != null) {
            Tab.chainLocalSymbols(currentClass);
            Tab.closeScope();
        }
        currentClass = null;
    }

    public void visit(ClassNameExtend classNameExtend) {
        report_info("Pronadjena klasa " + classNameExtend.getI1() + " na liniji " + classNameExtend.getLine());

        currentClass = new Struct(Struct.Class);
        Tab.insert(Obj.Type, classNameExtend.getI1(), currentClass);
        Tab.openScope();

        Struct extendsType = classNameExtend.getOptExtendsIndicator().struct;
        if (extendsType.getKind() == Struct.Class) {
            inheritanceMap.put(currentClass, extendsType);

            Collection<Obj> superClassMembers = extendsType.getMembers().symbols();
            for (Obj member : superClassMembers) {
                if (member.getKind() == Obj.Fld) {
                    Tab.insert(Obj.Fld, member.getName(), member.getType());
                }
            }
        }
    }

    public void visit(OptExtendsIndicator_NoEps optExtendsIndicator_noEps) {
        Struct classSuperType = optExtendsIndicator_noEps.getType().struct;
        if (classSuperType.getKind() == Struct.Class && !classSuperType.equals(Tab.nullType)) {
            optExtendsIndicator_noEps.struct = classSuperType;
        } else {
            report_error("-- extends ne ukazuje na unutrasnju klasu na liniji " + optExtendsIndicator_noEps.getLine());
            optExtendsIndicator_noEps.struct = Tab.noType;
        }
    }

    public void visit(OptExtendsIndicator_Error optExtendsIndicator_error) {
        optExtendsIndicator_error.struct = Tab.noType;
    }

    public void visit(OptExtendsIndicator_Eps optExtendsIndicator_eps) {
        optExtendsIndicator_eps.struct = Tab.noType;
    }

    public void visit(ClassDeclExtHelper classDeclExtHelper) {
        if (inheritanceMap.containsKey(currentClass)) {
            Collection<Obj> superClassMembers = inheritanceMap.get(currentClass).getMembers().symbols();
            for (Obj member : superClassMembers) {
                if (member.getKind() == Obj.Meth) {
                    Obj methObj = Tab.insert(Obj.Meth, member.getName(), member.getType());
                    methObj.setAdr(member.getAdr());
                    methObj.setFpPos(member.getFpPos());
                    methObj.setLevel(member.getLevel());

                    Collection<Obj> methLocals = member.getLocalSymbols();
                    Tab.openScope();

                    for (Obj methLocal : methLocals) {
                        Obj temp = Tab.insert(methLocal.getKind(), methLocal.getName(), methLocal.getType());
                        temp.setAdr(methLocal.getAdr());
                        temp.setFpPos(methLocal.getFpPos());
                        temp.setLevel(methLocal.getLevel());
                    }

                    Tab.chainLocalSymbols(methObj);
                    Tab.closeScope();
                }
            }
        }
    }

    public void visit(MethodDecl methodDecl) {
        if (currentMethod != null) {
            if (!returnFound && !currentMethod.getType().equals(Tab.noType)) {
                report_error("Funkcija " + methodDecl.getMethodSignature().getI2() + " nema return iskaz!");
            }

            Tab.chainLocalSymbols(currentMethod);
            Tab.closeScope();
            currentMethod = null;
        }
        returnFound = false;
    }

    public void visit(MethodSignature methodSignature) {
        report_info("Pronadjena funkcija " + methodSignature.getI2() + " na liniji " + methodSignature.getLine());

        currentMethod = null;
        // istoimeni simbol iz trenutnog scope-a
        Obj sym = Tab.currentScope().findSymbol(methodSignature.getI2());
        if (sym == null) {
            // ukoliko ga nema -> pronasli smo novu metodu
            currentMethod = Tab.insert(Obj.Meth, methodSignature.getI2(), methodSignature.getVoidableType().struct);
            Tab.openScope();

            if (currentClass != null) {
                Tab.insert(Obj.Var, "this", currentClass);
            }

            for (Obj methFormPar : currentMethFormPars) {
                Obj temp = Tab.insert(methFormPar.getKind(), methFormPar.getName(), methFormPar.getType());
                temp.setFpPos(methFormPar.getFpPos());
            }

            currentMethod.setLevel(currentMethFormPars.size());
        } else if (sym.getKind() == Obj.Meth && currentClass != null && inheritanceMap.containsKey(currentClass)){
            // ukoliko ga ima, ono predstavlja metodu, a trenutno obradjujemo nasledjenu klasu
            // proveravamo da li je 'this' parametar metode vec promenjen (vec overridovana klasa)
            Obj methThisObj = getMethodThis(sym);
            if (methThisObj != null && methThisObj.getType().equals(inheritanceMap.get(currentClass))) {
                // ako je 'this' tipa superklase, to znaci da smo naisli na prvi moguci override
                // ostaje nam da proverimo da li nova metoda ima kompatibilan potpis sa virtuelnom
                if (canOverride(methodSignature, sym)) {
                    currentMethod = sym;
                    Tab.openScope();
                    Tab.insert(Obj.Var, "this", currentClass);

                    for (Obj methFormPar : currentMethFormPars) {
                        Obj temp = Tab.insert(methFormPar.getKind(), methFormPar.getName(), methFormPar.getType());
                        temp.setFpPos(methFormPar.getFpPos());
                    }
                } else {
                    report_error("-- metoda " + methodSignature.getI2() + " na liniji " + methodSignature.getLine() + " nema kompatibilan potpis za override");
                }
            } else {
                report_error("-- dupli override metode " + methodSignature.getI2() + " na liniji " + methodSignature.getLine());
            }
        } else {
            report_error("Ime " + methodSignature.getI2() + " na liniji " + methodSignature.getLine() + " je vec deklarisano!");
        }

        currentMethFormPars = null;
    }

    public void visit(PreFormParsDummy preFormParsDummy) {
        currentMethFormPars = new ArrayList<>();
    }

    public void visit(VoidableType_Type voidableType_type) {
        voidableType_type.struct = voidableType_type.getType().struct;
    }

    public void visit(VoidableType_Void voidableType_void) {
        voidableType_void.struct = Tab.noType;
    }

    public void visit(FormParDecl_Ident formParDecl_ident) {
        if (findByName(formParDecl_ident.getI2(), currentMethFormPars) == null) {
            report_info("Pronadjen formalni parametar " + formParDecl_ident.getI2() + " na liniji " + formParDecl_ident.getLine());

            Struct formParType = null;
            if (formParDecl_ident.getOptArrayIndicator() instanceof OptArrayIndicator_Eps) {
                formParType = formParDecl_ident.getType().struct;
            } else {
                formParType = new Struct(Struct.Array, formParDecl_ident.getType().struct);
            }

            Obj formParObj = new Obj(Obj.Var, formParDecl_ident.getI2(), formParType);
            currentMethFormPars.add(formParObj);
        } else {
            report_error("Ime " + formParDecl_ident.getI2() + " na liniji " + formParDecl_ident.getLine() + " je vec deklarisano!");
        }
    }

    public void visit(Type type) {
        String typeName = type.getI1();
        Obj typeObjNode = Tab.find(typeName);
        if (typeObjNode == Tab.noObj)
        {
            report_error("Nije pronadjen tip " + typeName + " u tabeli simbola");

            type.struct = Tab.noType;
        }
        else
        {
            if (Obj.Type == typeObjNode.getKind())
            {
                type.struct = typeObjNode.getType();
            }
            else
            {
                report_error("Ime " + typeName + " ne predstavlja tip ");

                type.struct = Tab.noType;
            }
        }
    }

    public void visit(DesignatorStatement_Assign designatorStatement_assign) {
        Obj desigObj = designatorStatement_assign.getDesignator().obj;
        int kind = desigObj.getKind();

        if (kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld) {
            report_error("-- neispravan lhs na liniji " + designatorStatement_assign.getLine());
        } else if (!assignableTo(designatorStatement_assign.getAssignment().struct, desigObj.getType())) {
            report_error("-- nekompatibilni tipovi u dodeli na liniji " + designatorStatement_assign.getLine());
        }
    }

    public void visit(Assignment_Expr assignment_expr) {
        assignment_expr.struct = assignment_expr.getExpr().struct;
    }

    public void visit(Assignment_Error assignment_error) {
        assignment_error.struct = Tab.noType;
    }

    public void visit(DesignatorStatement_Inc designatorStatement_inc) {
        Obj desigObj = designatorStatement_inc.getDesignator().obj;
        int kind = desigObj.getKind();

        if (kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld ) {
            report_error("-- inc/dec designator mora biti promenljiva na liniji " + designatorStatement_inc.getLine());
        } else if (!desigObj.getType().equals(Tab.intType)) {
            report_error("-- inc/dec designator mora biti tipa int na liniji " + designatorStatement_inc.getLine());
        }
    }

    public void visit(DesignatorStatement_Dec designatorStatement_dec) {
        Obj desigObj = designatorStatement_dec.getDesignator().obj;
        int kind = desigObj.getKind();

        if (kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld) {
            report_error("-- inc/dec designator mora biti promenljiva na liniji " + designatorStatement_dec.getLine());
        } else if (!desigObj.getType().equals(Tab.intType)) {
            report_error("-- inc/dec designator mora biti tipa int na liniji " + designatorStatement_dec.getLine());
        }
    }

    public void visit(FunctionCall functionCall) {
        Obj desigObj = functionCall.getDesignator().obj;
        ArrayList<Struct> currentActParTypes = currentActParTypesStack.pop();

        if (desigObj.getKind() != Obj.Meth) {
            // TODO: nabavi full naziv designatora, a ne getI1  vvv
            report_error("Ime " + functionCall.getDesignator().getI1() + " nije funkcija na liniji " + functionCall.getLine());

            functionCall.struct = Tab.noType;
        } else {
            int numOfFPs = desigObj.getLevel();
            if (numOfFPs != currentActParTypes.size()) {
                report_error("-- broj parametara funkcije nije odgovarajuc na liniji " + functionCall.getLine());
            } else {
                ArrayList<Obj> methLocalParams = new ArrayList<>(desigObj.getLocalSymbols());

                // TODO: proveri za tip fje int globalFunction(classA this, int a, int b, int c);
                int thisOffset = 0;
                if (methLocalParams.size() > 0 && methLocalParams.get(0).getName().equals("this") && methLocalParams.get(0).getType().getKind() == Struct.Class) {
                    thisOffset = 1;
                }

                for (int i = 0; i < numOfFPs; i++) {
                    Obj formPar = methLocalParams.get(thisOffset + i);
                    Struct actParType = currentActParTypes.get(i);

                    if (!assignableTo(actParType, formPar.getType())) {
                        report_error("-- neslaganje stvarnih i formalnih parametara u funkciji " + desigObj.getName() + " na liniji " + functionCall.getLine());
                        break;
                    }
                }
            }

            functionCall.struct = desigObj.getType();
        }
    }

    public void visit(PreActParsDummy preActParsDummy) {
        currentActParTypesStack.push(new ArrayList<>());
    }

    public void visit(Statement_Break statement_break) {
        if (!inDoWhileLoop) {
            report_error("-- break iskaz van do-while petlje na liniji " + statement_break.getLine());
        }
    }

    public void visit(Statement_Cont statement_cont) {
        if (!inDoWhileLoop) {
            report_error("-- continue iskaz van do-while petlje na liniji " + statement_cont.getLine());
        }
    }

    public void visit(Statement_Read statement_read) {
        Obj desigObj = statement_read.getDesignator().obj;
        int kind = desigObj.getKind();

        if (kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld) {
            report_error("-- parametar read funkcije nije promenljiva na liniji " + statement_read.getLine());
        } else if (!desigObj.getType().equals(Tab.intType) && !desigObj.getType().equals(Tab.charType) && !desigObj.getType().equals(TabExt.boolType)) {
            report_error("-- parametar read funkcije je nepravilnog tipa na liniji " + statement_read.getLine());
        }
    }

    public void visit(Statement_Print statement_print) {
        Struct type = statement_print.getExpr().struct;

        if (!type.equals(Tab.intType) && !type.equals(Tab.charType) && !type.equals(TabExt.boolType)) {
            report_error("-- parametar print funkcije je nepravilnog tipa na liniji " + statement_print.getLine());
        }
    }

    public void visit(Statement_Return statement_return) {
        if (returnFound) {
            report_error("-- dupli return na liniji " + statement_return.getLine());
        } else if (currentMethod == null) {
            report_error("-- return iskaz van metode na liniji " + statement_return.getLine());
        } else {
            Struct returnType = statement_return.getOptExpr().struct;
            if (!currentMethod.getType().equals(returnType)) {
                report_error("-- return tip nije ispravan na liniji " + statement_return.getLine());
            }
            returnFound = true;
        }
    }

    public void visit(OptExpr_NoEps optExpr_noEps) {
        optExpr_noEps.struct = optExpr_noEps.getExpr().struct;
    }

    public void visit(OptExpr_Eps optExpr_eps) {
        optExpr_eps.struct = Tab.noType;
    }

    public void visit(IfCondition_Cond ifCondition_cond) {
        if (!ifCondition_cond.getCondition().struct.equals(TabExt.boolType)) {
            report_error("-- if condition nije boolean tip na liniji " + ifCondition_cond.getLine());
        }
    }

    public void visit(Statement_DoWhile statement_doWhile) {
        if (!statement_doWhile.getCondition().struct.equals(TabExt.boolType)) {
            report_error("-- if condition nije boolean tip na liniji " + statement_doWhile.getLine());
        }

        inDoWhileLoop = false;
    }

    public void visit(DoWhileDummy doWhileDummy) {
        inDoWhileLoop = true;
    }

    public void visit(ActPar actPar) {
        currentActParTypesStack.peek().add(actPar.getExpr().struct);
    }

    public void visit(Condition_Chain condition_chain) {
        condition_chain.struct = condition_chain.getCondition().struct;
    }

    public void visit(Condition_Item condition_item) {
        condition_item.struct = condition_item.getCondTerm().struct;
    }

    public void visit(CondTerm_Chain condTerm_chain) {
        condTerm_chain.struct = condTerm_chain.getCondTerm().struct;
    }

    public void visit(CondTerm_Item condTerm_item) {
        condTerm_item.struct = condTerm_item.getCondFact().struct;
    }

    public void visit(CondFact_Expr condFact_expr) {
        condFact_expr.struct = condFact_expr.getExpr().struct;
    }

    public void visit(CondFact_Relop condFact_relop) {
        if (!condFact_relop.getExpr().struct.compatibleWith(condFact_relop.getExpr1().struct)) {
            report_error("-- tipovi cond_fact_relop nisu kompatibilni na liniji " + condFact_relop.getLine());
        } else {
            if (((condFact_relop.getExpr().struct.getKind() == Struct.Class
                        || condFact_relop.getExpr().struct.getKind() == Struct.Array)
                    || (condFact_relop.getExpr1().struct.getKind() == Struct.Class
                        || condFact_relop.getExpr1().struct.getKind() == Struct.Array))
                && !(condFact_relop.getRelop() instanceof Relop_Eq
                    || condFact_relop.getRelop() instanceof Relop_Neq)) {
                report_error("-- cond_fact_relop sa klasama ili nizovima koriste relop drugaciji od == i != na liniji " + condFact_relop.getLine());
            }
        }

        condFact_relop.struct = TabExt.boolType;
    }

    public void visit(Expr_Term expr_term) {
        expr_term.struct = expr_term.getTerm().struct;
    }

    public void visit(Expr_SubTerm expr_subTerm) {
        if (!expr_subTerm.getTerm().struct.equals(Tab.intType)) {
            report_error("-- negira se tip koji nije int na liniji " + expr_subTerm.getLine());
        }

        expr_subTerm.struct = expr_subTerm.getTerm().struct;
    }

    public void visit(Expr_AddopTerm expr_addopTerm) {
        if (!expr_addopTerm.getExpr().struct.equals(Tab.intType) || !expr_addopTerm.getTerm().struct.equals(Tab.intType)) {
            report_error("-- sabirci nisu tipa int na liniji " + expr_addopTerm.getAddop().getLine());
        }

        if (!expr_addopTerm.getExpr().struct.compatibleWith(expr_addopTerm.getTerm().struct)) {
            report_error("-- sabirci nisu kompatibilni na liniji " + expr_addopTerm.getAddop().getLine());
        }

        expr_addopTerm.struct = expr_addopTerm.getExpr().struct;
    }

    public void visit(Term_Item term_item) {
        term_item.struct = term_item.getFactor().struct;
    }

    public void visit(Term_Chain term_chain) {
        if (!term_chain.getTerm().struct.equals(Tab.intType) || !term_chain.getFactor().struct.equals(Tab.intType)) {
            report_error("-- term_chain clanovi nisu tipa int na liniji " + term_chain.getMulop().getLine());
        }

        term_chain.struct = term_chain.getTerm().struct;
    }

    public void visit(Factor_Desig factor_desig) {
        factor_desig.struct = factor_desig.getDesignator().obj.getType();
    }

    public void visit(Factor_DesigFunc factor_desigFunc) {
        factor_desigFunc.struct = factor_desigFunc.getFunctionCall().struct;
    }

    public void visit(Factor_Num factor_num) {
        factor_num.struct = Tab.intType;
    }

    public void visit(Factor_Char factor_char) {
        factor_char.struct = Tab.charType;
    }

    public void visit(Factor_Bool factor_bool) {
        factor_bool.struct = TabExt.boolType;
    }

    public void visit(Factor_New factor_new) {
        if (factor_new.getType().struct.getKind() != Struct.Class || factor_new.getType().struct.equals(Tab.nullType)) {
            report_error("-- tip u new iskazu ne predstavlja klasu na liniji " + factor_new.getLine());
        }

        factor_new.struct = factor_new.getType().struct;
    }

    public void visit(Factor_NewArr factor_newArr) {
        if (!factor_newArr.getExpr().struct.equals(Tab.intType)) {
            report_error("-- tip expr u new Type[expr] izrazu nije int na liniji " + factor_newArr.getLine());
        }

        factor_newArr.struct = new Struct(Struct.Array, factor_newArr.getType().struct);
    }

    public void visit(Factor_Paren factor_paren) {
        factor_paren.struct = factor_paren.getExpr().struct;
    }

    public void visit(Designator designator) {
        Obj obj = Tab.find(designator.getI1());
        Collection<Obj> possibleNextList = obj.getType().getMembers().symbols();

        if (obj == Tab.noObj) {
            report_error("-- nije pronadjen simbol " + designator.getI1() + " na liniji " + designator.getLine());
        } else {
            if (obj.getName().equals("this") && currentClass != null && currentMethod != null) {
                // specijalni slucaj, objekti klase su jos uvek (currentScope - 1) (scope metode je trenutni)
                possibleNextList = Tab.currentScope().getOuter().values();
            }

            DesignatorMore designatorMore = designator.getDesignatorMore();
            while (designatorMore instanceof DesignatorMore_NoEps) {
                DesignatorMore_NoEps designatorMore_noEps = (DesignatorMore_NoEps) designatorMore;
                DesignatorMoreChoice designatorMoreChoice = designatorMore_noEps.getDesignatorMoreChoice();

                if (designatorMoreChoice instanceof DesignatorMoreChoice_Dot) {
                    DesignatorMoreChoice_Dot designatorMoreChoice_dot = (DesignatorMoreChoice_Dot) designatorMoreChoice;
                    obj = findByName(designatorMoreChoice_dot.getI1(), possibleNextList);

                    if (obj == null) {
                        report_error("-- nije pronadjen simbol " + designatorMoreChoice_dot.getI1() + " na liniji " + designatorMoreChoice_dot.getLine());
                        designator.obj = Tab.noObj;
                        break;
                    }
                } else {
                    DesignatorMoreChoice_Array designatorMoreChoice_array = (DesignatorMoreChoice_Array) designatorMoreChoice;

                    if (obj.getType().getKind() != Struct.Array) {
                        report_error("-- ime " + obj.getName() + " nije niz na liniji " + designatorMore.getLine());
                        designator.obj = Tab.noObj;
                        break;
                    } else if (!designatorMoreChoice_array.getExpr().struct.equals(Tab.intType)) {
                        report_error("-- indeks niza nije tipa int na liniji " + designatorMoreChoice_array.getLine());
                    }

                    obj = new Obj(Obj.Elem, obj.getName(), obj.getType().getElemType());
                }

                designatorMore = designatorMore_noEps.getDesignatorMore();
                possibleNextList = obj.getType().getMembers().symbols();
            }
        }

        if (obj != null) {
            designator.obj = obj;
        } else {
            designator.obj = Tab.noObj;
        }
    }
}
