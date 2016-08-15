# Okapi Acquisitions Proof-of-Concept Module

## Install lsp-apis-impl, run embedded MongoDB with circulation and acquisitions data 

```
git clone https://github.com/sling-incubator/lsp-apis-impl/
cd lsp-apis-impl/domain-models-poc
mvn clean install 
cd ../circulation
mvn clean install
cd ../acquisitions
mvn clean install
cd ..
java -jar circulation/target/circulation-fat.jar   -conf '{ "http.port": 8081 }'
```

In second command shell:
```
java -jar acquisitions/target/acquisitions-fat.jar -conf '{ "http.port": 8082 }'
```

## Install and run the acq module

```
git clone https://github.com/sling-incubator/okapi/
cd okapi/acquisitions
mvn package -DskipTests
java -jar target/okapi-acquisitions-module-fat.jar
```

## Try it out
Open browser at `http://localhost:8079/acq/invoices`
Or try JSON request:
```
curl http://localhost:8079/acq/invoices -H "Accept: application/json"
curl http://localhost:8079/acq/funds    -H "Accept: application/json"
```
