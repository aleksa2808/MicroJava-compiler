package rs.ac.bg.etf.pp1;

import java_cup.runtime.*;
import java.io.*;

import org.apache.log4j.Logger;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.visitors.SymbolTableVisitor;

class Compiler {
    public static void main(String args[]) throws Exception {
        if (args.length < 2) {
            System.out.println("Not enough arguments supplied! Usage: Compiler <source-file> <obj-file> ");
            return;
        }

        File sourceCode = new File("test/conditionals.mj");//args[0]);
        if (!sourceCode.exists()) {
            System.out.println("Source file [" + sourceCode.getAbsolutePath() + "] not found!");
            return;
        }

        System.out.println("Compiling source file: " + sourceCode.getAbsolutePath());

        FileReader r = new FileReader(sourceCode);
        Yylex scanner = new Yylex(r);
        MJParser p = new MJParser(scanner);
        Symbol sym = p.parse();

        if (!p.fatalErrorDetected) {
            Program prog = (Program)(sym.value);

//            // ispis sintaksnog stabla
//            System.out.println("================APSTRAKTNO SINTAKSNO STABLO====================");
//            System.out.println(prog.toString(""));

            System.out.println("=====================SEMANTICKA OBRADA=========================");

            TabExt.init(); // Universe scope

            // ispis prepoznatih programskih konstrukcija
            SemanticAnalyzer s = new SemanticAnalyzer();
            prog.traverseBottomUp(s);

            if (!p.errorDetected && s.passed()) {
                s.printInfo();

                Code.dataSize = s.nVars;
                CodeGenerator c = new CodeGenerator();
                prog.traverseBottomUp(c);
                Code.mainPc = c.getMainPc();

                tsdump();

                if (!Code.greska) {
                    File objFile = new File(args[1]);
                    if (objFile.exists())
                        objFile.delete();

                    System.out.println("Generating bytecode file: " + objFile.getAbsolutePath());
                    Code.write(new FileOutputStream(objFile));
                    System.out.println("Compilation successful!");
                } else {
                    System.out.println("Problems found during code generation!");
                }
            }
            else {
                // nepotpuno, fale adrese metoda etc.
                tsdump();
                System.out.println("Problems found during parsing!");
            }
        }
    }

    public static void tsdump() {
        SymbolTableVisitor stv = new SimpleSymbolTableVisitor(false);
        Tab.dump(stv);
    }
}
