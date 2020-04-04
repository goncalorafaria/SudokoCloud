## compiler options
export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS

export CLASSPATH="$CLASSPATH:$PWD/tools/"

## instrumentation path
export CLASSPATH="$CLASSPATH:$PWD/instrumented/"

## base source path
export CLASSPATH="$CLASSPATH:$PWD/project/"

## dependencies 
export CLASSPATH="$CLASSPATH:$PWD/project/BIT/"

echo ""
echo "#######################"
echo "Installing dependencies"
echo "#######################"
echo ""