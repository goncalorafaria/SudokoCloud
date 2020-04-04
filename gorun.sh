## compiler options
export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS

## instrumentation tools path
export CLASSPATH="$CLASSPATH:/Users/graf/Documents/cnv/tools/"

## instrumentation path
export CLASSPATH="$CLASSPATH:/Users/graf/Documents/cnv/instrumented/"

## base source path
export CLASSPATH="$CLASSPATH:/Users/graf/Documents/cnv/project/"

## dependencies 
export CLASSPATH="$CLASSPATH:/Users/graf/Documents/BIT/"
##export CLASSPATH="$CLASSPATH:/Users/graf/Documents/BIT/samples/"

## echo "-------------------------Setup_Classpath-------------------------"


#javac tools/ICount.java
javac tools/StatisticsTool.java

#java ICount project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ 
java StatisticsTool -dynamic project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ 

gcc stresstest/run.c stresstest/board.c -o stresstest/run

./stresstest/run