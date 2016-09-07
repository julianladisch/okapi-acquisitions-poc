# Okapi Acquisitions Proof-of-Concept Module

This explains how to install and run this module.

## Install and run raml module for acquisitions data 

```
git clone https://github.com/folio-org/raml-module-builder.git
cd raml-module-builder/
mvn clean install 
cd ..

git clone https://github.com/folio-org/mod-acquisitions.git
cd mod-acquisitions
mvn clean install
java -jar target/acquisitions-fat.jar -conf '{ "http.port": 8082 }'
```

## Install and run the acq module

Use a second shell:

```
git clone https://github.com/julianladisch/okapi-acquisitions-poc/
cd okapi-acquisitions-poc
mvn package
java -jar target/okapi-acquisitions-poc-fat.jar
```

## Try it out
Open browser at `http://localhost:8079/acq/invoices`
Or try JSON request:
```
curl http://localhost:8079/acq/invoices -H "Accept: application/json"
curl http://localhost:8079/acq/funds    -H "Accept: application/json"
```
