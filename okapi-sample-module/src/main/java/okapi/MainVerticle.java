/*
 * Copyright (c) 1995-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.lang.management.ManagementFactory;

public class MainVerticle extends AbstractVerticle {

  public void my_stream_handle(RoutingContext ctx) {
    ctx.response().setStatusCode(200);
    final String ctype = ctx.request().headers().get("Content-Type");
    String xmlMsg = "";
    if (ctype != null && ctype.toLowerCase().contains("xml")) {
      xmlMsg = " (XML) ";
    }
    final String xmlMsg2 = xmlMsg;
    //System.out.println("Sample: ctype='" + ctype + "'");
    if (ctx.request().method().equals(HttpMethod.GET)) {
      ctx.request().endHandler(x -> {
        ctx.response().end("It works" + xmlMsg2);
      });
    } else {
      ctx.response().setChunked(true);
      ctx.response().write("Hello " + xmlMsg2);
      ctx.request().handler(x -> {
        ctx.response().write(x);
      });
      ctx.request().endHandler(x -> {
        ctx.response().end();
      });
    }
  }

  @Override
  public void start(Future<Void> fut) throws IOException {
    Router router = Router.router(vertx);

    final int port = Integer.parseInt(System.getProperty("port", "8080"));
    System.out.println("Started sample-module on port " + port);
    //enable reading body to string

    router.get("/sample").handler(this::my_stream_handle);
    router.post("/sample").handler(this::my_stream_handle);

    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(
        port,
        result -> {
          if (result.succeeded()) {
            System.out.println("Okapi-Sample started PID "
              + ManagementFactory.getRuntimeMXBean().getName()
              + ". Listening on port " + port);
            fut.complete();
          } else {
            fut.fail(result.cause());
            System.out.println("Okapi-Sample failed to start listening! " + result.cause());
            //vertx.close();
          }
        }
      );
  }

}
