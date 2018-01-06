package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.*;
import rs.etf.pp1.symboltable.visitors.SymbolTableVisitor;

import java.util.*;

public class SemanticAnalyzer extends VisitorAdaptor {
    public int nVars;

    private int classCnt = 0;
    private int methodCnt = 0;
    private int globalVarCnt = 0;
    private int globalConstCnt = 0;
    private int globalArrayCnt = 0;
    private int mainLocalVarCnt = 0;
    private int mainStatementCnt = 0;
    private int mainFuncCallCnt = 0;

    private Obj currentTypeObj = Tab.noObj;
    private Obj currentClass = Tab.noObj;
    private Obj currentMethod = Tab.noObj;
    private ArrayList<Obj> currentMethFormPars = null;
    private Stack<ArrayList<Struct>> currentActParTypesStack;

    private Map<Struct, Struct> classInheritanceMap;
    private Map<Obj, Obj> methodOriginMap;

    private boolean returnFound = false;
    private int doWhileDepth = 0;

    private boolean mainFound = false;
    private boolean errorDetected = false;

    SemanticAnalyzer() {
        super();
        currentActParTypesStack = new Stack<>();
        classInheritanceMap = new HashMap<>();
        methodOriginMap = new HashMap<>();
    }

    public boolean passed() {
        if (!mainFound) {
            report_info("\nU programu nije pronadjena metoda main!");
        }

        return mainFound && !errorDetected;
    }

    public void printInfo() {
        System.out.println("=====================SINTAKSNA ANALIZA=========================");
        System.out.println(classCnt + "\t classes");
        System.out.println(methodCnt + "\t methods in the program");
        System.out.println(globalVarCnt + "\t global variables");
        System.out.println(globalConstCnt + "\t global constants");
        System.out.println(globalArrayCnt + "\t global arrays");
        System.out.println(mainLocalVarCnt + "\t local variables in main");
        System.out.println(mainStatementCnt + "\t statements in main");
        System.out.println(mainFuncCallCnt + "\t function calls in main");
    }

//********* Helper functions *****************
    private void report_error(String message) {
        errorDetected = true;
        System.out.println(">> " + message);
    }

    private void report_info(String message) {
        System.out.println(message);
    }

    private void report_obj(Obj obj, int line) {
        SymbolTableVisitor symTableVisitor = new SimpleSymbolTableVisitor(true);

        if (    // NIVO A
                obj.getKind() == Obj.Con
                        || obj.getKind() == Obj.Var && obj.getLevel() == 0
                        || obj.getKind() == Obj.Var && obj.getLevel() > 0
                        // NIVO B
                        || obj.getKind() == Obj.Meth && obj.getLevel() == 0
                        || obj.getKind() == Obj.Elem
                        || obj.getKind() == Obj.Var && obj.getFpPos() > 0
                        // NIVO C
                        || obj.getKind() == Obj.Type && obj.getType().getKind() == Struct.Class
                        || obj.getKind() == Obj.Fld
                        || obj.getKind() == Obj.Meth && obj.getLevel() > 0) {
            symTableVisitor.visitObjNode(obj);
            report_info("Pretraga na " + line + "(" + obj.getName() + "), nadjeno " + symTableVisitor.getOutput());
        }
    }

    private Obj varInsert(String varName, Struct varType, int varLine) {
        if (Tab.currentScope().findSymbol(varName) == null) {
            int kind;
            if (!currentClass.equals(Tab.noObj) && currentMethod.equals(Tab.noObj)) {
                kind = Obj.Fld;
            } else {
                kind = Obj.Var;
            }

            return Tab.insert(kind, varName, varType);
        } else {
            report_error("Greska na " + varLine + "(" + varName + ") vec deklarisano");
        }

        return null;
    }

    private void formParInsert(String formParName, Struct formParType, int formParLine) {
        if (findByName(formParName, currentMethFormPars).equals(Tab.noObj)) {
            Obj formParObj = new Obj(Obj.Var, formParName, formParType);
            currentMethFormPars.add(formParObj);
        } else {
            report_error("Greska na " + formParLine + "(" + formParName + ") vec deklarisano");
        }
    }

    private boolean canOverride(MethodSignature methodSignature, Obj superClassMeth) {
        // provera return tipa
        if (!methodSignature.getVoidableType().obj.getType().equals(superClassMeth.getType())) {
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

        return Tab.noObj;
    }

    private boolean assignableTo(Struct srcType, Struct dstType) {
        if (srcType.assignableTo(dstType)) {
            return true;
        }

        // provera nasledjenosti
        if (srcType.getKind() == Struct.Class && dstType.getKind() == Struct.Class) {
            Struct superType = classInheritanceMap.get(srcType);

            while (superType != null) {
                if (superType.equals(dstType)) {
                    return true;
                }
                superType = classInheritanceMap.get(superType);
            }
        }

        return false;
    }

//********* Visitor functions *****************


    public void visit(Program program) {
        nVars = Tab.currentScope().getnVars();
        Tab.chainLocalSymbols(program.getProgName().obj);
        Tab.closeScope();
	}

    public void visit(ProgName progName) {
        progName.obj = Tab.insert(Obj.Prog, progName.getI1(), Tab.noType);
        Tab.openScope();
    }

    public void visit(ConstDecl constDecl) {
        currentTypeObj = Tab.noObj;
	}

    public void visit(ConstValAssign constValAssign) {
        Obj valObj = constValAssign.getConstVal().obj;
        if (valObj.getType().equals(currentTypeObj.getType())) {
            if (Tab.currentScope().findSymbol(constValAssign.getI1()) == null) {
                Obj temp = Tab.insert(valObj.getKind(), constValAssign.getI1(), valObj.getType());
                temp.setAdr(valObj.getAdr());

                if (temp.getLevel() == 0) {
                    globalConstCnt++;
                }
            } else {
                report_error("Greska na " + constValAssign.getLine() + "(" + constValAssign.getI1() + ") vec deklarisano");
            }
        } else {
            report_error("Greska na " + constValAssign.getLine() + ": nekompatibilni tipovi podataka");
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
        currentTypeObj = Tab.noObj;
    }

    public void visit(GlobalVarDecl globalVarDecl) {
        currentTypeObj = Tab.noObj;
    }

    public void visit(FieldVarDecl fieldVarDecl) {
        currentTypeObj = Tab.noObj;
    }

    public void visit(FieldVarDeclLbrace fieldVarDeclLbrace) {
        currentTypeObj = Tab.noObj;
    }

    public void visit(TypeDummy typeDummy) {
        currentTypeObj = typeDummy.getType().obj;
    }

    public void visit(VarDeclDefine_Single varDeclDefine_single) {
        Obj temp = varInsert(varDeclDefine_single.getI1(), currentTypeObj.getType(), varDeclDefine_single.getLine());
        if (temp != null && temp.getKind() == Obj.Var && temp.getLevel() == 0) {
            if (temp.getLevel() == 0) {
                globalVarCnt++;
            } else if ("main".equalsIgnoreCase(currentMethod.getName())) {
                mainLocalVarCnt++;
            }
        }
    }

    public void visit(VarDeclDefine_Array varDeclDefine_array) {
        Obj temp = varInsert(varDeclDefine_array.getI1(), new Struct(Struct.Array, currentTypeObj.getType()), varDeclDefine_array.getLine());
        if (temp != null && temp.getKind() == Obj.Var) {
            if (temp.getLevel() == 0) {
                globalArrayCnt++;
            } else if ("main".equalsIgnoreCase(currentMethod.getName())) {
                mainLocalVarCnt++;
            }
        }
    }

    public void visit(ClassDecl classDecl) {
        classCnt++;

        Tab.chainLocalSymbols(currentClass.getType());
        Tab.closeScope();

        currentClass = Tab.noObj;
    }

    public void visit(ClassName className) {
        currentClass = new Obj(Obj.Type, "classErrorDummy", new Struct(Struct.Class));

        if (Tab.currentScope().findSymbol(className.getI1()) == null) {
            currentClass = Tab.insert(Obj.Type, className.getI1(), new Struct(Struct.Class));
            Tab.openScope();
            Tab.insert(Obj.Fld, "vftPtr", Tab.noType);
        } else {
            report_error("Greska na " + className.getLine() + "(" + className.getI1() + ") vec deklarisano");

            Tab.openScope();
        }

        className.obj = currentClass;
    }

    public void visit(OptExtendsIndicator_NoEps optExtendsIndicator_noEps) {
        Obj classSuperTypeObj = optExtendsIndicator_noEps.getType().obj;
        if (classSuperTypeObj.getType().getKind() == Struct.Class && !classSuperTypeObj.getType().equals(Tab.nullType)) {
            classInheritanceMap.put(currentClass.getType(), classSuperTypeObj.getType());

            Collection<Obj> superClassMembers = classSuperTypeObj.getType().getMembers().symbols();
            for (Obj member : superClassMembers) {
                if (member.getKind() == Obj.Fld) {
                    Tab.insert(Obj.Fld, member.getName(), member.getType());
                }
            }
        } else {
            report_error("Greska na " + optExtendsIndicator_noEps.getLine() + ": extends tip ne ukazuje na unutrasnju klasu");
        }
    }

    public void visit(ClassDeclExtHelper classDeclExtHelper) {
        if (classInheritanceMap.containsKey(currentClass.getType())) {
            Collection<Obj> superClassMembers = classInheritanceMap.get(currentClass.getType()).getMembers().symbols();
            for (Obj member : superClassMembers) {
                if (member.getKind() == Obj.Meth) {
                    Obj methObj = Tab.insert(Obj.Meth, member.getName(), member.getType());
                    methObj.setLevel(member.getLevel());

                    Collection<Obj> methLocals = member.getLocalSymbols();
                    Tab.openScope();

                    for (Obj methLocal : methLocals) {
                        Obj temp = Tab.insert(methLocal.getKind(), methLocal.getName(), methLocal.getType());
                        temp.setLevel(methLocal.getLevel());
                    }

                    Tab.chainLocalSymbols(methObj);
                    Tab.closeScope();

                    TabExt.methodScopeMap.put(methObj, currentClass);

                    Obj originMethod = methodOriginMap.getOrDefault(member, member);
                    TabExt.methodAdrFixMap.get(originMethod).add(methObj);
                    methodOriginMap.put(methObj, originMethod);
                }
            }
        }
    }

    public void visit(MethodDecl methodDecl) {
        methodCnt++;

        if (!returnFound && !currentMethod.getType().equals(Tab.noType) && !"main".equalsIgnoreCase(currentMethod.getName())) {
            report_error("Greska na " + methodDecl.getLine() + "(" + currentMethod.getName() + ") nema return iskaz");
        }

        Tab.chainLocalSymbols(currentMethod);
        Tab.closeScope();

        currentMethod = Tab.noObj;
        returnFound = false;
    }

    public void visit(MethodSignature methodSignature) {
        currentMethod = Tab.noObj;
        // istoimeni simbol iz trenutnog scope-a
        Obj sym = Tab.currentScope().findSymbol(methodSignature.getI2());
        if (sym == null) {
            // ukoliko ga nema -> pronasli smo novu metodu
            currentMethod = Tab.insert(Obj.Meth, methodSignature.getI2(), methodSignature.getVoidableType().obj.getType());
            Tab.openScope();

            if (!currentClass.equals(Tab.noObj)) {
                Tab.insert(Obj.Var, "this", currentClass.getType());
                TabExt.methodScopeMap.put(currentMethod, currentClass);
                TabExt.methodAdrFixMap.put(currentMethod, new ArrayList<>());
            }

            if (currentClass.equals(Tab.noObj) && "main".equalsIgnoreCase(currentMethod.getName())) {
                mainFound = true;

                if (!currentMethod.getType().equals(Tab.noType)) {
                    report_error("Greska na " + methodSignature.getLine() + ": return vrednost metode main mora biti void");
                }

                if (!currentMethFormPars.isEmpty()) {
                    report_error("Greska na " + methodSignature.getLine() + ": metoda main ne sme imati formalne parametre");
                }
            }

            int fpCnt = 1;
            for (Obj methFormPar : currentMethFormPars) {
                Obj temp = Tab.insert(methFormPar.getKind(), methFormPar.getName(), methFormPar.getType());
                temp.setFpPos(fpCnt++);
            }

            currentMethod.setLevel(currentMethFormPars.size());
        } else if (sym.getKind() == Obj.Meth && !currentClass.equals(Tab.noObj) && classInheritanceMap.containsKey(currentClass.getType())){
            // ukoliko ga ima, ono predstavlja metodu, a trenutno obradjujemo nasledjenu klasu
            // proveravamo da li je 'this' parametar metode vec promenjen (vec overridovana klasa)
            Obj methThisObj = sym.getLocalSymbols().iterator().next();
            if (methThisObj.getType().equals(classInheritanceMap.get(currentClass.getType()))) {
                // ako je 'this' tipa superklase, to znaci da smo naisli na prvi moguci override
                // ostaje nam da proverimo da li nova metoda ima kompatibilan potpis sa virtuelnom
                if (canOverride(methodSignature, sym)) {
                    currentMethod = sym;
                    Tab.openScope();
                    Tab.insert(Obj.Var, "this", currentClass.getType());

                    int fpCnt = 1;
                    for (Obj methFormPar : currentMethFormPars) {
                        Obj temp = Tab.insert(methFormPar.getKind(), methFormPar.getName(), methFormPar.getType());
                        temp.setFpPos(fpCnt++);
                    }

                    Obj originMethod = methodOriginMap.get(currentMethod);
                    TabExt.methodAdrFixMap.get(originMethod).remove(currentMethod);
                    methodOriginMap.remove(currentMethod);
                    TabExt.methodAdrFixMap.put(currentMethod, new ArrayList<>());
                } else {
                    report_error("Greska na " + methodSignature.getLine() + "(" + methodSignature.getI2() + ") nema kompatibilan potpis za override");
                }
            } else {
                report_error("Greska na " + methodSignature.getLine() + "(" + methodSignature.getI2() + ") redeklarisano");
            }
        } else {
            report_error("Greska na " + methodSignature.getLine() + "(" + methodSignature.getI2() + ") vec deklarisano");
        }

        if (currentMethod.equals(Tab.noObj)) {
            currentMethod = new Obj(Obj.Meth, "methodErrorDummy", methodSignature.getVoidableType().obj.getType());
            Tab.openScope();

            if (!currentClass.equals(Tab.noObj)) {
                Tab.insert(Obj.Var, "this", currentClass.getType());
                TabExt.methodScopeMap.put(currentMethod, currentClass);
            }

            int fpCnt = 1;
            for (Obj methFormPar : currentMethFormPars) {
                Obj temp = Tab.insert(methFormPar.getKind(), methFormPar.getName(), methFormPar.getType());
                temp.setFpPos(fpCnt);
            }

            currentMethod.setLevel(currentMethFormPars.size());
        }

        methodSignature.obj = currentMethod;
        currentMethFormPars = null;
    }

    public void visit(PreFormParsDummy preFormParsDummy) {
        currentMethFormPars = new ArrayList<>();
    }

    public void visit(VoidableType_Type voidableType_type) {
        voidableType_type.obj = voidableType_type.getType().obj;
    }

    public void visit(VoidableType_Void voidableType_void) {
        voidableType_void.obj = Tab.noObj;
    }

    public void visit(StatementList_NoEps statementList_noEps) {
        if ("main".equalsIgnoreCase(currentMethod.getName())) {
            mainStatementCnt++;
        }
    }

    public void visit(FormParDecl_Single formParDecl_single) {
        formParInsert(formParDecl_single.getI2(), formParDecl_single.getType().obj.getType(), formParDecl_single.getLine());
    }

    public void visit(FormParDecl_Array formParDecl_array) {
        formParInsert(formParDecl_array.getI2(), new Struct(Struct.Array, formParDecl_array.getType().obj.getType()), formParDecl_array.getLine());
    }

    public void visit(Type type) {
        String typeName = type.getI1();
        type.obj = Tab.find(typeName);
        if (type.obj == Tab.noObj)
        {
            report_error("Greska na " + type.getLine() + "(" + typeName + ") nije nadjeno");
        }
        else if (Obj.Type != type.obj.getKind()) {
            report_error("Greska na " + type.getLine() + "(" + typeName + ") ne predstavlja tip");
            type.obj = Tab.noObj;
        }
    }

    public void visit(DesignatorStatement_Assign designatorStatement_assign) {
        Obj desigObj = designatorStatement_assign.getDesignator().obj;
        int kind = desigObj.getKind();

        if (kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld) {
            report_error("Greska na " + designatorStatement_assign.getLine() + ": neispravna leva strana dodele");
        } else if (!assignableTo(designatorStatement_assign.getAssignment().struct, desigObj.getType())) {
            report_error("Greska na " + designatorStatement_assign.getLine() + ": nekompatibilni tipovi u dodeli");
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
            report_error("Greska na " + designatorStatement_inc.getLine() + "(" + desigObj.getName() + ") nije promenljiva");
        } else if (!desigObj.getType().equals(Tab.intType)) {
            report_error("Greska na " + designatorStatement_inc.getLine() + "(" + desigObj.getName() + ") nije int");
        }
    }

    public void visit(DesignatorStatement_Dec designatorStatement_dec) {
        Obj desigObj = designatorStatement_dec.getDesignator().obj;
        int kind = desigObj.getKind();

        if (kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld) {
            report_error("Greska na " + designatorStatement_dec.getLine() + "(" + desigObj.getName() + ") nije promenljiva");
        } else if (!desigObj.getType().equals(Tab.intType)) {
            report_error("Greska na " + designatorStatement_dec.getLine() + "(" + desigObj.getName() + ") nije int");
        }
    }

    public void visit(FunctionCall functionCall) {
        if ("main".equalsIgnoreCase(currentMethod.getName())) {
            mainFuncCallCnt++;
        }

        Obj desigObj = functionCall.getDesignator().obj;
        ArrayList<Struct> currentActParTypes = currentActParTypesStack.pop();

        if (desigObj.getKind() != Obj.Meth) {
            report_error("Greska na " + functionCall.getLine() + "(" + desigObj.getName() + ") nije funkcija");

            functionCall.struct = Tab.noType;
        } else {
            int numOfFPs = desigObj.getLevel();
            if (numOfFPs != currentActParTypes.size()) {
                report_error("Greska na " + functionCall.getLine() + ": neispravan broj parametara funkcije");
            } else {
                ArrayList<Obj> methLocalParams = new ArrayList<>(desigObj.getLocalSymbols());

                if ("len".equals(desigObj.getName()) && !TabExt.methodScopeMap.containsKey(desigObj)) {
                    Struct arr = currentActParTypes.get(0);
                    if (arr.getKind() != Struct.Array || (!arr.getElemType().equals(Tab.intType) && !arr.getElemType().equals(Tab.charType))) {
                        report_error("Greska na " + functionCall.getLine() + ": neispravni argumenti metode len(arr)");
                    }
                } else {
                    int thisOffset = 0;
                    if (TabExt.methodScopeMap.containsKey(desigObj)) {
                        thisOffset = 1;
                    }

                    for (int i = 0; i < numOfFPs; i++) {
                        Obj formPar = methLocalParams.get(thisOffset + i);
                        Struct actParType = currentActParTypes.get(i);

                        if (!assignableTo(actParType, formPar.getType())) {
                            report_error("Greska na " + functionCall.getLine() + "(" + desigObj.getName() + ") poziv nema ispravne parametre");
                            break;
                        }
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
        if (doWhileDepth == 0) {
            report_error("Greska na " + statement_break.getLine() + ": break iskaz van petlje");
        }
    }

    public void visit(Statement_Cont statement_cont) {
        if (doWhileDepth == 0) {
            report_error("Greska na " + statement_cont.getLine() + ": continue iskaz van petlje");
        }
    }

    public void visit(Statement_Read statement_read) {
        Obj desigObj = statement_read.getDesignator().obj;
        int kind = desigObj.getKind();

        if (kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld) {
            report_error("Greska na " + statement_read.getLine() + ": parametar read funkcije nije promenljiva");
        } else if (!desigObj.getType().equals(Tab.intType) && !desigObj.getType().equals(Tab.charType) && !desigObj.getType().equals(TabExt.boolType)) {
            report_error("Greska na " + statement_read.getLine() + ": parametar read funkcije je nepravilnog tipa");
        }
    }

    public void visit(Statement_Print statement_print) {
        Struct type = statement_print.getExpr().struct;

        if (!type.equals(Tab.intType) && !type.equals(Tab.charType) && !type.equals(TabExt.boolType)) {
            report_error("Greska na " + statement_print.getLine() + ": parametar print funkcije je nepravilnog tipa");
        }
    }

    public void visit(Statement_Return statement_return) {
        if (returnFound) {
            report_error("Greska na " + statement_return.getLine() + ": dupli return iskaz");
        } else if (currentMethod.equals(Tab.noObj)) {
            report_error("Greska na " + statement_return.getLine() + ": return iskaz van metode");
        } else {
            Struct returnType = statement_return.getOptExpr().struct;
            if (!currentMethod.getType().equals(returnType)) {
                report_error("Greska na " + statement_return.getLine() + ": tip return iskaza nije ispravan");
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

    public void visit(IfCondParen_Cond ifCondParen_cond) {
        if (!ifCondParen_cond.getCondition().struct.equals(TabExt.boolType)) {
            report_error("Greska na " + ifCondParen_cond.getLine() + ": if uslov nije tipa bool");
        }
    }

    public void visit(Statement_DoWhile statement_doWhile) {
        if (!statement_doWhile.getCondition().struct.equals(TabExt.boolType)) {
            report_error("Greska na " + statement_doWhile.getLine() + ": while uslov nije tipa bool");
        }

        doWhileDepth--;
    }

    public void visit(DoDummy doDummy) {
        doWhileDepth++;
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
            report_error("Greska na " + condFact_relop.getLine() + ": tipovi relacionog izraza nisu kompatibilni");
        } else {
            if (((condFact_relop.getExpr().struct.getKind() == Struct.Class
                        || condFact_relop.getExpr().struct.getKind() == Struct.Array)
                    || (condFact_relop.getExpr1().struct.getKind() == Struct.Class
                        || condFact_relop.getExpr1().struct.getKind() == Struct.Array))
                && !(condFact_relop.getRelop() instanceof Relop_Eq
                    || condFact_relop.getRelop() instanceof Relop_Neq)) {
                report_error("Greska na " + condFact_relop.getLine() + ": relacioni izraz sa referentnim tipovima moze koristiti samo '==' i '!=' operatore");
            }
        }

        condFact_relop.struct = TabExt.boolType;
    }

    public void visit(Expr_Term expr_term) {
        expr_term.struct = expr_term.getTerm().struct;
    }

    public void visit(Expr_Neg expr_neg) {
        if (!expr_neg.getTerm().struct.equals(Tab.intType)) {
            report_error("Greska na " + expr_neg.getLine() + ": negira se tip koji nije int");
        }

        expr_neg.struct = expr_neg.getTerm().struct;
    }

    public void visit(Expr_Addop expr_addop) {
        if (!expr_addop.getExpr().struct.equals(Tab.intType) || !expr_addop.getTerm().struct.equals(Tab.intType)) {
            report_error("Greska na " + expr_addop.getLine() + ": clanovi izraza nisu tipa int");
        }

        if (!expr_addop.getExpr().struct.compatibleWith(expr_addop.getTerm().struct)) {
            report_error("Greska na " + expr_addop.getLine() + ": clanovi izraza nisu kompatibilni");
        }

        expr_addop.struct = expr_addop.getExpr().struct;
    }

    public void visit(Term_Factor term_factor) {
        term_factor.struct = term_factor.getFactor().struct;
    }

    public void visit(Term_Mulop term_mulop) {
        if (!term_mulop.getTerm().struct.equals(Tab.intType) || !term_mulop.getFactor().struct.equals(Tab.intType)) {
            report_error("Greska na " + term_mulop.getLine() + ": clanovi izraza nisu tipa int");
        }

        term_mulop.struct = term_mulop.getTerm().struct;
    }

    public void visit(Factor_Desig factor_desig) {
        Obj desigObj = factor_desig.getDesignator().obj;
        int kind = desigObj.getKind();

        // TODO: racvanje od spec, quadruple check
        if (kind != Obj.Con && kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld ) {
            report_error("Greska na " + factor_desig.getLine() + "(" + desigObj.getName() + ") nije promenljiva");
        }

        factor_desig.struct = factor_desig.getDesignator().obj.getType();
    }

    public void visit(Factor_DesigFunc factor_desigFunc) {
        factor_desigFunc.struct = factor_desigFunc.getFunctionCall().struct;
    }

    public void visit(Factor_ConstVal factor_constVal) {
        factor_constVal.struct = factor_constVal.getConstVal().obj.getType();
    }

    public void visit(Factor_New factor_new) {
        Obj typeObj = factor_new.getType().obj;
        if (typeObj.getType().getKind() != Struct.Class || typeObj.getType().equals(Tab.nullType)) {
            report_error("Greska na " + factor_new.getLine() + ": tip u new iskazu ne predstavlja klasu");
        } else {
            report_obj(factor_new.getType().obj, factor_new.getType().getLine());
        }

        factor_new.struct = factor_new.getType().obj.getType();
    }

    public void visit(Factor_NewArr factor_newArr) {
        if (!factor_newArr.getExpr().struct.equals(Tab.intType)) {
            report_error("Greska na " + factor_newArr.getLine() + ": tip izraza u new[] izrazu nije int");
        }

        factor_newArr.struct = new Struct(Struct.Array, factor_newArr.getType().obj.getType());
    }

    public void visit(Factor_Paren factor_paren) {
        factor_paren.struct = factor_paren.getExpr().struct;
    }

    public void visit(Designator_Field designator_field) {
        Obj desigObj = designator_field.getDesignator().obj;
        int kind = desigObj.getKind();
        designator_field.obj = Tab.noObj;

        // TODO: racvanje od spec, quadruple check
        if (kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld) {
            report_error("Greska na " + designator_field.getLine() + "(" + desigObj.getName() + ") nije objekat");
            // u slucaju Obj.Type moze se pristupati static poljima, mada to u ovom projektu nije implementirano
        } else if (desigObj.getType().getKind() != Struct.Class) {
            report_error("Greska na " + designator_field.getLine() + "(" + desigObj.getName() + ") nije klasnog tipa");
        } else {
            Collection<Obj> possibleNextList = desigObj.getType().getMembers().symbols();
            if (!currentClass.equals(Tab.noObj) && !currentMethod.equals(Tab.noObj) && desigObj.getType().equals(currentClass.getType())) {
                // specijalni slucaj, objekti klase su jos uvek (currentScope - 1) (scope metode je trenutni)
                possibleNextList = Tab.currentScope().getOuter().values();
            }

            designator_field.obj = findByName(designator_field.getI2(), possibleNextList);

            if (designator_field.obj.equals(Tab.noObj)) {
                report_error("Greska na " + designator_field.getLine() + "(" + designator_field.getI2() + ") nije nadjeno");
            } else {
                report_obj(designator_field.obj, designator_field.getLine());
            }
        }

        // TODO: dubl chek
        if (designator_field.obj.equals(Tab.noObj)) {
            designator_field.obj = new Obj(Tab.noObj.getKind(), designator_field.getI2(), Tab.noType);
        }
    }

    public void visit(Designator_Array designator_array) {
        Obj desigObj = designator_array.getDesignator().obj;
        designator_array.obj = Tab.noObj;

        if (desigObj.getType().getKind() != Struct.Array) {
            report_error("Greska na " + designator_array.getLine() + "(" + desigObj.getName() + ") nije niz");
        } else {
            designator_array.obj = new Obj(Obj.Elem, desigObj.getName() + "_elem", desigObj.getType().getElemType());

            if (!designator_array.getExpr().struct.equals(Tab.intType)) {
                report_error("Greska na " + designator_array.getLine() + ": indeks niza nije tipa int");
            } else {
                report_obj(designator_array.obj, designator_array.getLine());
            }
        }

        // TODO: dubl chek
        if (designator_array.obj.equals(Tab.noObj)) {
            designator_array.obj = new Obj(Tab.noObj.getKind(), desigObj.getName() + "_elem", Tab.noType);
        }
    }

    public void visit(Designator_Ident designator_ident) {
        designator_ident.obj = Tab.find(designator_ident.getI1());

        if (designator_ident.obj.equals(Tab.noObj)) {
            report_error("Greska na " + designator_ident.getLine() + "(" + designator_ident.getI1() + ") nije nadjeno");

            // TODO: dubl chek
            designator_ident.obj = new Obj(Tab.noObj.getKind(), designator_ident.getI1(), Tab.noType);
        } else {
            report_obj(designator_ident.obj, designator_ident.getLine());
        }
    }
}
