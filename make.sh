#!/bin/sh

# The pde.jar file may be buried inside the .app file on Mac OS X.
#PDE=`find ../.. -name pde.jar`
PDE=./pde.jar

javac -target 1.5 \
  -cp "../../lib/core.jar:$PDE" \
  -d bin \
  src/cc/makeblock/zerogdb/ZeroGDB.java

cd bin && zip -r ../tool/zerodb.jar * 
cp ../tool/zerodb.jar ../../processing/tools/zerogdb/tool
cd ..
