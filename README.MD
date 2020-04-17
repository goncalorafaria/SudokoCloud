## Dependencies.

* AWS
* JAVA 7

É preciso indicar a diretoria da API da AWS no ficheiro dep.config.


 
#### Before running the code:
* Fill in your AWS access credentials in the provided credentials file template, and be sure to move the file to the default location (~/.aws/credentials) where the sample code will load the credentials from.

https://console.aws.amazon.com/iam/home?#security_credential

* Ir ao ao ficheiro dep.config e alterar a variável $AWS_DIR para a diretoria da api do aws.

## Execution.

(chmod 755 aos ficheiro *.sh)


*  Executa o program em C para calcular tempos de vários parametros de execução e coleciona o resultados de instrumentação

` $ ./crun.sh `

*  Executa um exemplo no solver diretamente.

` $ ./solve.sh `

* Executa o servidor

` $ ./server.sh `

* Executa o load balancer

` $ ./balancer.sh `

* Limpa os ficheiros gerados nas execuções anteriores.

` $ ./clean.sh `
