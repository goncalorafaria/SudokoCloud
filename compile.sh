echo "Starting the compilation process"

source dependencies.sh

javac tools/*.java
javac project/supervisor/balancer/*.java
javac project/supervisor/server/*.java 
javac project/supervisor/storage/*.java 
javac project/supervisor/util/*.java

# Making sure the folders required by the instrumentation exist
mkdir -p $PWD/instrumented/pt/ulisboa/tecnico/cnv/solver/
mkdir -p $PWD/instrumented/pt/ulisboa/tecnico/cnv/server/
mkdir -p $PWD/instrumented/pt/ulisboa/tecnico/cnv/util/

java ILoad 

java ICount project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ 0 0 1 0

# java ICount instrumented/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ true

java HijackQuery 

