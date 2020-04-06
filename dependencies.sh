## compiler options
#export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS

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
export CLASSPATH="$CLASSPATH:$AWS_DIR/lib/aws-java-sdk-1.11.751.jar"
export CLASSPATH="$CLASSPATH:$AWS_DIR/third-party/lib/*"