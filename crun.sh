source dependencies.sh

source compile.sh

#java ICount project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ 
java StatisticsTool -dynamic project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/ 

gcc stresstest/run.c stresstest/board.c -o stresstest/run

./stresstest/run