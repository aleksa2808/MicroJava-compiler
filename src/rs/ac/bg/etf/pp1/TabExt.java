package rs.ac.bg.etf.pp1;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class StructExt {
    public static final int Bool = 1005;
}

// jednostavna klasa jer je dodatak veoma mali
// moze prosiriti Tab i sve pozive Tab-u zameniti sa TabExt
public class TabExt {
    static Struct boolType = new Struct(StructExt.Bool); // hardkod :/

    static Map<Obj, ArrayList<Obj>> methodAdrFixMap;
    static Map<Obj, Obj> methodScopeMap;

    public static void init() {
        Tab.init(); // Universe scope
        Tab.insert(Obj.Type, "bool", TabExt.boolType);

        methodAdrFixMap = new HashMap<>();
        methodScopeMap = new HashMap<>();
    }
}
