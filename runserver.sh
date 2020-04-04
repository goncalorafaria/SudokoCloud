source dependencies.sh

#javac tools/ICount.java

#javac tools/SolveCallback.java

#java ICount project/pt/ulisboa/tecnico/cnv/server/ instrumented/pt/ulisboa/tecnico/cnv/server/ 
#java ICount project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ 
#java SolveCallback 

javac project/pt/ulisboa/tecnico/cnv/server/LoadBalancer.java
java pt.ulisboa.tecnico.cnv.server.LoadBalancer
