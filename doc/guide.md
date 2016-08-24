# Okapi Guide and Reference

This is the guide and reference to Okapi: a gateway for
managing and running microservices.

## Table of Contents

<!-- Regenerate this as needed by running `make guide-toc.md` and including its output here -->
* [Table of Contents](#table-of-contents)
* [Introduction](#introduction)
* [Architecture](#architecture)
    * [Okapi's own Web Services](#okapis-own-web-services)
    * [Deployment and Discovery](#deployment-and-discovery)
    * [Request Processing](#request-processing)
    * [Status Codes](#status-codes)
    * [Header Merging Rules](#header-merging-rules)
    * [Versioning and Dependencies](#versioning-and-dependencies)
    * [Security](#security)
    * [Open Issues](#open-issues)
* [Implementation](#implementation)
    * [Missing features](#missing-features)
* [Compiling and Running](#compiling-and-running)
* [Using Okapi](#using-okapi)
    * [Storage](#storage)
    * [Curl examples](#curl-examples)
    * [Example modules](#example-modules)
    * [Running Okapi itself](#running-okapi-itself)
    * [Deploying Modules](#deploying-modules)
    * [Creating tenants](#creating-tenants)
    * [Enabling a module for a tenant](#enabling-a-module-for-a-tenant)
    * [Using a module](#using-a-module)
    * [Enabling both modules for the other tenant](#enabling-both-modules-for-the-other-tenant)
    * [Authentication problems](#authentication-problems)
    * [Cleaning up](#cleaning-up)
* [Reference](#reference)
    * [Okapi program](#okapi-program)
    * [Web Service](#web-service)
    * [Instrumentation](#instrumentation)

## Introduction

This document aims to provide an overview of concepts that relate to Okapi and
the entire ecosystem around it (e.g. core vs modules) as well as details of the
implementation and usage of Okapi: by presenting concrete web service
endpoints and details of request processing - handling of request and
response entities, status codes, error conditions, etc.

Okapi is an implementation of a couple different patterns commonly used within
the microservice architecture. The most central of them is the so called "API
Gateway" pattern which is implemented by the core Okapi 'proxy' service.
Conceptually, the API Gateway is a server that is a single entry point into
the system. It is similar to the
[Facade pattern](http://en.wikipedia.org/wiki/Facade_pattern)
from object-oriented design. Per the [standard
definition](https://www.nginx.com/blog/building-microservices-using-an-api-gateway/),
which Okapi follows quite closely, _the API Gateway encapsulates the
internal system architecture and provides a unified API that may be
tailored to each client; it might also include core responsibilities
such as authentication, monitoring, load balancing, caching, request
shaping and management, and static response handling_: from the Message
Queue design pattern to allow broadcasting of requests to multiple
services (initially synchronously and eventually, possibly,
asynchronously) and returning a final response. Finally, Okapi
facilitates communication between services by acting as a Service
Discovery tool: service A wanting to talk to service B only needs to
know its HTTP interface since Okapi will inspect the registry of
available services to locate the physical instance of the service.

Okapi is designed to be configurable and extensible - it allows one to
expose new, or enrich existing, web service endpoints without a need
for programmatic changes to the software itself. Registration of new
services ('modules' as seen from Okapi) happens by making calls to the Okapi
core web services. It is envisioned that the registration, and associated core
management tasks, will be performed by the Service Provider
administrator. This configurability and extensibility is necessary to
allow for app store features in which services or groups of services
('applications') can be enabled or disabled per tenant on demand.

## Architecture

Web service endpoints in Okapi can be, roughly, divided into two
parts: (1) general module and tenant management APIs, sometimes
referred to as 'core' - initially part of Okapi itself but potentially
separable into their own services  - and (2) endpoints for accessing
module-provided, business-logic specific interfaces, e.g. Patron
management or Circulation. This document will discuss the former in
detail and offer a general overview of allowed formats and styles for
the latter.

The specification of the core Okapi web services, in its current form,
is captured in [RAML](http://raml.org/) (RESTful API Modeling
Language). See the [Reference](#reference) section.  The
specification, however, aims to make very few assumptions about the
actual API endpoints exposed by specific modules, which are basically
left undefined.  The goal is to allow for different styles and formats
of those APIs (RESTful vs RPC and JSON vs XML, etc.) with only the
basic requirement of a common transport protocol (HTTP). It is
envisioned that the transport protocol assumption may be lifted or
worked around for some special cases (e.g. the ability to integrate
non-HTTP, binary protocols, such as a truly asynchronous protocol for
operation similar to a message queue).

### Okapi's own Web Services

As mentioned, Okapi's own web services provide the basic functionality
to set up, configure and enable modules and manage tenants. The core
endpoints are:

 * `/_/deployment`
 * `/_/discovery`
 * `/_/proxy`

The special prefix `/_` is used to to distinguish the routing for Okapi
internal web services from the extension points provided by modules.

 * The `/_/proxy` endpoint is used for configuring the proxying service:
   specifying which modules we know of, how their requests are to be
   routed, which tenants we know about, and which modules are enabled for
   which tenants.

 * The `/_/deployment` endpoint is responsible for deploying modules.
   In a clustered environment there should be one instance of the
   deployment service running on
   each node. It will be responsible for starting processes on that node,
   and allocating network addresses for the various service modules.

 * The `/_/discovery` endpoint manages the mapping from service IDs to network
   addresses on the cluster. Information is posted to it, and the proxy service
   will query it to find where the needed modules are actually available. It also
   offers shortcuts for deploying and registering a module in one
   go. There is only a single discovery endpoint covering all of the
   nodes in a cluster. Requests to the discovery service can also deploy
   modules on specific nodes, so it is rarely necessary to invoke
   deployment directly.

These three parts are coded as separate services, so that it will be possible
to use alternative deployment and discovery methods, if the chosen clustering
system offers such.

![Module Management Diagram](module_management.png "Module Management Diagram")

#### What are 'modules'?

Modules in the Okapi ecosystem are defined in terms of their _behavior_
(or, in other words, _interface contract_)  rather than their _contents_,
meaning there is no exact definition of a module as a package or an archive,
e.g. with the underlying file structure standardized.
Those details are left to the particular module implementation (as noted
before, Okapi server-side modules can utilize any technology stack).

Hence any piece of software that manifests the following traits can become
an Okapi module:

* It is an HTTP network server that communicates using a REST-styled
web service protocol -- typically, but not necessarily, with a JSON payload.

* It comes with a descriptor file, namely the
[`ModuleDescriptor.json`](../okapi-core/src/main/raml/ModuleDescriptor.json), which
declares the basic module metadata (id, name, etc.), specifies the module's dependencies
on other modules (interface identifiers to be precise), and reports all
"provided" interfaces.

* `ModuleDescriptor.json` has a list of all `routes` (HTTP paths and methods)
that a given module handles, this gives Okapi necessary information to proxy
traffic to the module (this is similar to a simplified RAML specification).

* It follows versioning rules defined in the chapter
[_Versioning and Dependencies_](#versioning-and-dependencies).

* WIP: it provides interfaces required for monitoring and instrumentation.

As you can see, none of those requirements specifically state rules for
deployment and, as such, it would be entirely possible to integrate
a third party web service (e.g. the API of a publicly accessible Internet server)
as an Okapi module. That is, assuming the endpoint style and versioning
semantics are a close match for what is required in Okapi, and a
suitable module descriptor can be written to describe it.

Okapi, however, includes additional services (for service deployment and
discovery) that allows it to execute, run and monitor services natively
on a cluster that it manages.
Those _native modules_ require an additional descriptor
file, the
[`DeploymentDescriptor.json`](../okapi-core/src/main/raml/DeploymentDescriptor.json),
which specifies the low-level information
about how to run the module. Also, native modules must be packaged according
to one of the packaging options supported by Okapi's deployment service: at
this point that means providing the executable (and all dependencies) on each
node or using on a self-contained Docker image to distribute the executable
from a centralized place.


#### API guidelines

Okapi's own web services must, and other modules should, adhere to these
guidelines as far as practically possible.

 * No trailing slashes in paths
 * Always expect and return proper JSON
 * The primary key should always be called 'id'

We try to make the Okapi code exemplary, so that it would serve well as
an example for other module developers to emulate.


#### Core Okapi Web Service Authentication and Authorization

Access to the core services (all resources under the `/_/` path) is
granted to the Service Provider (SP) administrator, as the
functionality provided by those services spans multiple tenants. The
details of authentication and authorization of the SP administrators
are to be defined at a later stage and will most likely be provided by
an external module that can hook into a specific Service Provider
authentication system.

### Deployment and Discovery

Making a module available to a tenant is a multi-step process:

 * The module gets deployed. That means a process is started on some nodes,
offering a web service on some network address.
 * The service ID and network address are POSTed to the discovery module, so
Okapi can find out where the service is running.
 * The service ID and routing entries are posted to the proxy module, so Okapi
can know where to route incoming requests
 * The service ID is enabled for some tenants.

We assume some external management program will be making these requests.  It
can not be a proper Okapi module itself, because it needs to be running before
any modules have been deployed. For testing, see
the curl command-line [examples](#using-okapi) later in this document.

The `/_/discovery` endpoint offers a shortcut to deploy a module at the same time
as registering it. If the DeploymentDescriptor contains a nodeId, we assume
that the module is to be deployed on that node. If the DeploymentDescriptor
contains a LaunchDescriptor, this is used for starting the process. If not,
Okapi fetches the ModuleDescriptor that has been registered with the
proxy module, and if that
contains a LaunchDescriptor, it will be used. This way, we can adapt to what
ever deployment needs the installation will have.

Note that the deployment and discovery stuff is transient, Okapi does not store
any of that in its database. If a node goes down, the processes on it will die
too. When it gets restarted, modules need to be deployed on it again, either via
Okapi, or through some other means.

The discovery data is kept in a shared map, so as long as there is one Okapi
running on the cluster, the map will survive. But if the whole cluster is taken
down, the discovery data is lost. It would be fairly useless at that point anyway.

### Request Processing

Any number of modules can request registration on a single URI
path. Okapi will then forward the requests to those modules in an
order controlled by the integer-valued `level` setting in the module
registration configuration: modules with lower levels are processed
before those with higher levels.

Although Okapi accepts both HTTP 1.0 and HTTP 1.1 requests, it uses HTTP 1.1 with
chunked encoding to make the connections to the modules.

We envision that different kinds of modules will carry different level
values: e.g. authentication and authorization will have the lowest
possible priority, next the actual business logic processing unit,
followed by metrics, statistics, monitoring, logging, etc.

The module metadata also controls how the request is forwarded to
consecutive modules in a pipeline and how the responses are
processed. Currently, we have three kinds of request processing by
modules (controlled by the `type` parameter in the module registration
configuration). The possible values are:

 * `headers` - the module is interested in headers/parameters only,
and it can inspect them and perform an action based on the
presence/absence of headers/parameters and their corresponding
value. The module is not expected to return any entity in the
response, but only a status code to control the further chain of
execution or, in the case of an error, an immediate termination. The
module may return certain response headers that will be merged into
the complete response header list according to the header manipulation
rules below.

 * `request-only` - the module is interested in the full client
request: header/parameters and the entity body attached to the
request. It does not produce a modified version or a new entity in the
response but performs an associated action and returns optional
headers and a status code to indicate further processing or
termination. In cases when an entity is returned, Okapi will discard
it and continue forwarding the original request body to the subsequent
modules in the pipeline.

 * `request-response` - the module is interested in both
headers/parameters and the request body. It is also expected that the
module will return an entity in the response. This may be e.g. a
modified request body, in which case the module acts as a filter. The
returned response may then be forwarded on to the subsequent modules
as the new request body. Again, the chain of processing or termination
is controlled via the response status codes, and the response headers
are merged back into the complete response using the rules described
below.

Most requests will likely be of type `request-response`, which is the
most powerful but potentially also most inefficient type, since it
requires content to be streamed to and from the module. Where more
efficient types can be used, they should be. For example, the
Authentication module's permission checking consults only the headers
of the request, and returns no body, so it is of type
`headers`. However, the same module's initial login request consults
the request body to determine the login parameters, and it also
returns a message; so it must be of type `request-response`.


### Status Codes

Continuation or termination of the pipeline is controlled by a status
code returned by an executed module. Standard [HTTP status
code](https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html) ranges
are accepted in Okapi:

 * 2xx range: OK return codes; if a code in this range is
returned by a module, Okapi continues execution of the pipeline and
forwards information to the consecutive modules according to the rules
described above. At the end of the chain, the status returned by the
last module invoked is the one returned to the caller.

 * 3xx range: Redirect codes. The pipeline is terminated, and the
response (including any `Location` header) is immediately returned
to the caller.

 * 4xx-5xx range: user request errors or internal system errors; if a
code in this range is returned by a module, Okapi immediately
terminates the entire chain and returns the code back to the caller.

### Header Merging Rules

Since Okapi forwards the response from a previous module on to the
next module in the pipeline (e.g. for additional filtering/processing),
certain initial request headers become invalid - e.g. when a
module converts the entity to a different content type or changes its
size. Invalid headers need to be updated, based on the module's
response header values, before the request can be forwarded to the
next module. At the same time Okapi also collects a set of response
headers in order to produce a final response that is sent back to the
original client when the processing pipeline completes.

Both sets of headers are modified according to the following rules:

 * Any headers that provide metadata about the request entity body
(e.g.  Content-Type, Content-Length, etc.) are merged from the last
response back into the request.

 * An additional set of special debug and monitoring headers is merged
from the last response into the current request (in order to forward
them to the next module).

 * A list of headers that provide metadata about the response entity
body is merged to the final response header set.

 * An additional set of special headers (debug, monitoring) or any
other headers that should be visible in the final response is merged
into the final response header set.

Okapi always adds a X-Okapi-Url header to the request to any modules.
This tells the modules how they can make further calls to Okapi, should
they need to. This Url can be specified on the command line when starting
Okapi, and it can well point to some load balancer in front of multiple
Okapi instances.

### Versioning and Dependencies

Modules can provide one or more interfaces, and can consume interfaces
provided by other modules. The interfaces have versions, and dependencies
can require given versions of an interface. Okapi will check dependencies and versions
whenever a module is deployed, and also when a module is enabled for a tenant.

Note that we can have multiple modules providing the same interface. These
can be deployed in Okapi simultaneously, but only one such module can be enabled
for any given tenant at a given time. For example, we can have two ways to
manage our patrons, one based on a local database, one talking to an external
system. The installation can know both, but each tenant must choose one or
the other.


#### Version numbers

We use a 3-part versioning scheme for module software versions, like
3.1.41 -- very much like [Semantic Versioning](http://semver.org/).
Interface versions consist only of the first two parts.

The first number is the major version of the interface. It needs to be
incremented whenever making a change that is not strictly backwards
compatible, for example removing functionality or changing semantics.
Okapi will require that the major version number matches exactly what
is required.

The second number is the minor version of the interface. It needs to
be incremented whenever backwards-compatible changes are made, for
example adding new functionality or optional fields.  Okapi will check
that the module implementing a service provides at least the required
minor version.

The third number is the software version. It should be incremented on changes
that do not affect the interface, for example fixing bugs or improving
efficiency.

If a module requires an interface 3.2.41, it will accept:
* 3.2.41  - same version
* 3.2.68  - same interface, later software version
* 3.3.8   - Higher minor version, compatible interfaces

But it will reject:
* 2.2.2   - Lower major version
* 4.4.4   - Higher major version
* 3.1.9   - Lesser minor version
* 3.2.27  - Too small software version, may not contain crucial bug-fixes


### Security

(Note: We are in the process of moving the security discussion into its own
document, [Okapi Security Model](security.md). )

Most of the security discussion has been moved into its own document,
[Okapi Security Model](security.md).

The security model is concerned about three things:
* Authentication - that we know who the user is
* Authorization - that the user is allowed to make this request
* Permissions - mapping from user roles all the way down to detailed permissions
Most of this work has been delegated to modules, so Okapi itself will not have
to do so much work. But it still needs to orchestrate the whole operation.

Ignoring all the messy details, this how it works: The client (often on a web
browser, but can really be anything) calls the `/login` service to identify
itself. Depending on the tenant, we may have different authorization modules
serving the /login request, and they may take different parameters (username
and password are the most likely, but we can have anything from simple IP
authentication to complex interactions with LDAP, OAuth, or other systems).

The authorization service returns a token to the client, and the client passes
this token in a special header in all requests it makes to Okapi. Okapi in turn
passes it to the authorization module, together with information of what modules
will be called to satisfy the request, and what permissions those modules require
and desire, and if they have special module level permissions. The authorization
service checks the permissions. If required permissions are not there, the whole
request is denied. If all is well, the module returns information about the
desired permissions, and possibly special tokens to be passed to some modules.

Okapi passes the request to each module in the pipeline in turn. Each of them
get information of the desired permissions, so they can alter the behavior as
needed, and a token that they can use for further calls.

The trivial okapi-test-auth-module module included in the Okapi source tree does
not implement much of this scheme. It is there just to provide an example of some
kind of authentication mechanisms and its interfaces.

### Open Issues

#### Caching

Okapi can provide an additional caching layer between modules,
especially in busy, read-heavy, multi-module pipelines. We plan to
follow standard HTTP mechanisms and semantics in this respect, and
implementation details will be established within the coming months.

#### Instrumentation and Analytics

In a microservices architecture, monitoring is key to ensure robustness
and health of the entire system. The way to provide useful monitoring
is to include well-defined instrumentation points ("hooks") before and
after each step of execution of the request processing
pipeline. Besides monitoring, instrumentation is crucial for the
ability to quickly diagnose issues in the running system ("hot"
debugging) and discovering performance bottlenecks (profiling). We are
looking at established solutions in this regard: e.g. JMX,
Dropwizard Metrics, Graphite, etc.

A multi-module system may provide a wide variety of metrics and an
immense amount of measurement data. Only a fraction of this data can
be analyzed at runtime, most of it must be captured for analysis at a
later stage. Capturing and storing data in a form that lends itself to
an effortless post factum analysis is essential for analytics and we
are looking into integration between open and popular solutions and
Okapi.

#### Response Aggregation

There is no direct support for response aggregation in Okapi at the
moment, as Okapi assumes sequential execution of the pipeline and
forwards each response to the next module in the pipeline. In this
mode, it is entirely possible to implement an aggregation module that
will communicate with multiple modules (via Okapi, to retain the
provided authentication and service discovery) and combine the
responses. In further releases a more generic approach to response
aggregation will be evaluated.

#### Asynchronous messaging

At present, Okapi assumes and implements HTTP as the transport
protocol between modules, both on the front-end and within the
system. HTTP is based on a request-response paradigm and does not
directly include asynchronous messaging capabilities.  It is, however,
entirely possible to model an asynchronous mode of operation on top of
HTTP, e.g. using a polling approach or HTTP extensions like
websockets. We anticipate that for future releases of Okapi we will
investigate the asynchronous approach in depth and provide support for
some open messaging protocols (e.g. STOMP).

## Implementation

We have a rudimentary implementation of Okapi in place. The examples below
are supposed to work with the current implementation.

### Missing features

 Nothing major, at this point.


## Compiling and Running

The latest source of the software can be found at
[GitHub](https://github.com/sling-incubator/okapi). At the moment the
repository is not publicly visible.

<!-- TODO - Use the public address, when we have one -->

The build requirements are:

 * Apache Maven 3.3.1 or later.
 * Java 8 JDK
 * [Git](https://git-scm.com)

With these available, build with:

```
git clone git@github.com:sling-incubator/okapi.git
cd okapi
mvn install
```

<!-- TODO - Use the public address, when we have one -->

The install rule also runs a few tests. Tests should not fail.
If they do, please report it and in the meantime fall back to:

```
mvn install -DskipTests
```

If successful, the output of `mvn install` should have this line near
the end:

```
[INFO] BUILD SUCCESS
```

The okapi directory contains a few sub modules. These are:

 * `okapi-core`: the gateway server itself
 * `okapi-common`: utilities used by both gateway and modules
 * `doc`: documentation, including this guide
 * `okapi-test-auth-module`: a simple module for testing authentication stuff
 * `okapi-test-module`: a module mangling HTTP content for test purposes
 * `okapi-test-header-module`: a module to test headers-only mode

(Note the build order specified in the `pom.xml`:
okapi-core must be last because its tests rely on the previous ones.)

The result for each module and okapi-core is a combined jar file
with all necessary components combined - including Vert.x. The listening
port is adjusted with property `port`.

For example, to run the okapi-test-auth-module module and listen on port 8600, use:

```
cd okapi-test-auth-module
java -Dport=8600 -jar target/okapi-test-auth-module-fat.jar
```

In the same way, to run the okapi-core, specify its jar file. It is
also necessary to provide a further command-line argument: a command
telling okapi-core what mode to run in. When playing with okapi on a
single node, we use the `dev` mode.

```
cd okapi-core
java -Dport=8600 -jar target/okapi-core-fat.jar dev
```

There are other commands available. Supply `help` to get a description of
these.

A Maven rule to run the gateway is provided as part of the `pom.xml`,
in the main directory.

```
mvn exec:exec
```
This will start the okapi-core and make it listen on its default port: 9130.

For remote debugging you can use
```
mvn exec:exec@debug
```
This command requires Maven >= 3.3.1. It will listen for a
debugging client on port 5005.

## Using Okapi

These examples show how to use Okapi from the command line, using the `curl`
http client. You should be able to copy and paste the commands to your
command line from this document.

The exact definition of the services is in the RAML files listed in
the [Reference](#reference) section.

### Storage

Okapi defaults to an internal in-memory mock storage, so it can run without
any database layer under it. This is fine for development and testing, but of
course in real life we will want some of our data to persist from one invocation
to the next. At the moment, MongoDB storage can be enabled by adding the
option `-Dstorage=mongo` to the command line that starts Okapi.

### Curl examples

The examples in the following sections can be pasted into a command-line console.

It is also possible to extract all the example records with a perl
one-liner, assuming you have this MarkDown source of this guide in the
current directory as _guide.md_ -- as is the case in the source tree.

```
perl -n -e  'print if /^cat /../^END/;' guide.md  | sh
```

It is also possible to run all the examples with a slightly more complex command:

```
perl -n -e  'print if /^curl /../http/; ' guide.md |
  grep -v 8080 | grep -v DELETE |
  sh -x
```

This explicitly omits the cleaning up DELETE commands, so it leaves Okapi in a
well-defined state with a few modules enabled for a few known tenants.

See the script `doc/okapi-examples.sh`.
Also see [Okapi demonstration](demos.md).

### Example modules

Okapi is all about invoking modules, so we need to have a few to play with.
It comes with three dummy modules that demonstrate different things.

Note that these are only intended for demonstration and test purposes.
Do not base any real modules on these.

There are additional modules in the separate repository
[folio-sample-modules](https://github.com/sling-incubator/folio-sample-modules).

<!-- TODO - Use the public address, when we have one -->

#### Okapi-test-module

This is a very simple module. If you make a GET request to it, it will reply "It
works". If you POST something to it, it will reply with "Hello" followed by
whatever you posted. It can do a few other tricks too, like echoing request
headers. These are used in the tests for okapi-core.

Normally Okapi will be starting and stopping these modules for you, but we will
run this one directly for now -- mostly to see how to use curl, a
command-line HTTP client that is useful for testing.

Open a console window, navigate to the okapi project root and issue the command:

```
java -jar okapi-test-module/target/okapi-test-module-fat.jar
```

This starts the okapi-test-module listening on port 8080.

Now open another console window, and try to access the
test module with:

```
curl -w '\n' http://localhost:8080/testb
```

It should tell you that it works.

The option "`-w '\n'`" is just to make curl output an extra newline,
because the responses do not necessarily end in newlines.

Now we will try to POST something to the test module. In real life this
would be a JSON structure, but for now a simple text string will do.

```
echo "Testing Okapi" > okapi.txt
curl -w '\n' -X POST -d @okapi.txt http://localhost:8080/testb
```

Again we have the -w option to get a newline in the output, and this
time we add `-X POST` to make it a post request, and `-d @okapi.txt`
to specify the name of the file containing the data that we want to
post.

The test module should respond with

    Hello Testing Okapi

which is our test data, with a "Hello" prepended to it.

That is enough about the okapi-test-module for now. Go back to the window
where you left it running, and kill it with a `Ctrl-C` command. It should
not have produced any output after the initial messages.

#### Okapi-test-header-module

The `test-header` module demonstrates the use of a type=headers module; that is
a module which inspects HTTP headers and produces a new set of HTTP headers.
The response body is ignored and should be empty.

Start with:

```
java -jar okapi-test-header-module/target/okapi-test-header-module-fat.jar
```

The module reads `X-my-header` from leading path `/testb`. If that header is
present, it will take its value and append `,foo`.
If no such header is present, it will use the value `foo`.

These two cases can be demonstrated with:

```
curl -w '\n' -D- http://localhost:8080/testb
```
and
```
curl -w '\n' -H "X-my-header:hey" -D- http://localhost:8080/testb
```

As above, now stop that simple verification.

#### Okapi-test-auth-module

Okapi itself does not do authentication: it delegates that to a
module.  We do not have a fully functional authentication module yet,
but we have a dummy module that can be used to demonstrate how it
works. Also this one is mostly used for testing the auth mechanisms in
Okapi itself.

The dummy module supports two functions: `/login` is, as its name implies,
a login function that takes a username and password, and if acceptable,
returns a token in a HTTP header. Any other path goes through the check
function that checks that we have a valid token in the HTTP request
headers.  The token, for this dummy module, is simply the username and
tenant-id concatenated with a checksum. In a real authentication
module it will be something opaque and difficult to fake.

We will see examples of this when we get to play with Okapi itself. If
you want, you can verify the module directly as with the okapi-test-module.

### Running Okapi itself

Now we are ready to start Okapi.
Note: for this example to work it is important that the current directory
of the Okapi is the top-level directory `.../okapi`.

```
java -jar okapi-core/target/okapi-core-fat.jar dev
```

The `dev` command tells to run it in development mode, which makes it start
with a known clean state without any modules or tenants defined.

Okapi lists its PID (process ID) and says it `succeeded deploying verticle`.
That means it is running, and listening on the default port
which happens to be 9130, and using in-memory storage. (To use MongoDB
storage instead, add `-Dstorage=mongo` to the command line.)

At the moment Okapi does not know of any module or tenant. But it does
have its own web services enabled. We can verify both by asking Okapi
to list modules and tenants.
```
curl -w '\n' http://localhost:9130/_/proxy/modules
curl -w '\n' http://localhost:9130/_/proxy/tenants
```
Both of these return lists in the form of JSON structures. At present,
because we have just started running, it is an empty list in both
cases:

```
[ ]
```

### Deploying Modules

So we need to tell Okapi that we want to work with some modules. In real life
these operations would be carried out by a properly authorized administrator.

As mentioned above, the process consists of three parts: deployment, discovery,
and configuring the proxying.

#### Deploying the test-basic module

To tell Okapi that we want to use the `okapi-test-module`, we create a JSON
structure of module metadata and POST it to Okapi:

```
cat > /tmp/okapi-deploy-test-basic.json <<END
{
  "srvcId" : "test-basic",
  "descriptor" : {
    "exec" : "java -Dport=%p -jar okapi-test-module/target/okapi-test-module-fat.jar"
   }
}
END
```

The module descriptor tells Okapi that it needs to start the given
process to deploy the module.

Now we will deploy the module:

```
curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/okapi-deploy-test-basic.json  \
  http://localhost:9130/_/deployment/modules
```

Note that we need to add the Content-Type header, otherwise curl will try to
be helpful and say something about it being url-encoded, which will confuse
the Java libraries and result in a "500 - Internal Error". We also
added the "-D -" option to make curl display all response headers.

You should see something like this:

```
HTTP/1.1 201 Created
Content-Type: application/json
Location: /_/deployment/modules/localhost-9131
Content-Length: 231

{
  "instId" : "localhost-9131",
  "srvcId" : "test-basic",
  "nodeId" : "localhost",
  "url" : "http://localhost:9131",
  "descriptor" : {
    "exec" : "java -Dport=%p -jar okapi-test-module/target/okapi-test-module-fat.jar"
  }
}
```

Okapi has started the process and has given it an instance ID (instId) which is
part of the Location header. Like other RESTful services the Location header can
be used to identify the resource later.

If you look at the output of
`ps axf | grep okapi`
you should see that okapi-core has spawned a new process for the
okapi-test-module, and that it has been assigned port 9131.

You can ask Okapi to list deployed modules:

```
curl -D - -w '\n' http://localhost:9130/_/deployment/modules

HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 235

[ {
  "instId" : "localhost-9131",
  "srvcId" : "test-basic",
  "nodeId" : "localhost",
  "url" : "http://localhost:9131",
  "descriptor" : {
    "exec" : "java -Dport=%p -jar okapi-test-module/target/okapi-test-module-fat.jar"
  }
} ]
```

Note that Okapi has added an `instId` and a `url`. You can check that
the URL points to the running module:

```
curl -D - -w '\n' http://localhost:9131/testb

HTTP/1.1 200 OK
Content-Type: text/plain
Content-Length: 8

It works
```

If we were running in a clustered environment, this step should be repeated
for each node that should run the `test-basic` module.

#### Adding the module to the discovery

Okapi deployment registers the module with discovery automatically.

#### Telling the proxy about the module

Now we need to inform the proxy that we have a module that can
be enabled for tenants. The proxy is interested in the service identifier (srvcId), so
we pass "test-basic" to it.

```
cat > /tmp/okapi-proxy-test-basic.json <<END
  {
    "id" : "test-basic",
    "name" : "Okapi test module",
    "provides" : [ {
      "id" : "test-basic",
      "version" : "2.2.3"
    } ],
    "routingEntries" : [ {
      "methods" : [ "GET", "POST" ],
      "path" : "/testb",
      "level" : "30",
      "type" : "request-response",
      "permissionsRequired" : [ "test-basic.needed" ],
      "permissionsDesired" : [ "test-basic.extra" ]
    } ]
  }
END

curl -w '\n' -X POST -D -   \
    -H "Content-type: application/json"   \
    -d @/tmp/okapi-proxy-test-basic.json \
   http://localhost:9130/_/proxy/modules

HTTP/1.1 201 Created
Content-Type: application/json
Location: /_/proxy/modules/test-basic
Content-Length: 378

{
  "id" : "test-basic",
  "name" : "Okapi test module",
  "provides" : [ {
    "id" : "test-basic",
    "version" : "2.2.3"
  } ],
  "routingEntries" : [ {
    "methods" : [ "GET", "POST" ],
    "path" : "/testb",
    "level" : "30",
    "type" : "request-response",
    "permissionsRequired" : [ "test-basic.needed" ],
    "permissionsDesired" : [ "test-basic.extra" ]
  } ]
}
```

The `routingEntries` indicate that the module is interested in GET and POST
requests to the `/testb` path and nothing else, and that the module is
supposed to provide a full response. The level is used to to specify
the order in which the request will be sent to multiple modules, as will
be seen later.

`permissionsRequired` and `permissionsDesired` are arrays of permission
bits that are strictly needed for calling `/testb`, or that the
`test-basic` module wants to know about, for example to enable some extra
functionality. Each permission bit is expressed as an opaque string,
but some convention may be used to organise them semantically into a
hierarchy. Okapi collects these permissions into the
`X-Okapi-Permissions-Required` and `X-Okapi-Permissions-Desired` headers, and passes
them on as part of each request that it makes to a module. The
authentication module checks whether the user actually has the required
permissions, and refuses the request if not. (The present dummy
authentication module does not do this kind of check, as it has no
user database to work with.)

The 'modulePermissions' is a list of permissions granted to this module, so it
may do things beyond what the user has permissions for. In this simple example
we ignore those.

Since this is an Okapi module, not a UI module, we don't need any UI descriptors.

The launchDescriptor could be used for an easier way to deploy the module. More
about that below.

#### Deploying and proxying the test-auth module

We could do the same thing with the `okapi-test-auth-module` module.
But there is an easier way. We
simply register the module to the proxy, even without having it running. Then
we tell discovery that we want it, and it will go and deploy it for us. This is
more like the way things will work once we have our app store implemented.

```
cat > /tmp/okapi-module-auth.json <<END
{
  "id" : "test-auth",
  "name" : "Okapi test auth module",
  "provides" : [ {
    "id" : "test-auth",
    "version" : "3.4.5"
  } ],
  "requires" : [ {
    "id" : "test-basic",
    "version" : "2.2.1"
  } ],
  "routingEntries" : [ {
    "methods" : [ "*" ],
    "path" : "/",
    "level" : "10",
    "type" : "headers"
  }, {
    "methods" : [ "POST" ],
    "path" : "/login",
    "level" : "20",
    "type" : "request-response"
  } ],
  "launchDescriptor" : {
    "exec" : "java -Dport=%p -jar okapi-test-auth-module/target/okapi-test-auth-module-fat.jar"
  }
}
END
```

For the purposes of this example, we specify that the `test-auth` module requires
the `test-basic` module to be available, and at least version 2.2.1. You can
try to see what happens if you require different versions, like 1.9.9,
2.1.1, or 2.3.9.

Here we have two routing entries. The second says that this module is
interested in POST requests to the /login path. This is what we use for
actually logging in.

The first routing entry says that this module is interested in seeing
any request at all, and on a pretty low level too (10), which means
that any request should go through the `test-auth` module before being
directed to a higher-level module that does the actual work. In this
way, supporting modules like authentication or logging can be tied to
some or all requests.

Note that we specify in the launchDescriptor how this module is supposed to
be started.

Then we post it as before:

```
curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/okapi-module-auth.json  \
  http://localhost:9130/_/proxy/modules
```

And should see:

```
HTTP/1.1 201 Created
Content-Type: application/json
Location: /_/proxy/modules/test-auth
Content-Length: 698

{
  "id" : "test-auth",
  "name" : "Okapi test auth module",
  "provides" : [ {
    "id" : "test-auth",
    "version" : "3.4.5"
  } ],
  "requires" : [ {
    "id" : "test-basic",
    "version" : "2.2.1"
  } ],
  "routingEntries" : [ {
    "methods" : [ "*" ],
    "path" : "/",
    "level" : "10",
    "type" : "headers",
    "permissionsRequired" : null,
    "permissionsDesired" : null
  }, {
    "methods" : [ "POST" ],
    "path" : "/login",
    "level" : "20",
    "type" : "request-response",
    "permissionsRequired" : null,
    "permissionsDesired" : null
  } ],
  "launchDescriptor" : {
    "exec" : "java -Dport=%p -jar okapi-test-auth-module/target/okapi-test-auth-module-fat.jar"
  }
}
```

At this point Okapi knows that we have an auth module. It has stored the info
in its database, so in theory it is persistent. In practice we are running our
Okapi in 'dev' mode, which uses a volatile in-memory database.

Now we need to deploy the module, and tell the discovery that we have it
running. This can be done in one request. Note that we post this to `/_/discovery`,
it will talk to `/_/deployment` on the correct node - which in this case is
`localhost`, the machine we are running our demo on.

```
cat > /tmp/okapi-deploy-test-auth.json <<END
{
  "srvcId" : "test-auth",
  "nodeId" : "localhost",
  "descriptor" : null
}
END

curl -w '\n' -D - -s \
  -X POST \
  -H "Content-type: application/json" \
  -d @/tmp/okapi-deploy-test-auth.json  \
  http://localhost:9130/_/discovery/modules

HTTP/1.1 201 Created
Content-Type: application/json
Location: /_/discovery/modules/test-auth/localhost-9132
Content-Length: 240

{
  "instId" : "localhost-9132",
  "srvcId" : "test-auth",
  "nodeId" : "localhost",
  "url" : "http://localhost:9132",
  "descriptor" : {
    "exec" : "java -Dport=%p -jar okapi-test-auth-module/target/okapi-test-auth-module-fat.jar"
  }
}
```

Now we have two modules, as can be seen with:

```
curl -w '\n' http://localhost:9130/_/proxy/modules
curl -w '\n' http://localhost:9130/_/discovery/modules
```

but we still can not use them in the way that they would be used in a real
system. Since each invocation of a module is on behalf of a tenant,
we need to create some tenants too.

### Creating tenants

For this example we create two tenants. These are simple requests:

```
cat > /tmp/okapi-tenant1.json <<END
{
  "id" : "our",
  "name" : "our library",
  "description" : "Our Own Library"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/okapi-tenant1.json  \
  http://localhost:9130/_/proxy/tenants
```

Okapi responds with:

```
HTTP/1.1 201 Created
Content-Type: application/json
Location: /_/proxy/tenants/our
Content-Length: 81

{
  "id" : "our",
  "name" : "our library",
  "description" : "Our Own Library"
}
```

And the second tenant is similar:

```
cat > /tmp/okapi-tenant2.json <<END
{
  "id" : "other",
  "name" : "otherlibrary",
  "description" : "The Other Library"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/okapi-tenant2.json  \
  http://localhost:9130/_/proxy/tenants

HTTP/1.1 201 Created
Content-Type: application/json
Location: /_/proxy/tenants/other
Content-Length: 86

{
  "id" : "other",
  "name" : "otherlibrary",
  "description" : "The Other Library"
}
```

Again, we can list them with:

```
curl -w '\n' http://localhost:9130/_/proxy/tenants
```

We can now get information for one of these again:

```
curl -w '\n' http://localhost:9130/_/proxy/tenants/our
```

### Enabling a module for a tenant

There is still one step before we can use our modules. We need to specify which
tenants have which modules enabled. For our own library we enable the
`test-basic` module, without enabling the `test-auth` module.

```
cat > /tmp/okapi-enable-tenant1.json <<END
{
  "id" : "test-basic"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/okapi-enable-tenant1.json  \
  http://localhost:9130/_/proxy/tenants/our/modules
```

Note that we are using a RESTful approach here: the URL
`http://localhost:9130/_/proxy/tenants/our/modules` names the set of
modules that are enabled for our library, and we POST a deployed
module to this set. The tenant for which to enable the module is in the URL;
the module to be enabled is in the payload.

Now we can ask Okapi which modules are enabled for our tenant, and get
back a JSON list:

````
curl -w '\n' http://localhost:9130/_/proxy/tenants/our/modules
````

### Using a module

Finally we should be able to make use of the module, as a regular tenant:

```
curl -w '\n' -D -  http://localhost:9130/test-basic
```

But of course Okapi can not know which tenant it is that is wanting to use our
`test-basic` module, so it can not allow such, and returns a 403 forbidden.

We need to pass the tenant in the `X-Okapi-Tenant` header of our request:

```
curl -w '\n' -D -  \
  -H "X-Okapi-Tenant: our" \
  http://localhost:9130/testb
```

and indeed the `test-basic` module says: _It works_

### Enabling both modules for the other tenant

For the other tenant, we want to require that only authenticated users
can access the `test-basic` module. So we need to enable both
`test-basic` and `test-auth` for it:

```
cat > /tmp/okapi-enable-tenant2a.json <<END
{
  "id" : "test-basic"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/okapi-enable-tenant2a.json  \
  http://localhost:9130/_/proxy/tenants/other/modules

cat > /tmp/okapi-enable-tenant2b.json <<END
{
  "id" : "test-auth"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/okapi-enable-tenant2b.json  \
  http://localhost:9130/_/proxy/tenants/other/modules
```

You can list the enabled modules with:

```
curl -w '\n' -D - http://localhost:9130/_/proxy/tenants/other/modules
```

### Authentication problems

If the other library tries to use our `test-basic` module:

```
curl -w '\n' -D -  \
  -H "X-Okapi-Tenant: other" \
  http://localhost:9130/testb
```

it fails with:

```
HTTP/1.1 401 Unauthorized
Content-Type: text/plain
X-Okapi-Trace: GET test-auth:401 52904us
Transfer-Encoding: chunked

Auth.check called without X-Okapi-Token
```

Why does this happen? The other library has the `test-auth` module enabled,
and that module intercepts _all_ requests (by means of the
`routingEntry` whose path is `/` and whose `level` is 10). As a result,
the `test-auth` module is invoked before the `test-basic` module.
And the `test-auth`
module causes the request to be rejected unless it contains a suitable
`X-Okapi-Token`.

In order to get that token, we need to invoke the `/login` service
first:

```
cat > /tmp/okapi-login.json <<END
{
  "tenant": "other",
  "username": "peter",
  "password": "peter-password"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant: other" \
  -d @/tmp/okapi-login.json  \
  http://localhost:9130/login
```

At present, any username is accepted so long as the password is that
username with "`-password`" appended. Obviously a real authentication
module would look up the username/password pair in a user register.

When successful, `/login` echoes the login parameters as its response;
but more importantly, it also returns a header containing an
authentication token:

    X-Okapi-Token: other:peter:04415268d4170e95ec497077ad4cba3c

Now we can add that header to the request, and see if things finally work:

```
curl -w '\n' -D -  \
  -H "X-Okapi-Tenant: other" \
  -H "X-Okapi-Token: other:peter:04415268d4170e95ec497077ad4cba3c" \
  http://localhost:9130/testb
```

It works!

Okapi also supports PUT requests to modify existing modules and tenants.
These are left as an exercise for the reader.

### Cleaning up

Now we can clean up some things:

```
curl -X DELETE -w '\n'  -D - http://localhost:9130/_/proxy/tenants/our
curl -X DELETE -w '\n'  -D - http://localhost:9130/_/proxy/tenants/other
curl -X DELETE -w '\n'  -D - http://localhost:9130/_/proxy/modules/test-auth
curl -X DELETE -w '\n'  -D - http://localhost:9130/_/proxy/modules/test-basic
curl -X DELETE -w '\n'  -D - http://localhost:9130/_/discovery/modules/test-auth/localhost-9132
curl -X DELETE -w '\n'  -D - http://localhost:9130/_/discovery/modules/test-basic/localhost-9131
```

Okapi responds to each of these with a simple:

```
HTTP/1.1 204 No Content
Content-Type: text/plain
Content-Length: 0

```

Finally we can stop the Okapi instance we had running, with a simple `Ctrl-C`
command.

## Reference

### Okapi program

The Okapi program is shipped as a bundled jar (okapi-core-fat.jar). The
general invocation is:

  `java` [*java-options*] `-jar path/okapi-core-fat.jar` *command* [*options*]

This is a standard Java command line. Of particular interest is
java-option `-D` which may set properties for the program: see below
for relevant properties. Okapi itself parses *command* and any
*options* that follow.

#### Java -D options

The -D option can be used to specify various run-time parameters in
Okapi. These must be at the beginning of the command line, before the
`-jar`.

* `port`: The port on which Okapi listens. Defaults to 9130
* `port_start` and `port_end`: The range of ports for modules. Default to
`port`+1 to `port`+10, normally 9131 to 9141
* `host`: Hostname to be used in the URLs returned by the deployment service.
Defaults to `localhost`
* `storage`: Defines the storage back end, `mongo` or (the default) `inmemory`
* `loglevel`: The logging level. Defaults to `INFO`; other useful values are
`DEBUG`, `TRACE`, `WARN` and `ERROR`.
* `okapiurl`: Tells Okapi its own official URL. This gets passed to the modules
as X-Okapi-Url header, and the modules can use this to make further requests
to Okapi. Defaults to `http://localhost:9130/` or what ever port specified.

#### Command

Okapi requires exactly one command to be given. These are:
* `cluster` for running in clustered mode/production
* `dev` for running in development, single-node mode
* `deployment` for deployment only. Clustered mode
* `proxy` for proxy + discovery. Clustered mode
* `help` to list command-line options and commands

#### Command-line options

These options are at the end of the command line:

* `-hazelcast-config-cp` _file_ -- Read config from class path
* `-hazelcast-config-file` _file_ -- Read config from local file
* `-hazelcast-config-url` _url_ -- Read config from URL
* `-enable-metrics` -- Enables the sending of various metrics to a Carbon back
end.
* `-cluster-host` _ip_ -- Vertx cluster host
* `-cluster-port` _port_ -- Vertx cluster port

### Web Service

The Okapi service requests (all those prefixed with `/_/`) are specified
in the [RAML](http://raml.org/) syntax.

  * The top-level file, [okapi.raml](../okapi-core/src/main/raml/okapi.raml)
  * [Directory of RAML and included JSON Schema files](../okapi-core/src/main/raml)

### Instrumentation

Okapi pushes instrumentation data to a Carbon/Graphite backend, from which
they can be shown with something like Grafana. Vert.x pushes some numbers
automatically, but various parts of Okapi push their own numbers explicitly,
so we can classify by tenant or module. Individual
modules may push their own numbers as well, as needed. It is hoped that they
will use a key naming scheme that is close to what we do in Okapi.

  * `folio.okapi.`_\$HOST_`.proxy.`_\$TENANT_`.`_\$HTTPMETHOD_`.`_\$PATH`_ -- Time for the whole request, including all modules that it ended up invoking.
  * `folio.okapi.`_\$HOST_`.proxy.`_\$TENANT_`.module.`_\$SRVCID`_ -- Time for one module invocation.
  * `folio.okapi.`_\$HOST_`.tenants.count` -- Number of tenants known to the system
  * `folio.okapi.`_\$HOST_`.tenants.`_\$TENANT_`.create` -- Timer on the creation of tenants
  * `folio.okapi.`_\$HOST_`.tenants.`_\$TENANT_`.update` -- Timer on the updating of tenants
  * `folio.okapi.`_\$HOST_`.tenants.`_\$TENANT_`.delete` -- Timer on deleting tenants
  * `folio.okapi.`_\$HOST_`.modules.count` -- Number of modules known to the system
  * `folio.okapi.`_\$HOST_`.deploy.`_\$SRVCID_`.deploy` -- Timer for deploying a module
  * `folio.okapi.`_\$HOST_`.deploy.`_\$SRVCID_`.undeploy` -- Timer for undeploying a module
  * `folio.okapi.`_\$HOST_`.deploy.`_\$SRVCID_`.update` -- Timer for updating a module

The `$`_NAME_ variables will of course get the actual values.

There are some examples of Grafana dashboard definitions in
the `doc` directory:

* [`grafana-main-dashboard.json`](grafana-main-dashboard.json)
* [`grafana-module-dashboard.json`](grafana-module-dashboard.json)
* [`grafana-node-dashboard.json`](grafana-node-dashboard.json)
* [`grafana-tenant-dashboard.json`](grafana-tenant-dashboard.json)

Here are some examples of useful graphs in Grafana. These can be pasted directly under the
metric, once you change edit mode (the tool menu at the end of the line) to text
mode.

  * Activity by tenant:

      `aliasByNode(sumSeriesWithWildcards(stacked(folio.okapi.localhost.proxy.*.*.*.m1_rate, 'stacked'), 5, 6), 4)`
  * HTTP requests per minute (also for PUT, POST, DELETE, etc)

      `alias(folio.okapi.*.vertx.http.servers.*.*.*.*.get-requests.m1_rate, 'GET')`
  * HTTP return codes (also for 4XX and 5XX codes)

      `alias(folio.okapi.*.vertx.http.servers.*.*.*.*.responses-2xx.m1_rate, '2XX OK')`
  * Modules invoked by a given tenant

      `aliasByNode(sumSeriesWithWildcards(folio.okapi.localhost.SOMETENANT.other.*.*.m1_rate, 5),5)`
