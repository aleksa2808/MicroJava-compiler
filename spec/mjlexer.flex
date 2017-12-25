package rs.ac.bg.etf.pp1;
import java_cup.runtime.Symbol;

%%

%{
		// ukljucivanje informacije o poziciji tokena
		private Symbol new_symbol(int type) {
				return new Symbol(type, yyline+1, yycolumn);
		}

		// ukljucivanje informacije o poziciji tokena
		private Symbol new_symbol(int type, Object value) {
				return new Symbol(type, yyline+1, yycolumn, value);
		}

		private int lastErrLinePos = -1;
		private int lastErrColPos = -1;
		private String lastError = "";
		private int lastErrLine = -1;
		private int lastErrCol = -1;

		private void handleError(String s, boolean end) {
		    if (!end && lastErrLinePos == yyline+1 && lastErrColPos == yycolumn) {
                lastError += s;
		    } else {
                if (!lastError.equals("")) {
                    System.out.println("ERROR: Leksicka greska (" + lastError + ") u liniji " + lastErrLine + " na koloni " + lastErrCol);
                }

                lastError = s;
                lastErrLine = yyline+1;
                lastErrCol = yycolumn+1;
		    }

		    lastErrLinePos = yyline+1;
		    lastErrColPos = yycolumn+1;
		}
%}

%cup
%line
%column

%xstate COMMENT

%eofval{
    handleError("", true);
    return new_symbol(sym.EOF);
%eofval}

%%

" "     {}
"\b"    {}
"\t"    {}
"\n"    {}
"\r\n"  {}
"\f"    {}

"program"   { return new_symbol(sym.PROG, yytext()); }
"break"     { return new_symbol(sym.BREAK, yytext()); }
"class"     { return new_symbol(sym.CLASS, yytext()); }
"else"      { return new_symbol(sym.ELSE, yytext()); }
"if"        { return new_symbol(sym.IF, yytext()); }
"new"       { return new_symbol(sym.NEW, yytext()); }
"print"     { return new_symbol(sym.PRINT, yytext()); }
"read"      { return new_symbol(sym.READ, yytext()); }
"return"    { return new_symbol(sym.RETURN, yytext()); }
"void"      { return new_symbol(sym.VOID, yytext()); }
"do"        { return new_symbol(sym.DO, yytext()); }
"while"     { return new_symbol(sym.WHILE, yytext()); }
"extends"   { return new_symbol(sym.EXTENDS, yytext()); }
"continue"  { return new_symbol(sym.CONTINUE, yytext()); }
"const"     { return new_symbol(sym.CONST, yytext()); }

"+"         { return new_symbol(sym.OP_ADD, yytext()); }
"-"         { return new_symbol(sym.OP_SUB, yytext()); }
"*"         { return new_symbol(sym.OP_MUL, yytext()); }
"/"         { return new_symbol(sym.OP_DIV, yytext()); }
"%"         { return new_symbol(sym.OP_MOD, yytext()); }

"=="        { return new_symbol(sym.OP_EQ, yytext()); }
"!="        { return new_symbol(sym.OP_NEQ, yytext()); }
">"         { return new_symbol(sym.OP_GRE, yytext()); }
">="        { return new_symbol(sym.OP_GEQ, yytext()); }
"<"         { return new_symbol(sym.OP_LES, yytext()); }
"<="        { return new_symbol(sym.OP_LEQ, yytext()); }

"&&"        { return new_symbol(sym.OP_AND, yytext()); }
"||"        { return new_symbol(sym.OP_OR, yytext()); }

"="         { return new_symbol(sym.ASSIGN, yytext()); }

"++"        { return new_symbol(sym.OP_INC, yytext()); }
"--"        { return new_symbol(sym.OP_DEC, yytext()); }

";"         { return new_symbol(sym.SEMI, yytext()); }
","         { return new_symbol(sym.COMMA, yytext()); }
"."         { return new_symbol(sym.DOT, yytext()); }

"("         { return new_symbol(sym.LPAREN, yytext()); }
")"         { return new_symbol(sym.RPAREN, yytext()); }
"["         { return new_symbol(sym.LSQUARE, yytext()); }
"]"         { return new_symbol(sym.RSQUARE, yytext()); }
"{"         { return new_symbol(sym.LBRACE, yytext()); }
"}"         { return new_symbol(sym.RBRACE, yytext()); }

"//"                { yybegin(COMMENT); }
<COMMENT> .         { yybegin(COMMENT); }
<COMMENT>"\n"       { yybegin(YYINITIAL); }
<COMMENT> "\r\n"    { yybegin(YYINITIAL); }

"true" | "false" { return new_symbol(sym.BOOL_CONST, yytext()); }
[0-9]+ { return new_symbol(sym.NUM_CONST, new Integer(yytext())); }
([a-z]|[A-Z])[a-z|A-Z|0-9|_]* { return new_symbol(sym.IDENT, yytext()); }
"'"[\040-\176]"'" { return new_symbol(sym.CHAR_CONST, new Character(yytext().charAt(1))); }

.
{
    handleError(yytext(), false);
}