# Intended for local offline installer creation based on custom tag in a cygwin environment using Windows binaries
set -e

if [ $( dirname "${BASH_SOURCE[0]}" ) != "." ]; then
    echo "Run from the installers directory"
    exit -1
fi

export TRAVIS_TAG=$(git describe --tags --abbrev=0)
TARNAME=images-${TRAVIS_TAG}.tar


echo -n "Creating images archive... "
( cd ../../backend && docker save -o $TARNAME $(docker-compose config | awk '{if ($1 == "image:") { sub(/\r/, "", $2); print $2; }}' ORS=" ") )
mv ../../backend/$TARNAME .
echo "done."

"/cygdrive/c/Program Files (x86)/Inno Setup 5/ISCC.exe" /DVersion=${TRAVIS_TAG} /DOffline windows.iss

