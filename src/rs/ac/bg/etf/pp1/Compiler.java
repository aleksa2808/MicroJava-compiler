package rs.ac.bg.etf.pp1;

import java_cup.runtime.*;
import java.io.*;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;

class Compiler {
    public static void main(String args[]) throws Exception {
        FileReader r = new FileReader(args[0]);
        Yylex scanner = new Yylex(r);
        MJParser p = new MJParser(scanner);
        Symbol s = p.parse();
        Program prog = (Program)(s.value);

        // ispis sintaksnog stabla
        System.out.println(prog.toString(""));
        System.out.println("===================================");

        // ispis prepoznatih programskih konstrukcija
        SemanticAnalyzer v = new SemanticAnalyzer();
        prog.traverseBottomUp(v);

        tsdump();
    }

    public static void tsdump() {
        Tab.dump();
    }
}
