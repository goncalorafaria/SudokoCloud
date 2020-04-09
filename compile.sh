javac tools/*.java
javac project/supervisor/server/*.java
javac project/supervisor/storage/*.java
javac project/supervisor/util/*.java

java ILoad

java ICount project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/

java HijackQuery

