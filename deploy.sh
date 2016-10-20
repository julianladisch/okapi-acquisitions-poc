#!/bin/bash

post () {
  echo $1 $2
  curl -w '\n' -X POST --connect-timeout 2 -D - \
    -H "Content-type: application/json" \
    -d "$2" http://localhost:9130/_/$1             || exit
}

post deployment/modules  @okapi-acquisitions-poc-deploy.json
post proxy/modules       @okapi-acquisitions-poc-proxy.json

post deployment/modules  @mod-acquisitions-deploy.json
post proxy/modules       @mod-acquisitions-proxy.json

#post deployment/modules  @circ-module-deploy.json
#post proxy/modules       @circ-module-proxy.json

post proxy/tenants '{ "id": "hbz", "name": "hbz", "description": "Hochschulbibliothekszentrum" }'
#post proxy/tenants/hbz/modules '{ "id": "hbz-deliver-module" }'
post proxy/tenants/hbz/modules '{ "id": "okapi-acquisitions-poc" }'
post proxy/tenants/hbz/modules '{ "id": "mod-acquisitions" }'
#post proxy/tenants/hbz/modules '{ "id": "circulation" }'

