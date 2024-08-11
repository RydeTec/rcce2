
/*

  The Toker converts an inout stream into tokens for use by the parser.

  */

#ifndef TOKER_H
#define TOKER_H

enum{
	DIM=0x8000,GOTO,GOSUB,EXIT,RETURN,
	IF,THEN,ELSE,ENDIF,ELSEIF,
	WHILE,WEND,
	FOR,TO,STEP,NEXT,
	FUNCTION,ENDFUNCTION,
	TYPE,ENDTYPE,EACH,
	METHOD,ENDMETHOD,
	GLOBAL,LOCAL,FIELD,BBCONST,
	SELECT,CASE,DEFAULT,ENDSELECT,
	REPEAT,UNTIL,FOREVER,
	DATA,READ,RESTORE,
	ABS,SGN,MOD,
	PI,BBTRUE,BBFALSE,
	BBINT,BBFLOAT,BBSTR,
	INCLUDE,TEST, ENDTEST,

	BBNEW,BBDELETE,FIRST,LAST,INSERT,BEFORE,AFTER,
	OBJECT,BBHANDLE,ASSERT,RECAST,
	AND,OR,XOR,NOT,SHL,SHR,SAR,

	LE,GE,NE,
	IDENT,INTCONST,BINCONST,HEXCONST,FLOATCONST,STRINGCONST,NULLCONST,

	USESTRICTTYPING, NOTRACE,

	BBPOINTER
};

class Toker{
public:
	Toker( istream &in );

	int pos();
	int curr();
	int next();
	int at(int toke);
	int findNext( int toke, int offset=0 );
	string text();
	string textAt(int toke);
	int current_toke();
	int lookAhead( int n );
	void inject( string code );
	void rollback();
	string getLine();

	static int chars_toked;

	static map<string,int> &getKeywords();

	static bool noTrace;

private:
	struct Toke{
		int n,from,to;
		Toke( int n,int f,int t ):n(n),from(f),to(t){}
	};
	istream &in;
	string line;
	vector<Toke> tokes;
	void nextline();
	int curr_row,curr_toke;

	int process_injected=0;
	vector<string> line_cache;
	vector<vector<Toke>> tokes_cache;
	vector<int> curr_toke_cache;
};

#endif
