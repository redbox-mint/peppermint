# Peppermint [![Build Status](https://travis-ci.org/redbox-mint/peppermint.svg?branch=master)](https://travis-ci.org/redbox-mint/peppermint)

JSON data transformer for [ReDBox](http://redboxresearchdata.com.au/), and others.

## Architecture
```

 +------------------+                            +-------------------+
 |                  |                            |                   |
 | Source of Truth  |                            | Peppermint Portal |
 |                  |                            |                   |
 +--------^---------+                            +-------------------+
          |                                               |
          |                                               |
 +------------------+      +--------------+      +--------v----------+
 |                  |      |              |      |                   |
 |    Peppermint    +------>  Peppermint  +------>    Search Index   |
 |      Runner      |      |              |      |      (SOLR)       |
 +------------------+      +--------------+      +-------------------+


```

## Requirements

- JRE installation
- A Groovy installation

## Running

### Via Docker (e.g. local development)
- Install [docker-compose](https://docs.docker.com/compose/install/)
- Run: `mkdir /mnt/data/solr; chown 8983:8983 /mnt/data/solr`
- From the project directory, run: `docker-compose up`

### Via manual run

- Build, the project (see below), then run command from project directory: `java -cp <path to groovy installation>/lib/*:./build/libs/peppermint-fat.jar io.vertx.core.Launcher`
- App is pre-configured to use a SOLR instance available on the `localhost:8983`.


## Building
- Run command: ` ./gradlew shadowJar`

## Customising
The `scripts` directory contains some sample scripts that will accept Datacrate JSONs and turn these unto custom Dataset, OAI-PMH and RIF-CS records.

Take note that these scripts are now 'cached' for efficiency, and will thus require a restart of Peppermint for every change.
