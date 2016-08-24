
Okapi — a multitenant API Gateway
=================================

System requirements
-------------------

The Okapi software has the following compile-time dependencies:

* Java 8

* Apache Maven 3.3.x or higher

In addition, the test suite must be able to bind to ports 9130-9134 to succeed.

*Note: If tests fail, the API Gateway may be unable in some cases to shut down
microservices that it has spawned, and they may need to be terminated
manually.*

Quick start
-----------

To build and run:

    $ mvn install
    $ mvn exec:exec

Okapi listens on port 9130.

Documentation
-------------

* [Okapi Guide and Reference](doc/guide.md)
* [Contributing guidelines](CONTRIBUTING.md)

License
-------

Licensed under the Apache License, Version 2.0 (see [LICENSE](LICENSE)).
