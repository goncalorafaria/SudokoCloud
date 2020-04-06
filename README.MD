## Dependencies.

É preciso indicar a diretoria da API da AWS no ficheiro dep.config.

 
#### Before running the code:
Fill in your AWS access credentials in the provided credentials file template, and be sure to move the file to the default location (~/.aws/credentials) where the sample code will load the credentials from.

https://console.aws.amazon.com/iam/home?#security_credential

## Execution.

*  Executa o program em C para calcular tempos de vários parametros de execução e coleciona o resultados de instrumentação

` $ ./crun.sh `

*  Executa um exemplo no solver diretamente.

` $ ./solve.sh `

* Executa o servidor

` $ ./serve.sh `
