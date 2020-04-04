## compiler options
export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS

source dep.config

export CLASSPATH="$CLASSPATH:$PWD/tools/"

## instrumentation path
export CLASSPATH="$CLASSPATH:$PWD/instrumented/"

## base source path
export CLASSPATH="$CLASSPATH:$PWD/project/"

## dependencies 

# BIT
export CLASSPATH="$CLASSPATH:$PWD/project/BIT/"

# AWS API
export CLASSPATH="$CLASSPATH:$AWS_DIR/lib/aws-java-sdk-1.11.751.jar"

export CLASSPATH="$CLASSPATH:$AWS_DIR/third-party/lib/*"

echo ""
echo "#######################"
echo "Installing dependencies"
echo "#######################"
echo ""