source dependencies.sh

source compile.sh

java supervisor.server.LoadBalancer 2>> compile.err
