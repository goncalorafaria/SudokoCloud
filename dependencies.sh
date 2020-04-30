## compiler options
export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS

source dep.config

## instrumentation tools source
export CLASSPATH="$CLASSPATH:$PWD/tools/"

## instrumentation path
export CLASSPATH="$CLASSPATH:$PWD/instrumented/"

## base source path
export CLASSPATH="$CLASSPATH:$PWD/project/"

# BIT
export CLASSPATH="$CLASSPATH:$PWD/project/BIT/"

# AWS API
export CLASSPATH="$CLASSPATH:$AWS_DIR/lib/aws-java-sdk-1.11.766.jar" #751
export CLASSPATH="$CLASSPATH:$AWS_DIR/third-party/lib/*"



## JM
 
export JAVA_HOME="/c/Program Files/Java/jdk1.7.0_80"
#/usr/lib/jvm/java-7-openjdk-amd64/
export JAVA_ROOT="/c/Program Files/Java/jdk1.7.0_80"
export JDK_HOME="/c/Program Files/Java/jdk1.7.0_80"
export JRE_HOME="/c/Program Files/Java/jdk1.7.0_80/jre"
export PATH="/c/Program Files/Java/jdk1.7.0_80/bin":$PATH
export SDK_HOME="/c/Program Files/Java/jdk1.7.0_80"
