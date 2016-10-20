# Okapi Acquisitions Proof-of-Concept Module

## Run as stand alone

### Install and run persistency layer for acquisitions data

```
git clone https://github.com/folio-org/raml-module-builder.git
cd raml-module-builder/
mvn clean install 
cd ..

git clone https://github.com/folio-org/mod-acquisitions.git
cd mod-acquisitions
mvn clean install
java -jar target/acquisitions-fat.jar -Dhttp.port=8083 embed_mongo=true
```

### Install and run the functional acq module

Use a second shell:

```
git clone https://github.com/julianladisch/okapi-acquisitions-poc/
cd okapi-acquisitions-poc
mvn package
java -jar target/okapi-acquisitions-poc-fat.jar
```

### Try it out
Open browser at `http://localhost:8079/acq/invoices`

Or try JSON request:
```
curl http://localhost:8079/acq/invoices -H "Accept: application/json"
curl http://localhost:8079/acq/funds    -H "Accept: application/json"
curl http://localhost:8079/acq/po_lines -H "Accept: application/json"
```

## Run using Okapi framework

### Install and run Okapi

```
git clone https://github.com/folio-org/okapi.git
cd okapi
mvn clean install -DskipTests
mvn exec:exec
```

### Building and deploying acquisitions modules

Required directory layout to make relative paths required for okapi work:

```
common base directory
├── okapi
├── mod-acquisitions
└── okapi-acquisitions-poc
```

```
git clone https://github.com/folio-org/raml-module-builder.git
cd raml-module-builder/
mvn clean install
cd ..

git clone https://github.com/folio-org/mod-acquisitions.git
cd mod-acquisitions
mvn clean install
cd ..

git clone https://github.com/julianladisch/okapi-acquisitions-poc/
cd okapi-acquisitions-poc
mvn package
./deploy.sh
```

Now invoke `http://localhost:9130/acq/invoices` in your browser.
