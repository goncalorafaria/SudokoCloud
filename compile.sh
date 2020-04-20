echo "Starting the compilation process" 2>> compile.err

javac tools/*.java 2>> compile.err
javac project/supervisor/server/*.java 2>> compile.err
javac project/supervisor/storage/*.java 2>> compile.err
javac project/supervisor/util/*.java 2>> compile.err

java ILoad 2>> compile.err

java ICount project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ 0 0 0 0 2>> compile.err

java ICount instrumented/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ true 2>> compile.err

java HijackQuery 2>> compile.err

