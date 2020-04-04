
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

##echo "-------------------------Setup_Classpath-------------------------"

javac tools/ICount.java

java ICount project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ 

java pt.ulisboa.tecnico.cnv.solver.SolverMain -n1 9 -n2 9 -un 81 -i SUDOKU_PUZZLE_9x19_101 -s CP -b [[2,0,0,8,0,5,0,9,1],[9,0,8,0,7,1,2,0,6],[0,1,4,2,0,3,7,5,8],[5,0,1,0,8,7,9,2,4],[0,4,9,6,0,2,0,8,7],[7,0,2,1,4,9,3,0,5],[1,3,7,5,0,6,0,4,9],[4,2,5,0,1,8,6,0,3],[0,9,6,7,3,4,0,1,2]]