## compiler options
export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS

export CLASSPATH="$CLASSPATH:$PWD/tools/"

## instrumentation path
export CLASSPATH="$CLASSPATH:$PWD/instrumented/"

## base source path
export CLASSPATH="$CLASSPATH:$PWD/project/"

## dependencies 

# BIT
export CLASSPATH="$CLASSPATH:$PWD/project/BIT/"

# AWS API
export CLASSPATH="$CLASSPATH:$PWD/project/aws-java-sdk-1.11.751/lib/aws-java-sdk-1.11.751.jar"

export CLASSPATH="$CLASSPATH:$PWD/project/aws-java-sdk-1.11.751/third-party/lib/*"


echo ""
echo "#######################"
echo "Installing dependencies"
echo "#######################"
echo ""