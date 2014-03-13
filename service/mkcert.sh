#!/bin/sh
PASSWORD=000000
DNAME='CN=kazzla.com,OU=Kazzla,O=Kazzla,ST=Tokyo,C=JP'
JKS=domain.jks
CACERT=ca/demoCA/cacert.pem
keytool -genkeypair -dname $DNAME -alias domain -keyalg RSA -keypass $PASSWORD -keystore $JKS -storepass $PASSWORD -storetype JKS -validity 3650
keytool -import -noprompt -alias kazzla -file $CACERT -keystore $JKS -storetype JKS -storepass $PASSWORD
keytool -certreq    -alias domain -file domain.csr -keypass $PASSWORD -keystore $JKS -storetype JKS -storepass $PASSWORD
pushd ..
openssl ca -config service.domain/ca/demoCA/openssl.cnf -batch -in service.domain/domain.csr -out service.domain/domain.crt -days 3650 -passin pass:kazzla -keyfile service.domain/ca/demoCA/private/cakey.pem
popd
keytool -importcert -alias domain -file domain.crt -keypass $PASSWORD -noprompt -keystore $JKS -storetype JKS -storepass $PASSWORD

