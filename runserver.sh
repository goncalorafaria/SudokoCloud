
## compiler options
#export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS

## instrumentation tools path
export CLASSPATH="$CLASSPATH:/Users/graf/Documents/cnv/tools/"

## instrumentation path
export CLASSPATH="$CLASSPATH:/Users/graf/Documents/cnv/instrumented/"

## base source path
export CLASSPATH="$CLASSPATH:/Users/graf/Documents/cnv/project/"

## dependencies 
export CLASSPATH="$CLASSPATH:/Users/graf/Documents/BIT/"
## export CLASSPATH="$CLASSPATH:/Users/graf/Documents/BIT/samples/"

#javac tools/ICount.java

#javac tools/SolveCallback.java

#java ICount project/pt/ulisboa/tecnico/cnv/server/ instrumented/pt/ulisboa/tecnico/cnv/server/ 
#java ICount project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ 
#java SolveCallback 
javac project/pt/ulisboa/tecnico/cnv/server/LoadBalancer.java
java pt.ulisboa.tecnico.cnv.server.LoadBalancer
