echo "Starting the compilation process"

# configure aws, java 7 and add everything to filepath.
source dependencies.sh

#actually compile
#javac -Xlint:unchecked tools/*.java
#javac -Xlint:unchecked project/supervisor/balancer/*.java
#javac -Xlint:unchecked project/supervisor/server/*.java 
#javac -Xlint:unchecked project/supervisor/storage/*.java 
#javac -Xlint:unchecked project/supervisor/util/*.java

javac tools/*.java
javac project/supervisor/balancer/*.java
javac project/supervisor/server/*.java 
javac project/supervisor/storage/*.java 
javac project/supervisor/util/*.java   

# Making sure the folders required by the instrumentation exist
mkdir -p $PWD/instrumented/pt/ulisboa/tecnico/cnv/solver/
mkdir -p $PWD/instrumented/pt/ulisboa/tecnico/cnv/server/
mkdir -p $PWD/instrumented/pt/ulisboa/tecnico/cnv/util/

# Instrument base supervisor in webserver. 
java ILoad 

# instrumentation with branch's taken only. 
java ICount project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ 0 0 1 0

# uncomment for mesuring instrumentation overhead. 
# java ICount instrumented/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ true

# Instrument request intercept.
java HijackQuery 

