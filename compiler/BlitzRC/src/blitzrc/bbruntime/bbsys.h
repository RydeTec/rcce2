
#ifndef BBSYS_H
#define BBSYS_H

#include "basic.h"
#include "../gxruntime/gxruntime.h"

extern bool debug,test;
extern gxRuntime *gx_runtime;

extern std::vector<std::string> errorLog;
BBStr * bbErrorLog( );

struct bbEx{
	const char *err;
	bbEx( const char *e ):err(e){
		if( e ) {
            
            string panicStr = e;
            panicStr+="\n\nStack trace [" + std::to_string(blockTraces.size()) + "]:\n";
            try {
                string tmp = "";
                if (blockTraces.size() == 0) panicStr = e;
                for (int i=0; i<blockTraces.size(); i++) {
                    tmp = "- "+ blockTraces[i].print() + "\n" + tmp;
                }
                panicStr+=tmp;
            } catch (exception e){
                panicStr+="ERROR RETRIEVING FULL STACKTRACE: ";
                panicStr+=e.what();
                panicStr+="\n";
            } catch (...) {
                panicStr+="ERROR RETRIEVING FULL STACKTRACE\n";
            }

            gx_runtime->debugError( panicStr.c_str() );
        }
	}
};

#define RTEX( _X_ ) throw bbEx( _X_ );

#endif