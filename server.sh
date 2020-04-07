source dependencies.sh

source compile.sh

java ILoad

java ICount project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver/

java pt.ulisboa.tecnico.cnv.server.WebServer
