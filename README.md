ZHdK `users` and `groups` sync for _leihs_
==========================================

This repository contains code, build definition and deployment recipies for the
synchronization of `users` and `groups` to the ZHdK leihs instance,
https://ausleihe.zhdk.ch of the ZHdK. The source of data is the `ZAPI` service
which essentially maps data from the evento database and makes it available via
HTTP.

The final service will run nightly when deployed.


## Development

The in clojure written source code is located under `/src` and some
configuration under `/resources`. 

A prototyping and development environment can be started with

    lein do clean, repl

within the REPL, the main methond can be invoked with e.g.

    (main- "-h")

The final build artifact can be constructed with

    lein uberjar

and run via

    java -jar leihs_zhdk-sync.jar -h



## Deployment

example:

    ansible-playbook -v -i ../zhdk-inventory/developer-hosts --limit tom deploy/deploy_play.yml -e 'leihs_api_token=ABC'


### ZAPI 

    curl -u $ZAPI_TOKEN_USER https://zapi.zhdk.ch/v1/documentation | json_pp

    curl -u $ZAPI_TOKEN_USER https://zapi.zhdk.ch/v1/person/documentation | json_pp

    curl -u $ZAPI_TOKEN_USER https://zapi.zhdk.ch/v1/person/?limit=1 | json_pp

    curl -s -u "$ZAPI_TOKEN_USER:" 'https://zapi.zhdk.ch/v1/person/?limit=1&last_name=schank&fieldsets=basic,personal_contact,leihs_temp' | json_pp


