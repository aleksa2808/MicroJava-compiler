package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.visitors.SymbolTableVisitor;

import java.io.*;

public class MJParserTest {

    static {
        DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
        Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
    }

    public static void main(String[] args) throws Exception {
        Logger log = Logger.getLogger(MJParserTest.class);
//        if (args.length < 2) {
//            log.error("Not enough arguments supplied! Usage: MJParser <source-file> <obj-file> ");
//            return;
//        }

        File sourceCode = new File("test/Emil/test1.mj");//args[0]);
        if (!sourceCode.exists()) {
            System.out.println("Source file [" + sourceCode.getAbsolutePath() + "] not found!");
            return;
        }

        System.out.println("Compiling source file: " + sourceCode.getAbsolutePath());

        try (BufferedReader br = new BufferedReader(new FileReader(sourceCode))) {
            Yylex lexer = new Yylex(br);
            MJParser p = new MJParser(lexer);
            Symbol s = p.parse();  //pocetak parsiranja

            if (!p.errorDetected) {
                SyntaxNode prog = (SyntaxNode)(s.value);

                System.out.println("=====================SEMANTICKA OBRADA=========================");

                TabExt.init(); // Universe scope
                SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
                prog.traverseBottomUp(semanticAnalyzer);

                semanticAnalyzer.printInfo();

                if (semanticAnalyzer.passed()) {
                    Code.dataSize = semanticAnalyzer.nVars;
                    CodeGenerator codeGenerator = new CodeGenerator();
                    prog.traverseBottomUp(codeGenerator);
                    Code.mainPc = codeGenerator.getMainPc();

                    tsdump();

                    File objFile = new File("test/program.obj");//args[1]);
                    if (objFile.exists())
                        objFile.delete();

                    int nemojsmaratiintellidzeju;

                    System.out.println();
                    System.out.println("Generating bytecode file: " + objFile.getAbsolutePath());
                    Code.write(new FileOutputStream(objFile));
                    System.out.println("Parsiranje uspesno zavrseno!");
                }
                else {
                    // nepotpuno, fale adrese metoda etc.
                    tsdump();
                    System.out.println();
                    System.out.println("Parsiranje NIJE uspesno zavrseno!");
                }

            }
        }
    }

    public static void tsdump() {
        SymbolTableVisitor stv = new SimpleSymbolTableVisitor(false);
        Tab.dump(stv);
    }
}
