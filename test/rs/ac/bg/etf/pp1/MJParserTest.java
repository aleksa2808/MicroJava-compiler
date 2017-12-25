package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.symboltable.Tab;

import java.io.*;

public class MJParserTest {

    static {
        DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
        Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
    }

    public static void main(String[] args) throws Exception {

        Logger log = Logger.getLogger(MJParserTest.class);

        Reader br = null;
        try {
            File sourceCode = new File("test/Dragana/sveobuhvatni.mj");
            log.info("Compiling source file: " + sourceCode.getAbsolutePath());

            br = new BufferedReader(new FileReader(sourceCode));
            Yylex lexer = new Yylex(br);

            MJParser p = new MJParser(lexer);
            Symbol s = p.parse();  //pocetak parsiranja

            if (!p.errorDetected) {
                Program prog = (Program) (s.value);

                // ispis sintaksnog stabla
                //System.out.println(prog.toString(""));
                System.out.println("===================================");

                // ispis prepoznatih programskih konstrukcija
                SemanticAnalyzer v = new SemanticAnalyzer();
                prog.traverseBottomUp(v);

                log.info("Print calls = " + p.printCallCount);

                Tab.dump();

                if (!v.errorDetected) {
                    log.info("Parsiranje uspesno zavrseno!");
                } else {
                    log.error("Parsiranje NIJE uspesno zavrseno!");
                }
            }
        }
        finally {
            if (br != null) try { br.close(); } catch (IOException e1) { log.error(e1.getMessage(), e1); }
        }

    }

}
