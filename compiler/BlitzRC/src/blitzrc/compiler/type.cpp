
#include "std.h"
#include "type.h"

static struct v_type : public Type{
	bool canCastTo( Type *t ){
		return t==Type::void_type;
	}
}v;

static struct i_type : public Type{
	bool intType(){ 
		return true; 
	}
	bool canCastTo( Type *t ){
		return t==Type::int_type || t==Type::float_type || t==Type::string_type || (t->blitzType() && !t->strict);
	}
	bool canPointTo(Type* t) {
		return t->blitzType() && t->isPointer();
	}
	string name(){return "Int";}
}i;

static struct f_type : public Type{
	bool floatType(){
		return true;
	}
	bool canCastTo( Type *t ){
		return t==Type::int_type || t==Type::float_type || t==Type::string_type;
	}
	string name(){return "Float";}
}f;

static struct s_type : public Type{
	bool stringType(){
		return true;
	}
	bool canCastTo( Type *t ){
		return t==Type::int_type || t==Type::float_type || t==Type::string_type;
	}
	string name(){return "String";}
}s;

bool StructType::canCastTo( Type *t ){
	return t==this || t==Type::null_type || (this==Type::null_type && (t->structType() || t->blitzType())) || (t->structType() && t->structType()->ident == tag) || t->isPointer();
}

bool BlitzType::canCastTo( Type *t ){
	return t==this || t==Type::null_type || (t==Type::int_type && !t->strict);
}

bool BlitzType::canPointTo(Type* t) {
	return ((t == Type::int_type && this->isPointer()) || (t == Type::string_type && this->isPointer()) || (t->blitzType() && this->isPointer()) || (t->isPointer()) || (t->structType() && this->isPointer()));
}

bool VectorType::canCastTo( Type *t ){
	if( this==t ) return true;
	if( VectorType *v=t->vectorType() ){
		if( elementType!=v->elementType ) return false;
		if( sizes.size()!=v->sizes.size() ) return false;
		for( int k=0;k<(int)sizes.size();++k ){
			if( sizes[k]!=v->sizes[k] ) return false;
		}
		return true;
	}
	return false;
}

static StructType n( "Null" );

std::vector<BlitzType*> Type::blitzTypes;

static BlitzType bbbank( "BBBank" );
static BlitzType bbchannel( "BBChannel" );
static BlitzType bbsound( "BBSound" );
static BlitzType bbtexture( "BBTexture" );
static BlitzType bbbrush( "BBBrush" );
static BlitzType bbsurface( "BBSurface" );
static BlitzType bbentity( "BBEntity" );
static BlitzType bbbuffer( "BBBuffer" );
static BlitzType bbimage( "BBImage" );
static BlitzType bbfont( "BBFont" );
static BlitzType bbstream( "BBStream" );
static BlitzType bbdir( "BBDir" );
static BlitzType bbpointer("BBPointer");

Type *Type::void_type=&v;
Type *Type::int_type=&i;
Type *Type::float_type=&f;
Type *Type::string_type=&s;
Type *Type::null_type=&n;
