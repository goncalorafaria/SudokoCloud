source dependencies.sh

#javac tools/ICount.java
javac tools/StatisticsTool.java

#java ICount project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ 
java StatisticsTool -dynamic project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ 

gcc stresstest/run.c stresstest/board.c -o stresstest/run

./stresstest/run