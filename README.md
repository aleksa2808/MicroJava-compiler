# MicroJava compiler

## Postavka zadatka

Cilj ovog projekta je izrada kompajlera za mikrojavu. Kompajler se generise iz 3 modula: 
- Generatora lexera, koji prihvata .flex fajl u kojem specificiramo tokene jezika; izvršavanjem LexerGenerator run configuracije, iz .flex fajla generiše se lexer,
-	Generatora parsera, koji se generiše iz .cup fajla i specificira ispravne izraze programskog jezika mikrojava,
-	Generatora koda, koji prima naredbe od parsera i ima za zadatak da generiše ispravan kod.

## Instrukcije za pokretanje rešenja

Nakon pozicioniranja u src folder:
1.	Generator lexera: `java -cp ../lib/JFlex.jar JFlex.Main -d rs\ac\bg\etf\pp1 ..\spec\mjlexer.flex`
2.	Generator parsera:  `java -cp ../lib/cup_v10k.jar java_cup.Main -destdir rs\ac\bg\etf\pp1 -parser MJParser -ast rs.ac.bg.etf.pp1.ast -buildtree ..\spec\mjparser.cup`
3.	Kompajliranje mikrojava  programa: `java -cp ..\MJCompiler.jar rs.ac.bg.etf.pp1.Compiler <.mj_file_src> <.obj_file_dst>`
4.	Disasm: `java -cp ..\lib\mj-runtime.jar rs.etf.pp1.mj.runtime.disasm <.obj_file_src>`
5.	Run/Debug: `java -cp ..\lib\mj-runtime.jar rs.etf.pp1.mj.runtime.Run [-debug] <.obj_file_src>`

## Opis priloženih test primera

Pored javnih testova, [conditionals.mj](test/conditionals.mj) demonstrira rad sa if iskazima i petljama, iskakanje iz petlji, dok [virt_metode.mj](test/virt_metode.mj) testira nasledjivanje i polimorfizam.

## Novouvedene klase

#### TabExt.java

Prosiruje datu implementaciju tabele simbola, dodavajuci osnovni tip bool, kao i dve strukture podataka koje pomazu pri obradi klasnih metoda.

#### SimpleSymbolTableVisitor.java

Implementacija SymbolTableVisitor klase koja sluzi za ispis tabele simbola.
